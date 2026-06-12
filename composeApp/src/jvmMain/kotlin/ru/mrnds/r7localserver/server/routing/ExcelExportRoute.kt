package ru.mrnds.r7localserver.server.routing

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.exporter.ExcelSheetExporter
import ru.mrnds.r7localserver.server.dto.ExportExcelSheetsRequest
import java.io.File

private val logger = LoggerFactory.getLogger("ExcelExportRoute")

fun Route.excelExportRoute(excelSheetExporter: ExcelSheetExporter) {
    post("/export/excel-sheets") {
        call.handleErrors(logger) {
            val request = call.receive<ExportExcelSheetsRequest>()
            val sourcePath = File(request.sourceDirectoryPath, request.sourceFileName).path
            val targetPath = File(request.targetDirectoryPath, request.targetFileName).path
            logger.info(
                "Export Excel sheets request: source={}, target={}, sheets={}, overwrite={}",
                sourcePath, targetPath, request.sheetNames, request.overwrite
            )

            val response = excelSheetExporter.exportSheets(request)
            call.respond(response)
            logger.info(
                "Excel sheets exported successfully: target={}, sheets={}, replacedFormulas={}",
                targetPath, response.exportedSheetNames, response.replacedFormulaCount
            )
        }
    }
}