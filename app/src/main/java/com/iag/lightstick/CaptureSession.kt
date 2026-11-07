package com.iag.lightstick

import java.util.concurrent.atomic.AtomicBoolean

/** A Service instance admits one token/worker; stopping never admits a replacement worker. */
internal class CaptureSession {
    private val admitted = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    val stopping: Boolean get() = stopped.get()
    fun start(): Boolean = !stopping && admitted.compareAndSet(false, true)
    fun stop() { stopped.set(true) }
}

internal enum class CaptureKind { MUSIC, MANUAL_FOLLOW, REFERENCE, FOLLOW }

/** Mode is authoritative; stale optional fields never turn a music request into a recording. */
internal object CaptureRouting {
    fun resolve(mode: String, referenceId: String, manual: Boolean): CaptureKind = when (mode) {
        CaptureService.MODE_RECORD -> {
            require(referenceId.isNotBlank()) { "参考录制缺少工程标识" }
            CaptureKind.REFERENCE
        }
        CaptureService.MODE_SYNC -> CaptureKind.FOLLOW
        CaptureService.MODE_MUSIC -> if (manual) CaptureKind.MANUAL_FOLLOW else CaptureKind.MUSIC
        else -> error("不支持的捕获模式")
    }
}
