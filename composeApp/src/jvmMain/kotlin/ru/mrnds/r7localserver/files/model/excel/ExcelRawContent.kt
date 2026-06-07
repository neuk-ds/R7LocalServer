package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
data class ExcelRawContent(
    val sheets: List<ExcelRawSheet>
)