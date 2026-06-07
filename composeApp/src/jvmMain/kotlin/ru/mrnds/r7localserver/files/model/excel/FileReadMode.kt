package ru.mrnds.r7localserver.files.model.excel

import kotlinx.serialization.Serializable

@Serializable
enum class FileReadMode {
    DEFAULT,
    TYPED,
    RAW
}