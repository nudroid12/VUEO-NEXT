package com.vueo.tv.player

import android.content.Context
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
    private var lastNormalBottomText: CharSequence? = null
    private var lastNormalFontSizeSp = 26
    private var upperLayerUsesCommentarySize = true

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
            lastNormalBottomText = null
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
        lastNormalBottomText = null
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
            lastNormalFontSizeSp = style.fontSizeSp
        }
        if (
            previous == null ||
            previous.commentaryFontSizeSp != style.commentaryFontSizeSp ||
            previous.fontSizeSp != style.fontSizeSp
        ) {
            applyUpperLayerTextSize(style)
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
        lastStyle = style
        updateCommentaryBottomPadding()
        if (showCommentary != style.showCommentary) {
            showCommentary = style.showCommentary
            // Re-filter the active group immediately, without seeking or restarting playback.
            displayCues(boundPlayer?.currentCues?.cues ?: emptyList())
        }
    }

    private fun displayCues(cues: List<Cue>) {
        if (lastInputCues == cues && lastDisplayCommentary == showCommentary) return

        // Separate simultaneous lower-screen subtitle layers by occupancy, not only by
        // commentary metadata. Tagged commentary still gets Commentary Size, but two
        // ordinary tracks (for example original + translated addon subtitles) are also
        // rendered as distinct lower/upper layers with the same collision gap.
        val layers = splitLowerSubtitleLayers(cues, showCommentary)
        val mainDisplayed = tvStackCollidingSubtitleCues(layers.lowerCues, showCommentary = false)
        val upperDisplayed = tvStackCollidingSubtitleCues(layers.upperCues, showCommentary = true)

        lastInputCues = cues
        lastDisplayCommentary = showCommentary
        if (upperLayerUsesCommentarySize != layers.upperUsesCommentarySize) {
            upperLayerUsesCommentarySize = layers.upperUsesCommentarySize
            lastStyle?.let(::applyUpperLayerTextSize)
        }
        lastNormalBottomLineCount = normalBottomLineCount(mainDisplayed)
        lastNormalBottomText = normalBottomText(mainDisplayed)
        updateCommentaryBottomPadding()

        if (lastDisplayedCues != mainDisplayed) {
            managedSubtitleView.setCues(mainDisplayed)
            lastDisplayedCues = mainDisplayed
        }
        if (lastDisplayedCommentaryCues != upperDisplayed) {
            commentarySubtitleView.setCues(upperDisplayed)
            lastDisplayedCommentaryCues = upperDisplayed
        }
    }

    private fun splitLowerSubtitleLayers(
        cues: List<Cue>,
        commentaryEnabled: Boolean,
    ): TvSubtitleLayerSplit {
        val nonLower = mutableListOf<Cue>()
        val lowerNormal = mutableListOf<Cue>()
        val taggedCommentary = mutableListOf<Cue>()

        for (cue in cues) {
            if (cue.bitmap == null && cue.verticalType == Cue.TYPE_UNSET && !cue.text.isNullOrBlank()) {
                // Preserve authored commentary metadata regardless of placement.
                if (tvIsTaggedSubtitleCommentary(cue)) {
                    taggedCommentary += cue
                    continue
                }
                if (isLowerTextCue(cue)) {
                    lowerNormal += cue
                    continue
                }
            }
            nonLower += cue
        }

        val normalUnique = lowerNormal.distinctBy { it.text.toString() }
        val taggedUnique = taggedCommentary.distinctBy { it.text.toString() }
        val activeLowerCount = normalUnique.size + taggedUnique.count(::isLowerTextCue)
        val parentheticalCommentary = if (activeLowerCount >= 2) {
            normalUnique.filter(::isFullyParenthesizedCue)
        } else {
            emptyList()
        }
        val parentheticalTexts = parentheticalCommentary
            .map { it.text.toString() }
            .toSet()
        val remainingNormal = normalUnique.filterNot { it.text.toString() in parentheticalTexts }
        val commentaryUnique = (taggedUnique + parentheticalCommentary)
            .distinctBy { it.text.toString() }
            .let { if (commentaryEnabled) it else emptyList() }

        return when {
            commentaryUnique.isNotEmpty() -> TvSubtitleLayerSplit(
                lowerCues = nonLower + remainingNormal,
                upperCues = commentaryUnique,
                upperUsesCommentarySize = true,
            )
            remainingNormal.size >= 2 -> TvSubtitleLayerSplit(
                // When there is no commentary role, retain generic dual-layer spacing.
                lowerCues = nonLower + remainingNormal.last(),
                upperCues = remainingNormal.dropLast(1),
                upperUsesCommentarySize = false,
            )
            else -> TvSubtitleLayerSplit(
                lowerCues = nonLower + remainingNormal,
                upperCues = emptyList(),
                upperUsesCommentarySize = false,
            )
        }
    }

    private fun isFullyParenthesizedCue(cue: Cue): Boolean {
        val value = cue.text?.toString()?.trim().orEmpty()
        if (value.length < 2 || value.first() != '(' || value.last() != ')') return false

        var depth = 0
        value.forEachIndexed { index, char ->
            when (char) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth < 0) return false
                }
            }
            // The outer pair must wrap the whole cue. A closing parenthesis followed by
            // dialogue means this is an ordinary subtitle, not commentary.
            if (depth == 0 && index < value.lastIndex) return false
        }
        return depth == 0
    }

    private fun isLowerTextCue(cue: Cue): Boolean = when {
        cue.line == Cue.DIMEN_UNSET -> true
        cue.lineType == Cue.LINE_TYPE_FRACTION -> cue.line >= .70f
        cue.lineType == Cue.LINE_TYPE_NUMBER -> cue.line in -3f..-1f
        else -> false
    }

    private fun applyUpperLayerTextSize(style: TvPlayerSubtitleStyleState) {
        val sizeSp = if (upperLayerUsesCommentarySize) {
            style.commentaryFontSizeSp
        } else {
            style.fontSizeSp
        }
        commentarySubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    }

    private fun normalBottomLineCount(cues: List<Cue>): Int {
        val lineCount = cues.asSequence()
            .filter { it.bitmap == null && it.verticalType == Cue.TYPE_UNSET && it.line == Cue.DIMEN_UNSET }
            .mapNotNull { it.text?.toString() }
            .maxOfOrNull { text -> text.count { it == '\n' } + 1 }
        return lineCount ?: 0
    }

    private fun normalBottomText(cues: List<Cue>): CharSequence? = cues.asSequence()
        .filter { it.bitmap == null && it.verticalType == Cue.TYPE_UNSET && it.line == Cue.DIMEN_UNSET }
        .mapNotNull { it.text }
        .maxByOrNull { it.length }

    private fun measuredNormalBottomHeightPx(): Float {
        val text = lastNormalBottomText?.takeIf { it.isNotBlank() } ?: return 0f
        val metrics = resources.displayMetrics
        val fontPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            lastNormalFontSizeSp.toFloat(),
            metrics,
        )
        val fallback = fontPx * SUBTITLE_LINE_HEIGHT_FACTOR * lastNormalBottomLineCount.coerceAtLeast(1)
        val availableWidthPx = (width * SUBTITLE_MEASURE_WIDTH_FRACTION).toInt()
        if (availableWidthPx <= 0) return fallback

        val subtitleStyle = lastStyle
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface = com.vueo.shared.core.player.SubtitleFonts.resolve(
                context,
                subtitleStyle?.fontFamily ?: "default",
                subtitleStyle?.bold ?: false,
            )
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, availableWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()
        return maxOf(layout.height.toFloat(), fallback)
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
        val gapPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            COMMENTARY_GAP_DP,
            metrics,
        )
        val extraPx = if (lastNormalBottomLineCount > 0) {
            measuredNormalBottomHeightPx() + gapPx
        } else {
            0f
        }
        val extraFraction = extraPx / playerHeight.toFloat()
        commentarySubtitleView.setBottomPaddingFraction((base + extraFraction).coerceAtMost(MAX_COMMENTARY_BOTTOM_PADDING))
    }

    private data class TvSubtitleLayerSplit(
        val lowerCues: List<Cue>,
        val upperCues: List<Cue>,
        val upperUsesCommentarySize: Boolean,
    )

    private companion object {
        const val COMMENTARY_GAP_DP = 28f
        const val SUBTITLE_LINE_HEIGHT_FACTOR = 1.35f
        const val SUBTITLE_MEASURE_WIDTH_FRACTION = 0.90f
        const val MAX_COMMENTARY_BOTTOM_PADDING = 0.55f
    }
}
