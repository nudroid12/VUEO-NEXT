package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.shared.core.dna.UserDnaAffinity
import com.vueo.shared.core.dna.UserDnaReadiness
import com.vueo.shared.core.dna.UserDnaSnapshot
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import com.vueo.shared.core.storage.VueoProfile
import com.vueo.tv.core.TvRuntime
import com.vueo.tv.ui.TvDesign
import com.vueo.tv.ui.motion.TvMotion
import kotlinx.coroutines.launch

/** TV presentation of Mobile's local-first Your DNA experience. */
@Composable
fun TvUserDnaScreen(
    runtime: TvRuntime,
    dataVersion: Int,
    onSwitchProfiles: () -> Unit,
    onBack: () -> Unit,
) {
    val profile = remember(dataVersion) { runtime.profileStore.activeProfile() }
    val snapshot = remember(profile.id, dataVersion) { runtime.dnaEngine.build() }
    val switchRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 64.dp.toPx() }

    BackHandler(onBack = onBack)
    LaunchedEffect(profile.id) {
        withFrameNanos { }
        runCatching { switchRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black)
            .padding(horizontal = 56.dp, vertical = 34.dp)
            .onPreviewKeyEvent { event ->
                val direction = when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> 1
                    KeyEvent.KEYCODE_DPAD_UP -> -1
                    else -> 0
                }
                if (direction == 0) false else {
                    if (event.type == KeyEventType.KeyDown) {
                        scrollScope.launch { scrollState.scrollBy(direction * scrollStep) }
                    }
                    true
                }
            }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Your DNA",
                color = TvDesign.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Your local taste profile, shaped by what you watch and save.",
                color = TvDesign.Muted,
                fontSize = 14.sp,
            )
        }

        DnaProfileCard(
            profile = profile,
            snapshot = snapshot,
            switchRequester = switchRequester,
            onSwitchProfiles = onSwitchProfiles,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DnaAffinityCard(
                modifier = Modifier.weight(1.05f),
                title = "Top Genres",
                subtitle = "What your recent watching and Favorites say about your taste.",
                affinities = snapshot.topGenres.take(5),
                emptyText = if (snapshot.readiness == UserDnaReadiness.STARTING) {
                    "Your DNA is just getting started. Keep watching to build your taste profile."
                } else {
                    "Not enough genre data yet."
                },
            )

            DnaTasteCard(
                modifier = Modifier.weight(.95f),
                snapshot = snapshot,
            )
        }

        DnaViewingCard(modifier = Modifier.fillMaxWidth(), snapshot = snapshot)

        Text(
            text = "Your DNA is calculated locally from this profile's History and Favorites. Nothing is sent to a server or AI API.",
            color = TvDesign.Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DnaProfileCard(
    profile: VueoProfile,
    snapshot: UserDnaSnapshot,
    switchRequester: FocusRequester,
    onSwitchProfiles: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TvDesign.SurfaceRaised.copy(alpha = .88f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DnaAvatar(profile)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    color = TvDesign.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = snapshot.readiness.displayLabel(),
                    color = TvDesign.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(TvDesign.White.copy(alpha = .10f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Text(
                text = if (profile.isKids) "Kids taste profile" else "Local taste profile",
                color = TvDesign.Muted,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DNA Strength",
                    color = TvDesign.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(250.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(TvDesign.White.copy(alpha = .10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((snapshot.confidencePercent / 100f).coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(TvDesign.White.copy(alpha = .82f)),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "${snapshot.confidencePercent}%",
                    color = TvDesign.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        var focused by remember { mutableStateOf(false) }
        val focusScale by animateFloatAsState(
            targetValue = if (focused) 1.02f else 1f,
            animationSpec = tween(
                durationMillis = if (focused) TvMotion.FOCUS_IN_MS else TvMotion.FOCUS_OUT_MS,
                easing = TvMotion.EaseOut,
            ),
            label = "DNA switch profile focus",
        )
        Box(
            modifier = Modifier
                .focusRequester(switchRequester)
                .onFocusChanged { focused = it.isFocused }
                .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
                .clip(RoundedCornerShape(12.dp))
                .border(
                    if (focused) 2.dp else 0.dp,
                    if (focused) TvDesign.Focus.copy(alpha = .92f) else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .background(if (focused) TvDesign.White else TvDesign.White.copy(alpha = .10f))
                .clickable(onClick = onSwitchProfiles)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Switch Profiles",
                color = if (focused) TvDesign.Black else TvDesign.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DnaAvatar(profile: VueoProfile) {
    val avatarDrawable = ProfileAvatarCatalog.drawableRes(profile.avatar)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(TvDesign.Surface),
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
                text = profile.name.trim().firstOrNull()?.uppercase() ?: "V",
                color = TvDesign.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DnaAffinityCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    affinities: List<UserDnaAffinity>,
    emptyText: String,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TvDesign.Surface.copy(alpha = .86f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = TvDesign.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TvDesign.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))

        if (affinities.isEmpty()) {
            Text(emptyText, color = TvDesign.Muted, fontSize = 13.sp)
        } else {
            affinities.forEach { affinity ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = affinity.name,
                        modifier = Modifier.width(130.dp),
                        color = TvDesign.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(TvDesign.Black.copy(alpha = .45f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((affinity.percent / 100f).coerceIn(0f, 1f))
                                .height(7.dp)
                                .background(TvDesign.Accent),
                        )
                    }
                    Text(
                        text = "${affinity.percent}%",
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End,
                        color = TvDesign.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnaTasteCard(
    modifier: Modifier,
    snapshot: UserDnaSnapshot,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TvDesign.Surface.copy(alpha = .86f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your Taste", color = TvDesign.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        val tags = snapshot.tasteTags.take(3)
        if (tags.isEmpty()) {
            Text("Taste tags will appear as your DNA develops.", color = TvDesign.Muted, fontSize = 13.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    Text(
                        tag, color = TvDesign.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(TvDesign.Accent.copy(alpha = .12f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }

        DnaCompactAffinity("Movies vs Series", snapshot.typeBreakdown.take(2))
        DnaCompactAffinity("Your Era", snapshot.decadeBreakdown.take(3))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnaCompactAffinity(label: String, values: List<UserDnaAffinity>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = TvDesign.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (values.isEmpty()) {
            Text("Not enough data yet", color = TvDesign.Muted, fontSize = 13.sp)
        } else {
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(TvDesign.Black.copy(alpha = .45f))) {
                values.forEachIndexed { index, value ->
                    if (value.percent > 0) Box(Modifier.weight(value.percent.toFloat()).height(8.dp)
                        .background(TvDesign.Accent.copy(alpha = when (index) { 0 -> 1f; 1 -> .65f; 2 -> .35f; else -> .25f })))
                }
                val remainder = (100 - values.sumOf { it.percent }).coerceAtLeast(0)
                if (remainder > 0) Spacer(Modifier.weight(remainder.toFloat()))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                values.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(TvDesign.Accent.copy(alpha = when (index) { 0 -> 1f; 1 -> .65f; 2 -> .35f; else -> .25f })))
                        Text("${value.name} ${value.percent}%", color = TvDesign.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DnaViewingCard(
    modifier: Modifier,
    snapshot: UserDnaSnapshot,
) {
    val behavior = snapshot.behavior
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TvDesign.Surface.copy(alpha = .86f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Viewing Signals", color = TvDesign.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DnaStat(Modifier.weight(1f), "Watched titles", behavior.watchedTitles.toString())
            DnaStat(Modifier.weight(1f), "Favorites", behavior.myListTitles.toString())
            DnaStat(Modifier.weight(1f), "Completion", "${behavior.completionRatePercent}%")
            DnaStat(Modifier.weight(1f), "Taste titles", behavior.uniqueTitles.toString())
            DnaStat(Modifier.weight(1f), "History entries", behavior.historyEntries.toString())
            DnaStat(Modifier.weight(1f), "Avg. progress", "${behavior.averageProgressPercent}%")
        }
        Text("Taste titles are distinct titles used by DNA. History entries are watched movie and episode records.",
            color = TvDesign.Muted, fontSize = 12.sp)

    }
}

@Composable
private fun DnaStat(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TvDesign.White.copy(alpha = .06f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(value, color = TvDesign.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TvDesign.Muted, fontSize = 12.sp, maxLines = 2)
    }
}

private fun UserDnaReadiness.displayLabel(): String = when (this) {
    UserDnaReadiness.STARTING -> "Finding Your Taste"
    UserDnaReadiness.LEARNING -> "Learning"
    UserDnaReadiness.DEVELOPING -> "Developing"
    UserDnaReadiness.STRONG -> "Strong"
}
