package ru.mrnds.r7localserver.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mrnds.r7localserver.viewmodel.AppViewModel

@Composable
fun TrayPanel(
    viewModel: AppViewModel,
    onOpenMainWindow: () -> Unit,
    onClosePanel: () -> Unit,
    onExit: () -> Unit
) {
    val statusText = when {
        viewModel.serverStarting -> "Статус: запуск..."
        viewModel.serverStopping -> "Статус: остановка..."
        viewModel.serverRunning -> "Статус: запущен"
        else -> "Статус: остановлен"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "R7 Local Server",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "http://127.0.0.1:${viewModel.serverPort}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onOpenMainWindow()
                    onClosePanel()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Открыть")
            }

            OutlinedButton(
                onClick = {
                    if (viewModel.serverRunning) {
                        viewModel.stopServer()
                    } else {
                        viewModel.startServer()
                    }
                },
                enabled = !viewModel.serverBusy,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (viewModel.serverRunning) "Стоп" else "Старт")
            }
        }

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выход")
        }
    }
}