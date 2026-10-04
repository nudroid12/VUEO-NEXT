package com.vueo.tv.player

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics

/** Own the final caption output so raw PlayerView callbacks cannot undo cue stacking. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvSubtitlePlayerView(context: Context) : PlayerView(context) {
    val managedSubtitleView = SubtitleView(context)
    private var boundPlayer: Player? = null
    private var listenerRegistered = false
    private var lastLayoutLogMs = -5_000L

    private val captionListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            displayCues(cueGroup.cues)
        }
    }

    init {
        // PlayerView still owns video and its other callbacks; only its caption view is hidden.
        subtitleView?.visibility = View.GONE
        managedSubtitleView.isFocusable = false
        managedSubtitleView.isFocusableInTouchMode = false
        managedSubtitleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(
            managedSubtitleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun bindPlayer(next: Player?) {
        if (boundPlayer !== next) {
            unregisterListener()
            boundPlayer = next
            player = next
            managedSubtitleView.setCues(emptyList())
            lastLayoutLogMs = -5_000L
        }
        subtitleView?.visibility = View.GONE
        registerListener()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerListener()
    }

    override fun onDetachedFromWindow() {
        unregisterListener()
        managedSubtitleView.setCues(emptyList())
        super.onDetachedFromWindow()
    }

    private fun registerListener() {
        val current = boundPlayer ?: return
        if (listenerRegistered) return
        current.addListener(captionListener)
        listenerRegistered = true
        // Also normalize the cached group when attaching or switching sources mid-cue.
        displayCues(current.currentCues.cues, forceLog = true)
    }

    private fun unregisterListener() {
        if (listenerRegistered) boundPlayer?.removeListener(captionListener)
        listenerRegistered = false
    }

    private fun displayCues(cues: List<Cue>, forceLog: Boolean = false) {
        val displayed = tvStackCollidingSubtitleCues(cues)
        managedSubtitleView.setCues(displayed)
        val now = SystemClock.elapsedRealtime()
        if (forceLog || (cues.size > 1 && now - lastLayoutLogMs >= 5_000L)) {
            lastLayoutLogMs = now
            RuntimeDiagnostics.recordSubtitleLayout(
                platform = "TV",
                positionMs = boundPlayer?.currentPosition ?: 0L,
                incomingCues = cues.size,
                displayedCues = displayed.size,
                unpositionedTextCues = cues.count {
                    it.bitmap == null && !it.text.isNullOrBlank() && it.line == Cue.DIMEN_UNSET
                },
            )
        }
    }
}
