package com.vueo.shared.core.plugin

/** Per-run, bounded evidence owned outside the cancellation/timeout scope. */
internal class ProviderExecutionProgress(
    private val nowNs: () -> Long = System::nanoTime,
) {
    private val startedNs = nowNs()
    private val console = BoundedProviderLog(24)
    private val timeline = BoundedProviderLog(10)
    private data class Pending(val label: String, val startedMs: Long)
    private val pending = linkedMapOf<Long, Pending>()
    private var sequence = 0L
    private var untrackedPending = 0
    private var lastStage = "Provider start"
    private fun elapsedMs() = ((nowNs() - startedNs) / 1_000_000L).coerceAtLeast(0L)

    @Synchronized operator fun plusAssign(message: String) {
        console += message.take(1200)
        timeline += "TIMELINE: +${elapsedMs()}ms ${message.take(280)}"
    }

    @Synchronized fun stage(label: String) {
        lastStage = label.replace('\n', ' ').take(280)
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
        stage("${request?.label ?: "Request"}: ${result.take(120)}")
    }

    @Synchronized fun snapshot(): List<String> = buildList {
        addAll(console.toList())
        addAll(timeline.toList())
        add("LAST STAGE: $lastStage")
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
    fun lastStage(logs: List<String>): String? =
        logs.lastOrNull { it.startsWith("LAST STAGE: ") }?.removePrefix("LAST STAGE: ")
    fun pending(logs: List<String>): List<String> =
        logs.filter { it.startsWith("PENDING: ") }.map { it.removePrefix("PENDING: ") }
    fun timeline(logs: List<String>): List<String> =
        logs.filter { it.startsWith("TIMELINE: ") }.map { it.removePrefix("TIMELINE: ") }
}
