package ru.mrnds.r7localserver.settings.autoStart

import ru.mrnds.r7localserver.platform.OperatingSystem
import ru.mrnds.r7localserver.platform.Platform

fun createAutoStart(): AutoStart {
    return when (Platform.current) {
        OperatingSystem.WINDOWS -> WindowsAutoStart()
        OperatingSystem.LINUX -> LinuxAutoStart()
        OperatingSystem.MACOS -> NoOpAutoStart()
        OperatingSystem.OTHER -> NoOpAutoStart()
    }
}