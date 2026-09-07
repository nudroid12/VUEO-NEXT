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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.List
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
        val showChrome = controlsVisible && activePanel == TvPlayerPanel.NONE
        if (showChrome || activePanel != TvPlayerPanel.NONE || playbackError != null || activeSkip != null || nextCountdown > 0 || warningVisible) {
            NuvioPlayerCinematicScrim(strong = activePanel != TvPlayerPanel.NONE || playbackError != null)
        }

        if (showChrome) {
            NuvioPlayerControls(
                media = media,
                episode = episode,
                activeSource = activeSource,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                nextEpisode = nextEpisode,
                hasSubtitles = hasSubtitles,
                hasAudio = hasAudio,
                hasSources = hasSources,
                hasEpisodes = hasEpisodes,
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
            )
        }

        if (warningVisible) {
            Text(
                text = "Content guidance  •  ${media.certification}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 38.dp, end = 48.dp),
            )
        }

        activeSkip?.let { segment ->
            NuvioPlayerPromptButton(
                text = nuvioSkipLabel(segment), requester = skipRequester, downRequester = playPauseRequester,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 118.dp),
                onInteraction = onInteraction, onClick = { onSkip(segment) },
            )
        }
        if (nextCountdown > 0 && nextEpisode != null) {
            NuvioPlayerPromptButton(
                text = "Next in $nextCountdown  •  ${nextEpisode.title}", requester = nextContextRequester, downRequester = playPauseRequester,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (activeSkip != null) 168.dp else 118.dp),
                onInteraction = onInteraction, onClick = onNext,
            )
        }

        playbackError?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFFB0B0),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .86f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
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
private fun NuvioPlayerCinematicScrim(strong: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (strong) .78f else .66f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (strong) .92f else .80f)))))
    }
}

@Composable
private fun NuvioPlayerControls(
    media: MediaItem,
    episode: EpisodeItem?,
    activeSource: StreamSource,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    nextEpisode: EpisodeItem?,
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
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onHideControls: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), verticalArrangement = Arrangement.Bottom) {
        Text(media.name, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        episode?.let {
            Spacer(Modifier.height(3.dp))
            Text("S${it.season}E${it.episode} • ${it.title}", color = Color.White.copy(alpha = .88f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!playing) {
            Spacer(Modifier.height(4.dp))
            Text("Via ${activeSource.providerName}", color = Color.White.copy(alpha = .56f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        NuvioPlayerProgressRail(positionMs, durationMs, progressRequester, playPauseRequester, onInteraction, onSeekBy, onHideControls)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                NuvioPlayerControlButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", playPauseRequester, progressRequester, onHideControls, onInteraction, onPlayPause)
                if (nextEpisode != null) NuvioPlayerControlButton(Icons.Rounded.SkipNext, "Next episode", nextRequester, progressRequester, onHideControls, onInteraction, onNext)
                if (hasSubtitles) NuvioPlayerControlButton(Icons.Rounded.Subtitles, "Subtitles", subtitlesRequester, progressRequester, onHideControls, onInteraction, onClick = { onOpenPanel(TvPlayerPanel.SUBTITLES) })
                if (hasAudio) NuvioPlayerControlButton(Icons.Rounded.VolumeUp, "Audio", audioRequester, progressRequester, onHideControls, onInteraction, onClick = { onOpenPanel(TvPlayerPanel.AUDIO) })
                if (hasSources) NuvioPlayerControlButton(Icons.Rounded.SwapHoriz, "Sources", sourcesRequester, progressRequester, onHideControls, onInteraction, onClick = { onOpenPanel(TvPlayerPanel.SOURCES) })
                if (hasEpisodes) NuvioPlayerControlButton(Icons.Rounded.List, "Episodes", episodesRequester, progressRequester, onHideControls, onInteraction, onClick = { onOpenPanel(TvPlayerPanel.EPISODES) })
                NuvioPlayerControlButton(Icons.Rounded.KeyboardArrowRight, "More", moreRequester, progressRequester, onHideControls, onInteraction, onClick = { onOpenPanel(TvPlayerPanel.MORE) })
            }
            Text("${nuvioPlayerTime(positionMs)} / ${nuvioPlayerTime(durationMs)}", color = Color.White.copy(alpha = .88f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
