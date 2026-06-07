package ru.mrnds.r7localserver


import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import r7localserver.composeapp.generated.resources.Res
import r7localserver.composeapp.generated.resources.icon
import ru.mrnds.r7localserver.platform.AppDirectories
import ru.mrnds.r7localserver.ui.App
import ru.mrnds.r7localserver.viewmodel.AppViewModel
import java.io.File

fun main() {
    val logDirectory = configureLogDirectory()
    try {
        application {
            val viewModel = remember { AppViewModel() }
            val appIcon = painterResource(Res.drawable.icon)

            LaunchedEffect(Unit) {
                if (viewModel.settings.startServerOnAppStart) {
                    viewModel.startServer()
                }
            }

            val windowState = rememberWindowState(
                width = 1000.dp,
                height = 700.dp
            )

            var isWindowVisible by remember { mutableStateOf(!viewModel.settings.startMinimized) }

            fun showWindow() {
                isWindowVisible = true
                windowState.isMinimized = false
            }

            val serverStatusText = when {
                viewModel.serverStarting -> "Статус: запуск..."
                viewModel.serverStopping -> "Статус: остановка..."
                viewModel.serverRunning -> "Статус: запущен"
                else -> "Статус: остановлен"
            }

            val serverAddress = "Адрес: http://127.0.0.1:${viewModel.serverPort}"

            Tray(
                icon = appIcon,
                tooltip = "R7 Local Server",
                onAction = { showWindow() },
                menu = {
                    Item(
                        text = serverStatusText,
                        enabled = false,
                        onClick = {}
                    )
                    Item(
                        text = serverAddress,
                        enabled = false,
                        onClick = {}
                    )

                    Separator()

                    Item(
                        text = "Открыть",
                        onClick = { showWindow() }
                    )

                    Separator()

                    Item(
                        text = "Выход",
                        onClick = {
                            viewModel.shutdown()
                            exitApplication()
                        }
                    )
                }
            )

            LaunchedEffect(windowState.isMinimized) {
                if (windowState.isMinimized) {
                    isWindowVisible = false
                    windowState.isMinimized = false
                }
            }

            if (isWindowVisible) {
                Window(
                    onCloseRequest = {
                        if (viewModel.serverRunning || viewModel.serverBusy)
                            isWindowVisible = false
                        else exitApplication()
                    },
                    title = "R7 Local Server",
                    icon = appIcon,
                    state = windowState,
                ) {
                    App(viewModel)
                }
            }
        }
    } catch (e: Throwable) {
        File(logDirectory, "fatal.log").appendText(
            buildString {
                appendLine("Fatal error at ${java.time.LocalDateTime.now()}")
                appendLine(e.stackTraceToString())
                appendLine()
            }
        )
        throw e
    }
}

private fun configureLogDirectory(): File {
    val logDirectory = AppDirectories.logsDirectory
    logDirectory.mkdirs()

    System.setProperty("r7.log.dir", logDirectory.absolutePath)

    return logDirectory
}