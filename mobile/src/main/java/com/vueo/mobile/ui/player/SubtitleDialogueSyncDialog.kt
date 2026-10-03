package com.vueo.mobile.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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

/** Live offset capture. Keeps playback running; no pause, seek or rollback on dismissal. */
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
    var cues by remember(track?.url) { mutableStateOf<List<SubtitleDialogue>?>(null) }
    var error by remember(track?.url) { mutableStateOf<String?>(null) }
    var result by remember(track?.url) { mutableStateOf<Int?>(null) }
    var selectedIndex by remember(track?.url) { mutableIntStateOf(-1) }
    val latestApply by rememberUpdatedState(onApply)
    LaunchedEffect(track?.url) {
        if (track == null) {
            error = "Select an external subtitle first. Embedded subtitles cannot supply dialogue timestamps; use manual Sync."
            return@LaunchedEffect
        }
        player.play()
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
    fun capture(index: Int, cue: SubtitleDialogue) {
        val videoMs = player.currentPosition
        if (!player.isPlaying) {
            error = "Wait until video is playing before syncing."
            return
        }
        val offset = SubtitleDialogueSync.offset(videoMs, cue.startMs)
        if (offset == null) {
            error = "Difference exceeds 60 seconds. Choose a closer dialogue or another subtitle version."
        } else {
            latestApply(offset)
            selectedIndex = index
            result = offset
            error = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= 30) {
                window?.insetsController?.apply {
                    hide(WindowInsets.Type.systemBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.5f).fillMaxHeight()
                    .background(Color(0xF5202124)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Live Sync", color = Color.White)
                Text("Select the line when you hear it start. Select another line to adjust again.", color = Color.LightGray)
                result?.let { Text("Subtitle offset: %+.2fs".format(it / 1000.0), color = Color.White) }
                error?.let { Text(it, color = Color(0xFFFFAAAA)) }
                val loaded = cues
                if (loaded == null) {
                    if (error == null) Text("Loading subtitle…", color = Color.White)
                    Spacer(Modifier.weight(1f))
                } else {
                    val nearest = remember(loaded) {
                        val rawPosition = (player.currentPosition - delayMs).coerceAtLeast(0L)
                        loaded.indices.minByOrNull { kotlin.math.abs(loaded[it].startMs - rawPosition) } ?: 0
                    }
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = nearest)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(loaded) { index, cue ->
                            var focused by remember(index) { mutableStateOf(false) }
                            val selected = selectedIndex == index
                            val background = when {
                                focused -> Color(0xFF62656A)
                                selected -> Color.White
                                else -> Color(0xFF303236)
                            }
                            Button(
                                onClick = { capture(index, cue) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = background,
                                    contentColor = if (selected && !focused) Color.Black else Color.White,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                                    .onFocusChanged { focused = it.isFocused },
                            ) {
                                val seconds = cue.startMs / 1000
                                Text(
                                    "%d:%02d  %s".format(seconds / 60, seconds % 60, cue.text),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
            }
        }
    }
}
