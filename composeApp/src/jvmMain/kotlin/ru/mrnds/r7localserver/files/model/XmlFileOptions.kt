package ru.mrnds.r7localserver.files.model

import kotlinx.serialization.Serializable

@Serializable
data class XmlFileOptions(
    val root: XmlNode,
    val lineSeparator: String = "\n",
    val indent: String = "\t"
)
