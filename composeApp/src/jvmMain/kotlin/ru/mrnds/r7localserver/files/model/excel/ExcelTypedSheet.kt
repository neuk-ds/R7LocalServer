package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ExcelTypedSheet(
    val name: String,
    val rows: List<List<JsonElement>>
)
