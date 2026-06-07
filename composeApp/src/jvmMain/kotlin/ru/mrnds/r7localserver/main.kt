package ru.mrnds.r7localserver


import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.jetbrains.compose.resources.painterResource
import r7localserver.composeapp.generated.resources.Res
import r7localserver.composeapp.generated.resources.icon
import ru.mrnds.r7localserver.platform.AppDirectories
import ru.mrnds.r7localserver.tray.AppTray
import ru.mrnds.r7localserver.ui.App
import ru.mrnds.r7localserver.ui.widgets.TrayPanel
import ru.mrnds.r7localserver.viewmodel.AppViewModel
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

fun main() {
    val logDirectory = configureLogDirectory()
    try {
        application(exitProcessOnExit = false) {
            val viewModel = remember { AppViewModel() }
            val appIcon = painterResource(Res.drawable.icon)
            var isTrayPanelVisible by remember { mutableStateOf(false) }

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


            val tray = remember {
                AppTray(
                    onOpenMainWindow = {
                        isTrayPanelVisible = false
                        showWindow()
                    },
                    onOpenTrayPanel = {
                        isTrayPanelVisible = !isTrayPanelVisible
                    }
                )
            }

            DisposableEffect(Unit) {
                tray.install()
                onDispose {
                    tray.dispose()
                }
            }

            LaunchedEffect(windowState.isMinimized) {
                if (windowState.isMinimized) {
                    isWindowVisible = false
                    windowState.isMinimized = false
                }
            }

            if (isTrayPanelVisible) {
                Window(
                    onCloseRequest = { isTrayPanelVisible = false },
                    title = "R7 Local Server",
                    alwaysOnTop = true,
                    resizable = false,
                    undecorated = true,
                    state = rememberWindowState(
                        width = 360.dp,
                        height = 250.dp,
                        position = WindowPosition.Aligned(Alignment.BottomEnd)
                    )
                ) {
                    DisposableEffect(window) {
                        val listener = object : WindowAdapter() {
                            override fun windowLostFocus(e: WindowEvent?) {
                                isTrayPanelVisible = false
                            }
                        }

                        window.addWindowFocusListener(listener)

                        onDispose {
                            window.removeWindowFocusListener(listener)
                        }
                    }

                    TrayPanel(
                        viewModel = viewModel,
                        onOpenMainWindow = { showWindow() },
                        onClosePanel = { isTrayPanelVisible = false },
                        onExit = {
                            viewModel.shutdown()
                            exitApplication()
                        }
                    )
                }
            }


            Window(
                visible = isWindowVisible,
                onCloseRequest = {
                    isWindowVisible = false
                },
                title = "R7 Local Server",
                icon = appIcon,
                state = windowState,
            ) {
                App(viewModel)
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