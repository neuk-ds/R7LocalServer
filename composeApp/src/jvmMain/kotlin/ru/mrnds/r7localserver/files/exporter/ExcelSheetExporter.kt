package ru.mrnds.r7localserver.files.exporter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import ru.mrnds.r7localserver.files.model.excel.ExcelRawContent
import ru.mrnds.r7localserver.files.writers.ExcelFileWriter
import ru.mrnds.r7localserver.server.dto.ExportExcelSheetsRequest
import ru.mrnds.r7localserver.server.dto.ExportExcelSheetsResponse
import java.io.File

class ExcelSheetExporter {
    private val fileWriter = ExcelFileWriter()
    private val styleCopier = ExcelStyleCopier()
    private val json = Json { encodeDefaults = true }

    fun exportSheets(request: ExportExcelSheetsRequest): ExportExcelSheetsResponse {
        validateXlsxFileName(request.sourceFileName, "sourceFileName")
        validateXlsxFileName(request.targetFileName, "targetFileName")

        val sourceFile = File(request.sourceDirectoryPath, request.sourceFileName)
        val targetFile = File(request.targetDirectoryPath, request.targetFileName)

        require(sourceFile.exists()) { "Source file does not exist: ${sourceFile.absolutePath}" }
        require(sourceFile.isFile) { "Source path is not a file: ${sourceFile.absolutePath}" }
        require(request.overwrite || !targetFile.exists()) { "Target file already exists: ${targetFile.absolutePath}" }
        require(request.sheets.isNotEmpty()) { "Sheets must not be empty" }

        targetFile.parentFile?.mkdirs()

        fileWriter.write(
            file = targetFile,
            content = json.encodeToJsonElement(ExcelRawContent(sheets = request.sheets))
        )

        val sheetNames = request.sheets.map { it.name }

        styleCopier.copyStyles(
            sourceFile = sourceFile,
            targetFile = targetFile,
            sheetNames = sheetNames
        )

        return ExportExcelSheetsResponse(
            targetDirectoryPath = request.targetDirectoryPath,
            targetFileName = request.targetFileName,
            exportedSheetNames = sheetNames,
        )
    }

    private fun validateXlsxFileName(fileName: String, fieldName: String) {
        require(fileName.endsWith(".xlsx", ignoreCase = true)) {
            "$fieldName must have .xlsx extension"
        }
    }
}