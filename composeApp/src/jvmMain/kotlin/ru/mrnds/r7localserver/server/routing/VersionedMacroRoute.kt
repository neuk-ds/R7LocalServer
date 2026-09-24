package ru.mrnds.r7localserver.server.routing

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.server.macros.*

fun Route.versionedMacroRoute(service: VersionedMacroService) {
    val logger = LoggerFactory.getLogger("VersionedMacroRoute")
    route("/macros/v2") {
        post("/state") { call.handleErrors(logger) { val request = call.receive<LibraryRequest>(); call.respond(withContext(Dispatchers.IO) { service.state(request) }) } }
        post("/order") { call.handleErrors(logger) { val request = call.receive<OrderRequest>(); call.respond(withContext(Dispatchers.IO) { service.saveOrder(request) }) } }
        post("/preview") { call.handleErrors(logger) { val request = call.receive<PreviewRequest>(); call.respond(withContext(Dispatchers.IO) { service.preview(request) }) } }
        post("/apply") { call.handleErrors(logger) { val request = call.receive<ApplyRequest>(); call.respond(withContext(Dispatchers.IO) { service.apply(request) }) } }
        post("/history") { call.handleErrors(logger) { val request = call.receive<LibraryRequest>(); call.respond(withContext(Dispatchers.IO) { service.history(request) }) } }
        post("/revision") { call.handleErrors(logger) { val request = call.receive<LibraryRequest>(); call.respond(withContext(Dispatchers.IO) { service.revision(request) }) } }
    }
}
