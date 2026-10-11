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
    private data class Event(val tab: Tab, val message: String, val line: String, var repeats: Int = 1, var lastStamp: String = "")
    private val installed = AtomicBoolean(false)
    private val enabled = AtomicBoolean(false)
    private val dropped = AtomicLong(0L)
    private val lock = Any()
    private val events = ArrayDeque<Event>()
    private val repeatedStages = linkedMapOf<String, Event>()
    private val coalesced = AtomicLong(0L)
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
    fun clear() = synchronized(lock) { events.clear(); repeatedStages.clear(); coalesced.set(0L); dropped.set(0L) }

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
    private val htmlStagePattern = Regex(" STAGE elapsed=\\d+ms (HTML (?:parse|select|attr|text))$")
    private fun appendLocked(tab: Tab, raw: String) {
        val message = sanitize(raw)
        val stamp = timestampFormat.get()!!.format(Date())
        val htmlStage = htmlStagePattern.find(message)?.groupValues?.get(1)
        val repeatKey = htmlStage?.let { "${field(message, "scan")}:${field(message, "providerId")}:$it" }
        if (repeatKey != null) {
            repeatedStages[repeatKey]?.let { existing ->
                existing.repeats++
                existing.lastStamp = stamp
                coalesced.incrementAndGet()
                return
            }
        }
        if (events.size >= MAX_EVENTS) {
            val removed = events.removeFirst()
            repeatedStages.entries.removeAll { it.value === removed }
            dropped.incrementAndGet()
        }
        val event = Event(tab, message, "$stamp | [${tab.label.uppercase(Locale.US)}] $message")
        events.addLast(event)
        if (repeatKey != null) {
            if (repeatedStages.size >= 128) repeatedStages.remove(repeatedStages.keys.first())
            repeatedStages[repeatKey] = event
        }
    }
    private fun snapshot(tab: Tab): List<Event> = synchronized(lock) {
        events.filter { tab == Tab.FULL || it.tab == tab }.map { it.copy() }
    }
    private fun retentionWarning(): String? {
        val removed = dropped.get()
        return if (removed > 0L) {
            "INCOMPLETE RECORD: $removed oldest events were discarded across all tabs because the $MAX_EVENTS-event buffer filled. Counts cover retained events only."
        } else null
    }
    private fun raw(tab: Tab, rows: List<Event>, total: Int): String = buildString {
        appendLine("PROVIDER RAW LOG — ${tab.label}")
        appendLine("${if (isCollecting()) "ON" else "OFF"} • Events: $total • Dropped: ${dropped.get()}")
        retentionWarning()?.let { appendLine(it) }
        if (rows.size < total) appendLine("Preview: latest ${rows.size} of $total retained events. Export includes all retained events.")
        if (rows.isEmpty()) appendLine("No provider events recorded yet.")
        var previousScan = ""
        rows.forEach { event ->
            val scan = scanId(event)
            if (scan.isNotBlank() && scan != previousScan) {
                appendLine()
                appendLine("SCAN $scan")
                previousScan = scan
            }
            appendLine(event.line + if (event.repeats > 1) " [occurrences=${event.repeats} last=${event.lastStamp}]" else "")
            appendLine()
        }
    }
    fun exportRaw(tab: Tab): String { val rows = snapshot(tab); return raw(tab, rows, rows.size) }
    fun previewRaw(tab: Tab, maxEvents: Int = 220): String {
        val rows = snapshot(tab); return raw(tab, rows.takeLast(maxEvents.coerceIn(20, 500)), rows.size)
    }
    private val labelBoundary = Regex(" [A-Za-z][A-Za-z0-9]*=")
    private fun label(message: String, key: String): String {
        val value = message.substringAfter(" $key=", "")
        val boundary = labelBoundary.find(value)?.range?.first ?: value.length
        return value.take(boundary).trim()
    }
    private fun scanId(event: Event): String = if (event.message.startsWith("SCAN_"))
        field(event.message, "id") else field(event.message, "scan")
    private fun runKey(event: Event): String {
        val id = field(event.message, "providerId").ifBlank { label(event.message, "provider") }
        return if (id.isBlank()) "" else "${scanId(event)}:$id"
    }
    private fun duration(value: String): String {
        val ms = value.removeSuffix("ms").toLongOrNull() ?: return "—"
        return if (ms < 1000L) "${ms}ms" else String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
    private fun statusLabel(status: String): String = when (status) {
        "ONLINE" -> "OK"
        "SLOW" -> "OK (slow)"
        "NO_RESULTS" -> "EMPTY"
        "FAILED" -> "ERROR"
        "TIMEOUT" -> "TIMEOUT"
        "CANCELLED" -> "CANCELLED"
        else -> status.ifBlank { "RUNNING" }
    }
    private fun failureEvidence(run: List<Event>): String? {
        val evidence = linkedSetOf<String>()
        run.lastOrNull { field(it.message, "phase") in setOf("QJS_ABORT", "QJS_TIMEOUT", "QJS_CANCELLED") }?.let {
            evidence += "${field(it.message, "phase")} ${label(it.message, "type")}".trim()
        }
        run.filter { it.tab == Tab.REQUESTS && (field(it.message, "status") in setOf("0", "403", "404", "429") || " error=" in it.message) }
            .takeLast(3).forEach {
                evidence += label(it.message, "request").take(110) + " • status=${field(it.message, "status")} • ${field(it.message, "elapsed")} " + label(it.message, "error").take(120)
            }
        run.lastOrNull { "Unsupported host" in it.message }?.let { evidence += it.message.substringAfter("LOG: ").take(200) }
        run.lastOrNull { "PARSE: " in it.message && ("rejected:" in it.message || "Invalid result" in it.message) }?.let {
            evidence += it.message.substringAfter("PARSE: ").take(200)
        }
        run.lastOrNull { "ERROR: " in it.message }?.let { evidence += it.message.substringAfter("ERROR: ").take(220) }
        return evidence.takeIf { it.isNotEmpty() }?.joinToString(" | ")
    }
    private fun meaningfulStage(run: List<Event>): String? {
        val stage = run.lastOrNull {
            (" STAGE elapsed=" in it.message &&
                !it.message.substringAfter("ms ").let { text -> text.startsWith("HTML ") || text.startsWith("Provider finished") || text.startsWith("Parsing provider results") || text.endsWith("completed") }) ||
                (it.message.startsWith("DISCOVERY_TRACE ") && field(it.message, "stage") !in setOf("HTTP", "WEBVIEW"))
        } ?: return null
        return if (" STAGE elapsed=" in stage.message) stage.message.substringAfter("ms ").take(180)
            else field(stage.message, "stage") + " • " + stage.message.substringAfter(" plugin=").substringAfter(' ').take(180)
    }
    private fun summary(tab: Tab, all: List<Event>, maxRuns: Int = Int.MAX_VALUE): String = buildString {
        val selected = all.filter { tab == Tab.FULL || it.tab == tab }
        val selectedScans = selected.map(::scanId).filter { it.isNotBlank() }.toSet()
        val selectedKeys = selected.map(::runKey).filter { it.isNotBlank() }.toSet()
        val grouped = all.filter { runKey(it).isNotBlank() &&
            (tab == Tab.FULL || (tab == Tab.SCANS && scanId(it) in selectedScans) || runKey(it) in selectedKeys) }
            .groupBy(::runKey)
        val finished = grouped.values.mapNotNull { run -> run.lastOrNull { it.message.startsWith("PROVIDER_END ") } }
        appendLine("PROVIDER SUMMARY — ${tab.label}")
        appendLine("${if (isCollecting()) "ON • recording" else "OFF • inactive"}")
        appendLine("Retained events in tab: ${selected.size} • Dropped across all tabs: ${dropped.get()}")
        retentionWarning()?.let { appendLine(it) }
        appendLine("Provider runs: ${grouped.size} • OK: ${finished.count { field(it.message, "status") in setOf("ONLINE", "SLOW") }}")
        appendLine("Empty: ${finished.count { field(it.message, "status") == "NO_RESULTS" }} • Error/timeout: ${finished.count { field(it.message, "status") in setOf("FAILED", "TIMEOUT", "THREW") }} • Unavailable: ${finished.count { field(it.message, "status") == "UNAVAILABLE" }} • Cancelled: ${finished.count { field(it.message, "status") == "CANCELLED" }}")
        synchronized(lock) { appendLine("Active scans/providers/QuickJS: ${scans.size}/${providers.size}/${executions.size}") }
        if (grouped.isEmpty()) {
            appendLine()
            appendLine("No provider runs recorded in this tab yet. Use Raw for individual events.")
            return@buildString
        }
        appendLine("Evidence records observed failures; handled request/console errors do not alone prove the final cause.")
        val visible = grouped.entries.toList().takeLast(maxRuns)
        if (visible.size < grouped.size) appendLine("Preview: latest ${visible.size} provider runs. Copy/export includes all retained runs.")
        var previousScan = ""
        visible.forEach { (_, run) ->
            val start = run.firstOrNull { it.message.startsWith("PROVIDER_START ") }
            val finish = run.lastOrNull { it.message.startsWith("PROVIDER_END ") }
            val identity = finish ?: start ?: run.first()
            val scan = scanId(identity)
            if (scan != previousScan) {
                appendLine()
                val end = all.lastOrNull { it.message.startsWith("SCAN_END ") && field(it.message, "id") == scan }
                appendLine("SCAN $scan • ${end?.let { field(it.message, "outcome").uppercase(Locale.US) } ?: "IN PROGRESS / END NOT RETAINED"}")
                if (end != null) {
                    appendLine("Duration: ${duration(field(end.message, "elapsed"))} • Candidates: ${field(end.message, "candidateStreams")} • Final streams: ${field(end.message, "streams")}")
                    all.lastOrNull { field(it.message, "scan") == scan && field(it.message, "stage") == "STREAM_SELECTION" }?.let {
                        appendLine("Selection: " + it.message.substringAfter(" plugin=").substringAfter(' '))
                    }
                }
                previousScan = scan
            }
            appendLine()
            val name = label(identity.message, "provider").ifBlank { field(identity.message, "providerId") }
            val plugin = (finish ?: start)?.let { label(it.message, "plugin") }.orEmpty()
            appendLine(if (plugin.isBlank()) "$name • Scan $scan" else "$name • $plugin • Scan $scan")
            val status = finish?.let { field(it.message, "status") }.orEmpty()
            val pendingStatus = if (status.isBlank() && start == null) "UNKNOWN (lifecycle not retained)" else statusLabel(status)
            appendLine("Status: $pendingStatus • ${finish?.let { duration(field(it.message, "elapsed")) } ?: "—"} • Streams: ${finish?.let { field(it.message, "streams") } ?: "—"}")
            run.firstOrNull { " IDENTITY " in it.message }?.let {
                appendLine("Version: provider ${field(it.message, "providerVersion")} • plugin ${field(it.message, "pluginVersion")}")
            }
            run.firstOrNull { " CODE_IDENTITY " in it.message }?.let { appendLine("Code SHA-256: ${field(it.message, "sha256")}") }
            meaningfulStage(run)?.let { appendLine("Last meaningful stage: $it") }
            val requests = run.filter { it.tab == Tab.REQUESTS }
            if (requests.isNotEmpty()) appendLine("Requests: ${requests.size} retained • failures: ${requests.count { field(it.message, "status") == "0" || (field(it.message, "status").toIntOrNull() ?: 0) >= 400 || " error=" in it.message }}")
            run.lastOrNull { "PARSE: Result selection " in it.message }?.let { appendLine(it.message.substringAfter("PARSE: ")) }
            run.lastOrNull { "PARSE: Result entries=" in it.message }?.let { appendLine(it.message.substringAfter("PARSE: ")) }
            if (status in setOf("FAILED", "TIMEOUT", "NO_RESULTS", "CANCELLED", "UNAVAILABLE", "THREW")) {
                val reason = failureEvidence(run) ?: when (status) {
                    "NO_RESULTS" -> "No links returned; plugin did not report a specific cause."
                    "TIMEOUT" -> "Provider exceeded its runtime timeout."
                    "CANCELLED" -> "Provider run cancelled."
                    else -> "No detailed error retained. Inspect Raw."
                }
                appendLine("Evidence: $reason")
            }
        }
    }
    fun exportSummary(tab: Tab): String = summary(tab, snapshot(Tab.FULL))
    fun previewSummary(tab: Tab): String = summary(tab, snapshot(Tab.FULL), maxRuns = 40)

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
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        appendLine("App version: ${info?.versionName ?: "unknown"}; versionCode: ${if (Build.VERSION.SDK_INT >= 28) info?.longVersionCode else info?.versionCode}")
        appendLine("Build fingerprint: ${Build.FINGERPRINT}")
        appendLine("Repeated HTML stages coalesced: ${coalesced.get()}; errors and results are retained individually.")
        appendLine("heapDelta and Java/native/PSS/RSS snapshots describe the whole app process, not per-provider allocations.")
        appendLine("Oldest events discarded across all tabs: ${dropped.get()}")
        retentionWarning()?.let { appendLine(it) }
        appendLine("No system sampler, frame probes or automatic body/header capture.")
        appendLine("URLs and credential fields redacted before buffering. Request IDs and path fingerprints distinguish redacted endpoints.")
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
                entry("summary/${tab.name.lowercase(Locale.US)}.txt", summary(tab, all))
            }
        }
    }
}
