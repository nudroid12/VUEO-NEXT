package com.vueo.app.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.ui.components.NetworkImage

internal data class PlayerEpisodeProgress(
    val fraction: Float = 0f,
    val watched: Boolean = false,
    val positionMs: Long = 0L,
)

private val EpisodeAccent = Color(0xFFB9FF3A)
private val EpisodeCardShape = RoundedCornerShape(14.dp)

@Composable
internal fun PlayerEpisodesWorkspace(
    seriesTitle: String,
    episodes: List<EpisodeItem>,
    currentEpisode: EpisodeItem?,
    progressByEpisodeId: Map<String, PlayerEpisodeProgress>,
    switchingEpisodeId: String?,
    switchingFailed: Boolean,
    onEpisodeSelected: (EpisodeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val groupedEpisodes = remember(episodes) {
        episodes
            .sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
            .groupBy { it.season }
    }
    val seasons = remember(groupedEpisodes) {
        groupedEpisodes.keys.sortedBy(::playerSeasonSortKey)
    }
    var selectedSeason by remember(currentEpisode?.id, seasons) {
        mutableIntStateOf(
            currentEpisode?.season
                ?.takeIf { it in seasons }
                ?: seasons.firstOrNull()
                ?: 1
        )
    }
    val visibleEpisodes = groupedEpisodes[selectedSeason].orEmpty()
    val episodeListState = rememberLazyListState()
    val nextEpisodeId = remember(episodes, currentEpisode?.id) {
        val ordered = episodes.sortedWith(
            compareBy<EpisodeItem> { it.season }
                .thenBy { it.episode }
        )
        val currentIndex = ordered.indexOfFirst {
            it.id == currentEpisode?.id
        }
        ordered.getOrNull(currentIndex + 1)?.id
    }

    LaunchedEffect(selectedSeason, currentEpisode?.id, visibleEpisodes) {
        if (visibleEpisodes.isEmpty()) {
            return@LaunchedEffect
        }
        val currentIndex = visibleEpisodes.indexOfFirst { it.id == currentEpisode?.id }
        episodeListState.scrollToItem(currentIndex.coerceAtLeast(0))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepEpisodesDialogImmersive()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .32f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .12f),
                        .38f to Color.Black.copy(alpha = .64f),
                        1f to Color.Black.copy(alpha = .97f),
                    )
                )
                .clickable(onClick = onDismiss)
                .padding(start = 24.dp, end = 40.dp, top = 20.dp, bottom = 20.dp),
        ) {
            val workspaceWidth = minOf(maxWidth * .56f, 620.dp)
            Surface(
                modifier = Modifier
                    .width(workspaceWidth)
                    .fillMaxHeight(.90f)
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xF2181A1C),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = .09f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Episodes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "$seriesTitle • ${seasonLabel(selectedSeason)} • ${visibleEpisodes.size} episodes",
                        color = Color.White.copy(alpha = .52f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(11.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 10.dp),
                    ) {
                        items(seasons, key = { it }) { season ->
                            SeasonChip(
                                season = season,
                                selected = season == selectedSeason,
                                onClick = { selectedSeason = season },
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))

                    if (visibleEpisodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No episodes available for this season.",
                                color = Color.White.copy(alpha = .52f),
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = episodeListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            itemsIndexed(
                                items = visibleEpisodes,
                                key = { index, candidate ->
                                    "${candidate.season}:${candidate.episode}:${candidate.id}:$index"
                                },
                            ) { _, candidate ->
                                val switching =
                                    candidate.id == switchingEpisodeId
                                EpisodeWorkspaceRow(
                                    episode = candidate,
                                    current =
                                        candidate.id == currentEpisode?.id,
                                    next = candidate.id == nextEpisodeId,
                                    loading =
                                        switching && !switchingFailed,
                                    failed =
                                        switching && switchingFailed,
                                    progress =
                                        progressByEpisodeId[candidate.id]
                                            ?: PlayerEpisodeProgress(),
                                    onClick = {
                                        if (
                                            (!switching || switchingFailed) &&
                                            candidate.id != currentEpisode?.id
                                        ) {
                                            onEpisodeSelected(candidate)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(
    season: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) EpisodeAccent else Color.White.copy(alpha = .08f),
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else Color.White.copy(alpha = .10f),
        ),
    ) {
        Text(
            seasonLabel(season),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) Color(0xFF202124) else Color.White.copy(alpha = .72f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EpisodeWorkspaceRow(
    episode: EpisodeItem,
    current: Boolean,
    next: Boolean,
    loading: Boolean,
    failed: Boolean,
    progress: PlayerEpisodeProgress,
    onClick: () -> Unit,
) {
    val highlighted = current || loading || failed
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EpisodeCardShape)
            .clickable(
                enabled = !current && !loading,
                onClick = onClick,
            ),
        shape = EpisodeCardShape,
        color = if (highlighted) EpisodeAccent.copy(alpha = .10f)
        else Color.White.copy(alpha = .055f),
        border = BorderStroke(
            if (highlighted) 1.5.dp else 1.dp,
            if (highlighted) EpisodeAccent.copy(alpha = .72f)
            else Color.White.copy(alpha = .08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = .06f)),
            ) {
                NetworkImage(
                    url = episode.thumbnail,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallbackText = "E${episode.episode}",
                )
                if (progress.fraction > 0f && !progress.watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = .28f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(EpisodeAccent),
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = .72f),
                ) {
                    Text(
                        "S${episode.season} E${episode.episode}",
                        modifier = Modifier.padding(
                            horizontal = 5.dp,
                            vertical = 2.dp,
                        ),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        episode.title.ifBlank { "Episode ${episode.episode}" },
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = .94f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        failed -> EpisodeStatus(
                            "No source",
                            Color(0xFFFF8A80),
                        )
                        loading -> EpisodeStatus("Loading", EpisodeAccent)
                        current -> EpisodeStatus("Playing", EpisodeAccent)
                        progress.watched -> EpisodeStatus(
                            "Watched",
                            Color.White.copy(alpha = .70f),
                        )
                        next -> EpisodeStatus("Next", EpisodeAccent)
                    }
                }
                episode.overview
                    ?.takeIf { it.isNotBlank() }
                    ?.let { overview ->
                        Text(
                            overview,
                            color = Color.White.copy(alpha = .48f),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                if (progress.fraction > 0f && !progress.watched && !current) {
                    Text(
                        resumeEpisodeLabel(progress),
                        color = EpisodeAccent.copy(alpha = .78f),
                        fontSize = 8.sp,
                    )
                }
            }

            if (progress.watched) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Watched",
                    tint = Color.White.copy(alpha = .60f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeStatus(
    label: String,
    colour: Color,
) {
    Surface(
        shape = CircleShape,
        color = colour.copy(alpha = .12f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = colour,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun seasonLabel(season: Int): String =
    if (season == 0) "Specials" else "Season $season"

private fun playerSeasonSortKey(season: Int): Int =
    if (season == 0) Int.MAX_VALUE else season

private fun resumeEpisodeLabel(
    progress: PlayerEpisodeProgress,
): String =
    if (progress.positionMs > 0L) {
        "Resume ${formatEpisodeTime(progress.positionMs)}"
    } else {
        "${(progress.fraction * 100).toInt().coerceIn(1, 99)}% watched"
    }

private fun formatEpisodeTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun KeepEpisodesDialogImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                window.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                run {
                    window.decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }
            }
        }
        onDispose { }
    }
}
