package ru.mrnds.r7localserver.platform

import java.io.File

object AppDirectories {
    private const val APP_DIRECTORY_NAME = "R7LocalServer"

    val configDirectory: File by lazy {
        when (Platform.current) {
            OperatingSystem.WINDOWS -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) {
                    File(appData, APP_DIRECTORY_NAME)
                } else {
                    File(System.getProperty("user.home"), "AppData/Roaming/$APP_DIRECTORY_NAME")
                }
            }

            OperatingSystem.LINUX -> {
                val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
                if (!xdgConfigHome.isNullOrBlank()) {
                    File(xdgConfigHome, APP_DIRECTORY_NAME)
                } else {
                    File(System.getProperty("user.home"), ".config/$APP_DIRECTORY_NAME")
                }
            }

            OperatingSystem.MACOS -> {
                File(System.getProperty("user.home"), "Library/Application Support/$APP_DIRECTORY_NAME")
            }

            OperatingSystem.OTHER -> {
                File(System.getProperty("user.home"), ".$APP_DIRECTORY_NAME")
            }
        }
    }

    val stateDirectory: File by lazy {
        when (Platform.current) {
            OperatingSystem.WINDOWS -> configDirectory

            OperatingSystem.LINUX -> {
                val xdgStateHome = System.getenv("XDG_STATE_HOME")
                if (!xdgStateHome.isNullOrBlank()) {
                    File(xdgStateHome, APP_DIRECTORY_NAME)
                } else {
                    File(System.getProperty("user.home"), ".local/state/$APP_DIRECTORY_NAME")
                }
            }

            OperatingSystem.MACOS -> {
                File(System.getProperty("user.home"), "Library/Logs/$APP_DIRECTORY_NAME")
            }

            OperatingSystem.OTHER -> configDirectory
        }
    }
    val autostartDirectory: File by lazy {
        when (Platform.current) {
            OperatingSystem.LINUX -> {
                val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
                val configHome = if (!xdgConfigHome.isNullOrBlank()) {
                    File(xdgConfigHome)
                } else {
                    File(System.getProperty("user.home"), ".config")
                }

                File(configHome, "autostart")
            }

            else -> configDirectory
        }
    }
    val logsDirectory: File by lazy {
        File(stateDirectory, "logs")
    }

    val settingsFile: File by lazy {
        File(configDirectory, "settings.json")
    }
}