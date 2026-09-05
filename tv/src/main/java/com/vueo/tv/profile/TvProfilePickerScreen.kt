package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.R
import kotlinx.coroutines.delay

private val PickerBlack = Color(0xFF050706)
private val PickerPanel = Color(0xFF101412)
private val PickerPanelRaised = Color(0xFF171C19)
private val PickerGreen = Color(0xFF84E100)
private val PickerMuted = Color(0xFFAAB2AD)
private val PickerStroke = Color.White.copy(alpha = 0.14f)

private val TvProfileAvatars =
    mapOf(
        "avatar_man_1" to R.drawable.avatar_man_1,
        "avatar_man_2" to R.drawable.avatar_man_2,
        "avatar_woman_1" to R.drawable.avatar_woman_1,
        "avatar_woman_2" to R.drawable.avatar_woman_2,
        "avatar_boy_1" to R.drawable.avatar_boy_1,
        "avatar_boy_2" to R.drawable.avatar_boy_2,
        "avatar_girl_1" to R.drawable.avatar_girl_1,
        "avatar_girl_2" to R.drawable.avatar_girl_2,
    )

@Composable
fun TvProfilePickerScreen(
    profileStore: ProfileStore,
    onProfileSelected: (String) -> Unit,
) {
    val profiles = remember { profileStore.profiles() }
    val activeProfileId = remember { profileStore.activeProfileId() }
    val firstRequester = remember { FocusRequester() }
    var lockedProfile by remember { mutableStateOf<VueoProfile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinResetToken by remember { mutableIntStateOf(0) }
    var askOnStartup by remember {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }

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
                            Color(0xFF111513),
                            Color(0xFF080B09),
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
            Image(
                painter = painterResource(R.drawable.vueo_tv_logo),
                contentDescription = "VUEO",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(148.dp)
                    .height(42.dp),
            )
            Spacer(Modifier.height(18.dp))

            Text(
                text = "Who's Watching?",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Choose a profile to continue",
                color = PickerMuted,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(32.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(
                    profiles,
                    key = { _, profile -> profile.id },
                ) { index, profile ->
                    PickerProfileCard(
                        profile = profile,
                        active = profile.id == activeProfileId,
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

            Spacer(Modifier.height(34.dp))

            TvPickerToggleRow(
                title = "Ask on startup",
                subtitle = "Show Who's Watching when VUEO launches",
                checked = askOnStartup,
                onClick = {
                    askOnStartup = !askOnStartup
                    profileStore.setAskWhoIsWatchingOnStartup(askOnStartup)
                },
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Add, edit, secure or delete profiles from Profile settings.",
                color = PickerMuted.copy(alpha = .76f),
                fontSize = 12.sp,
            )
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
    active: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.065f else 1f,
        label = "pickerProfileScale",
    )
    val avatarDrawable = TvProfileAvatars[profile.avatar]

    Column(
        modifier =
            modifier
                .width(180.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(
                        if (focused) Color.White.copy(alpha = 0.11f) else PickerPanel,
                    )
                    .border(
                        width = if (focused) 4.dp else if (active) 2.dp else 1.dp,
                        color =
                            when {
                                focused -> Color.White
                                active -> PickerGreen
                                else -> PickerStroke
                            },
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarDrawable != null) {
                Image(
                    painter = painterResource(avatarDrawable),
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text =
                        profile.name
                            .trim()
                            .firstOrNull()
                            ?.uppercase()
                            ?: "V",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            if (active) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 7.dp),
                    shape = RoundedCornerShape(50),
                    color = PickerGreen,
                ) {
                    Text(
                        text = "ACTIVE",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profile.isKids) {
                TvProfileBadge("KIDS")
            }
            if (locked) {
                TvProfileBadge("PIN")
            }
            if (!profile.isKids && !locked) {
                Text(
                    text = if (active) "Current profile" else "Profile",
                    color = if (active) PickerGreen else PickerMuted,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TvProfileBadge(
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = PickerGreen.copy(alpha = .10f),
        border = BorderStroke(1.dp, PickerGreen.copy(alpha = .34f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = PickerGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TvPickerToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "pickerToggleScale",
    )

    Row(
        modifier = Modifier
            .width(520.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .background(
                if (focused) PickerPanelRaised else PickerPanel,
                RoundedCornerShape(18.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else PickerStroke,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = PickerMuted,
                fontSize = 11.sp,
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = if (checked) PickerGreen else Color.White.copy(alpha = .08f),
            border = BorderStroke(
                1.dp,
                if (checked) PickerGreen else Color.White.copy(alpha = .18f),
            ),
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                color = if (checked) Color.Black else PickerMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
