package com.vueo.tv.player

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

/** Own the final caption output so raw PlayerView callbacks cannot undo cue stacking. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvSubtitlePlayerView(context: Context) : PlayerView(context) {
    val managedSubtitleView = SubtitleView(context)
    private var boundPlayer: Player? = null
    private var listenerRegistered = false
    private var lastStyle: TvPlayerSubtitleStyleState? = null
    private var lastBottomPadding = Float.NaN
    private var showCommentary = true
    private var lastInputCues: List<Cue>? = null
    private var lastDisplayedCues: List<Cue> = emptyList()
    private var lastDisplayCommentary = true

    private val captionListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            displayCues(cueGroup.cues)
        }
    }

    init {
        // PlayerView still owns video and its other callbacks; only its caption view is hidden.
        subtitleView?.visibility = View.GONE
        managedSubtitleView.setApplyEmbeddedStyles(false)
        managedSubtitleView.setApplyEmbeddedFontSizes(false)
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
            lastInputCues = null
            lastDisplayedCues = emptyList()
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
        lastInputCues = null
        lastDisplayedCues = emptyList()
        super.onDetachedFromWindow()
    }

    private fun registerListener() {
        val current = boundPlayer ?: return
        if (listenerRegistered) return
        current.addListener(captionListener)
        listenerRegistered = true
        // Also normalize the cached group when attaching or switching sources mid-cue.
        displayCues(current.currentCues.cues)
    }

    private fun unregisterListener() {
        if (listenerRegistered) boundPlayer?.removeListener(captionListener)
        listenerRegistered = false
    }

    /** Apply user preferences only when they change, not on progress/focus recompositions. */
    fun applySubtitlePresentation(
        style: TvPlayerSubtitleStyleState,
        captionStyle: CaptionStyleCompat,
        bottomPadding: Float,
    ) {
        val previous = lastStyle
        if (previous == null || previous.fontSizeSp != style.fontSizeSp) {
            managedSubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp.toFloat())
        }
        if (previous == null || previous.textColor != style.textColor ||
            previous.bold != style.bold || previous.outlineEnabled != style.outlineEnabled ||
            previous.outlineColor != style.outlineColor ||
            previous.backgroundEnabled != style.backgroundEnabled ||
            previous.backgroundColor != style.backgroundColor ||
            previous.backgroundOpacityPercent != style.backgroundOpacityPercent
        ) {
            managedSubtitleView.setStyle(captionStyle)
        }
        if (lastBottomPadding != bottomPadding) {
            managedSubtitleView.setBottomPaddingFraction(bottomPadding)
            lastBottomPadding = bottomPadding
        }
        lastStyle = style
        if (showCommentary != style.showCommentary) {
            showCommentary = style.showCommentary
            // Re-filter the active group immediately, without seeking or restarting playback.
            displayCues(boundPlayer?.currentCues?.cues ?: emptyList())
        }
    }

    private fun displayCues(cues: List<Cue>) {
        if (lastInputCues == cues && lastDisplayCommentary == showCommentary) return
        val displayed = tvStackCollidingSubtitleCues(cues, showCommentary)
        lastInputCues = cues
        lastDisplayCommentary = showCommentary
        if (lastDisplayedCues != displayed) {
            managedSubtitleView.setCues(displayed)
            lastDisplayedCues = displayed
        }
    }
}
