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
    private val commentarySubtitleView = SubtitleView(context)
    private var boundPlayer: Player? = null
    private var listenerRegistered = false
    private var lastStyle: TvPlayerSubtitleStyleState? = null
    private var lastBottomPadding = Float.NaN
    private var showCommentary = true
    private var lastInputCues: List<Cue>? = null
    private var lastDisplayedCues: List<Cue> = emptyList()
    private var lastDisplayedCommentaryCues: List<Cue> = emptyList()
    private var lastDisplayCommentary = true
    private var lastNormalBottomLineCount = 0
    private var lastFontSizeSp = 26

    private val captionListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            displayCues(cueGroup.cues)
        }
    }

    init {
        // PlayerView still owns video and its other callbacks; only its caption view is hidden.
        subtitleView?.visibility = View.GONE
        configureSubtitleView(managedSubtitleView)
        configureSubtitleView(commentarySubtitleView)
        addView(
            managedSubtitleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            commentarySubtitleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateCommentaryBottomPadding()
        }
    }

    private fun configureSubtitleView(view: SubtitleView) {
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bindPlayer(next: Player?) {
        if (boundPlayer !== next) {
            unregisterListener()
            boundPlayer = next
            player = next
            managedSubtitleView.setCues(emptyList())
            commentarySubtitleView.setCues(emptyList())
            lastInputCues = null
            lastDisplayedCues = emptyList()
            lastDisplayedCommentaryCues = emptyList()
            lastNormalBottomLineCount = 0
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
        commentarySubtitleView.setCues(emptyList())
        lastInputCues = null
        lastDisplayedCues = emptyList()
        lastDisplayedCommentaryCues = emptyList()
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
            commentarySubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp.toFloat())
            lastFontSizeSp = style.fontSizeSp
        }
        if (previous == null || previous.textColor != style.textColor ||
            previous.bold != style.bold || previous.fontFamily != style.fontFamily || previous.outlineEnabled != style.outlineEnabled ||
            previous.outlineColor != style.outlineColor ||
            previous.backgroundEnabled != style.backgroundEnabled ||
            previous.backgroundColor != style.backgroundColor ||
            previous.backgroundOpacityPercent != style.backgroundOpacityPercent
        ) {
            managedSubtitleView.setStyle(captionStyle)
            commentarySubtitleView.setStyle(captionStyle)
        }
        if (lastBottomPadding != bottomPadding) {
            managedSubtitleView.setBottomPaddingFraction(bottomPadding)
            lastBottomPadding = bottomPadding
        }
        updateCommentaryBottomPadding()
        lastStyle = style
        if (showCommentary != style.showCommentary) {
            showCommentary = style.showCommentary
            // Re-filter the active group immediately, without seeking or restarting playback.
            displayCues(boundPlayer?.currentCues?.cues ?: emptyList())
        }
    }

    private fun displayCues(cues: List<Cue>) {
        if (lastInputCues == cues && lastDisplayCommentary == showCommentary) return

        // Normal captions and commentary deliberately use separate SubtitleViews. This gives
        // the commentary a real vertical gap instead of a fragile blank-line separator.
        val mainDisplayed = tvStackCollidingSubtitleCues(cues, showCommentary = false)
        val commentaryDisplayed = if (showCommentary) {
            tvStackCollidingSubtitleCues(
                cues.filter(::tvIsTaggedSubtitleCommentary),
                showCommentary = true,
            )
        } else {
            emptyList()
        }

        lastInputCues = cues
        lastDisplayCommentary = showCommentary
        lastNormalBottomLineCount = normalBottomLineCount(mainDisplayed)
        updateCommentaryBottomPadding()

        if (lastDisplayedCues != mainDisplayed) {
            managedSubtitleView.setCues(mainDisplayed)
            lastDisplayedCues = mainDisplayed
        }
        if (lastDisplayedCommentaryCues != commentaryDisplayed) {
            commentarySubtitleView.setCues(commentaryDisplayed)
            lastDisplayedCommentaryCues = commentaryDisplayed
        }
    }

    private fun normalBottomLineCount(cues: List<Cue>): Int {
        val lineCount = cues.asSequence()
            .filter { it.bitmap == null && it.verticalType == Cue.TYPE_UNSET && it.line == Cue.DIMEN_UNSET }
            .mapNotNull { it.text?.toString() }
            .maxOfOrNull { text -> text.count { it == '\n' } + 1 }
        return lineCount ?: 0
    }

    private fun updateCommentaryBottomPadding() {
        val base = lastBottomPadding
        if (base.isNaN()) return
        val playerHeight = height
        if (playerHeight <= 0) {
            commentarySubtitleView.setBottomPaddingFraction(base)
            return
        }

        val metrics = resources.displayMetrics
        val fontPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            lastFontSizeSp.toFloat(),
            metrics,
        )
        val gapPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            COMMENTARY_GAP_DP,
            metrics,
        )
        val extraPx = if (lastNormalBottomLineCount > 0) {
            fontPx * SUBTITLE_LINE_HEIGHT_FACTOR * lastNormalBottomLineCount + gapPx
        } else {
            0f
        }
        val extraFraction = extraPx / playerHeight.toFloat()
        commentarySubtitleView.setBottomPaddingFraction((base + extraFraction).coerceAtMost(MAX_COMMENTARY_BOTTOM_PADDING))
    }

    private companion object {
        const val COMMENTARY_GAP_DP = 14f
        const val SUBTITLE_LINE_HEIGHT_FACTOR = 1.25f
        const val MAX_COMMENTARY_BOTTOM_PADDING = 0.55f
    }
}
