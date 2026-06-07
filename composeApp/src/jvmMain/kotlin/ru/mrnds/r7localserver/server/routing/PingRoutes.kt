package ru.mrnds.r7localserver.server.routing

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.pingRoutes() {

    get("/ping") {

        call.respond(
            PingResponse(
                status = "ok"
            )
        )
    }
}

@Serializable
data class PingResponse(
    val status: String
)