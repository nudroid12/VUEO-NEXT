package com.vueo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.core.dna.UserDnaAffinity
import com.vueo.app.core.dna.UserDnaEngine
import com.vueo.app.core.dna.UserDnaReadiness
import com.vueo.app.core.dna.UserDnaSnapshot
import com.vueo.app.core.storage.LibraryStore
import com.vueo.app.core.storage.VueoProfile

/**
 * Local-first User DNA experience.
 *
 * This screen never calls a server or AI API. It reads the profile-scoped
 * LibraryStore through UserDnaEngine and presents the resulting taste model.
 *
 * [dataVersion] should be incremented by the caller when Library data changes
 * so the snapshot is rebuilt while this screen is open.
 */
@Composable
internal fun UserDnaScreen(
    profile: VueoProfile,
    libraryStore: LibraryStore,
    dataVersion: Int,
    onBack: () -> Unit,
) {
    val snapshot =
        remember(
            profile.id,
            dataVersion,
        ) {
            UserDnaEngine(
                libraryStore
            ).build()
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    VueoPalette.Background
                ),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 32.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            ),
    ) {
        item(
            key = "dna-header"
        ) {
            UserDnaHeader(
                onBack = onBack
            )
        }

        item(
            key = "dna-profile"
        ) {
            UserDnaProfileCard(
                profile = profile,
                snapshot = snapshot,
            )
        }

        if (
            snapshot.readiness ==
                UserDnaReadiness.STARTING
        ) {
            item(
                key = "dna-starting"
            ) {
                UserDnaStartingCard()
            }
        } else {
            if (
                snapshot.topGenres
                    .isNotEmpty()
            ) {
                item(
                    key = "dna-genres-label"
                ) {
                    UserDnaSectionLabel(
                        "YOUR TASTE"
                    )
                }

                item(
                    key = "dna-genres"
                ) {
                    UserDnaAffinityCard(
                        title = "Top Genres",
                        subtitle =
                            "What your recent watching and My List say about your taste.",
                        affinities =
                            snapshot.topGenres
                                .take(5),
                    )
                }
            }

            if (
                snapshot.tasteTags
                    .isNotEmpty()
            ) {
                item(
                    key = "dna-tags"
                ) {
                    UserDnaTasteTags(
                        tags =
                            snapshot.tasteTags
                    )
                }
            }

            if (
                snapshot.typeBreakdown
                    .isNotEmpty()
            ) {
                item(
                    key = "dna-format"
                ) {
                    UserDnaAffinityCard(
                        title =
                            "Movies vs Series",
                        subtitle =
                            "The formats you naturally spend more time with.",
                        affinities =
                            snapshot.typeBreakdown
                                .take(3),
                    )
                }
            }

            if (
                snapshot.decadeBreakdown
                    .isNotEmpty()
            ) {
                item(
                    key = "dna-era"
                ) {
                    UserDnaAffinityCard(
                        title =
                            "Your Era",
                        subtitle =
                            "Release periods that appear most often in your taste signals.",
                        affinities =
                            snapshot.decadeBreakdown
                                .take(4),
                    )
                }
            }

            item(
                key = "dna-behavior-label"
            ) {
                UserDnaSectionLabel(
                    "VIEWING SIGNALS"
                )
            }

            item(
                key = "dna-behavior"
            ) {
                UserDnaBehaviorCard(
                    snapshot = snapshot
                )
            }
        }

        item(
            key = "dna-privacy"
        ) {
            UserDnaPrivacyCard()
        }
    }
}

