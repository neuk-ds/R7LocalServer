package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
data class ExcelTypedContent(
    val sheets: List<ExcelTypedSheet>
)
