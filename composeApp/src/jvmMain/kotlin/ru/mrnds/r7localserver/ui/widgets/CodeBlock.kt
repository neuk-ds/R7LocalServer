package ru.mrnds.r7localserver.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mrnds.r7localserver.settings.AppThemeMode
import ru.mrnds.r7localserver.ui.theme.AppTheme
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500.milliseconds)
            copied = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
            .padding(12.dp)
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .align(Alignment.TopStart)
            )
        }
        TextButton(
            modifier = Modifier
                .align(Alignment.TopEnd),
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            onClick = {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(StringSelection(text)))
                    copied = true
                }
            }
        ) {
            Text(
                text = if (copied) "Скопировано" else "Копировать",
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
fun CodeBlockPreview() {
    AppTheme(AppThemeMode.DARK) {
        CodeBlock(text = "Hello world")
    }
}