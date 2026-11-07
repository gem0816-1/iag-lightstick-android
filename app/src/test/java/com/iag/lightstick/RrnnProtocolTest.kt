package com.iag.lightstick

import org.junit.Assert.*
import org.junit.Test

class RrnnProtocolTest {
    @Test fun renamedPayloadStillCompilesTheKnownRedFrame() {
        val cue = RrnnCue(type = "color", start = 0.0, dur = 1.0, color = "#FF0000")
        val frame = StageCompile.compileAt(cue, 0.5)
        assertEquals(1, frame.cmd)
        assertArrayEquals(intArrayOf(255, 0, 0), frame.rgb)
        assertArrayEquals(byteArrayOf(0x5A, 0xA5.toByte(), 4, 0, 1, 0xFF.toByte(), 0, 0, 3),
            StageCompile.frame(frame.cmd, frame.rgb.toList()))
    }

    @Test fun originalOffAndBlackBytesAreUnchanged() {
        assertArrayEquals(byteArrayOf(0x5A, 0xA5.toByte(), 2, 0, 0, 0, 1), StageCompile.OFF)
        assertArrayEquals(byteArrayOf(0x5A, 0xA5.toByte(), 4, 0, 1, 0, 0, 0, 4), StageCompile.BLACK)
    }
}
