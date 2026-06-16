package ru.mrnds.r7localserver.server.routing

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.server.dto.MacroSyncRequest
import ru.mrnds.r7localserver.server.macros.MacroSyncService
import java.io.File

private val logger = LoggerFactory.getLogger("MacroSyncRoute")

fun Route.macroSyncRoute(macroSyncService: MacroSyncService) {
    post("/macros/sync") {
        call.handleErrors(logger) {
            val request = call.receive<MacroSyncRequest>()

            require(request.directoryPath.isNotBlank()) { "directoryPath must not be empty" }
            require(request.fileName.isNotBlank()) { "fileName must not be empty" }
            require(request.mode in setOf("merge", "push", "load", "refresh")) {
                "mode must be 'merge', 'push', 'load', or 'refresh', got '${request.mode}'"
            }

            val filePath = File(request.directoryPath, request.fileName).path
            logger.info(
                "Macros sync request: mode={}, file={}, macros={}",
                request.mode, filePath, request.macrosArray.size
            )

            val response = when (request.mode) {
                "merge"   -> macroSyncService.merge(request)
                "load"    -> macroSyncService.load(request)
                "refresh" -> macroSyncService.refresh(request)
                else      -> macroSyncService.push(request)
            }

            call.respond(response)
            logger.info(
                "Macros sync completed: mode={}, updated={}, added={}",
                request.mode, response.updated, response.added
            )
        }
    }
}
