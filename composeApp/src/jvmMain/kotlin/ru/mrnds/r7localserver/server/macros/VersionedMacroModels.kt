package ru.mrnds.r7localserver.server.macros

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

const val LIBRARY_META = "_r7Library"
const val SYNC_META = "_r7Sync"
const val SEPARATOR_ID = "00000000-separator-0000-000000000000"
internal val macroJson = Json { prettyPrint = true; encodeDefaults = true }
internal val localFields = setOf(SYNC_META, "isExcludedFromAutoSync", "isUniversal", "isSeparator")
class MacroConflict(message: String) : RuntimeException(message)

@Serializable
data class LibraryRequest(val directoryPath: String, val fileName: String, val revisionId: String? = null)

@Serializable
data class PreviewRequest(
    val directoryPath: String,
    val fileName: String,
    val action: String,
    val document: JsonObject = buildJsonObject { put("macrosArray", JsonArray(emptyList())) },
    val selectedGuids: List<String> = emptyList(),
    val revisionId: String? = null,
    val replacementDocument: JsonObject? = null,
)

@Serializable
data class ApplyRequest(
    val directoryPath: String,
    val fileName: String,
    val token: String,
    val operationId: String,
    val selectedGuids: List<String>,
    val resolutions: Map<String, Resolution> = emptyMap(),
    val comment: String = "",
    val backupDirectory: String = "",
)

@Serializable
data class Resolution(val macro: JsonObject? = null)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class MacroChange(
    val guid: String,
    val name: String,
    val kind: String,
    val base: JsonObject?,
    val document: JsonObject?,
    val library: JsonObject?,
    val result: JsonObject?,
    @EncodeDefault val conflicts: List<String> = emptyList(),
    @EncodeDefault val chunks: List<CodeChunk> = emptyList(),
    @EncodeDefault val diff: List<DiffLine> = emptyList(),
)

@Serializable
data class CodeChunk(val text: String = "", val document: String? = null, val library: String? = null)
@Serializable
data class DiffLine(val kind: String, val text: String, val oldLine: Int? = null, val newLine: Int? = null)
@Serializable
data class PreviewResponse(val token: String, val operationId: String, val changes: List<MacroChange>, val expiresAt: String)
@Serializable
data class ApplyResponse(
    val revisionId: String? = null,
    val document: JsonObject? = null,
    val expectedDocument: JsonObject? = null,
    val backupPath: String? = null,
    val changed: Boolean = false,
)
@Serializable
data class LibraryState(val library: JsonObject, val managed: Boolean, val externalChanges: Boolean)
@Serializable
data class MacroRevision(
    val id: String,
    val parent: String?,
    val libraryId: String,
    val time: String,
    val author: String,
    val operationId: String,
    val comment: String,
    val library: JsonObject,
)

internal fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull ?: ""
internal fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.clean(): JsonObject = JsonObject(filterKeys { it !in localFields })
internal fun JsonObject.guid(): String = string("guid")
internal fun JsonObject.macros(): List<JsonObject> {
    val array = get("macrosArray") as? JsonArray ?: throw IllegalArgumentException("macrosArray must be an array")
    val macros = array.map { it as? JsonObject ?: throw IllegalArgumentException("Invalid macro") }
        .filterNot { it.flag("isSeparator") || it.guid() == SEPARATOR_ID }
    require(macros.all { it.guid().isNotBlank() }) { "Macro GUID must not be empty" }
    require(macros.map { it.guid() }.distinct().size == macros.size) { "Duplicate macro GUID" }
    require(macros.all { it["value"] is JsonPrimitive && it["value"]!!.jsonPrimitive.isString }) { "Macro value must be a string" }
    return macros
}
internal fun JsonObject.withMacros(macros: List<JsonObject>): JsonObject = JsonObject(toMutableMap().apply {
    put("macrosArray", JsonArray(macros))
})
