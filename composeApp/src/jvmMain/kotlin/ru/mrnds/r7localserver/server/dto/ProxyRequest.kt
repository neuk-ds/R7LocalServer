package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProxyRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val authType: ProxyAuthType = ProxyAuthType.NONE,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    val ignoreSslErrors: Boolean = false,
)
