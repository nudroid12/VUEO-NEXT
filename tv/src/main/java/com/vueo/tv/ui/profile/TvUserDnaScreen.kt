package com.vueo.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.vueo.tv.ui.tvPremiumFocus

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

    BackHandler(onBack = onBack)
    LaunchedEffect(profile.id) {
        runCatching { switchRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDesign.Black)
            .padding(horizontal = 56.dp, vertical = 34.dp),
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
                fontSize = 13.sp,
            )
        }

        DnaProfileCard(
            profile = profile,
            snapshot = snapshot,
            switchRequester = switchRequester,
            onSwitchProfiles = onSwitchProfiles,
        )

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DnaAffinityCard(
                modifier = Modifier.weight(1.05f).fillMaxSize(),
                title = "Top Genres",
                subtitle = "What your recent watching and My List say about your taste.",
                affinities = snapshot.topGenres.take(5),
                emptyText = if (snapshot.readiness == UserDnaReadiness.STARTING) {
                    "Your DNA is just getting started. Keep watching to build your taste profile."
                } else {
                    "Not enough genre data yet."
                },
            )

            Column(
                modifier = Modifier.weight(.95f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DnaTasteCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    snapshot = snapshot,
                )
                DnaViewingCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    snapshot = snapshot,
                )
            }
        }

        Text(
            text = "Your DNA is calculated locally from this profile's History and My List. Nothing is sent to a server or AI API.",
            color = TvDesign.Dim,
            fontSize = 10.sp,
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
        Box(
            modifier = Modifier
                .focusRequester(switchRequester)
                .onFocusChanged { focused = it.isFocused }
                .tvPremiumFocus(scale = 1.02f)
                .clip(RoundedCornerShape(12.dp))
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = TvDesign.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TvDesign.Muted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))

        if (affinities.isEmpty()) {
            Text(emptyText, color = TvDesign.Muted, fontSize = 13.sp)
        } else {
            affinities.forEach { affinity ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = affinity.name,
                        modifier = Modifier.width(120.dp),
                        color = TvDesign.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(TvDesign.White.copy(alpha = .10f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((affinity.percent / 100f).coerceIn(0f, 1f))
                                .height(5.dp)
                                .background(TvDesign.White.copy(alpha = .72f)),
                        )
                    }
                    Text(
                        text = "${affinity.percent}%",
                        modifier = Modifier.width(42.dp),
                        color = TvDesign.Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

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
        Text("Your Taste", color = TvDesign.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        val tags = snapshot.tasteTags.take(4)
        Text(
            text = if (tags.isNotEmpty()) tags.joinToString("  •  ") else "Taste tags will appear as your DNA develops.",
            color = if (tags.isNotEmpty()) TvDesign.White.copy(alpha = .88f) else TvDesign.Muted,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        DnaCompactAffinity("Movies vs Series", snapshot.typeBreakdown.take(2))
        DnaCompactAffinity("Your Era", snapshot.decadeBreakdown.take(3))
    }
}

@Composable
private fun DnaCompactAffinity(label: String, values: List<UserDnaAffinity>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TvDesign.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = values.joinToString("  •  ") { "${it.name} ${it.percent}%" }.ifBlank { "Not enough data yet" },
            color = TvDesign.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        Text("Viewing Signals", color = TvDesign.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DnaStat(Modifier.weight(1f), "Watched", behavior.watchedTitles.toString())
            DnaStat(Modifier.weight(1f), "My List", behavior.myListTitles.toString())
            DnaStat(Modifier.weight(1f), "Complete", "${behavior.completionRatePercent}%")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DnaStat(Modifier.weight(1f), "Titles", behavior.uniqueTitles.toString())
            DnaStat(Modifier.weight(1f), "History", behavior.historyEntries.toString())
            DnaStat(Modifier.weight(1f), "Avg progress", "${behavior.averageProgressPercent}%")
        }
    }
}

@Composable
private fun DnaStat(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TvDesign.White.copy(alpha = .06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, color = TvDesign.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TvDesign.Muted, fontSize = 9.sp)
    }
}

private fun UserDnaReadiness.displayLabel(): String = when (this) {
    UserDnaReadiness.STARTING -> "Finding Your Taste"
    UserDnaReadiness.LEARNING -> "Learning"
    UserDnaReadiness.DEVELOPING -> "Developing"
    UserDnaReadiness.STRONG -> "Strong"
}
