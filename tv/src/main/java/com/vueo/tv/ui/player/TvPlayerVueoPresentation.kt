package com.vueo.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSkipKind
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.tv.ui.motion.tvPanelEnter
import com.vueo.tv.ui.motion.tvPanelExit
import kotlinx.coroutines.delay

@Composable
internal fun VueoPlayerPresentation(
    media: MediaItem,
    episode: EpisodeItem?,
    activeSource: StreamSource,
    controlsVisible: Boolean,
    seekFeedbackVisible: Boolean,
    activePanel: TvPlayerPanel,
    playing: Boolean,
    isBuffering: Boolean,
    playbackFeedbackToken: Int,
    playbackFeedbackPaused: Boolean,
    translatingSubtitles: Boolean,
    statusIndicatorsEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    nextEpisode: EpisodeItem?,
    activeSkip: PlayerSkipSegment?,
    nextCountdown: Int,
    contentWarnings: List<ContentWarning>,
    warningVisible: Boolean,
    onWarningComplete: () -> Unit,
    playbackError: String?,
    panelOptions: List<TvPlayerOption>,
    episodes: List<EpisodeItem>,
    hasSubtitles: Boolean,
    hasAudio: Boolean,
    hasSources: Boolean,
    hasEpisodes: Boolean,
    restartRequester: FocusRequester,
    progressRequester: FocusRequester,
    nextRequester: FocusRequester,
    subtitlesRequester: FocusRequester,
    audioRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    episodesRequester: FocusRequester,
    moreRequester: FocusRequester,
    skipRequester: FocusRequester,
    nextContextRequester: FocusRequester,
    errorRequester: FocusRequester,
    onInteraction: () -> Unit,
    onChromeInteraction: () -> Unit,
    onPromptFocused: (TvPlayerPromptTarget) -> Unit,
    onPlayPause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onRestart: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekCommit: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onSkip: (PlayerSkipSegment) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onPanelSelected: (TvPlayerOption) -> Unit,
) {
    val retainedPanelOptions = remember { mutableStateOf<List<TvPlayerOption>>(emptyList()) }
    LaunchedEffect(panelOptions) {
        if (panelOptions.isNotEmpty()) retainedPanelOptions.value = panelOptions
    }
    val displayedPanelOptions = panelOptions.ifEmpty { retainedPanelOptions.value }
    val progressUpRequester = when {
        playbackError != null -> errorRequester
        activeSkip != null -> skipRequester
        nextCountdown > 0 && nextEpisode != null -> nextContextRequester
        else -> restartRequester
    }

    val density = LocalDensity.current
    var bottomControlsHeight by remember { mutableStateOf(72.dp) }
    Box(Modifier.fillMaxSize()) {
        val showPrompts = activePanel == TvPlayerPanel.NONE && playbackError == null
        // The progress rail is the first item in the measured bottom controls.
        // Hidden feedback has a 3dp rail above its existing 22dp bottom inset.
        val promptBottomPadding = if (controlsVisible) bottomControlsHeight + 12.dp else 22.dp + 3.dp + 12.dp
        val skipOnRight = activeSkip?.kind == PlayerSkipKind.ENDING
        val showChrome = controlsVisible && activePanel == TvPlayerPanel.NONE
        val showScrim = showChrome ||
            activePanel != TvPlayerPanel.NONE ||
            playbackError != null ||
            (showPrompts && activeSkip != null) ||
            (showPrompts && nextCountdown > 0)
        AnimatedVisibility(
            visible = showScrim,
            enter = fadeIn(tween(TvMotion.ELEMENT_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
        ) {
            VueoPlayerCinematicScrim(strong = activePanel != TvPlayerPanel.NONE || playbackError != null)
        }

        VueoPlayerControls(
            visible = showChrome,
            onBottomControlsHeightChanged = { heightPx ->
                bottomControlsHeight = with(density) { heightPx.toDp() }
            },
            media = media,
            episode = episode,
            activeSource = activeSource,
            contentWarningVisible = warningVisible,
            playing = playing,
            positionMs = positionMs,
            durationMs = durationMs,
            nextEpisode = nextEpisode,
            hasSubtitles = hasSubtitles,
            hasAudio = hasAudio,
            hasSources = hasSources,
            hasEpisodes = hasEpisodes,
            restartRequester = restartRequester,
            progressRequester = progressRequester,
            progressUpRequester = progressUpRequester,
            nextRequester = nextRequester,
            subtitlesRequester = subtitlesRequester,
            audioRequester = audioRequester,
            sourcesRequester = sourcesRequester,
            episodesRequester = episodesRequester,
            moreRequester = moreRequester,
            onInteraction = onChromeInteraction,
            onPlayPause = onPlayPause,
            onRestart = onRestart,
            onSeekBy = onSeekBy,
            onSeekCommit = onSeekCommit,
            onNext = onNext,
            onOpenPanel = onOpenPanel,
        )

        // Feedback is display-only: root focus continues receiving quick seeks.
        AnimatedVisibility(
            visible = seekFeedbackVisible && !controlsVisible && activePanel == TvPlayerPanel.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 22.dp)) {
                VueoPlayerProgressRail(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    requester = progressRequester,
                    upRequester = FocusRequester.Cancel,
                    downRequester = FocusRequester.Cancel,
                    onInteraction = {},
                    onSeekBy = {},
                    onSeekCommit = {},
                    onTogglePlayback = {},
                    interactive = false,
                )
            }
        }

        if (warningVisible && contentWarnings.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 32.dp),
            ) {
                VueoContentWarningsOverlay(
                    warnings = contentWarnings,
                    onAnimationComplete = onWarningComplete,
                )
            }
        }

        if (showPrompts && (activeSkip != null || nextCountdown > 0)) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = promptBottomPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (activeSkip != null && !skipOnRight) {
                    VueoPlayerPromptButton(
                        text = vueoSkipLabel(activeSkip), requester = skipRequester,
                        upRequester = if (nextCountdown > 0 && nextEpisode != null) nextContextRequester else FocusRequester.Cancel,
                        downRequester = if (showChrome) progressRequester else FocusRequester.Cancel,
                        onInteraction = onInteraction,
                        onFocused = { onPromptFocused(TvPlayerPromptTarget.SKIP) },
                        onClick = { onSkip(activeSkip) },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (nextCountdown > 0 && nextEpisode != null) {
                        VueoPlayerNextEpisodeCard(
                            episode = nextEpisode,
                            fallbackImage = media.background,
                            countdown = nextCountdown,
                            requester = nextContextRequester,
                            upRequester = FocusRequester.Cancel,
                            downRequester = if (activeSkip != null) skipRequester else if (showChrome) progressRequester else FocusRequester.Cancel,
                            onInteraction = onInteraction,
                            onFocused = { onPromptFocused(TvPlayerPromptTarget.NEXT) },
                            onClick = onNext,
                        )
                    }
                    if (activeSkip != null && skipOnRight) {
                        VueoPlayerPromptButton(
                            text = vueoSkipLabel(activeSkip), requester = skipRequester,
                            upRequester = if (nextCountdown > 0 && nextEpisode != null) nextContextRequester else FocusRequester.Cancel,
                            downRequester = if (showChrome) progressRequester else FocusRequester.Cancel,
                            onInteraction = onInteraction,
                            onFocused = { onPromptFocused(TvPlayerPromptTarget.SKIP) },
                            onClick = { onSkip(activeSkip) },
                        )
                    }
                }
            }
        }

        playbackError?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .86f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = message, color = Color(0xFFFFB0B0), fontSize = 13.sp, lineHeight = 18.sp)
                VueoPlayerPromptButton(
                    text = "Retry", requester = errorRequester,
                    upRequester = FocusRequester.Cancel,
                    downRequester = progressRequester,
                    onInteraction = onInteraction, onClick = onRetryPlayback,
                )
            }
        }

        AnimatedVisibility(
            visible = activePanel == TvPlayerPanel.SOURCES,
            enter = tvPanelEnter(),
            exit = tvPanelExit(),
        ) {
            VueoPlayerSourcesPanel(
                title = episode?.let { "S${it.season}E${it.episode} • ${it.title}" } ?: media.name,
                options = displayedPanelOptions,
                onInteraction = onInteraction,
                onDismiss = onDismissPanel,
                onSelected = onPanelSelected,
            )
        }
        VueoPlayerStatusIndicators(
            buffering = isBuffering,
            feedbackToken = playbackFeedbackToken,
            paused = playbackFeedbackPaused,
            translating = translatingSubtitles,
            enabled = statusIndicatorsEnabled && playbackError == null && activePanel == TvPlayerPanel.NONE,
            showPlaybackFeedback = !controlsVisible,
            translationTopPadding = if (controlsVisible) 100.dp else 30.dp,
        )
        AnimatedVisibility(
            visible = activePanel == TvPlayerPanel.EPISODES,
            enter = tvPanelEnter(),
            exit = tvPanelExit(),
        ) {
            VueoPlayerEpisodesPanel(
                mediaTitle = media.name,
                episodes = episodes,
                currentEpisode = episode,
                onInteraction = onInteraction,
                onDismiss = onDismissPanel,
                onSelected = onPlayEpisode,
            )
        }
    }
}

