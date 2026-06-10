package ru.mrnds.r7localserver.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ru.mrnds.r7localserver.logging.UiLogEntry
import ru.mrnds.r7localserver.logging.UiLogLevel
import ru.mrnds.r7localserver.logging.UiLogStore

@Composable
fun LogsScreen(logDirectoryPath: String, onOpenLogDirectory: () -> Unit) {
    val lines by UiLogStore.lines.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Файл логов: $logDirectoryPath",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 5.dp, start = 16.dp, end = 16.dp)
                .clickable { onOpenLogDirectory() }
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            text = "Логи пока пустые",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )
                    } else {
                        lines.forEach { entry ->
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = logColor(entry),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun logColor(entry: UiLogEntry): Color {
    return when (entry.level) {
        UiLogLevel.ERROR -> MaterialTheme.colorScheme.error
        UiLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        UiLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        UiLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        UiLogLevel.TRACE -> MaterialTheme.colorScheme.outline
    }
}