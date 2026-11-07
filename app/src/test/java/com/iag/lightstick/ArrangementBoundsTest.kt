package com.iag.lightstick

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArrangementBoundsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun document(start: Long, duration: Long) = JSONObject()
        .put("version", 1).put("name", "bounds").put("video_url", "")
        .put("duration_ms", 1000L).put("bpm", 120).put("snap_ms", 100)
        .put("tracks", JSONArray().put(JSONObject().put("id", "one").put("name", "track").put("priority", 0)))
        .put("events", JSONArray().put(JSONObject().put("id", "event").put("track_id", "one")
            .put("start_ms", start).put("duration_ms", duration).put("color", "#0066FF")
            .put("effect", "solid").put("period_ms", 1000L))).toString()

    @Test fun wrappedLongEndCannotBypassTheTimelineBoundary() {
        for ((start, duration) in listOf(Long.MAX_VALUE to 1L, (Long.MAX_VALUE - 10L) to 100L)) {
            try {
                LightingArrangement.fromJson(document(start, duration))
                fail("An overflowing event must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun invalidImportLeavesTheExistingDocumentAndListIntact() {
        val store = ArrangementStorage(folder.root)
        val raw = document(0L, 1000L)
        val saved = store.importJson(raw)
        try {
            store.importJson(document(Long.MAX_VALUE, 100L))
            fail("Invalid import must not create another project")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf(saved.id), store.list().map { it.id })
        assertEquals(raw, store.rawJson(saved.id))
    }

    @Test fun exactEndAndLastMillisecondRemainCompatible() {
        assertEquals(1000L, LightingArrangement.fromJson(document(0L, 1000L)).events.single().endMs)
        assertEquals(1000L, LightingArrangement.fromJson(document(999L, 1L)).events.single().endMs)
        for ((start, duration) in listOf(1000L to 1L, 999L to 2L, -1L to 1L, 0L to 0L)) {
            try { LightingArrangement.fromJson(document(start, duration)); fail("Outside the timeline") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
