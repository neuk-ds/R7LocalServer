package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.io.File

class JsonFileReader : FileReader {
    private val logger = LoggerFactory.getLogger(JsonFileReader::class.java)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override fun read(file: File): JsonElement {
        val content = file.readText()
        return try {
            json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Invalid JSON file content: {}", file.absolutePath, e)
            throw IllegalArgumentException("Invalid JSON file content")
        }
    }

}