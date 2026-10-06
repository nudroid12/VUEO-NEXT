package com.vueo.mobile.ui

import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

/**
 * Own the final Mobile subtitle output so simultaneous bottom cues can be stacked
 * consistently and commentary can be toggled without seeking or restarting playback.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class MobileSubtitlePlayerView(context: Context) : PlayerView(context) {
    private val managedSubtitleView = SubtitleView(context)
    private val commentarySubtitleView = SubtitleView(context)
    private var boundPlayer: Player? = null
    private var listenerRegistered = false
    private var lastStyle: PlayerSubtitleStyleState? = null
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
        displayCues(current.currentCues.cues)
    }

    private fun unregisterListener() {
        if (listenerRegistered) boundPlayer?.removeListener(captionListener)
        listenerRegistered = false
    }

    fun applySubtitlePresentation(
        style: PlayerSubtitleStyleState,
        bottomPadding: Float,
    ) {
        val previous = lastStyle
        if (previous == null || previous.fontSizeSp != style.fontSizeSp) {
            managedSubtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                style.fontSizeSp.toFloat(),
            )
            commentarySubtitleView.setFixedTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                style.fontSizeSp.toFloat(),
            )
            lastFontSizeSp = style.fontSizeSp
        }
        if (
            previous == null ||
            previous.textColor != style.textColor ||
            previous.bold != style.bold ||
            previous.fontFamily != style.fontFamily ||
            previous.outlineEnabled != style.outlineEnabled ||
            previous.outlineColor != style.outlineColor ||
            previous.backgroundEnabled != style.backgroundEnabled ||
            previous.backgroundColor != style.backgroundColor ||
            previous.backgroundOpacityPercent != style.backgroundOpacityPercent
        ) {
            val captionStyle = CaptionStyleCompat(
                style.textColor,
                if (style.backgroundEnabled) {
                    mobileSubtitleWithAlpha(
                        style.backgroundColor,
                        style.backgroundOpacityPercent,
                    )
                } else {
                    android.graphics.Color.TRANSPARENT
                },
                android.graphics.Color.TRANSPARENT,
                if (style.outlineEnabled) {
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE
                } else {
                    CaptionStyleCompat.EDGE_TYPE_NONE
                },
                style.outlineColor,
                com.vueo.shared.core.player.SubtitleFonts.resolve(
                    context,
                    style.fontFamily,
                    style.bold,
                ),
            )
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
            displayCues(boundPlayer?.currentCues?.cues ?: emptyList())
        }
    }

    private fun displayCues(cues: List<Cue>) {
        if (lastInputCues == cues && lastDisplayCommentary == showCommentary) return

        // Use two real caption layers so commentary has an explicit visual gap above
        // the normal subtitle instead of relying on blank lines inside one cue.
        val mainDisplayed = mobileStackCollidingSubtitleCues(cues, showCommentary = false)
        val commentaryDisplayed = if (showCommentary) {
            mobileStackCollidingSubtitleCues(
                cues.filter(::mobileIsTaggedSubtitleCommentary),
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

private fun mobileSubtitleWithAlpha(colour: Int, opacityPercent: Int): Int {
    val alpha = (opacityPercent.coerceIn(0, 100) * 255 + 50) / 100
    return (colour and 0x00FFFFFF) or (alpha shl 24)
}

/**
 * Match the TV renderer: combine simultaneous lower-screen captions. Commentary may be
 * filtered out by the owning PlayerView and rendered in its own lower caption layer.
 * Authored upper/middle placements remain separate.
 */
