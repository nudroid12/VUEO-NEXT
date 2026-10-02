package com.vueo.tv.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.StreamSource

/** Discovery and playback preparation stay over the mounted TV player. */
@Composable
internal fun TvEpisodeSwitchOverlay(
    episode: EpisodeItem,
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
            .background(Color.Black.copy(alpha = .72f))
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).background(Color(0xFF171A20), RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("S${episode.season} E${episode.episode} • ${episode.title}", color = Color.White, fontSize = 20.sp)
            when {
                showSources -> {
                    Text("Choose a source", color = Color.White)
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        sources.forEachIndexed { index, source ->
                            Button(
                                onClick = { onSelectSource(source) },
                                modifier = if (index == 0) Modifier.focusRequester(actionRequester) else Modifier,
                            ) {
                                Text("${source.providerName} • ${source.name}", maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    Column(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = TvDesign.Accent)
                        Text("Loading episode…", color = TvDesign.Accent, fontSize = 16.sp)
                    }
                }
            }
            Text("Back to cancel", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
        }
    }
}
