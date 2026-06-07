package ru.mrnds.r7localserver.server.proxy

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.server.dto.ProxyRequest
import ru.mrnds.r7localserver.server.dto.ProxyResponse
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager

class ProxyService {
    private val logger = LoggerFactory.getLogger(ProxyService::class.java)
    private val defaultClient = HttpClient(CIO)
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(
            chain: Array<X509Certificate>?,
            authType: String?
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<X509Certificate>?,
            authType: String?
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val unsafeClient = HttpClient(CIO) {
        engine {
            https {
                trustManager = trustAllManager
            }
        }
    }


    suspend fun proxy(request: ProxyRequest): ProxyResponse {
        if (request.ignoreSslErrors) {
            logger.warn("Proxy request uses disabled SSL certificate validation: {}", request.url)
        }
        val client = if (request.ignoreSslErrors) unsafeClient else defaultClient

        val response = client.request(request.url) {
            method = HttpMethod.parse(request.method.uppercase())

            headers {
                request.headers.forEach { (name, value) ->
                    if (canForwardHeader(name)) {
                        append(name, value)
                    }
                }

                if (!request.username.isNullOrBlank() && request.password != null) {
                    append(HttpHeaders.Authorization, basicAuth(request.username, request.password))
                }

                if (request.body != null) {
                    setBody(request.body)
                }
            }
        }
        logger.info("Proxy response received: {} {} -> {}", request.method, request.url, response.status.value)
        return ProxyResponse(
            status = response.status.value,
            headers = response.headers.entries().associate { it.key to it.value.joinToString(", ") },
            body = response.bodyAsText()
        )
    }

    private fun basicAuth(username: String, password: String): String {
        val raw = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray())
        return "Basic $encoded"
    }

    private fun canForwardHeader(name: String): Boolean {
        val lower = name.lowercase()

        return lower !in setOf(
            "host",
            "content-length",
            "connection"
        )
    }
}