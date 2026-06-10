package ru.mrnds.r7localserver.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ServerStatusBar(
    address: String,
    isRunning: Boolean,
    isStopping: Boolean = false,
    isStarting: Boolean = false,
    errorMessage: String?,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val busy = isStarting || isStopping
    val statusText = when {
        isStarting -> "Запускается"
        isStopping -> "Останавливается"
        isRunning -> "Запущен"
        else -> "Остановлен"
    }
    Column {
        HorizontalDivider(
            modifier = Modifier,
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 16.dp,
                alignment = Alignment.Start,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            } ?: Spacer(
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = !busy,
                onClick = {
                    if (isRunning) {
                        onStopClick()
                    } else {
                        onStartClick()
                    }
                },
                colors = if (isRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = when {
                        isRunning -> "Остановить"
                        else -> "Запустить"
                    }
                )
            }
        }
    }
}

@Composable
@Preview
private fun PreviewServerStatusBar() {
    MaterialTheme {
        ServerStatusBar(
            address = "127.0.0.1:8124",
            isRunning = true,
            errorMessage = null,
            onStartClick = {},
            onStopClick = {},
        )
    }
}