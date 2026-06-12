package ru.mrnds.r7localserver.files.writers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.io.File

class JsonFileWriter : FileWriter {
    private val logger = LoggerFactory.getLogger(JsonFileWriter::class.java)
    private val json = Json { prettyPrint = true }
    override fun write(file: File, content: JsonElement): File {
        try {
            file.writeText(json.encodeToString(content))
            return file
        } catch (e: Exception) {
            logger.error("Invalid JSON content", e)
            throw IllegalArgumentException("Invalid JSON", e)
        }
    }
}