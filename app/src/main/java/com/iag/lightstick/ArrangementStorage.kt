package com.iag.lightstick

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** JSON format shared with lighting-arranger/lighting_arranger/models.py (project version 1). */
data class ArrangementTrack(
    val id: String,
    val name: String,
    val priority: Int,
    val enabled: Boolean,
)

enum class ArrangementEffect(val id: String, val label: String) {
    SOLID("solid", "常亮"),
    OFF("off", "关闭"),
    BLINK("blink", "闪烁"),
    FAST_BLINK("fast_blink", "快闪"),
    FADE("fade", "渐变"),
    BREATHE("breathe", "呼吸"),
    GRADIENT("gradient", "渐变循环");

    companion object {
        fun parse(value: String): ArrangementEffect = entries.firstOrNull { it.id == value }
            ?: throw IllegalArgumentException("不支持的灯效：$value")
    }
}

data class ArrangementEvent(
    val id: String,
    val trackId: String,
    val startMs: Long,
    val durationMs: Long,
    val color: String,
    val effect: ArrangementEffect,
    val periodMs: Long,
    val flashHz: Double? = null,
    val breatheGear: Int? = null,
) {
    val endMs: Long get() = startMs + durationMs
}

data class LightingArrangement(
    val version: Int,
    val name: String,
    val videoUrl: String,
    val durationMs: Long,
    val bpm: Double,
    val snapMs: Int,
    val snapToBeat: Boolean,
    val tracks: List<ArrangementTrack>,
    val events: List<ArrangementEvent>,
) {
    fun activeEventAt(positionMs: Long): ArrangementEvent? {
        val trackOrder = tracks.mapIndexed { index, track -> track.id to index }.toMap()
        val enabledTracks = tracks.associateBy { it.id }
        return events.asSequence()
            .filter { event ->
                enabledTracks[event.trackId]?.enabled == true &&
                    event.startMs <= positionMs && positionMs < event.endMs
            }
            .sortedWith { left, right ->
                val priority = compareValues(
                    enabledTracks[right.trackId]?.priority ?: 0,
                    enabledTracks[left.trackId]?.priority ?: 0,
                )
                if (priority != 0) priority else {
                    val track = compareValues(trackOrder[left.trackId] ?: Int.MAX_VALUE, trackOrder[right.trackId] ?: Int.MAX_VALUE)
                    if (track != 0) track else {
                        val start = compareValues(right.startMs, left.startMs)
                        if (start != 0) start else left.id.compareTo(right.id)
                    }
                }
            }
            .firstOrNull()
    }

    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("name", name)
        put("video_url", videoUrl)
        put("duration_ms", durationMs)
        put("bpm", bpm)
        put("snap_ms", snapMs)
        put("snap_to_beat", snapToBeat)
        put("tracks", JSONArray().apply {
            tracks.forEach { track -> put(JSONObject().apply {
                put("id", track.id)
                put("name", track.name)
                put("priority", track.priority)
                put("enabled", track.enabled)
            }) }
        })
        put("events", JSONArray().apply {
            events.forEach { event -> put(JSONObject().apply {
                put("id", event.id)
                put("track_id", event.trackId)
                put("start_ms", event.startMs)
                put("duration_ms", event.durationMs)
                put("color", event.color)
                put("effect", event.effect.id)
                put("period_ms", event.periodMs)
                event.flashHz?.let { put("flash_hz", it) }
                event.breatheGear?.let { put("breathe_gear", it) }
            }) }
        })
    }.toString(2)

    companion object {
        fun fromJson(json: String): LightingArrangement {
            val root = JSONObject(json)
            val version = root.getInt("version")
            require(version == 1) { "工程版本 $version 暂不支持，只能读取版本 1" }
            val name = root.getString("name").trim()
            require(name.isNotEmpty()) { "工程名称不能为空" }
            val videoUrl = root.optString("video_url", "").trim()
            val durationMs = root.getLong("duration_ms")
            require(durationMs in 1_000L..86_400_000L) { "工程时长须在 1 秒至 24 小时之间" }
            val bpm = root.getDouble("bpm")
            require(bpm > 0.0 && bpm <= 400.0) { "BPM 须在 0 到 400 之间" }
            val snapMs = root.getInt("snap_ms")
            require(snapMs > 0) { "吸附间隔必须大于 0" }
            val snapToBeat = root.optBoolean("snap_to_beat", false)
            val trackJson = root.getJSONArray("tracks")
            require(trackJson.length() > 0) { "工程至少需要一条轨道" }
            val tracks = (0 until trackJson.length()).map { index ->
                val item = trackJson.getJSONObject(index)
                ArrangementTrack(
                    id = item.getString("id").also { require(it.isNotBlank()) { "轨道 ID 不能为空" } },
                    name = item.getString("name").trim().also { require(it.isNotEmpty()) { "轨道名称不能为空" } },
                    priority = item.getInt("priority"),
                    enabled = item.optBoolean("enabled", true),
                )
            }
            require(tracks.map { it.id }.distinct().size == tracks.size) { "轨道 ID 重复" }
            val trackIds = tracks.map { it.id }.toSet()
            val eventJson = root.getJSONArray("events")
            val events = (0 until eventJson.length()).map { index ->
                val item = eventJson.getJSONObject(index)
                val id = item.getString("id").also { require(it.isNotBlank()) { "事件 ID 不能为空" } }
                val trackId = item.getString("track_id")
                require(trackId in trackIds) { "事件 $id 指向不存在的轨道" }
                val startMs = item.getLong("start_ms")
                val eventDuration = item.getLong("duration_ms")
                // Check by subtraction: start + duration can wrap even for two positive Longs.
                require(startMs in 0 until durationMs && eventDuration > 0L && eventDuration <= durationMs - startMs) {
                    "事件 $id 超出工程时间范围"
                }
                val color = item.getString("color").uppercase()
                require(color.matches(Regex("#[0-9A-F]{6}"))) { "事件 $id 颜色格式错误，应为 #RRGGBB" }
                val periodMs = item.getLong("period_ms")
                require(periodMs >= 40L) { "事件 $id 的灯效周期至少为 40 毫秒" }
                val flashHz = if (item.has("flash_hz") && !item.isNull("flash_hz")) item.getDouble("flash_hz") else null
                require(flashHz == null || flashHz.isFinite() && flashHz in 0.5..10.0) {
                    "事件 $id 的闪烁频率必须在 0.5 到 10 Hz 之间"
                }
                val breatheGear = if (item.has("breathe_gear") && !item.isNull("breathe_gear")) item.getInt("breathe_gear") else null
                require(breatheGear == null || breatheGear in 1..20) { "事件 $id 的呼吸档位必须在 1 到 20 之间" }
                ArrangementEvent(
                    id = id,
                    trackId = trackId,
                    startMs = startMs,
                    durationMs = eventDuration,
                    color = color,
                    effect = ArrangementEffect.parse(item.getString("effect")),
                    periodMs = periodMs,
                    flashHz = flashHz,
                    breatheGear = breatheGear,
                )
            }
            require(events.map { it.id }.distinct().size == events.size) { "事件 ID 重复" }
            return LightingArrangement(version, name, videoUrl, durationMs, bpm, snapMs, snapToBeat, tracks, events)
        }
    }
}

