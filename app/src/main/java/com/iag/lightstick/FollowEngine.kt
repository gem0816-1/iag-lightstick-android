package com.iag.lightstick

import android.os.Handler
import android.os.SystemClock

enum class FollowState { WAITING, LOCKED, HOLD, LOST }

/**
 * 跟随引擎（JIHUA.md §1）：权威时间样本（指纹匹配）+ 本地外推（anchor 时钟）+ 对齐。
 *
 * 状态机：
 *  - WAITING：等待首个咬合样本（从 0:00 播放时首个样本即预咬合）；
 *  - LOCKED：咬合，250ms 外推推进，样本小误差缓慢收敛（SOFT_GAIN）；
 *  - HOLD：静音（暂停/缓冲）→ 冻结时钟不外推，恢复出声继续；
 *  - LOST：有声但持续失配 >3s（倍速/换视频）→ 冻结保持静态光，等重新咬合。
 *
 * 对齐规则：|样本 − 外推| > 600ms（拖动）→ 硬对齐（回调 hard=true，重置棒子频闪相位）；
 * 小误差 → anchor 微调收敛。全部运行在主线程（广播接收器 + Handler）。
 */
class FollowEngine internal constructor(
    private val post: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val now: () -> Long,
    private val durationMs: () -> Long,
    private val onUpdate: (positionMs: Long, hard: Boolean) -> Unit,
    private val onState: (FollowState) -> Unit,
) {
    constructor(handler: Handler, durationMs: () -> Long,
        onUpdate: (Long, Boolean) -> Unit, onState: (FollowState) -> Unit) : this(
        { runnable, delay -> handler.postDelayed(runnable, delay); Unit },
        { handler.removeCallbacks(it) }, { SystemClock.elapsedRealtime() }, durationMs, onUpdate, onState)

    companion object {
        private const val TICK_MS = 50L
        private const val HARD_ALIGN_MS = 600f
        private const val SOFT_GAIN = 0.15f
        private const val SILENCE_HOLD_MS = 800L
        private const val MISMATCH_LOST_MS = 3000L
    }

    var userOffsetMs = 0L
        private set

    private var anchorPos = 0L
    private var anchorElapsed = 0L
    private var armedAnchor = 0L
    private var locked = false
    private var holding = false
    private var lost = false
    private var manual = false
    private var armed = false
    private var running = false
    private var audio = true
    private var silentSince = 0L
    private var mismatchSince = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (locked && !holding && !lost) {
                val pos = anchorPos + (now() - anchorElapsed)
                val dur = durationMs()
                if (dur > 0 && pos >= dur) {
                    holding = true
                    onUpdate(dur, true)
                } else {
                    onUpdate(pos, false)
                }
            }
            if (running) post(this, TICK_MS)
        }
    }

    fun start() {
        cancel(tick)
        locked = false
        holding = false
        lost = false
        manual = false
        armed = false
        audio = true
        silentSince = 0L
        mismatchSince = 0L
        anchorPos = 0L
        anchorElapsed = now()
        running = true
        setState(FollowState.WAITING)
        post(tick, 0L)
    }

    /** 手动对齐（JIHUA 降级第二级）：武装监听，视频出声的瞬间从锚点开始外推；静音照常保持；忽略指纹样本。 */
    fun startArmed(anchorMs: Long) {
        cancel(tick)
        locked = false
        holding = false
        lost = false
        manual = true
        armed = true
        armedAnchor = anchorMs
        audio = false
        silentSince = 0L
        mismatchSince = 0L
        anchorElapsed = now()
        running = true
        setState(FollowState.WAITING)
        post(tick, 0L)
    }

    fun stop() {
        running = false
        cancel(tick)
    }

    /** 指纹位置样本（positionMs < 0 = 本轮失配）；手动模式无样本，忽略。 */
    fun onSample(positionMs: Long) {
        if (!running || manual) return
        if (positionMs < 0) {
            if (audio) {
                val now = now()
                if (mismatchSince == 0L) mismatchSince = now
                if (locked && !lost && now - mismatchSince > MISMATCH_LOST_MS) {
                    freezeClock(now)
                    if (!running) return
                    lost = true
                    setState(FollowState.LOST)
                }
            }
            return
        }
        mismatchSince = 0L
        val sample = positionMs + userOffsetMs
        if (!locked || lost) {
            hardLock(sample)
            return
        }
        val extrapolated = if (holding) anchorPos else anchorPos + (now() - anchorElapsed)
        val err = sample - extrapolated
        if (kotlin.math.abs(err) > HARD_ALIGN_MS) {
            hardLock(sample)
        } else {
            anchorPos = extrapolated + (err * SOFT_GAIN).toLong()
            anchorElapsed = now()
        }
    }

    /** 捕获电平（0 = 静音）：静音 → 保持（暂停）；恢复出声 → 继续外推。 */
    fun onLevel(level: Int) {
        if (!running) return
        val now = now()
        if (level <= 0) {
            if (audio) {
                audio = false
                silentSince = now
            }
            if (locked && !holding && now - silentSince > SILENCE_HOLD_MS) {
                freezeClock(now)
                if (running) setState(FollowState.HOLD)
            }
        } else {
            if (!audio) mismatchSince = 0L
            audio = true
            silentSince = 0L
            if (armed) {
                armed = false
                hardStart()
                return
            }
            if (holding && !lost) {
                holding = false
                anchorElapsed = now
                setState(FollowState.LOCKED)
            }
        }
    }

    /** 用户 ± 偏移微调（0.5s 步进），立即反映到时钟。返回新偏移。 */
    fun adjustOffset(deltaMs: Long): Long {
        userOffsetMs += deltaMs
        if (running && locked) {
            val time = now()
            anchorPos = clamp(positionAt(time) + deltaMs)
            anchorElapsed = time
            onUpdate(anchorPos, false)
        }
        return userOffsetMs
    }

    /** 通知栏"重新对齐"：丢弃当前咬合，等下一个样本硬对齐。 */
    fun forceRelock() {
        if (!running) return
        locked = false
        lost = false
        holding = false
        mismatchSince = 0L
        setState(FollowState.WAITING)
    }

    private fun hardLock(sample: Long) {
        locked = true
        lost = false
        holding = !audio
        anchorPos = clamp(sample)
        anchorElapsed = now()
        setState(if (holding) FollowState.HOLD else FollowState.LOCKED)
        onUpdate(anchorPos, true)
    }

    /** 武装结束：视频已出声，从锚点（含用户偏移）开始外推。 */
    private fun hardStart() {
        locked = true
        lost = false
        holding = false
        anchorPos = clamp(armedAnchor + userOffsetMs)
        anchorElapsed = now()
        setState(FollowState.LOCKED)
        onUpdate(anchorPos, true)
    }

    private fun clamp(position: Long): Long = position.coerceIn(0L, durationMs().coerceAtLeast(0L))

    private fun positionAt(time: Long): Long =
        if (locked && !holding && !lost) anchorPos + (time - anchorElapsed) else anchorPos

    private fun freezeClock(time: Long) {
        anchorPos = clamp(positionAt(time))
        anchorElapsed = time
        holding = true
        onUpdate(anchorPos, false)
    }

    private fun setState(state: FollowState) {
        onState(state)
    }
}
