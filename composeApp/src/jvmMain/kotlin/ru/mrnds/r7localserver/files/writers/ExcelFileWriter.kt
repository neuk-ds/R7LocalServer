package ru.mrnds.r7localserver.files.writers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.excel.ExcelCellType
import ru.mrnds.r7localserver.files.model.excel.ExcelRawCell
import ru.mrnds.r7localserver.files.model.excel.ExcelRawContent
import java.io.File
import java.io.FileOutputStream

class ExcelFileWriter : FileWriter {
    private val logger = LoggerFactory.getLogger(ExcelFileWriter::class.java)
    val json = Json {
        ignoreUnknownKeys = true
    }

    override fun write(file: File, content: JsonElement): File {
        val options = try {
            json.decodeFromJsonElement<ExcelRawContent>(content)
        } catch (e: Exception) {
            logger.error("Invalid Excel options JSON", e)
            throw IllegalArgumentException("Invalid Excel options JSON")
        }

        require(options.sheets.isNotEmpty()) { "Excel sheets must not be empty" }
        XSSFWorkbook().use { workbook ->
            options.sheets.forEach { sheetOptions ->
                require(sheetOptions.name.isNotBlank()) {
                    "Sheet name must not be empty"
                }

                val sheet = workbook.createSheet(sheetOptions.name)
                val rows = mutableMapOf<Int, Row>()

                sheetOptions.cells.forEach { rawCell ->
                    val row = rows.getOrPut(rawCell.rowIndex) {
                        sheet.createRow(rawCell.rowIndex)
                    }

                    val cell = row.createCell(rawCell.columnIndex)

                    writeRawCell(
                        cell = cell,
                        rawCell = rawCell
                    )
                }

                val maxColumnIndex = sheetOptions.cells.maxOfOrNull { it.columnIndex } ?: -1

                for (columnIndex in 0..maxColumnIndex) {
                    sheet.autoSizeColumn(columnIndex)
                }
            }
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
        }
        return file
    }

    private fun writeRawCell(
        cell: Cell,
        rawCell: ExcelRawCell
    ) {
        when (rawCell.type) {
            ExcelCellType.STRING -> {
                cell.setCellValue(rawCell.value)
            }

            ExcelCellType.NUMBER -> {
                val number = rawCell.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid number value at ${rawCell.address}: ${rawCell.value}")
                cell.setCellValue(number)
            }

            ExcelCellType.BOOLEAN -> {
                val boolean = rawCell.value.toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("Invalid boolean value at ${rawCell.address}: ${rawCell.value}")

                cell.setCellValue(boolean)
            }

            ExcelCellType.FORMULA -> {
                val formula = rawCell.formula ?: rawCell.value

                require(formula.isNotBlank()) {
                    "Formula must not be empty at ${rawCell.address}"
                }

                cell.cellFormula = formula
            }

            ExcelCellType.DATETIME -> {
                cell.setCellValue(rawCell.value)
            }

            ExcelCellType.BLANK -> {
                cell.setBlank()
            }
        }
    }
}