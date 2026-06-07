package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
data class ExcelRawCell(
    val address: String,
    val rowIndex: Int,
    val columnIndex: Int,
    val type: ExcelCellType,
    val value: String,
    val formula: String? = null,
    val format: String? = null,
    val valueState: ExcelCellValueState
)