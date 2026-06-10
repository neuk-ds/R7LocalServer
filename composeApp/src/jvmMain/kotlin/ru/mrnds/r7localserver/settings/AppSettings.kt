package ru.mrnds.r7localserver.settings

import kotlinx.serialization.Serializable
import ru.mrnds.r7localserver.settings.AppThemeMode.*

@Serializable
data class AppSettings(
    val serverPort: String = "8124",
    val startWithSystem: Boolean = false,
    val startServerOnAppStart: Boolean = false,
    val startMinimized: Boolean = false,
    val themeMode: AppThemeMode = SYSTEM,
)
