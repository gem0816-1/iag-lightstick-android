package com.iag.lightstick

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Validate the complete payload before touching local files; serialize all publishers. */
object LibrarySync {
    private val mutex = Mutex()
    internal data class Entry(val raw: String, val project: LightingArrangement, val reference: ByteArray?)
    internal data class Prepared(val version: Int?, val entries: List<Entry>)

    internal fun prepare(body: String): Prepared {
        require(body.trimStart().startsWith("{")) { "服务器返回网页，请上传 iag-lightapp/library.json" }
        val root = JSONObject(body)
        val version = if (root.has("libraryVersion") && !root.isNull("libraryVersion")) root.getInt("libraryVersion") else null
        require(version == null || version >= 0) { "曲库版本无效" }
        val items = if (version == null) listOf(root) else {
            val array = root.getJSONArray("arrangements")
            (0 until array.length()).map { array.getJSONObject(it) }
        }
        val entries = items.map { item ->
            val raw = item.toString()
            require(raw.toByteArray(Charsets.UTF_8).size <= InputLimits.PROJECT_BYTES) { "单份工程过大" }
            val project = ArrangementDocuments.decode(raw).arrangement
            val ref = item.optJSONObject("reference")
            require(!item.has("reference") || item.isNull("reference") || ref != null) { "参考指纹字段无效" }
            val bytes = ref?.let {
                require(it.getString("format") == "IAGREF1") { "参考指纹格式不支持" }
                require(it.optInt("sampleRate", 44100) == 44100 && it.optInt("window", 1024) == 1024 && it.optInt("hop", 512) == 512) { "参考指纹参数不匹配" }
                val decoded = Base64.getDecoder().decode(it.getString("data").filterNot(Char::isWhitespace))
                require(ReferenceDb.validBytes(decoded)) { "参考指纹不完整或已损坏" }
                decoded
            }
            Entry(raw, project, bytes)
        }
        return Prepared(version, entries)
    }

    suspend fun sync(context: Context, force: Boolean = false): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val job = currentCoroutineContext()
        mutex.withLock {
            var connection: HttpURLConnection? = null
            try {
                val conn = URL(BuildConfig.LIGHTAPP_BASE_URL + "library.json").openConnection() as HttpURLConnection
                connection = conn
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val status = conn.responseCode
                require(status == 200) { "HTTP $status" }
                val body = conn.inputStream.use { InputLimits.read(it, InputLimits.LIBRARY_BYTES).toString(Charsets.UTF_8) }
                job.ensureActive()
                val prepared = prepare(body)
                val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
                val version = prepared.version
                if (!force && version != null && version <= prefs.getInt("version", -1)) return@withLock "曲库已是最新 v$version"
                val storage = ArrangementStorage(app)
                for (entry in prepared.entries) {
                    job.ensureActive()
                    storage.upsertRaw(entry.raw)
                    entry.reference?.let { bytes ->
                        val id = refIdFor(entry.project.videoUrl, entry.project.name)
                        AtomicFiles.write(File(app.filesDir, "ref_$id.bin")) { it.write(bytes) }
                    }
                }
                job.ensureActive()
                if (version != null) check(prefs.edit().putInt("version", version).commit()) { "无法保存曲库版本" }
                if (version == null) "曲库已同步：${prepared.entries.first().project.name}"
                else "曲库已更新 v$version · ${prepared.entries.size} 份工程"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "曲库同步失败: ${e.message ?: "网络错误"}"
            } finally { connection?.disconnect() }
        }
    }

    fun refIdFor(videoUrl: String, name: String): String =
        Regex("BV[0-9A-Za-z]{8,12}").find(videoUrl)?.value
            ?: name.replace(Regex("[^0-9A-Za-z_-]"), "_").take(32).ifBlank { "default" }
}
