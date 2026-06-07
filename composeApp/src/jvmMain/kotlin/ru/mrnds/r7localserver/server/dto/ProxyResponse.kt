package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProxyResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String
)
