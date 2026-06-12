package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import ru.mrnds.r7localserver.files.model.excel.ExcelRawSheet

@Serializable
data class ExportExcelSheetsRequest(
    val sourceDirectoryPath: String,
    val sourceFileName: String,
    val targetDirectoryPath: String,
    val targetFileName: String,
    val overwrite: Boolean = false,
    val sheets: List<ExcelRawSheet>,
)