@Composable
private fun UserDnaHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
        ) {
            Icon(
                imageVector =
                    Icons.Default.ArrowBack,
                contentDescription =
                    "Back",
                tint = Color.White,
            )
        }

        Spacer(
            Modifier.width(
                4.dp
            )
        )

        Column(
            modifier =
                Modifier.weight(
                    1f
                ),
        ) {
            Text(
                text = "Your DNA",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                text =
                    "Your taste, built locally from how you use VUEO.",
                color =
                    VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun UserDnaProfileCard(
    profile: VueoProfile,
    snapshot: UserDnaSnapshot,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette
                        .SurfaceElevated,
            ),
        shape =
            RoundedCornerShape(
                22.dp
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        16.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                ),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                UserDnaAvatar(
                    profile = profile
                )

                Spacer(
                    Modifier.width(
                        14.dp
                    )
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            3.dp
                        ),
                ) {
                    Text(
                        text =
                            profile.name,
                        color =
                            Color.White,
                        fontSize = 19.sp,
                        fontWeight =
                            FontWeight.Black,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                    )

                    Text(
                        text =
                            if (
                                profile.isKids
                            ) {
                                "Kids taste profile"
                            } else {
                                "Local taste profile"
                            },
                        color =
                            VueoPalette.Muted,
                        fontSize = 11.sp,
                    )
                }

                Surface(
                    shape =
                        RoundedCornerShape(
                            50
                        ),
                    color =
                        VueoPalette.Accent
                            .copy(
                                alpha = .12f
                            ),
                ) {
                    Text(
                        text =
                            snapshot.readiness
                                .displayLabel(),
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    10.dp,
                                vertical =
                                    6.dp,
                            ),
                        color =
                            VueoPalette.Accent,
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.Black,
                    )
                }
            }

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        7.dp
                    ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "DNA Strength",
                        modifier =
                            Modifier.weight(
                                1f
                            ),
                        color =
                            Color.White,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Bold,
                    )

                    Text(
                        text =
                            "${snapshot.confidencePercent}%",
                        color =
                            VueoPalette.Accent,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Black,
                    )
                }

                UserDnaProgressBar(
                    progress =
                        snapshot
                            .confidencePercent /
                            100f,
                )

                Text(
                    text =
                        snapshot.readiness
                            .supportingText(),
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun UserDnaAvatar(
    profile: VueoProfile,
) {
    val context =
        LocalContext.current

    val avatarDrawable =
        remember(
            profile.avatar,
            context,
        ) {
            if (
                profile.avatar
                    .startsWith(
                        "avatar_"
                    )
            ) {
                context.resources
                    .getIdentifier(
                        profile.avatar,
                        "drawable",
                        context.packageName,
                    )
                    .takeIf {
                        it != 0
                    }
            } else {
                null
            }
        }

    Box(
        modifier =
            Modifier
                .size(
                    66.dp
                )
                .clip(
                    CircleShape
                )
                .background(
                    VueoPalette
                        .SurfaceStrong
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        if (
            avatarDrawable != null
        ) {
            androidx.compose.foundation.Image(
                painter =
                    painterResource(
                        avatarDrawable
                    ),
                contentDescription =
                    "${profile.name} profile avatar",
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text =
                    profile.avatar
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?: profile.name
                            .trim()
                            .firstOrNull()
                            ?.uppercase()
                        ?: "P",
                color =
                    Color.White,
                fontSize = 26.sp,
                fontWeight =
                    FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UserDnaStartingCard() {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette.Surface,
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    18.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                ),
        ) {
            Text(
                text =
                    "Your DNA is just getting started",
                color =
                    Color.White,
                fontSize = 17.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                text =
                    "Watch a few titles and add things you like to My List. VUEO will build this profile automatically as your taste becomes clearer.",
                color =
                    VueoPalette.Muted,
                fontSize = 12.sp,
            )

            Surface(
                shape =
                    RoundedCornerShape(
                        50
                    ),
                color =
                    VueoPalette.Accent
                        .copy(
                            alpha = .10f
                        ),
            ) {
                Text(
                    text =
                        "Keep watching to evolve your DNA",
                    modifier =
                        Modifier.padding(
                            horizontal =
                                11.dp,
                            vertical =
                                7.dp,
                        ),
                    color =
                        VueoPalette.Accent,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun UserDnaAffinityCard(
    title: String,
    subtitle: String,
    affinities:
        List<UserDnaAffinity>,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette.Surface,
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                ),
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        3.dp
                    ),
            ) {
                Text(
                    text = title,
                    color =
                        Color.White,
                    fontSize = 16.sp,
                    fontWeight =
                        FontWeight.Black,
                )

                Text(
                    text = subtitle,
                    color =
                        VueoPalette.Muted,
                    fontSize = 10.sp,
                )
            }

            affinities.forEach {
                affinity ->
                UserDnaAffinityRow(
                    affinity =
                        affinity
                )
            }
        }
    }
}

@Composable
private fun UserDnaAffinityRow(
    affinity: UserDnaAffinity,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                6.dp
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    affinity.name,
                modifier =
                    Modifier.weight(
                        1f
                    ),
                color =
                    Color.White.copy(
                        alpha = .92f
                    ),
                fontSize = 12.sp,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Text(
                text =
                    "${affinity.percent}%",
                color =
                    VueoPalette.Accent,
                fontSize = 11.sp,
                fontWeight =
                    FontWeight.Black,
            )
        }

        UserDnaProgressBar(
            progress =
                affinity.percent
                    .coerceIn(
                        0,
                        100
                    ) / 100f,
        )
    }
}

@Composable
private fun UserDnaProgressBar(
    progress: Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    7.dp
                )
                .clip(
                    RoundedCornerShape(
                        50
                    )
                )
                .background(
                    VueoPalette
                        .SurfaceStrong
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(
                        progress
                            .coerceIn(
                                0f,
                                1f
                            )
                    )
                    .height(
                        7.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            50
                        )
                    )
                    .background(
                        VueoPalette.Accent
                    ),
        )
    }
}

