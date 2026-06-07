package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import ru.mrnds.r7localserver.files.model.excel.FileReadMode

@Serializable
data class ReadFileRequest(
    val directoryPath: String,
    val fileName: String,
    val readMode: FileReadMode = FileReadMode.DEFAULT
)
