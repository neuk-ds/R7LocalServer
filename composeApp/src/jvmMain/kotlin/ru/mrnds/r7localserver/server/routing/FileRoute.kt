package ru.mrnds.r7localserver.server.routing

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.FileService
import ru.mrnds.r7localserver.server.dto.*
import java.io.File

private val logger = LoggerFactory.getLogger("FileRoute")

fun Route.fileRoute(fileService: FileService) {
    post("/files/write") {
        call.handleErrors(logger) {
            val request = call.receive<WriteFileRequest>()
            val filePath = File(request.directoryPath, request.fileName).path
            val fileType = fileService.getFileType(request.fileName)
            logger.info("Write file request: type={}, path={}, overwrite={}", fileType, filePath, request.overwrite)

            val file = fileService.writeFile(
                directoryPath = request.directoryPath,
                fileName = request.fileName,
                overwrite = request.overwrite,
                content = request.content,
            )
            call.respond(WriteFileResponse(success = true, path = file.absolutePath, fileType = fileType))
            logger.info("File written successfully: type={}, path={}", fileType, file.absolutePath)
        }
    }

    post("/files/read") {
        call.handleErrors(logger) {
            val request = call.receive<ReadFileRequest>()
            val filePath = File(request.directoryPath, request.fileName).path
            val fileType = fileService.getFileType(request.fileName)
            logger.info("Read file request: type={}, mode={}, path={}", fileType, request.readMode, filePath)

            val content = fileService.readFile(
                directoryPath = request.directoryPath,
                fileName = request.fileName,
                readMode = request.readMode,
            )
            call.respond(
                ReadFileResponse(
                    directoryPath = request.directoryPath,
                    fileName = request.fileName,
                    fileType = fileType,
                    lastModifiedAt = fileService.getLastModifiedAt(
                        directoryPath = request.directoryPath,
                        fileName = request.fileName
                    ),
                    content = content,
                )
            )
            logger.info("File read successfully: type={}, mode={}, path={}", fileType, request.readMode, filePath)
        }
    }
}