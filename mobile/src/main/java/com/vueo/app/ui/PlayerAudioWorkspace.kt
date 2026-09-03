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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private val AudioAccent = Color(0xFFB9FF3A)
private val AudioRowShape = RoundedCornerShape(14.dp)

@Composable
internal fun PlayerAudioWorkspace(
    tracks: List<PlayerTrackChoice>,
    automaticSelected: Boolean,
    onAutomatic: () -> Unit,
    onSelect: (PlayerTrackChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedTrack = tracks.firstOrNull { it.selected }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepAudioDialogImmersive()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .22f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .12f),
                        .52f to Color.Black.copy(alpha = .68f),
                        1f to Color.Black.copy(alpha = .96f),
                    )
                )
                .clickable(onClick = onDismiss)
                .padding(start = 24.dp, end = 40.dp, top = 20.dp, bottom = 20.dp),
        ) {
            val panelWidth = minOf(
                400.dp,
                (maxWidth * .42f).coerceAtLeast(340.dp),
            )
            val listHeight = (maxHeight - 148.dp)
                .coerceAtLeast(130.dp)
                .coerceAtMost(390.dp)

            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .heightIn(max = maxHeight - 40.dp)
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
                    width = 1.dp,
                    color = Color.White.copy(alpha = .09f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Audio",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (automaticSelected) {
                            selectedTrack?.let {
                                "Stream default • ${it.label}"
                            } ?: "Using the stream default"
                        } else {
                            selectedTrack?.let {
                                "${it.label} selected"
                            } ?: "Choose an audio track"
                        },
                        color = Color.White.copy(alpha = .52f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = listHeight),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "audio:auto") {
                            AudioTrackRow(
                                title = "Stream default",
                                detail = "Select audio automatically",
                                selected = automaticSelected,
                                onClick = onAutomatic,
                            )
                        }

                        items(tracks, key = { it.key }) { track ->
                            AudioTrackRow(
                                title = track.label,
                                detail = listOfNotNull(
                                    track.metadata?.takeIf { it.isNotBlank() },
                                    track.sourceLabel.takeIf { it.isNotBlank() },
                                ).distinct().joinToString(" • "),
                                selected = track.selected && !automaticSelected,
                                onClick = { onSelect(track) },
                            )
                        }

                        if (tracks.isEmpty()) {
                            item(key = "audio:empty") {
                                Text(
                                    text = "No selectable audio tracks are available for this stream.",
                                    color = Color.White.copy(alpha = .54f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 14.dp,
                                    ),
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
private fun AudioTrackRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AudioRowShape,
        color = if (selected) {
            AudioAccent.copy(alpha = .15f)
        } else {
            Color.White.copy(alpha = .045f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                AudioAccent.copy(alpha = .58f)
            } else {
                Color.White.copy(alpha = .08f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        color = if (selected) {
                            AudioAccent.copy(alpha = .14f)
                        } else {
                            Color.White.copy(alpha = .055f)
                        },
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = if (selected) AudioAccent else Color.White.copy(alpha = .68f),
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = if (selected) .98f else .86f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        color = Color.White.copy(alpha = .49f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = AudioAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun KeepAudioDialogImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val decor = window?.decorView
        val previousFlags = decor?.systemUiVisibility ?: 0

        window?.setDimAmount(0f)
        if (Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            decor?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        onDispose {
            if (Build.VERSION.SDK_INT < 30) {
                decor?.systemUiVisibility = previousFlags
            }
        }
    }
}