/** rrnn != null 时为 RRNN 官方载荷（jiaoben.js schema v2），播放走 StageCompile；arrangement 仅作展示。 */
data class StoredArrangement(val id: String, val arrangement: LightingArrangement,
    val rrnn: RrnnPayload? = null, val resolve: ResolveCueSheet? = null)

internal data class ArrangementDocument(val arrangement: LightingArrangement,
    val rrnn: RrnnPayload? = null, val resolve: ResolveCueSheet? = null) {
    fun stored(id: String) = StoredArrangement(id, arrangement, rrnn, resolve)
}

/** Decode for playback/display; storage and sharing retain the complete original document. */
internal object ArrangementDocuments {
    fun decode(text: String): ArrangementDocument {
        val json = text.removePrefix("\uFEFF")
        val root = JSONObject(json)
        return when {
            root.optInt("schemaVersion", -1) == 2 -> RrnnPayload.fromJson(json).let { ArrangementDocument(it.toDisplayArrangement(), rrnn = it) }
            root.has("cues") && root.has("fps") && !root.has("tracks") -> ResolveCueSheet.fromJson(json).let { ArrangementDocument(it.toDisplayArrangement(), resolve = it) }
            else -> ArrangementDocument(LightingArrangement.fromJson(json))
        }
    }
}

