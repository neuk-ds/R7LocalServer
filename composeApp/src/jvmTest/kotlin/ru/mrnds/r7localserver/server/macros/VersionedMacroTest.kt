package ru.mrnds.r7localserver.server.macros

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.*

class VersionedMacroTest {
    private fun macro(code: String = "a\nb\nc\n", id: String = "one", universal: Boolean = true) = buildJsonObject {
        put("guid", id); put("name", id); put("value", code); put("autostart", false); put("isUniversal", universal)
    }
    private fun document(vararg macros: JsonObject) = buildJsonObject { put("macrosArray", JsonArray(macros.toList())); put("current", 0) }
    private fun code(macro: JsonObject, value: String) = JsonObject(macro + ("value" to JsonPrimitive(value)))
    private class Fixture : AutoCloseable {
        val directory = Files.createTempDirectory("r7-macro-test-").toFile()
        val file = File(directory, "library.json")
        val service = VersionedMacroService()
        val location = LibraryRequest(directory.path, file.name)
        fun preview(action: String, doc: JsonObject = buildJsonObject { put("macrosArray", JsonArray(emptyList())) }, ids: List<String> = listOf("one"), revision: String? = null) =
            service.preview(PreviewRequest(directory.path, file.name, action, doc, ids, revision))
        fun request(preview: PreviewResponse) = ApplyRequest(directory.path, file.name, preview.token, preview.operationId, preview.changes.map { it.guid }, backupDirectory = File(directory, "backups").path)
        fun apply(preview: PreviewResponse) = service.apply(request(preview))
        override fun close() { directory.deleteRecursively() }
    }

    @Test fun `independent edits are merged preserving newlines`() {
        for (newline in listOf("\n", "\r\n")) {
            val base = macro("a${newline}b${newline}c${newline}").clean()
            val result = MacroMerge.change("one", base, code(base, "A${newline}b${newline}c${newline}"), code(base, "a${newline}b${newline}C${newline}"), false)
            assertEquals(emptyList(), result.conflicts)
            assertEquals("A${newline}b${newline}C${newline}", result.result!!.string("value"))
        }
        val base = macro("a\nb\nc").clean()
        assertEquals("A\nb\nC", MacroMerge.change("one", base, code(base, "A\nb\nc"), code(base, "a\nb\nC"), false).result!!.string("value"))
    }

    @Test fun `another JVM holds the library lock and prevents writing`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        val snapshot = f.file.readBytes()
        val preview = f.preview("delete")
        val javaFile = File(f.directory, "LockHolder.java")
        javaFile.writeText("""
            import java.nio.file.*;
            import java.nio.channels.*;
            public class LockHolder {
                public static void main(String[] args) throws Exception {
                    try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                         FileLock lock = channel.lock()) {
                        Files.writeString(Path.of(args[1]), "ready");
                        System.in.read();
                    }
                }
            }
        """.trimIndent())
        val javaExecutable = File(System.getProperty("java.home"), "bin/" + if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val ready = File(f.directory, "ready")
        val process = ProcessBuilder(javaExecutable.path, javaFile.path, File(f.directory, f.file.name + ".r7lock").path, ready.path)
            .redirectErrorStream(true).redirectOutput(File(f.directory, "child.log")).start()
        try {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
            while (!ready.exists() && process.isAlive && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(ready.exists(), File(f.directory, "child.log").readText())
            assertFailsWith<MacroConflict> { f.apply(preview) }
            assertContentEquals(snapshot, f.file.readBytes())
        } finally {
            process.outputStream.close()
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
        }
        assertTrue(f.apply(preview).changed)
    }

    @Test fun `backup failure prevents document preparation`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        val preview = f.preview("pull", document())
        val blocked = File(f.directory, "not-a-directory").apply { writeText("occupied") }
        val before = f.file.readBytes()
        assertFails { f.service.apply(f.request(preview).copy(backupDirectory = blocked.path)) }
        assertContentEquals(before, f.file.readBytes())
    }

