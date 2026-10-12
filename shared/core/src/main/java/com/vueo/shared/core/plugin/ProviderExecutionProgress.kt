package com.vueo.shared.core.plugin

/** Per-run, bounded evidence owned outside the cancellation/timeout scope. */
internal class ProviderExecutionProgress(
    private val nowNs: () -> Long = System::nanoTime,
    private val diagnosticSink: ((String) -> Unit)? = null,
) {
    private val startedNs = nowNs()
    private val console = BoundedProviderLog(24)
    private val timeline = BoundedProviderLog(10)
    private data class Pending(val label: String, val startedMs: Long)
    private val pending = linkedMapOf<Long, Pending>()
    private var sequence = 0L
    private var untrackedPending = 0
    private var lastStage = "Provider start"
    private var lastCompleted: String? = null
    private fun elapsedMs() = ((nowNs() - startedNs) / 1_000_000L).coerceAtLeast(0L)

    @Synchronized operator fun plusAssign(message: String) {
        diagnosticSink?.invoke(message.take(1200))
        console += message.take(1200)
    }

    @Synchronized fun diagnostic(message: String) { diagnosticSink?.invoke(message) }

    @Synchronized fun stage(label: String) {
        lastStage = label.replace('\n', ' ').take(280)
        diagnosticSink?.invoke("STAGE elapsed=${elapsedMs()}ms $lastStage")
        timeline += "TIMELINE: +${elapsedMs()}ms $lastStage"
    }

    @Synchronized fun begin(label: String): Long {
        val id = ++sequence
        val safeLabel = label.replace('\n', ' ').take(280)
        if (pending.size < 32) pending[id] = Pending(safeLabel, elapsedMs())
        else untrackedPending++
        stage("Started $safeLabel")
        return id
    }

    @Synchronized fun finish(id: Long, result: String) {
        val request = pending.remove(id)
        if (request == null) untrackedPending = (untrackedPending - 1).coerceAtLeast(0)
        if (request != null && result == "completed") lastCompleted = request.label
        stage("${request?.label ?: "Request"}: ${result.take(120)}")
    }

    @Synchronized fun snapshot(): List<String> = buildList {
        addAll(console.toList())
        addAll(timeline.toList())
        add("LAST STAGE: $lastStage")
        lastCompleted?.let { add("LAST COMPLETED: $it") }
        val atMs = elapsedMs()
        pending.values.take(4).forEach {
            add("PENDING: ${it.label} • waiting ${atMs - it.startedMs}ms")
        }
        val omitted = (pending.size - 4).coerceAtLeast(0) + untrackedPending
        if (omitted > 0) add("PENDING: $omitted additional requests still waiting")
    }
}

/** Backwards compatible with persisted diagnostic logs from older versions. */
object ProviderDiagnosticProgress {
    fun timeoutEvidence(logs: List<String>): ProviderTimeoutEvidence {
        val completed = logs.lastOrNull { it.startsWith("LAST COMPLETED: ") }
            ?.removePrefix("LAST COMPLETED: ")
            ?: timeline(logs).lastOrNull { it.endsWith(": completed") }
                ?.substringAfter("ms ")?.removeSuffix(": completed")
        val waiting = pending(logs).firstOrNull { it.contains(" • waiting ") && it.endsWith("ms") }
        val observed = lastStage(logs)?.takeIf { it.isNotBlank() }
        val duration = waiting?.substringAfterLast(" • waiting ")?.removeSuffix("ms")?.toLongOrNull()
        return ProviderTimeoutEvidence(
            completed?.takeIf { it.isNotBlank() } ?: "Unknown",
            waiting?.substringBeforeLast(" • waiting ")?.takeIf { it.isNotBlank() }
                ?: observed?.let { "Unknown • last observed: $it" } ?: "Unknown",
            duration?.coerceAtLeast(0L),
        )
    }

    /** Formats captured lines once. Timestamps are shown only when the source recorded them. */
    fun rawLog(logs: List<String>, error: String?): List<String> = buildList {
        error?.takeIf { it.isNotBlank() && "ERROR: $it" !in logs }?.let { add("[ERROR] $it") }
        logs.forEach { line ->
            add(when {
                line.startsWith("TIMELINE: ") -> {
                    val event = line.removePrefix("TIMELINE: ")
                    "${event.substringBefore(' ')}  TRACE  ${event.substringAfter(' ', "") }"
                }
                line.startsWith("LOG: ") -> "[LOG] " + line.removePrefix("LOG: ")
                line.startsWith("ERROR: ") -> "[ERROR] " + line.removePrefix("ERROR: ")
                line.startsWith("WARN: ") -> "[WARN] " + line.removePrefix("WARN: ")
                line.startsWith("PENDING: ") -> "[WAITING] " + line.removePrefix("PENDING: ")
                line.startsWith("LAST STAGE: ") -> "[LAST STAGE] " + line.removePrefix("LAST STAGE: ")
                line.startsWith("LAST COMPLETED: ") -> "[LAST COMPLETED] " + line.removePrefix("LAST COMPLETED: ")
                else -> line
            })
        }
    }

    fun lastStage(logs: List<String>): String? =
        logs.lastOrNull { it.startsWith("LAST STAGE: ") }?.removePrefix("LAST STAGE: ")
    fun pending(logs: List<String>): List<String> =
        logs.filter { it.startsWith("PENDING: ") }.map { it.removePrefix("PENDING: ") }
    fun timeline(logs: List<String>): List<String> =
        logs.filter { it.startsWith("TIMELINE: ") }.map { it.removePrefix("TIMELINE: ") }
}


/** Observed checkpoint only; never infers a successful step from an arbitrary console message. */
data class ProviderTimeoutEvidence(
    val lastCompleted: String,
    val stoppedAt: String,
    val waitingMs: Long?,
)
