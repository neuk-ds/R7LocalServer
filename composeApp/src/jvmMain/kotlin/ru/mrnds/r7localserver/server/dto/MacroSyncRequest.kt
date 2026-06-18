package ru.mrnds.r7localserver.server.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MacroSyncRequest(
    val directoryPath: String,
    val fileName: String,
    val mode: String = "merge",
    val macrosArray: List<JsonObject> = emptyList(),
    val selectedGuids: List<String> = emptyList(),
)
