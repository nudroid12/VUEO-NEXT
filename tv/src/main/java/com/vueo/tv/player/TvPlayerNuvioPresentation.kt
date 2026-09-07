package com.vueo.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.tv.ui.TvDesign

/**
 * TV 40A presentation root.
 *
 * This screen is intentionally Nuvio-first: the player surface is supplied by
 * VUEO, while all visible player chrome, focus geometry, gradients and panels
 * live in this presentation tree rather than in the playback controller.
 */
@Composable
internal fun NuvioPlayerPresentation(
    media: MediaItem,
    episode: EpisodeItem?,
    activeSource: StreamSource,
    controlsVisible: Boolean,
    activePanel: TvPlayerPanel,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    nextEpisode: EpisodeItem?,
    activeSkip: PlayerSkipSegment?,
    nextCountdown: Int,
    warningVisible: Boolean,
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
    skipRequester: FocusRequester,
    nextContextRequester: FocusRequester,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onHideControls: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onSkip: (PlayerSkipSegment) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onPanelSelected: (TvPlayerOption) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (controlsVisible || activePanel != TvPlayerPanel.NONE || playbackError != null || activeSkip != null || nextCountdown > 0 || warningVisible) {
            NuvioPlayerCinematicScrim(
                strong = activePanel != TvPlayerPanel.NONE || playbackError != null,
            )
        }

        if (controlsVisible && (activePanel == TvPlayerPanel.NONE || activePanel == TvPlayerPanel.MORE)) {
            NuvioPlayerControls(
                title = media.name,
                releaseInfo = media.releaseInfo,
                episode = episode,
                activeSource = activeSource,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                hasNextEpisode = nextEpisode != null,
                hasSubtitles = hasSubtitles,
                hasAudio = hasAudio,
                hasSources = hasSources,
                hasEpisodes = hasEpisodes,
                moreOpen = activePanel == TvPlayerPanel.MORE,
                moreOptions = if (activePanel == TvPlayerPanel.MORE) panelOptions else emptyList(),
                playPauseRequester = playPauseRequester,
                progressRequester = progressRequester,
                nextRequester = nextRequester,
                subtitlesRequester = subtitlesRequester,
                audioRequester = audioRequester,
                sourcesRequester = sourcesRequester,
                episodesRequester = episodesRequester,
                moreRequester = moreRequester,
                onInteraction = onInteraction,
                onPlayPause = onPlayPause,
                onSeekBy = onSeekBy,
                onHideControls = onHideControls,
                onNext = onNext,
                onOpenPanel = onOpenPanel,
                onDismissPanel = onDismissPanel,
                onMoreOptionSelected = onPanelSelected,
            )
        }

        if (warningVisible) {
            Text(
                text = "Content guidance  •  ${media.certification}",
                color = TvDesign.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 48.dp)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }

        activeSkip?.let { segment ->
            NuvioPlayerPromptButton(
                text = nuvioSkipLabel(segment),
                requester = skipRequester,
                downRequester = playPauseRequester,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 126.dp),
                onInteraction = onInteraction,
                onClick = { onSkip(segment) },
            )
        }

        if (nextCountdown > 0 && nextEpisode != null) {
            NuvioPlayerPromptButton(
                text = "Next in $nextCountdown  •  ${nextEpisode.title}",
                requester = nextContextRequester,
                downRequester = playPauseRequester,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = if (activeSkip != null) 176.dp else 126.dp),
                onInteraction = onInteraction,
                onClick = onNext,
            )
        }

        playbackError?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .86f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text(
                    text = message,
                    color = Color(0xFFFFB0B0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        when (activePanel) {
            TvPlayerPanel.SUBTITLES,
            TvPlayerPanel.AUDIO -> {
                NuvioPlayerLeftOptionsOverlay(
                    panel = activePanel,
                    options = panelOptions,
                    onInteraction = onInteraction,
                    onSelected = onPanelSelected,
                )
            }

            TvPlayerPanel.SOURCES -> {
                NuvioPlayerSourcesPanel(
                    title = episode?.let { "S${it.season}E${it.episode} • ${it.title}" } ?: media.name,
                    options = panelOptions,
                    onInteraction = onInteraction,
                    onDismiss = onDismissPanel,
                    onSelected = onPanelSelected,
                )
            }

            TvPlayerPanel.EPISODES -> {
                NuvioPlayerEpisodesPanel(
                    mediaTitle = media.name,
                    episodes = episodes,
                    currentEpisode = episode,
                    onInteraction = onInteraction,
                    onDismiss = onDismissPanel,
                    onSelected = onPlayEpisode,
                )
            }

            TvPlayerPanel.MORE,
            TvPlayerPanel.NONE -> Unit
        }
    }
}

@Composable
private fun NuvioPlayerCinematicScrim(strong: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (strong) .80f else .68f),
                            Color.Transparent,
                        )
                    )
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = if (strong) .93f else .82f),
                        )
                    )
                ),
        )
    }
}