internal fun mobileStackCollidingSubtitleCues(
    cues: List<Cue>,
    showCommentary: Boolean = true,
): List<Cue> {
    if (cues.isEmpty()) return emptyList()

    val orderedGroups = mutableListOf<MutableList<Cue>>()
    val groups = linkedMapOf<MobileSubtitlePlacement, MutableList<Cue>>()
    val bottomGroup = mutableListOf<Cue>()
    val commentaryGroup = mutableListOf<Cue>()

    for (cue in cues) {
        if (cue.bitmap != null || cue.verticalType != Cue.TYPE_UNSET || cue.text.isNullOrBlank()) {
            orderedGroups.add(mutableListOf(cue))
            continue
        }
        if (mobileIsTaggedSubtitleCommentary(cue)) {
            if (showCommentary) {
                if (commentaryGroup.isEmpty()) orderedGroups.add(commentaryGroup)
                commentaryGroup.add(cue)
            }
            continue
        }
        if (mobileIsBottomSubtitleCue(cue)) {
            if (bottomGroup.isEmpty()) orderedGroups.add(bottomGroup)
            bottomGroup.add(cue)
            continue
        }

        val placement = MobileSubtitlePlacement(
            line = cue.line,
            lineType = if (cue.line == Cue.DIMEN_UNSET) Cue.TYPE_UNSET else cue.lineType,
            lineAnchor = if (cue.line == Cue.DIMEN_UNSET) Cue.TYPE_UNSET else cue.lineAnchor,
            position = cue.position,
            positionAnchor = cue.positionAnchor,
            size = cue.size,
            alignment = cue.textAlignment,
            multiRowAlignment = cue.multiRowAlignment,
            shearDegrees = cue.shearDegrees,
        )
        groups.getOrPut(placement) {
            mutableListOf<Cue>().also { orderedGroups.add(it) }
        }.add(cue)
    }

    return orderedGroups.mapNotNull { group ->
        val unique = group.distinctBy { it.text.toString() }
        when {
            group === commentaryGroup && bottomGroup.isNotEmpty() -> null
            group === bottomGroup && commentaryGroup.isNotEmpty() ->
                mobileBuildBottomSubtitleCue(commentaryGroup, bottomGroup)
            group === bottomGroup || group === commentaryGroup ->
                mobileBuildBottomSubtitleCue(emptyList(), unique)
            unique.size == 1 -> unique.first()
            else -> {
                val builder = unique.first().buildUpon()
                val text = SpannableStringBuilder()
                unique.forEachIndexed { index, item ->
                    if (index > 0) text.append('\n')
                    text.append(requireNotNull(item.text))
                }
                builder.setText(SpannedString(text)).build()
            }
        }
    }
}

private fun mobileBuildBottomSubtitleCue(commentary: List<Cue>, normal: List<Cue>): Cue {
    val commentaryUnique = commentary.distinctBy { it.text.toString() }
    val normalUnique = normal.distinctBy { it.text.toString() }
    val seed = normalUnique.firstOrNull() ?: commentaryUnique.first()
    val text = SpannableStringBuilder()
    commentaryUnique.forEachIndexed { index, item ->
        if (index > 0) text.append('\n')
        text.append(requireNotNull(item.text))
    }
    if (commentaryUnique.isNotEmpty() && normalUnique.isNotEmpty()) text.append("\n\n")
    normalUnique.forEachIndexed { index, item ->
        if (index > 0) text.append('\n')
        text.append(requireNotNull(item.text))
    }
    return seed.buildUpon()
        .setText(SpannedString(text))
        .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
        .setLineAnchor(Cue.TYPE_UNSET)
        .setPosition(Cue.DIMEN_UNSET)
        .setPositionAnchor(Cue.TYPE_UNSET)
        .setSize(Cue.DIMEN_UNSET)
        .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMultiRowAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()
}

internal fun mobileIsTaggedSubtitleCommentary(cue: Cue): Boolean {
    val text = cue.text as? Spanned ?: return false
    val trimmed = text.toString().trim()
    if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return false

    val markers = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        .filter { (it.foregroundColor and 0x00FFFFFF) == 0x00FFFFCC }
    if (markers.isEmpty()) return false

    for (index in text.indices) {
        if (
            !text[index].isWhitespace() &&
            markers.none {
                text.getSpanStart(it) <= index && text.getSpanEnd(it) > index
            }
        ) {
            return false
        }
    }
    return true
}

private fun mobileIsBottomSubtitleCue(cue: Cue): Boolean = when {
    cue.line == Cue.DIMEN_UNSET -> true
    cue.lineType == Cue.LINE_TYPE_FRACTION -> cue.line >= .70f
    cue.lineType == Cue.LINE_TYPE_NUMBER -> cue.line in -3f..-1f
    else -> false
}

private data class MobileSubtitlePlacement(
    val line: Float,
    val lineType: Int,
    val lineAnchor: Int,
    val position: Float,
    val positionAnchor: Int,
    val size: Float,
    val alignment: Layout.Alignment?,
    val multiRowAlignment: Layout.Alignment?,
    val shearDegrees: Float,
)
