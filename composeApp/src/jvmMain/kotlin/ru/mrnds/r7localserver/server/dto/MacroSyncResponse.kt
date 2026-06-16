package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MacroSyncResponse(
    val status: String = "ok",
    val macrosArray: List<JsonObject>? = null,
    val conflicts: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val added: List<String> = emptyList(),
    val totalUniversal: Int? = null,
)
