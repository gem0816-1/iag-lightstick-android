package com.iag.lightstick

import java.io.File
import java.util.UUID

/** Share a private copy, never the storage directory or a projection that loses Cue metadata. */
internal object ArrangementShareFiles {
    fun fileName(stored: StoredArrangement): String {
        val title = stored.arrangement.name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().take(64).let { if (it.isNotEmpty() && Character.isHighSurrogate(it.last())) it.dropLast(1) else it }
            .trimEnd('.', ' ').ifBlank { "IAG-编排" }
        return title + if (stored.resolve != null) ".iagcue.json" else ".json"
    }

    fun prepare(directory: File, stored: StoredArrangement, raw: String): File {
        val folder = File(directory, UUID.randomUUID().toString())
        val file = File(folder, fileName(stored))
        AtomicFiles.text(file, raw)
        return file
    }
}
