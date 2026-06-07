package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
data class ExcelRawSheet(
    val name: String,
    val cells: List<ExcelRawCell>
)