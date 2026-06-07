package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class TextFileReader : FileReader {
    override fun read(file: File): JsonElement {
        return JsonPrimitive(file.readText())
    }
}