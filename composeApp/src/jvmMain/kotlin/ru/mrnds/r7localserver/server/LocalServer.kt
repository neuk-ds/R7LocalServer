package ru.mrnds.r7localserver.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.FileService
import ru.mrnds.r7localserver.files.exporter.ExcelSheetExporter
import ru.mrnds.r7localserver.server.proxy.ProxyService
import ru.mrnds.r7localserver.server.routing.excelExportRoute
import ru.mrnds.r7localserver.server.routing.fileRoute
import ru.mrnds.r7localserver.server.routing.pingRoutes
import ru.mrnds.r7localserver.server.routing.proxyRoute

class LocalServer(
    private val config: ServerConfig = ServerConfig()
) {
    private val logger = LoggerFactory.getLogger(LocalServer::class.java)
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val fileService = FileService()
    private val proxyService = ProxyService()
    private val excelSheetExporter = ExcelSheetExporter()

    fun start() {
        if (server != null) {
            logger.info("Local server is already running")
            return
        }

        server = embeddedServer(
            factory = Netty,
            host = config.host,
            port = config.port
        ) {
            install(ContentNegotiation) {
                json()
            }

            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)

                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Accept)
                allowHeader(HttpHeaders.Authorization)

                allowCredentials = true

                allowOrigins { origin ->
                    origin == "file://" || origin == "null"
                }
            }

            routing {
                pingRoutes()
                fileRoute(
                    fileService = fileService,
                )
                excelExportRoute(
                    excelSheetExporter = excelSheetExporter
                )
                proxyRoute(
                    proxyService = proxyService
                )
            }
        }
        try {
            server?.start(wait = false)
            logger.info("Local server started on http://{}:{}", config.host, config.port)
        } catch (e: Exception) {
            server = null
            logger.error("Failed to start local server on {}:{}", config.host, config.port, e)
            throw RuntimeException(
                "Failed to start local server: ${e.message}",
                e
            )
        }

    }

    fun stop() {
        server?.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 2000
        )

        server = null
        logger.info("Local server stopped")
    }
}