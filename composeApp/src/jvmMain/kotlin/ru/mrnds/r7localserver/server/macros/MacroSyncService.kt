package ru.mrnds.r7localserver.server.macros

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.server.NotFoundException
import ru.mrnds.r7localserver.server.dto.MacroSyncRequest
import ru.mrnds.r7localserver.server.dto.MacroSyncResponse
import java.io.File

private const val SEPARATOR_GUID = "00000000-separator-0000-000000000000"

private val SEPARATOR_OBJECT = buildJsonObject {
    put("name", " ")
    put("guid", SEPARATOR_GUID)
    put("value", "")
    put("autostart", false)
    put("isSeparator", true)
}

@Serializable
private data class UniversalMacrosFile(val macrosArray: List<JsonObject>)

class MacroSyncService {
    private val logger = LoggerFactory.getLogger(MacroSyncService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun merge(request: MacroSyncRequest): MacroSyncResponse {
        val file = File(request.directoryPath, request.fileName)

        if (!file.exists()) {
            throw NotFoundException(
                "Universal macros file not found: ${file.absolutePath}. Use mode=push to create it first."
            )
        }

        val universalFile = readUniversalFile(file)
        val universalGuids = universalFile.macrosArray.map { it.guid() }.toSet()

        val withoutSeparator = request.macrosArray.filter { !it.isSeparator() }
        val (universalInBook, specificInBook) = withoutSeparator.partition { macro ->
            macro.guid() in universalGuids || macro.isUniversal()
        }
        val universalInBookByGuid = universalInBook.associateBy { it.guid() }

        val updated = mutableListOf<String>()
        val added = mutableListOf<String>()

        val resultUniversals = universalFile.macrosArray.map { fileMacro ->
            if (fileMacro.guid() in universalInBookByGuid) {
                updated += fileMacro.name()
            } else {
                added += fileMacro.name()
            }
            fileMacro.withUniversal()
        }

        return MacroSyncResponse(
            macrosArray = resultUniversals + SEPARATOR_OBJECT + specificInBook,
            updated = updated,
            added = added,
        )
    }

    fun refresh(request: MacroSyncRequest): MacroSyncResponse {
        val file = File(request.directoryPath, request.fileName)

        if (!file.exists()) return MacroSyncResponse(macrosArray = request.macrosArray)

        val savedByGuid = readUniversalFile(file).macrosArray.associateBy { it.guid() }
        val updated = mutableListOf<String>()

        val newMacros = request.macrosArray.map { macro ->
            val saved = savedByGuid[macro.guid()]
            if (saved != null) {
                updated += macro.name()
                saved.withUniversal()
            } else {
                macro
            }
        }

        return MacroSyncResponse(
            macrosArray = ensureSeparator(newMacros),
            updated = updated,
        )
    }

    fun load(request: MacroSyncRequest): MacroSyncResponse {
        val file = File(request.directoryPath, request.fileName)

        if (!file.exists()) {
            throw NotFoundException(
                "Universal macros file not found: ${file.absolutePath}. Use mode=push to create it first."
            )
        }

        val universalFile = readUniversalFile(file)
        val selectedGuidSet = request.selectedGuids.toSet()
        val toLoad = universalFile.macrosArray.filter { it.guid() in selectedGuidSet }

        val currentGuidSet = request.macrosArray.map { it.guid() }.toSet()
        val conflicts = toLoad.filter { it.guid() in currentGuidSet }.map { it.name() }

        val updated = mutableListOf<String>()
        val added = mutableListOf<String>()

        var newMacros = request.macrosArray.map { macro ->
            val incoming = toLoad.find { it.guid() == macro.guid() }
            if (incoming != null) {
                updated += macro.name()
                incoming.withUniversal()
            } else {
                macro
            }
        }.toMutableList()

        val newOnes = toLoad.filter { it.guid() !in currentGuidSet }
        newOnes.forEach { added += it.name() }
        if (newOnes.isNotEmpty()) {
            newMacros = (newOnes.map { it.withUniversal() } + newMacros).toMutableList()
        }

        return MacroSyncResponse(
            macrosArray = ensureSeparator(newMacros),
            conflicts = conflicts,
            updated = updated,
            added = added,
        )
    }

    fun push(request: MacroSyncRequest): MacroSyncResponse {
        val file = File(request.directoryPath, request.fileName)

        val universalsToSync = request.macrosArray.filter { it.isUniversal() && !it.isSeparator() }
        val universalsToSyncByGuid = universalsToSync.associateBy { it.guid() }

        val existingMacros = if (file.exists()) readUniversalFile(file).macrosArray else emptyList()
        val existingByGuid = existingMacros.associateBy { it.guid() }

        val updated = mutableListOf<String>()
        val added = mutableListOf<String>()

        val resultMacros = existingMacros.map { existing ->
            val incoming = universalsToSyncByGuid[existing.guid()]
            if (incoming != null) {
                updated += incoming.name()
                incoming.withUniversal()
            } else {
                existing
            }
        }.toMutableList()

        universalsToSync.forEach { incoming ->
            if (incoming.guid() !in existingByGuid) {
                added += incoming.name()
                resultMacros += incoming.withUniversal()
            }
        }

        writeUniversalFile(file, resultMacros)

        return MacroSyncResponse(
            updated = updated,
            added = added,
            totalUniversal = resultMacros.size,
        )
    }

    fun delete(request: MacroSyncRequest): MacroSyncResponse {
        val file = File(request.directoryPath, request.fileName)

        if (!file.exists()) {
            throw NotFoundException("Universal macros file not found: ${file.absolutePath}.")
        }

        val universalFile = readUniversalFile(file)
        val toDeleteGuids = request.selectedGuids.toSet()
        val deleted = universalFile.macrosArray.filter { it.guid() in toDeleteGuids }.map { it.name() }
        val remaining = universalFile.macrosArray.filter { it.guid() !in toDeleteGuids }

        writeUniversalFile(file, remaining)

        return MacroSyncResponse(
            deleted = deleted,
            totalUniversal = remaining.size,
        )
    }

    private fun ensureSeparator(macros: List<JsonObject>): List<JsonObject> {
        val without = macros.filter { !it.isSeparator() }
        val universal = without.filter { it.isUniversal() }
        val regular = without.filter { !it.isUniversal() }
        if (universal.isEmpty()) return without
        return universal + SEPARATOR_OBJECT + regular
    }

    private fun readUniversalFile(file: File): UniversalMacrosFile {
        return try {
            json.decodeFromString<UniversalMacrosFile>(file.readText())
        } catch (e: Exception) {
            logger.error("Failed to parse universal macros file: {}", file.absolutePath, e)
            throw IllegalStateException("Failed to parse universal macros file: ${e.message}", e)
        }
    }

    private fun writeUniversalFile(file: File, macros: List<JsonObject>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(UniversalMacrosFile(macros)))
        } catch (e: Exception) {
            logger.error("Failed to write universal macros file: {}", file.absolutePath, e)
            throw IllegalStateException("Failed to write universal macros file: ${e.message}", e)
        }
    }
}

private fun JsonObject.guid(): String = this["guid"]?.jsonPrimitive?.contentOrNull ?: ""
private fun JsonObject.name(): String = this["name"]?.jsonPrimitive?.contentOrNull ?: ""
private fun JsonObject.isUniversal(): Boolean = this["isUniversal"]?.jsonPrimitive?.booleanOrNull == true
private fun JsonObject.isSeparator(): Boolean =
    this["isSeparator"]?.jsonPrimitive?.booleanOrNull == true || guid() == SEPARATOR_GUID

private fun JsonObject.withUniversal(): JsonObject = buildJsonObject {
    this@withUniversal.forEach { (k, v) -> put(k, v) }
    put("isUniversal", true)
}
