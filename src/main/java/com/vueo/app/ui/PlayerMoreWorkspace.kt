package com.vueo.app.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.app.core.storage.PlayerVideoFit

private val MoreAccent = Color(0xFFB9FF3A)
private val MoreCard = Color(0xF2181A1C)

internal enum class PlayerSleepTimerOption(
    val label: String,
    val minutes: Int? = null,
    val endOfEpisode: Boolean = false,
) {
    OFF("Off"),
    MINUTES_15("15 min", minutes = 15),
    MINUTES_30("30 min", minutes = 30),
    MINUTES_45("45 min", minutes = 45),
    MINUTES_60("60 min", minutes = 60),
    END_OF_EPISODE("End of episode", endOfEpisode = true),
}

@Composable
internal fun PlayerMoreWorkspace(
    playbackSpeed: Float,
    videoFit: PlayerVideoFit,
    sleepTimer: PlayerSleepTimerOption,
    sleepTimerRemainingSeconds: Long?,
    autoPlayNextEpisode: Boolean,
    skipSegmentsEnabled: Boolean,
    contentWarningsEnabled: Boolean,
    onPlaybackSpeedChange: (Float) -> Unit,
    onVideoFitChange: (PlayerVideoFit) -> Unit,
    onSleepTimerChange: (PlayerSleepTimerOption) -> Unit,
    onAutoPlayNextEpisodeChange: (Boolean) -> Unit,
    onSkipSegmentsChange: (Boolean) -> Unit,
    onContentWarningsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepMoreWorkspaceImmersive()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .28f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .98f),
                        .72f to Color.Black.copy(alpha = .88f),
                        1f to Color.Black.copy(alpha = .58f),
                    )
                )
                .clickable(onClick = onDismiss)
                .padding(horizontal = 34.dp, vertical = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(.88f)
                    .fillMaxHeight()
                    .align(Alignment.Center),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MoreSectionCard(
                        title = "Playback",
                        modifier = Modifier.weight(1.05f),
                    ) {
                        MoreLabel("Speed")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                0.5f,
                                0.75f,
                                1f,
                                1.25f,
                                1.5f,
                                2f,
                            ).forEach { speed ->
                                MoreChoiceChip(
                                    label = formatSpeed(speed),
                                    selected = playbackSpeed == speed,
                                    onClick = {
                                        onPlaybackSpeedChange(speed)
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        MoreLabel("Video fit")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlayerVideoFit.values().forEach { option ->
                                MoreChoiceChip(
                                    label = option.label,
                                    selected = videoFit == option,
                                    onClick = {
                                        onVideoFitChange(option)
                                    },
                                )
                            }
                        }
                        Text(
                            text = when (videoFit) {
                                PlayerVideoFit.FIT ->
                                    "Shows the complete frame."
                                PlayerVideoFit.FILL ->
                                    "Fills the screen dimensions."
                                PlayerVideoFit.ZOOM ->
                                    "Crops edges to fill without stretching."
                            },
                            color = Color.White.copy(alpha = .42f),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }

                    MoreSectionCard(
                        title = "Sleep timer",
                        subtitle = sleepTimerStatus(
                            sleepTimer,
                            sleepTimerRemainingSeconds,
                        ),
                        modifier = Modifier.weight(.9f),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlayerSleepTimerOption.values().forEach { option ->
                                MoreOptionRow(
                                    label = option.label,
                                    selected = sleepTimer == option,
                                    onClick = {
                                        onSleepTimerChange(option)
                                    },
                                )
                            }
                        }
                    }

                    MoreSectionCard(
                        title = "Behaviour",
                        modifier = Modifier.weight(1f),
                    ) {
                        MoreToggleRow(
                            label = "Auto-play next episode",
                            checked = autoPlayNextEpisode,
                            onCheckedChange =
                                onAutoPlayNextEpisodeChange,
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

@Composable
private fun MoreSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = MoreAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MoreLabel(label: String) {
    Text(
        text = label,
        color = Color.White.copy(alpha = .60f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun MoreChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color(0xFF151A11) else Color.White.copy(alpha = .74f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (selected) MoreAccent else Color.White.copy(alpha = .06f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun MoreOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MoreAccent.copy(alpha = .12f)
        } else {
            Color.White.copy(alpha = .035f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MoreAccent.copy(alpha = .42f)
            } else {
                Color.White.copy(alpha = .06f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(MoreAccent, RoundedCornerShape(50)),
                )
            }
        }
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

private fun sleepTimerStatus(
    option: PlayerSleepTimerOption,
    remainingSeconds: Long?,
): String = when {
    option == PlayerSleepTimerOption.END_OF_EPISODE ->
        "Stops when this episode ends"
    remainingSeconds != null -> {
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L
        "%d:%02d remaining".format(minutes, seconds)
    }
    else -> "Not active"
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
