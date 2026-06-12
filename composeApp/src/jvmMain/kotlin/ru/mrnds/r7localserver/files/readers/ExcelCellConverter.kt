package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellReference
import org.apache.poi.ss.util.NumberToTextConverter
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.excel.*
import java.time.format.DateTimeFormatter

internal class ExcelCellConverter {
    private val logger = LoggerFactory.getLogger(ExcelCellConverter::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun cellToString(cell: Cell, evaluator: FormulaEvaluator): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> numericToString(cell)
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> formulaToString(cell, evaluator)
            CellType.BLANK, CellType.ERROR -> ""
            else -> ""
        }
    }

    fun cellToJsonElement(cell: Cell, evaluator: FormulaEvaluator): JsonElement {
        return when (cell.cellType) {
            CellType.STRING -> JsonPrimitive(cell.stringCellValue)
            CellType.NUMERIC -> numericToJsonElement(cell)
            CellType.BOOLEAN -> JsonPrimitive(cell.booleanCellValue)
            CellType.FORMULA -> formulaToJsonElement(cell, evaluator)
            CellType.BLANK, CellType.ERROR -> JsonPrimitive("")
            else -> JsonPrimitive("")
        }
    }

    fun cellToRawCell(cell: Cell, evaluator: FormulaEvaluator): ExcelRawCell? {
        if (cell.cellType == CellType.BLANK) return null

        val result = rawCellValue(cell, evaluator)
        if (result.value.isEmpty() && result.valueState == ExcelCellValueState.BLANK) return null

        return ExcelRawCell(
            address = CellReference(cell).formatAsString(false),
            rowIndex = cell.rowIndex,
            columnIndex = cell.columnIndex,
            type = toCellType(cell),
            value = result.value,
            formula = if (cell.cellType == CellType.FORMULA) cell.cellFormula else null,
            format = cell.cellStyle?.dataFormatString,
            valueState = result.valueState
        )
    }

    private fun numericToString(cell: Cell): String {
        return if (DateUtil.isCellDateFormatted(cell)) {
            cell.localDateTimeCellValue.format(dateTimeFormatter)
        } else {
            NumberToTextConverter.toText(cell.numericCellValue)
        }
    }

    private fun numericToJsonElement(cell: Cell): JsonElement {
        return if (DateUtil.isCellDateFormatted(cell)) {
            JsonPrimitive(cell.localDateTimeCellValue.format(dateTimeFormatter))
        } else {
            jsonNumber(NumberToTextConverter.toText(cell.numericCellValue))
        }
    }

    private fun formulaToString(cell: Cell, evaluator: FormulaEvaluator): String {
        val evaluated = tryEvaluate(cell, evaluator)
        if (evaluated != null) {
            return when (evaluated.cellType) {
                CellType.STRING -> evaluated.stringValue
                CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.format(dateTimeFormatter)
                } else {
                    NumberToTextConverter.toText(evaluated.numberValue)
                }
                CellType.BOOLEAN -> evaluated.booleanValue.toString()
                CellType.BLANK, CellType.ERROR -> ""
                else -> ""
            }
        }
        return try {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> numericToString(cell)
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.BLANK, CellType.ERROR -> ""
                else -> ""
            }
        } catch (e: Exception) {
            logCachedFailure(cell, e)
            ""
        }
    }

    private fun formulaToJsonElement(cell: Cell, evaluator: FormulaEvaluator): JsonElement {
        val evaluated = tryEvaluate(cell, evaluator)
        if (evaluated != null) {
            return when (evaluated.cellType) {
                CellType.STRING -> JsonPrimitive(evaluated.stringValue)
                CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) {
                    JsonPrimitive(cell.localDateTimeCellValue.format(dateTimeFormatter))
                } else {
                    jsonNumber(NumberToTextConverter.toText(evaluated.numberValue))
                }
                CellType.BOOLEAN -> JsonPrimitive(evaluated.booleanValue)
                CellType.BLANK, CellType.ERROR -> JsonPrimitive("")
                else -> JsonPrimitive("")
            }
        }
        return try {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> JsonPrimitive(cell.stringCellValue)
                CellType.NUMERIC -> numericToJsonElement(cell)
                CellType.BOOLEAN -> JsonPrimitive(cell.booleanCellValue)
                CellType.BLANK, CellType.ERROR -> JsonPrimitive("")
                else -> JsonPrimitive("")
            }
        } catch (e: Exception) {
            logCachedFailure(cell, e)
            JsonPrimitive("")
        }
    }

    private fun rawCellValue(cell: Cell, evaluator: FormulaEvaluator): RawCellValueResult {
        return when (cell.cellType) {
            CellType.STRING -> RawCellValueResult(cell.stringCellValue, ExcelCellValueState.VALUE)
            CellType.NUMERIC -> RawCellValueResult(numericToString(cell), ExcelCellValueState.VALUE)
            CellType.BOOLEAN -> RawCellValueResult(cell.booleanCellValue.toString(), ExcelCellValueState.VALUE)
            CellType.FORMULA -> formulaToRawResult(cell, evaluator)
            CellType.BLANK -> RawCellValueResult("", ExcelCellValueState.BLANK)
            CellType.ERROR -> RawCellValueResult("", ExcelCellValueState.ERROR)
            else -> RawCellValueResult("", ExcelCellValueState.BLANK)
        }
    }

    private fun formulaToRawResult(cell: Cell, evaluator: FormulaEvaluator): RawCellValueResult {
        val evaluated = tryEvaluate(cell, evaluator)
        if (evaluated != null) {
            return when (evaluated.cellType) {
                CellType.STRING -> RawCellValueResult(evaluated.stringValue, ExcelCellValueState.EVALUATED_FORMULA_VALUE)
                CellType.NUMERIC -> RawCellValueResult(NumberToTextConverter.toText(evaluated.numberValue), ExcelCellValueState.EVALUATED_FORMULA_VALUE)
                CellType.BOOLEAN -> RawCellValueResult(evaluated.booleanValue.toString(), ExcelCellValueState.EVALUATED_FORMULA_VALUE)
                CellType.BLANK -> RawCellValueResult("", ExcelCellValueState.FORMULA_NOT_EVALUATED)
                CellType.ERROR -> RawCellValueResult("", ExcelCellValueState.ERROR)
                else -> RawCellValueResult("", ExcelCellValueState.FORMULA_NOT_EVALUATED)
            }
        }

        val cached = try {
            when (cell.cachedFormulaResultType) {
                CellType.STRING -> RawCellValueResult(cell.stringCellValue, ExcelCellValueState.CACHED_FORMULA_VALUE)
                CellType.NUMERIC -> RawCellValueResult(NumberToTextConverter.toText(cell.numericCellValue), ExcelCellValueState.CACHED_FORMULA_VALUE)
                CellType.BOOLEAN -> RawCellValueResult(cell.booleanCellValue.toString(), ExcelCellValueState.CACHED_FORMULA_VALUE)
                CellType.BLANK -> RawCellValueResult("", ExcelCellValueState.FORMULA_NOT_EVALUATED)
                CellType.ERROR -> RawCellValueResult("", ExcelCellValueState.ERROR)
                else -> RawCellValueResult("", ExcelCellValueState.FORMULA_NOT_EVALUATED)
            }
        } catch (e: Exception) {
            logCachedFailure(cell, e)
            RawCellValueResult("", ExcelCellValueState.FORMULA_NOT_EVALUATED)
        }

        if (cached.value.isNotEmpty()) return cached

        return if (cell.cellFormula.contains("[") && cell.cellFormula.contains("]")) {
            RawCellValueResult("", ExcelCellValueState.EXTERNAL_REFERENCE_NOT_AVAILABLE)
        } else {
            RawCellValueResult("", ExcelCellValueState.FORMULA_NOT_EVALUATED)
        }
    }

    private fun toCellType(cell: Cell): ExcelCellType {
        return when (cell.cellType) {
            CellType.STRING -> ExcelCellType.STRING
            CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) ExcelCellType.DATETIME else ExcelCellType.NUMBER
            CellType.BOOLEAN -> ExcelCellType.BOOLEAN
            CellType.FORMULA -> ExcelCellType.FORMULA
            CellType.BLANK, CellType.ERROR -> ExcelCellType.BLANK
            else -> ExcelCellType.BLANK
        }
    }

    private fun tryEvaluate(cell: Cell, evaluator: FormulaEvaluator): CellValue? {
        return try {
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
    }

    private fun logCachedFailure(cell: Cell, e: Exception) {
        logger.debug(
            "Failed to read cached Excel formula value: sheet={}, address={}",
            cell.sheet.sheetName,
            CellReference(cell).formatAsString(false),
            e
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun jsonNumber(value: String): JsonElement = JsonUnquotedLiteral(value)

    data class RawCellValueResult(val value: String, val valueState: ExcelCellValueState)
}