@Composable
private fun NuvioPlayerControls(
    title: String,
    releaseInfo: String?,
    episode: EpisodeItem?,
    activeSource: StreamSource,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasNextEpisode: Boolean,
    hasSubtitles: Boolean,
    hasAudio: Boolean,
    hasSources: Boolean,
    hasEpisodes: Boolean,
    moreOpen: Boolean,
    moreOptions: List<TvPlayerOption>,
    playPauseRequester: FocusRequester,
    progressRequester: FocusRequester,
    nextRequester: FocusRequester,
    subtitlesRequester: FocusRequester,
    audioRequester: FocusRequester,
    sourcesRequester: FocusRequester,
    episodesRequester: FocusRequester,
    moreRequester: FocusRequester,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onHideControls: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onMoreOptionSelected: (TvPlayerOption) -> Unit,
) {
    val speedRequester = remember { FocusRequester() }
    val aspectRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 27.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        episode?.let {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "S${it.season}E${it.episode} • ${it.title}",
                color = Color.White.copy(alpha = .90f),
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val sourceLine = if (!playing) {
            "Via ${activeSource.providerName}" + activeSource.quality?.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty()
        } else null
        if (!releaseInfo.isNullOrBlank() || sourceLine != null) {
            Spacer(Modifier.height(5.dp))
            releaseInfo?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = .66f),
                    fontSize = 12.sp,
                )
            }
            sourceLine?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = .66f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(15.dp))

        NuvioPlayerProgressRail(
            positionMs = positionMs,
            durationMs = durationMs,
            requester = progressRequester,
            downRequester = playPauseRequester,
            onInteraction = onInteraction,
            onSeekBy = onSeekBy,
            onHideControls = onHideControls,
        )

        Spacer(Modifier.height(15.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvioPlayerControlButton(
                    icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    label = if (playing) "Pause" else "Play",
                    requester = playPauseRequester,
                    upRequester = progressRequester,
                    onDown = onHideControls,
                    onInteraction = onInteraction,
                    onClick = onPlayPause,
                )
                if (hasNextEpisode) {
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.SkipNext,
                        label = "Next episode",
                        requester = nextRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = onNext,
                    )
                }
                if (hasSubtitles) {
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.Subtitles,
                        label = "Subtitles",
                        requester = subtitlesRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = { onOpenPanel(TvPlayerPanel.SUBTITLES) },
                    )
                }
                if (hasAudio) {
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.VolumeUp,
                        label = "Audio",
                        requester = audioRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = { onOpenPanel(TvPlayerPanel.AUDIO) },
                    )
                }
                if (hasSources) {
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.SwapHoriz,
                        label = "Sources",
                        requester = sourcesRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = { onOpenPanel(TvPlayerPanel.SOURCES) },
                    )
                }
                if (hasEpisodes) {
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.List,
                        label = "Episodes",
                        requester = episodesRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = { onOpenPanel(TvPlayerPanel.EPISODES) },
                    )
                }
                if (moreOpen) {
                    val speedOptions = moreOptions.filter { it.key.startsWith("speed:") }
                    val fitOptions = moreOptions.filter { it.key.startsWith("fit:") }
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.Speed,
                        label = speedOptions.firstOrNull { it.selected }?.meta?.let { "Speed $it" } ?: "Playback speed",
                        requester = speedRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = {
                            val current = speedOptions.indexOfFirst { it.selected }.coerceAtLeast(0)
                            speedOptions.getOrNull((current + 1) % speedOptions.size.coerceAtLeast(1))
                                ?.let(onMoreOptionSelected)
                        },
                    )
                    NuvioPlayerControlButton(
                        icon = Icons.Rounded.AspectRatio,
                        label = fitOptions.firstOrNull { it.selected }?.meta?.let { "Video fit $it" } ?: "Video fit",
                        requester = aspectRequester,
                        upRequester = progressRequester,
                        onDown = onHideControls,
                        onInteraction = onInteraction,
                        onClick = {
                            val current = fitOptions.indexOfFirst { it.selected }.coerceAtLeast(0)
                            fitOptions.getOrNull((current + 1) % fitOptions.size.coerceAtLeast(1))
                                ?.let(onMoreOptionSelected)
                        },
                    )
                }
                NuvioPlayerControlButton(
                    icon = if (moreOpen) Icons.Rounded.KeyboardArrowLeft else Icons.Rounded.KeyboardArrowRight,
                    label = if (moreOpen) "Close more" else "More",
                    requester = moreRequester,
                    upRequester = progressRequester,
                    onDown = onHideControls,
                    onInteraction = onInteraction,
                    onClick = {
                        if (moreOpen) onDismissPanel() else onOpenPanel(TvPlayerPanel.MORE)
                    },
                )
            }

            Text(
                text = "${nuvioPlayerTime(positionMs)} / ${nuvioPlayerTime(durationMs)}",
                color = Color.White.copy(alpha = .90f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
