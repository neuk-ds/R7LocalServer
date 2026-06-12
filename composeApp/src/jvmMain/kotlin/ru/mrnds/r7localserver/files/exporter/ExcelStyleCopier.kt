package ru.mrnds.r7localserver.files.exporter

import org.apache.poi.ss.usermodel.*
import org.slf4j.LoggerFactory
import java.io.File

class ExcelStyleCopier {
    private val logger = LoggerFactory.getLogger(ExcelStyleCopier::class.java)

    fun copyStyles(sourceFile: File, targetFile: File, sheetNames: List<String>) {
        val sourceWorkbook = try {
            WorkbookFactory.create(sourceFile.inputStream())
        } catch (e: Exception) {
            logger.error("Cannot open source file for style copying: {}", sourceFile.absolutePath, e)
            throw IllegalArgumentException("Cannot open source file for style copying", e)
        }

        sourceWorkbook.use { source ->
            val targetWorkbook = try {
                WorkbookFactory.create(targetFile.inputStream())
            } catch (e: Exception) {
                logger.error("Cannot open target file for style copying: {}", targetFile.absolutePath, e)
                throw IllegalArgumentException("Cannot open target file for style copying", e)
            }

            targetWorkbook.use { target ->
                sheetNames.forEach { sheetName ->
                    val sourceSheet = source.getSheet(sheetName)
                    val targetSheet = target.getSheet(sheetName)

                    if (sourceSheet == null) {
                        logger.warn("Sheet not found in source for style copying: {}", sheetName)
                        return@forEach
                    }
                    if (targetSheet == null) {
                        logger.warn("Sheet not found in target for style copying: {}", sheetName)
                        return@forEach
                    }

                    copySheetStyles(sourceSheet, target, targetSheet)
                }

                targetFile.outputStream().use { target.write(it) }
            }
        }
    }

    private fun copySheetStyles(
        sourceSheet: Sheet,
        targetWb: Workbook,
        targetSheet: Sheet,
    ) {
        targetSheet.defaultRowHeight = sourceSheet.defaultRowHeight

        val maxCol = sourceSheet.maxOfOrNull { row -> row.lastCellNum.toInt().coerceAtLeast(0) } ?: 0
        for (col in 0 until maxCol) {
            targetSheet.setColumnWidth(col, sourceSheet.getColumnWidth(col))
        }

        sourceSheet.mergedRegions.forEach { region ->
            targetSheet.addMergedRegion(region)
        }

        for (rowIdx in sourceSheet.firstRowNum..sourceSheet.lastRowNum) {
            val sourceRow = sourceSheet.getRow(rowIdx) ?: continue
            val targetRow = targetSheet.getRow(rowIdx) ?: targetSheet.createRow(rowIdx)
            targetRow.height = sourceRow.height

            for (sourceCell in sourceRow) {
                val targetCell = targetRow.getCell(sourceCell.columnIndex)
                    ?: targetRow.createCell(sourceCell.columnIndex)
                val newStyle = targetWb.createCellStyle()
                newStyle.cloneStyleFrom(sourceCell.cellStyle)
                targetCell.cellStyle = newStyle
            }
        }
    }
}