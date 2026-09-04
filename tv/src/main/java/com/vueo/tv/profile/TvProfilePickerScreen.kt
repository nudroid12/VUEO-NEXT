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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.VueoProfile
import kotlinx.coroutines.delay

private val PickerBlack = Color(0xFF050706)
private val PickerPanel = Color(0xFF101412)
private val PickerYellow = Color(0xFFD6FF00)
private val PickerGreen = Color(0xFF84E100)
private val PickerMuted = Color(0xFFAAB2AD)

@Composable
fun TvProfilePickerScreen(
    profileStore: ProfileStore,
    onProfileSelected: (String) -> Unit,
) {
    val profiles = remember { profileStore.profiles() }
    val firstRequester = remember { FocusRequester() }
    var lockedProfile by remember { mutableStateOf<VueoProfile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }

    BackHandler { }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0A100C),
                            PickerBlack,
                            PickerBlack,
                        )
                    )
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Who's Watching?",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose a profile to continue",
                color = PickerMuted,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(34.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(
                    profiles,
                    key = { _, profile -> profile.id },
                ) { index, profile ->
                    PickerProfileCard(
                        profile = profile,
                        locked = profileStore.hasProfilePin(profile.id),
                        modifier =
                            if (index == 0) {
                                Modifier.focusRequester(firstRequester)
                            } else {
                                Modifier
                            },
                        onClick = {
                            if (profileStore.hasProfilePin(profile.id)) {
                                pinError = null
                                lockedProfile = profile
                            } else if (profileStore.setActiveProfile(profile.id)) {
                                onProfileSelected(profile.id)
                            }
                        },
                    )
                }
            }
        }

        val target = lockedProfile
        if (target != null) {
            key(target.id, pinResetToken) {
                TvPinEntryOverlay(
                    title = "Unlock ${target.name}",
                    subtitle = "Enter the 4-digit profile PIN",
                    errorText = pinError,
                    onComplete = { pin ->
                        if (profileStore.verifyProfilePin(target.id, pin)) {
                            pinError = null
                            lockedProfile = null
                            if (profileStore.setActiveProfile(target.id)) {
                                onProfileSelected(target.id)
                            }
                        } else {
                            pinError = "Incorrect PIN"
                            pinResetToken += 1
                        }
                    },
                    onCancel = {
                        pinError = null
                        lockedProfile = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PickerProfileCard(
    profile: VueoProfile,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        label = "pickerProfileScale",
    )

    Column(
        modifier =
            modifier
                .width(170.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .width(128.dp)
                    .height(128.dp)
                    .background(PickerPanel, CircleShape)
                    .border(
                        if (focused) 3.dp else 1.dp,
                        if (focused) PickerYellow else Color.White.copy(alpha = 0.12f),
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    profile.name
                        .trim()
                        .firstOrNull()
                        ?.uppercase()
                        ?: "V",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
            )

            if (locked) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 7.dp)
                            .background(
                                PickerBlack.copy(alpha = 0.88f),
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                PickerGreen.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "PIN",
                        color = PickerGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.isKids) {
                Text(
                    text = "  KIDS",
                    color = PickerGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
