package com.iag.lightstick

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Commit only a fully written sibling file. Failure leaves the previous document intact. */
internal object AtomicFiles {
    fun write(target: File, encode: (OutputStream) -> Unit) {
        val parent = target.absoluteFile.parentFile ?: error("文件目录无效")
        check(parent.isDirectory || parent.mkdirs()) { "无法创建文件目录" }
        val temp = File.createTempFile(".${target.name}.", ".tmp", parent)
        try {
            FileOutputStream(temp).use { out -> encode(out); out.flush(); out.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    fun text(target: File, text: String) = write(target) { it.write(text.toByteArray(Charsets.UTF_8)) }
}

internal object InputLimits {
    const val PROJECT_BYTES = 4 * 1024 * 1024
    const val LIBRARY_BYTES = 8 * 1024 * 1024

    fun read(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(count > 0) { "文件读取没有进展" }
            require(out.size().toLong() + count <= limit) { "文件过大（最多 ${limit / 1024 / 1024} MB）" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
