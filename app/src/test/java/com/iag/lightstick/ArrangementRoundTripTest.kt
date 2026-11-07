package com.iag.lightstick

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.util.Base64

class ArrangementRoundTripTest {
    @get:Rule val folder = TemporaryFolder()

    private fun project(): JSONObject = JSONObject()
        .put("version", 1).put("name", "test").put("video_url", "https://www.bilibili.com/video/BV1NqHe6aEd9")
        .put("duration_ms", 1000).put("bpm", 120).put("snap_ms", 100).put("snap_to_beat", false)
        .put("tracks", JSONArray().put(JSONObject().put("id", "one").put("name", "主轨").put("priority", 0)))
        .put("events", JSONArray()).put("future_metadata", JSONObject().put("keep", "原样保留"))

    private fun rrnn(): JSONObject = JSONObject().put("schemaVersion", 2).put("title", "RRNN test")
        .put("target", JSONObject().put("bvid", "BV1NqHe6aEd9").put("duration", 1).put("cid", "123"))
        .put("fxs", JSONArray().put(JSONObject().put("type", "color").put("start", 0).put("dur", 1).put("color", "#FF0000")))
        .put("future_metadata", JSONObject().put("keep", "原样保留"))

    private fun reference(): JSONObject {
        val bytes = "IAGREF1".toByteArray() + ByteBuffer.allocate(8).putInt(1).putInt(123).array()
        return JSONObject().put("format", "IAGREF1").put("sampleRate", 44100).put("window", 1024).put("hop", 512)
            .put("data", Base64.getEncoder().encodeToString(bytes))
    }

    @Test fun importAndExportKeepReferenceAndUnknownFieldsExactly() {
        val store = ArrangementStorage(folder.root)
        val raw = project().put("reference", reference()).toString(2)
        val imported = store.importJson(raw)
        assertEquals(raw, store.rawJson(imported.id))
        assertEquals(1, store.list().size)
    }

    @Test fun firstLaunchDoesNotRequireCreatingTheDirectoryInTheConstructor() {
        val missing = java.io.File(folder.root, "not-created-yet")
        val store = ArrangementStorage(missing)
        assertFalse(missing.exists())
        assertTrue(store.list().isEmpty())
        val saved = store.importJson(project().toString())
        assertEquals(saved.id, store.list().single().id)
    }

    @Test fun corruptFileIsReportedAndKeptWhileValidProjectsStillLoad() {
        val store = ArrangementStorage(folder.root)
        val good = store.importJson(project().toString())
        val broken = java.io.File(folder.root, "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0000.json")
        val brokenBytes = "{not valid json".toByteArray(Charsets.UTF_8)
        broken.writeBytes(brokenBytes)

        val failures = mutableListOf<ArrangementStorage.ReadFailure>()
        val loaded = store.list { failures.add(it) }

        assertEquals(listOf(good.id), loaded.map { it.id })
        assertEquals(listOf(broken.name), failures.map { it.fileName })
        assertTrue(failures.single().reason.isNotBlank())
        // 源文件字节不变：读取失败绝不删除或改写用户数据。
        assertArrayEquals(brokenBytes, broken.readBytes())
    }

    @Test fun domainInvalidJsonIsAlsoReportedAsReadFailure() {
        val store = ArrangementStorage(folder.root)
        // 合法 JSON 但超出工程约束（版本 2），与截断/乱码一样属于无法读取。
        java.io.File(folder.root, "aaaaaaaa-bbbb-cccc-dddd-eeeeffff0001.json")
            .writeText(project().put("version", 2).toString())

        val failures = mutableListOf<ArrangementStorage.ReadFailure>()
        val loaded = store.list { failures.add(it) }

        assertTrue(loaded.isEmpty())
        assertEquals(1, failures.size)
    }

    @Test fun rrnnImportAndExportKeepNativePayload() {
        val store = ArrangementStorage(folder.root)
        val raw = rrnn().put("reference", reference()).toString(2)
        val imported = store.importJson(raw)
        assertNotNull(imported.rrnn)
        assertNotNull(store.list().single().rrnn)
        assertEquals(raw, store.rawJson(imported.id))
    }

    @Test fun largerLegacyPrivateFilesRemainReadableAndExportable() {
        val store = ArrangementStorage(folder.root)
        val raw = project().put("legacy_extra", "x".repeat(InputLimits.PROJECT_BYTES)).toString()
        val imported = store.importJson(raw)
        assertEquals(imported.id, store.list().single().id)
        assertEquals(raw, store.rawJson(imported.id))
    }

    @Test fun upsertKeepsIdentityAndInvalidReplacementLeavesPreviousData() {
        val store = ArrangementStorage(folder.root)
        val raw = project().toString()
        val first = store.upsertRaw(raw)
        assertEquals(first.id, store.upsertRaw(project().put("extension", 42).toString()).id)
        val lastGood = store.rawJson(first.id)
        try { store.upsertRaw("{broken}"); fail("Must reject") } catch (_: IllegalStateException) { }
        assertEquals(lastGood, store.rawJson(first.id))
        assertEquals(1, store.list().size)
    }

    @Test fun completeMixedLibraryIsValidatedWithoutLosingNativeFields() {
        val body = JSONObject().put("libraryVersion", 4).put("arrangements", JSONArray()
            .put(project().put("reference", reference())).put(rrnn())).toString()
        val result = LibrarySync.prepare(body)
        assertEquals(4, result.version)
        assertEquals(2, result.entries.size)
        assertNotNull(result.entries[0].reference)
        assertTrue(JSONObject(result.entries[1].raw).has("fxs"))
    }

    @Test fun oneInvalidItemRejectsTheCompleteLibrary() {
        val body = JSONObject().put("libraryVersion", 4).put("arrangements", JSONArray().put(project()).put(JSONObject())).toString()
        assertThrows(Exception::class.java) { LibrarySync.prepare(body) }
    }

    @Test fun truncatedReferenceAndWrongParametersAreRejected() {
        val corrupted = project().put("reference", reference().put("data", Base64.getEncoder().encodeToString("IAGREF1".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) { LibrarySync.prepare(corrupted.toString()) }
        val wrong = project().put("reference", reference().put("hop", 256))
        assertThrows(IllegalArgumentException::class.java) { LibrarySync.prepare(wrong.toString()) }
    }

    @Test fun htmlAndMalformedEnvelopeDoNotBecomeSuccess() {
        assertThrows(IllegalArgumentException::class.java) { LibrarySync.prepare("<html>fallback</html>") }
        assertThrows(Exception::class.java) { LibrarySync.prepare("{\"libraryVersion\":4}") }
    }
}
