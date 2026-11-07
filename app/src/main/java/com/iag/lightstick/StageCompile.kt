package com.iag.lightstick

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * StageLight Compile 层 1:1 移植（jiaoben.js StageLightCompile）。
 * 唯一职责：给定一条 cue + 绝对时刻 t（秒），产出标准协议帧所需字段。
 * 频率语义：
 *  - Flash（设备 0x03）：Hz = 52.05/byte，byte = round(52.05/Hz)，范围 5–255，Hz 0.5–10（实测反比）。
 *  - Breathe（设备 0x04）：档位 1..20（非 Hz）；档位→Hz 用相机实测表线性插值，档位 1–2 未标定返回 null。
 *  - Pulse/Strobe（宿主模拟）：requestedHz 受 BLE 可达上限 2.5Hz 约束。
 */

object StageCompile {

    const val FLASH_HZ_MIN = 0.5
    const val FLASH_HZ_MAX = 10.0
    const val FLASH_BYTE_MIN = 5
    const val FLASH_BYTE_MAX = 255
    const val FLASH_K = 52.05

    const val SW_HZ_MIN = 0.5
    const val SW_HZ_MAX = 20.0
    const val SW_BLE_MAX_HZ = 2.5

    const val BREATHE_GEAR_MIN = 1
    const val BREATHE_GEAR_MAX = 20

    /** 呼吸档位实测表（2026-10-13 相机标定）：正比 Hz ≈ 0.1928 × 档位。 */
    val BREATHE_GEAR_HZ_MEASURED = listOf(
        3 to 0.590, 4 to 0.780, 5 to 0.977, 6 to 1.165, 8 to 1.579,
        10 to 1.935, 12 to 2.310, 15 to 3.000, 20 to 3.750,
    )

    @Volatile
    private var breatheTable: List<Pair<Int, Double>>? = BREATHE_GEAR_HZ_MEASURED

    data class CompiledFrame(
        val cmd: Int,
        val rgb: IntArray,
        val freqByte: Int?,
        val realHz: Double? = null,
        val multiColor: Boolean = false,
    ) {
        val hex: String get() = "#%02X%02X%02X".format(rgb[0], rgb[1], rgb[2])
    }

    // ---------- 基础颜色 ----------

