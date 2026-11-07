package dev.amenhancer.backdrop

import android.os.Build
import android.os.Trace


/** Scoped CPU markers for system traces; a null name leaves uninstrumented callers alone. */
inline fun <T> traceBackdrop(name: String?, block: () -> T): T {
    if (name == null) return block()
    val tracing = beginBackdropTrace(name)
    try {
        return block()
    } finally {
        if (tracing) endBackdropTrace()
    }
}


@PublishedApi
internal fun beginBackdropTrace(name: String): Boolean {
    if (Build.VERSION.SDK_INT < 29 || !Trace.isEnabled()) return false
    Trace.beginSection(name)
    return true
}

@PublishedApi
internal fun endBackdropTrace() {
    Trace.endSection()
}
