package personal.notes.touch.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeColor: Int = 0xFFFB6443.toInt(),
    val darkMode: Int = 0,
    val penDefaultsJson: String = "{}",
    val keepScreenOn: Boolean = false,
    val autoSaveIntervalMs: Int = 1000,
    val defaultBackground: Int = 0,
    val pressureSizeEnabled: Boolean = true,
    val autoShapeEnabled: Boolean = false,
    val volumeForPage: Boolean = false,
    val backgroundMode: Int = 0,
    val backgroundDensity: Int = 40,
    val doublePage: Boolean = false,
    val showPenBox: Boolean = false,
    val fullScreen: Boolean = false,
    val autoCenter: Boolean = true,
    val penSlot1Color: Int = 0xFF000000.toInt(),
    val penSlot1Width: Float = 6f,
    val penSlot2Color: Int = 0xFFFB6443.toInt(),
    val penSlot2Width: Float = 3f
)