    fun toRgb(hex: String?): IntArray {
        var h = (hex ?: "#000000").replace("#", "")
        if (h.length == 3) h = h.map { it }.joinToString("") { it.toString() + it }
        if (!Regex("^[0-9a-fA-F]{6}$").matches(h)) h = "000000"
        return intArrayOf(
            h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16),
        )
    }

    fun rgbToHex(rgb: IntArray): String =
        "#" + rgb.joinToString("") { v -> v.coerceIn(0, 255).toString(16).padStart(2, '0') }

    fun lerpRgb(a: String?, b: String?, t: Double): DoubleArray {
        val A = toRgb(a); val B = toRgb(b)
        return doubleArrayOf(
            A[0] + (B[0] - A[0]) * t, A[1] + (B[1] - A[1]) * t, A[2] + (B[2] - A[2]) * t,
        )
    }

    fun hsvToRgb(h: Double, s: Double, v: Double): DoubleArray {
        val i = floor(h * 6).toInt()
        val f = h * 6 - i
        val p = v * (1 - s)
        val q = v * (1 - f * s)
        val t = v * (1 - (1 - f) * s)
        return when (((i % 6) + 6) % 6) {
            0 -> doubleArrayOf(v, t, p)
            1 -> doubleArrayOf(q, v, p)
            2 -> doubleArrayOf(p, v, t)
            3 -> doubleArrayOf(p, q, v)
            4 -> doubleArrayOf(t, p, v)
            else -> doubleArrayOf(v, p, q)
        }.map { it * 255 }.toDoubleArray()
    }

    fun applyEase(t: Double, ease: String?): Double = when (ease) {
        "easeIn" -> t * t
        "easeOut" -> 1 - (1 - t) * (1 - t)
        "easeInOut" -> if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
        else -> t
    }

    private fun scaleRgb(rgb: DoubleArray, k: Double): DoubleArray =
        doubleArrayOf(rgb[0] * k, rgb[1] * k, rgb[2] * k)

    private fun roundRgb(rgb: DoubleArray): IntArray = rgb.map { it.roundToInt() }.toIntArray()

    private fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    // ---------- Flash 频率 ----------

    fun flashHzToByte(hz: Double): Int {
        val h = clamp(if (hz.isFinite()) hz else 1.0, FLASH_HZ_MIN, FLASH_HZ_MAX)
        val byte = FLASH_K / h
        return clamp(byte.roundToInt().toDouble(), FLASH_BYTE_MIN.toDouble(), FLASH_BYTE_MAX.toDouble()).toInt()
    }

    fun flashByteToHz(byte: Int): Double {
        val b = clamp(byte.toDouble(), FLASH_BYTE_MIN.toDouble(), FLASH_BYTE_MAX.toDouble())
        return FLASH_K / b
    }

    data class FlashRate(val requestedHz: Double, val freqByte: Int, val effectiveHz: Double)

    fun getFlashRate(requestedHz: Double): FlashRate {
        val hz = clamp(if (requestedHz.isFinite()) requestedHz else 1.0, FLASH_HZ_MIN, FLASH_HZ_MAX)
        val freqByte = flashHzToByte(hz)
        return FlashRate(hz, freqByte, flashByteToHz(freqByte))
    }

    // ---------- 软件模拟频率 ----------

    fun softwareHz(requestedHz: Double?): Double {
        val hz = clamp(if (requestedHz != null && requestedHz.isFinite()) requestedHz else 3.0, SW_HZ_MIN, SW_HZ_MAX)
        return min(hz, SW_BLE_MAX_HZ)
    }

    // ---------- 呼吸档位 ----------

    fun breatheGear(freq: Double?): Int =
        clamp((if (freq != null && freq.isFinite()) freq else 3.0).roundToInt().toDouble(),
            BREATHE_GEAR_MIN.toDouble(), BREATHE_GEAR_MAX.toDouble()).toInt()

    fun setBreatheTable(pairs: List<Pair<Int, Double>>?) {
        breatheTable = pairs?.filter { it.second > 0 }?.sortedBy { it.first }?.takeIf { it.size >= 2 }
    }

    /** 档位 → 实测 Hz（线性插值）；低于实测下限（1–2 档）返回 null，不编造。 */
    fun breatheGearToHz(gear: Int): Double? {
        val table = breatheTable ?: return null
        val g = gear.toDouble()
        if (g <= 0) return null
        val first = table.first()
        if (g < first.first) return null
        if (g == first.first.toDouble()) return first.second
        val last = table.last()
        if (g >= last.first) return last.second
        for (i in 1 until table.size) {
            val (g0, h0) = table[i - 1]
            val (g1, h1) = table[i]
            if (g <= g1) {
                val k = (g - g0) / (g1 - g0)
                return h0 + (h1 - h0) * k
            }
        }
        return last.second
    }

    // ---------- 闪烁频率关键帧（fk） ----------

    private fun clampHz(hz: Double): Double = max(FLASH_HZ_MIN, min(FLASH_HZ_MAX, if (hz.isFinite()) hz else FLASH_HZ_MIN))

    private fun flashKeys(cue: RrnnCue): List<Pair<Double, Double>>? {
        val raw = cue.fk ?: return null
        val ks = raw
            .filter { it.first.isFinite() && it.second.isFinite() }
            .map { max(0.0, it.first) to clampHz(it.second) }
            .sortedBy { it.first }
        return if (ks.size >= 2) ks else null
    }

    fun hasFlashKeys(cue: RrnnCue): Boolean = flashKeys(cue) != null

    /** 该时刻请求的 Hz：有关键帧则线性插值（Hz 空间），否则 null（调用方用 freq）。 */
    private fun flashHzAt(cue: RrnnCue, tAbs: Double): Double? {
        val ks = flashKeys(cue) ?: return null
        val dt = max(0.0, tAbs - cue.start)
        if (dt <= ks[0].first) return ks[0].second
        val lastK = ks.last()
        if (dt >= lastK.first) return lastK.second
        for (i in 1 until ks.size) {
            val (t0, h0) = ks[i - 1]
            val (t1, h1) = ks[i]
            if (dt <= t1) return clampHz(h0 + (h1 - h0) * (dt - t0) / (t1 - t0))
        }
        return lastK.second
    }

    /** 多色闪烁颜色序列（2..8 个 #RRGGBB）。 */
    private fun flashColors(cue: RrnnCue): List<String>? {
        val colors = cue.flashColors
            ?.take(8)
            ?.filter { Regex("^#[0-9a-fA-F]{6}$").matches(it) }
            ?.map { it.uppercase() }
            ?: return null
        return if (colors.size >= 2) colors else null
    }

    /** 多色闪烁为宿主端软件模拟：累计相位 = flashPhase + ∫softwareHz。 */
    private fun multiFlashCyclesAt(cue: RrnnCue, dt: Double): Double {
        val t = max(0.0, dt)
        val phase = max(0.0, cue.flashPhase ?: 0.0)
        val ks = flashKeys(cue)
        if (ks == null) return phase + t * softwareHz(cue.freq)
        val scaled = ks.map { it.first to softwareHz(it.second) }
        var acc = min(t, scaled[0].first) * scaled[0].second
        if (t <= scaled[0].first) return phase + acc
        for (i in 1 until scaled.size) {
            val (ta, ha) = scaled[i - 1]
            val (tb, hb) = scaled[i]
            if (t <= ta) break
            val end = min(t, tb)
            val hEnd = ha + (hb - ha) * (end - ta) / (tb - ta)
            acc += (ha + hEnd) / 2 * (end - ta)
            if (t <= tb) return phase + acc
        }
        val lastK = scaled.last()
        return phase + acc + max(0.0, t - lastK.first) * lastK.second
    }

    private fun flashColorAt(cue: RrnnCue, tAbs: Double): String {
        val colors = flashColors(cue) ?: return cue.color ?: "#D18EFF"
        val cycles = multiFlashCyclesAt(cue, clamp(tAbs - cue.start, 0.0, cue.dur))
        return colors[floor(cycles + 1e-9).toInt().mod(colors.size)]
    }

    // ---------- 渐变相位 ----------

    private const val GRAD_CYCLE_MIN = 0.05
    private const val GRAD_CYCLE_MAX = 3600.0

    private fun gradCycleSec(cue: RrnnCue): Double {
        val dur = max(0.05, if (cue.dur.isFinite() && cue.dur >= 1.0) cue.dur else 1.0)
        var c = cue.cycleSec ?: 0.0
        if (c <= 0 || !c.isFinite()) {
            val legacy = (cue.repeat ?: 0.0).roundToInt()
            c = if (legacy >= 1) dur / legacy else dur
        }
        return max(GRAD_CYCLE_MIN, min(GRAD_CYCLE_MAX, c))
    }

    /** 返回 0..1 的颜色插值位置；pingpong 为三角波。 */
    private fun gradientPhase(cue: RrnnCue, p: Double): Double {
        val pClamped = clamp(p, 0.0, 1.0)
        val dur = max(0.05, if (cue.dur.isFinite() && cue.dur >= 1.0) cue.dur else 1.0)
        val cyc = gradCycleSec(cue)
        val elapsed = pClamped * dur + (cue.phaseOffset ?: 0.0)
        if (cue.gradientMode == "once") return clamp(elapsed / cyc, 0.0, 1.0)
        val x = elapsed / cyc
        val ping = cue.pingpong
        if (!ping && x == floor(x) && elapsed >= (cue.phaseDuration ?: dur) - 1e-9) return 1.0
        val i = floor(x)
        val frac = x - i
        return if (ping) (if (i.toLong() % 2 == 0L) frac else 1 - frac) else frac
    }

    // ---------- 包络 ----------

    private fun cueEnvelope(cue: RrnnCue, dt: Double): Double {
        val duration = cue.phaseDuration ?: cue.dur
        val elapsed = dt + (cue.phaseOffset ?: 0.0)
        // 原生振荡（flash/breathe/off）无法在不重启硬件相位的情况下平滑淡入淡出
        if (cue.type == "flash" || cue.type == "breathe" || cue.type == "off") return 1.0
        val a = max(0.0, cue.fadeIn ?: 0.0)
        val b = max(0.0, cue.fadeOut ?: 0.0)
        return min(
            if (a > 0) applyEase(clamp(elapsed / a, 0.0, 1.0), cue.fadeEase) else 1.0,
            if (b > 0) applyEase(clamp((duration - elapsed) / b, 0.0, 1.0), cue.fadeEase) else 1.0,
        )
    }

    // ---------- 主函数：cue + tAbs → 标准帧 ----------

    fun compileAt(cue: RrnnCue, tAbs: Double): CompiledFrame {
        val p = clamp((tAbs - cue.start) / max(0.01, cue.dur), 0.0, 1.0)
        val kInt = ((cue.intensity ?: 100.0) / 100.0) * cueEnvelope(cue, p * cue.dur)

        if (cue.type == "off") return CompiledFrame(0x00, intArrayOf(0, 0, 0), null)

        if (cue.type == "flash" && flashColors(cue) != null) {
            val cycles = multiFlashCyclesAt(cue, p * cue.dur)
            val lit = cycles - floor(cycles + 1e-9) < 0.5
            val rgb = if (lit) roundRgb(scaleRgb(toRgb(flashColorAt(cue, cue.start + p * cue.dur)).map { it.toDouble() }.toDoubleArray(), kInt)) else intArrayOf(0, 0, 0)
            return CompiledFrame(0x01, rgb, null, multiColor = true)
        }

        val baseRgb: DoubleArray = when (cue.type) {
            "gradient" -> lerpRgb(cue.color ?: "#D18EFF", cue.colorEnd ?: "#FF3B5C", gradientPhase(cue, p))
            "rainbow" -> hsvToRgb((p * cue.dur + (cue.phaseOffset ?: 0.0)) / (cue.phaseDuration ?: cue.dur), 1.0, 1.0)
            else -> toRgb(cue.color ?: "#D18EFF").map { it.toDouble() }.toDoubleArray()
        }

        when (cue.type) {
            "color" -> {
                val rgb = roundRgb(scaleRgb(baseRgb, kInt))
                return CompiledFrame(0x01, rgb, null)
            }
            "breathe" -> {
                val gear = breatheGear(cue.freq)
                val rgb = roundRgb(scaleRgb(baseRgb, kInt))
                return CompiledFrame(0x04, rgb, gear, realHz = breatheGearToHz(gear))
            }
            "flash" -> {
                val rampHz = flashHzAt(cue, tAbs)
                val rate = getFlashRate(rampHz ?: (cue.freq ?: 1.0))
                val rgb = roundRgb(scaleRgb(baseRgb, kInt))
                return CompiledFrame(0x03, rgb, rate.freqByte, realHz = rate.effectiveHz)
            }
        }

        // 软件模拟：pulse / strobe / gradient / rainbow —— 直接用 requestedHz（受 BLE 上限约束）
        val lo = ((cue.bmin ?: 20.0)) / 100.0
        val hi = ((cue.bmax ?: 100.0)) / 100.0
        val ph = p * cue.dur + (cue.phaseOffset ?: 0.0)
        val hz = softwareHz(cue.freq)
        var env = 1.0
        if (cue.type == "pulse") env = 0.5 + 0.5 * sin(ph * PI * 2 * hz)
        else if (cue.type == "strobe") env = if (floor(ph * hz * 2).toLong() % 2 == 0L) 1.0 else 0.0
        val eased = applyEase(env, cue.ease)
        val k = (lo + (hi - lo) * eased) * kInt
        val rgb = roundRgb(scaleRgb(baseRgb, k))
        return CompiledFrame(0x01, rgb, null)
    }

    // ---------- 协议帧（与 blue.js / jiaoben.js frame() 一致） ----------

    fun frame(cmd: Int, data: List<Int>): ByteArray {
        val prefix = ArrayList<Int>(5 + data.size)
        prefix.add(0x5A); prefix.add(0xA5)
        prefix.add(data.size + 1)
        prefix.add(0x00)
        prefix.add(cmd)
        prefix.addAll(data)
        val checksum = prefix.fold(0) { s, v -> (s + v) and 0xFF }
        return ByteArray(prefix.size + 1) { i -> if (i < prefix.size) prefix[i].toByte() else checksum.toByte() }
    }

    val OFF: ByteArray by lazy { frame(0x00, listOf(0)) }
    val BLACK: ByteArray by lazy { frame(0x01, listOf(0, 0, 0)) }

    fun pack(c: CompiledFrame): ByteArray = when (c.cmd) {
        0x00 -> OFF
        else -> frame(c.cmd, c.rgb.toList() + listOfNotNull(c.freqByte))
    }

    /** 活动 cue：later-cue-wins（同刻后开始者胜出，jiaoben.js active()）。 */
    fun <T> activeSelect(fxs: List<T>, t: Double, start: (T) -> Double, end: (T) -> Double): T? {
        var found: T? = null
        for (f in fxs) {
            if (t >= start(f) && t < end(f) && (found == null || start(f) >= start(found!!))) found = f
        }
        return found
    }

    fun active(fxs: List<RrnnCue>, t: Double): RrnnCue? =
        activeSelect(fxs, t, { it.start }, { it.start + it.dur })
}
