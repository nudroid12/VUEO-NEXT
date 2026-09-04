package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

private val SecurityBlack = Color(0xFF050706)
private val SecurityPanel = Color(0xFF111412)
private val SecurityMuted = Color(0xFFAAB2AD)

@Composable
fun TvPinEntryOverlay(
    title: String,
    subtitle: String,
    errorText: String? = null,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val requesters = remember { List(PIN_KEY_COUNT) { FocusRequester() } }

    BackHandler(onBack = onCancel)

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { requesters.first().requestFocus() }
    }

    fun appendDigit(digit: Char) {
        if (pin.length >= PIN_LENGTH) return
        val next = pin + digit
        pin = next
        if (next.length == PIN_LENGTH) {
            onComplete(next)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(80f)
                .background(SecurityBlack.copy(alpha = 0.985f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(560.dp)
                    .background(SecurityPanel, RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = SecurityMuted,
                fontSize = 15.sp,
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier =
                            Modifier
                                .size(23.dp)
                                .background(
                                    if (filled) Color.White else Color.Transparent,
                                    CircleShape,
                                )
                                .border(
                                    2.dp,
                                    if (filled) Color.White else Color.White.copy(alpha = 0.30f),
                                    CircleShape,
                                ),
                    )
                }
            }

            if (!errorText.isNullOrBlank()) {
                Spacer(Modifier.height(13.dp))
                Text(
                    text = errorText,
                    color = Color(0xFFFF8A80),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(26.dp))

            val keys =
                listOf(
                    "1", "2", "3",
                    "4", "5", "6",
                    "7", "8", "9",
                    "Delete", "0", "Cancel",
                )

            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEachIndexed { columnIndex, label ->
                        val index = rowIndex * 3 + columnIndex
                        PinKey(
                            label = label,
                            requester = requesters[index],
                            onMove = { direction ->
                                val next =
                                    when (direction) {
                                        PinMove.Left -> if (columnIndex > 0) index - 1 else index
                                        PinMove.Right -> if (columnIndex < 2) index + 1 else index
                                        PinMove.Up -> if (rowIndex > 0) index - 3 else index
                                        PinMove.Down -> if (rowIndex < 3) index + 3 else index
                                    }

                                if (next != index) {
                                    runCatching { requesters[next].requestFocus() }
                                    true
                                } else {
                                    false
                                }
                            },
                            onClick = {
                                when (label) {
                                    "Delete" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "Cancel" -> onCancel()
                                    else -> appendDigit(label.first())
                                }
                            },
                        )
                    }
                }

                if (rowIndex < 3) {
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

private enum class PinMove {
    Left,
    Right,
    Up,
    Down,
}

@Composable
private fun PinKey(
    label: String,
    requester: FocusRequester,
    onMove: (PinMove) -> Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "pinKeyScale",
    )

    Box(
        modifier =
            Modifier
                .width(128.dp)
                .height(64.dp)
                .scale(scale)
                .background(
                    if (focused) {
                        Color.White.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.055f)
                    },
                    RoundedCornerShape(14.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color =
                        if (focused) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.10f)
                        },
                    shape = RoundedCornerShape(14.dp),
                )
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> onMove(PinMove.Left)
                            Key.DirectionRight -> onMove(PinMove.Right)
                            Key.DirectionUp -> onMove(PinMove.Up)
                            Key.DirectionDown -> onMove(PinMove.Down)
                            else -> false
                        }
                    }
                }
                .clickable(onClick = onClick)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (label.length == 1) 24.sp else 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val PIN_LENGTH = 4
private const val PIN_KEY_COUNT = 12
