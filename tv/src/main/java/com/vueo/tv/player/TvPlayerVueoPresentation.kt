package com.vueo.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import com.vueo.shared.core.player.PlayerSkipSegment
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotion
import com.vueo.tv.ui.motion.tvPanelEnter
import com.vueo.tv.ui.motion.tvPanelExit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun VueoPlayerPresentation(
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
    onPlayPause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onRestart: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
    onDismissPanel: () -> Unit,
    onSkip: (PlayerSkipSegment) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onPanelSelected: (TvPlayerOption) -> Unit,
) {
    val retainedPanelOptions = remember { mutableStateOf<List<TvPlayerOption>>(emptyList()) }
    val moreLastFocusKey = remember { mutableStateOf<String?>(null) }
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

    Box(Modifier.fillMaxSize()) {
        val showChrome = controlsVisible && activePanel == TvPlayerPanel.NONE
        val showScrim = showChrome ||
            activePanel != TvPlayerPanel.NONE ||
            playbackError != null ||
            activeSkip != null ||
            nextCountdown > 0
        AnimatedVisibility(
            visible = showScrim,
            enter = fadeIn(tween(TvMotion.ELEMENT_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
        ) {
            VueoPlayerCinematicScrim(strong = activePanel != TvPlayerPanel.NONE || playbackError != null)
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(TvMotion.ELEMENT_MS, easing = TvMotion.EaseOut)),
            exit = fadeOut(tween(TvMotion.QUICK_MS, easing = TvMotion.EaseInOut)),
        ) {
            VueoPlayerControls(
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
                restartRequester = restartRequester,
                progressRequester = progressRequester,
                progressUpRequester = progressUpRequester,
                nextRequester = nextRequester,
                subtitlesRequester = subtitlesRequester,
                audioRequester = audioRequester,
                sourcesRequester = sourcesRequester,
                episodesRequester = episodesRequester,
                moreRequester = moreRequester,
                onInteraction = onInteraction,
                onPlayPause = onPlayPause,
                onRestart = onRestart,
                onSeekBy = onSeekBy,
                onNext = onNext,
                onOpenPanel = onOpenPanel,
            )
        }

        if (warningVisible && contentWarnings.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 38.dp),
            ) {
                VueoContentWarningsOverlay(
                    warnings = contentWarnings,
                    onAnimationComplete = onWarningComplete,
                )
            }
        }

        activeSkip?.let { segment ->
            VueoPlayerPromptButton(
                text = vueoSkipLabel(segment), requester = skipRequester,
                upRequester = if (nextCountdown > 0 && nextEpisode != null) nextContextRequester else FocusRequester.Cancel,
                downRequester = progressRequester,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 118.dp),
                onInteraction = onInteraction, onClick = { onSkip(segment) },
            )
        }
        if (nextCountdown > 0 && nextEpisode != null) {
            VueoPlayerPromptButton(
                text = "Next in $nextCountdown  •  ${nextEpisode.title}", requester = nextContextRequester,
                upRequester = FocusRequester.Cancel,
                downRequester = if (activeSkip != null) skipRequester else progressRequester,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (activeSkip != null) 168.dp else 118.dp),
                onInteraction = onInteraction, onClick = onNext,
            )
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
            visible = activePanel == TvPlayerPanel.MORE,
            enter = tvPanelEnter(),
            exit = tvPanelExit(),
        ) {
            VueoPlayerCompactOverlay(
                panel = TvPlayerPanel.MORE,
                options = displayedPanelOptions,
                initialFocusKey = moreLastFocusKey.value,
                onInteraction = onInteraction,
                onFocused = { moreLastFocusKey.value = it.key },
                onSelected = onPanelSelected,
            )
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

    val totalLineHeight = (count * 14) + ((count - 1) * 2)
    val containerAlpha = remember { Animatable(0f) }
    val lineHeightFraction = remember { Animatable(0f) }
    val itemAlphas = remember(count) { List(count) { Animatable(0f) } }

    LaunchedEffect(warnings) {
        containerAlpha.snapTo(0f)
        lineHeightFraction.snapTo(0f)
        itemAlphas.forEach { it.snapTo(0f) }

        containerAlpha.animateTo(1f, tween(220, easing = TvMotion.EaseOut))
        lineHeightFraction.animateTo(
            1f,
            tween(260, easing = TvMotion.EaseOut),
        )

        coroutineScope {
            itemAlphas.forEachIndexed { index, alpha ->
                launch {
                    delay(index * 55L)
                    alpha.animateTo(1f, tween(160, easing = TvMotion.EaseOut))
                }
            }
        }

        delay(5_000L)

        coroutineScope {
            itemAlphas.asReversed().forEachIndexed { index, alpha ->
                launch {
                    delay(index * 40L)
                    alpha.animateTo(0f, tween(100, easing = TvMotion.EaseInOut))
                }
            }
        }

        lineHeightFraction.animateTo(
            0f,
            tween(180, easing = TvMotion.EaseInOut),
        )
        containerAlpha.animateTo(0f, tween(120, easing = TvMotion.EaseInOut))
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
    onNext: () -> Unit,
    onOpenPanel: (TvPlayerPanel) -> Unit,
) {
    val title = episode?.let {
        "S${it.season} E${it.episode} • ${it.title.ifBlank { "Episode ${it.episode}" }}"
    } ?: media.name

    val bottomActions = buildList {
        if (hasSubtitles) add(VueoPlayerChromeAction(Icons.Rounded.Subtitles, "Subs", subtitlesRequester, TvPlayerPanel.SUBTITLES))
        if (hasAudio) add(VueoPlayerChromeAction(Icons.Rounded.VolumeUp, "Audio", audioRequester, TvPlayerPanel.AUDIO))
        if (hasSources) add(VueoPlayerChromeAction(Icons.Rounded.SwapHoriz, "Sources", sourcesRequester, TvPlayerPanel.SOURCES))
        if (hasEpisodes) add(VueoPlayerChromeAction(Icons.Rounded.List, "Episodes", episodesRequester, TvPlayerPanel.EPISODES))
    }
    val bottomDefaultRequester = bottomActions.firstOrNull()?.requester ?: FocusRequester.Cancel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 30.dp, end = 30.dp, top = 28.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 24.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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

        Spacer(Modifier.weight(1f))

        VueoPlayerProgressRail(
            positionMs = positionMs,
            durationMs = durationMs,
            requester = progressRequester,
            upRequester = progressUpRequester,
            downRequester = bottomDefaultRequester,
            onInteraction = onInteraction,
            onSeekBy = onSeekBy,
            onTogglePlayback = onPlayPause,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                vueoPlayerTime(positionMs),
                color = Color.White.copy(alpha = .90f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                vueoPlayerTime(durationMs),
                color = Color.White.copy(alpha = .90f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (bottomActions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF111316).copy(alpha = .88f))
                    .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
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

private data class VueoPlayerChromeAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val requester: FocusRequester,
    val panel: TvPlayerPanel,
)
