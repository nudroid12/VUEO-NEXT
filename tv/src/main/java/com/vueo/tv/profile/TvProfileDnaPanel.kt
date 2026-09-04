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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vueo.shared.core.dna.UserDnaEngine
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.tv.library.TvLibraryStore
import com.vueo.tv.player.TvPlaybackStore
import kotlinx.coroutines.delay

private val DnaBlack = Color(0xFF050706)
private val DnaPanel = Color(0xFF101412)
private val DnaMuted = Color(0xFFAAB2AD)
private val DnaGreen = Color(0xFF84E100)

@Composable
fun TvProfileDnaPanel(
    profileStore: ProfileStore,
    onDismiss: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activeProfile = remember { profileStore.activeProfile() }
    val dnaPreferences =
        remember(context) {
            UserDnaPreferences(
                context = context.applicationContext,
                prefsName = TvPlaybackStore.SETTINGS_PREFS_NAME,
            )
        }
    val dnaEnabled = remember(activeProfile.id) {
        dnaPreferences.userDnaEnabled(activeProfile.id)
    }
    val snapshot =
        remember(activeProfile.id) {
            runCatching {
                UserDnaEngine(
                    LibraryStore(
                        context = context.applicationContext,
                        prefsName = TvLibraryStore.PREFS_NAME,
                        watchlistStorageKey = TvLibraryStore.KEY_LIBRARY,
                        profileStore = profileStore,
                    )
                ).build()
            }.getOrNull()
        }
    val firstActionRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { firstActionRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(70f)
                .background(Color.Black.copy(alpha = 0.58f)),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(470.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                DnaPanel.copy(alpha = 0.98f),
                                DnaBlack,
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                    )
                    .padding(horizontal = 34.dp, vertical = 38.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(66.dp)
                            .height(66.dp)
                            .background(Color.White.copy(alpha = 0.10f), CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.82f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = activeProfile.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Column {
                    Text(
                        text = activeProfile.name,
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (activeProfile.isKids) "Kids profile" else "Active profile",
                        color = DnaMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "VUEO DNA",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )

                val status =
                    when {
                        !dnaEnabled -> "Off"
                        snapshot == null -> "Unavailable"
                        else -> snapshot.readiness.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                Text(
                    text =
                        if (dnaEnabled && snapshot != null) {
                            "$status • ${snapshot.confidencePercent}% confidence"
                        } else {
                            status
                        },
                    color = if (dnaEnabled) DnaGreen else DnaMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Built locally from this profile's History and My List.",
                    color = DnaMuted,
                    fontSize = 12.sp,
                )
            }

            if (dnaEnabled && snapshot != null && snapshot.hasUsefulData) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "Taste",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    snapshot.topGenres.take(4).forEach { genre ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = genre.name,
                                color = DnaMuted,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = "${genre.percent}%",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DnaStat(
                        label = "Watched",
                        value = snapshot.behavior.watchedTitles.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    DnaStat(
                        label = "My List",
                        value = snapshot.behavior.myListTitles.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    DnaStat(
                        label = "Completed",
                        value = "${snapshot.behavior.completionRatePercent}%",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else if (dnaEnabled) {
                Text(
                    text = "Keep watching and adding titles to My List. VUEO DNA will become more accurate over time.",
                    color = DnaMuted,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            DnaAction(
                label = "Switch Profile",
                requester = firstActionRequester,
                onClick = onSwitchProfile,
            )
            DnaAction(
                label = "Profile Settings",
                onClick = onOpenSettings,
            )

            Text(
                text = "Press Back to close",
                color = DnaMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun DnaStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            color = DnaMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun DnaAction(
    label: String,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.025f else 1f,
        label = "dnaActionScale",
    )
    val focusModifier =
        if (requester != null) {
            Modifier.focusRequester(requester)
        } else {
            Modifier
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(focusModifier)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable()
                .background(
                    if (focused) Color.White.copy(alpha = 0.17f) else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(12.dp),
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
