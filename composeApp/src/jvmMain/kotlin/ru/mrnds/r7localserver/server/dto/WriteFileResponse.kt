package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import ru.mrnds.r7localserver.files.model.FileType

@Serializable
data class WriteFileResponse(
    val success: Boolean,
    val path: String,
    val fileType: FileType,
)
