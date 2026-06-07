package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.mrnds.r7localserver.files.model.FileType

@Serializable
data class ReadFileResponse(
    val directoryPath: String,
    val fileName: String,
    val fileType: FileType,
    val lastModifiedAt: String,
    val content: JsonElement
)
