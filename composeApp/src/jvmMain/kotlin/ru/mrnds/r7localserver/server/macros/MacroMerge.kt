package ru.mrnds.r7localserver.server.macros

import kotlinx.serialization.json.*
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.merge.MergeAlgorithm
import org.eclipse.jgit.merge.MergeChunk.ConflictState

internal object MacroMerge {
    fun change(guid: String, base: JsonObject?, document: JsonObject?, library: JsonObject?, pull: Boolean): MacroChange {
        val ours = document?.clean()
        val theirs = library?.clean()
        if (pull) {
            // Loading uses the library as the source of truth; publishing still uses the base for merging.
            val kind = when {
                ours == theirs -> "unchanged"
                ours == null -> "added"
                theirs == null -> "deleted"
                else -> "modified"
            }
            return MacroChange(guid, (theirs ?: ours ?: base)?.string("name") ?: guid,
                kind, base, ours, theirs, theirs, diff = diff(ours?.string("value") ?: "", theirs?.string("value") ?: ""))
        }
        val conflicts = mutableListOf<String>()
        var chunks = emptyList<CodeChunk>()
        val result = when {
            ours == theirs -> ours
            base == null && ours != null && theirs != null -> {
                conflicts += "base"
                ours
            }
            base == null -> ours ?: theirs
            ours == base -> theirs
            theirs == base -> ours
            ours == null || theirs == null -> {
                conflicts += "deletion"
                ours
            }
            else -> buildJsonObject {
                (base.keys + ours.keys + theirs.keys).forEach { key ->
                    val b = base[key]; val d = ours[key]; val l = theirs[key]
                    val merged = when {
                        d == l -> d
                        d == b -> l
                        l == b -> d
                        key == "value" && listOf(b, d, l).all { it is JsonPrimitive && it.isString } -> {
                            chunks = mergeCode(b!!.jsonPrimitive.content, d!!.jsonPrimitive.content, l!!.jsonPrimitive.content)
                            if (chunks.any { it.document != null }) conflicts += key
                            JsonPrimitive(chunks.joinToString("") { it.document ?: it.text })
                        }
                        else -> { conflicts += key; d }
                    }
                    if (merged != null) put(key, merged)
                }
            }
        }
        val target = theirs
        val kind = when {
            conflicts.isNotEmpty() -> "conflict"
            target == result -> "unchanged"
            target == null -> "added"
            result == null -> "deleted"
            else -> "modified"
        }
        return MacroChange(guid, (result ?: ours ?: theirs ?: base)?.string("name") ?: guid,
            kind, base, ours, theirs, result, conflicts, chunks,
            diff(target?.string("value") ?: "", result?.string("value") ?: ""))
    }

    fun diff(before: String, after: String): List<DiffLine> {
        val a = RawText(before.toByteArray(Charsets.UTF_8)); val b = RawText(after.toByteArray(Charsets.UTF_8))
        val edits = HistogramDiff().diff(RawTextComparator.DEFAULT, a, b)
        val result = mutableListOf<DiffLine>()
        var old = 0; var new = 0
        for (edit in edits) {
            while (old < edit.beginA) result += DiffLine("same", a.getString(old), ++old, ++new)
            while (old < edit.endA) result += DiffLine("removed", a.getString(old), ++old, null)
            while (new < edit.endB) result += DiffLine("added", b.getString(new), null, ++new)
        }
        while (old < a.size()) result += DiffLine("same", a.getString(old), ++old, ++new)
        return result
    }

    private fun mergeCode(base: String, document: String, library: String): List<CodeChunk> {
        val texts = listOf(base, document, library).map { RawText(it.toByteArray(Charsets.UTF_8)) }
        val merged = MergeAlgorithm().merge(RawTextComparator.DEFAULT, texts[0], texts[1], texts[2])
        val result = mutableListOf<CodeChunk>()
        for (chunk in merged) {
            // JGit may return an invalid range for this bookkeeping chunk; it is never part of the result.
            if (chunk.conflictState == ConflictState.BASE_CONFLICTING_RANGE) continue
            val raw = texts[chunk.sequenceIndex]
            val text = if (chunk.begin == chunk.end) "" else raw.getString(chunk.begin, chunk.end, false)
            when (chunk.conflictState) {
                ConflictState.BASE_CONFLICTING_RANGE -> Unit
                ConflictState.NO_CONFLICT -> result += CodeChunk(text = text)
                ConflictState.FIRST_CONFLICTING_RANGE -> result += CodeChunk(document = text, library = "")
                ConflictState.NEXT_CONFLICTING_RANGE -> {
                    val previous = result.removeAt(result.lastIndex)
                    result += previous.copy(library = previous.library.orEmpty() + text)
                }
            }
        }
        return result
    }
}