@Composable
private fun VueoContentWarningsOverlay(
    warnings: List<ContentWarning>,
    onAnimationComplete: () -> Unit,
) {
    val count = warnings.size
    if (count == 0) return

    val totalLineHeight = (count * 16) + ((count - 1) * 3)
    val containerAlpha = remember { Animatable(0f) }
    val lineHeightFraction = remember { Animatable(0f) }
    val itemAlphas = remember(count) { List(count) { Animatable(0f) } }

    LaunchedEffect(warnings) {
        containerAlpha.snapTo(0f)
        lineHeightFraction.snapTo(0f)
        itemAlphas.forEach { it.snapTo(0f) }

        containerAlpha.animateTo(1f, tween(300, easing = TvMotion.EaseOut))
        lineHeightFraction.animateTo(
            1f,
            tween(400, easing = TvMotion.EaseOut),
        )

        itemAlphas.forEach { alpha ->
            delay(80L)
            alpha.animateTo(1f, tween(200, easing = TvMotion.EaseOut))
        }

        delay(5_000L)

        itemAlphas.asReversed().forEach { alpha ->
            delay(60L)
            alpha.animateTo(0f, tween(150, easing = TvMotion.EaseInOut))
        }

        delay(100L)
        lineHeightFraction.animateTo(
            0f,
            tween(300, easing = TvMotion.EaseInOut),
        )
        delay(200L)
        containerAlpha.animateTo(0f, tween(200, easing = TvMotion.EaseInOut))
        onAnimationComplete()
    }

    if (containerAlpha.value <= 0f) return

    Row(
        modifier = Modifier.alpha(containerAlpha.value),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((totalLineHeight * lineHeightFraction.value).dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFB9FF3A)),
        )
        Column(
            modifier = Modifier.padding(start = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            warnings.forEachIndexed { index, warning ->
                Row(
                    modifier = Modifier.alpha(itemAlphas.getOrNull(index)?.value ?: 0f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = warning.label,
                        color = Color.White.copy(alpha = .92f),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " • ${warning.severity}",
                        color = Color.White.copy(alpha = .56f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun VueoPlayerCinematicScrim(strong: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (strong) .78f else .66f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (strong) .92f else .80f)))))
    }
}

