package ru.mrnds.r7localserver.files.exporter

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import ru.mrnds.r7localserver.server.dto.ExportExcelSheetsRequest
import ru.mrnds.r7localserver.server.dto.ExportExcelSheetsResponse
import java.io.File

class ExcelSheetExporter {

    fun exportSheets(request: ExportExcelSheetsRequest): ExportExcelSheetsResponse {
        validateXlsxFileName(request.sourceFileName, "sourceFileName")
        validateXlsxFileName(request.targetFileName, "targetFileName")

        val sourceFile = File(request.sourceDirectoryPath, request.sourceFileName)
        val targetFile = File(request.targetDirectoryPath, request.targetFileName)

        require(sourceFile.exists()) {
            "Source file does not exist: ${sourceFile.absolutePath}"
        }

        require(sourceFile.isFile) {
            "Source path is not a file: ${sourceFile.absolutePath}"
        }

        require(request.overwrite || !targetFile.exists()) {
            "Target file already exists: ${targetFile.absolutePath}"
        }

        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)

        val workbook = targetFile.inputStream().use { input ->
            WorkbookFactory.create(input) as XSSFWorkbook
        }

        val sheetNamesToKeep: Set<String>
        val replacedFormulaCount: Int

        workbook.use {
            sheetNamesToKeep = resolveSheetNamesToKeep(workbook, request.sheetNames)

            replacedFormulaCount = replaceUnsafeFormulas(
                workbook = workbook,
                sheetNamesToKeep = sheetNamesToKeep
            )

            removeOtherSheets(
                workbook = workbook,
                sheetNamesToKeep = sheetNamesToKeep
            )

            targetFile.outputStream().use { output ->
                workbook.write(output)
            }
        }

        return ExportExcelSheetsResponse(
            targetDirectoryPath = request.targetDirectoryPath,
            targetFileName = request.targetFileName,
            exportedSheetNames = sheetNamesToKeep.toList(),
            replacedFormulaCount = replacedFormulaCount
        )
    }

    private fun validateXlsxFileName(fileName: String, fieldName: String) {
        require(fileName.endsWith(".xlsx", ignoreCase = true)) {
            "$fieldName must have .xlsx extension"
        }
    }

    private fun resolveSheetNamesToKeep(
        workbook: XSSFWorkbook,
        requestedSheetNames: List<String>
    ): Set<String> {
        val workbookSheetNames = (0 until workbook.numberOfSheets)
            .map { workbook.getSheetName(it) }

        if (requestedSheetNames.isEmpty()) {
            return workbookSheetNames.toSet()
        }

        val missingSheetNames = requestedSheetNames.filter { it !in workbookSheetNames }

        require(missingSheetNames.isEmpty()) {
            "Sheets not found: ${missingSheetNames.joinToString(", ")}"
        }

        return requestedSheetNames.toSet()
    }

    private fun replaceUnsafeFormulas(
        workbook: XSSFWorkbook,
        sheetNamesToKeep: Set<String>
    ): Int {
        val evaluator = workbook.creationHelper.createFormulaEvaluator()
        var replacedCount = 0

        sheetNamesToKeep.forEach { sheetName ->
            val sheet = workbook.getSheet(sheetName) ?: return@forEach

            for (row in sheet) {
                for (cell in row) {
                    if (cell.cellType == CellType.FORMULA && isUnsafeFormula(cell.cellFormula, sheetNamesToKeep)) {
                        val replaced = replaceFormulaWithCachedValue(cell)

                        if (!replaced) {
                            runCatching {
                                val evaluatedValue = evaluator.evaluate(cell)
                                replaceFormulaWithEvaluatedValue(cell, evaluatedValue)
                            }.onSuccess {
                                replacedCount++
                            }
                        } else {
                            replacedCount++
                        }
                    }
                }
            }
        }

        return replacedCount
    }

    private fun hasExternalWorkbookReference(formula: String): Boolean {
        return Regex("""\[[^]]+\.(xlsx|xlsm|xls|xlsb)]""", RegexOption.IGNORE_CASE)
            .containsMatchIn(formula)
    }

    private fun isUnsafeFormula(
        formula: String,
        sheetNamesToKeep: Set<String>
    ): Boolean {
        return hasExternalWorkbookReference(formula) || findReferencedSheetNames(formula)
            .any { referencedSheetName -> referencedSheetName !in sheetNamesToKeep }
    }

    private fun findReferencedSheetNames(formula: String): Set<String> {
        val quotedSheetRegex = Regex("'([^']+)'!")
        val unquotedSheetRegex = Regex("""(?<![A-Za-z0-9_])([A-Za-zА-Яа-яЁё0-9_ ]+)!""")

        val quotedNames = quotedSheetRegex.findAll(formula)
            .map { it.groupValues[1] }

        val unquotedNames = unquotedSheetRegex.findAll(formula)
            .map { it.groupValues[1].trim() }

        return (quotedNames + unquotedNames).toSet()
    }

    private fun replaceFormulaWithCachedValue(cell: Cell): Boolean {
        return when (cell.cachedFormulaResultType) {
            CellType.NUMERIC -> {
                val value = cell.numericCellValue
                cell.setBlank()
                cell.setCellValue(value)
                true
            }

            CellType.STRING -> {
                val value = cell.stringCellValue
                cell.setBlank()
                cell.setCellValue(value)
                true
            }

            CellType.BOOLEAN -> {
                val value = cell.booleanCellValue
                cell.setBlank()
                cell.setCellValue(value)
                true
            }

            CellType.ERROR -> {
                val value = cell.errorCellValue
                cell.setBlank()
                cell.setCellErrorValue(value)
                true
            }

            else -> false
        }
    }

    private fun replaceFormulaWithEvaluatedValue(
        cell: Cell,
        value: org.apache.poi.ss.usermodel.CellValue?
    ) {
        if (value == null) {
            return
        }

        when (value.cellType) {
            CellType.NUMERIC -> {
                cell.setBlank()
                cell.setCellValue(value.numberValue)
            }

            CellType.STRING -> {
                cell.setBlank()
                cell.setCellValue(value.stringValue)
            }

            CellType.BOOLEAN -> {
                cell.setBlank()
                cell.setCellValue(value.booleanValue)
            }

            CellType.ERROR -> {
                cell.setBlank()
                cell.setCellErrorValue(value.errorValue)
            }

            else -> Unit
        }
    }

    private fun removeOtherSheets(
        workbook: XSSFWorkbook,
        sheetNamesToKeep: Set<String>
    ) {
        for (index in workbook.numberOfSheets - 1 downTo 0) {
            val sheetName = workbook.getSheetName(index)

            if (sheetName !in sheetNamesToKeep) {
                workbook.removeSheetAt(index)
            }
        }
    }
}