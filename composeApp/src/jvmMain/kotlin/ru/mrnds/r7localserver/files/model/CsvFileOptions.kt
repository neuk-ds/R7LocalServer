package ru.mrnds.r7localserver.files.model

import kotlinx.serialization.Serializable

@Serializable
data class CsvFileOptions(
    val delimiter: String = ";",
    val lineSeparator: String = "\n",
    val table: List<List<String>>
)
