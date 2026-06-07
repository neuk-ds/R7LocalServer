package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.json.JsonElement
import java.io.File

interface FileReader {
    fun read(file: File): JsonElement
}