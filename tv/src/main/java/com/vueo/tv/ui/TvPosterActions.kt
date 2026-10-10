package com.vueo.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.tv.ui.motion.TvMotionDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryStore
import kotlinx.coroutines.delay

/** Handles remote OK as click-on-release and opens poster actions on hold. */
internal fun Modifier.tvPosterActivation(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = composed {
    var longPressTriggered by remember { mutableStateOf(false) }

    onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        val activationKey =
            code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER ||
                code == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!activationKey) return@onPreviewKeyEvent false

        when (event.type) {
            KeyEventType.KeyDown -> {
                if (
                    event.nativeKeyEvent.repeatCount > 0 &&
                    !longPressTriggered
                ) {
                    longPressTriggered = true
                    onLongClick()
                }
                true
            }

            KeyEventType.KeyUp -> {
                if (!longPressTriggered) onClick()
                longPressTriggered = false
                true
            }

            else -> true
        }
    }
}

@Composable
internal fun TvPosterActionDialog(
    media: MediaItem,
    libraryStore: LibraryStore,
    continueEntry: LibraryPlaybackEntry? = null,
    onOpenDetails: () -> Unit,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var watchlisted by remember(media.id, media.type) {
        mutableStateOf(libraryStore.isWatchlisted(media))
    }
    var watched by remember(media.id, media.type) {
        mutableStateOf(libraryStore.isMarkedWatched(media))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val finish = com.vueo.tv.ui.motion.LocalTvMotionFinish.current
        Column(
            modifier = Modifier
                .width(430.dp)
                .background(TvDesign.SurfaceRaised, RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    TvDesign.White.copy(alpha = .14f),
                    RoundedCornerShape(20.dp),
                )
                .padding(24.dp),
        ) {
            Text(
                text = media.name,
                color = TvDesign.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Poster options",
                color = TvDesign.Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))

            val actions = buildList {
                add("Open details" to {
                    finish { onDismiss(); onOpenDetails() }
                })
                add(
                    (if (watchlisted) "Remove from My List" else "Add to My List") to {
                        watchlisted = libraryStore.toggleWatchlist(media)
                        onChanged()
                    }
                )
                add(
                    (if (watched) "Mark as unwatched" else "Mark as watched") to {
                        watched = !watched
                        libraryStore.setMarkedWatched(media, watched)
                        onChanged()
                    }
                )
                if (continueEntry != null) {
                    add("Remove from Continue Watching" to {
                        libraryStore.removeFromContinueWatching(continueEntry)
                        onChanged()
                        finish { onDismiss() }
                    })
                }
            }

            actions.forEachIndexed { index, (label, action) ->
                TvPosterActionRow(
                    label = label,
                    requestInitialFocus = index == 0,
                    onClick = action,
                )
                if (index != actions.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TvPosterActionRow(
    label: String,
    requestInitialFocus: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var activationArmed by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            delay(80L)
            runCatching { requester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                val activationKey =
                    code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!activationKey) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // Only a fresh press inside the dialog arms an action.
                        // The release from the hold that opened the dialog is
                        // therefore ignored instead of activating row one.
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            activationArmed = true
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (activationArmed) onClick()
                        activationArmed = false
                        true
                    }
                    else -> true
                }
            }
            .clickable(onClick = onClick)
            .background(
                if (focused) TvDesign.White.copy(alpha = .14f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .border(
                if (focused) 1.dp else 0.dp,
                if (focused) TvDesign.White.copy(alpha = .72f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = TvDesign.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text("›", color = TvDesign.Muted, fontSize = 19.sp)
    }
}
