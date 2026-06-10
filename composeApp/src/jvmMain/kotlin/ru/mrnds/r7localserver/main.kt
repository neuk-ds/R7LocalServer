package ru.mrnds.r7localserver


import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import r7localserver.composeapp.generated.resources.Res
import r7localserver.composeapp.generated.resources.icon
import ru.mrnds.r7localserver.platform.AppDirectories
import ru.mrnds.r7localserver.platform.Platform
import ru.mrnds.r7localserver.tray.AppTray
import ru.mrnds.r7localserver.ui.App
import ru.mrnds.r7localserver.ui.theme.AppTheme
import ru.mrnds.r7localserver.ui.widgets.TrayPanel
import ru.mrnds.r7localserver.viewmodel.AppViewModel
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
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
            val trayWindowSize = DpSize(360.dp, 240.dp)
            var trayWindowPosition by remember {
                mutableStateOf<WindowPosition>(WindowPosition.Aligned(Alignment.BottomEnd))
            }

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
                        trayWindowPosition = trayWindowPositionNearCursor(trayWindowSize)
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
                if (windowState.isMinimized && !Platform.isLinux) {
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
                    transparent = true,
                    state = rememberWindowState(
                        size = trayWindowSize,
                        position = trayWindowPosition
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
                    AppTheme(themeMode = viewModel.settings.themeMode) {

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
            }


            Window(
                visible = isWindowVisible,
                onCloseRequest = {
                    if (viewModel.serverBusy || viewModel.serverRunning) {
                        if (Platform.isLinux) {
                            windowState.isMinimized = true
                        } else {
                            isWindowVisible = false
                        }
                    } else {
                        exitApplication()
                    }
                },
                title = "R7 Local Server",
                icon = appIcon,
                state = windowState,
            ) {
                AppTheme(themeMode = viewModel.settings.themeMode) {
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

private fun trayWindowPositionNearCursor(windowSize: DpSize): WindowPosition {
    if (!Platform.isWindows) {
        return WindowPosition.Aligned(Alignment.BottomEnd)
    }

    val pointerLocation = runCatching {
        MouseInfo.getPointerInfo()?.location
    }.getOrNull() ?: return WindowPosition.Aligned(Alignment.BottomEnd)

    val screenBounds = findScreenBounds(pointerLocation.x, pointerLocation.y)
        ?: return WindowPosition.Aligned(Alignment.BottomEnd)

    val windowWidthPx = windowSize.width.value.toInt()
    val windowHeightPx = windowSize.height.value.toInt()
    val marginPx = 12

    val preferredX = pointerLocation.x - windowWidthPx
    val preferredY = pointerLocation.y - windowHeightPx

    val x = preferredX.coerceIn(
        screenBounds.x + marginPx,
        screenBounds.x + screenBounds.width - windowWidthPx - marginPx
    )

    val y = preferredY.coerceIn(
        screenBounds.y + marginPx,
        screenBounds.y + screenBounds.height - windowHeightPx - marginPx
    )

    return WindowPosition.Absolute(
        x = x.dp,
        y = y.dp
    )
}

private fun findScreenBounds(x: Int, y: Int): Rectangle? {
    return GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .screenDevices
        .map { it.defaultConfiguration.bounds }
        .firstOrNull { it.contains(x, y) }
}