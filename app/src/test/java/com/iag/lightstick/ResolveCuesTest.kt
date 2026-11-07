package com.iag.lightstick

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResolveCuesTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/resolve/$name")!!.bufferedReader().use { it.readText() }
    private fun root() = JSONObject(fixture("three-cues.json"))
    private fun sheet(o: JSONObject = root()) = ResolveCueSheet.fromJson(o.toString())

    @Test fun actualResolveExampleKeepsTimelineOriginAndNativeParameterBytes() {
        val parsed = sheet()
        assertEquals(108000L, parsed.startFrame)
        assertEquals("01:00:00:00", parsed.timecodeStart)
        assertEquals(15000L, parsed.durationMs)
        assertEquals(ResolveLightState("solid", "#0066FF"), parsed.sample(0).state)
        assertEquals(ResolveLightState("breathing", "#AA55FF", 5), parsed.sample(5000).state)
        assertEquals(ResolveLightState("flash", "#FF0055", 6), parsed.sample(10000).state)
        assertEquals(ResolveLightState.BLACK, parsed.sample(15000).state)
    }

    @Test fun matchesAllSamplesGeneratedByTheExistingPythonBridge() {
        val cases = JSONArray(fixture("bridge-contract.json"))
        for (caseIndex in 0 until cases.length()) {
            val case = cases.getJSONObject(caseIndex)
            val parsed = sheet(case.getJSONObject("sheet"))
            val expected = case.getJSONArray("expected")
            for (i in 0 until expected.length()) {
                val e = expected.getJSONObject(i)
                val actual = parsed.sample(e.getLong("position_ms")).state
                val speed = if (e.isNull("speed")) null else e.getInt("speed")
                assertEquals("case=$caseIndex sample=$i", ResolveLightState(e.getString("mode"), e.getString("color"), speed), actual)
            }
        }
    }

    @Test fun subsecondTimelineKeepsShortCuesAndSchedulesTheirBoundary() {
        val o = root().put("fps", JSONObject().put("numerator", 60).put("denominator", 1))
            .put("duration_frames", 2).put("cues", JSONArray()
                .put(JSONObject().put("start_frame", 0).put("end_frame", 1).put("mode", "solid").put("color", "#0000FF"))
                .put(JSONObject().put("start_frame", 1).put("end_frame", 2).put("mode", "solid").put("color", "#FF0000")))
        val parsed = sheet(o)
        assertEquals(34L, parsed.durationMs)
        assertEquals(17L, parsed.nextDelayMs(0))
        assertEquals(1L, parsed.nextDelayMs(16))
        assertEquals("#0000FF", parsed.sample(16).state.color)
        assertEquals("#FF0000", parsed.sample(17).state.color)
        assertEquals(17L, parsed.nextDelayMs(17))
        assertEquals(34L, parsed.toDisplayArrangement().durationMs)
    }

    @Test fun overlapFailsAndGapsRemainBlack() {
        val o = root()
        o.getJSONArray("cues").getJSONObject(1).put("start_frame", 149)
        assertThrows(IllegalArgumentException::class.java) { sheet(o) }
        o.getJSONArray("cues").getJSONObject(1).put("start_frame", 180)
        val parsed = sheet(o)
        assertEquals(ResolveLightState.BLACK, parsed.sample(5500).state)
        assertEquals("breathing", parsed.sample(6000).state.mode)
    }

    @Test fun unorderedCuesAreSortedWithoutChangingTheirFrames() {
        val o = root()
        val c = o.getJSONArray("cues")
        o.put("cues", JSONArray().put(c.get(2)).put(c.get(0)).put(c.get(1)))
        assertEquals(listOf(0L, 150L, 300L), sheet(o).cues.map { it.startFrame })
    }

    @Test fun rawNativeSpeedIsNotConvertedToHertzOrBreathingGearRange() {
        val o = root()
        o.getJSONArray("cues").getJSONObject(1).put("speed", 255)
        assertEquals(255, sheet(o).sample(6000).state.speed)
    }

    @Test fun invalidNumericTypesAndRangesAreRejected() {
        listOf<Any>(0, -1, 1.5, true, "30").forEach { bad ->
            val o = root()
            o.getJSONObject("fps").put("numerator", bad)
            assertThrows("fps=$bad", Exception::class.java) { sheet(o) }
        }
        listOf<Any>(1.5, true, "0", -1).forEach { bad ->
            val o = root()
            o.getJSONArray("cues").getJSONObject(0).put("start_frame", bad)
            assertThrows("frame=$bad", Exception::class.java) { sheet(o) }
        }
        val zeroDenominator = root()
        zeroDenominator.getJSONObject("fps").put("denominator", 0)
        assertThrows(IllegalArgumentException::class.java) { sheet(zeroDenominator) }
    }

    @Test fun malformedColorsUnsupportedModesAndInappropriateSpeedFail() {
        listOf("color" to "#xyzxyz", "mode" to "raw_write", "speed" to 256, "brightness" to 101).forEach { (key, value) ->
            val o = root()
            o.getJSONArray("cues").getJSONObject(0).put(key, value)
            assertThrows(Exception::class.java) { sheet(o) }
        }
        val noEndColor = root()
        noEndColor.getJSONArray("cues").getJSONObject(0).put("mode", "gradient")
        assertThrows(IllegalArgumentException::class.java) { sheet(noEndColor) }
    }

    @Test fun futureOrTransportFieldsCannotSilentlyBecomeCommands() {
        assertThrows(IllegalArgumentException::class.java) { sheet(root().put("version", 2)) }
        assertThrows(IllegalArgumentException::class.java) { sheet(root().put("raw_frame", "5A A5")) }
    }

    @Test fun durationsAreBoundedBeforeFrameMultiplicationCanOverflow() {
        assertThrows(IllegalArgumentException::class.java) { sheet(root().put("duration_frames", Long.MAX_VALUE)) }
        assertThrows(IllegalArgumentException::class.java) { sheet(root().put("duration_frames", 149)) }
        val empty = root().put("cues", JSONArray()).put("duration_frames", 30)
        assertEquals(ResolveLightState.BLACK, sheet(empty).sample(0).state)
        assertThrows(IllegalArgumentException::class.java) { sheet(empty.put("duration_frames", 0)) }
    }

    @Test fun nativeWritesDeduplicateAndOnlyGradientsNeedPeriodicSampling() {
        val gate = ResolveOutputGate()
        val native = ResolveSample(ResolveLightState("breathing", "#AA55FF", 5), 0, false)
        assertEquals(native.state, gate.next(native, 1000))
        assertNull(gate.next(native, 1001))
        assertNull(gate.next(native.copy(cueIndex = 1), 2000))
        assertEquals(native.state, gate.next(native, 2001, force = true))
        gate.reset()
        assertEquals(native.state, gate.next(native, 2002))
    }

    @Test fun gradientThrottleStillAllowsSeekAndCueBoundaryChanges() {
        val gate = ResolveOutputGate()
        val a = ResolveSample(ResolveLightState("solid", "#112233"), 0, true)
        val b = a.copy(state = ResolveLightState("solid", "#223344"))
        assertEquals(a.state, gate.next(a, 1000))
        assertNull(gate.next(b, 1033))
        assertNull(gate.next(b, 1099))
        assertEquals(b.state, gate.next(b, 1100))
        assertEquals(a.state, gate.next(a.copy(cueIndex = 1), 1101))
        assertEquals(b.state, gate.next(b, 1102, force = true))
    }
}
