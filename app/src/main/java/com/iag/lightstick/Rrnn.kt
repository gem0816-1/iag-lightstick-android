package com.iag.lightstick

import org.json.JSONObject

/*
 * RRNN 官方回放数据（jiaoben.js payload schema）：
 * schemaVersion 2 / timeDomain 'target-video-seconds' / target{key,cid,duration,bvid,page} / fxs[]。
 * 由自有服务器 library.json 分发（rrnn.bj.cn/iag-lightapp），App 端解析宽容（未知字段忽略）。
 */

data class RrnnCue(
    val type: String,
    val start: Double,
    val dur: Double,
    val color: String? = null,
    val colorEnd: String? = null,
    val freq: Double? = null,
    val fk: List<Pair<Double, Double>>? = null,
    val flashColors: List<String>? = null,
    val flashPhase: Double? = null,
    val cycleSec: Double? = null,
    val pingpong: Boolean = false,
    val gradientMode: String? = null,
    val repeat: Double? = null,
    val phaseOffset: Double? = null,
    val phaseDuration: Double? = null,
    val fadeIn: Double? = null,
    val fadeOut: Double? = null,
    val fadeEase: String? = null,
    val ease: String? = null,
    val bmin: Double? = null,
    val bmax: Double? = null,
    val intensity: Double? = null,
)

class RrnnPayload(
    val title: String,
    val artist: String?,
    val bvid: String,
    val page: Int,
    val cid: String,
    val durationSec: Double,
    val fxs: List<RrnnCue>,
) {
    val videoUrl: String get() = "https://www.bilibili.com/video/$bvid" + if (page > 1) "?p=$page" else ""

    companion object {
        fun fromJson(json: String): RrnnPayload {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", -1)
            require(schemaVersion == 2) { "RRNN 载荷版本 $schemaVersion 不支持（仅 schemaVersion 2）" }
            val timeDomain = root.optString("timeDomain", "target-video-seconds")
            require(timeDomain == "target-video-seconds") { "RRNN 时间域不支持：$timeDomain" }
            val target = root.getJSONObject("target")
            val key = target.optString("key", "")
            val bvid = target.optString("bvid").ifBlank {
                Regex("BV[0-9A-Za-z]{8,12}").find(key)?.value ?: error("RRNN target 缺少 bvid")
            }
            val page = target.optInt("page", 1)
            val cid = target.optString("cid", "")
            val duration = target.optDouble("duration", 0.0)
            val title = root.optString("title").ifBlank { "RRNN $bvid" }
            val artist = root.optString("artist").ifBlank { null }
            val fxsJson = root.getJSONArray("fxs")
            val fxs = (0 until fxsJson.length()).map { i -> parseCue(fxsJson.getJSONObject(i)) }
            require(fxs.isNotEmpty()) { "RRNN fxs 为空" }
            val lastEnd = fxs.maxOf { it.start + it.dur }
            val dur = if (duration > 0.1) duration else lastEnd
            return RrnnPayload(title, artist, bvid, page, cid, dur, fxs)
        }

        private fun optHex(obj: JSONObject, name: String): String? {
            val v = obj.optString(name, "")
            return if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) v.uppercase() else null
        }

        private fun optDouble(obj: JSONObject, name: String): Double? {
            if (!obj.has(name) || obj.isNull(name)) return null
            val v = obj.optDouble(name, Double.NaN)
            return if (v.isFinite()) v else null
        }

        private fun parseCue(o: JSONObject): RrnnCue {
            val fkRaw = optDoublePairs(o, "fk")
            val flashColors = o.optJSONArray("flashColors")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val v = arr.optString(i, "")
                    if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) v.uppercase() else null
                }.takeIf { it.size >= 2 }
            }
            return RrnnCue(
                type = o.optString("type", "color"),
                start = optDouble(o, "start") ?: 0.0,
                dur = (optDouble(o, "dur") ?: 1.0).coerceAtLeast(0.01),
                color = optHex(o, "color"),
                colorEnd = optHex(o, "colorEnd"),
                freq = optDouble(o, "freq"),
                fk = fkRaw,
                flashColors = flashColors,
                flashPhase = optDouble(o, "flashPhase"),
                cycleSec = optDouble(o, "cycleSec"),
                pingpong = o.optBoolean("pingpong", false),
                gradientMode = o.optString("gradientMode").ifBlank { null },
                repeat = optDouble(o, "repeat"),
                phaseOffset = optDouble(o, "phaseOffset"),
                phaseDuration = optDouble(o, "phaseDuration"),
                fadeIn = optDouble(o, "fadeIn"),
                fadeOut = optDouble(o, "fadeOut"),
                fadeEase = o.optString("fadeEase").ifBlank { null },
                ease = o.optString("ease").ifBlank { null },
                bmin = optDouble(o, "bmin"),
                bmax = optDouble(o, "bmax"),
                intensity = optDouble(o, "intensity"),
            )
        }

        private fun optDoublePairs(o: JSONObject, name: String): List<Pair<Double, Double>>? {
            val arr = o.optJSONArray(name) ?: return null
            val out = ArrayList<Pair<Double, Double>>()
            for (i in 0 until arr.length()) {
                val pair = arr.optJSONArray(i) ?: continue
                if (pair.length() >= 2) {
                    val a = pair.optDouble(0, Double.NaN)
                    val b = pair.optDouble(1, Double.NaN)
                    if (a.isFinite() && b.isFinite()) out.add(a to b)
                }
            }
            return out.takeIf { it.size >= 2 }
        }
    }
}

/** RRNN → 展示用 LightingArrangement（时间轴画布/列表副标题复用现有 UI，播放走 StageCompile）。 */
fun RrnnPayload.toDisplayArrangement(): LightingArrangement {
    val events = fxs.mapIndexed { index, cue ->
        val color = cue.color ?: cue.colorEnd ?: "#000000"
        val effect = when (cue.type) {
            "off" -> ArrangementEffect.OFF
            "color" -> ArrangementEffect.SOLID
            "flash" -> ArrangementEffect.BLINK
            "breathe" -> ArrangementEffect.BREATHE
            "gradient", "rainbow" -> ArrangementEffect.GRADIENT
            else -> ArrangementEffect.FAST_BLINK
        }
        ArrangementEvent(
            id = "rrnn_$index",
            trackId = "rrnn",
            startMs = (cue.start * 1000).toLong(),
            durationMs = (cue.dur * 1000).toLong().coerceAtLeast(40),
            color = color,
            effect = effect,
            periodMs = 500,
            flashHz = if (cue.type == "flash") cue.freq else null,
            breatheGear = if (cue.type == "breathe") cue.freq?.toInt() else null,
        )
    }
    return LightingArrangement(
        version = 1,
        name = title,
        videoUrl = videoUrl,
        durationMs = (durationSec * 1000).toLong().coerceAtLeast(1000),
        bpm = 120.0,
        snapMs = 500,
        snapToBeat = false,
        tracks = listOf(ArrangementTrack("rrnn", title, 1, true)),
        events = events,
    )
}
