package personal.notes.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import personal.notes.touch.data.db.APP_MIGRATIONS
import java.io.File

/**
 * Schema/migration policy tests (audit C1 / CH4, 2026-11-04: "schema upgrades
 * never lose user data").
 *
 * These run on the plain JVM: the Room schema JSON exported by kapt under
 * app/schemas/ is read as raw text, and the migration registry
 * data/db/Migrations.kt (`APP_MIGRATIONS`) is checked against the schema
 * version it covers. Together they pin the policy declared in the KDoc of
 * AppDatabase:
 *
 * - every schema version must be exported and kept in the repo,
 * - the baseline schema (version 3) stays pinned at `3.json` with its shipped
 *   table list, and legacy dev schemas (1-2) were wiped-by-policy,
 * - the latest exported schema defines the current version: while it is the
 *   baseline the registry must stay empty, and as soon as it is bumped,
 *   consecutive Migration steps from version 3 up to it must exist in
 *   APP_MIGRATIONS at the same time.
 *
 * A Room DAO smoke test (open AppDatabase via Room.inMemoryDatabaseBuilder
 * under an Android context and verify the main DAOs return empty flows) needs
 * Robolectric or real instrumentation. Robolectric is not on the unit-test
 * classpath in this environment; its runner also pulls the android-all SDK
 * jars from the network the first time it runs. Enable such a test when the
 * Robolectric/instrumented infra lands, extending the assumption-guarded
 * pattern already used in PdfImportManagerTest.
 */
class DatabaseSchemaPolicyTest {

    private val schemaDirRelative = "schemas/personal.notes.touch.data.db.AppDatabase"
    private val currentSchemaVersion = 3

    /**
     * Highest schema version exported so far. Bump this together with
     * `version` in AppDatabase and the matching Migration step(s); the
     * baseline pin above keeps `3.json` immutable for legacy reasoning.
     */
    private val latestSchemaVersion = 10

    /** Entity tables that shipped in schema version 3. */
    private val version3Tables = listOf(
        "notes",
        "folders",
        "pages",
        "labels",
        "note_label_cross_ref",
        "flash_cards",
        "flash_cards_types",
        "flash_cards_tags",
        "note_label_usage",
        "settings"
    )

    private fun schemaFile(version: Int): File {
        var probe: File? = File(requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }).absoluteFile
        while (probe != null) {
            val candidate = File(probe, "$schemaDirRelative/$version.json")
            if (candidate.isFile) return candidate
            probe = probe.parentFile
        }
        throw IllegalStateException(
            "Room schema not found at $schemaDirRelative/$version.json (searched up from ${System.getProperty("user.dir")}). " +
                "kapt must copy it from the schemaLocation set in app/build.gradle — " +
                "run ./gradlew assembleDebug once and keep the file committed."
        )
    }

    /**
     * Reads the database version from the header of the exported schema,
     * i.e. the region between "database": { and "entities": [ so entity or
     * column names can never shadow the version field.
     */
    private fun databaseVersion(schemaJson: String): Int {
        val dbStart = schemaJson.indexOf("\"database\"")
        check(dbStart >= 0) { "schema JSON contains no \"database\" object" }
        val entitiesStart = schemaJson.indexOf("\"entities\"", dbStart).let { if (it < 0) schemaJson.length else it }
        val header = schemaJson.substring(dbStart, entitiesStart)
        val match = Regex("\"version\"\\s*:\\s*(\\d+)").find(header)
            ?: throw IllegalStateException("no \"version\" field in the database header of the schema JSON")
        return match.groupValues[1].toInt()
    }

    @Test
    fun currentSchemaVersionIsPinned() {
        val file = schemaFile(currentSchemaVersion)
        assertEquals(
            "$currentSchemaVersion.json must still carry database version $currentSchemaVersion " +
                "(bump it only together with a real Migration in data/db/Migrations.kt)",
            currentSchemaVersion,
            databaseVersion(file.readText())
        )
    }

    @Test
    fun exportedSchemaListsAllEntityTables() {
        val json = schemaFile(currentSchemaVersion).readText()
        for (table in version3Tables) {
            assertTrue(
                "schema $currentSchemaVersion.json must declare table \"$table\"",
                json.contains("\"tableName\"\\s*:\\s*\"$table\"".toRegex())
            )
        }
    }

    @Test
    fun migrationRegistryMatchesSchemaVersion() {
        val latestFile = schemaFile(latestSchemaVersion)
        val version = databaseVersion(latestFile.readText())
        if (version == currentSchemaVersion) {
            // Baseline policy: as long as the shipped schema is version 3,
            // the registry holds no migrations and none may be fabricated.
            assertEquals(
                "APP_MIGRATIONS must stay empty while the schema is the version-3 baseline",
                0,
                APP_MIGRATIONS.size
            )
        } else {
            // Version was bumped: every consecutive step from the baseline
            // schema version upward must be registered in the same change.
            assertEquals(
                "$latestSchemaVersion.json must itself carry database version $latestSchemaVersion",
                latestSchemaVersion,
                version
            )
            val covered = APP_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
            for (v in currentSchemaVersion until version) {
                assertTrue(
                    "schema is at version $version but no Migration for $v -> ${v + 1} is registered in APP_MIGRATIONS",
                    (v to v + 1) in covered
                )
            }
        }
    }
}
