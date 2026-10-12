package com.vueo.shared.core.diagnostics

import java.io.File

/** Bounded reader of Android debuggerd's tombstone.proto; ignores memory dumps and log buffers. */
internal object NativeCrashEvidence {
    data class Evidence(val signal: String, val abort: String, val tid: Long, val thread: String,
        val frames: List<String>, val initializationThreads: Int) {
        val headline: String get() = if (frames.any { "libquickjs.so" in it })
            "QuickJS native crash • ${signal.ifBlank { "signal unavailable" }}" else "Native crash • ${signal.ifBlank { "signal unavailable" }}"
        fun text(): String = buildString {
            appendLine("Native signal: ${signal.ifBlank { "Unavailable" }}")
            if (abort.isNotBlank()) appendLine("Native abort: $abort")
            appendLine("Crashing thread: ${thread.ifBlank { "Unnamed" }} • tid=$tid")
            appendLine("Crashing thread frames:")
            frames.take(18).forEach { appendLine("  $it") }
            if (initializationThreads > 1) appendLine("Observed QuickJS initGlobals threads: $initializationThreads; concurrent initialization is a suspected cause, not a proven diagnosis.")
            appendLine("The last provider breadcrumb is context, not identification of the crashing thread or proof of provider fault.")
        }
    }
    private data class Field(val number: Int, val value: Long = 0, val start: Int = 0, val end: Int = 0, val wire: Int)
    private data class NativeThread(val id: Long, val name: String, val frames: List<String>)
    private var cachedPath = ""
    private var cachedModified = 0L
    private var cachedLength = 0L
    private var cached: Evidence? = null

    @Synchronized fun read(file: File?): Evidence? {
        if (file == null || !file.exists() || file.length() !in 1..4_194_304L) return null
        if (cachedPath == file.absolutePath && cachedModified == file.lastModified() && cachedLength == file.length()) return cached
        cachedPath = file.absolutePath; cachedModified = file.lastModified(); cachedLength = file.length()
        cached = runCatching { parse(file.readBytes()) }.getOrNull()
        return cached
    }
    fun parse(bytes: ByteArray): Evidence? {
        require(bytes.size <= 4_194_304)
        var tid = 0L; var signal = ""; var abort = ""
        val threads = mutableListOf<NativeThread>()
        fields(bytes, 0, bytes.size) { f ->
            when {
                f.number == 6 && f.wire == 0 -> tid = f.value
                f.number == 10 && f.wire == 2 -> fields(bytes, f.start, f.end) { sf ->
                    if (sf.number == 2 && sf.wire == 2) signal = text(bytes, sf)
                }
                f.number == 14 && f.wire == 2 -> abort = text(bytes, f)
                f.number == 16 && f.wire == 2 && threads.size < 512 -> {
                    fields(bytes, f.start, f.end) { entry ->
                        if (entry.number == 2 && entry.wire == 2) threads += thread(bytes, entry)
                    }
                }
            }
        }
        if (tid == 0L && signal.isBlank() && abort.isBlank()) return null
        val crashed = threads.firstOrNull { it.id == tid }
        return Evidence(signal, abort, tid, crashed?.name.orEmpty(), crashed?.frames.orEmpty(),
            threads.count { t -> t.frames.any { "QuickJs_initGlobals" in it } })
    }
    private fun thread(bytes: ByteArray, root: Field): NativeThread {
        var id = 0L; var name = ""; val frames = mutableListOf<String>()
        fields(bytes, root.start, root.end) { f ->
            when {
                f.number == 1 && f.wire == 0 -> id = f.value
                f.number == 2 && f.wire == 2 -> name = text(bytes, f)
                f.number == 4 && f.wire == 2 && frames.size < 96 -> {
                    var function = ""; var library = ""
                    fields(bytes, f.start, f.end) { frame ->
                        if (frame.wire == 2) when (frame.number) {
                            4 -> function = text(bytes, frame)
                            6 -> library = text(bytes, frame).substringAfterLast('/')
                        }
                    }
                    if (function.isNotBlank() || library.isNotBlank()) frames += "$library • $function"
                }
            }
        }
        return NativeThread(id, name, frames)
    }
    private fun text(bytes: ByteArray, f: Field): String = String(bytes, f.start, (f.end - f.start).coerceAtMost(600), Charsets.UTF_8)
        .replace('\n', ' ').replace('\r', ' ')
    private fun fields(bytes: ByteArray, start: Int, end: Int, visit: (Field) -> Unit) {
        var position = start; var count = 0
        fun varint(): Long {
            var result = 0L
            for (shift in 0..63 step 7) {
                require(position < end)
                val b = bytes[position++].toInt() and 255
                if (shift == 63) require(b <= 1)
                result = result or ((b and 127).toLong() shl shift)
                if (b < 128) return result
            }
            error("Invalid protobuf varint")
        }
        while (position < end) {
            require(++count <= 16_384)
            val tag = varint(); val number = (tag ushr 3).toInt(); val wire = (tag and 7).toInt()
            require(number > 0)
            when (wire) {
                0 -> visit(Field(number, value = varint(), wire = wire))
                1 -> { require(end - position >= 8); position += 8 }
                2 -> {
                    val length = varint(); require(length >= 0 && length <= (end - position).toLong())
                    val stop = position + length.toInt()
                    visit(Field(number, start = position, end = stop, wire = wire)); position = stop
                }
                5 -> { require(end - position >= 4); position += 4 }
                else -> error("Unsupported protobuf wire type")
            }
        }
    }
}
