package com.vueo.tv.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.enrichment.ContentWarning
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSkipSegment
import kotlinx.coroutines.delay

private data class MobileTvBottomAction(
    val icon: ImageVector,
    val label: String,
    val requester: FocusRequester,
    val panel: TvPlayerPanel,
)

@Composable
internal fun NuvioPlayerPresentation(
    media: MediaItem,
    episode: EpisodeItem?,
    activeSource: StreamSource,
    controlsVisible: Boolean,
    controlsLocked: Boolean,
    resumePromptVisible: Boolean,
    resumePositionMs: Long,
    activePanel: TvPlayerPanel,
    playing: Boolean,
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
    playPauseRequester: FocusRequester,
    progressRequester: FocusRequester,
    nextRequester: FocusRequester,
    subtitlesRequester: FocusRequester,
    audioRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    episodesRequester: FocusRequester,
    moreRequester: FocusRequester,
    unlockRequester: FocusRequester,
    skipRequester: FocusRequester,
    nextContextRequester: FocusRequester,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onSkip: (PlayerSkipSegment) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onPanelSelected: (TvPlayerOption) -> Unit,
) {
    val rewindRequester = remember { FocusRequester() }
    val forwardRequester = remember { FocusRequester() }
    val lockRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }

    val bottomActions = buildList {
        if (hasSubtitles) add(MobileTvBottomAction(Icons.Rounded.ClosedCaption, "Subs", subtitlesRequester, TvPlayerPanel.SUBTITLES))
        if (hasAudio) add(MobileTvBottomAction(Icons.Rounded.VolumeUp, "Audio", audioRequester, TvPlayerPanel.AUDIO))
        if (hasSources) add(MobileTvBottomAction(Icons.Rounded.Dns, "Sources", sourcesRequester, TvPlayerPanel.SOURCES))
        if (hasEpisodes) add(MobileTvBottomAction(Icons.Rounded.VideoLibrary, "Episodes", episodesRequester, TvPlayerPanel.EPISODES))
    }
    val firstBottomRequester = bottomActions.firstOrNull()?.requester
    val topFirstRequester = if (nextEpisode != null) nextRequester else lockRequester

    Box(Modifier.fillMaxSize()) {
        val showChrome = controlsVisible && !controlsLocked && !resumePromptVisible && activePanel == TvPlayerPanel.NONE
        if (showChrome) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = .62f),
                                Color.Transparent,
                                Color.Black.copy(alpha = .70f),
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = episode?.let { "S${it.season} E${it.episode} • ${it.title}" } ?: media.name,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (nextEpisode != null) {
                    MobileTvPlayerTopAction(
                        icon = Icons.Rounded.SkipNext,
                        label = "Next episode",
                        requester = nextRequester,
                        rightRequester = lockRequester,
                        downRequester = playPauseRequester,
                        onInteraction = onInteraction,
                        onClick = onNext,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                MobileTvPlayerTopAction(
                    icon = Icons.Rounded.Lock,
                    label = "Lock controls",
                    requester = lockRequester,
                    leftRequester = if (nextEpisode != null) nextRequester else null,
                    rightRequester = moreRequester,
                    downRequester = playPauseRequester,
                    onInteraction = onInteraction,
                    onClick = onLock,
                )
                Spacer(Modifier.width(8.dp))
                MobileTvPlayerTopAction(
                    icon = Icons.Rounded.MoreHoriz,
                    label = "More controls",
                    requester = moreRequester,
                    leftRequester = lockRequester,
                    rightRequester = backRequester,
                    downRequester = playPauseRequester,
                    onInteraction = onInteraction,
                    onClick = { onOpenPanel(TvPlayerPanel.MORE) },
                )
                Spacer(Modifier.width(8.dp))
                MobileTvPlayerTopAction(
                    icon = Icons.Rounded.ArrowBack,
                    label = "Back",
                    requester = backRequester,
                    leftRequester = moreRequester,
                    downRequester = playPauseRequester,
                    onInteraction = onInteraction,
                    onClick = onBack,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MobileTvPlayerRoundAction(
                    icon = Icons.Rounded.Replay10,
                    label = "Rewind 10 seconds",
                    requester = rewindRequester,
                    rightRequester = playPauseRequester,
                    upRequester = topFirstRequester,
                    downRequester = progressRequester,
                    onInteraction = onInteraction,
                    onClick = { onSeekBy(-10_000L) },
                )
                MobileTvPlayerRoundAction(
                    icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    label = if (playing) "Pause" else "Play",
                    requester = playPauseRequester,
                    leftRequester = rewindRequester,
                    rightRequester = forwardRequester,
                    upRequester = topFirstRequester,
                    downRequester = progressRequester,
                    primary = true,
                    onInteraction = onInteraction,
                    onClick = onPlayPause,
                )
                MobileTvPlayerRoundAction(
                    icon = Icons.Rounded.Forward10,
                    label = "Forward 10 seconds",
                    requester = forwardRequester,
                    leftRequester = playPauseRequester,
                    upRequester = topFirstRequester,
                    downRequester = progressRequester,
                    onInteraction = onInteraction,
                    onClick = { onSeekBy(10_000L) },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                playbackError?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFFB0B0),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .align(Alignment.End)
                            .background(Color.Black.copy(alpha = .80f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }

                MobileTvPlayerProgressRail(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    requester = progressRequester,
                    upRequester = playPauseRequester,
                    downRequester = firstBottomRequester,
                    onInteraction = onInteraction,
                    onSeekBy = onSeekBy,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(nuvioPlayerTime(positionMs), color = Color.White, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(nuvioPlayerTime(durationMs), color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                }

                if (bottomActions.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().offset(y = (-6).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(30.dp))
                                .background(Color(0xD9161719), RoundedCornerShape(30.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            bottomActions.forEachIndexed { index, action ->
                                MobileTvPlayerPanelAction(
                                    icon = action.icon,
                                    label = action.label,
                                    requester = action.requester,
                                    leftRequester = bottomActions.getOrNull(index - 1)?.requester,
                                    rightRequester = bottomActions.getOrNull(index + 1)?.requester,
                                    upRequester = progressRequester,
                                    onInteraction = onInteraction,
                                    onClick = { onOpenPanel(action.panel) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (resumePromptVisible) {
            MobileTvResumePrompt(
                positionLabel = nuvioPlayerTime(resumePositionMs),
                onInteraction = onInteraction,
                onResume = onResume,
                onStartOver = onStartOver,
            )
        }

        if (controlsLocked && activePanel == TvPlayerPanel.NONE) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            ) {
                MobileTvPlayerUnlockAction(
                    requester = unlockRequester,
                    onInteraction = onInteraction,
                    onClick = onUnlock,
                )
            }
        }

        if (warningVisible && contentWarnings.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 20.dp),
            ) {
                NuvioContentWarningsOverlay(
                    warnings = contentWarnings,
                    onAnimationComplete = onWarningComplete,
                )
            }
        }

        activeSkip?.let { segment ->
            NuvioPlayerPromptButton(
                text = nuvioSkipLabel(segment),
                requester = skipRequester,
                downRequester = playPauseRequester,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 118.dp),
                onInteraction = onInteraction,
                onClick = { onSkip(segment) },
            )
        }

        if (nextCountdown > 0 && nextEpisode != null) {
            NuvioPlayerPromptButton(
                text = "Next in $nextCountdown • ${nextEpisode.title}",
                requester = nextContextRequester,
                downRequester = playPauseRequester,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 126.dp),
                onInteraction = onInteraction,
                onClick = onNext,
            )
        }

        when (activePanel) {
            TvPlayerPanel.SUBTITLES, TvPlayerPanel.AUDIO, TvPlayerPanel.MORE ->
                NuvioPlayerCompactOverlay(
                    panel = activePanel,
                    options = panelOptions,
                    onInteraction = onInteraction,
                    onSelected = onPanelSelected,
                )
            TvPlayerPanel.SOURCES -> NuvioPlayerSourcesPanel(
                title = episode?.let { "S${it.season}E${it.episode} • ${it.title}" } ?: media.name,
                options = panelOptions,
                onInteraction = onInteraction,
                onDismiss = onDismissPanel,
                onSelected = onPanelSelected,
            )
            TvPlayerPanel.EPISODES -> NuvioPlayerEpisodesPanel(
                mediaTitle = media.name,
                episodes = episodes,
                currentEpisode = episode,
                onInteraction = onInteraction,
                onDismiss = onDismissPanel,
                onSelected = onPlayEpisode,
            )
            TvPlayerPanel.NONE -> Unit
        }
    }
}

@Composable
private fun MobileTvResumePrompt(
    positionLabel: String,
    onInteraction: () -> Unit,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    val resumeRequester = remember { FocusRequester() }
    val startOverRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(45L)
        runCatching { resumeRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .62f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2131416))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(VueoTvPlayerAccent.copy(alpha = .14f), CircleShape)
                        .border(1.dp, VueoTvPlayerAccent.copy(alpha = .42f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = VueoTvPlayerAccent,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Resume watching?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Continue from $positionLabel",
                        color = Color.White.copy(alpha = .58f),
                        fontSize = 11.sp,
                    )
                }
            }

            MobileTvPlayerChoiceAction(
                label = "Resume $positionLabel",
                requester = resumeRequester,
                downRequester = startOverRequester,
                primary = true,
                onInteraction = onInteraction,
                onClick = onResume,
            )
            MobileTvPlayerChoiceAction(
                label = "Start Over",
                requester = startOverRequester,
                upRequester = resumeRequester,
                onInteraction = onInteraction,
                onClick = onStartOver,
            )

            Text(
                text = "Back to leave player",
                color = Color.White.copy(alpha = .38f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun NuvioContentWarningsOverlay(
    warnings: List<ContentWarning>,
    onAnimationComplete: () -> Unit,
) {
    val count = warnings.size
    if (count == 0) return

    val totalLineHeight = (count * 14) + ((count - 1) * 2)
    val containerAlpha = remember { Animatable(0f) }
    val lineHeightFraction = remember { Animatable(0f) }
    val itemAlphas = remember(count) { List(count) { Animatable(0f) } }

    LaunchedEffect(warnings) {
        containerAlpha.snapTo(0f)
        lineHeightFraction.snapTo(0f)
        itemAlphas.forEach { it.snapTo(0f) }

        containerAlpha.animateTo(1f, tween(300))
        lineHeightFraction.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        for (index in 0 until count) {
            delay(80L)
            itemAlphas[index].animateTo(1f, tween(200))
        }
        delay(5_000L)
        for (index in (count - 1) downTo 0) {
            delay(60L)
            itemAlphas[index].animateTo(0f, tween(150))
        }
        delay(100L)
        lineHeightFraction.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        delay(200L)
        containerAlpha.animateTo(0f, tween(200))
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
                .background(VueoTvPlayerAccent),
        )
        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            warnings.forEachIndexed { index, warning ->
                Row(
                    modifier = Modifier.alpha(itemAlphas.getOrNull(index)?.value ?: 0f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = warning.label,
                        color = Color.White.copy(alpha = .92f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " • ${warning.severity}",
                        color = Color.White.copy(alpha = .56f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                    )
                }
            }
        }
    }
}
