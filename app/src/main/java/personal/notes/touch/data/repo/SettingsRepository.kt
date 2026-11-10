package personal.notes.touch.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import personal.notes.touch.data.db.dao.SettingsDao
import personal.notes.touch.data.db.entity.SettingsEntity

class SettingsRepository(private val settingsDao: SettingsDao) {
    fun getSettings(): Flow<SettingsEntity?> = settingsDao.getSettings()

    suspend fun getSettingsDirect(): SettingsEntity = withContext(Dispatchers.IO) {
        settingsDao.getSettingsDirect() ?: SettingsEntity().also {
            settingsDao.insertOrUpdate(it)
        }
    }

    /**
     * The settings live in one row. Two updates that run at the same time
     * would both read the old row, and the second write would undo the
     * first one - so the read-modify-write is serialised here.
     */
    private val updateLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun updateSettingsField(
        block: (SettingsEntity) -> SettingsEntity
    ) = withContext(Dispatchers.IO) {
        updateLock.withLock {
            val current = getSettingsDirect()
            settingsDao.insertOrUpdate(block(current))
        }
    }

    suspend fun updateSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.insertOrUpdate(settings)
    }

    suspend fun updateThemeColor(color: Int) = updateSettingsField { current ->
        current.copy(themeColor = color)
    }

    suspend fun updateDarkMode(mode: Int) = updateSettingsField { current ->
        current.copy(darkMode = mode)
    }

    suspend fun updateKeepScreenOn(keep: Boolean) = updateSettingsField { current ->
        current.copy(keepScreenOn = keep)
    }

    suspend fun updateAutoSaveInterval(intervalMs: Int) = updateSettingsField { current ->
        current.copy(autoSaveIntervalMs = intervalMs)
    }

    suspend fun updateDefaultBackground(bgOrdinal: Int) = updateSettingsField { current ->
        current.copy(defaultBackground = bgOrdinal)
    }

    suspend fun updatePressureSizeEnabled(enabled: Boolean) = updateSettingsField { current ->
        current.copy(pressureSizeEnabled = enabled)
    }

    suspend fun updateAutoShapeEnabled(enabled: Boolean) = updateSettingsField { current ->
        current.copy(autoShapeEnabled = enabled)
    }

    suspend fun updateVolumeForPage(enabled: Boolean) = updateSettingsField { current ->
        current.copy(volumeForPage = enabled)
    }

    suspend fun updateBackgroundMode(mode: Int) = updateSettingsField { current ->
        current.copy(backgroundMode = mode)
    }

    suspend fun updateBackgroundDensity(density: Int) = updateSettingsField { current ->
        current.copy(backgroundDensity = density)
    }

    suspend fun updateDoublePage(enabled: Boolean) = updateSettingsField { current ->
        current.copy(doublePage = enabled)
    }

    suspend fun updateShowPenBox(enabled: Boolean) = updateSettingsField { current ->
        current.copy(showPenBox = enabled)
    }

    suspend fun updateFullScreen(enabled: Boolean) = updateSettingsField { current ->
        current.copy(fullScreen = enabled)
    }

    suspend fun updateAutoCenter(enabled: Boolean) = updateSettingsField { current ->
        current.copy(autoCenter = enabled)
    }

    suspend fun updatePageTurningDirection(direction: Int) = updateSettingsField { current ->
        current.copy(pageTurningDirection = direction)
    }

    suspend fun updatePageTurningMode(mode: Int) = updateSettingsField { current ->
        current.copy(pageTurningMode = mode)
    }

    suspend fun updatePenSlot(slot: Int, color: Int, width: Float) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        val updated = if (slot == 1) current.copy(penSlot1Color = color, penSlot1Width = width)
        else current.copy(penSlot2Color = color, penSlot2Width = width)
        settingsDao.insertOrUpdate(updated)
    }

    suspend fun updatePenDefaults(color: Int, strokeWidth: Float, pressure: Float = 1.0f) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        val json = """{"color":$color,"width":$strokeWidth,"pressure":$pressure}"""
        settingsDao.insertOrUpdate(current.copy(penDefaultsJson = json))
    }
}
