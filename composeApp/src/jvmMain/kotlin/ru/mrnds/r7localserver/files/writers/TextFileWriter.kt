package ru.mrnds.r7localserver.files.writers

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class TextFileWriter : FileWriter {
    override fun write(file: File, content: JsonElement): File {
        file.writeText(content.jsonPrimitive.content)
        return file
    }
}