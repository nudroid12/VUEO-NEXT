package com.vueo.mobile.ui

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Counts owned players without retaining their Player, View, Context or media. */
internal class MobilePlayerResourceLease(private val report: (String) -> Unit) {
    private val id = sequence.incrementAndGet()
    private val releaseAttempted = AtomicBoolean(false)

    init {
        outstanding.incrementAndGet()
        emit("created")
    }

    private fun emit(event: String, detail: String = "") {
        runCatching {
            report("PLAYER_RESOURCE platform=Mobile id=$id event=$event outstanding=${outstanding.get()} $detail".trim())
        }
    }

    fun disposalStarted() = emit("dispose_requested")

    /** Each step is isolated: persistence/Trakt failure must never skip codec release. */
    fun release(beforeRelease: () -> Unit, releasePlayer: () -> Unit) {
        if (!releaseAttempted.compareAndSet(false, true)) return
        emit("cleanup_started")
        try {
            try {
                beforeRelease()
            } catch (error: Exception) {
                emit("cleanup_failed", "error=${error.javaClass.simpleName}")
            }
        } finally {
            val started = System.nanoTime()
            emit("release_started")
            try {
                releasePlayer()
                outstanding.decrementAndGet()
                emit("released", "elapsedMs=${(System.nanoTime() - started) / 1_000_000L}")
            } catch (error: Exception) {
                // Keep it outstanding: a failed release is not evidence of freed resources.
                emit("release_failed", "error=${error.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private val sequence = AtomicInteger()
        private val outstanding = AtomicInteger()
    }
}
