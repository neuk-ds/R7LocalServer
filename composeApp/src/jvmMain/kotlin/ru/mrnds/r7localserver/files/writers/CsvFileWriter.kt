package ru.mrnds.r7localserver.files.writers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.CsvFileOptions
import java.io.File

class CsvFileWriter : FileWriter {
    private val logger = LoggerFactory.getLogger(CsvFileWriter::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun write(file: File, content: JsonElement): File {
        val options = try {
            json.decodeFromJsonElement<CsvFileOptions>(content)
        } catch (e: Exception) {
            logger.error("Invalid CSV options JSON", e)
            throw IllegalArgumentException("Invalid CSV options JSON", e)
        }

        require(options.delimiter.isNotEmpty()) {
            "Delimiter cannot be empty"
        }
        require(options.lineSeparator.isNotEmpty()) {
            "Line separator cannot be empty"
        }
        require(options.table.isNotEmpty()) {
            "Table cannot be empty"
        }

        val csvContent = options.table.joinToString(options.lineSeparator) { row ->
            row.joinToString(options.delimiter) { value ->
                escapeCsvValue(
                    value = value,
                    delimiter = options.delimiter,
                    lineSeparator = options.lineSeparator
                )
            }
        }

        file.writeText(csvContent)
        return file
    }

    private fun escapeCsvValue(
        value: String,
        delimiter: String,
        lineSeparator: String
    ): String {
        val containsSpecialCharacters =
            value.contains(delimiter) ||
                    value.contains("\"") ||
                    value.contains("\n") ||
                    value.contains("\r") ||
                    value.contains(lineSeparator)
        if (!containsSpecialCharacters) {
            return value
        }
        val escapedValue = value.replace("\"", "\"\"")
        return "\"$escapedValue\""
    }

}