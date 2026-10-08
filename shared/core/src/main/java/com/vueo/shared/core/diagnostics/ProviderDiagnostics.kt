package com.vueo.shared.core.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Opt-in provider evidence. No frame probes, memory sampler or background writer. */
object ProviderDiagnostics {
    private const val PREFS = "vueo_provider_diagnostics"
    private const val MAX_EVENTS = 3_000
    private const val MAX_LINE = 1_200
    enum class Tab(val label: String) {
        FULL("Full"), SCANS("Scans"), PROVIDERS("Providers"),
        QUICKJS("QuickJS"), REQUESTS("Requests"), LOGS("Plugin Logs"),
    }
    data class SaveResult(val displayName: String, val location: String)
    private data class Event(val tab: Tab, val message: String, val line: String)
    private val installed = AtomicBoolean(false)
    private val enabled = AtomicBoolean(false)
    private val dropped = AtomicLong(0L)
    private val lock = Any()
    private val events = ArrayDeque<Event>()
    private val scans = HashSet<String>()
    private val providers = HashSet<String>()
    private val executions = HashSet<String>()
    private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
    private val urlPattern = Regex("(?i)https?://[^\\s<>\"']+")
    // Replace the complete value/header, including JSON and multi-cookie values.
    private val secretPattern = Regex("(?i)([\"']?(?:authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret)[\"']?\\s*[:=])\\s*(?:\"[^\"]*\"|'[^']*'|[^\\r\\n}]*)")

    fun install(context: Context) {
        if (installed.compareAndSet(false, true)) {
            enabled.set(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("enabled", false))
        }
    }
    fun isEnabled(context: Context): Boolean { install(context); return enabled.get() }
    fun isCollecting(): Boolean = enabled.get()
    fun setEnabled(context: Context, value: Boolean) {
        install(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).apply()
        synchronized(lock) {
            if (enabled.getAndSet(value) != value) {
                scans.clear(); providers.clear(); executions.clear()
                appendLocked(Tab.LOGS, "RECORDING_${if (value) "START" else "STOP"}")
            }
        }
    }
    fun clear() = synchronized(lock) { events.clear(); dropped.set(0L) }

    private fun field(message: String, key: String): String =
        message.substringAfter(" $key=", "").substringBefore(' ')

    fun captureRuntimeEvent(message: String) {
        if (!isCollecting()) return
        val tab = when {
            message.startsWith("SCAN_") -> Tab.SCANS
            message.startsWith("PROVIDER_") || message.startsWith("UI_STALL_RISK ") -> Tab.PROVIDERS
            message.startsWith("QJS_PHASE ") -> Tab.QUICKJS
            message.startsWith("DISCOVERY_TRACE ") -> {
                if (field(message, "scan") == "0" && !message.contains(" provider=")) return
                if (field(message, "stage").uppercase(Locale.US) in setOf("HTTP", "WEBVIEW")) Tab.REQUESTS else Tab.LOGS
            }
            else -> return
        }
        synchronized(lock) {
            if (!isCollecting()) return
            when {
                message.startsWith("SCAN_START ") -> scans.add(field(message, "id"))
                message.startsWith("SCAN_END ") -> scans.remove(field(message, "id"))
                message.startsWith("PROVIDER_START ") -> providers.add(providerKey(message, " activeProviders="))
                message.startsWith("PROVIDER_END ") -> providers.remove(providerKey(message, " status="))
                message.startsWith("QJS_PHASE ") -> {
                    val key = "${field(message, "scan")}:${field(message, "exec")}"
                    when (field(message, "phase")) {
                        "QJS_CREATE_BEGIN" -> executions.add(key)
                        "QJS_CLOSE", "QJS_ABORT", "QJS_TIMEOUT", "QJS_CANCELLED" -> executions.remove(key)
                    }
                }
            }
            appendLocked(tab, message)
        }
    }
    private fun providerKey(message: String, end: String): String {
        val runId = field(message, "run")
        val identity = runId.ifBlank { message.substringAfter(" provider=").substringBefore(end) }
        return "${field(message, "scan")}:$identity"
    }