@Composable
private fun VueoPlayerControls(
    visible: Boolean,
    onBottomControlsHeightChanged: (Int) -> Unit,
    media: MediaItem,
    episode: EpisodeItem?,
    activeSource: StreamSource,
    contentWarningVisible: Boolean,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    nextEpisode: EpisodeItem?,
    hasSubtitles: Boolean,
    hasAudio: Boolean,
    hasSources: Boolean,
    hasEpisodes: Boolean,
    restartRequester: FocusRequester,
    progressRequester: FocusRequester,
    progressUpRequester: FocusRequester,
    nextRequester: FocusRequester,
    subtitlesRequester: FocusRequester,
    audioRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    episodesRequester: FocusRequester,
    moreRequester: FocusRequester,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onRestart: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekCommit: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
) {
    val episodeLine = episode?.let {
        "S${it.season} E${it.episode} • ${it.title.ifBlank { "Episode ${it.episode}" }}"
    }

    val bottomActions = buildList {
        if (hasSubtitles) add(VueoPlayerChromeAction(Icons.Rounded.Subtitles, "Subs", subtitlesRequester, TvPlayerPanel.SUBTITLES))
        if (hasAudio) add(VueoPlayerChromeAction(Icons.Rounded.VolumeUp, "Audio", audioRequester, TvPlayerPanel.AUDIO))
        if (hasSources) add(VueoPlayerChromeAction(Icons.Rounded.SwapHoriz, "Sources", sourcesRequester, TvPlayerPanel.SOURCES))
        if (hasEpisodes) add(VueoPlayerChromeAction(Icons.Rounded.List, "Episodes", episodesRequester, TvPlayerPanel.EPISODES))
    }
    val bottomDefaultRequester = bottomActions.firstOrNull()?.requester ?: FocusRequester.Cancel

    CompositionLocalProvider(LocalPlayerChromeInteractive provides visible) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(
                    animationSpec = tween(TvMotion.PANEL_IN_MS, easing = TvMotion.EaseOut),
                    initialOffsetY = { -it },
                ) + fadeIn(tween(TvMotion.PANEL_IN_MS, easing = TvMotion.EaseOut)),
                exit = slideOutVertically(
                    animationSpec = tween(TvMotion.PANEL_OUT_MS, easing = TvMotion.EaseInOut),
                    targetOffsetY = { -it },
                ) + fadeOut(tween(TvMotion.PANEL_OUT_MS, easing = TvMotion.EaseInOut)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (contentWarningVisible) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = media.name,
                                color = Color.White,
                                fontSize = 20.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            episodeLine?.let { line ->
                                Text(
                                    text = line,
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = 14.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VueoPlayerTopAction(
                            icon = Icons.Rounded.Replay,
                            label = "Restart",
                            requester = restartRequester,
                            downRequester = progressRequester,
                            leftRequester = FocusRequester.Cancel,
                            rightRequester = if (nextEpisode != null) nextRequester else moreRequester,
                            onInteraction = onInteraction,
                            onClick = onRestart,
                        )
                        if (nextEpisode != null) {
                            VueoPlayerTopAction(
                                icon = Icons.Rounded.SkipNext,
                                label = "Next episode",
                                requester = nextRequester,
                                downRequester = progressRequester,
                                leftRequester = restartRequester,
                                rightRequester = moreRequester,
                                onInteraction = onInteraction,
                                onClick = onNext,
                            )
                        }
                        VueoPlayerTopAction(
                            icon = Icons.Rounded.MoreHoriz,
                            label = "More",
                            requester = moreRequester,
                            downRequester = progressRequester,
                            leftRequester = if (nextEpisode != null) nextRequester else restartRequester,
                            rightRequester = FocusRequester.Cancel,
                            onInteraction = onInteraction,
                            onClick = { onOpenPanel(TvPlayerPanel.MORE) },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = tween(TvMotion.PANEL_IN_MS, easing = TvMotion.EaseOut),
                    initialOffsetY = { it },
                ) + fadeIn(tween(TvMotion.PANEL_IN_MS, easing = TvMotion.EaseOut)),
                exit = slideOutVertically(
                    animationSpec = tween(TvMotion.PANEL_OUT_MS, easing = TvMotion.EaseInOut),
                    targetOffsetY = { it },
                ) + fadeOut(tween(TvMotion.PANEL_OUT_MS, easing = TvMotion.EaseInOut)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { onBottomControlsHeightChanged(it.height) }
                        .padding(start = 30.dp, end = 30.dp, bottom = 22.dp),
                ) {
                    VueoPlayerProgressRail(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        requester = progressRequester,
                        upRequester = progressUpRequester,
                        downRequester = bottomDefaultRequester,
                        onInteraction = onInteraction,
                        onSeekBy = onSeekBy,
                        onSeekCommit = onSeekCommit,
                        onTogglePlayback = onPlayPause,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            vueoPlayerTime(positionMs),
                            color = Color.White.copy(alpha = .90f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            vueoPlayerRemainingTime(positionMs, durationMs),
                            color = Color.White.copy(alpha = .90f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (bottomActions.isNotEmpty()) {
                        Spacer(Modifier.height(1.dp))
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF111316).copy(alpha = .88f))
                                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(22.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            bottomActions.forEachIndexed { index, action ->
                                VueoPlayerPillAction(
                                    icon = action.icon,
                                    label = action.label,
                                    requester = action.requester,
                                    upRequester = progressRequester,
                                    leftRequester = bottomActions.getOrNull(index - 1)?.requester ?: FocusRequester.Cancel,
                                    rightRequester = bottomActions.getOrNull(index + 1)?.requester ?: FocusRequester.Cancel,
                                    onInteraction = onInteraction,
                                    onClick = { onOpenPanel(action.panel) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class VueoPlayerChromeAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val requester: FocusRequester,
    val panel: TvPlayerPanel,
)
