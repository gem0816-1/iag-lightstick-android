package com.iag.lightstick

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CaptureSessionTest {
    @Test fun concurrentStartsAdmitOneWorker() {
        val session = CaptureSession()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(50) { java.util.concurrent.Callable { session.start() } })
            assertEquals(1, results.count { it.get(2, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
    @Test fun oldStoppedSessionCannotResetANewSession() {
        val old = CaptureSession(); assertTrue(old.start()); old.stop()
        assertFalse(old.start())
        val fresh = CaptureSession(); assertTrue(fresh.start())
        assertTrue(old.stopping); assertFalse(fresh.stopping)
    }

    @Test fun staleReferenceCannotChangeANewMusicOrFollowRequest() {
        assertEquals(CaptureKind.MUSIC, CaptureRouting.resolve(CaptureService.MODE_MUSIC, "old-cancelled-recording", false))
        assertEquals(CaptureKind.FOLLOW, CaptureRouting.resolve(CaptureService.MODE_SYNC, "old-cancelled-recording", true))
    }

    @Test fun manualFollowAndReferenceRemainExplicitDifferentModes() {
        assertEquals(CaptureKind.MANUAL_FOLLOW, CaptureRouting.resolve(CaptureService.MODE_MUSIC, "", true))
        assertEquals(CaptureKind.REFERENCE, CaptureRouting.resolve(CaptureService.MODE_RECORD, "BV1NqHe6aEd9", false))
    }

    @Test fun invalidRecordingDoesNotSilentlyFallBackToMusic() {
        assertThrows(IllegalArgumentException::class.java) { CaptureRouting.resolve(CaptureService.MODE_RECORD, "", false) }
    }
}