/** 内部 App 存储 keeps imported projects private and available offline. */
class ArrangementStorage internal constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, "arrangements"))
    companion object { private val LOCK = Any() }


    /** 读取失败但原文件保留的条目；reason 面向用户展示，须短且不含完整文件内容。 */
    data class ReadFailure(val fileName: String, val reason: String)

    fun list(onReadFailure: ((ReadFailure) -> Unit)? = null): List<StoredArrangement> = directory.listFiles { file -> file.extension == "json" }
        .orEmpty()
        .sortedByDescending(File::lastModified)
        .mapNotNull { file ->
            runCatching {
                // Limits apply at untrusted import/network boundaries, not to existing user files.
                val text = file.readText(Charsets.UTF_8)
                ArrangementDocuments.decode(text).stored(file.nameWithoutExtension)
            }.onFailure { error ->
                // 损坏工程不删除、不改写，只上报让用户知晓（审计 P1：损坏私有工程反馈）。
                onReadFailure?.invoke(ReadFailure(file.name, (error.message ?: error.javaClass.simpleName).take(120)))
            }.getOrNull()
        }

    fun importJson(json: String, requestId: String? = null): StoredArrangement = synchronized(LOCK) {
        // A restored external-import request reuses its private ID, preventing rotation duplicates.
        if (requestId != null) require(requestId.matches(Regex("[0-9a-fA-F-]{36}"))) { "导入请求标识无效" }
        val id = requestId ?: UUID.randomUUID().toString()
        val target = File(directory, "$id.json")
        if (requestId != null && target.exists()) return@synchronized decodeImport(target.readText(Charsets.UTF_8)).stored(id)
        val parsed = decodeImport(json)
        AtomicFiles.text(target, json)
        parsed.stored(id)
    }

    fun rawJson(id: String): String = synchronized(LOCK) {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "工程标识无效" }
        File(directory, "$id.json").readText(Charsets.UTF_8)
    }

    /** 在线曲库 upsert（JIHUA §3）：按 name+videoUrl 覆盖或新增；存原始 JSON 以保留 reference 等新字段。 */
    fun upsertRaw(json: String): StoredArrangement = synchronized(LOCK) {
        val parsed = decodeImport(json)
        val project = parsed.arrangement
        val existing = list().firstOrNull {
            it.arrangement.name == project.name && it.arrangement.videoUrl == project.videoUrl
        }
        if (existing != null) {
            AtomicFiles.text(File(directory, "${existing.id}.json"), json)
            parsed.stored(existing.id)
        } else {
            val id = UUID.randomUUID().toString()
            AtomicFiles.text(File(directory, "$id.json"), json)
            parsed.stored(id)
        }
    }

    private fun decodeImport(json: String): ArrangementDocument = runCatching { ArrangementDocuments.decode(json) }
        .getOrElse { error("编排文件无效：${it.message ?: "不支持的格式"}") }

    fun delete(id: String) = synchronized(LOCK) {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "工程标识无效" }
        val target = File(directory, "$id.json")
        if (target.exists() && !target.delete()) error("无法删除工程文件")
    }
}
