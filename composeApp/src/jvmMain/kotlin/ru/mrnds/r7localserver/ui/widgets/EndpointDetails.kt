package ru.mrnds.r7localserver.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

@Composable
fun EndpointDetails(
    doc: EndpointDoc,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = doc.title,
            style = MaterialTheme.typography.titleLarge
        )

        if (doc.method != null && doc.path != null) {

            Text(
                text = "${doc.method} ${doc.path}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            SelectionContainer {
                Text(
                    text = "$baseUrl${doc.path}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            text = doc.description,
            style = MaterialTheme.typography.bodyMedium
        )

        doc.requestExample?.let { requestExample ->
            SectionTitle("Запрос")
            CodeBlock(requestExample)
        }

        doc.responseExample?.let { responseExample ->
            SectionTitle("Ответ")
            CodeBlock(responseExample)
        }

        if (doc.notes.isNotEmpty()) {
            SectionTitle("Примечания")
            SelectionContainer {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    doc.notes.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
    }
}

@Composable
private fun SectionTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}