    @Test fun `orphan history files are not published and empty selection leaves document unchanged`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        File(MacroHistoryStore.history(f.file), UUID.randomUUID().toString() + ".json").writeText("invalid orphan")
        assertEquals(1, f.service.history(f.location).size)
        val original = document(macro())
        val preview = f.preview("pull", original)
        val result = f.service.apply(f.request(preview).copy(selectedGuids = emptyList()))
        assertFalse(result.changed)
        assertEquals(original, result.document)
        assertNull(result.backupPath)
    }

    @Test fun `overlap and missing base require resolution`() {
        val base = macro().clean()
        val overlap = MacroMerge.change("one", base, code(base, "A\nb\nc\n"), code(base, "X\nb\nc\n"), true)
        assertEquals(listOf("value"), overlap.conflicts)
        assertTrue(overlap.chunks.any { it.document == "A\n" && it.library == "X\n" })
        assertEquals(listOf("base"), MacroMerge.change("one", null, base, code(base, "changed"), true).conflicts)
        assertEquals(listOf("deletion"), MacroMerge.change("one", base, code(base, "changed"), null, true).conflicts)
    }

    @Test fun `unknown fields merge independently and conflicting names are reported`() {
        val base = macro().clean()
        val ours = JsonObject(base + mapOf("name" to JsonPrimitive("ours"), "custom" to JsonPrimitive(1)))
        val theirs = JsonObject(base + ("name" to JsonPrimitive("theirs")))
        val result = MacroMerge.change("one", base, ours, theirs, false)
        assertEquals(listOf("name"), result.conflicts)
        assertEquals(JsonPrimitive(1), result.result!!["custom"])
    }

    @Test fun `preview is read only and migration retains original snapshot`() = Fixture().use { f ->
        val legacy = document(macro())
        f.file.writeText(legacy.toString())
        val preview = f.preview("import")
        assertEquals(listOf(f.file.name), f.directory.listFiles()!!.map { it.name })
        assertEquals(legacy, macroJson.parseToJsonElement(f.file.readText()))
        val response = f.apply(preview)
        assertTrue(response.changed)
        val history = f.service.history(f.location)
        assertEquals(2, history.size)
        assertEquals(legacy, history.last().library)
        assertEquals(history.first().id, f.service.state(f.location).library[LIBRARY_META]!!.jsonObject.string("revisionId"))
        assertEquals(response.revisionId, VersionedMacroService().apply(f.request(preview)).revisionId)
        assertEquals(2, f.service.history(f.location).size)
    }

    @Test fun `push keeps document untouched and pull preserves unpublished local edits`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        val loaded = f.apply(f.preview("pull", document(macro()))).document!!
        val tracked = loaded.macros().single()
        val local = code(tracked, "A\nb\nc\n")
        val remote = code(tracked, "a\nb\nC\n")
        f.apply(f.preview("push", document(remote)))
        val localDoc = document(local, macro("private", "private", false))
        val pull = f.preview("pull", localDoc)
        assertEquals("A\nb\nC\n", pull.changes.single().result!!.string("value"))
        val result = f.apply(pull)
        assertNotNull(result.backupPath)
        assertEquals(localDoc, macroJson.parseToJsonElement(File(result.backupPath).readText()))
        val merged = result.document!!.macros().first()
        assertEquals("A\nb\nC\n", merged.string("value"))
        assertEquals("a\nb\nC\n", merged[SYNC_META]!!.jsonObject["base"]!!.jsonObject.string("value"))
        assertEquals("private", result.document.macros().last().string("value"))
        val push = f.preview("push", localDoc)
        val saved = f.apply(push)
        assertNull(saved.document)
        assertEquals("A\nb\nc\n", localDoc.macros().first().string("value"))
        assertEquals(tracked[SYNC_META], localDoc.macros().first()[SYNC_META])
    }

    @Test fun `stale previews and competing writers never overwrite latest revision`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        val base = f.apply(f.preview("pull", document(macro()))).document!!
        val first = f.preview("push", document(code(base.macros().single(), "first")))
        val secondService = VersionedMacroService()
        val second = secondService.preview(PreviewRequest(f.directory.path, f.file.name, "push", document(code(base.macros().single(), "second")), listOf("one")))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val outcomes = pool.invokeAll(listOf(
                java.util.concurrent.Callable { runCatching { f.apply(first) } },
                java.util.concurrent.Callable { runCatching { secondService.apply(f.request(second)) } },
            )).map { it.get() }
            assertEquals(1, outcomes.count { it.isSuccess })
            assertIs<MacroConflict>(outcomes.single { it.isFailure }.exceptionOrNull())
            assertEquals(2, f.service.history(f.location).size)
        } finally { pool.shutdownNow() }
    }

    @Test fun `external changes must be registered and can be restored`() = Fixture().use { f ->
        val initial = f.apply(f.preview("push", document(macro())))
        val current = f.service.state(f.location).library
        f.file.writeText(current.withMacros(listOf(macro("external"))).toString())
        assertTrue(f.service.state(f.location).externalChanges)
        assertFailsWith<MacroConflict> { f.preview("push", document(macro())) }
        f.apply(f.preview("import"))
        assertFalse(f.service.state(f.location).externalChanges)
        val restored = f.apply(f.preview("restore", revision = initial.revisionId))
        assertTrue(restored.changed)
        assertEquals("a\nb\nc\n", f.service.state(f.location).library.macros().single().string("value"))
        assertEquals(3, f.service.history(f.location).size)
    }

    @Test fun `unresolved conflicts invalid GUIDs and direct writes are blocked`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        val conflict = f.preview("push", document(macro("different")))
        assertFailsWith<IllegalArgumentException> { f.apply(conflict) }
        assertFailsWith<IllegalArgumentException> { f.preview("push", document(macro(), macro())) }
        assertFailsWith<IllegalArgumentException> { f.preview("push", document(macro(id = ""))) }
        assertFailsWith<MacroConflict> { MacroHistoryStore.protect(f.file) }
        assertFailsWith<MacroConflict> { MacroHistoryStore.protect(File(MacroHistoryStore.history(f.file), "fake.json")) }
        val resolved = f.request(conflict).copy(resolutions = mapOf("one" to Resolution(macro("merged"))))
        f.service.apply(resolved)
        assertEquals("merged", f.service.state(f.location).library.macros().single().string("value"))
    }

    @Test fun `unchanged publish does not create a revision and absence does not delete others`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro(), macro("two", "two")), listOf("one", "two")))
        val result = f.apply(f.preview("push", document(macro())))
        assertFalse(result.changed)
        assertEquals(1, f.service.history(f.location).size)
        assertEquals(2, f.service.state(f.location).library.macros().size)
    }

    @Test fun `document restore retains exact backup and backs up current document`() = Fixture().use { f ->
        f.file.writeText("broken library must not block document recovery")
        val current = document(macro("current")); val backup = document(macro("backup", "other"))
        val preview = f.service.preview(PreviewRequest(f.directory.path, f.file.name, "restoreDocument", current, replacementDocument = backup))
        val response = f.apply(preview)
        assertEquals(backup, response.document)
        assertEquals(current, macroJson.parseToJsonElement(File(response.backupPath!!).readText()))
        assertEquals("broken library must not block document recovery", f.file.readText())
        assertFalse(File(f.directory, f.file.name + ".r7lock").exists())
    }

    @Test fun `interrupted commit journal is completed once on retry`() = Fixture().use { f ->
        f.apply(f.preview("push", document(macro())))
        val old = f.service.state(f.location).library
        val operation = UUID.randomUUID().toString(); val id = UUID.randomUUID().toString()
        val next = JsonObject(old.withMacros(listOf(macro("recovered"))) + (LIBRARY_META to JsonObject(old[LIBRARY_META]!!.jsonObject + ("revisionId" to JsonPrimitive(id)))))
        val record = MacroRevision(id, old[LIBRARY_META]!!.jsonObject.string("revisionId"), old[LIBRARY_META]!!.jsonObject.string("libraryId"), "2026-01-01T00:00:00Z", "test", operation, "recovery", next)
        val journal = buildJsonObject {
            put("fingerprint", MacroHistoryStore.hash(f.file.readBytes()))
            put("records", JsonArray(listOf(macroJson.encodeToJsonElement(MacroRevision.serializer(), record))))
            put("next", next)
        }
        File(MacroHistoryStore.history(f.file), ".pending.json").writeText(journal.toString())
        val result = VersionedMacroService().apply(ApplyRequest(f.directory.path, f.file.name, "expired", operation, listOf("one")))
        assertEquals(id, result.revisionId)
        assertEquals("recovered", f.service.state(f.location).library.macros().single().string("value"))
        assertFalse(File(MacroHistoryStore.history(f.file), ".pending.json").exists())
    }
}
