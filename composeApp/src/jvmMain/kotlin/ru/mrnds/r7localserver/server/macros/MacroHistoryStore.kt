package ru.mrnds.r7localserver.server.macros

import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class MacroHistoryStore {
    @Serializable
    private data class Pending(val fingerprint: String, val records: List<MacroRevision>, val next: JsonObject)
    companion object {
        private val locks = ConcurrentHashMap<String, ReentrantLock>()
        fun history(file: File) = File(file.parentFile, file.name + ".history")
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun protect(file: File) {
            val canonical = file.canonicalFile
            val parentIsHistory = generateSequence(canonical.parentFile) { it.parentFile }
                .any { it.name.endsWith(".history") && (File(it, ".managed").exists() || File(it, ".pending.json").exists() || File(it.parentFile, it.name.removeSuffix(".history")).exists()) }
            val isLock = canonical.name.endsWith(".r7lock") && history(File(canonical.parentFile, canonical.name.removeSuffix(".r7lock"))).exists()
            if (parentIsHistory || isLock || history(canonical).exists() ||
                (canonical.isFile && canonical.extension.equals("json", true) && runCatching {
                    macroJson.parseToJsonElement(canonical.readText()).jsonObject.containsKey(LIBRARY_META)
                }.getOrDefault(false))) throw MacroConflict("Versioned library: use Macros Sync v2; direct writes are disabled")
        }
    }

    fun file(directory: String, name: String): File {
        require(directory.isNotBlank() && name.isNotBlank()) { "Directory and file name are required" }
        require(name == File(name).name && name !in setOf(".", "..")) { "fileName must be a file name" }
        return File(directory, name).canonicalFile
    }

    fun read(file: File): JsonObject = if (file.exists()) {
        val root = macroJson.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        root.macros()
        val meta = root[LIBRARY_META]
        if (meta != null) require(meta.jsonObject["schemaVersion"] == JsonPrimitive(1)) { "Unsupported library schema" }
        root
    } else buildJsonObject { put("macrosArray", JsonArray(emptyList())) }

    fun fingerprint(file: File): String = if (file.exists()) hash(file.readBytes()) else "missing"
    fun meta(root: JsonObject): JsonObject? = root[LIBRARY_META] as? JsonObject
    fun libraryId(root: JsonObject): String = meta(root)?.string("libraryId")?.takeIf { it.isNotBlank() }
        ?: UUID.nameUUIDFromBytes(root.toString().toByteArray(Charsets.UTF_8)).toString()

    fun revision(file: File, id: String): MacroRevision {
        require(runCatching { UUID.fromString(id) }.isSuccess) { "Invalid revision ID" }
        val record = File(history(file), "$id.json")
        if (!record.isFile) throw MacroConflict("History revision is missing: $id")
        val revision = macroJson.decodeFromString<MacroRevision>(record.readText(Charsets.UTF_8))
        require(revision.id == id) { "Corrupt revision" }
        return revision
    }

    fun revisions(file: File, root: JsonObject = read(file)): List<MacroRevision> {
        var id = meta(root)?.string("revisionId")?.takeIf { it.isNotEmpty() }
        if (id == null && File(history(file), ".managed").exists()) throw MacroConflict("Library metadata missing; restore library from a known history snapshot")
        val seen = mutableSetOf<String>()
        val records = mutableListOf<MacroRevision>()
        while (id != null) {
            if (!seen.add(id)) throw MacroConflict("History cycle detected")
            val record = revision(file, id)
            if (record.libraryId != libraryId(root)) throw MacroConflict("History belongs to another library")
            records += record
            id = record.parent
        }
        return records
    }

    fun external(file: File, root: JsonObject): Boolean {
        val id = meta(root)?.string("revisionId")?.takeIf { it.isNotEmpty() }
        if (id == null) {
            if (File(history(file), ".managed").exists()) throw MacroConflict("Library metadata missing; restore library from a known history snapshot")
            return false
        }
        return withoutOrdering(revision(file, id).library) != withoutOrdering(root)
    }

    private fun withoutOrdering(root: JsonObject): JsonObject {
        val entries = (root["macrosArray"] as JsonArray).map { it.jsonObject }
        val normalized = mutableListOf<JsonObject>()
        val group = mutableListOf<JsonObject>()
        fun flush() { normalized.addAll(group.sortedBy { it.guid() }); group.clear() }
        for (macro in entries) {
            if (macro.flag("isSeparator") || macro.guid() == SEPARATOR_ID) {
                flush()
                normalized.add(macro)
            } else group.add(macro)
        }
        flush()
        return root.withMacros(normalized)
    }

    fun <T> locked(file: File, action: () -> T): T {
        file.parentFile.mkdirs()
        return locks.computeIfAbsent(file.path) { ReentrantLock() }.withLock {
            FileChannel.open(File(file.parentFile, file.name + ".r7lock").toPath(), CREATE, WRITE).use { channel ->
                val lock = channel.tryLock() ?: throw MacroConflict("Library is busy; retry after refreshing")
                lock.use { recover(file); action() }
            }
        }
    }

    fun commit(file: File, previous: JsonObject, next: JsonObject, operationId: String, comment: String, force: Boolean = false): ApplyResponse {
        if (!force && previous == next && meta(previous) != null) return ApplyResponse(meta(previous)!!.string("revisionId"))
        val libraryId = libraryId(previous)
        var parent = meta(previous)?.string("revisionId")?.takeIf { it.isNotEmpty() }
        history(file).mkdirs()
        val records = mutableListOf<MacroRevision>()
        if (parent == null && file.exists()) {
            parent = UUID.randomUUID().toString()
            records += record(parent, null, libraryId, "initial-$operationId", "Initial snapshot", previous)
        }
        val id = UUID.randomUUID().toString()
        val root = JsonObject(next.toMutableMap().apply {
            put(LIBRARY_META, buildJsonObject {
                put("schemaVersion", 1); put("libraryId", libraryId); put("revisionId", id)
            })
        })
        records += record(id, parent, libraryId, operationId, comment, root)
        atomicWrite(File(history(file), ".pending.json"), macroJson.encodeToString(Pending(fingerprint(file), records, root)))
        recover(file)
        return ApplyResponse(revisionId = id, changed = true)
    }

    // Only called under the library lock, completing a previously confirmed operation.
    private fun recover(file: File) {
        val journal = File(history(file), ".pending.json")
        if (!journal.exists()) return
        val pending = macroJson.decodeFromString<Pending>(journal.readText(Charsets.UTF_8))
        val alreadyPublished = runCatching { read(file) == pending.next }.getOrDefault(false)
        if (!alreadyPublished && fingerprint(file) != pending.fingerprint)
            throw MacroConflict("Library changed during interrupted commit; manual recovery required; history and journal preserved")
        pending.records.forEach { record ->
            val saved = File(history(file), "${record.id}.json")
            if (saved.exists()) {
                if (revision(file, record.id) != record) throw MacroConflict("History revision was modified")
            } else saveRecord(file, record)
        }
        if (!alreadyPublished) atomicWrite(file, macroJson.encodeToString(pending.next))
        atomicWrite(File(history(file), ".managed"), libraryId(pending.next))
        Files.delete(journal.toPath())
    }

    private fun record(id: String, parent: String?, libraryId: String, operation: String, comment: String, root: JsonObject) =
        MacroRevision(id, parent, libraryId, Instant.now().toString(),
            "${System.getProperty("user.name")}@${System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "local"}", operation, comment, root)

    private fun saveRecord(file: File, record: MacroRevision) = atomicWrite(File(history(file), "${record.id}.json"), macroJson.encodeToString(record))

    internal fun atomicWrite(file: File, text: String) {
        val temp = Files.createTempFile(file.parentFile.toPath(), ".r7-", ".tmp")
        try {
            FileChannel.open(temp, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
