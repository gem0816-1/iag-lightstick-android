package com.iag.lightstick

import org.junit.Assert.*
import org.junit.Test

class FollowEngineTest {
    private class Clock {
        var time = 1000L
        var tick: Runnable? = null
        val updates = mutableListOf<Long>()
        val states = mutableListOf<FollowState>()
        val engine = FollowEngine({ task, _ -> tick = task }, { task -> if (tick === task) tick = null },
            { time }, { 100_000L }, { p, _ -> updates += p }, { states += it })
        fun advance(ms: Long) { time += ms; tick?.run() }
    }

    @Test fun continuousAudioDoesNotEraseMismatchTimeoutAndLostFreezes() {
        val c = Clock(); c.engine.start(); c.engine.onSample(10000)
        repeat(8) { c.advance(500); c.engine.onLevel(80); c.engine.onSample(-1) }
        assertEquals(FollowState.LOST, c.states.last())
        val position = c.updates.last(); c.advance(2000)
        assertEquals(position, c.updates.last())
    }

    @Test fun silenceFreezeResumesFromTheHeldPosition() {
        val c = Clock(); c.engine.start(); c.engine.onSample(10000)
        c.advance(500); c.engine.onLevel(0)
        c.advance(900); c.engine.onLevel(0)
        val held = c.updates.last()
        c.advance(2000); assertEquals(held, c.updates.last())
        c.engine.onLevel(30); c.advance(50)
        assertEquals(held + 50, c.updates.last())
    }

    @Test fun changingOffsetDoesNotDiscardElapsedTime() {
        val c = Clock(); c.engine.start(); c.engine.onSample(10000)
        c.time += 400
        assertEquals(500, c.engine.adjustOffset(500))
        assertEquals(10900L, c.updates.last())
    }

    @Test fun relockCannotRewindToAnOutOfRangeReference() {
        val c = Clock(); c.engine.start(); c.engine.onSample(-1)
        assertTrue(c.updates.isEmpty())
        c.engine.onSample(150000)
        assertEquals(100000L, c.updates.last())
    }

    @Test fun stoppingDuringUpdateDoesNotLeaveAnotherTickQueued() {
        var posted = 0; var time = 1000L
        var task: Runnable? = null
        lateinit var engine: FollowEngine
        engine = FollowEngine({ r, _ -> posted++; task = r }, { task = null }, { time }, { 100L },
            { _, _ -> if (time > 1000) engine.stop() }, {})
        engine.start(); engine.onSample(0); val before = posted; time += 200; task?.run()
        assertEquals(before, posted)
        assertNull(task)
    }

    @Test fun stoppingInsideFreezeCallbackDoesNotPublishAStateAfterTeardown() {
        var time = 1000L
        val states = mutableListOf<FollowState>()
        lateinit var engine: FollowEngine
        engine = FollowEngine({ _, _ -> }, {}, { time }, { 100L },
            { p, _ -> if (p >= 100) engine.stop() }, { states += it })
        engine.start(); engine.onSample(0)
        time = 2000; engine.onLevel(0)
        time = 3000; engine.onLevel(0)
        assertEquals(FollowState.LOCKED, states.last())
    }
}
