package com.vueo.tv.player

import com.vueo.shared.core.player.LiveSmartSubs
import com.vueo.shared.core.diagnostics.PerformanceDiagnostics
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
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
    private var smartSubsBinding: LiveSmartSubs? = null
    private val smartSubsCues: (List<Cue>) -> Unit = { displayCues(it) }
    private var boundContentKey: String? = null
    private var flowObserver: TvSubtitleFlowObserver? = null
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

    private var layoutRequestKey: String? = null
    private var layoutGeneration = 0
    private var layoutAnalysisFailed = false
    private var layoutReady = false
    private var protectedTimelineText = emptySet<String>()
    private var timelineRegions = emptyList<TvSubtitleTemporalPolicy.Region>()
    private var infoLayerActive = false
    private var infoReservedHeightPx = 0f
    private var infoMeasureKey: List<Any?>? = null
    private var subtitleLayoutDelayMs = 0L
    private var layoutScope: kotlinx.coroutines.CoroutineScope? = null

    private fun prepareTimelineLayout() {
        val current = boundPlayer ?: return
        val request = TvSubtitleTimelineLoader.request(current)
        if (request == null) {
            if (layoutRequestKey != null) resetTimelineLayout()
            return
        }
        if (request.key == layoutRequestKey) return
        resetTimelineLayout()
        layoutRequestKey = request.key
        val version = layoutGeneration
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
        layoutScope = scope
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { TvSubtitleTimelineLoader.parse(request) }.getOrNull()
            }
            if (version != layoutGeneration || boundPlayer !== current) return@launch
            timelineRegions = result?.regions.orEmpty()
            protectedTimelineText = result?.protectedText.orEmpty()
            layoutAnalysisFailed = result == null
            layoutReady = true
            PerformanceDiagnostics.captureRuntimeEvent("TV_SUBTITLE_TIMELINE regions=${timelineRegions.size} protected=${protectedTimelineText.size} cached=true parsed=${result != null}")
            refreshRenderedCues()
        }
    }

    private fun resetTimelineLayout() {
        layoutGeneration++
        layoutScope?.cancel()
        layoutScope = null
        layoutRequestKey = null
        timelineRegions = emptyList()
        protectedTimelineText = emptySet()
        layoutReady = false
        layoutAnalysisFailed = false
        lastInputCues = null
        infoLayerActive = false
        infoMeasureKey = null
    }

    private var cueDiagnosticGroups = 0
    private var upperBottomPadding = Float.NaN
    private val captionListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            if (boundPlayer?.let(LiveSmartSubs::ownsCues) != true) displayCues(cueGroup.cues)
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
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                refreshRenderedCues()
            } else updateCommentaryBottomPadding()
        }
    }

    private fun configureSubtitleView(view: SubtitleView) {
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bindPlayer(next: Player?, contentKey: String) {
        val contentChanged = boundContentKey != contentKey
        if (boundPlayer !== next || contentChanged) {
            // Retain a frame only for updates to this video. A genuine source or
            // episode change must clear the previous picture before rebinding.
            setKeepContentOnPlayerReset(false)
            if (contentChanged && boundPlayer === next) player = null
            unregisterListener()
            boundPlayer = next
            boundContentKey = contentKey
            player = next
            setKeepContentOnPlayerReset(true)
            managedSubtitleView.setCues(emptyList())
            commentarySubtitleView.setCues(emptyList())
            lastInputCues = null
            lastDisplayedCues = emptyList()
            lastDisplayedCommentaryCues = emptyList()
            cueDiagnosticGroups = 0
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
        flowObserver = (current as? androidx.media3.exoplayer.ExoPlayer)?.let { TvSubtitleFlowObserver(it) }
        // Also normalize the cached group when attaching or switching sources mid-cue.
        smartSubsBinding = LiveSmartSubs.get(current)
        smartSubsBinding?.addCueListener(smartSubsCues)
        displayCues(LiveSmartSubs.cues(current))
    }

    private fun unregisterListener() {
        resetTimelineLayout()
        smartSubsBinding?.removeCueListener(smartSubsCues)
        smartSubsBinding = null
        if (listenerRegistered) boundPlayer?.removeListener(captionListener)
        flowObserver?.close()
        flowObserver = null
        listenerRegistered = false
    }

    /** Apply user preferences only when they change, not on progress/focus recompositions. */
    fun applySubtitlePresentation(
        style: TvPlayerSubtitleStyleState,
        bottomPadding: Float,
        subtitleDelayMs: Long = 0L,
    ) {
        val delayChanged = subtitleLayoutDelayMs != subtitleDelayMs
        subtitleLayoutDelayMs = subtitleDelayMs
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
            // Derive the drawable payload from the same immutable value used
            // by the change guard and lastStyle. A caller cannot supply a stale
            // CaptionStyleCompat alongside a newer UI preference snapshot.
            val backgroundColor = if (style.backgroundEnabled) {
                val alpha = (255 * style.backgroundOpacityPercent.coerceIn(0, 100) / 100) shl 24
                (style.backgroundColor and 0x00FFFFFF) or alpha
            } else android.graphics.Color.TRANSPARENT
            val captionStyle = CaptionStyleCompat(
                style.textColor,
                backgroundColor,
                android.graphics.Color.TRANSPARENT,
                if (style.outlineEnabled) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                style.outlineColor,
                com.vueo.shared.core.player.SubtitleFonts.resolve(context, style.fontFamily, style.bold),
            )
            managedSubtitleView.setStyle(captionStyle)
            commentarySubtitleView.setStyle(captionStyle)
        }
        val paddingChanged = lastBottomPadding != bottomPadding
        if (paddingChanged) {
            managedSubtitleView.setBottomPaddingFraction(bottomPadding)
            lastBottomPadding = bottomPadding
        }
        val presentationChanged = previous != null && previous != style
        if (previous != style) {
            PerformanceDiagnostics.captureRuntimeEvent(
                "TV_SUBTITLE_STYLE_APPLIED font=${style.fontFamily} bold=${style.bold} " +
                    "color=${Integer.toHexString(style.textColor)} outline=${style.outlineEnabled} " +
                    "background=${style.backgroundEnabled}",
            )
        }
        lastStyle = style
        updateCommentaryBottomPadding()
        if (showCommentary != style.showCommentary) {
            showCommentary = style.showCommentary
            // Re-filter the active group immediately, without seeking or restarting playback.
            displayCues(currentPresentationCues())
        } else if (presentationChanged || paddingChanged || delayChanged) {
            refreshRenderedCues()
        }
    }

    /** Resolve captions from their active owner; native currentCues is empty for AI. */
    private fun currentPresentationCues(): List<Cue> {
        val current = boundPlayer ?: return emptyList()
        return if (LiveSmartSubs.ownsCues(current)) {
            LiveSmartSubs.cues(current)
        } else {
            lastInputCues ?: current.currentCues.cues
        }
    }

    private fun refreshRenderedCues() {
        // Force the currently visible cue layout through SubtitleView again. Media3
        // otherwise may keep the previous glyph/background layout until the next cue.
        managedSubtitleView.setCues(emptyList())
        commentarySubtitleView.setCues(emptyList())
        // Rebuild from the current raw group, including when video is paused.
        // Keep filtering/stacking and the two visible layers in the same pass.
        val currentCues = currentPresentationCues()
        lastInputCues = null
        lastDisplayedCues = emptyList()
        lastDisplayedCommentaryCues = emptyList()
        displayCues(currentCues)
        managedSubtitleView.postInvalidateOnAnimation()
        commentarySubtitleView.postInvalidateOnAnimation()
    }

    private fun isSmartSubsLayoutPending(): Boolean =
        layoutRequestKey != null && !layoutReady &&
            boundPlayer?.let(LiveSmartSubs::ownsCues) == true

    private fun displayCues(cues: List<Cue>) {
        prepareTimelineLayout()
        if (layoutRequestKey != null && !layoutReady && !isSmartSubsLayoutPending()) {
            // Native tracks retain their existing source-position fallback.
            // AI automatic cues continue through split/stack/padding below instead
            // of appearing once at raw coordinates before the analysis refresh.
            val visible = if (!showCommentary && cues.size >= 2) cues.filterNot {
                TvSubtitleTimelineLoader.isAutomaticText(it) && isFullyParenthesizedCue(it)
            } else cues
            managedSubtitleView.setCues(visible)
            commentarySubtitleView.setCues(emptyList())
            // Retain the raw group for the refresh when analysis completes.
            // refreshRenderedCues clears the equality guard before rebuilding it.
            lastInputCues = cues.toList()
            return
        }
        if (lastInputCues == cues && lastDisplayCommentary == showCommentary) return

        // Separate simultaneous lower-screen subtitle layers by occupancy, not only by
        // the global parenthetical commentary rule. A full (...) cue gets Commentary Size, while two
        // ordinary tracks (for example original + translated addon subtitles) are also
        // rendered as distinct lower/upper layers with the same collision gap.
        val auditGroup = if (cues.size >= 2 && cueDiagnosticGroups < 24 && TvSubtitleCueDiagnostics.enabled()) {
            ++cueDiagnosticGroups
        } else 0
        if (auditGroup > 0) TvSubtitleCueDiagnostics.capture(
            boundPlayer?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty(), auditGroup, "input", cues,
            "viewport=${width}x$height font=$lastNormalFontSizeSp pad=$lastBottomPadding overlay=${boundPlayer?.let(LiveSmartSubs::ownsCues) == true}",
        )
        val clockUs = ((boundPlayer?.currentPosition ?: 0L) - subtitleLayoutDelayMs) * 1_000L
        val region = timelineRegions.firstOrNull { clockUs >= it.startUs && clockUs < it.endUs }
        val automatic = cues.filter(::isLowerTextCue)
        val info = if (region != null) automatic.filter { it.text.toString() in region.info } else emptyList()
        val dialogue = if (region != null) automatic.filter { it.text.toString() in region.dialogue } else emptyList()
        // Do not reinterpret an ambiguous group, or override the commentary rule.
        infoLayerActive = info.isNotEmpty() && automatic.all { it in info || it in dialogue } &&
            automatic.none(::isFullyParenthesizedCue)
        val measureKey = listOf(region, lastStyle?.fontSizeSp, lastStyle?.fontFamily, lastStyle?.bold, width, height)
        if (infoLayerActive && region != null && infoMeasureKey != measureKey) {
            infoReservedHeightPx = region.dialogue.maxOfOrNull { measuredNormalBottomHeightPx(it) } ?: 0f
            infoMeasureKey = measureKey
        }
        val layers = if (infoLayerActive) TvSubtitleLayerSplit(
            lowerCues = cues.filterNot { it in info }, upperCues = info,
            upperUsesCommentarySize = false,
        ) else splitLowerSubtitleLayers(cues, showCommentary)
        val effectiveProtected = if ((layoutReady && !layoutAnalysisFailed) || isSmartSubsLayoutPending()) {
            protectedTimelineText
        } else {
            protectedTimelineText + cues.filter { it.line != Cue.DIMEN_UNSET }.map { it.text.toString() }
        }
        val stackedMain = tvStackCollidingSubtitleCues(layers.lowerCues, effectiveProtected)
        val stackedUpper = tvStackCollidingSubtitleCues(layers.upperCues, effectiveProtected)

        lastInputCues = cues
        lastDisplayCommentary = showCommentary
        if (upperLayerUsesCommentarySize != layers.upperUsesCommentarySize) {
            upperLayerUsesCommentarySize = layers.upperUsesCommentarySize
            lastStyle?.let(::applyUpperLayerTextSize)
        }
        lastNormalBottomLineCount = normalBottomLineCount(stackedMain)
        lastNormalBottomText = normalBottomText(stackedMain)
        updateCommentaryBottomPadding()
        // Authored positions are immutable. The old collision solver moved signs.
        val mainDisplayed = stackedMain
        val upperDisplayed = stackedUpper

        if (auditGroup > 0) {
            val source = boundPlayer?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            TvSubtitleCueDiagnostics.capture(source, auditGroup, "main", mainDisplayed,
                "pad=$lastBottomPadding font=$lastNormalFontSizeSp")
            TvSubtitleCueDiagnostics.capture(source, auditGroup, "upper", upperDisplayed,
                "pad=$upperBottomPadding commentary=${layers.upperUsesCommentarySize} info=$infoLayerActive font=${if (layers.upperUsesCommentarySize) lastStyle?.commentaryFontSizeSp else lastNormalFontSizeSp}")
        }

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

        for (cue in cues) {
            if (
                cue.bitmap == null &&
                cue.verticalType == Cue.TYPE_UNSET &&
                !cue.text.isNullOrBlank() &&
                isLowerTextCue(cue)
            ) {
                lowerNormal += cue
                continue
            }
            nonLower += cue
        }

        val normalUnique = lowerNormal.distinctBy { it.text.toString() }
        // Global commentary rule: when two or more lower subtitle cues are active,
        // only a cue whose entire trimmed text is wrapped by one outer (...) pair
        // is commentary. Embedded colour/style metadata is deliberately ignored.
        val parentheticalCommentary = if (normalUnique.size >= 2) {
            normalUnique.filter(::isFullyParenthesizedCue)
        } else {
            emptyList()
        }
        val parentheticalTexts = parentheticalCommentary
            .map { it.text.toString() }
            .toSet()
        val remainingNormal = normalUnique.filterNot { it.text.toString() in parentheticalTexts }
        val commentaryUnique = parentheticalCommentary
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

    private fun isLowerTextCue(cue: Cue): Boolean = cue.text.toString() !in protectedTimelineText &&
        TvSubtitleTimelineLoader.isAutomaticText(cue) && (
            cue.line == Cue.DIMEN_UNSET || (layoutReady && !layoutAnalysisFailed) ||
                isSmartSubsLayoutPending()
            )

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

    private fun measuredNormalBottomHeightPx(value: CharSequence? = lastNormalBottomText): Float {
        val text = value?.takeIf { it.isNotBlank() } ?: return 0f
        val metrics = resources.displayMetrics
        val fontPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            lastNormalFontSizeSp.toFloat(),
            metrics,
        )
        val fallback = fontPx * SUBTITLE_LINE_HEIGHT_FACTOR * (text.count { it == '\n' } + 1).coerceAtLeast(1)
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
            upperBottomPadding = base
            commentarySubtitleView.setBottomPaddingFraction(base)
            return
        }

        val metrics = resources.displayMetrics
        val gapPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            COMMENTARY_GAP_DP,
            metrics,
        )
        val extraPx = if (infoLayerActive) {
            infoReservedHeightPx + gapPx
        } else if (lastNormalBottomLineCount > 0) {
            measuredNormalBottomHeightPx() + gapPx
        } else {
            0f
        }
        val extraFraction = extraPx / playerHeight.toFloat()
        upperBottomPadding = ((if (infoLayerActive) maxOf(.18f, (lastStyle?.bottomPaddingPercent ?: 9) / 100f) else base) + extraFraction).coerceAtMost(MAX_COMMENTARY_BOTTOM_PADDING)
        commentarySubtitleView.setBottomPaddingFraction(upperBottomPadding)
    }

    private data class TvSubtitleLayerSplit(
        val lowerCues: List<Cue>,
        val upperCues: List<Cue>,
        val upperUsesCommentarySize: Boolean,
    )

    private companion object {
        const val COMMENTARY_GAP_DP = 12f
        const val SUBTITLE_LINE_HEIGHT_FACTOR = 1.35f
        const val SUBTITLE_MEASURE_WIDTH_FRACTION = 0.90f
        const val MAX_COMMENTARY_BOTTOM_PADDING = 0.55f
    }
}
