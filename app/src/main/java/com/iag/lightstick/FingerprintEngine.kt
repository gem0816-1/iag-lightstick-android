package com.iag.lightstick

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 音频指纹引擎（自研，重建版同步方案核心）。
 *
 * 原理（Shazam 式简化版）：
 * 1. 16bit 单声道 44100Hz 音频按 1024 样本加窗（Hann）+ FFT；
 * 2. 频域能量映射到对数频带，比较相邻频带能量差生成 32bit 哈希；
 * 3. 参考模式：完整播放一遍目标视频，逐帧哈希存入 ReferenceDb（帧号 → 时间）；
 * 4. 同步模式：对最近 2-3 秒的实时音频做同样哈希，在 ReferenceDb 中投票
 *    出偏移量 → 得到当前播放位置。
 *
 * 注意：本引擎只做"音频指纹对齐"，不依赖 B 站任何接口，合规可验证。
 */
object FingerprintEngine {

    const val SAMPLE_RATE = 44100
    const val WINDOW = 1024            // FFT 帧长（约 23.2ms）
    const val HOP = 512                // 帧移（约 11.6ms，每秒约 86 帧）
    const val MIN_FREQ_HZ = 250.0      // 指纹有效频带下限
    const val MAX_FREQ_HZ = 8000.0     // 上限（电话带宽内容不参与）
    const val BANDS = 24               // 对数频带数
    const val HASH_BITS = 32

    private val window = FloatArray(WINDOW).also { w ->
        for (i in 0 until WINDOW) {
            w[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (WINDOW - 1)))).toFloat()
        }
    }

    // 对数频带边界（Hz），共 BANDS+1 个边界
    private val bandEdges = DoubleArray(BANDS + 1).also { e ->
        val logMin = log10(MIN_FREQ_HZ)
        val logMax = log10(MAX_FREQ_HZ)
        for (i in 0..BANDS) {
            e[i] = 10.0.pow(logMin + (logMax - logMin) * i / BANDS)
        }
    }

    // FFT 相关缓存
    private var cosTable: FloatArray? = null
    private var sinTable: FloatArray? = null

    /** 迭代式 radix-2 FFT（原地，实部/虚部分开数组） */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        if (n != im.size) throw IllegalArgumentException("size mismatch")
        // 位反转
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = (-2.0 * PI / len).toFloat()
            val wRe = cos(ang.toDouble()).toFloat()
            val wIm = sin(ang.toDouble()).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val idx = i + k
                    val idx2 = idx + len / 2
                    val tRe = curRe * re[idx2] - curIm * im[idx2]
                    val tIm = curRe * im[idx2] + curIm * re[idx2]
                    re[idx2] = re[idx] - tRe
                    im[idx2] = im[idx] - tIm
                    re[idx] = re[idx] + tRe
                    im[idx] = im[idx] + tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** 单帧 PCM（WINDOW 长度）→ 32bit 指纹哈希 */
    fun fingerprintFrame(pcm: FloatArray): Int {
        require(pcm.size == WINDOW) { "frame size must be $WINDOW" }
        val re = FloatArray(WINDOW)
        val im = FloatArray(WINDOW)
        for (i in 0 until WINDOW) re[i] = pcm[i] * window[i]
        fft(re, im)

        // 频带能量
        val bandEnergy = DoubleArray(BANDS)
        val binHz = SAMPLE_RATE.toDouble() / WINDOW
        for (i in 1 until WINDOW / 2) {
            val f = i * binHz
            // 找到所属频带
            var b = BANDS - 1
            for (k in 0 until BANDS) {
                if (f <= bandEdges[k + 1]) { b = k; break }
            }
            val mag = sqrt((re[i].toDouble() * re[i] + im[i].toDouble() * im[i]))
            bandEnergy[b] += mag
        }

        // 相邻频带能量差 → 哈希位
        var hash = 0
        for (b in 1 until BANDS) {
            val bit = if (bandEnergy[b] > bandEnergy[b - 1]) 1 else 0
            hash = (hash shl 1) or bit
        }
        // 只用低 HASH_BITS 位（BANDS-1 通常 23 位；不足部分用能量总和补）
        val total = bandEnergy.sum()
        val extraBit = if (total > 0) 1 else 0
        hash = (hash shl 1) or extraBit
        // Int 已是 32 位；JVM 的 Int 移位只取低 5 位，1 shl 32 等于 1，
        // 再减 1 会得到零掩码并清空所有指纹。当前编码仅占 24 位，直接保留。
        return hash
    }

    /** 流式指纹：pcm 16bit 样本 → 逐帧回调 (frameIndex, hash) */
    fun fingerprintStream(pcm: ShortArray, onFingerprint: (frameIndex: Int, hash: Int) -> Unit) {
        val frame = FloatArray(WINDOW)
        var fi = 0
        var off = 0
        while (off + WINDOW <= pcm.size) {
            for (i in 0 until WINDOW) frame[i] = pcm[off + i] / 32768f
            onFingerprint(fi, fingerprintFrame(frame))
            fi++
            off += HOP
        }
    }
}

