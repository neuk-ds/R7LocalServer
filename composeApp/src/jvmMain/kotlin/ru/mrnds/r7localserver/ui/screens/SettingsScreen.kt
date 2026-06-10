package ru.mrnds.r7localserver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import ru.mrnds.r7localserver.settings.AppThemeMode
import ru.mrnds.r7localserver.ui.widgets.ThemeModeDropdown

@Composable
fun SettingsScreen(
    serverPort: String,
    isServerRunning: Boolean,
    startWithSystem: Boolean,
    startServerOnAppStart: Boolean,
    startMinimized: Boolean,
    appVersion: String,
    themeMode: AppThemeMode,
    onServerPortChange: (String) -> Unit,
    onStartWithSystemChange: (Boolean) -> Unit,
    onStartServerOnAppStartChange: (Boolean) -> Unit,
    onStartMinimizedChange: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleLarge,
            )
            Row {
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = onServerPortChange,
                    label = {
                        Text("Порт сервера")
                    },
                    enabled = !isServerRunning,
                    singleLine = true,
                    modifier = Modifier.widthIn(
                        min = 220.dp,
                        max = 320.dp
                    )
                )

                Text(
                    text = if (isServerRunning) {
                        "Чтобы изменить порт, сначала остановите сервер."
                    } else {
                        "Порт будет использован при следующем запуске сервера"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(all = 16.dp)
                )
            }

            SettingsSwitch(
                text = "Запускать при старте системы",
                checked = startWithSystem,
                onCheckedChange = onStartWithSystemChange
            )

            SettingsSwitch(
                text = "Запускать сервер при старте приложения",
                checked = startServerOnAppStart,
                onCheckedChange = onStartServerOnAppStartChange
            )

            SettingsSwitch(
                text = "Запускать приложение свёрнутым",
                checked = startMinimized,
                onCheckedChange = onStartMinimizedChange
            )

            ThemeModeDropdown(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
        Text(
            text = "Версия: $appVersion",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 16.dp).align(Alignment.BottomEnd)
        )
    }
}

@Composable
fun SettingsSwitch(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.widthIn(min = 320.dp, max = 480.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 1.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}