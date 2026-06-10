package ru.mrnds.r7localserver.server.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.exporter.ExcelSheetExporter
import ru.mrnds.r7localserver.server.dto.ErrorResponse
import ru.mrnds.r7localserver.server.dto.ExportExcelSheetsRequest
import java.io.File

private val logger = LoggerFactory.getLogger("ExcelExportRoute")

fun Route.excelExportRoute(
    excelSheetExporter: ExcelSheetExporter
) {
    post("/export/excel-sheets") {
        var request: ExportExcelSheetsRequest? = null

        try {
            request = call.receive<ExportExcelSheetsRequest>()

            val sourcePath = File(request.sourceDirectoryPath, request.sourceFileName).path
            val targetPath = File(request.targetDirectoryPath, request.targetFileName).path

            logger.info(
                "Export Excel sheets request: source={}, target={}, sheets={}, overwrite={}",
                sourcePath,
                targetPath,
                request.sheetNames,
                request.overwrite
            )

            val response = excelSheetExporter.exportSheets(request)
            call.respond(response)

            logger.info(
                "Excel sheets exported successfully: target={}, sheets={}, replacedFormulas={}",
                targetPath,
                response.exportedSheetNames,
                response.replacedFormulaCount
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = e.message ?: "Invalid export request")
            )
            logger.warn("Invalid Excel export request: {}", e.message)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(message = e.message ?: "Excel export failed")
            )
            logger.error(
                "Excel export failed: sourceDirectory={}, sourceFile={}, targetDirectory={}, targetFile={}",
                request?.sourceDirectoryPath,
                request?.sourceFileName,
                request?.targetDirectoryPath,
                request?.targetFileName,
                e
            )
        }
    }
}