package com.vueo.shared.core.storage

/** Shared eligibility for taste learning, watched filtering and recommendation seeds. */
object LibraryViewingPolicy {
    fun isMeaningfullyWatched(entry: LibraryPlaybackEntry): Boolean = when {
        entry.isCompleted -> true
        entry.durationMs > 0L -> entry.positionMs >= 300_000L && entry.progressFraction >= .20f
        else -> entry.positionMs >= 600_000L
    }
}
