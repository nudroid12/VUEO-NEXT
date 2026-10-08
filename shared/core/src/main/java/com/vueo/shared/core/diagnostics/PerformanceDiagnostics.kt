package com.vueo.shared.core.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Choreographer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Opt-in performance recorder shared by TV and Mobile.
 *
 * The persisted master switch has only two user-facing states:
 * OFF = no frame probe, sampler or new event buffering.
 * ON  = collection starts immediately and remains active until switched off.
 * Existing in-memory events are retained when collection is stopped; only clear()
 * removes them. RuntimeDiagnostics forwards its existing breadcrumbs here so page,
 * player and provider activity can be correlated without duplicate instrumentation.
 */
object PerformanceDiagnostics {
    private const val PREFS = "vueo_performance_diagnostics"
    private const val KEY_ENABLED = "enabled"
    private const val MAX_EVENTS = 5_000
    private const val SYSTEM_SAMPLE_MS = 1_500L
    private const val PSS_SAMPLE_MS = 5_000L
    private const val JANK_THRESHOLD_MS = 48L

    enum class Tab(val label: String) {
        HOME("Home"),
        SEARCH("Search"),
        DETAILS("Details"),
        PLAYER("Player"),
        SOURCES("Sources"),
        EPISODES("Episodes"),
        LIBRARY("Library"),
        SETTINGS("Settings"),
        PROVIDER("Provider"),
        SYSTEM("System"),
        OTHER("Other"),
        FULL("Full"),
    }

    data class SaveResult(
        val displayName: String,
        val location: String,
    )

    private data class Event(
        val timestampMs: Long,
        val category: Tab,
        val page: Tab,
        val line: String,
    )

    private data class SummaryStats(
        val matchingEvents: Int,
        val firstTimestampMs: Long?,
        val lastTimestampMs: Long?,
        val jankEvents: Int,
        val worstFrameGapMs: Long,
        val stallEvents: Int,
        val worstStallMs: Long,
        val scanStarts: Int,
        val scanFailures: Int,
        val providerStarts: Int,
        val providerIssues: Int,
        val quickJsEvents: Int,
        val quickJsExecutions: Int,
        val quickJsCancelled: Int,
        val providerCancelled: Int,
        val scanCancelled: Int,
        val systemSamples: Int,
        val quickJsIssues: Int,
        val playbackIssues: Int,
        val recentSignals: List<String>,
    )

    private val installed = AtomicBoolean(false)
    private val enabled = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val sessionSequence = AtomicLong(0L)
    private val workSequence = AtomicLong(0L)
    private val providerIssueStatuses = setOf("ERROR", "FAILED", "FAILURE", "TIMEOUT", "UNAVAILABLE", "BLOCKED")
    private val playbackIssueEvents = setOf("ERROR", "FAILED", "TIMEOUT")
    private val quickJsIssuePhases = setOf("QJS_ABORT", "QJS_TIMEOUT")
    private val droppedEvents = AtomicLong(0L)
    private val activeScans = AtomicInteger(0)
    private val activeProviders = AtomicInteger(0)
    private val activeQuickJs = AtomicInteger(0)
    private val activityLock = Any()
    private val scanKeys = HashSet<String>()
    private val providerKeys = HashSet<String>()
    private val quickJsKeys = HashSet<String>()
    private val screenLock = Any()
    private var baseScreen = "UNKNOWN"
    private val playerScreens = LinkedHashMap<Long, String>()
    private val screenSequence = AtomicLong(0L)
    @Volatile private var sampledPssMb = -1L
    fun lastSampledPssMb(): Long? = sampledPssMb.takeIf { it >= 0L }
    private val currentScreen = AtomicReference("UNKNOWN")
    private val currentPage = AtomicReference(Tab.OTHER)
    private val eventsLock = Any()
    private val events = ArrayDeque<Event>(MAX_EVENTS)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val timestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sampler: ScheduledExecutorService? = null

