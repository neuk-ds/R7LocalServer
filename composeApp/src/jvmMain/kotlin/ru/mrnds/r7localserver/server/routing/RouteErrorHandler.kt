package ru.mrnds.r7localserver.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.Logger
import ru.mrnds.r7localserver.server.NotFoundException
import ru.mrnds.r7localserver.server.dto.ErrorResponse

suspend fun ApplicationCall.handleErrors(logger: Logger, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(message = e.message ?: "Invalid request"))
        logger.warn("Bad request: {}", e.message)
    } catch (e: NotFoundException) {
        respond(HttpStatusCode.NotFound, ErrorResponse(message = e.message ?: "Not found"))
        logger.warn("Not found: {}", e.message)
    } catch (e: Exception) {
        respond(HttpStatusCode.InternalServerError, ErrorResponse(message = e.message ?: "Internal server error"))
        logger.error("Request failed", e)
    }
}