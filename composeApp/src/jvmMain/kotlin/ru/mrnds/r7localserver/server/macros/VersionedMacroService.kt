package ru.mrnds.r7localserver.server.macros

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.mrnds.r7localserver.platform.AppDirectories
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VersionedMacroService {
    private val store = MacroHistoryStore()
    private data class Preview(
        val request: PreviewRequest, val file: File, val fingerprint: String, val root: JsonObject,
        val changes: List<MacroChange>, val operation: String, val expires: Instant,
    )
    private val previews = ConcurrentHashMap<String, Preview>()

    fun state(request: LibraryRequest): LibraryState {
        val file = store.file(request.directoryPath, request.fileName)
        val root = store.read(file)
        return LibraryState(root, store.meta(root) != null, store.external(file, root))
    }

    fun saveOrder(request: OrderRequest): LibraryState {
        val file = store.file(request.directoryPath, request.fileName)
        return store.locked(file) {
            val root = store.read(file)
            val revision = store.meta(root)?.string("revisionId")
                ?: throw MacroConflict("Initialize version history before reordering macros")
            if (store.external(file, root)) throw MacroConflict("External changes detected: review and register them first")
            if (revision != request.revisionId) throw MacroConflict("Library changed since it was opened; refresh and retry")
            val macros = (root["macrosArray"] as JsonArray).map { it.jsonObject }
            val currentGuids = macros.map { it.guid() }
            if (currentGuids == request.orderedGuids) return@locked LibraryState(root, true, false)
            if (currentGuids != request.expectedGuids) throw MacroConflict("Library order changed; refresh and retry")
            require(request.orderedGuids.size == currentGuids.size && request.orderedGuids.toSet().size == currentGuids.size &&
                request.orderedGuids.toSet() == currentGuids.toSet()) { "Order must contain every library macro exactly once" }
            val separators = macros.indices.filter { macros[it].flag("isSeparator") || macros[it].guid() == SEPARATOR_ID }
            var start = 0
            for (end in separators + macros.size) {
                require(request.orderedGuids.subList(start, end).toSet() == currentGuids.subList(start, end).toSet()) {
                    "Macros cannot cross a separator"
                }
                if (end < macros.size) require(request.orderedGuids[end] == currentGuids[end]) { "Separator cannot move" }
                start = end + 1
            }
            val byGuid = macros.associateBy { it.guid() }
            val next = root.withMacros(request.orderedGuids.map { byGuid.getValue(it) })
            store.atomicWrite(file, macroJson.encodeToString(next))
            LibraryState(next, true, false)
        }
    }

    fun history(request: LibraryRequest): List<MacroRevision> = store.revisions(store.file(request.directoryPath, request.fileName))
    fun revision(request: LibraryRequest): MacroRevision = history(request).firstOrNull { it.id == request.revisionId }
        ?: throw MacroConflict("Revision is not in the published history")

    fun preview(request: PreviewRequest): PreviewResponse {
        require(request.action in setOf("push", "pull", "delete", "restore", "import", "restoreDocument")) { "Invalid action" }
        val file = store.file(request.directoryPath, request.fileName)
        val documentOnly = request.action == "restoreDocument"
        val before = if (documentOnly) "" else store.fingerprint(file)
        val root = if (documentOnly) buildJsonObject { put("macrosArray", JsonArray(emptyList())) } else store.read(file)
        if (!documentOnly && store.fingerprint(file) != before) throw MacroConflict("Library changed while reading; refresh")
        if (request.action !in setOf("import", "restoreDocument") && store.external(file, root)) throw MacroConflict("External changes detected: review and register them first")
        if (request.action == "pull" && store.meta(root) == null) throw MacroConflict("Initialize version history before loading macros")
        val document = request.document.macros().associateBy { it.guid() }
        val library = root.macros().associateBy { it.guid() }
        val libraryId = store.libraryId(root)
        require(request.selectedGuids.distinct().size == request.selectedGuids.size) { "Duplicate selected GUID" }
        val changes = when (request.action) {
            "restoreDocument" -> {
                val replacement = requireNotNull(request.replacementDocument) { "Backup document is required" }.macros().associateBy { it.guid() }
                (document.keys + replacement.keys).map { guid -> directChange(guid, document[guid], replacement[guid]) }
            }
            "import", "restore" -> {
                val current = if (request.action == "import") {
                    store.meta(root)?.string("revisionId")?.let { store.revision(file, it).library } ?: root.withMacros(emptyList())
                } else root
                val incoming = if (request.action == "restore") revision(LibraryRequest(request.directoryPath, request.fileName, request.revisionId)).library else root
                val old = current.macros().associateBy { it.guid() }; val next = incoming.macros().associateBy { it.guid() }
                (old.keys + next.keys).map { guid -> directChange(guid, old[guid], next[guid]) }
            }
            else -> request.selectedGuids.map { guid ->
                val doc = document[guid]; val lib = library[guid]
                require(doc != null || lib != null) { "Unknown GUID: $guid" }
                if (request.action == "push") require(doc?.flag("isUniversal") == true) { "Only universal macros can be published" }
                if (request.action == "delete") directChange(guid, lib, null)
                else {
                    val sync = doc?.get(SYNC_META) as? JsonObject
                    val base = if (sync?.string("libraryId") == libraryId && sync["schemaVersion"] == JsonPrimitive(1))
                        (sync["base"] as? JsonObject)?.takeIf { it.guid() == guid }?.clean() else null
                    MacroMerge.change(guid, base, doc, lib, request.action == "pull")
                }
            }
        }
        previews.entries.removeIf { it.value.expires.isBefore(Instant.now()) }
        if (previews.size >= 256) throw MacroConflict("Too many open previews; close old previews and retry later")
        val token = UUID.randomUUID().toString(); val operation = UUID.randomUUID().toString()
        val expires = Instant.now().plusSeconds(1800)
        previews[token] = Preview(request, file, before, root, changes, operation, expires)
        return PreviewResponse(token, operation, changes, expires.toString())
    }

    private fun directChange(guid: String, old: JsonObject?, next: JsonObject?): MacroChange = MacroChange(
        guid, (next ?: old)?.string("name") ?: guid,
        when { old?.clean() == next?.clean() -> "unchanged"; next == null -> "deleted"; old == null -> "added"; else -> "modified" },
        old?.clean(), next?.clean(), old?.clean(), next?.clean(),
        diff = MacroMerge.diff(old?.string("value") ?: "", next?.string("value") ?: ""),
    )

    fun apply(request: ApplyRequest): ApplyResponse {
        require(runCatching { UUID.fromString(request.operationId) }.isSuccess) { "Invalid operation ID" }
        val file = store.file(request.directoryPath, request.fileName)
        previews[request.token]?.takeIf { it.request.action == "restoreDocument" }?.let { preview ->
            validatePreview(preview, request, file)
            require(request.resolutions.isEmpty()) { "Document restoration cannot edit resolutions" }
            require(request.selectedGuids.toSet() == preview.changes.map { it.guid }.toSet()) { "Restore must include the entire snapshot" }
            return documentResponse(preview, request, preview.request.document, preview.request.replacementDocument!!)
        }
        return store.locked(file) {
            val root = store.read(file)
            store.revisions(file, root).firstOrNull { it.operationId == request.operationId }?.let {
                return@locked ApplyResponse(revisionId = it.id, changed = true)
            }
            val preview = previews[request.token] ?: throw MacroConflict("Preview expired or server restarted; review changes again")
            validatePreview(preview, request, file)
            if (store.fingerprint(file) != preview.fingerprint) throw MacroConflict("Library changed since preview; review changes again")
            val selected = request.selectedGuids.toSet()
            require(selected.size == request.selectedGuids.size && selected.all { id -> preview.changes.any { it.guid == id } }) { "Invalid selection" }
            require(request.resolutions.keys.all { it in selected }) { "Resolution is not selected" }
            if (preview.request.action in setOf("import", "restore", "restoreDocument")) {
                require(selected == preview.changes.map { it.guid }.toSet()) { "Import and restore must include the entire snapshot" }
            }
            if (preview.request.action in setOf("import", "restore", "restoreDocument", "delete")) require(request.resolutions.isEmpty()) { "This operation cannot edit resolutions" }
            val results = preview.changes.filter { it.guid in selected }.associate { change ->
                require(change.conflicts.isEmpty() || request.resolutions.containsKey(change.guid)) { "Unresolved conflict: ${change.name}" }
                val result = if (request.resolutions.containsKey(change.guid)) request.resolutions.getValue(change.guid).macro?.clean() else change.result
                if (result != null) {
                    require(result.guid() == change.guid) { "Resolution cannot change GUID" }
                    buildJsonObject { put("macrosArray", JsonArray(listOf(result))) }.macros()
                }
                change.guid to result
            }
            if (preview.request.action in setOf("pull", "restoreDocument")) return@locked prepareDocument(preview, request, results)
            val next = if (preview.request.action == "import") root else if (preview.request.action == "restore") {
                val source = revision(LibraryRequest(request.directoryPath, request.fileName, preview.request.revisionId)).library
                JsonObject(source.toMutableMap().apply { root[LIBRARY_META]?.let { put(LIBRARY_META, it) } })
            } else root.withMacros(replace(root.macros(), results.mapValues { (_, macro) ->
                macro?.let { JsonObject(it.clean() + ("isUniversal" to JsonPrimitive(true))) }
            }))
            store.commit(file, root, next, request.operationId, request.comment,
                force = preview.request.action == "import" && (store.meta(root) == null || store.external(file, root)))
        }
    }

    private fun validatePreview(preview: Preview, request: ApplyRequest, file: File) {
        if (preview.file != file || preview.operation != request.operationId || preview.expires.isBefore(Instant.now()))
            throw MacroConflict("Preview expired or does not match this operation")
    }

    private fun prepareDocument(preview: Preview, request: ApplyRequest, results: Map<String, JsonObject?>): ApplyResponse {
        val original = preview.request.document
        if (preview.request.action == "restoreDocument") return documentResponse(preview, request, original, preview.request.replacementDocument!!)
        val source = preview.root.macros().associateBy { it.guid() }
        val current = original.macros().associateBy { it.guid() }
        val prepared = results.mapValues { (guid, result) -> result?.let {
            JsonObject(it.toMutableMap().apply {
                put("isUniversal", JsonPrimitive(true))
                current[guid]?.get("isExcludedFromAutoSync")?.let { flag -> put("isExcludedFromAutoSync", flag) }
                put(SYNC_META, buildJsonObject {
                    put("schemaVersion", 1); put("libraryId", store.libraryId(preview.root))
                    put("revisionId", store.meta(preview.root)!!.string("revisionId"))
                    put("base", source[guid]?.clean() ?: JsonNull)
                })
            })
        } }
        val macros = replace(original.macros(), prepared)
        val universal = macros.filter { it.flag("isUniversal") }
        val separator = buildJsonObject { put("name", " "); put("guid", SEPARATOR_ID); put("value", ""); put("autostart", false); put("isSeparator", true) }
        val next = if (results.isEmpty()) original
            else original.withMacros(if (universal.isEmpty()) macros else universal + separator + macros.filterNot { it.flag("isUniversal") })
        return documentResponse(preview, request, original, next)
    }

    private fun documentResponse(preview: Preview, request: ApplyRequest, original: JsonObject, next: JsonObject): ApplyResponse {
        val changed = next != original
        val backup = if (changed) {
            val directory = if (request.backupDirectory.isBlank()) File(AppDirectories.stateDirectory, "macro-backups") else File(request.backupDirectory)
            directory.mkdirs()
            val file = File(directory, "macros-${request.operationId}.json")
            if (file.exists()) {
                if (macroJson.parseToJsonElement(file.readText(Charsets.UTF_8)) != original) throw MacroConflict("Backup operation already exists with different data")
            } else store.atomicWrite(file, macroJson.encodeToString(original))
            file.absolutePath
        } else null
        return ApplyResponse(store.meta(preview.root)?.string("revisionId"), next, original, backup, changed)
    }

    private fun replace(old: List<JsonObject>, replacements: Map<String, JsonObject?>): List<JsonObject> {
        val ids = old.map { it.guid() }.toSet()
        return old.mapNotNull { if (replacements.containsKey(it.guid())) replacements[it.guid()] else it } +
            replacements.filterKeys { it !in ids }.values.filterNotNull()
    }
}
