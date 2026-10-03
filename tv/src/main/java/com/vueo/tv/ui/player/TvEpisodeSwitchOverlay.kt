package com.vueo.tv.player

import com.vueo.shared.core.player.PlayerSourceDisplay

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.TvNetworkImage
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.StreamSource

/** Discovery and playback preparation stay over the mounted TV player. */
@Composable
internal fun TvEpisodeSwitchOverlay(
    episode: EpisodeItem,
    fallbackImage: String?,
    error: String?,
    showSources: Boolean,
    sources: List<StreamSource>,
    onRetry: () -> Unit,
    onShowSources: () -> Unit,
    onSelectSource: (StreamSource) -> Unit,
    onCancel: () -> Unit,
) {
    val rootRequester = remember { FocusRequester() }
    val actionRequester = remember { FocusRequester() }
    val backCaptured = remember { booleanArrayOf(false) }
    BackHandler { onCancel() }
    LaunchedEffect(episode.id, error, showSources, sources.isEmpty()) {
        val target = if (error != null || showSources) actionRequester else rootRequester
        target.requestTvFocus()
    }
    Box(
        Modifier.fillMaxSize()
            .focusRequester(rootRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) backCaptured[0] = true
                if (event.type == KeyEventType.KeyUp && backCaptured[0]) {
                    backCaptured[0] = false
                    if (!event.nativeKeyEvent.isCanceled) onCancel()
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            Modifier.padding(end = 32.dp, bottom = 37.dp)
                .width(360.dp)
                .background(Color(0xFF171A20).copy(alpha = .94f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(16.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvNetworkImage(
                    url = episode.thumbnail?.takeIf { it.isNotBlank() } ?: fallbackImage,
                    contentDescription = episode.title,
                    modifier = Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    fallback = Color(0xFF292D34),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Next Episode", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                    Text("S${episode.season} E${episode.episode} • ${episode.title.ifBlank { "Episode ${episode.episode}" }}",
                        color = Color.White, fontSize = 14.sp, lineHeight = 18.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (error == null && !showSources) {
                    CircularProgressIndicator(color = TvDesign.Accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            when {
                showSources -> {
                    Text("Choose a source", color = Color.White)
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        sources.forEachIndexed { index, source ->
                            Button(
                                onClick = { onSelectSource(source) },
                                modifier = if (index == 0) Modifier.focusRequester(actionRequester) else Modifier,
                            ) {
                                Text("${PlayerSourceDisplay.titleWithQuality(source)}\n${PlayerSourceDisplay.details(source)}", maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Button(onClick = onRetry, modifier = if (sources.isEmpty()) Modifier.focusRequester(actionRequester) else Modifier) {
                            Text("Retry discovery")
                        }
                    }
                }
                error != null -> {
                    Text(error, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onRetry, modifier = Modifier.focusRequester(actionRequester)) { Text("Retry") }
                        Button(onClick = onShowSources) { Text("Sources") }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Loading episode…", color = TvDesign.Accent, fontSize = 12.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Back to cancel", color = Color.White.copy(alpha = .65f), fontSize = 10.sp,
                            modifier = Modifier.padding(start = 12.dp), maxLines = 1)
                    }
                }
            }
            if (showSources || error != null) {
                Text("Back to cancel", color = Color.White.copy(alpha = .65f), fontSize = 10.sp)
            }
        }
    }
}