@Composable
private fun UserDnaTasteTags(
    tags: List<String>,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                9.dp
            ),
    ) {
        Text(
            text =
                "Your vibe",
            color =
                Color.White,
            fontSize = 14.sp,
            fontWeight =
                FontWeight.Black,
        )

        LazyRow(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                ),
        ) {
            items(
                items = tags,
                key = {
                    it
                },
            ) {
                tag ->
                Surface(
                    shape =
                        RoundedCornerShape(
                            50
                        ),
                    color =
                        VueoPalette.Accent
                            .copy(
                                alpha = .10f
                            ),
                ) {
                    Text(
                        text = tag,
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    11.dp,
                                vertical =
                                    7.dp,
                            ),
                        color =
                            VueoPalette.Accent,
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserDnaBehaviorCard(
    snapshot: UserDnaSnapshot,
) {
    val behavior =
        snapshot.behavior

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette.Surface,
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                ),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
            ) {
                UserDnaStat(
                    label =
                        "Watched",
                    value =
                        behavior
                            .watchedTitles
                            .toString(),
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                )

                UserDnaStat(
                    label =
                        "My List",
                    value =
                        behavior
                            .myListTitles
                            .toString(),
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
            ) {
                UserDnaStat(
                    label =
                        "Completion",
                    value =
                        "${behavior.completionRatePercent}%",
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                )

                UserDnaStat(
                    label =
                        "Avg. progress",
                    value =
                        "${behavior.averageProgressPercent}%",
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                )
            }
        }
    }
}

@Composable
private fun UserDnaStat(
    label: String,
    value: String,
    modifier: Modifier =
        Modifier,
) {
    Surface(
        modifier =
            modifier,
        shape =
            RoundedCornerShape(
                16.dp
            ),
        color =
            VueoPalette
                .SurfaceElevated,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    13.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    3.dp
                ),
        ) {
            Text(
                text = value,
                color =
                    VueoPalette.Accent,
                fontSize = 20.sp,
                fontWeight =
                    FontWeight.Black,
            )

            Text(
                text = label,
                color =
                    VueoPalette.Muted,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UserDnaPrivacyCard() {
    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        color =
            VueoPalette.Accent
                .copy(
                    alpha = .07f
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    15.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp
                ),
        ) {
            Text(
                text =
                    "Built on this device",
                color =
                    Color.White,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Bold,
            )

            Text(
                text =
                    "Your DNA is calculated locally from this profile's History and My List. No account, server or AI API is required.",
                color =
                    VueoPalette.Muted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun UserDnaSectionLabel(
    label: String,
) {
    Text(
        text = label,
        color =
            VueoPalette.Muted,
        fontSize = 10.sp,
        fontWeight =
            FontWeight.Black,
        letterSpacing = 1.2.sp,
    )
}

private fun UserDnaReadiness
    .displayLabel(): String =
    when (this) {
        UserDnaReadiness.STARTING ->
            "STARTING"

        UserDnaReadiness.LEARNING ->
            "LEARNING"

        UserDnaReadiness.DEVELOPING ->
            "DEVELOPING"

        UserDnaReadiness.STRONG ->
            "STRONG"
    }

private fun UserDnaReadiness
    .supportingText(): String =
    when (this) {
        UserDnaReadiness.STARTING ->
            "VUEO needs a few more taste signals."

        UserDnaReadiness.LEARNING ->
            "Your early preferences are starting to appear."

        UserDnaReadiness.DEVELOPING ->
            "Your taste profile is becoming more reliable."

        UserDnaReadiness.STRONG ->
            "VUEO has a strong local picture of your taste."
    }