/**
 * 参考指纹库：视频音频的 帧号 → 哈希 映射。
 * 帧号 × HOP / SAMPLE_RATE = 视频内时间。
 */
class ReferenceDb {
    private val hashToFrames = HashMap<Int, MutableList<Int>>()
    private val frameHashes = ArrayList<Int>()
    var totalFrames = 0
        private set

    fun add(hash: Int, frameIndex: Int) {
        if (hash != 0) hashToFrames.getOrPut(hash) { mutableListOf() }.add(frameIndex)
        while (frameHashes.size <= frameIndex) frameHashes.add(0)
        frameHashes[frameIndex] = hash
        if (frameIndex >= totalFrames) totalFrames = frameIndex + 1
    }

    fun frameToMs(frameIndex: Int): Long =
        (frameIndex.toLong() * FingerprintEngine.HOP * 1000 / FingerprintEngine.SAMPLE_RATE)

    /** 序列化保存（magic + totalFrames + 逐帧 hash） */
    fun save(file: java.io.File) {
        require(totalFrames > 0) { "参考指纹为空" }
        AtomicFiles.write(file) { stream ->
            val out = java.io.DataOutputStream(java.io.BufferedOutputStream(stream))
            out.writeBytes("IAGREF1")
            out.writeInt(totalFrames)
            for (i in 0 until totalFrames) out.writeInt(frameHashes[i])
            out.flush()
        }
    }

    companion object {
        fun validBytes(bytes: ByteArray): Boolean {
            if (bytes.size < 15 || !bytes.copyOfRange(0, 7).contentEquals("IAGREF1".toByteArray(Charsets.US_ASCII))) return false
            val frames = java.nio.ByteBuffer.wrap(bytes, 7, 4).int
            return frames > 0 && 11L + frames.toLong() * 4 == bytes.size.toLong()
        }

        fun load(file: java.io.File): ReferenceDb? {
            return try {
                java.io.DataInputStream(
                    java.io.BufferedInputStream(java.io.FileInputStream(file))
                ).use { inp ->
                    val magic = ByteArray(7)
                    inp.readFully(magic)
                    if (String(magic) != "IAGREF1") return null
                    val n = inp.readInt()
                    if (n <= 0 || 11L + n.toLong() * 4 != file.length()) return null
                    val db = ReferenceDb()
                    for (i in 0 until n) db.add(inp.readInt(), i)
                    db
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 用最近一段实时指纹投票出参考偏移。
     * 返回 (位置ms, 票数)；票数低于阈值视为未匹配。
     */
    fun match(recent: List<Int>, minVotes: Int = 3): Pair<Long, Int>? {
        val votes = HashMap<Int, Int>()  // offset(参考帧-实时帧序号) → 票数
        var best = 0
        var bestOffset = 0
        for ((idx, h) in recent.withIndex()) {
            val frames = hashToFrames[h] ?: continue
            for (ref in frames) {
                val off = ref - idx
                val v = (votes[off] ?: 0) + 1
                votes[off] = v
                if (v > best) {
                    best = v
                    bestOffset = off
                }
            }
        }
        return if (best >= minVotes) frameToMs(bestOffset) to best else null
    }
}
