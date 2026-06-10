package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExportExcelSheetsResponse(
    val targetDirectoryPath: String,
    val targetFileName: String,
    val exportedSheetNames: List<String>,
    val replacedFormulaCount: Int,
)
