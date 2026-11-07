package com.iag.lightstick

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import kotlin.math.sqrt

/**
 * 音频捕获服务（Android 14+ MediaProjection 规范流程）。
 * 模式：
 *  - probe:  10 秒 RMS 测试（验证 B 站是否允许捕获）
 *  - record: 持续捕获并生成参考指纹库（ReferenceDb），停止时保存到 filesDir
 *  - sync:   持续捕获 + 参考库实时匹配，周期性广播匹配位置
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_RESULT = "com.iag.lightstick.CAPTURE_RESULT"
        const val EXTRA_SESSION_ID = "capture_session"
        const val EXTRA_MSG = "msg"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_VOTES = "votes"
        const val MODE_PROBE = "probe"
        const val MODE_RECORD = "record"
        const val MODE_SYNC = "sync"
        const val MODE_MUSIC = "music"
        private const val TAG = "IAGCapture"

        /** 跟随采样参数（JIHUA.md §1）：2s 匹配窗口、0.5s 采样间隔、管道固有延迟常数补偿。 */
        private const val SYNC_WINDOW_MS = 2000L
        private const val SYNC_INTERVAL_MS = 500L
        private const val SAMPLE_LATENCY_MS = 250L

    }

    private var sessionId: String? = null
    private val session = CaptureSession()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "IAG-audio-capture") }
    private val stopRequested: Boolean get() = session.stopping
    @Volatile private var destroyed = false
    @Volatile private var projectionRevoked = false
    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var activeProjection: MediaProjection? = null
    @Volatile private var projectionCallback: MediaProjection.Callback? = null

    private fun requestStop() {
        session.stop()
        try { activeRecorder?.stop() } catch (_: IllegalStateException) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                "capture_channel", "音频捕获", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!session.start()) return START_NOT_STICKY
        sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        if (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            broadcast("捕获失败: 需要 Android 10 及录音权限")
            stopSelf()
            return START_NOT_STICKY
        }
        val n = Notification.Builder(this, "capture_channel")
            .setContentTitle("IAG 音频捕获运行中")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(1001, n)
        } catch (e: SecurityException) {
            broadcast("捕获失败: 系统拒绝前台捕获授权")
            stopSelf()
            return START_NOT_STICKY
        }

        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_PROBE
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID) ?: "default"
        if (rc != Activity.RESULT_OK || data == null || !videoId.matches(Regex("[0-9A-Za-z_-]{1,64}"))) {
            broadcast("捕获失败: 授权或参考标识无效")
            stopSelf()
            return START_NOT_STICKY
        }
        executor.execute { runCapture(rc, data, mode, videoId) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        requestStop()
        activeProjection?.let { projection ->
            projectionCallback?.let { callback -> runCatching { projection.unregisterCallback(callback) } }
            runCatching { projection.stop() }
        }
        executor.shutdown()
        super.onDestroy()
    }

    private fun broadcast(msg: String) {
        Log.i(TAG, msg)
        sendBroadcast(
            Intent(ACTION_RESULT).putExtra(EXTRA_SESSION_ID, sessionId).putExtra(EXTRA_MSG, msg).setPackage(packageName)
        )
    }

    @RequiresApi(29)
    private fun runCapture(resultCode: Int, data: Intent?, mode: String, videoId: String) {
        var recorder: AudioRecord? = null
        var projection: MediaProjection? = null
        try {
            if (stopRequested) return
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                broadcast("捕获失败: 录音权限已撤销")
                return
            }
            if (data == null) {
                broadcast("捕获失败: 授权数据为空")
                return
            }
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val activeProjection = mpm.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("系统音频捕获授权无效")
            projection = activeProjection
            this.activeProjection = activeProjection
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!session.stopping) {
                        projectionRevoked = true
                        broadcast("捕获失败: 系统已停止音频捕获")
                        requestStop()
                    }
                }
            }
            projectionCallback = callback
            activeProjection.registerCallback(callback, Handler(Looper.getMainLooper()))
            broadcast("授权成功，音频捕获开始（模式=$mode）...")

            val config = AudioPlaybackCaptureConfiguration.Builder(activeProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(44100 * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            activeRecorder = recorder
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "音频捕获初始化失败" }
            if (stopRequested) return
            recorder.startRecording()
            Log.i(TAG, "capture started mode=$mode")

            when (mode) {
                MODE_RECORD -> recordLoop(recorder, videoId)
                MODE_SYNC -> syncLoop(recorder, videoId)
                MODE_MUSIC -> musicLoop(recorder)
                else -> probeLoop(recorder)
            }
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            if (!stopRequested) broadcast("捕获失败: ${e.message}")
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            projectionCallback?.let { callback -> runCatching { projection?.unregisterCallback(callback) } }
            runCatching { projection?.stop() }
            activeRecorder = null
            activeProjection = null
            if (!destroyed) stopSelf()
        }
    }

    /** 10 秒 RMS 探测 */
    private fun probeLoop(recorder: AudioRecord) {
        val buf = ShortArray(4410)
        var peak = 0.0
        var total = 0.0
        var count = 0
        val t0 = android.os.SystemClock.elapsedRealtime()
        while (!stopRequested && android.os.SystemClock.elapsedRealtime() - t0 < 10_000) {
            val n = recorder.read(buf, 0, buf.size)
            if (stopRequested) break
            check(n >= 0) { "系统播放音频读取失败: $n" }
            if (n == 0) { Thread.sleep(10); continue }
            if (n > 0) {
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = sqrt(sum / n)
                if (rms > peak) peak = rms
                total += rms
                count++
            }
        }
        val avg = if (count > 0) total / count else 0.0
        Log.i(TAG, "probe done peak=$peak avg=$avg")
        broadcast("捕获完成: 峰值RMS=%.1f 平均RMS=%.1f".format(peak, avg))
        broadcast(
            if (peak > 100) "✅ 捕获到声音：B 站允许 AudioPlaybackCapture，指纹同步路线可行！"
            else "❌ 捕获为静音：B 站不允许音频捕获（或当时未播放）"
        )
    }

    /**
     * 系统播放音频音乐调光输入。这里的 AudioRecord 使用
     * AudioPlaybackCaptureConfiguration，不使用 AudioSource.MIC。
     * 每 100 ms 计算一帧，并复刻 xzq 的平均绝对振幅量化公式。
     */
    private fun musicLoop(recorder: AudioRecord) {
        val frame = ShortArray(4410) // 44.1 kHz × 100 ms，单声道
        var frames = 0L
        while (!stopRequested) {
            var filled = 0
            while (filled < frame.size && !stopRequested) {
                val read = recorder.read(frame, filled, frame.size - filled, AudioRecord.READ_BLOCKING)
                if (stopRequested) break
                if (read == 0) { Thread.sleep(10); continue }
                if (read < 0) throw IllegalStateException("系统播放音频读取失败: $read")
                if (read > 0) filled += read
            }
            if (stopRequested || filled == 0) continue

            var absoluteSum = 0L
            for (i in 0 until filled) absoluteSum += kotlin.math.abs(frame[i].toInt())
            var level = (500.0 * absoluteSum / (16383.0 * filled)).toInt()
            if (level >= 200) level = 200
            if (level < 10) level = 0

            sendBroadcast(
                Intent(ACTION_RESULT).putExtra(EXTRA_SESSION_ID, sessionId)
                    .putExtra(EXTRA_LEVEL, level)
                    .setPackage(packageName)
            )
            frames++
            if (frames % 50L == 0L) broadcast("系统播放音频调光运行中，当前音量 $level/200")
        }
        broadcast("系统播放音频调光已停止")
    }

    /** 流式帧切分：按 WINDOW/HOP 滑动窗口回调（高效环形缓冲） */
    private fun streamFrames(recorder: AudioRecord, onFrame: (FloatArray) -> Unit) {
        val cap = FingerprintEngine.WINDOW + FingerprintEngine.HOP * 4
        val pending = ShortArray(cap)
        var size = 0
        val frame = FloatArray(FingerprintEngine.WINDOW)
        val buf = ShortArray(FingerprintEngine.HOP)
        while (!stopRequested) {
            val n = recorder.read(buf, 0, buf.size)
            if (stopRequested) break
            check(n >= 0) { "系统播放音频读取失败: $n" }
            if (n == 0) { Thread.sleep(10); continue }
            if (size + n > cap) {
                val overflow = size + n - cap
                System.arraycopy(pending, overflow, pending, 0, size - overflow)
                size -= overflow
            }
            System.arraycopy(buf, 0, pending, size, n)
            size += n
            while (!stopRequested && size >= FingerprintEngine.WINDOW) {
                for (i in 0 until FingerprintEngine.WINDOW) frame[i] = pending[i] / 32768f
                onFrame(frame)
                System.arraycopy(
                    pending, FingerprintEngine.HOP,
                    pending, 0, size - FingerprintEngine.HOP
                )
                size -= FingerprintEngine.HOP
            }
        }
    }

    /** 参考指纹录制：持续到 stopRequested，保存 ReferenceDb */
    private fun recordLoop(recorder: AudioRecord, videoId: String) {
        val db = ReferenceDb()
        var frameIndex = 0
        var lastReport = 0L
        val t0 = android.os.SystemClock.elapsedRealtime()
        streamFrames(recorder) { frame ->
            db.add(FingerprintEngine.fingerprintFrame(frame), frameIndex)
            frameIndex++
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastReport > 3000) {
                lastReport = now
                val sec = (now - t0) / 1000
                broadcast("录制中: ${frameIndex} 帧 (~${db.frameToMs(frameIndex) / 1000}s)")
            }
        }
        if (frameIndex == 0 || projectionRevoked) {
            broadcast("参考录制已取消，原文件已保留")
            return
        }
        val file = File(filesDir, "ref_$videoId.bin")
        db.save(file)
        broadcast(
            "✅ 参考录制完成: ${frameIndex} 帧 (${db.frameToMs(frameIndex) / 1000}s) → ${file.absolutePath}"
        )
    }

    /** 实时跟随（JIHUA §1）：2s 滑窗投票匹配，每 0.5s 广播位置样本；同时广播电平供静音判定。 */
    private fun syncLoop(recorder: AudioRecord, videoId: String) {
        val db = ReferenceDb.load(File(filesDir, "ref_$videoId.bin"))
        if (db == null) {
            broadcast("❌ 未找到参考库 ref_$videoId.bin，无法跟随")
            return
        }
        broadcast("参考库已加载: ${db.totalFrames} 帧 (${db.frameToMs(db.totalFrames) / 1000}s)")

        val windowFrames = (SYNC_WINDOW_MS * FingerprintEngine.SAMPLE_RATE / 1000 / FingerprintEngine.HOP).toInt()
        val windowMs = (windowFrames - 1).toLong() * FingerprintEngine.HOP * 1000 / FingerprintEngine.SAMPLE_RATE
        val window = ArrayDeque<Int>()
        var lastMatch = 0L
        var framesSinceLevel = 0
        streamFrames(recorder) { frame ->
            window.addLast(FingerprintEngine.fingerprintFrame(frame))
            while (window.size > windowFrames) window.removeFirst()
            framesSinceLevel++
            if (framesSinceLevel >= 8) {
                framesSinceLevel = 0
                var sum = 0.0
                for (v in frame) sum += v.toDouble() * v
                val rms = sqrt(sum / frame.size)
                val level = if (rms < 0.004) 0 else (rms * 12000).toInt().coerceIn(1, 200)
                sendBroadcast(
                    Intent(ACTION_RESULT).putExtra(EXTRA_SESSION_ID, sessionId)
                        .putExtra(EXTRA_LEVEL, level)
                        .setPackage(packageName)
                )
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastMatch >= SYNC_INTERVAL_MS && window.size >= windowFrames) {
                lastMatch = now
                val m = db.match(window.toList(), minVotes = 4)
                if (m != null) {
                    // match 返回窗口首帧位置；当前位置 ≈ 首帧 + 窗口时长 + 管道延迟
                    val sample = m.first + windowMs + SAMPLE_LATENCY_MS
                    sendBroadcast(
                        Intent(ACTION_RESULT).putExtra(EXTRA_SESSION_ID, sessionId)
                            .putExtra(EXTRA_POSITION_MS, sample)
                            .putExtra(EXTRA_VOTES, m.second)
                            .setPackage(packageName)
                    )
                } else {
                    sendBroadcast(
                        Intent(ACTION_RESULT).putExtra(EXTRA_SESSION_ID, sessionId)
                            .putExtra(EXTRA_POSITION_MS, -1L)
                            .setPackage(packageName)
                    )
                }
            }
        }
        broadcast("音频跟随已停止")
    }
}
