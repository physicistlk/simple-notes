package personal.notes.touch.data.repo

import kotlinx.coroutines.Dispatchers
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

    suspend fun updateSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.insertOrUpdate(settings)
    }

    suspend fun updateThemeColor(color: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(themeColor = color))
    }

    suspend fun updateDarkMode(mode: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(darkMode = mode))
    }

    suspend fun updateKeepScreenOn(keep: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(keepScreenOn = keep))
    }

    suspend fun updateAutoSaveInterval(intervalMs: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(autoSaveIntervalMs = intervalMs))
    }

    suspend fun updateDefaultBackground(bgOrdinal: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(defaultBackground = bgOrdinal))
    }

    suspend fun updatePressureSizeEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(pressureSizeEnabled = enabled))
    }

    suspend fun updateAutoShapeEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(autoShapeEnabled = enabled))
    }

    suspend fun updateVolumeForPage(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(volumeForPage = enabled))
    }

    suspend fun updateBackgroundMode(mode: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(backgroundMode = mode))
    }

    suspend fun updateBackgroundDensity(density: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(backgroundDensity = density))
    }

    suspend fun updateDoublePage(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(doublePage = enabled))
    }

    suspend fun updateShowPenBox(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(showPenBox = enabled))
    }

    suspend fun updateFullScreen(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(fullScreen = enabled))
    }

    suspend fun updateAutoCenter(enabled: Boolean) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(autoCenter = enabled))
    }

    suspend fun updatePageTurningDirection(direction: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(pageTurningDirection = direction))
    }

    suspend fun updatePageTurningMode(mode: Int) = withContext(Dispatchers.IO) {
        val current = getSettingsDirect()
        settingsDao.insertOrUpdate(current.copy(pageTurningMode = mode))
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
