package com.iag.lightstick

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class ResolveDocumentsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun raw() = javaClass.getResourceAsStream("/resolve/three-cues.json")!!.bufferedReader().use { it.readText() }

    @Test fun originalCueJsonAndBomSurviveImportReloadExport() {
        val store = ArrangementStorage(folder.root)
        val original = "\uFEFF" + raw()
        val first = store.importJson(original)
        assertNotNull(first.resolve)
        assertNull(first.rrnn)
        assertEquals(original, store.rawJson(first.id))
        val loaded = store.list().single()
        assertEquals(108000L, loaded.resolve!!.startFrame)
        assertEquals(30L, loaded.resolve.numerator)
        assertEquals(ResolveLightState("breathing", "#AA55FF", 5), loaded.resolve.sample(5000).state)
    }

    @Test fun newImportsAreCopiesAndInvalidImportsLeaveExistingFilesAlone() {
        val store = ArrangementStorage(folder.root)
        val original = raw()
        val a = store.importJson(original)
        val b = store.importJson(original)
        assertNotEquals(a.id, b.id)
        assertThrows(IllegalStateException::class.java) { store.importJson(JSONObject(original).put("fps", 0).toString()) }
        assertEquals(2, store.list().size)
        assertEquals(original, store.rawJson(a.id))
    }

    @Test fun restoredShareRequestDoesNotDuplicateOrOverwriteItsFirstImport() {
        val store = ArrangementStorage(folder.root)
        val id = UUID.randomUUID().toString()
        val first = store.importJson(raw(), id)
        val retry = store.importJson(JSONObject(raw()).put("timeline_name", "Changed outside").toString(), id)
        assertEquals(first.id, retry.id)
        assertEquals(first.arrangement.name, retry.arrangement.name)
        assertEquals(1, store.list().size)
        assertEquals(raw(), store.rawJson(id))
        assertThrows(IllegalArgumentException::class.java) { store.importJson(raw(), "../../escape") }
    }

    @Test fun sharingWritesAnOriginalPrivateCopyWithThePortableCueExtension() {
        val store = ArrangementStorage(folder.newFolder("projects"))
        val original = "\uFEFF" + raw()
        val entry = store.importJson(original)
        val cache = folder.newFolder("shared")
        val shared = ArrangementShareFiles.prepare(cache, entry, store.rawJson(entry.id))
        assertTrue(shared.canonicalPath.startsWith(cache.canonicalPath + File.separator))
        assertTrue(shared.name.endsWith(".iagcue.json"))
        assertArrayEquals(original.toByteArray(Charsets.UTF_8), shared.readBytes())
        val second = ArrangementShareFiles.prepare(cache, entry, original)
        assertNotEquals(shared, second)
        assertEquals(original, shared.readText(Charsets.UTF_8))
        assertEquals(original, store.rawJson(entry.id))
    }

    @Test fun userTitlesCannotEscapeTheSharingDirectory() {
        val store = ArrangementStorage(folder.newFolder("projects"))
        val hostile = JSONObject(raw()).put("timeline_name", "../../bad/\nshow\\:*?<>|")
        val entry = store.importJson(hostile.toString())
        val name = ArrangementShareFiles.fileName(entry)
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('\n'))
        val cache = folder.newFolder("shared")
        val shared = ArrangementShareFiles.prepare(cache, entry, hostile.toString())
        assertTrue(shared.canonicalPath.startsWith(cache.canonicalPath + File.separator))
    }

    @Test fun singleCueLibraryEntryUsesTheSameDecoderAndKeepsItsRawDocument() {
        val prepared = LibrarySync.prepare(raw())
        assertEquals(1, prepared.entries.size)
        assertEquals("IAG native timeline example", prepared.entries.single().project.name)
        assertEquals(JSONObject(raw()).toString(), prepared.entries.single().raw)
    }

    @Test fun longEmojiTitlesRemainValidUtf8FileNames() {
        val store = ArrangementStorage(folder.newFolder("projects"))
        val entry = store.importJson(JSONObject(raw()).put("timeline_name", "灯" + "🌈".repeat(40)).toString())
        val name = ArrangementShareFiles.fileName(entry)
        assertEquals(name, name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertTrue(name.toByteArray(Charsets.UTF_8).size < 255)
    }
}
