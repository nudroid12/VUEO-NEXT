package com.vueo.tv.source

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.core.TvSourceBundle
import com.vueo.tv.ui.TvDesign

@Composable
fun TvSourceScreen(
    runtime: TvRuntime,
    media: MediaItem,
    episode: EpisodeItem?,
    onBack: () -> Unit,
    onPlay: (TvSourceBundle, StreamSource) -> Unit,
) {
    BackHandler(onBack = onBack)

    var bundle by remember(media.id, episode?.id) { mutableStateOf<TvSourceBundle?>(null) }
    var searching by remember(media.id, episode?.id) { mutableStateOf(true) }
    var progress by remember(media.id, episode?.id) { mutableStateOf("Searching") }
    var error by remember(media.id, episode?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(media.id, episode?.id) {
        searching = true
        error = null
        bundle = runCatching {
            runtime.discover(media, episode) { progress = it }
        }.onFailure {
            error = it.message ?: "Source discovery failed"
        }.getOrNull()
        searching = false
    }

    val playable = bundle?.sources.orEmpty().filter { it.isDirectPlayable }

    Box(Modifier.fillMaxSize().background(TvDesign.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 46.dp),
        ) {
            Text(
                text = if (episode == null) media.name
                else "${media.name}  •  S${episode.season}E${episode.episode}",
                color = TvDesign.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Choose a source",
                color = TvDesign.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 20.dp),
            )

            when {
                searching && playable.isEmpty() -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(22.dp),
                            color = TvDesign.White,
                            strokeWidth = 2.dp,
                        )
                        Text(progress, color = TvDesign.Muted, fontSize = 14.sp)
                    }
                }

                error != null && playable.isEmpty() -> {
                    Text(error ?: "Source discovery failed", color = Color(0xFFFF9C9C), fontSize = 14.sp)
                }

                !searching && playable.isEmpty() -> {
                    Text(
                        text = "No directly playable sources found.",
                        color = TvDesign.Muted,
                        fontSize = 14.sp,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (searching) {
                            item {
                                Text(
                                    text = progress,
                                    color = TvDesign.Dim,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        items(
                            items = playable,
                            key = { it.url ?: "${it.providerId}:${it.name}" },
                        ) { source ->
                            SourceRow(
                                source = source,
                                onClick = { bundle?.let { onPlay(it, source) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: StreamSource,
    onClick: () -> Unit,
) {
    var focused by remember(source.url, source.name) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth(.72f)
            .background(
                color = if (focused) TvDesign.White.copy(alpha = .12f) else TvDesign.Surface.copy(alpha = .78f),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) TvDesign.White else TvDesign.White.copy(alpha = .08f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = source.name,
                color = TvDesign.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.providerName,
                color = TvDesign.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Text(
            text = listOfNotNull(source.quality, source.codec, source.hdr).joinToString("  •  ").ifBlank { "Auto" },
            color = if (focused) TvDesign.White else TvDesign.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
