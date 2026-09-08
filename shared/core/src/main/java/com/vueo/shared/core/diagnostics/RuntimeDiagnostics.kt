package com.vueo.shared.core.diagnostics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object RuntimeDiagnostics {
    private const val FILE_NAME = "vueo_runtime_diagnostics.log"
    private const val ROTATE_AT_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 256 * 1024
    private const val PROBE_INTERVAL_MS = 500L
    private const val STALL_THRESHOLD_MS = 350L

    private val installed = AtomicBoolean(false)
    private val scanSequence = AtomicLong(0L)
    private val activeScans = AtomicInteger(0)
    private val activeProviders = AtomicInteger(0)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vueo-runtime-diagnostics").apply { isDaemon = true }
    }
    private val fileLock = Any()
    private val scanStates = ConcurrentHashMap<Long, ScanState>()
    private val activeProbes = ConcurrentHashMap.newKeySet<Long>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var appContext: Context? = null

    data class ProviderToken internal constructor(
        val scanId: Long,
        val providerName: String,
        val startedNs: Long,
        val heapStartBytes: Long,
        val startedOnMainThread: Boolean,
    )

    private data class ScanState(
        val startedNs: Long,
        val startedOnMainThread: Boolean,
        val activeProviders: AtomicInteger = AtomicInteger(0),
        val maxConcurrentProviders: AtomicInteger = AtomicInteger(0),
    )

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return

        installCrashHandler()
        record(
            "SESSION_START android=${Build.VERSION.SDK_INT} " +
                "device=${safeToken(Build.MANUFACTURER)}_${safeToken(Build.MODEL)} " +
                memoryLabel()
        )
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
        startMainThreadProbe(id)
        return id
    }

    fun finishSourceScan(
        scanId: Long,
        streams: Int,
        completedProviders: Int,
        outcome: String = "complete",
    ) {
        val state = scanStates.remove(scanId)
        activeProbes.remove(scanId)
        val scans = activeScans.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (state == null) return

        val elapsedMs = elapsedMs(state.startedNs)
        record(
            "SCAN_END id=$scanId outcome=${safeToken(outcome)} elapsed=${elapsedMs}ms " +
                "streams=$streams providers=$completedProviders " +
                "maxConcurrentProviders=${state.maxConcurrentProviders.get()} " +
                "activeScans=$scans thread=${threadLabel()} ${memoryLabel()}"
        )

    }

    fun recordDiscoveryTrace(
        scanId: Long,
        stage: String,
        details: String,
        providerName: String? = null,
    ) {
        val providerPart = providerName
            ?.takeIf { it.isNotBlank() }
            ?.let { " provider=${safeText(it, 80)}" }
            .orEmpty()

        record(
            "DISCOVERY_TRACE scan=$scanId stage=${safeToken(stage)}$providerPart " +
                safeText(details, 360)
        )
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

    fun export(context: Context): String {
        install(context)
        flushWriter()
        val body = synchronized(fileLock) {
            runCatching { logFile().takeIf(File::exists)?.readText().orEmpty() }.getOrDefault("")
        }
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        return buildString {
            appendLine("VUEO Performance / Crash Diagnostic Log")
            appendLine("Package: ${context.packageName}")
            appendLine("Version: ${packageInfo?.versionName ?: "unknown"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Current: ${memoryLabel()}")
            appendLine()
            if (body.isBlank()) {
                appendLine("No runtime diagnostics captured yet.")
                appendLine("Open a title and run source discovery, then reopen this log.")
            } else {
                append(body.takeLast(400_000))
            }
        }
    }

    fun clear(context: Context) {
        install(context)
        flushWriter()
        synchronized(fileLock) {
            runCatching { logFile().writeText("") }
        }
        record("DIAGNOSTICS_CLEARED")
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = throwable.stackTrace
                    .take(36)
                    .joinToString(" | ") { frame ->
                        "${frame.className}.${frame.methodName}:${frame.lineNumber}"
                    }
                appendSync(
                    timestamped(
                        "CRASH thread=${safeText(thread.name, 60)} " +
                            "type=${safeToken(throwable::class.java.simpleName)} " +
                            "message=${safeText(throwable.message.orEmpty(), 220)} ${memoryLabel()} " +
                            "stack=${safeText(stack, 5000)}"
                    )
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun startMainThreadProbe(scanId: Long) {
        activeProbes += scanId
        val runnable = object : Runnable {
            var expected = SystemClock.uptimeMillis() + PROBE_INTERVAL_MS

            override fun run() {
                if (scanId !in activeProbes) return
                val now = SystemClock.uptimeMillis()
                val lateBy = now - expected
                if (lateBy >= STALL_THRESHOLD_MS) {
                    record(
                        "UI_STALL scan=$scanId mainLooperDelay=${lateBy}ms " +
                            "activeProviders=${activeProviders.get()} ${memoryLabel()}"
                    )
                }
                expected = now + PROBE_INTERVAL_MS
                mainHandler.postDelayed(this, PROBE_INTERVAL_MS)
            }
        }
        mainHandler.postDelayed(runnable, PROBE_INTERVAL_MS)
    }

    private fun record(message: String) {
        val line = timestamped(message)
        writer.execute { appendSync(line) }
    }

    private fun appendSync(line: String) {
        val context = appContext ?: return
        synchronized(fileLock) {
            runCatching {
                val file = File(context.noBackupFilesDir, FILE_NAME)
                file.appendText(line + "\n")
                if (file.length() > ROTATE_AT_BYTES) {
                    val bytes = file.readBytes()
                    val tail = bytes.copyOfRange((bytes.size - KEEP_BYTES).coerceAtLeast(0), bytes.size)
                    file.writeBytes(tail)
                }
            }
        }
    }

    private fun logFile(): File {
        val context = requireNotNull(appContext) { "RuntimeDiagnostics is not installed" }
        return File(context.noBackupFilesDir, FILE_NAME)
    }

    private fun flushWriter() {
        runCatching { writer.submit { }.get(800, TimeUnit.MILLISECONDS) }
    }

    private fun timestamped(message: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        return "$stamp | $message"
    }

    private fun memoryLabel(): String {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return "heap=${mb(used)}MB/${mb(runtime.maxMemory())}MB"
    }

    private fun heapUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun mb(bytes: Long): Long = bytes / (1024L * 1024L)

    private fun elapsedMs(startedNs: Long): Long =
        (System.nanoTime() - startedNs) / 1_000_000L

    private fun threadLabel(): String = safeText(Thread.currentThread().name, 60)

    private fun safeToken(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun safeText(value: String, max: Int): String =
        value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("(?i)(authorization|cookie|token|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+")) {
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
