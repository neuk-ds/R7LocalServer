package ru.mrnds.r7localserver.files.readers

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.CsvFileOptions
import java.io.File

class CsvFileReader : FileReader {
    private val logger = LoggerFactory.getLogger(CsvFileReader::class.java)
    private val json = Json {
        encodeDefaults = true
    }

    override fun read(file: File): JsonElement {
        val delimiter = detectDelimiter(file)
        logger.debug("CSV delimiter detected: file={}, delimiter={}", file.absolutePath, delimiter)
        val table = try {
            csvReader {
                this.delimiter = delimiter
            }.readAll(file)
        } catch (e: Exception) {
            logger.error("Invalid CSV file: {}", file.absolutePath, e)
            throw IllegalArgumentException("Invalid CSV file", e)
        }
        val options = CsvFileOptions(
            delimiter = delimiter.toString(),
            lineSeparator = "\n",
            table = table
        )
        return json.encodeToJsonElement(options)
    }

    private fun detectDelimiter(
        file: File
    ): Char {
        val sampleLines =
            file.useLines { lines -> lines.take(5).toList() }

        val delimiters = listOf(
            ';',
            ',',
            '\t',
            '|'
        )

        val scores =
            delimiters.associateWith { delimiter ->
                sampleLines.sumOf { line -> line.count { it == delimiter } }
            }

        return scores.maxByOrNull { it.value }?.key ?: ';'
    }

}