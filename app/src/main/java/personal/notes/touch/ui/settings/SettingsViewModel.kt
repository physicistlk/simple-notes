package personal.notes.touch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import personal.notes.touch.data.db.entity.SettingsEntity
import personal.notes.touch.data.repo.SettingsRepository

data class PenSettings(
    val color: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 6f,
    val pressureSensitivity: Float = 1.0f
)

fun parsePenDefaults(json: String): PenSettings {
    if (json.isBlank()) return PenSettings()
    return try {
        val colorMatch = Regex(""""color"\s*:\s*(-?\d+)""").find(json)
        val widthMatch = Regex(""""width"\s*:\s*([\d.]+)""").find(json)
        val pressureMatch = Regex(""""pressure"\s*:\s*([\d.]+)""").find(json)
        val color = colorMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0xFF000000.toInt()
        val width = widthMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 6f
        val pressure = pressureMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f
        PenSettings(color = color, strokeWidth = width, pressureSensitivity = pressure)
    } catch (_: Exception) {
        PenSettings()
    }
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<SettingsEntity> = repository.getSettings()
        .map { it ?: SettingsEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsEntity()
        )

    fun setThemeColor(color: Int) {
        viewModelScope.launch {
            repository.updateThemeColor(color)
        }
    }

    fun setDarkMode(mode: Int) {
        viewModelScope.launch {
            repository.updateDarkMode(mode)
        }
    }

    fun setKeepScreenOn(keep: Boolean) {
        viewModelScope.launch {
            repository.updateKeepScreenOn(keep)
        }
    }

    fun setAutoSaveInterval(intervalMs: Int) {
        viewModelScope.launch {
            repository.updateAutoSaveInterval(intervalMs)
        }
    }

    fun setDefaultBackground(bgOrdinal: Int) {
        viewModelScope.launch {
            repository.updateDefaultBackground(bgOrdinal)
        }
    }

    fun setPressureSizeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePressureSizeEnabled(enabled)
        }
    }

    fun setAutoShapeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoShapeEnabled(enabled)
        }
    }

    fun setVolumeForPage(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateVolumeForPage(enabled)
        }
    }

    fun setBackgroundMode(mode: Int) {
        viewModelScope.launch {
            repository.updateBackgroundMode(mode)
        }
    }

    fun setBackgroundDensity(density: Int) {
        viewModelScope.launch {
            repository.updateBackgroundDensity(density)
        }
    }

    fun setDoublePage(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDoublePage(enabled)
        }
    }

    fun setShowPenBox(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateShowPenBox(enabled)
        }
    }

    fun setFullScreen(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateFullScreen(enabled)
        }
    }

    fun setAutoCenter(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoCenter(enabled)
        }
    }

    fun setPageTurningDirection(direction: Int) {
        viewModelScope.launch { repository.updatePageTurningDirection(direction) }
    }

    fun setPageTurningMode(mode: Int) {
        viewModelScope.launch { repository.updatePageTurningMode(mode) }
    }

    fun setPenSlot(slot: Int, color: Int, width: Float) {
        viewModelScope.launch {
            repository.updatePenSlot(slot, color, width)
        }
    }

    fun setPenDefaults(color: Int, strokeWidth: Float, pressure: Float = 1.0f) {
        viewModelScope.launch {
            repository.updatePenDefaults(color, strokeWidth, pressure)
        }
    }

    fun setPressureSensitivity(pressure: Float) {
        viewModelScope.launch {
            val current = parsePenDefaults(settings.value.penDefaultsJson)
            repository.updatePenDefaults(current.color, current.strokeWidth, pressure)
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
