package ru.mrnds.r7localserver.server.proxy

import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.NTCredentials
import org.apache.hc.client5.http.auth.StandardAuthScheme
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.impl.win.WinHttpClients
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder
import org.apache.hc.core5.ssl.SSLContexts
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.platform.Platform
import ru.mrnds.r7localserver.server.dto.ProxyAuthType
import ru.mrnds.r7localserver.server.dto.ProxyRequest
import ru.mrnds.r7localserver.server.dto.ProxyResponse

class ProxyService {
    private val logger = LoggerFactory.getLogger(ProxyService::class.java)
    private val defaultClient: CloseableHttpClient = HttpClients.createDefault()
    private val unsafeClient: CloseableHttpClient = createUnsafeClient()
    private val winClient: CloseableHttpClient by lazy {
        WinHttpClients.createDefault()
    }
    private val unsafeWinClient: CloseableHttpClient by lazy {
        createUnsafeWinClient()
    }

    fun proxy(request: ProxyRequest): ProxyResponse {
        validateRequest(request)

        if (request.ignoreSslErrors) {
            logger.warn("Proxy request uses disabled SSL certificate validation: {}", request.url)
        }

        val client = selectClient(request)
        val context = createContext(request)
        val httpRequest = createHttpRequest(request)

        val response = client.execute(httpRequest, context) { response ->
            toProxyResponse(response)
        }

        logger.info("Proxy response received: {} {} -> {}", request.method, request.url, response.status)
        return response
    }

    private fun validateRequest(request: ProxyRequest) {
        when (request.authType) {
            ProxyAuthType.NONE -> Unit

            ProxyAuthType.BASIC -> {
                require(!request.username.isNullOrBlank()) { "username is required for BASIC auth" }
                require(request.password != null) { "password is required for BASIC auth" }
            }

            ProxyAuthType.NTLM -> {
                require(!request.username.isNullOrBlank()) { "username is required for NTLM auth" }
                require(request.password != null) { "password is required for NTLM auth" }
                require(!request.domain.isNullOrBlank()) { "domain is required for NTLM auth" }
            }

            ProxyAuthType.NEGOTIATE -> {
                require(Platform.isWindows) { "NEGOTIATE authentication is supported only on Windows" }
                require(WinHttpClients.isWinAuthAvailable()) {
                    "Windows authentication is not available"
                }
            }
        }
    }

    private fun selectClient(request: ProxyRequest): CloseableHttpClient {
        return when (request.authType) {
            ProxyAuthType.NEGOTIATE -> if (request.ignoreSslErrors) unsafeWinClient else winClient
            else -> if (request.ignoreSslErrors) unsafeClient else defaultClient
        }
    }

    private fun createContext(request: ProxyRequest): HttpClientContext {
        val context = HttpClientContext.create()

        when (request.authType) {
            ProxyAuthType.NONE -> Unit

            ProxyAuthType.BASIC -> {
                val provider = BasicCredentialsProvider()
                provider.setCredentials(
                    AuthScope(null, -1),
                    UsernamePasswordCredentials(request.username, request.password!!.toCharArray())
                )
                context.credentialsProvider = provider
            }

            ProxyAuthType.NTLM -> {
                val provider = BasicCredentialsProvider()
                provider.setCredentials(
                    AuthScope(null, -1),
                    NTCredentials(
                        request.username,
                        request.password!!.toCharArray(),
                        null,
                        request.domain
                    )
                )
                context.credentialsProvider = provider
                context.requestConfig = RequestConfig.custom()
                    .setTargetPreferredAuthSchemes(listOf(StandardAuthScheme.NTLM))
                    .build()
            }

            ProxyAuthType.NEGOTIATE -> {
                context.requestConfig = RequestConfig.custom()
                    .setTargetPreferredAuthSchemes(
                        listOf(StandardAuthScheme.SPNEGO, StandardAuthScheme.NTLM)
                    )
                    .build()
            }
        }

        return context
    }

    private fun createHttpRequest(request: ProxyRequest) =
        ClassicRequestBuilder.create(request.method.uppercase())
            .setUri(request.url)
            .apply {
                request.headers.forEach { (name, value) ->
                    if (canForwardHeader(name)) {
                        addHeader(name, value)
                    }
                }

                if (request.body != null) {
                    setEntity(StringEntity(request.body))
                }
            }
            .build()

    private fun toProxyResponse(response: ClassicHttpResponse): ProxyResponse {
        return ProxyResponse(
            status = response.code,
            headers = response.headers
                .groupBy { it.name }
                .mapValues { (_, headers) -> headers.joinToString(", ") { it.value } },
            body = response.entity?.let { EntityUtils.toString(it) } ?: ""
        )
    }

    private fun createUnsafeClient(): CloseableHttpClient {
        return HttpClients.custom()
            .setConnectionManager(createUnsafeConnectionManager())
            .build()
    }

    private fun createUnsafeWinClient(): CloseableHttpClient {
        return WinHttpClients.custom()
            .setConnectionManager(createUnsafeConnectionManager())
            .build()
    }

    private fun createUnsafeConnectionManager() =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(
                SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(
                        SSLContexts.custom()
                            .loadTrustMaterial(null) { _, _ -> true }
                            .build()
                    )
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build()
            )
            .build()

    private fun canForwardHeader(name: String): Boolean {
        return name.lowercase() !in setOf(
            "host",
            "content-length",
            "connection"
        )
    }
}