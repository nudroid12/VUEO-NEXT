package com.vueo.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.SubtitleDialogue
import com.vueo.shared.core.player.SubtitleDialogueSync
import com.vueo.shared.core.player.SubtitleSyncReadFailure
import com.vueo.shared.core.player.SubtitleSessionDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout


/** Modal session: dismissal cancels loading and restores playback intent unless sync was applied. */
@Composable
internal fun SubtitleDialogueSyncDialog(
    player: ExoPlayer,
    track: SubtitleTrack?,
    headers: Map<String, String>,
    delayMs: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val originalPlaying = remember { player.playWhenReady }
    val originalDelay = remember { delayMs }
    val initialPosition = remember { player.currentPosition }
    var applied by remember { mutableStateOf(false) }
    val latestApplied by rememberUpdatedState(applied)
    var stage by remember { mutableIntStateOf(0) }
    var cues by remember { mutableStateOf<List<SubtitleDialogue>?>(null) }
    var chosen by remember { mutableStateOf<SubtitleDialogue?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableIntStateOf(0) }
    var undone by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        if (track != null) player.pause()
        onDispose { if (!latestApplied) runCatching { player.playWhenReady = originalPlaying } }
    }
    LaunchedEffect(track?.url) {
        if (track == null) {
            error = "Select an external subtitle first. Embedded subtitles cannot supply dialogue timestamps; use manual Sync."
            return@LaunchedEffect
        }
        try {
            val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(10_000).setReadTimeoutMs(10_000)
                .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers)
                .setUserAgent(headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: "VUEO subtitle sync")
            cues = withTimeout(20_000L) {
                SubtitleDialogueSync.load(track.url, SubtitleSessionDataSource.Factory(DefaultDataSource.Factory(context, http)))
            }
        } catch (cancelled: CancellationException) {
            if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                error = "Subtitle loading timed out. Try again or use manual Sync."
                SubtitleDialogueSync.diagnostic(track.url, "FAILED", "reason=DEADLINE_TIMEOUT")
            } else throw cancelled
        } catch (failure: SubtitleSyncReadFailure) {
            error = failure.userMessage
        } catch (failure: Exception) {
            error = "Could not start subtitle sync. Check Performance Diagnostic."
            SubtitleDialogueSync.diagnostic(track.url, "FAILED", "reason=SETUP type=${failure.javaClass.simpleName}")
        }
    }
    fun capture() {
        if (stage != 1) return
        if (!player.isPlaying) {
            error = "Wait until video is playing before syncing."
            return
        }
        val cue = chosen ?: return
        val offset = SubtitleDialogueSync.offset(player.currentPosition, cue.startMs)
        if (offset == null) {
            error = "Difference exceeds 60 seconds. Choose a closer dialogue or another subtitle version."
        } else {
            onApply(offset)
            result = offset
            applied = true
            stage = 2
            error = null
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(.90f)
                
                .background(Color(0xFF202124), RoundedCornerShape(18.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sync by dialogue", color = Color.White)
            when (stage) {
                0 -> {
                    Text("Pick a line you can recognise. Playback is paused while choosing.", color = Color.LightGray)
                    val loaded = cues
                    if (loaded == null && error == null) Text("Loading subtitle…", color = Color.White)
                    if (loaded != null) {
                        val nearest = remember(loaded) {
                            val rawPosition = (initialPosition - originalDelay).coerceAtLeast(0L)
                            loaded.indices.minByOrNull { kotlin.math.abs(loaded[it].startMs - rawPosition) } ?: 0
                        }
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = nearest)

                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), state = listState) {
                            itemsIndexed(loaded) { index, cue ->
                                Button(
                                    onClick = { chosen = cue; error = null; stage = 1; player.play() },
                                    modifier = Modifier.fillMaxWidth() ,
                                ) {
                                    val seconds = cue.startMs / 1000
                                    Text("%d:%02d  %s".format(seconds / 60, seconds % 60, cue.text), maxLines = 3)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Text(chosen?.text.orEmpty(), color = Color.White, maxLines = 3)
                    Text("Tap Sync now when this dialogue starts.", color = Color.LightGray)
                    Text("Wait for playback and the start of the spoken line. Back cancels.", color = Color.LightGray)
                    Button(onClick = { capture() }, modifier = Modifier ) { Text("Sync now") }
                    TextButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); player.play(); error = null }) { Text("Replay 10 seconds") }
                    TextButton(onClick = { player.pause(); stage = 0; error = null }) { Text("Choose another line") }
                }
                2 -> {
                    Text(if (undone) "Previous sync restored." else "Subtitle offset: %+.2fs".format(result / 1000.0), color = Color.White)
                    TextButton(onClick = { onApply(originalDelay); undone = true }, enabled = !undone) { Text("Undo") }
                }
            }
            error?.let { Text(it, color = Color(0xFFFFAAAA)) }
            TextButton(onClick = onDismiss) { Text(if (stage == 2) "Done" else "Cancel") }
        }
    }
}
