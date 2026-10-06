package com.vueo.shared.core.diagnostics

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RuntimeDiagnostics {
    private const val FILE_NAME = "vueo_runtime_diagnostics.log"
    private const val ROTATE_AT_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 256 * 1024
    private const val BATCH_DELAY_MS = 120L
    private const val MEMORY_SNAPSHOT_CACHE_MS = 500L
    // Provider main-thread risk threshold; independent of the live ANR watchdog.
    private const val STALL_THRESHOLD_MS = 350L

    private val secretPattern =
        Regex("(?i)(authorization|cookie|token|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+")
    private val playbackUrlPattern = Regex("https?://[^\\s<>\"']+")
    private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val installed = AtomicBoolean(false)
    private val scanSequence = AtomicLong(0L)
    private val quickJsSequence = AtomicLong(0L)
    private val activeScans = AtomicInteger(0)
    private val activeProviders = AtomicInteger(0)
    private val writer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "vueo-runtime-diagnostics").apply { isDaemon = true }
    }
    private val fileLock = Any()
    private val pendingLines = ConcurrentLinkedQueue<String>()
    private val flushScheduled = AtomicBoolean(false)
    private val latestSummaryLine = AtomicReference("")
    private val forceSystemSummary = AtomicBoolean(false)
    private val scanStates = ConcurrentHashMap<Long, ScanState>()
    private val providerPhases = ConcurrentHashMap<String, ProviderPhase>()
    private val resumedActivities = AtomicInteger(0)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val memoryLock = Any()
    private val memorySnapshotAtMs = AtomicLong(0L)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedMemorySnapshot: MemorySnapshot? = null

    data class ProviderToken internal constructor(
        val scanId: Long,
        val providerName: String,
        val startedNs: Long,
        val heapStartBytes: Long,
        val startedOnMainThread: Boolean,
    )

    data class QuickJsToken internal constructor(
        val scanId: Long,
        val providerName: String,
        val executionId: Long,
        val startedNs: Long,
    )

    data class DiagnosticSaveResult(
        val displayName: String,
        val location: String,
    )

    private data class ScanState(
        val startedNs: Long,
        val startedOnMainThread: Boolean,
        val activeProviders: AtomicInteger = AtomicInteger(0),
        val maxConcurrentProviders: AtomicInteger = AtomicInteger(0),
    )

    private data class ProviderPhase(
        val scanId: Long,
        val providerName: String,
        val phase: String,
        val executionId: Long?,
        val updatedMs: Long,
    )

    private data class MemorySnapshot(
        val javaHeapMb: Long,
        val javaHeapMaxMb: Long,
        val nativeHeapMb: Long,
        val pssMb: Long,
        val rssMb: Long,
    ) {
        fun label(): String =
            "java=${javaHeapMb}/${javaHeapMaxMb}MB native=${nativeHeapMb}MB pss=${pssMb}MB rss=${rssMb}MB"

        fun compact(): String = "j${javaHeapMb} n${nativeHeapMb} p${pssMb} r${rssMb}"
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return

        runCatching { CrashReportStore.startSession(context.applicationContext) }
        installCrashHandler()
        startStallWatchdog(context.applicationContext)
        record(
            "SESSION_START android=${Build.VERSION.SDK_INT} " +
                "device=${safeToken(Build.MANUFACTURER)}_${safeToken(Build.MODEL)} " +
                memoryLabel()
        )
    }

    fun recordScreen(label: String) {
        CrashReportStore.screen(label)
        forceSystemSummary.set(true)
        record("SCREEN ${safeText(label, 120)}")
    }

    fun beginSourceScan(requestLabel: String, targetProviders: Int): Long {
        val id = scanSequence.incrementAndGet()
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        scanStates[id] = ScanState(
            startedNs = System.nanoTime(),
            startedOnMainThread = onMain,
        )
        val scans = activeScans.incrementAndGet()
        record(
            "SCAN_START id=$id request=${safeText(requestLabel, 120)} " +
                "targets=$targetProviders activeScans=$scans thread=${threadLabel()} " +
                "mainThread=$onMain ${memoryLabel()}"
        )
        return id
    }

    fun finishSourceScan(
        scanId: Long,
        streams: Int,
        completedProviders: Int,
        outcome: String = "complete",
    ) {
        val state = scanStates.remove(scanId)
        val scans = activeScans.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (state == null) return

        providerPhases.entries
            .filter { it.value.scanId == scanId }
            .forEach { providerPhases.remove(it.key) }
        val elapsedMs = elapsedMs(state.startedNs)
        record(
            "SCAN_END id=$scanId outcome=${safeToken(outcome)} elapsed=${elapsedMs}ms " +
                "streams=$streams providers=$completedProviders " +
                "maxConcurrentProviders=${state.maxConcurrentProviders.get()} " +
                "activeScans=$scans thread=${threadLabel()} ${memoryLabel()}"
        )
    }

    fun recordPlayerEvent(platform: String, event: String, details: String) {
        record("PLAYER_EVENT platform=${safeToken(platform)} event=${safeToken(event)} ${safeText(details, 360)}")
    }

    fun recordDiscoveryTrace(
        scanId: Long,
        stage: String,
        details: String,
        providerName: String? = null,
    ) {
        val providerPart = providerName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                updateProviderPhase(scanId, it, stage, null)
                " provider=${safeText(it, 80)}"
            }
            .orEmpty()

        record(
            "DISCOVERY_TRACE scan=$scanId stage=${safeToken(stage)}$providerPart " +
                safeText(details, 360)
        )
    }

    fun recordPlaybackError(
        platform: String,
        provider: String,
        server: String,
        url: String?,
        mimeType: String?,
        errorCode: Int,
        errorName: String,
        positionMs: Long,
        state: Int,
        error: Throwable,
    ) {
        // Omit paths too: signed credentials may live in path segments, not just query parameters.
        fun redact(value: String): String = CrashRecoveryPolicy.sanitize(value)
            .replace(playbackUrlPattern) { match ->
                val host = runCatching { java.net.URI(match.value).host }.getOrNull()
                "https://${host ?: "redacted"}/<redacted>"
            }
        val endpoint = runCatching { java.net.URI(url.orEmpty()).host }.getOrNull().orEmpty()
        record(
            "PLAYBACK_ERROR platform=${safeToken(platform)} provider=${safeText(redact(provider), 120)} " +
                "server=${safeText(redact(server), 120)} host=${safeText(endpoint, 120)} " +
                "mime=${safeText(mimeType.orEmpty(), 80)} code=$errorCode name=${safeToken(errorName)} " +
                "positionMs=$positionMs state=$state"
        )
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 8 && seen.add(cause)) {
            val current = cause
            record(
                "PLAYBACK_CAUSE depth=$depth class=${current.javaClass.name} " +
                    "message=${safeText(redact(current.message.orEmpty()), 700)} " +
                    "location=${safeText(current.stackTrace.firstOrNull()?.toString().orEmpty(), 180)}"
            )
            cause = current.cause
            depth++
        }
    }

    fun failSourceScan(scanId: Long, error: Throwable, completedProviders: Int) {
        record(
            "SCAN_ERROR id=$scanId type=${safeToken(error::class.java.simpleName)} " +
                "message=${safeText(error.message.orEmpty(), 180)} providers=$completedProviders"
        )
        finishSourceScan(
            scanId = scanId,
            streams = 0,
            completedProviders = completedProviders,
            outcome = "failed",
        )
    }

    fun beginProvider(scanId: Long, providerName: String): ProviderToken {
        val globalActive = activeProviders.incrementAndGet()
        val state = scanStates[scanId]
        val scanActive = state?.activeProviders?.incrementAndGet() ?: 0
        state?.maxConcurrentProviders?.updateMax(scanActive)
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        val token = ProviderToken(
            scanId = scanId,
            providerName = providerName,
            startedNs = System.nanoTime(),
            heapStartBytes = heapUsedBytes(),
            startedOnMainThread = onMain,
        )
        updateProviderPhase(scanId, providerName, "PROVIDER_START", null)
        record(
            "PROVIDER_START scan=$scanId provider=${safeText(providerName, 80)} " +
                "activeProviders=$scanActive globalActiveProviders=$globalActive " +
                "thread=${threadLabel()} mainThread=$onMain"
        )
        return token
    }

    fun finishProvider(
        token: ProviderToken,
        status: String,
        streamCount: Int,
        errorType: String? = null,
    ) {
        val elapsedMs = elapsedMs(token.startedNs)
        val heapDeltaMb = (heapUsedBytes() - token.heapStartBytes) / (1024.0 * 1024.0)
        val globalActive = activeProviders.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        val scanActive = scanStates[token.scanId]
            ?.activeProviders
            ?.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            ?: 0
        providerPhases.remove(providerKey(token.scanId, token.providerName))
        val errorSuffix = errorType
            ?.takeIf { it.isNotBlank() }
            ?.let { " errorType=${safeToken(it)}" }
            .orEmpty()
        record(
            "PROVIDER_END scan=${token.scanId} provider=${safeText(token.providerName, 80)} " +
                "status=${safeToken(status)} elapsed=${elapsedMs}ms streams=$streamCount " +
                "heapDelta=${String.format(Locale.US, "%.1f", heapDeltaMb)}MB " +
                "startedOnMainThread=${token.startedOnMainThread} activeProviders=$scanActive " +
                "globalActiveProviders=$globalActive$errorSuffix"
        )
        if (token.startedOnMainThread && elapsedMs >= STALL_THRESHOLD_MS) {
            record(
                "UI_STALL_RISK scan=${token.scanId} provider=${safeText(token.providerName, 80)} " +
                    "providerRanOnMainThread=true elapsed=${elapsedMs}ms"
            )
        }
    }

    /**
     * Marks entry into the native QuickJS boundary. This breadcrumb is synchronously appended because
     * a native abort can terminate the process before the normal batched writer gets another chance.
     */
    fun beginQuickJsExecution(scanId: Long, providerName: String): QuickJsToken {
        val token = QuickJsToken(
            scanId = scanId,
            providerName = providerName,
            executionId = quickJsSequence.incrementAndGet(),
            startedNs = System.nanoTime(),
        )
        recordQuickJsPhase(token, "QJS_CREATE_BEGIN", critical = true)
        return token
    }

    fun recordQuickJsPhase(
        token: QuickJsToken,
        phase: String,
        details: String = "",
        critical: Boolean = phase == "QJS_CREATE_BEGIN" ||
            phase == "QJS_EVAL_BEGIN" || phase == "QJS_EVAL_END" ||
            phase == "QJS_ABORT",
    ) {
        updateProviderPhase(token.scanId, token.providerName, phase, token.executionId)
        // Memory probes (especially PSS) are cached so diagnostics do not become provider work.
        val memory = detailedMemorySnapshot(force = phase == "QJS_ABORT")
        val message = buildString {
            append("QJS_PHASE scan=${token.scanId} exec=${token.executionId} ")
            append("provider=${safeText(token.providerName, 80)} phase=${safeToken(phase)} ")
            append("elapsed=${elapsedMs(token.startedNs)}ms thread=${threadLabel()} ${memory.label()}")
            if (details.isNotBlank()) append(" ${safeText(details, 240)}")
        }
        if (critical) {
            val compact =
                "s${token.scanId} ${safeToken(token.providerName).take(28)} ${safeToken(phase).take(24)} " +
                    "e${token.executionId} ${memory.compact()}"
            recordCritical(message, compact)
        } else {
            record(message)
        }
    }

    fun export(context: Context): String = exportRaw(context)

    fun exportSummary(context: Context): String {
        install(context)
        flushWriter()
        val body = readBody()
        return buildSummary(context, body)
    }

    fun exportRaw(context: Context): String {
        install(context)
        flushWriter()
        return buildRaw(context, readBody())
    }

    /** Saves one ZIP to Downloads/VUEO on Android 10+, or app Documents on older Android. */
    fun saveBundle(context: Context): DiagnosticSaveResult {
        install(context)
        flushWriter()
        val body = readBody()
        val summary = buildSummary(context, body)
        val raw = buildRaw(context, body)
        val metadata = buildMetadata(context)
        val crashReport = CrashReportStore.exportCrashReport(context)
        val tombstone = CrashReportStore.nativeTombstoneFile(context)
        val displayName = "VUEO_Log_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.zip"

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VUEO")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Unable to create diagnostic file in Downloads"
            }
            try {
                resolver.openOutputStream(uri, "w")!!.use { output ->
                    writeDiagnosticZip(output, summary, raw, metadata, crashReport, tombstone)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return DiagnosticSaveResult(displayName, "Downloads/VUEO")
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val directory = File(root, "VUEO").apply { mkdirs() }
        val destination = File(directory, displayName)
        destination.outputStream().buffered().use { output ->
            writeDiagnosticZip(output, summary, raw, metadata, crashReport, tombstone)
        }
        return DiagnosticSaveResult(displayName, destination.parentFile?.absolutePath ?: directory.absolutePath)
    }

    fun clear(context: Context) {
        install(context)
        flushWriter()
        pendingLines.clear()
        providerPhases.clear()
        synchronized(fileLock) {
            runCatching { logFile().writeText("") }
        }
        runCatching { CrashReportStore.clearDiagnosticArtifacts(context.applicationContext) }
        record("DIAGNOSTICS_CLEARED")
    }

    private fun buildSummary(context: Context, body: String): String {
        val lines = body.lineSequence().filter { it.isNotBlank() }.toList()
        val latestScreen = lines.lastOrNull { " | SCREEN " in it }
            ?.substringAfter(" | SCREEN ")
            ?.take(120)
            ?: "Unavailable"
        val latestScan = lines.lastOrNull { " | SCAN_START " in it || " | SCAN_END " in it }
            ?.substringAfter(" | ")
            ?: "No source scan recorded."
        val recentCritical = lines.filter { " | QJS_PHASE " in it }.takeLast(8)
        val recentProblems = lines.filter { line ->
            listOf("CRASH ", "SCAN_ERROR ", "PLAYBACK_ERROR ", "UI_STALL_", "QJS_ABORT", "status=FAILED", "status=TIMEOUT")
                .any { marker -> marker in line }
        }.takeLast(12)
        val recentProviders = lines.filter { " | PROVIDER_END " in it }.takeLast(10)
        val active = providerPhases.values.sortedWith(compareBy<ProviderPhase> { it.scanId }.thenBy { it.providerName })
        val tombstone = CrashReportStore.nativeTombstoneFile(context)
        val crash = CrashReportStore.exportCrashReport(context)

        return buildString {
            appendLine("VUEO Diagnostics Summary")
            append(buildMetadata(context))
            appendLine("Current screen: $latestScreen")
            appendLine("Runtime: activeScans=${activeScans.get()} activeProviders=${activeProviders.get()} ${detailedMemorySnapshot(force = true).label()}")
            appendLine("Logging: normal events batched (${BATCH_DELAY_MS}ms); critical QuickJS boundary breadcrumbs sync")
            appendLine("Last scan: $latestScan")
            appendLine("Native tombstone: ${if (tombstone != null) "available (${tombstone.length()} bytes)" else "not available"}")
            crash?.let {
                appendLine("Stored crash: ${it.summary} @ ${formatTimestamp(it.timestampMs)}")
            }
            appendLine()
            appendLine("Active provider phases")
            if (active.isEmpty()) appendLine("- none")
            else active.forEach { phase ->
                appendLine("- scan=${phase.scanId} provider=${phase.providerName} phase=${phase.phase}" +
                    (phase.executionId?.let { " exec=$it" } ?: ""))
            }
            appendLine()
            appendLine("Recent QuickJS / native-boundary phases")
            if (recentCritical.isEmpty()) appendLine("- none") else recentCritical.forEach { appendLine(it) }
            appendLine()
            appendLine("Recent problems / stalls")
            if (recentProblems.isEmpty()) appendLine("- none") else recentProblems.forEach { appendLine(it) }
            appendLine()
            appendLine("Recent provider results")
            if (recentProviders.isEmpty()) appendLine("- none") else recentProviders.forEach { appendLine(it) }
        }
    }

    private fun buildRaw(context: Context, body: String): String = buildString {
        appendLine("VUEO Performance / Crash Diagnostic Log — Raw")
        append(buildMetadata(context))
        appendLine("Current: ${detailedMemorySnapshot(force = false).label()}")
        appendLine()
        if (body.isBlank()) {
            appendLine("No runtime diagnostics captured yet.")
            appendLine("Open a title and run source discovery, then reopen this log.")
        } else {
            append(body.takeLast(400_000))
        }
    }

    private fun buildMetadata(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return buildString {
            appendLine("Package: ${context.packageName}")
            appendLine("Version: ${packageInfo?.versionName ?: "unknown"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    private fun writeDiagnosticZip(
        output: OutputStream,
        summary: String,
        raw: String,
        metadata: String,
        crashReport: AppCrashReport?,
        tombstone: File?,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            zipText(zip, "summary.txt", summary)
            zipText(zip, "raw.log", raw)
            zipText(zip, "metadata.txt", metadata)
            crashReport?.let {
                zipText(zip, "crash_report.txt", "${it.summary}\n\n${it.details}")
            }
            tombstone?.takeIf { it.exists() && it.length() > 0L }?.let { file ->
                zip.putNextEntry(ZipEntry("native_tombstone.pb"))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun zipText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { appContext?.let { CrashReportStore.capture(it, thread, throwable) } }
            runCatching {
                val stack = throwable.stackTrace
                    .take(36)
                    .joinToString(" | ") { frame ->
                        "${frame.className}.${frame.methodName}:${frame.lineNumber}"
                    }
                recordCritical(
                    "CRASH thread=${safeText(thread.name, 60)} " +
                        "type=${safeToken(throwable::class.java.simpleName)} " +
                        "message=${safeText(throwable.message.orEmpty(), 220)} ${memoryLabel()} " +
                        "stack=${safeText(stack, 5000)}",
                    "CRASH ${safeToken(throwable::class.java.simpleName)} ${detailedMemorySnapshot(force = true).compact()}",
                )
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    // Runs independently of the main looper: captures stacks while it is stalled.
    private fun startStallWatchdog(context: Context) {
        (context as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumedActivities.incrementAndGet() }
            override fun onActivityPaused(activity: Activity) { resumedActivities.updateAndGet { maxOf(0, it - 1) } }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        val pendingSince = AtomicLong(0L)
        val probe = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "vueo-anr-watchdog").apply { isDaemon = true }
        }
        var samples = 0
        probe.scheduleWithFixedDelay({
            runCatching {
                if (resumedActivities.get() == 0) {
                    pendingSince.set(0L)
                    samples = 0
                    return@runCatching
                }
                val now = SystemClock.uptimeMillis()
                val since = pendingSince.get()
                if (since == 0L) {
                    samples = 0
                    if (pendingSince.compareAndSet(0L, now)) {
                        mainHandler.post { pendingSince.compareAndSet(now, 0L) }
                    }
                } else if (now - since >= 2_000L && samples < 3) {
                    samples++
                    val stack = Looper.getMainLooper().thread.stackTrace.take(80)
                        .joinToString("\n") { "  at $it" }
                    val evidence = "UI_STALL_LIVE delay=${now - since}ms sample=$samples " +
                        "activeScans=${activeScans.get()} activeProviders=${activeProviders.get()} ${memoryLabel()}\n$stack"
                    CrashReportStore.stall(timestamped(evidence))
                    record(evidence)
                }
            }
        }, 1_000L, 1_000L, TimeUnit.MILLISECONDS)
    }

    private fun record(message: String) {
        val line = timestamped(message)
        CrashReportStore.note(line)
        latestSummaryLine.set(line)
        pendingLines.add(line)
        scheduleFlush()
    }

    private fun recordCritical(message: String, compactState: String) {
        val line = timestamped(message)
        CrashReportStore.note(line)
        latestSummaryLine.set(line)
        synchronized(fileLock) {
            appendPendingLocked()
            appendLineLocked(line)
        }
        appContext?.let { CrashReportStore.criticalState(it, compactState) }
    }

    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        writer.schedule({
            try {
                synchronized(fileLock) { appendPendingLocked() }
                val latest = latestSummaryLine.get()
                appContext?.let {
                    CrashReportStore.updateSystemSummary(
                        it,
                        latest,
                        force = forceSystemSummary.getAndSet(false),
                    )
                }
            } finally {
                flushScheduled.set(false)
                if (pendingLines.isNotEmpty()) scheduleFlush()
            }
        }, BATCH_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun appendPendingLocked() {
        if (pendingLines.isEmpty()) return
        val buffer = StringBuilder()
        while (true) {
            val line = pendingLines.poll() ?: break
            buffer.append(line).append('\n')
        }
        if (buffer.isNotEmpty()) appendTextLocked(buffer.toString())
    }

    private fun appendLineLocked(line: String) {
        appendTextLocked(line + "\n")
    }

    private fun appendTextLocked(text: String) {
        val context = appContext ?: return
        runCatching {
            val file = File(context.noBackupFilesDir, FILE_NAME)
            file.appendText(text)
            if (file.length() > ROTATE_AT_BYTES) {
                val bytes = file.readBytes()
                val tail = bytes.copyOfRange((bytes.size - KEEP_BYTES).coerceAtLeast(0), bytes.size)
                file.writeBytes(tail)
            }
        }
    }

    private fun readBody(): String = synchronized(fileLock) {
        runCatching { logFile().takeIf(File::exists)?.readText().orEmpty() }.getOrDefault("")
    }

    private fun logFile(): File {
        val context = requireNotNull(appContext) { "RuntimeDiagnostics is not installed" }
        return File(context.noBackupFilesDir, FILE_NAME)
    }

    private fun flushWriter() {
        runCatching {
            writer.submit {
                synchronized(fileLock) { appendPendingLocked() }
            }.get(800, TimeUnit.MILLISECONDS)
        }
    }

    private fun updateProviderPhase(scanId: Long, providerName: String, phase: String, executionId: Long?) {
        val safeProvider = safeText(providerName, 80)
        val safePhase = safeToken(phase)
        providerPhases[providerKey(scanId, providerName)] = ProviderPhase(
            scanId = scanId,
            providerName = safeProvider,
            phase = safePhase,
            executionId = executionId,
            updatedMs = System.currentTimeMillis(),
        )
        CrashReportStore.observedState(
            "s$scanId ${safeToken(safeProvider).take(28)} ${safePhase.take(24)}" +
                (executionId?.let { " e$it" } ?: "")
        )
    }

    private fun providerKey(scanId: Long, providerName: String): String = "$scanId|$providerName"

    private fun timestamped(message: String): String {
        val stamp = timestampFormat.get()!!.format(Date())
        return "$stamp | $message"
    }

    private fun formatTimestamp(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))

    private fun memoryLabel(): String {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return "heap=${mb(used)}MB/${mb(runtime.maxMemory())}MB"
    }

    private fun detailedMemorySnapshot(force: Boolean): MemorySnapshot {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedMemorySnapshot
        if (!force && cached != null && now - memorySnapshotAtMs.get() < MEMORY_SNAPSHOT_CACHE_MS) return cached
        return synchronized(memoryLock) {
            val recheck = cachedMemorySnapshot
            val last = memorySnapshotAtMs.get()
            if (!force && recheck != null && now - last < MEMORY_SNAPSHOT_CACHE_MS) return@synchronized recheck
            val runtime = Runtime.getRuntime()
            val javaUsed = runtime.totalMemory() - runtime.freeMemory()
            val snapshot = MemorySnapshot(
                javaHeapMb = mb(javaUsed),
                javaHeapMaxMb = mb(runtime.maxMemory()),
                nativeHeapMb = mb(Debug.getNativeHeapAllocatedSize()),
                pssMb = Debug.getPss() / 1024L,
                rssMb = readRssKb() / 1024L,
            )
            cachedMemorySnapshot = snapshot
            memorySnapshotAtMs.set(now)
            snapshot
        }
    }

    private fun readRssKb(): Long = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.substringAfter(':')
                ?.trim()
                ?.substringBefore(' ')
                ?.toLongOrNull()
                ?: 0L
        }
    }.getOrDefault(0L)

    private fun heapUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun mb(bytes: Long): Long = bytes / (1024L * 1024L)

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000L

    private fun threadLabel(): String = safeText(Thread.currentThread().name, 60)

    private fun safeToken(value: String): String = buildString(minOf(80, value.length)) {
        value.forEach { char ->
            if (length >= 80) return@forEach
            append(if (char.isLetterOrDigit() || char == '.' || char == '_' || char == '-') char else '_')
        }
    }

    private fun safeText(value: String, max: Int): String =
        value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(secretPattern) {
                "${it.groupValues[1]}=<redacted>"
            }
            .take(max)

    private fun AtomicInteger.updateMax(candidate: Int) {
        while (true) {
            val current = get()
            if (candidate <= current || compareAndSet(current, candidate)) return
        }
    }
}
