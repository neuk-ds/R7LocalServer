package ru.mrnds.r7localserver.platform

object Platform {
    val current: OperatingSystem by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> OperatingSystem.WINDOWS
            osName.contains("linux") -> OperatingSystem.LINUX
            osName.contains("mac") -> OperatingSystem.MACOS
            else -> OperatingSystem.OTHER
        }
    }
    val isWindows: Boolean
        get() = current == OperatingSystem.WINDOWS
    val isLinux: Boolean
        get() = current == OperatingSystem.LINUX
    val isMacOs: Boolean
        get() = current == OperatingSystem.MACOS
}