package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.json.*
import org.apache.poi.ss.usermodel.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.excel.*
import java.io.File

class ExcelFileReader : FileReader {
    private val logger = LoggerFactory.getLogger(ExcelFileReader::class.java)
    private val json = Json { encodeDefaults = true }
    private val converter = ExcelCellConverter()

    override fun read(file: File): JsonElement = read(file, FileReadMode.DEFAULT)

    fun read(file: File, readMode: FileReadMode): JsonElement {
        return when (readMode) {
            FileReadMode.DEFAULT -> json.encodeToJsonElement(ExcelValuesContent(sheets = readValues(file)))
            FileReadMode.RAW -> json.encodeToJsonElement(ExcelRawContent(sheets = readRaw(file)))
            FileReadMode.TYPED -> json.encodeToJsonElement(ExcelTypedContent(sheets = readTyped(file)))
        }
    }

    fun readValues(file: File): List<ExcelValuesSheet> {
        openWorkbook(file).use { wb ->
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            return wb.map { sheet ->
                val maxColumns = sheet.maxOfOrNull { it.lastCellNum.toInt() } ?: 0
                val rows = (sheet.firstRowNum..sheet.lastRowNum).map { rowIndex ->
                    val row = sheet.getRow(rowIndex)
                    (0 until maxColumns).map { col ->
                        row?.getCell(col)?.let { converter.cellToString(it, evaluator) } ?: ""
                    }
                }
                ExcelValuesSheet(name = sheet.sheetName, rows = rows)
            }
        }
    }

    fun readTyped(file: File): List<ExcelTypedSheet> {
        openWorkbook(file).use { wb ->
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            return wb.map { sheet ->
                val maxColumns = sheet.maxOfOrNull { it.lastCellNum.toInt() } ?: 0
                val rows = (sheet.firstRowNum..sheet.lastRowNum).map { rowIndex ->
                    val row = sheet.getRow(rowIndex)
                    (0 until maxColumns).map { col ->
                        row?.getCell(col)?.let { converter.cellToJsonElement(it, evaluator) } ?: JsonPrimitive("")
                    }
                }
                ExcelTypedSheet(name = sheet.sheetName, rows = rows)
            }
        }
    }

    fun readRaw(file: File): List<ExcelRawSheet> {
        openWorkbook(file).use { wb ->
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            return wb.map { sheet ->
                val cells = sheet.flatMap { row ->
                    row.mapNotNull { cell -> converter.cellToRawCell(cell, evaluator) }
                }
                ExcelRawSheet(name = sheet.sheetName, cells = cells)
            }
        }
    }

    private fun openWorkbook(file: File): Workbook {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            logger.error("Cannot read Excel file: {}", file.absolutePath, e)
            throw IllegalArgumentException("Cannot read Excel file. It may be opened or locked by another program", e)
        }
        return try {
            WorkbookFactory.create(bytes.inputStream())
        } catch (e: Exception) {
            logger.error("Invalid Excel file content: {}", file.absolutePath, e)
            throw IllegalArgumentException("Invalid Excel file content", e)
        }
    }
}