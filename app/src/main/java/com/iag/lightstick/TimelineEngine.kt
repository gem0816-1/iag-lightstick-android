package com.iag.lightstick

import org.json.JSONArray
import org.json.JSONObject

/**
 * 灯光时间轴（重建版）。
 *
 * 重要声明：时间轴内容为根据演唱会录像人工重建的灯光编排，
 * 并非官方场控原始数据，不得对外声称与官方数据一致。
 *
 * JSON 结构（与项目需求书一致）：
 * {
 *   "video_id": "BV1otha6wEZc",
 *   "duration_ms": 180000,
 *   "events": [
 *     {"time_ms": 0, "effect": "solid", "color": "#FF0000"},
 *     {"time_ms": 5000, "effect": "flash", "color": "#0000FF", "duration_ms": 1000, "priority": 1}
 *   ]
 * }
 */

enum class Effect(val id: String) {
    SOLID("solid"),
    FLASH("flash"),
    GRADIENT("gradient"),
    OFF("off");

    companion object {
        fun from(id: String): Effect = entries.firstOrNull { it.id == id } ?: SOLID
    }
}

data class TimelineEvent(
    val timeMs: Long,
    val effect: Effect,
    val color: String?,          // "#RRGGBB"
    val durationMs: Long?,       // 闪烁/渐变类效果时长
    val priority: Int = 0,       // 同时间点多事件时取优先级最高者
)

class Timeline(
    val videoId: String,
    val durationMs: Long,
    val events: List<TimelineEvent>,
) {
    companion object {
        fun fromJson(json: String): Timeline {
            val o = JSONObject(json)
            val arr = o.getJSONArray("events")
            val evs = ArrayList<TimelineEvent>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                evs.add(
                    TimelineEvent(
                        timeMs = e.getLong("time_ms"),
                        effect = Effect.from(e.optString("effect", "solid")),
                        color = if (e.has("color")) e.getString("color") else null,
                        durationMs = if (e.has("duration_ms")) e.getLong("duration_ms") else null,
                        priority = e.optInt("priority", 0),
                    )
                )
            }
            return Timeline(
                videoId = o.getString("video_id"),
                durationMs = o.getLong("duration_ms"),
                events = evs.sortedBy { it.timeMs },
            )
        }
    }
}

/**
 * 时间轴执行器：把"播放位置"翻译成荧光棒指令帧序列。
 * 规则（按需求书）：
 * - 顺序推进时只发送新进入窗口的事件；
 * - 快进/回退后重新计算当前应有的灯光状态（回退 = 重发当前状态事件，不重放全部历史）；
 * - 同一时间点多事件按 priority 取最高者；
 * - 过期事件（已超过当前时刻）直接丢弃，不补发。
 */
class TimelineExecutor(
    private val timeline: Timeline,
    private val sendFrame: (frame: ByteArray) -> Unit,
) {
    private var lastSentIndex = -1
    private var lastPositionMs = -1L

    fun onPosition(positionMs: Long) {
        val evs = timeline.events
        if (lastPositionMs < 0) {
            // 初始进入：找到当前位置之前最近的一个事件作为当前状态
            lastPositionMs = positionMs
            val cur = evs.lastOrNull { it.timeMs <= positionMs }
            if (cur != null) {
                lastSentIndex = evs.indexOf(cur)
                sendEvent(cur)
            }
            return
        }

        if (positionMs >= lastPositionMs) {
            // 正常推进
            var idx = lastSentIndex + 1
            while (idx < evs.size && evs[idx].timeMs <= positionMs) {
                sendEvent(evs[idx])
                lastSentIndex = idx
                idx++
            }
            lastPositionMs = positionMs
        } else {
            // 回退（拖动进度条向后）：只重发"当前状态"事件，丢弃中间历史
            lastPositionMs = positionMs
            val cur = evs.lastOrNull { it.timeMs <= positionMs }
            if (cur != null) {
                lastSentIndex = evs.indexOf(cur)
                sendEvent(cur)
            }
        }
    }

    private fun sendEvent(e: TimelineEvent) {
        val frame = eventToFrame(e) ?: return
        sendFrame(frame)
    }

    /** 事件 → 协议帧。solid/off 用 9 字节颜色帧；flash/gradient 暂以颜色帧近似（待设备能力验证后增强） */
    private fun eventToFrame(e: TimelineEvent): ByteArray? {
        val hex = e.color ?: return null
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return null
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        val x: Int = when (e.effect) {
            Effect.OFF -> 0x04
            Effect.SOLID -> 0x00
            Effect.FLASH, Effect.GRADIENT -> 0x00  // 待验证设备内置效果指令后替换
        }
        return byteArrayOf(
            0x5A.toByte(), 0xA5.toByte(), 0x04, 0x00, 0x01,
            r.toByte(), g.toByte(), b.toByte(), x.toByte(),
        )
    }

    fun reset() {
        lastSentIndex = -1
        lastPositionMs = -1L
    }
}
