package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
data class ExcelValuesSheet(
    val name: String,
    val rows: List<List<String>>
)