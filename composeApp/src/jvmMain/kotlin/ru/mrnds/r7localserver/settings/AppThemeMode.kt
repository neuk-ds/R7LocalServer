package ru.mrnds.r7localserver.settings

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode(
    val title: String
) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}