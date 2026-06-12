package ru.mrnds.r7localserver.server.routing

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.server.dto.ProxyRequest
import ru.mrnds.r7localserver.server.proxy.ProxyService

private val logger = LoggerFactory.getLogger("ProxyRoute")

fun Route.proxyRoute(proxyService: ProxyService) {
    post("/proxy/request") {
        call.handleErrors(logger) {
            val request = call.receive<ProxyRequest>()
            logger.info(
                "Proxy request received: method={}, url={}, ignoreSslErrors={}",
                request.method, request.url, request.ignoreSslErrors
            )

            val response = proxyService.proxy(request)
            call.respond(response)
            logger.info("Proxy request completed: method={}, url={}", request.method, request.url)
        }
    }
}