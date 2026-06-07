package ru.mrnds.r7localserver.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.platform.fileOpener.createFileOpener
import ru.mrnds.r7localserver.server.LocalServer
import ru.mrnds.r7localserver.server.ServerConfig
import ru.mrnds.r7localserver.settings.AppSettings
import ru.mrnds.r7localserver.settings.AppSettingsRepository
import ru.mrnds.r7localserver.settings.autoStart.createAutoStart
import java.awt.Desktop
import java.io.File

class AppViewModel {
    private val logger = LoggerFactory.getLogger(AppViewModel::class.java)
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val settingsRepository = AppSettingsRepository()
    private val autoStart = createAutoStart()
    private val fileOpener = createFileOpener()
    var settings by mutableStateOf(settingsRepository.load())
        private set

    init {
        logger.info("Application started")
        syncStartWithSystem()
    }

    private var server: LocalServer? = null

    var serverStopping by mutableStateOf(false)
        private set
    var serverStarting by mutableStateOf(false)
        private set
    val serverBusy: Boolean
        get() = serverStarting || serverStopping
    var serverRunning by mutableStateOf(false)
        private set
    var serverPort by mutableStateOf(settings.serverPort)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    val logDirectoryPath: String
        get() {
            val logDirectory = System.getProperty("r7.log.dir") ?: "./logs"
            return java.io.File(logDirectory).absolutePath
        }

    fun startServer() {
        if (serverRunning || serverBusy) {
            return
        }

        val port = serverPort.toIntOrNull()
        if (port == null || port !in 1..65535) {
            errorMessage = "Порт должен быть числом от 1 до 65535"
            return
        }

        serverStarting = true
        errorMessage = null

        viewModelScope.launch(Dispatchers.IO) {
            val newServer = LocalServer(config = ServerConfig(port = port))

            try {
                logger.info("Starting local server on port {}", port)
                newServer.start()

                launch(Dispatchers.Swing) {
                    server = newServer
                    serverRunning = true
                    serverStarting = false
                    errorMessage = null
                }
            } catch (e: Exception) {
                logger.error("Local server start failed on port {}", port, e)
                try {
                    newServer.stop()
                } catch (_: Exception) {
                }

                launch(Dispatchers.Swing) {
                    errorMessage = e.message ?: "Failed to start server"
                    serverRunning = false
                    serverStarting = false
                }
            }
        }
    }

    fun stopServer() {
        if (!serverRunning || serverBusy) {
            return
        }

        val currentServer = server ?: return

        serverStopping = true
        errorMessage = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentServer.stop()

                launch(Dispatchers.Swing) {
                    if (server == currentServer) {
                        server = null
                        serverRunning = false
                    }

                    serverStopping = false
                }
            } catch (e: Exception) {
                logger.error("Local server stop failed", e)
                launch(Dispatchers.Swing) {
                    errorMessage = e.message ?: "Failed to stop server"
                    serverStopping = false
                }
            }
        }
    }

    fun updateServerPort(port: String) {
        if (serverRunning || serverBusy) {
            errorMessage = "Перед сменой порта нужно остановить сервер"
            return
        }

        serverPort = port
        saveSettings(settings.copy(serverPort = port))
        logger.info("Server port changed: {}", port)
        errorMessage = null
    }

    fun updateStartWithSystem(enabled: Boolean) {
        try {
            autoStart.setEnabled(enabled)
            saveSettings(settings.copy(startWithSystem = enabled))
            logger.info("Start with system changed: {}", enabled)
            errorMessage = null
        } catch (e: Exception) {
            logger.error("Failed to change system autostart: {}", enabled, e)
            errorMessage = e.message ?: "Не удалось изменить автозапуск в системе"
        }
    }

    fun updateStartServerOnAppStart(enabled: Boolean) {
        saveSettings(settings.copy(startServerOnAppStart = enabled))
        logger.info("Start server on app start changed: {}", enabled)
    }

    fun updateStartMinimized(enabled: Boolean) {
        saveSettings(settings.copy(startMinimized = enabled))
        logger.info("Start minimized changed: {}", enabled)
    }

    fun shutdown() {
        server?.stop()
        server = null
        serverRunning = false
    }

    fun openLogDirectory() {
        try {
            val directory = File(logDirectoryPath)
            directory.mkdirs()

            fileOpener.open(directory)
            logger.info("Log directory opened: {}", directory.absolutePath)
        } catch (e: Exception) {
            logger.error("Failed to open log directory", e)
            errorMessage = e.message ?: "Не удалось открыть папку логов"
        }
    }

    private fun saveSettings(newSettings: AppSettings) {
        settings = newSettings
        try {
            settingsRepository.save(newSettings)
        } catch (e: Exception) {
            logger.error("Failed to save application settings", e)
            errorMessage = e.message ?: "Не удалось сохранить настройки"
        }
    }

    private fun syncStartWithSystem() {
        val actualStartWithSystem = autoStart.isEnabled()

        if (settings.startWithSystem != actualStartWithSystem) {
            logger.info(
                "Start with system synchronized: settings={}, actual={}",
                settings.startWithSystem,
                actualStartWithSystem
            )
            saveSettings(
                settings.copy(
                    startWithSystem = actualStartWithSystem
                )
            )
        }
    }
}