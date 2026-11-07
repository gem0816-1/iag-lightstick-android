package com.iag.lightstick

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer

class FileSafetyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun failureDuringEncodingPreservesTheOldDocument() {
        val file = folder.newFile("project.json").apply { writeText("old-good-data") }
        try {
            AtomicFiles.write(file) { it.write("half".toByteArray()); throw IOException("injected") }
            fail("Write should fail")
        } catch (_: IOException) { }
        assertEquals("old-good-data", file.readText())
        assertEquals(listOf("project.json"), folder.root.listFiles()!!.map { it.name })
    }

    @Test fun successfulWriteReplacesAnExistingFile() {
        val file = folder.newFile("project.json").apply { writeText("old") }
        AtomicFiles.text(file, "new complete data")
        assertEquals("new complete data", file.readText())
    }

    @Test fun oversizedInputIsRejectedBeforeUnboundedAllocation() {
        try { InputLimits.read(ByteArrayInputStream(ByteArray(20)), 10); fail("Must reject") }
        catch (_: IllegalArgumentException) { }
        assertArrayEquals(ByteArray(10), InputLimits.read(ByteArrayInputStream(ByteArray(10)), 10))
    }

    @Test fun malformedReferencesAreRejectedWithoutConstructingAnIndex() {
        for (frames in listOf(-1, 0, Int.MAX_VALUE, 2)) {
            val bytes = "IAGREF1".toByteArray() + ByteBuffer.allocate(4).putInt(frames).array() + ByteArray(4)
            assertFalse(ReferenceDb.validBytes(bytes))
            val file = folder.newFile().apply { writeBytes(bytes) }
            assertNull(ReferenceDb.load(file))
        }
    }

    @Test fun silentFramesCannotVoteForAnUnrelatedPosition() {
        val db = ReferenceDb(); repeat(100) { db.add(0, it) }
        assertNull(db.match(List(20) { 0 }, 4))
    }

    @Test fun referenceRoundTripPreservesAllHashesAndPosition() {
        val db = ReferenceDb(); repeat(100) { db.add(it + 1, it) }
        val file = folder.newFile(); db.save(file)
        assertTrue(ReferenceDb.validBytes(file.readBytes()))
        assertEquals(db.match((21..40).toList(), 4), ReferenceDb.load(file)!!.match((21..40).toList(), 4))
    }
}
