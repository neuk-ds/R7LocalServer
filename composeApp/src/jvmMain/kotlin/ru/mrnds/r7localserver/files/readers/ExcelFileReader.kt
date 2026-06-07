package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellReference
import org.apache.poi.ss.util.NumberToTextConverter
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.excel.*
import java.io.File
import java.time.format.DateTimeFormatter

class ExcelFileReader : FileReader {
    private val logger = LoggerFactory.getLogger(ExcelFileReader::class.java)
    private val json = Json {
        encodeDefaults = true
    }
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    override fun read(file: File): JsonElement {
        return read(
            file = file,
            readMode = FileReadMode.DEFAULT
        )
    }

    fun read(
        file: File,
        readMode: FileReadMode
    ): JsonElement {
        return when (readMode) {
            FileReadMode.DEFAULT -> json.encodeToJsonElement(
                ExcelValuesContent(
                    sheets = readValues(file)
                )
            )

            FileReadMode.RAW -> json.encodeToJsonElement(
                ExcelRawContent(
                    sheets = readRaw(file)
                )
            )

            FileReadMode.TYPED -> json.encodeToJsonElement(
                ExcelTypedContent(
                    sheets = readTyped(file)
                )
            )
        }
    }

    fun readTyped(file: File): List<ExcelTypedSheet> {
        val workbook = openWorkbook(file)

        workbook.use { wb ->
            val evaluator = wb.creationHelper.createFormulaEvaluator()

            return wb.map { sheet ->
                val maxColumns = sheet.maxOfOrNull { row ->
                    row.lastCellNum.toInt()
                } ?: 0

                val rows = mutableListOf<List<JsonElement>>()

                for (rowIndex in sheet.firstRowNum..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex)
                    val values = mutableListOf<JsonElement>()

                    for (columnIndex in 0 until maxColumns) {
                        val cell = row?.getCell(columnIndex)

                        values.add(
                            if (cell != null) {
                                cellValueToJsonElement(cell, evaluator)
                            } else {
                                JsonPrimitive("")
                            }
                        )
                    }

                    rows.add(values)
                }

                ExcelTypedSheet(
                    name = sheet.sheetName,
                    rows = rows
                )
            }
        }
    }

    fun readValues(file: File): List<ExcelValuesSheet> {
        val workbook = openWorkbook(file)

        workbook.use { wb ->
            val evaluator = wb.creationHelper.createFormulaEvaluator()

            return wb.map { sheet ->
                val maxColumns = sheet.maxOfOrNull { row ->
                    row.lastCellNum.toInt()
                } ?: 0

                val rows = mutableListOf<List<String>>()

                for (rowIndex in sheet.firstRowNum..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex)
                    val values = mutableListOf<String>()

                    for (columnIndex in 0 until maxColumns) {
                        val cell = row?.getCell(columnIndex)

                        values.add(
                            if (cell != null) {
                                cellValueToString(cell, evaluator)
                            } else {
                                ""
                            }
                        )
                    }

                    rows.add(values)
                }

                ExcelValuesSheet(
                    name = sheet.sheetName,
                    rows = rows
                )
            }
        }
    }

    fun readRaw(file: File): List<ExcelRawSheet> {
        val workbook = openWorkbook(file)

        workbook.use { wb ->
            val evaluator = wb.creationHelper.createFormulaEvaluator()

            return wb.map { sheet ->
                val cells = mutableListOf<ExcelRawCell>()

                for (row in sheet) {
                    for (cell in row) {
                        val rawCell = cellToRawCell(
                            cell = cell,
                            evaluator = evaluator
                        )

                        if (rawCell != null) {
                            cells.add(rawCell)
                        }
                    }
                }

                ExcelRawSheet(
                    name = sheet.sheetName,
                    cells = cells
                )
            }
        }
    }

    private fun openWorkbook(file: File): Workbook {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            logger.error("Cannot read Excel file: {}", file.absolutePath, e)
            throw IllegalArgumentException("Cannot read Excel file. It may be opened or locked by another program")
        }

        return try {
            WorkbookFactory.create(bytes.inputStream())
        } catch (e: Exception) {
            logger.error("Invalid Excel file content: {}", file.absolutePath, e)
            throw IllegalArgumentException("Invalid Excel file content")
        }
    }

    private fun cellValueToString(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue

            CellType.NUMERIC -> numericCellValueToString(cell)

            CellType.BOOLEAN -> cell.booleanCellValue.toString()

            CellType.FORMULA -> formulaValueToString(
                cell = cell,
                evaluator = evaluator
            )

            CellType.BLANK -> ""

            CellType.ERROR -> ""

            else -> ""
        }
    }

    private fun numericCellValueToString(cell: Cell): String {
        return if (DateUtil.isCellDateFormatted(cell)) {
            cell.localDateTimeCellValue.format(dateTimeFormatter)
        } else {
            NumberToTextConverter.toText(cell.numericCellValue)
        }
    }

    private fun formulaValueToString(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): String {
        val evaluated = try {
            evaluator.evaluate(cell)
        } catch (e: Exception) {
            logger.debug(
                "Failed to evaluate Excel formula: sheet={}, address={}",
                cell.sheet.sheetName,
                CellReference(cell).formatAsString(false),
                e
            )
            null
        }

        if (evaluated != null) {
            return when (evaluated.cellType) {
                CellType.STRING -> evaluated.stringValue

                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.localDateTimeCellValue.format(dateTimeFormatter)
                    } else {
                        NumberToTextConverter.toText(evaluated.numberValue)
                    }
                }

                CellType.BOOLEAN -> evaluated.booleanValue.toString()

                CellType.BLANK -> ""

                CellType.ERROR -> ""

                else -> ""
            }
        }

        return cachedFormulaValueToString(cell)
    }

    private fun cachedFormulaValueToString(cell: Cell): String {
        return try {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> cell.stringCellValue

                CellType.NUMERIC -> numericCellValueToString(cell)

                CellType.BOOLEAN -> cell.booleanCellValue.toString()

                CellType.BLANK -> ""

                CellType.ERROR -> ""

                else -> ""
            }
        } catch (e: Exception) {
            logger.debug(
                "Failed to read cached Excel formula value: sheet={}, address={}",
                cell.sheet.sheetName,
                CellReference(cell).formatAsString(false),
                e
            )
            ""
        }
    }

    private fun cellToRawCell(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): ExcelRawCell? {
        if (cell.cellType == CellType.BLANK) {
            return null
        }

        val valueResult = rawCellValue(
            cell = cell,
            evaluator = evaluator
        )

        if (valueResult.value.isEmpty() && valueResult.valueState == ExcelCellValueState.BLANK) {
            return null
        }

        return ExcelRawCell(
            address = CellReference(cell).formatAsString(false),
            rowIndex = cell.rowIndex,
            columnIndex = cell.columnIndex,
            type = toExcelCellType(cell),
            value = valueResult.value,
            formula = if (cell.cellType == CellType.FORMULA) cell.cellFormula else null,
            format = cell.cellStyle?.dataFormatString,
            valueState = valueResult.valueState
        )
    }

    private fun rawCellValue(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): RawCellValueResult {
        return when (cell.cellType) {
            CellType.STRING -> RawCellValueResult(
                value = cell.stringCellValue,
                valueState = ExcelCellValueState.VALUE
            )

            CellType.NUMERIC -> RawCellValueResult(
                value = numericCellValueToString(cell),
                valueState = ExcelCellValueState.VALUE
            )

            CellType.BOOLEAN -> RawCellValueResult(
                value = cell.booleanCellValue.toString(),
                valueState = ExcelCellValueState.VALUE
            )

            CellType.FORMULA -> formulaRawValue(
                cell = cell,
                evaluator = evaluator
            )

            CellType.BLANK -> RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.BLANK
            )

            CellType.ERROR -> RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.ERROR
            )

            else -> RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.BLANK
            )
        }
    }

    private fun formulaRawValue(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): RawCellValueResult {
        val evaluated = try {
            evaluator.evaluate(cell)
        } catch (e: Exception) {
            logger.debug(
                "Failed to evaluate Excel raw formula: sheet={}, address={}",
                cell.sheet.sheetName,
                CellReference(cell).formatAsString(false),
                e
            )
            null
        }

        if (evaluated != null) {
            return evaluatedFormulaValueToRawResult(evaluated)
        }

        val cached = cachedFormulaValueToRawResult(cell)

        if (cached.value.isNotEmpty()) {
            return cached
        }

        return if (hasExternalReference(cell.cellFormula)) {
            RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.EXTERNAL_REFERENCE_NOT_AVAILABLE
            )
        } else {
            RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.FORMULA_NOT_EVALUATED
            )
        }
    }

    private fun evaluatedFormulaValueToRawResult(
        value: CellValue
    ): RawCellValueResult {
        return when (value.cellType) {
            CellType.STRING -> RawCellValueResult(
                value = value.stringValue,
                valueState = ExcelCellValueState.EVALUATED_FORMULA_VALUE
            )

            CellType.NUMERIC -> RawCellValueResult(
                value = NumberToTextConverter.toText(value.numberValue),
                valueState = ExcelCellValueState.EVALUATED_FORMULA_VALUE
            )

            CellType.BOOLEAN -> RawCellValueResult(
                value = value.booleanValue.toString(),
                valueState = ExcelCellValueState.EVALUATED_FORMULA_VALUE
            )

            CellType.BLANK -> RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.FORMULA_NOT_EVALUATED
            )

            CellType.ERROR -> RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.ERROR
            )

            else -> RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.FORMULA_NOT_EVALUATED
            )
        }
    }

    private fun cachedFormulaValueToRawResult(cell: Cell): RawCellValueResult {
        return try {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> RawCellValueResult(
                    value = cell.stringCellValue,
                    valueState = ExcelCellValueState.CACHED_FORMULA_VALUE
                )

                CellType.NUMERIC -> RawCellValueResult(
                    value = NumberToTextConverter.toText(cell.numericCellValue),
                    valueState = ExcelCellValueState.CACHED_FORMULA_VALUE
                )

                CellType.BOOLEAN -> RawCellValueResult(
                    value = cell.booleanCellValue.toString(),
                    valueState = ExcelCellValueState.CACHED_FORMULA_VALUE
                )

                CellType.BLANK -> RawCellValueResult(
                    value = "",
                    valueState = ExcelCellValueState.FORMULA_NOT_EVALUATED
                )

                CellType.ERROR -> RawCellValueResult(
                    value = "",
                    valueState = ExcelCellValueState.ERROR
                )

                else -> RawCellValueResult(
                    value = "",
                    valueState = ExcelCellValueState.FORMULA_NOT_EVALUATED
                )
            }
        } catch (e: Exception) {
            logger.debug(
                "Failed to read cached Excel raw formula value: sheet={}, address={}",
                cell.sheet.sheetName,
                CellReference(cell).formatAsString(false),
                e
            )
            RawCellValueResult(
                value = "",
                valueState = ExcelCellValueState.FORMULA_NOT_EVALUATED
            )
        }
    }

    private fun hasExternalReference(formula: String): Boolean {
        return formula.contains("[") && formula.contains("]")
    }

    private fun toExcelCellType(cell: Cell): ExcelCellType {
        return when (cell.cellType) {
            CellType.STRING -> ExcelCellType.STRING

            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    ExcelCellType.DATETIME
                } else {
                    ExcelCellType.NUMBER
                }
            }

            CellType.BOOLEAN -> ExcelCellType.BOOLEAN

            CellType.FORMULA -> ExcelCellType.FORMULA

            CellType.BLANK -> ExcelCellType.BLANK

            CellType.ERROR -> ExcelCellType.BLANK

            else -> ExcelCellType.BLANK
        }
    }

    private fun cellValueToJsonElement(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): JsonElement {
        return when (cell.cellType) {
            CellType.STRING -> JsonPrimitive(cell.stringCellValue)

            CellType.NUMERIC -> numericCellValueToJsonElement(cell)

            CellType.BOOLEAN -> JsonPrimitive(cell.booleanCellValue)

            CellType.FORMULA -> formulaValueToJsonElement(
                cell = cell,
                evaluator = evaluator
            )

            CellType.BLANK -> JsonPrimitive("")

            CellType.ERROR -> JsonPrimitive("")

            else -> JsonPrimitive("")
        }
    }

    private fun numericCellValueToJsonElement(cell: Cell): JsonElement {
        return if (DateUtil.isCellDateFormatted(cell)) {
            JsonPrimitive(cell.localDateTimeCellValue.format(dateTimeFormatter))
        } else {
            jsonNumber(NumberToTextConverter.toText(cell.numericCellValue))
        }
    }

    private fun formulaValueToJsonElement(
        cell: Cell,
        evaluator: FormulaEvaluator
    ): JsonElement {
        val evaluated = try {
            evaluator.evaluate(cell)
        } catch (e: Exception) {
            logger.debug(
                "Failed to evaluate Excel typed formula: sheet={}, address={}",
                cell.sheet.sheetName,
                CellReference(cell).formatAsString(false),
                e
            )
            null
        }

        if (evaluated != null) {
            return when (evaluated.cellType) {
                CellType.STRING -> JsonPrimitive(evaluated.stringValue)

                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        JsonPrimitive(cell.localDateTimeCellValue.format(dateTimeFormatter))
                    } else {
                        jsonNumber(NumberToTextConverter.toText(evaluated.numberValue))
                    }
                }

                CellType.BOOLEAN -> JsonPrimitive(evaluated.booleanValue)

                CellType.BLANK -> JsonPrimitive("")

                CellType.ERROR -> JsonPrimitive("")

                else -> JsonPrimitive("")
            }
        }

        return cachedFormulaValueToJsonElement(cell)
    }

    private fun cachedFormulaValueToJsonElement(cell: Cell): JsonElement {
        return try {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> JsonPrimitive(cell.stringCellValue)

                CellType.NUMERIC -> numericCellValueToJsonElement(cell)

                CellType.BOOLEAN -> JsonPrimitive(cell.booleanCellValue)

                CellType.BLANK -> JsonPrimitive("")

                CellType.ERROR -> JsonPrimitive("")

                else -> JsonPrimitive("")
            }
        } catch (e: Exception) {
            logger.debug(
                "Failed to read cached Excel typed formula value: sheet={}, address={}",
                cell.sheet.sheetName,
                CellReference(cell).formatAsString(false),
                e
            )
            JsonPrimitive("")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun jsonNumber(value: String): JsonElement {
        return JsonUnquotedLiteral(value)
    }

    private data class RawCellValueResult(
        val value: String,
        val valueState: ExcelCellValueState
    )
}