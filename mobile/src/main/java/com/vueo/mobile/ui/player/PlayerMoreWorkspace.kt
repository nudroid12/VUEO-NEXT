package com.vueo.mobile.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.mobile.core.storage.PlayerVideoFit

private val MoreAccent = Color(0xFFB9FF3A)
private val MoreCard = Color(0xF2181A1C)
private val MorePlaybackSpeeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)

@Composable
internal fun PlayerMoreWorkspace(
    visible: Boolean,
    playbackSpeed: Float,
    videoFit: PlayerVideoFit,
    autoPlayNextEpisode: Boolean,
    skipSegmentsEnabled: Boolean,
    contentWarningsEnabled: Boolean,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVideoFitChange: (PlayerVideoFit) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
    onSkipSegmentsChange: (Boolean) -> Unit,
    onContentWarningsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    VueoMotionDialogHost(
        visible = visible,
        onDismissRequest = onDismiss,
    ) {
        KeepMoreWorkspaceImmersive()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .28f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .18f),
                        .48f to Color.Black.copy(alpha = .45f),
                        1f to Color.Black.copy(alpha = .94f),
                    )
                )
                .clickable(onClick = onDismiss)
                .padding(start = 34.dp, end = 24.dp, top = 18.dp, bottom = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(.55f)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            ) {
                Text(
                    text = "More",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Playback and session controls",
                    color = Color.White.copy(alpha = .52f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Spacer(Modifier.height(12.dp))
                MoreSectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            MoreSectionTitle("Playback")
                            Spacer(Modifier.height(12.dp))
                            MoreCycleRow(
                                label = "Speed",
                                value = formatSpeed(playbackSpeed),
                                onClick = {
                                    onPlaybackSpeedChange(nextPlaybackSpeed(playbackSpeed))
                                },
                            )
                            Spacer(Modifier.height(7.dp))
                            MoreCycleRow(
                                label = "Video fit",
                                value = videoFit.label,
                                onClick = {
                                    onVideoFitChange(nextVideoFit(videoFit))
                                },
                            )
                            Text(
                                text = videoFitDescription(videoFit),
                                color = Color.White.copy(alpha = .42f),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = .08f))
                        )

                        Column(Modifier.weight(1.08f)) {
                            MoreSectionTitle("Behaviour")
                            Spacer(Modifier.height(5.dp))
                            MoreToggleRow(
                                label = "Auto-play next episode",
                                checked = autoPlayNextEpisode,
                                onCheckedChange = onAutoPlayNextEpisodeChange,
                            )
                            MoreToggleRow(
                                label = "Skip intro and ending",
                                checked = skipSegmentsEnabled,
                                onCheckedChange = onSkipSegmentsChange,
                            )
                            MoreToggleRow(
                                label = "Content warnings",
                                checked = contentWarningsEnabled,
                                onCheckedChange = onContentWarningsChange,
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = onReset,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Reset player controls")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(18.dp),
        color = MoreCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun MoreSectionTitle(label: String) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun MoreCycleRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White.copy(alpha = .055f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = .78f),
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MoreAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(
                    MoreAccent.copy(alpha = .12f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun MoreToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 42.dp, height = 28.dp),
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }

private fun nextPlaybackSpeed(current: Float): Float {
    val index = MorePlaybackSpeeds.indexOf(current)
    return MorePlaybackSpeeds[(index + 1) % MorePlaybackSpeeds.size]
}

private fun nextVideoFit(current: PlayerVideoFit): PlayerVideoFit {
    val options = PlayerVideoFit.values()
    return options[(options.indexOf(current) + 1) % options.size]
}

private fun videoFitDescription(videoFit: PlayerVideoFit): String = when (videoFit) {
    PlayerVideoFit.FIT -> "Shows the complete frame."
    PlayerVideoFit.FILL -> "Fills the screen dimensions."
    PlayerVideoFit.ZOOM -> "Crops edges to fill without stretching."
}

@Composable
private fun KeepMoreWorkspaceImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val decor = window?.decorView
        val previousFlags = decor?.systemUiVisibility ?: 0

        window?.setDimAmount(0f)
        if (Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
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
