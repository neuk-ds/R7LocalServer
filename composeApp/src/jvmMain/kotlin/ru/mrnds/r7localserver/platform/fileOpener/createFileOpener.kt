package ru.mrnds.r7localserver.platform.fileOpener

import ru.mrnds.r7localserver.platform.OperatingSystem
import ru.mrnds.r7localserver.platform.Platform

fun createFileOpener(): FileOpener {
    return when (Platform.current) {
        OperatingSystem.LINUX -> LinuxFileOpener()
        OperatingSystem.WINDOWS -> DesktopFileOpener()
        OperatingSystem.MACOS -> DesktopFileOpener()
        OperatingSystem.OTHER -> DesktopFileOpener()
    }
}