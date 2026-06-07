package ru.mrnds.r7localserver.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mrnds.r7localserver.ui.documentation.model.DocumentationGroup
import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

@Composable
fun EndpointList(
    groups: List<DocumentationGroup>,
    selectedDoc: EndpointDoc,
    onDocSelected: (EndpointDoc) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        groups.forEach { group ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                group.items.forEach { doc ->
                    val selected = doc.id == selectedDoc.id

                    Text(
                        text = doc.method?.let { method ->
                            "$method ${doc.path}"
                        } ?: doc.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDocSelected(doc)
                            }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            )
                    )
                }
            }
        }
    }
}