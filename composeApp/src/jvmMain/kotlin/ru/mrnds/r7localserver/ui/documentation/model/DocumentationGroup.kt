package ru.mrnds.r7localserver.ui.documentation.model

data class DocumentationGroup(
    val title: String,
    val items: List<EndpointDoc>,
)
