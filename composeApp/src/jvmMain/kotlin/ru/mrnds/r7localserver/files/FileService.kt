package ru.mrnds.r7localserver.files

import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.FileType
import ru.mrnds.r7localserver.files.model.excel.FileReadMode
import ru.mrnds.r7localserver.files.readers.*
import ru.mrnds.r7localserver.files.writers.*
import java.io.File
import java.nio.file.Files

class FileService {
    private val logger = LoggerFactory.getLogger(FileService::class.java)
    private val writers: Map<FileType, FileWriter> = mapOf(
        FileType.TXT to TextFileWriter(),
        FileType.JSON to JsonFileWriter(),
        FileType.CSV to CsvFileWriter(),
        FileType.XML to XmlFileWriter(),
        FileType.XLSX to ExcelFileWriter(),
    )

    private val readers: Map<FileType, FileReader> = mapOf(
        FileType.TXT to TextFileReader(),
        FileType.JSON to JsonFileReader(),
        FileType.CSV to CsvFileReader(),
        FileType.XML to XmlFileReader(),
        FileType.XLS to ExcelFileReader(),
        FileType.XLSX to ExcelFileReader(),
    )

    fun writeFile(
        directoryPath: String,
        fileName: String,
        content: JsonElement,
        overwrite: Boolean,
    ): File {
        validateCommon(directoryPath, fileName)
        val fileType = FileType.fromFileName(fileName)
        val writer =
            writers[fileType] ?: throw IllegalArgumentException("File type ${fileType.extension} not implement yet")
        val outputDirectory = prepareDirectory(directoryPath)

        val file = File(outputDirectory, fileName)
        if (file.exists() && !overwrite) {
            throw IllegalArgumentException("File already exists")
        }

        return writer.write(
            file = file,
            content = content
        )
    }

    fun readFile(
        directoryPath: String,
        fileName: String,
        readMode: FileReadMode = FileReadMode.DEFAULT,
    ): JsonElement {
        validateCommon(directoryPath, fileName)
        val fileType = FileType.fromFileName(fileName)
        val reader =
            readers[fileType] ?: throw IllegalArgumentException("File type ${fileType.extension} not implement yet")
        val file = File(directoryPath, fileName)
        require(file.exists()) { "File $fileName does not exist in $directoryPath" }
        require(file.isFile) { "File $fileName is not a file" }
        return if (reader is ExcelFileReader) {
            reader.read(
                file = file,
                readMode = readMode
            )
        } else {
            reader.read(file)
        }
    }

    fun getFileType(fileName: String): FileType {
        return FileType.fromFileName(fileName)
    }

    fun getLastModifiedAt(
        directoryPath: String,
        fileName: String
    ): String {
        validateCommon(directoryPath, fileName)

        val file = File(directoryPath, fileName)

        require(file.exists()) {
            "File $fileName does not exist in $directoryPath"
        }

        require(file.isFile) {
            "File $fileName is not a file"
        }

        return Files.getLastModifiedTime(file.toPath())
            .toInstant()
            .toString()
    }

    private fun validateCommon(
        directoryPath: String,
        fileName: String,
    ) {
        require(directoryPath.isNotBlank()) {
            "Directory must not be empty"
        }

        require(fileName.isNotBlank()) {
            "File name must not be empty"
        }

        require(!fileName.contains("/") && !fileName.contains("\\")) {
            "File name must not contain path separators"
        }
    }

    private fun prepareDirectory(
        directoryPath: String,
    ): File {
        val outputDirectory = File(directoryPath)
        if (!outputDirectory.exists()) {
            val created = outputDirectory.mkdirs()

            if (created) {
                logger.info("Output directory created: {}", outputDirectory.absolutePath)
            } else {
                logger.warn("Failed to create output directory: {}", outputDirectory.absolutePath)
            }
        }

        require(outputDirectory.isDirectory) { "OutputDirectory must be a directory" }
        return outputDirectory
    }
}