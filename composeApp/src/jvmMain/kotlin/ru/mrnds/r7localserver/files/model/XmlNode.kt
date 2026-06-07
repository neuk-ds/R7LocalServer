package ru.mrnds.r7localserver.files.model

import kotlinx.serialization.Serializable

@Serializable
data class XmlNode(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val text: String? = null,
    val children: List<XmlNode> = emptyList(),
)
