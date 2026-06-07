package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.mrnds.r7localserver.files.model.FileType

@Serializable
data class WriteFileRequest(
    val directoryPath: String,
    val fileName: String,
    val overwrite: Boolean = false,
    val fileType: FileType? = null,
    val lastModifiedAt: String? = null,
    val content: JsonElement,
)
