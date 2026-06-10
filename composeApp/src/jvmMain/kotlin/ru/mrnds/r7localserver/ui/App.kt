package ru.mrnds.r7localserver.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ru.mrnds.r7localserver.AppBuildInfo
import ru.mrnds.r7localserver.ui.screens.DocumentationScreen
import ru.mrnds.r7localserver.ui.screens.LogsScreen
import ru.mrnds.r7localserver.ui.screens.SettingsScreen
import ru.mrnds.r7localserver.ui.widgets.ServerStatusBar
import ru.mrnds.r7localserver.viewmodel.AppViewModel

@Composable
fun App(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(AppTab.SETTINGS) }

    val serverAddress = "http://127.0.0.1:${viewModel.serverPort}"
    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal
            ) {
                AppTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                        },
                        text = {
                            Text(tab.title)
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    AppTab.SETTINGS -> SettingsScreen(
                        serverPort = viewModel.serverPort,
                        isServerRunning = viewModel.serverRunning || viewModel.serverBusy,
                        startWithSystem = viewModel.settings.startWithSystem,
                        startServerOnAppStart = viewModel.settings.startServerOnAppStart,
                        startMinimized = viewModel.settings.startMinimized,
                        appVersion = AppBuildInfo.VERSION,
                        onServerPortChange = { viewModel.updateServerPort(it) },
                        themeMode = viewModel.settings.themeMode,
                        onStartWithSystemChange = { viewModel.updateStartWithSystem(it) },
                        onStartServerOnAppStartChange = { viewModel.updateStartServerOnAppStart(it) },
                        onStartMinimizedChange = { viewModel.updateStartMinimized(it) },
                        onThemeModeChange = { viewModel.updateThemeMode(it) },
                    )

                    AppTab.DOCUMENTATION -> DocumentationScreen(
                        baseUrl = serverAddress
                    )

                    AppTab.LOGS -> LogsScreen(
                        logDirectoryPath = viewModel.logDirectoryPath,
                        onOpenLogDirectory = { viewModel.openLogDirectory() })
                }
            }

            ServerStatusBar(
                address = serverAddress,
                isStarting = viewModel.serverStarting,
                isStopping = viewModel.serverStopping,
                isRunning = viewModel.serverRunning,
                errorMessage = viewModel.errorMessage,
                onStartClick = { viewModel.startServer() },
                onStopClick = { viewModel.stopServer() }
            )
        }
    }
}


private enum class AppTab(
    val title: String
) {
    SETTINGS("Настройки"),
    DOCUMENTATION("Справка"),
    LOGS("Логи")
}