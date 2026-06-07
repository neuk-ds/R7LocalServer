package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
enum class ExcelCellType {
    STRING,
    NUMBER,
    DATETIME,
    BOOLEAN,
    FORMULA,
    BLANK
}