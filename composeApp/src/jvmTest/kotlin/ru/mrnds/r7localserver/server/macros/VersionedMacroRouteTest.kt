package ru.mrnds.r7localserver.server.macros

import io.ktor.http.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.application.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.mrnds.r7localserver.files.FileService
import ru.mrnds.r7localserver.server.routing.*
import java.nio.file.Files
import kotlin.test.*

class VersionedMacroRouteTest {
    @Test fun `v2 HTTP contract and removed legacy route`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                versionedMacroRoute(VersionedMacroService())
                fileRoute(FileService())
            }
        }
        val directory = Files.createTempDirectory("r7-route-test-").toFile()
        try {
            val location = LibraryRequest(directory.path, "library.json")
            val root = buildJsonObject {
                put("macrosArray", buildJsonArray { add(buildJsonObject {
                    put("guid", "one"); put("name", "One"); put("value", "code"); put("isUniversal", true)
                }) })
            }
            val response = client.post("/macros/v2/preview") {
                contentType(ContentType.Application.Json)
                setBody(macroJson.encodeToString(PreviewRequest(directory.path, "library.json", "push", root, listOf("one"))))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val wireChange = macroJson.parseToJsonElement(response.bodyAsText()).jsonObject["changes"]!!.jsonArray.single().jsonObject
            assertEquals(JsonArray(emptyList()), wireChange["conflicts"])
            assertEquals(JsonArray(emptyList()), wireChange["chunks"])
            assertTrue(wireChange.containsKey("diff"))
            val preview = macroJson.decodeFromString<PreviewResponse>(response.bodyAsText())
            val applied = client.post("/macros/v2/apply") {
                contentType(ContentType.Application.Json)
                setBody(macroJson.encodeToString(ApplyRequest(directory.path, "library.json", preview.token, preview.operationId, listOf("one"))))
            }
            assertEquals(HttpStatusCode.OK, applied.status)
            val saved = macroJson.decodeFromString<ApplyResponse>(applied.bodyAsText())
            val reorder = client.post("/macros/v2/order") {
                contentType(ContentType.Application.Json)
                setBody(macroJson.encodeToString(OrderRequest(directory.path, "library.json", saved.revisionId!!, listOf("one"), listOf("one"))))
            }
            assertEquals(HttpStatusCode.OK, reorder.status)
            assertFalse(macroJson.decodeFromString<LibraryState>(reorder.bodyAsText()).externalChanges)
            val history = client.post("/macros/v2/history") { contentType(ContentType.Application.Json); setBody(macroJson.encodeToString(location)) }
            assertEquals(1, macroJson.decodeFromString<List<MacroRevision>>(history.bodyAsText()).size)
            assertEquals(HttpStatusCode.NotFound, client.post("/macros/sync").status)
            val bypass = client.post("/files/write") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("directoryPath", directory.path); put("fileName", "library.json"); put("overwrite", true); put("content", root) }.toString())
            }
            assertEquals(HttpStatusCode.Conflict, bypass.status)
            val expired = client.post("/macros/v2/apply") {
                contentType(ContentType.Application.Json)
                setBody(macroJson.encodeToString(ApplyRequest(directory.path, "library.json", "expired", java.util.UUID.randomUUID().toString(), listOf("one"))))
            }
            assertEquals(HttpStatusCode.Conflict, expired.status)
        } finally { directory.deleteRecursively() }
    }
}