    /** Caller supplies identity. Raw response bodies/headers are never captured automatically. */
    fun recordPluginLog(scanId: Long, providerId: String, providerName: String, pluginName: String, message: String) {
        if (!isCollecting()) return
        synchronized(lock) {
            if (!isCollecting()) return
            appendLocked(Tab.LOGS, "PLUGIN_LOG scan=$scanId providerId=$providerId provider=$providerName plugin=$pluginName $message")
        }
    }
    private fun sanitize(raw: String): String = raw
        .replace(secretPattern) { "${it.groupValues[1]}<redacted>" }
        .replace(urlPattern) { match ->
            val host = runCatching { java.net.URI(match.value).host }.getOrNull()
            "https://${host ?: "redacted"}/<redacted>"
        }.replace('\n', ' ').replace('\r', ' ').take(MAX_LINE)
    private fun appendLocked(tab: Tab, raw: String) {
        val message = sanitize(raw)
        val stamp = timestampFormat.get()!!.format(Date())
        if (events.size >= MAX_EVENTS) { events.removeFirst(); dropped.incrementAndGet() }
        events.addLast(Event(tab, message, "$stamp | [${tab.label.uppercase(Locale.US)}] $message"))
    }
    private fun snapshot(tab: Tab): List<Event> = synchronized(lock) {
        events.filter { tab == Tab.FULL || it.tab == tab }
    }
    private fun raw(tab: Tab, rows: List<Event>, total: Int): String = buildString {
        appendLine("PROVIDER RAW LOG — ${tab.label}")
        appendLine("${if (isCollecting()) "ON" else "OFF"} • Events: $total • Dropped: ${dropped.get()}")
        if (rows.size < total) appendLine("Showing latest ${rows.size} events.")
        if (rows.isEmpty()) appendLine("No provider events recorded yet.")
        rows.forEach { appendLine(it.line) }
    }
    fun exportRaw(tab: Tab): String { val rows = snapshot(tab); return raw(tab, rows, rows.size) }
    fun previewRaw(tab: Tab, maxEvents: Int = 220): String {
        val rows = snapshot(tab); return raw(tab, rows.takeLast(maxEvents.coerceIn(20, 500)), rows.size)
    }
    private fun summary(tab: Tab, rows: List<Event>): String = buildString {
        fun count(prefix: String) = rows.count { it.message.startsWith(prefix) }
        fun phase(value: String) = rows.count { it.message.startsWith("QJS_PHASE ") && field(it.message, "phase") == value }
        val finished = rows.filter { it.message.startsWith("PROVIDER_END ") }
        appendLine("PROVIDER SUMMARY — ${tab.label}")
        appendLine("Diagnostics: ${if (isCollecting()) "ON" else "OFF"}")
        appendLine("Events: ${rows.size} • Dropped: ${dropped.get()}")
        appendLine("Scans: ${count("SCAN_START ")} • Cancelled: ${count("SCAN_CANCELLED ")} • Errors: ${count("SCAN_ERROR ")}")
        appendLine("Providers started: ${count("PROVIDER_START ")}")
        appendLine("Cancelled: ${finished.count { field(it.message, "status") == "CANCELLED" }}")
        appendLine("Failed/timeout: ${finished.count { field(it.message, "status") in setOf("FAILED", "TIMEOUT") }}")
        appendLine("No results: ${finished.count { field(it.message, "status") == "NO_RESULTS" }}")
        appendLine("QuickJS executions: ${phase("QJS_CREATE_BEGIN")} • Phases: ${count("QJS_PHASE ")}")
        appendLine("QuickJS cancelled: ${phase("QJS_CANCELLED")} • Errors: ${phase("QJS_ABORT") + phase("QJS_TIMEOUT")}")
        synchronized(lock) { appendLine("Active scans/providers/QuickJS: ${scans.size}/${providers.size}/${executions.size}") }
        appendLine("Inspect Requests and Plugin Logs for request status, stages and parsing evidence.")
        appendLine("Plugin-specific extraction/selector details require the plugin to emit console or trace logs.")
        appendLine()
        appendLine("RECENT EVIDENCE")
        rows.takeLast(8).forEach { appendLine(it.line) }
    }
    fun exportSummary(tab: Tab): String = summary(tab, snapshot(tab))
    fun previewSummary(tab: Tab): String = exportSummary(tab)

    fun saveBundle(context: Context): SaveResult {
        install(context)
        val displayName = "VUEO_Provider_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.zip"
        val metadata = buildMetadata(context)

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VUEO")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Unable to create provider log in Downloads"
            }
            try {
                resolver.openOutputStream(uri, "w")!!.use { output -> writeZip(output, metadata) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return SaveResult(displayName, "Downloads/VUEO")
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val directory = File(root, "VUEO").apply { mkdirs() }
        val destination = File(directory, displayName)
        destination.outputStream().buffered().use { output -> writeZip(output, metadata) }
        return SaveResult(displayName, destination.parentFile?.absolutePath ?: directory.absolutePath)
    }

    private fun buildMetadata(context: Context): String = buildString {
        appendLine("VUEO Provider Diagnostic Metadata")
        appendLine("Package: ${context.packageName}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Enabled: ${isCollecting()}")
        appendLine("Event limit: $MAX_EVENTS; line limit: $MAX_LINE")
        appendLine("No system sampler, frame probes or automatic body/header capture.")
        appendLine("URLs and credential fields redacted before buffering.")
    }
    private fun writeZip(output: OutputStream, metadata: String) {
        val all = snapshot(Tab.FULL)
        ZipOutputStream(output.buffered()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("metadata.txt", metadata)
            entry("summary.txt", summary(Tab.FULL, all))
            Tab.entries.forEach { tab ->
                val rows = all.filter { tab == Tab.FULL || it.tab == tab }
                entry("${tab.name.lowercase(Locale.US)}.log", raw(tab, rows, rows.size))
                entry("summary/${tab.name.lowercase(Locale.US)}.txt", summary(tab, rows))
            }
        }
    }
}
