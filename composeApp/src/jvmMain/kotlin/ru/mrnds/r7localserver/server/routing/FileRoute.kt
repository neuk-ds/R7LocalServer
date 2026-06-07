package ru.mrnds.r7localserver.server.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.FileService
import ru.mrnds.r7localserver.server.dto.*
import java.io.File

private val logger = LoggerFactory.getLogger("FileRoute")
fun Route.fileRoute(
    fileService: FileService
) {
    post("/files/write") {

        try {
            val request = call.receive<WriteFileRequest>()
            val filePath = File(request.directoryPath, request.fileName).path
            val fileType = fileService.getFileType(request.fileName)
            logger.info(
                "Write file request: type={}, path={}, overwrite={}",
                fileType,
                filePath,
                request.overwrite
            )

            val file = fileService.writeFile(
                directoryPath = request.directoryPath,
                fileName = request.fileName,
                overwrite = request.overwrite,
                content = request.content,
            )
            call.respond(
                WriteFileResponse(
                    success = true,
                    path = file.absolutePath,
                    fileType = fileType,
                )
            )
            logger.info("File written successfully: type={}, path={}", fileType, file.absolutePath)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    message = e.message ?: "Invalid request"
                )
            )
            logger.warn("Invalid write file request: {}", e.message)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    message = e.message ?: "Invalid server error"
                )
            )
            logger.error("Failed to write file", e)
        }
    }

    post("/files/read") {
        try {
            val request = call.receive<ReadFileRequest>()
            val filePath = File(request.directoryPath, request.fileName).path
            val fileType = fileService.getFileType(request.fileName)
            logger.info(
                "Read file request: type={}, mode={}, path={}",
                fileType,
                request.readMode,
                filePath
            )
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
            logger.info(
                "File read successfully: type={}, mode={}, path={}",
                fileType,
                request.readMode,
                filePath
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    message = e.message ?: "Invalid request"
                )
            )
            logger.warn("Invalid read file request: {}", e.message)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    message = e.message ?: "Internal server error"
                )
            )
            logger.error("Failed to read file", e)
        }
    }
}