package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
enum class ExcelCellValueState {
    VALUE,
    EVALUATED_FORMULA_VALUE,
    CACHED_FORMULA_VALUE,
    EXTERNAL_REFERENCE_NOT_AVAILABLE,
    FORMULA_NOT_EVALUATED,
    ERROR,
    BLANK,
}