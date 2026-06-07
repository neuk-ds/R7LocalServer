package ru.mrnds.r7localserver.files.writers

import kotlinx.serialization.json.JsonElement
import java.io.File

interface FileWriter {
    fun write(
        file: File,
        content: JsonElement,
    ): File
}