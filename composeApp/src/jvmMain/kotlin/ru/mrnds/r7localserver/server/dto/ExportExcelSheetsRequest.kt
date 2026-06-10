package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExportExcelSheetsRequest(
    val sourceDirectoryPath: String,
    val sourceFileName: String,
    val targetDirectoryPath: String,
    val targetFileName: String,
    val sheetNames: List<String> = emptyList(),
    val overwrite: Boolean = false,
)
