package com.vueo.mobile.ui

/** Main-thread playback intent across visibility changes, scoped to one player. */
internal class MobilePlayerLifecycleState(
    initiallyForeground: Boolean,
    initiallyPlayWhenReady: Boolean,
) {
    var foreground: Boolean = initiallyForeground
        private set
    private var resumePending = !initiallyForeground && initiallyPlayWhenReady

    /** False for duplicate stop notifications, so the saved intent is not overwritten by pause(). */
    fun stop(playWhenReady: Boolean): Boolean {
        if (!foreground) return false
        resumePending = playWhenReady
        foreground = false
        return true
    }

    /** Consume once; prompts, completed playback and exit always veto automatic resume. */
    fun start(allowResume: Boolean): Boolean {
        if (foreground) return false
        foreground = true
        val resume = resumePending && allowResume
        resumePending = false
        return resume
    }
}
