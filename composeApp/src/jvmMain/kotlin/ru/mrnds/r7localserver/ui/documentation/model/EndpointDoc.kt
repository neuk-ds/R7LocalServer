package ru.mrnds.r7localserver.ui.documentation.model

data class EndpointDoc(
    val id: String,
    val title: String,
    val method: String? = null,
    val path: String? = null,
    val description: String,
    val requestExample: String? = null,
    val responseExample: String? = null,
    val notes: List<String> = emptyList()
)
