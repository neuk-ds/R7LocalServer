package ru.mrnds.r7localserver.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mrnds.r7localserver.ui.documentation.documentationGroups
import ru.mrnds.r7localserver.ui.widgets.EndpointDetails
import ru.mrnds.r7localserver.ui.widgets.EndpointList

@Composable
fun DocumentationScreen(
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    val docs = documentationGroups.flatMap { it.items }
    var selectedDoc by remember {
        mutableStateOf(docs.first())
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EndpointList(
            groups = documentationGroups,
            selectedDoc = selectedDoc,
            onDocSelected = { doc ->
                selectedDoc = doc
            },
            modifier = Modifier
                .width(240.dp)
                .padding(horizontal = 16.dp)
        )

        HorizontalDivider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxSize(),

            thickness = DividerDefaults.Thickness,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {

            EndpointDetails(
                doc = selectedDoc,
                baseUrl = baseUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            )

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}