    @Volatile
    private var lastFrameNs: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!recording.get()) {
                lastFrameNs = 0L
                return
            }
            val previous = lastFrameNs
            lastFrameNs = frameTimeNanos
            if (previous > 0L) {
                val gapMs = (frameTimeNanos - previous) / 1_000_000L
                if (gapMs >= JANK_THRESHOLD_MS) {
                    appendEvent(
                        category = currentPage.get(),
                        message = "FRAME_JANK gap=${gapMs}ms screen=${safe(currentScreen.get(), 100)}",
                    )
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        val storedEnabled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        enabled.set(storedEnabled)
        if (storedEnabled) startRecording(context.applicationContext)
    }

    fun isEnabled(context: Context): Boolean {
        install(context)
        return enabled.get()
    }

    fun isRecording(): Boolean = recording.get()

    fun isCollecting(): Boolean = enabled.get() && recording.get()

    fun setEnabled(context: Context, value: Boolean) {
        install(context)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        enabled.set(value)
        if (value) startRecording(context.applicationContext) else stopRecording()
    }

    fun startRecording(context: Context) {
        install(context)
        if (!enabled.get() || !recording.compareAndSet(false, true)) return
        synchronized(activityLock) {
            scanKeys.clear(); providerKeys.clear(); quickJsKeys.clear()
            activeScans.set(0); activeProviders.set(0); activeQuickJs.set(0)
        }
        sampledPssMb = -1L
        val session = sessionSequence.incrementAndGet()
        appendEvent(Tab.SYSTEM, "RECORDING_START session=$session")
        startSampler()
        mainHandler.post {
            if (recording.get()) {
                lastFrameNs = 0L
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }
    }

    fun stopRecording() {
        if (!recording.compareAndSet(true, false)) return
        appendEvent(Tab.SYSTEM, "RECORDING_STOP")
        synchronized(activityLock) {
            scanKeys.clear(); providerKeys.clear(); quickJsKeys.clear()
            activeScans.set(0); activeProviders.set(0); activeQuickJs.set(0)
        }
        sampler?.shutdownNow()
        sampler = null
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            lastFrameNs = 0L
        }
    }

    fun observeScreen(label: String) = synchronized(screenLock) {
        baseScreen = label.take(140)
        updateScreen(playerScreens.values.lastOrNull() ?: baseScreen)
    }

    private fun updateScreen(label: String) {
        currentScreen.set(label)
        currentPage.set(pageFor(label))
    }

    fun enterPlayerScreen(platform: String): Long = synchronized(screenLock) {
        val id = screenSequence.incrementAndGet()
        playerScreens[id] = "$platform PLAYER"
        updateScreen(playerScreens.getValue(id))
        if (isCollecting()) appendEvent(Tab.PLAYER, "SCREEN ${currentScreen.get()}")
        id
    }

    fun exitPlayerScreen(id: Long) = synchronized(screenLock) {
        if (playerScreens.remove(id) != null) {
            updateScreen(playerScreens.values.lastOrNull() ?: baseScreen)
            if (isCollecting()) appendEvent(currentPage.get(), "SCREEN ${currentScreen.get()}")
        }
    }

    /** Timings are collected only within the recording session that started the work. */
    suspend fun <T> measureWork(name: String, page: Tab, block: suspend () -> T): T {
        if (!isCollecting()) return block()
        val session = sessionSequence.get()
        val workId = workSequence.incrementAndGet()
        val startedNs = System.nanoTime()
        appendEvent(page, "WORK_START id=$workId name=$name thread=${Thread.currentThread().name}")
        var outcome = "complete"
        try {
            return block()
        } catch (error: Throwable) {
            outcome = if (error is CancellationException && error !is TimeoutCancellationException) "cancelled" else "failed"
            throw error
        } finally {
            if (isCollecting() && session == sessionSequence.get()) {
                appendEvent(page, "WORK_END id=$workId name=$name outcome=$outcome elapsed=${(System.nanoTime() - startedNs) / 1_000_000L}ms thread=${Thread.currentThread().name}")
            }
        }
    }

    fun <T> measureBlock(name: String, page: Tab, block: () -> T): T {
        if (!isCollecting()) return block()
        val session = sessionSequence.get()
        val workId = workSequence.incrementAndGet()
        val startedNs = System.nanoTime()
        var outcome = "complete"
        try {
            return block()
        } catch (error: Throwable) {
            outcome = "failed"
            throw error
        } finally {
            if (isCollecting() && session == sessionSequence.get()) {
                appendEvent(page, "WORK_END id=$workId name=$name outcome=$outcome elapsed=${(System.nanoTime() - startedNs) / 1_000_000L}ms thread=${Thread.currentThread().name}")
            }
        }
    }

    suspend fun <T> measuredContext(
        context: CoroutineContext,
        name: String,
        page: Tab,
        block: suspend CoroutineScope.() -> T,
    ): T = withContext(context) { measureWork(name, page) { block() } }

    private fun field(message: String, key: String): String =
        message.substringAfter(" $key=", "").substringBefore(' ')

    /** Called by RuntimeDiagnostics before its own crash-log enable check. */
    fun captureRuntimeEvent(message: String) {
        if (!isCollecting()) return
        val trimmed = message.trim()
        synchronized(activityLock) {
            if (!isCollecting()) return
            when {
                trimmed.startsWith("SCAN_START ") -> scanKeys.add(field(trimmed, "id"))
                trimmed.startsWith("SCAN_END ") -> scanKeys.remove(field(trimmed, "id"))
                trimmed.startsWith("PROVIDER_START ") -> providerKeys.add("${field(trimmed, "scan")}:${trimmed.substringAfter(" provider=").substringBefore(" activeProviders=")}")
                trimmed.startsWith("PROVIDER_END ") -> providerKeys.remove("${field(trimmed, "scan")}:${trimmed.substringAfter(" provider=").substringBefore(" status=")}")
                trimmed.startsWith("QJS_PHASE ") -> {
                    val key = "${field(trimmed, "scan")}:${field(trimmed, "exec")}"
                    when (field(trimmed, "phase")) {
                        "QJS_CREATE_BEGIN" -> quickJsKeys.add(key)
                        "QJS_CLOSE", "QJS_ABORT", "QJS_TIMEOUT", "QJS_CANCELLED" -> quickJsKeys.remove(key)
                    }
                }
            }
            activeScans.set(scanKeys.size)
            activeProviders.set(providerKeys.size)
            activeQuickJs.set(quickJsKeys.size)
        }
        if (trimmed.startsWith("SCREEN ")) {
            observeScreen(trimmed.removePrefix("SCREEN "))
            appendEvent(currentPage.get(), "SCREEN ${currentScreen.get()}")
            return
        }
        appendEvent(categoryFor(trimmed), trimmed)
    }

    /** Full chronological raw export for Copy/Save and offline inspection. */
    fun exportRaw(tab: Tab): String {
        val snapshot = snapshot(tab)
        return buildRaw(
            tab = tab,
            snapshot = snapshot,
            totalMatchingEvents = snapshot.size,
            isPreview = false,
        )
    }

    /**
     * Bounded raw UI preview so the diagnostics viewer does not become its own
     * performance problem while recording is active. The UI stays intentionally
     * terse: implementation details live in metadata.txt inside the saved bundle.
     */
    fun previewRaw(tab: Tab, maxEvents: Int = 220): String {
        val limit = maxEvents.coerceIn(20, 500)
        val snapshot = recentSnapshot(tab, limit)
        val totalMatchingEvents = matchingEventCount(tab)
        return buildRaw(
            tab = tab,
            snapshot = snapshot,
            totalMatchingEvents = totalMatchingEvents,
            isPreview = true,
        )
    }

    private fun buildRaw(
        tab: Tab,
        snapshot: List<Event>,
        totalMatchingEvents: Int,
        isPreview: Boolean,
    ): String = buildString {
        appendLine("RAW PERFORMANCE LOG — ${tab.label.uppercase(Locale.US)}")
        append("${if (isCollecting()) "ON • recording" else "OFF • inactive"}")
        append(" • Events: $totalMatchingEvents")
        if (droppedEvents.get() > 0L) append(" • Dropped: ${droppedEvents.get()}")
        appendLine()

        if (snapshot.isEmpty()) {
            appendLine()
            appendLine("No raw events recorded yet.")
            return@buildString
        }

        if (isPreview && snapshot.size < totalMatchingEvents) {
            appendLine("Showing latest ${snapshot.size} of $totalMatchingEvents events.")
        }
        appendLine()
        snapshot.forEach { appendLine(it.line) }
    }

    /** Human-readable performance overview for the selected tab. */
    fun exportSummary(tab: Tab): String = buildSummary(tab)

    /** Summary is already bounded to counters plus a few recent signals. */
    fun previewSummary(tab: Tab): String = buildSummary(tab)

    // Compatibility aliases for callers/older patches that still use export()/preview().
    fun export(tab: Tab): String = exportRaw(tab)

    fun preview(tab: Tab, maxEvents: Int = 220): String = previewRaw(tab, maxEvents)

    private fun buildSummary(tab: Tab): String {
        val stats = summaryStats(tab)
        val collecting = isCollecting()
        return buildString {
            appendLine("PERFORMANCE SUMMARY — ${tab.label.uppercase(Locale.US)}")
            appendLine()
            appendLine("Diagnostics: ${if (collecting) "ON • recording" else "OFF"}")

            if (stats.matchingEvents == 0) {
                appendLine(
                    if (collecting) {
                        "No performance data recorded yet. Use VUEO normally while diagnostics runs."
                    } else {
                        "No performance data recorded yet."
                    }
                )
                return@buildString
            }

            append("Events: ${stats.matchingEvents}")
            if (droppedEvents.get() > 0L) append(" • Dropped: ${droppedEvents.get()}")
            appendLine()
            appendLine("Session window: ${summaryWindow(stats.firstTimestampMs, stats.lastTimestampMs)}")
            appendLine("Current screen: ${currentScreen.get()}")

            appendLine()
            appendLine("FRAME / UI")
            append("Jank: ${stats.jankEvents}")
            if (stats.jankEvents > 0) append(" • Worst frame: ${stats.worstFrameGapMs}ms")
            appendLine()
            append("UI stalls: ${stats.stallEvents}")
            if (stats.stallEvents > 0) append(" • Worst stall: ${stats.worstStallMs}ms")
            appendLine()

            appendLine()
            appendLine("RUNTIME")
            append("Source scans: ${stats.scanStarts} • Cancelled: ${stats.scanCancelled} • Failed: ${stats.scanFailures}")
            if (activeScans.get() > 0) append(" • Active: ${activeScans.get()}")
            appendLine()
            append("Providers: ${stats.providerStarts} • Cancelled: ${stats.providerCancelled}")
            if (activeProviders.get() > 0) append(" • Active: ${activeProviders.get()}")
            if (stats.providerIssues > 0) append(" • Issues: ${stats.providerIssues}")
            appendLine()
            append("QuickJS executions: ${stats.quickJsExecutions} • Phases: ${stats.quickJsEvents} • Cancelled: ${stats.quickJsCancelled}")
            if (activeQuickJs.get() > 0) append(" • Active: ${activeQuickJs.get()}")
            if (stats.quickJsIssues > 0) append(" • Issues: ${stats.quickJsIssues}")
            appendLine()
            appendLine("Playback issues: ${stats.playbackIssues}")
            appendLine("System samples: ${stats.systemSamples}")

            appendLine()
            appendLine("SIGNALS TO INSPECT")
            var wroteSignal = false
            if (stats.worstFrameGapMs >= 80L) {
                appendLine("• High frame gap: ${stats.worstFrameGapMs}ms")
                wroteSignal = true
            }
            if (stats.stallEvents > 0) {
                appendLine("• UI stalls: ${stats.stallEvents}, worst ${stats.worstStallMs}ms")
                wroteSignal = true
            }
            if (stats.scanFailures > 0) {
                appendLine("• Failed source scans: ${stats.scanFailures}")
                wroteSignal = true
            }
            if (stats.providerIssues > 0) {
                appendLine("• Provider failure/timeout signals: ${stats.providerIssues}")
                wroteSignal = true
            }
            if (stats.quickJsIssues > 0) {
                appendLine("• QuickJS error/abort signals: ${stats.quickJsIssues}")
                wroteSignal = true
            }
            if (stats.playbackIssues > 0) {
                appendLine("• Playback error/failure signals: ${stats.playbackIssues}")
                wroteSignal = true
            }
            if (droppedEvents.get() > 0L) {
                appendLine("• ${droppedEvents.get()} old event(s) were dropped from the in-memory window")
                wroteSignal = true
            }
            if (!wroteSignal) appendLine("• No obvious performance issue detected in this window.")

            if (stats.recentSignals.isNotEmpty()) {
                appendLine()
                appendLine("RECENT SIGNALS")
                stats.recentSignals.takeLast(5).forEach { appendLine("• ${compactSignal(it)}") }
            }
        }
    }

    private fun summaryStats(tab: Tab): SummaryStats = synchronized(eventsLock) {
        var matchingEvents = 0
        var firstTimestampMs: Long? = null
        var lastTimestampMs: Long? = null
        var jankEvents = 0
        var worstFrameGapMs = 0L
        var stallEvents = 0
        var worstStallMs = 0L
        var scanStarts = 0
        var scanFailures = 0
        var providerStarts = 0
        var providerIssues = 0
        var quickJsExecutions = 0
        var quickJsCancelled = 0
        var providerCancelled = 0
        var scanCancelled = 0
        var systemSamples = 0
        var quickJsEvents = 0
        var quickJsIssues = 0
        var playbackIssues = 0
        val recentSignals = ArrayDeque<String>(8)

        events.forEach { event ->
            if (!matchesTab(tab, event)) return@forEach
            matchingEvents++
            if (firstTimestampMs == null) firstTimestampMs = event.timestampMs
            lastTimestampMs = event.timestampMs
            val payload = event.line.substringAfter("] ")
            val upper = payload.uppercase(Locale.US)

            if (upper.startsWith("FRAME_JANK ")) {
                jankEvents++
                worstFrameGapMs = maxOf(worstFrameGapMs, extractMs(event.line, "gap"))
            }
            if (upper.startsWith("UI_STALL ")) {
                stallEvents++
                worstStallMs = maxOf(worstStallMs, extractMs(event.line, "delay"))
            }
            if (upper.startsWith("SCAN_START ")) scanStarts++
            if (upper.startsWith("SCAN_END ") && field(upper, "OUTCOME") == "FAILED") scanFailures++
            if (upper.startsWith("SCAN_END ") && field(upper, "OUTCOME") == "CANCELLED") scanCancelled++
            if (upper.startsWith("SYSTEM_SAMPLE ")) systemSamples++
            if (upper.startsWith("PROVIDER_START ")) providerStarts++
            val status = field(upper, "STATUS")
            if (upper.startsWith("PROVIDER_END ")) {
                if (status == "CANCELLED") providerCancelled++
                else if (status in providerIssueStatuses) providerIssues++
            }
            if (upper.startsWith("QJS_PHASE ")) {
                quickJsEvents++
                when (field(upper, "PHASE")) {
                    "QJS_CREATE_BEGIN" -> quickJsExecutions++
                    "QJS_CANCELLED" -> quickJsCancelled++
                    "QJS_ABORT", "QJS_TIMEOUT" -> quickJsIssues++
                }
            }
            val playbackIssue = upper.startsWith("PLAYBACK_ERROR ") ||
                (upper.startsWith("PLAYER_EVENT ") && field(upper, "EVENT") in playbackIssueEvents)
            if (playbackIssue) playbackIssues++

            val notable = upper.startsWith("FRAME_JANK ") || upper.startsWith("UI_STALL ") ||
                upper.startsWith("UI_STALL_RISK ") || playbackIssue ||
                upper.startsWith("SCAN_END ") || upper.startsWith("SCAN_ERROR ") ||
                upper.startsWith("PROVIDER_END ") || field(upper, "PHASE") in quickJsIssuePhases
            if (notable) {
                if (recentSignals.size >= 8) recentSignals.removeFirst()
                recentSignals.addLast(event.line.take(260))
            }
        }

        SummaryStats(
            matchingEvents = matchingEvents,
            firstTimestampMs = firstTimestampMs,
            lastTimestampMs = lastTimestampMs,
            jankEvents = jankEvents,
            worstFrameGapMs = worstFrameGapMs,
            stallEvents = stallEvents,
            worstStallMs = worstStallMs,
            scanStarts = scanStarts,
            scanFailures = scanFailures,
            providerStarts = providerStarts,
            providerIssues = providerIssues,
            quickJsEvents = quickJsEvents,
            quickJsExecutions = quickJsExecutions,
            quickJsCancelled = quickJsCancelled,
            providerCancelled = providerCancelled,
            scanCancelled = scanCancelled,
            systemSamples = systemSamples,
            quickJsIssues = quickJsIssues,
            playbackIssues = playbackIssues,
            recentSignals = recentSignals.toList(),
        )
    }

    private fun extractMs(line: String, key: String): Long {
        val marker = "$key="
        val start = line.indexOf(marker)
        if (start < 0) return 0L
        val valueStart = start + marker.length
        val value = line.substring(valueStart).takeWhile { it.isDigit() }
        return value.toLongOrNull() ?: 0L
    }

    private fun summaryWindow(first: Long?, last: Long?): String {
        if (first == null || last == null) return "0s"
        val durationMs = (last - first).coerceAtLeast(0L)
        val totalSeconds = durationMs / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private fun compactSignal(line: String): String {
        val separator = line.indexOf(" | ")
        if (separator < 0) return line.take(220)
        val timestamp = line.substring(0, separator)
        val timeOnly = timestamp.substringAfter(' ', timestamp)
        val payload = line.substring(separator + 3)
        return "$timeOnly $payload".take(220)
    }

    private fun matchingEventCount(tab: Tab): Int = synchronized(eventsLock) {
        if (tab == Tab.FULL) events.size else events.count { matchesTab(tab, it) }
    }

    fun clear() {
        synchronized(eventsLock) { events.clear() }
        droppedEvents.set(0L)
    }

    fun saveBundle(context: Context): SaveResult {
        install(context)
        val displayName = "VUEO_Performance_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.zip"
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
                "Unable to create performance log in Downloads"
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

    private fun startSampler() {
        sampler?.shutdownNow()
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "vueo-performance-sampler").apply { isDaemon = true }
        }
        sampler = executor
        var previousWall = SystemClock.elapsedRealtime()
        var previousCpu = Process.getElapsedCpuTime()
        var lastPssAt = 0L
        var cachedPssMb = 0L
        executor.scheduleWithFixedDelay({
            if (!recording.get()) return@scheduleWithFixedDelay
            runCatching {
                val nowWall = SystemClock.elapsedRealtime()
                val nowCpu = Process.getElapsedCpuTime()
                val wallDelta = (nowWall - previousWall).coerceAtLeast(1L)
                val cpuDelta = (nowCpu - previousCpu).coerceAtLeast(0L)
                previousWall = nowWall
                previousCpu = nowCpu
                val cpuPercent = (cpuDelta * 100.0) / wallDelta.toDouble()
                val runtime = Runtime.getRuntime()
                val javaMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
                val javaMaxMb = runtime.maxMemory() / (1024L * 1024L)
                val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
                if (nowWall - lastPssAt >= PSS_SAMPLE_MS || lastPssAt == 0L) {
                    cachedPssMb = Debug.getPss() / 1024L
                    lastPssAt = nowWall
                }
                sampledPssMb = cachedPssMb
                val pssMb = cachedPssMb
                val rssMb = readRssKb() / 1024L
                appendEvent(
                    Tab.SYSTEM,
                    "SYSTEM_SAMPLE cpu=${String.format(Locale.US, "%.1f", cpuPercent)}% " +
                        "java=$javaMb/$javaMaxMb MB native=${nativeMb}MB pss=${pssMb}MB rss=${rssMb}MB " +
                        "threads~${Thread.activeCount()} activeScans=${activeScans.get()} " +
                        "activeProviders=${activeProviders.get()} activeQuickJs=${activeQuickJs.get()} " +
                        "screen=${safe(currentScreen.get(), 100)}"
                )
            }
        }, 0L, SYSTEM_SAMPLE_MS, TimeUnit.MILLISECONDS)
    }

    private fun appendEvent(category: Tab, message: String) {
        if (!recording.get() && !message.startsWith("RECORDING_STOP")) return
        val page = currentPage.get()
        val stamp = timestampFormat.get()!!.format(Date())
        val line = "$stamp | [${category.label.uppercase(Locale.US)}] ${safe(message, 900)}"
        val event = Event(System.currentTimeMillis(), category, page, line)
        synchronized(eventsLock) {
            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
                droppedEvents.incrementAndGet()
            }
            events.addLast(event)
        }
    }

    private fun snapshot(tab: Tab): List<Event> = synchronized(eventsLock) {
        events.filter { event -> matchesTab(tab, event) }
    }

    private fun recentSnapshot(tab: Tab, maxEvents: Int): List<Event> =
        synchronized(eventsLock) {
            val recent = ArrayList<Event>(maxEvents)
            val iterator = events.descendingIterator()
            while (iterator.hasNext() && recent.size < maxEvents) {
                val event = iterator.next()
                if (matchesTab(tab, event)) recent += event
            }
            recent.reverse()
            recent
        }

    private fun eventCount(): Int = synchronized(eventsLock) { events.size }

    private fun matchesTab(tab: Tab, event: Event): Boolean =
        tab == Tab.FULL || event.category == tab || (isPageTab(tab) && event.page == tab)

    private fun categoryFor(message: String): Tab {
        val upper = message.uppercase(Locale.US)
        return when {
            upper.startsWith("PLAYER_EVENT") && ("SOURCE" in upper || "SERVER" in upper) -> Tab.SOURCES
            upper.startsWith("PLAYER_EVENT") && "EPISODE" in upper -> Tab.EPISODES
            upper.startsWith("PLAYER_EVENT") || upper.startsWith("PLAYBACK_") -> Tab.PLAYER
            upper.startsWith("SCAN_") || upper.startsWith("PROVIDER_") || upper.startsWith("QJS_") ||
                upper.startsWith("DISCOVERY_TRACE") -> Tab.PROVIDER
            upper.startsWith("UI_STALL") || upper.startsWith("SYSTEM_") -> Tab.SYSTEM
            else -> currentPage.get()
        }
    }

    private fun pageFor(label: String): Tab {
        val upper = label.uppercase(Locale.US)
        return when {
            "SOURCES" in upper -> Tab.SOURCES
            "EPISODES" in upper -> Tab.EPISODES
            "PLAYER" in upper -> Tab.PLAYER
            "DETAIL" in upper -> Tab.DETAILS
            "SEARCH" in upper -> Tab.SEARCH
            "LIBRARY" in upper || "MY LIST" in upper -> Tab.LIBRARY
            "SETTING" in upper || "PLUGIN" in upper || "ADDON" in upper -> Tab.SETTINGS
            "HOME" in upper -> Tab.HOME
            else -> Tab.OTHER
        }
    }

    private fun isPageTab(tab: Tab): Boolean = tab in setOf(
        Tab.HOME, Tab.SEARCH, Tab.DETAILS, Tab.PLAYER, Tab.SOURCES,
        Tab.EPISODES, Tab.LIBRARY, Tab.SETTINGS, Tab.OTHER,
    )

    private fun writeZip(output: OutputStream, metadata: String) {
        ZipOutputStream(output.buffered()).use { zip ->
            zipText(zip, "metadata.txt", metadata)
            zipText(zip, "summary.txt", exportSummary(Tab.FULL))
            Tab.entries.forEach { tab ->
                val rawName = if (tab == Tab.FULL) "full.log" else "${tab.name.lowercase(Locale.US)}.log"
                zipText(zip, rawName, exportRaw(tab))
                val summaryName = "summary/${tab.name.lowercase(Locale.US)}.txt"
                zipText(zip, summaryName, exportSummary(tab))
            }
        }
    }

    private fun zipText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildMetadata(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return buildString {
            appendLine("VUEO Performance Diagnostic Metadata")
            appendLine("Package: ${context.packageName}")
            appendLine("Version: ${packageInfo?.versionName ?: "unknown"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Enabled: ${enabled.get()}")
            appendLine("Recording: ${recording.get()}")
            appendLine("Events: ${eventCount()}")
            appendLine("Dropped: ${droppedEvents.get()}")
            appendLine("Active scans/providers/quickJs: ${activeScans.get()}/${activeProviders.get()}/${activeQuickJs.get()}")
            appendLine("Current screen: ${currentScreen.get()}")
            appendLine("System sample interval: ${SYSTEM_SAMPLE_MS}ms")
            appendLine("PSS probe interval: ${PSS_SAMPLE_MS}ms")
            appendLine("Jank threshold: ${JANK_THRESHOLD_MS}ms")
            appendLine("Runtime disk writes while recording: none")
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

    private fun safe(value: String, max: Int): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(max)
}
