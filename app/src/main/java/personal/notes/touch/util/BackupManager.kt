package personal.notes.touch.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import personal.notes.touch.TouchNotesApp
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(
    val success: Boolean,
    val fileCount: Int = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null
)

object BackupManager {

    private const val BACKUP_VERSION = 1
    private const val COMMENT = "TouchNotes Backup v1.0"

    /**
     * Creates a full offline backup ZIP archive written to an OutputStream (e.g. SAF Document Uri).
     */
    suspend fun createBackup(
        context: Context,
        outputStream: OutputStream,
        onProgress: (currentFile: String, count: Int) -> Unit = { _, _ -> }
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as TouchNotesApp

            // 1. Force a WAL checkpoint to flush all pending SQLite transactions to touchnotes.db
            try {
                app.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (_: Exception) {
                // Ignore if in-memory or not supported
            }

            var fileCount = 0
            var totalBytes = 0L

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                zos.setComment(COMMENT)

                // 2. Write manifest.json
                val manifestJson = """{"version":$BACKUP_VERSION,"app":"personal.notes.touch","createdAt":${System.currentTimeMillis()}}"""
                zos.putNextEntry(ZipEntry("manifest.json"))
                val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
                zos.write(manifestBytes)
                zos.closeEntry()
                fileCount++
                totalBytes += manifestBytes.size
                onProgress("manifest.json", fileCount)

                // 3. Write database file and WAL sidecars if present
                val dbFile = context.getDatabasePath("touchnotes.db")
                val dbParent = dbFile.parentFile
                // The shared-memory index (-shm) is derived state and is
                // rebuilt by SQLite on open, so it is never archived.
                val dbFiles = listOfNotNull(
                    dbFile.takeIf { it.exists() },
                    File(dbParent, "touchnotes.db-wal").takeIf { it.exists() && it.length() > 0 }
                )
                for (f in dbFiles) {
                    zos.putNextEntry(ZipEntry("database/${f.name}"))
                    FileInputStream(f).use { fis ->
                        val copied = fis.copyTo(zos)
                        totalBytes += copied
                    }
                    zos.closeEntry()
                    fileCount++
                    onProgress(f.name, fileCount)
                }

                // 4. Write all files under files/note/
                val notesDir = File(context.filesDir, "note")
                if (notesDir.exists()) {
                    notesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relPath = "files/note/" + file.relativeTo(notesDir).path
                        zos.putNextEntry(ZipEntry(relPath))
                        FileInputStream(file).use { fis ->
                            val copied = fis.copyTo(zos)
                            totalBytes += copied
                        }
                        zos.closeEntry()
                        fileCount++
                        onProgress(file.name, fileCount)
                    }
                }
            }
            BackupResult(success = true, fileCount = fileCount, totalBytes = totalBytes)
        } catch (e: Exception) {
            BackupResult(success = false, errorMessage = e.message ?: "Backup failed")
        }
    }

    /**
     * Validates and restores a full backup ZIP archive from an SAF Document Uri.
     */
    suspend fun restoreBackup(
        context: Context,
        safUri: Uri,
        onProgress: (currentFile: String) -> Unit = {}
    ): BackupResult = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        try {
            val inputStream = context.contentResolver.openInputStream(safUri)
                ?: return@withContext BackupResult(success = false, errorMessage = "Cannot open selected backup file.")

            var fileCount = 0
            var totalBytes = 0L
            var hasManifest = false
            var hasDatabase = false

            // 1. Extract archive entries to staging directory
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName == "manifest.json") hasManifest = true
                    if (entryName.startsWith("database/") && entryName.endsWith(".db")) hasDatabase = true

                    if (!entry.isDirectory) {
                        // Prevent Zip Slip vulnerability
                        val destFile = File(stagingDir, entryName)
                        // The separator matters: a sibling whose name merely
                        // starts with the staging directory's name is not
                        // inside it.
                        val root = stagingDir.canonicalPath
                        val target = destFile.canonicalPath
                        if (target != root && !target.startsWith(root + File.separator)) {
                            throw SecurityException("Invalid zip entry: $entryName")
                        }
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            val copied = zis.copyTo(fos)
                            totalBytes += copied
                        }
                        fileCount++
                        onProgress(entryName)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 2. Validate archive structure
            if (!hasManifest && !hasDatabase) {
                stagingDir.deleteRecursively()
                return@withContext BackupResult(
                    success = false,
                    errorMessage = "Invalid backup archive: missing manifest.json or database."
                )
            }

            val app = context.applicationContext as TouchNotesApp

            // 3. Close active database connections
            app.reloadDatabase()

            // 4. Overwrite SQLite database with staged database
            val stagedDb = File(stagingDir, "database/touchnotes.db")
            if (stagedDb.exists()) {
                val targetDb = context.getDatabasePath("touchnotes.db")
                targetDb.parentFile?.mkdirs()
                // Clear old WAL and shared memory sidecars
                File(targetDb.parentFile, "touchnotes.db-wal").delete()
                File(targetDb.parentFile, "touchnotes.db-shm").delete()
                stagedDb.copyTo(targetDb, overwrite = true)
                val stagedWal = File(stagingDir, "database/touchnotes.db-wal")
                if (stagedWal.exists()) {
                    stagedWal.copyTo(File(targetDb.parentFile, "touchnotes.db-wal"), overwrite = true)
                }
            }

            // 5. Overwrite files/note/ directory with staged files
            val stagedNotesDir = File(stagingDir, "files/note")
            if (stagedNotesDir.exists()) {
                val targetNotesDir = File(context.filesDir, "note")
                targetNotesDir.mkdirs()
                stagedNotesDir.copyRecursively(targetNotesDir, overwrite = true)
            }

            // 6. Clean up staging directory
            stagingDir.deleteRecursively()

            // 7. Re-initialize database & repositories
            app.reloadDatabase()

            BackupResult(success = true, fileCount = fileCount, totalBytes = totalBytes)
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            BackupResult(success = false, errorMessage = e.message ?: "Restore failed")
        }
    }
}
