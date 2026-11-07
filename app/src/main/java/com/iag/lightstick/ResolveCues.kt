package com.iag.lightstick

import org.json.JSONObject
import java.math.BigInteger
import java.util.Locale
import kotlin.math.floor

/** The Resolve bridge's device-independent format. Frame ends are exclusive and relative. */
data class ResolveCue(
    val startFrame: Long, val endFrame: Long, val mode: String, val color: String,
    val endColor: String? = null, val speed: Int? = null, val brightness: Int = 100,
)

data class ResolveLightState(val mode: String, val color: String = "#000000", val speed: Int? = null) {
    companion object { val BLACK = ResolveLightState("solid") }
}

data class ResolveSample(val state: ResolveLightState, val cueIndex: Int?, val gradient: Boolean)

class ResolveCueSheet private constructor(
    val numerator: Long, val denominator: Long,
    val startFrame: Long, val timecodeStart: String, val name: String,
    val durationFrames: Long, val durationMs: Long, val cues: List<ResolveCue>,
) {
    private fun scaledFrame(frame: Long) = frame * denominator * 1000L
    private fun ceilMillis(frame: Long) = (scaledFrame(frame) + numerator - 1L) / numerator
    private fun indexAt(time: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var index = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (scaledFrame(cues[mid].startFrame) <= time) { index = mid; low = mid + 1 } else high = mid - 1
        }
        return index
    }

    /** Wake early for a short cue instead of rounding all frame boundaries onto the 33ms UI tick. */
    fun nextDelayMs(positionMs: Long): Long {
        if (positionMs < 0L || positionMs >= durationMs) return 33L
        val time = positionMs * numerator
        val i = indexAt(time)
        val next = if (i >= 0 && time < scaledFrame(cues[i].endFrame)) cues[i].endFrame
            else cues.getOrNull(i + 1)?.startFrame ?: durationFrames
        return (ceilMillis(next) - positionMs).coerceIn(1L, 33L)
    }

    fun sample(positionMs: Long): ResolveSample {
        if (positionMs < 0L || positionMs >= durationMs) return ResolveSample(ResolveLightState.BLACK, null, false)
        val time = positionMs * numerator
        val index = indexAt(time)
        if (index < 0 || time >= scaledFrame(cues[index].endFrame)) return ResolveSample(ResolveLightState.BLACK, null, false)
        val cue = cues[index]
        if (cue.mode == "off" || cue.brightness == 0) return ResolveSample(ResolveLightState.BLACK, index, false)
        val rgb = components(cue.color)
        if (cue.mode == "gradient") {
            val end = components(cue.endColor!!)
            val fraction = (time - scaledFrame(cue.startFrame)).toDouble() / scaledFrame(cue.endFrame - cue.startFrame)
            // The existing Python evaluator uses round(): ties to even, before brightness scaling.
            for (channel in 0..2) rgb[channel] = Math.rint(rgb[channel] + (end[channel] - rgb[channel]) * fraction).toInt()
        }
        for (channel in 0..2) rgb[channel] = floor(rgb[channel] * cue.brightness / 100.0 + 0.5).toInt().coerceIn(0, 255)
        val color = "#%02X%02X%02X".format(Locale.ROOT, rgb[0], rgb[1], rgb[2])
        val mode = when (cue.mode) { "breathing" -> "breathing"; "flash", "fast_flash", "slow_flash" -> "flash"; else -> "solid" }
        val speed = if (mode == "solid") null else cue.speed ?: when (cue.mode) { "breathing" -> 5; "slow_flash" -> 20; else -> 6 }
        return ResolveSample(ResolveLightState(mode, color, speed), index, cue.mode == "gradient")
    }

    fun toDisplayArrangement(): LightingArrangement = LightingArrangement(
        version = 1, name = name.ifBlank { "Resolve 灯效" }, videoUrl = "", durationMs = durationMs,
        bpm = 120.0, snapMs = 1, snapToBeat = false,
        tracks = listOf(ArrangementTrack("resolve", "IAG", 1, true)),
        events = cues.mapIndexed { i, cue ->
            val displayTime = ceilMillis(cue.startFrame)
            ArrangementEvent("resolve_$i", "resolve", displayTime, ceilMillis(cue.endFrame) - displayTime,
                cue.color, when (cue.mode) {
                    "solid" -> ArrangementEffect.SOLID; "off" -> ArrangementEffect.OFF
                    "breathing" -> ArrangementEffect.BREATHE; "gradient" -> ArrangementEffect.FADE
                    "fast_flash" -> ArrangementEffect.FAST_BLINK; else -> ArrangementEffect.BLINK
                }, periodMs = 500)
        },
    )

    companion object {
        private val MODES = setOf("solid", "breathing", "flash", "fast_flash", "slow_flash", "off", "gradient")
        private val ROOT_KEYS = setOf("version", "fps", "start_frame", "timecode_start", "timeline_name", "duration_frames", "cues")
        private val CUE_KEYS = setOf("start_frame", "end_frame", "mode", "color", "end_color", "speed", "brightness")
        private fun integer(o: JSONObject, key: String, default: Long? = null): Long {
            if (!o.has(key)) return default ?: error("Resolve Cue 缺少 $key")
            val value = o.get(key)
            require(value is Int || value is Long) { "Resolve Cue 的 $key 必须是整数" }
            return (value as Number).toLong()
        }
        private fun text(o: JSONObject, key: String, default: String? = null): String {
            if (!o.has(key)) return default ?: error("Resolve Cue 缺少 $key")
            return (o.get(key) as? String)?.also { require(it.length <= 1024) { "$key 过长" } }
                ?: error("$key 必须是文本")
        }
        private fun color(o: JSONObject, key: String, default: String? = null): String =
            text(o, key, default).also { require(it.matches(Regex("#[0-9a-fA-F]{6}"))) { "$key 必须是 #RRGGBB" } }.uppercase(Locale.ROOT)
        private fun keys(o: JSONObject, allowed: Set<String>) {
            require(o.keys().asSequence().all { it in allowed }) { "Resolve Cue 包含未支持的字段" }
        }
        private fun components(hex: String) = IntArray(3) { hex.substring(1 + it * 2, 3 + it * 2).toInt(16) }

        fun fromJson(json: String): ResolveCueSheet {
            val root = JSONObject(json.removePrefix("\uFEFF"))
            keys(root, ROOT_KEYS)
            require(integer(root, "version", 1) == 1L) { "Resolve Cue 版本不支持" }
            val fps = root.getJSONObject("fps")
            keys(fps, setOf("numerator", "denominator"))
            val numerator = integer(fps, "numerator")
            val denominator = integer(fps, "denominator")
            require(numerator in 1..1_000_000L && denominator in 1..1_000_000L && numerator <= denominator * 1000L) { "Resolve 帧率须在 0–1000fps 内且使用有效整数分数" }
            val origin = integer(root, "start_frame", 0)
            require(origin >= 0L) { "Resolve 时间轴起帧不能为负" }
            val tc = text(root, "timecode_start", "00:00:00:00")
            val name = text(root, "timeline_name", "")
            val array = root.getJSONArray("cues")
            require(array.length() <= 50_000) { "Resolve Cue 数量过多" }
            val cues = (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                keys(o, CUE_KEYS)
                val start = integer(o, "start_frame")
                val end = integer(o, "end_frame")
                require(start >= 0 && end > start) { "Cue ${index + 1} 必须满足 0 ≤ 开始帧 < 结束帧" }
                val mode = text(o, "mode")
                require(mode in MODES) { "不支持的 Resolve 灯效：$mode" }
                val rgb = color(o, "color", "#000000")
                val brightness = integer(o, "brightness", 100)
                require(brightness in 0..100L) { "Cue 亮度须为 0–100" }
                val endColor = if (o.has("end_color") && !o.isNull("end_color")) color(o, "end_color") else null
                require((mode == "gradient") == (endColor != null)) { "仅渐变灯效须指定 end_color" }
                val speed = if (o.has("speed") && !o.isNull("speed")) integer(o, "speed") else null
                require(speed == null || speed in 1..255L && mode in setOf("breathing", "flash", "fast_flash", "slow_flash")) { "speed 须为原生呼吸/闪烁的 1–255 参数字节" }
                ResolveCue(start, end, mode, if (mode == "off") "#000000" else rgb, endColor, speed?.toInt(), if (mode == "off") 100 else brightness.toInt())
            }.sortedWith(compareBy<ResolveCue> { it.startFrame }.thenBy { it.endFrame })
            cues.zipWithNext().forEach { (a, b) -> require(a.endFrame <= b.startFrame) { "Resolve 灯效块重叠，请先在时间轴中消除重叠" } }
            val duration = if (!root.has("duration_frames") || root.isNull("duration_frames")) cues.lastOrNull()?.endFrame ?: 0L else integer(root, "duration_frames")
            require(duration > 0 && duration >= (cues.lastOrNull()?.endFrame ?: 0L)) { "Resolve 时间轴为空或时长小于灯效范围" }
            val milliseconds = BigInteger.valueOf(duration).multiply(BigInteger.valueOf(denominator)).multiply(BigInteger.valueOf(1000))
                .add(BigInteger.valueOf(numerator - 1)).divide(BigInteger.valueOf(numerator))
            require(milliseconds in BigInteger.ONE..BigInteger.valueOf(86_400_000L)) { "Resolve 时间轴时长须大于 0 且不超过 24 小时" }
            return ResolveCueSheet(numerator, denominator, origin, tc, name, duration, milliseconds.toLong(), cues)
        }
    }
}

/** Native states are sent once; only host gradients sample, at most every 100ms between edges. */
internal class ResolveOutputGate {
    private var last: ResolveLightState? = null
    private var cue: Int? = null
    private var sentAt = 0L
    fun reset() { last = null; cue = null; sentAt = 0L }
    fun next(sample: ResolveSample, nowMs: Long, force: Boolean = false): ResolveLightState? {
        if (!force && (sample.state == last || sample.gradient && sample.cueIndex == cue && nowMs - sentAt in 0..99L)) return null
        last = sample.state; cue = sample.cueIndex; sentAt = nowMs
        return sample.state
    }
}
