package com.vueo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.app.R
import com.vueo.app.core.storage.ProfileStore
import com.vueo.app.core.storage.VueoProfile

private data class StockProfileAvatar(
    val id: String,
    val drawableRes: Int,
)

private val PROFILE_AVATARS =
    listOf(
        StockProfileAvatar(
            id = "avatar_man_1",
            drawableRes = R.drawable.avatar_man_1,
        ),
        StockProfileAvatar(
            id = "avatar_man_2",
            drawableRes = R.drawable.avatar_man_2,
        ),
        StockProfileAvatar(
            id = "avatar_woman_1",
            drawableRes = R.drawable.avatar_woman_1,
        ),
        StockProfileAvatar(
            id = "avatar_woman_2",
            drawableRes = R.drawable.avatar_woman_2,
        ),
        StockProfileAvatar(
            id = "avatar_boy_1",
            drawableRes = R.drawable.avatar_boy_1,
        ),
        StockProfileAvatar(
            id = "avatar_boy_2",
            drawableRes = R.drawable.avatar_boy_2,
        ),
        StockProfileAvatar(
            id = "avatar_girl_1",
            drawableRes = R.drawable.avatar_girl_1,
        ),
        StockProfileAvatar(
            id = "avatar_girl_2",
            drawableRes = R.drawable.avatar_girl_2,
        ),
    )

private fun stockAvatarDrawable(
    avatarId: String,
): Int? =
    PROFILE_AVATARS
        .firstOrNull {
            it.id == avatarId
        }
        ?.drawableRes

@Composable
internal fun WhosWatchingScreen(
    profileStore: ProfileStore,
    profileVersion: Int,
    onProfileSelected: () -> Unit,
    onProfilesChanged: () -> Unit,
) {
    val profiles = remember(profileVersion) {
        profileStore.profiles()
    }
    var askOnStartup by remember(profileVersion) {
        mutableStateOf(profileStore.askWhoIsWatchingOnStartup())
    }
    var editor by remember {
        mutableStateOf<ProfileEditorState?>(null)
    }

    editor?.let { state ->
        ProfileEditorDialog(
            state = state,
            onDismiss = { editor = null },
            onSave = { name, avatar, isKids ->
                profileStore.createProfile(
                    name = name,
                    avatar = avatar,
                    isKids = isKids,
                )
                onProfilesChanged()
                editor = null
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        contentPadding = PaddingValues(
            horizontal = 24.dp,
            vertical = 42.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            VueoBrandLockup()
        }

        item {
            Text(
                "Who's Watching?",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 31.sp,
                textAlign = TextAlign.Center,
            )
        }

        item {
            Text(
                "No sign-in required. Profiles are local to this device.",
                color = VueoPalette.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        items(
            profiles.chunked(2),
            key = { row -> row.joinToString("|") { it.id } },
        ) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { profile ->
                    WatchingProfileCard(
                        profile = profile,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            profileStore.setActiveProfile(profile.id)
                            onProfileSelected()
                        },
                    )
                }

                if (
                    row.size == 1 &&
                    profiles.size >= ProfileStore.MAX_PROFILES
                ) {
                    Spacer(Modifier.weight(1f))
                }

                if (
                    row.size == 1 &&
                    profiles.size < ProfileStore.MAX_PROFILES
                ) {
                    AddProfileCard(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editor = ProfileEditorState(profile = null)
                        },
                    )
                }
            }
        }

        if (
            profiles.size % 2 == 0 &&
            profiles.size < ProfileStore.MAX_PROFILES
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AddProfileCard(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            editor = ProfileEditorState(profile = null)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        askOnStartup = !askOnStartup
                        profileStore.setAskWhoIsWatchingOnStartup(
                            askOnStartup
                        )
                    },
                shape = RoundedCornerShape(18.dp),
                color = VueoPalette.SurfaceElevated,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "Ask on startup",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Show Who's Watching when VUEO launches.",
                            color = VueoPalette.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = askOnStartup,
                        onCheckedChange = {
                            askOnStartup = it
                            profileStore.setAskWhoIsWatchingOnStartup(it)
                        },
                    )
                }
            }
        }
    }
}
@Composable
internal fun ProfileSettingsScreen(
    profileStore: ProfileStore,
    profileVersion: Int,
    onProfilesChanged: () -> Unit,
    onActiveProfileChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val profiles =
        remember(
            profileVersion
        ) {
            profileStore.profiles()
        }

    val active =
        remember(
            profileVersion
        ) {
            profileStore.activeProfile()
        }

    var askOnStartup by remember(
        profileVersion
    ) {
        mutableStateOf(
            profileStore
                .askWhoIsWatchingOnStartup()
        )
    }

    var editor by remember {
        mutableStateOf<ProfileEditorState?>(
            null
        )
    }

    var deleteCandidate by remember {
        mutableStateOf<VueoProfile?>(
            null
        )
    }

    editor?.let {
        state ->
        ProfileEditorDialog(
            state = state,
            onDismiss = {
                editor = null
            },
            onSave = {
                name,
                avatar,
                isKids ->

                val existing =
                    state.profile

                if (existing == null) {
                    val created =
                        profileStore
                            .createProfile(
                                name = name,
                                avatar = avatar,
                                isKids = isKids,
                            )

                    profileStore
                        .setActiveProfile(
                            created.id
                        )

                    onActiveProfileChanged()
                } else {
                    profileStore
                        .updateProfile(
                            profileId =
                                existing.id,
                            name = name,
                            avatar = avatar,
                            isKids = isKids,
                        )
                }

                onProfilesChanged()
                editor = null
            },
        )
    }

    deleteCandidate?.let {
        profile ->
        AlertDialog(
            onDismissRequest = {
                deleteCandidate = null
            },
            title = {
                Text(
                    "Delete ${profile.name}?"
                )
            },
            text = {
                Text(
                    "This removes this profile's My List, Continue Watching, History, playback progress and personal playback/subtitle preferences."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wasActive =
                            profile.id ==
                                active.id

                        if (
                            profileStore
                                .deleteProfile(
                                    profile.id
                                )
                        ) {
                            if (wasActive) {
                                onActiveProfileChanged()
                            }
                            onProfilesChanged()
                        }

                        deleteCandidate =
                            null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteCandidate =
                            null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
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
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription =
                            "Back",
                        tint =
                            Color.White,
                    )
                }

                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = "Profiles",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 26.sp,
                    )

                    Text(
                        text = "Local profiles keep personal watching data separate.",
                        color =
                            VueoPalette.Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        item {
            ProfileSummaryCard(
                profile = active,
                profileCount =
                    profiles.size,
            )
        }

        item {
            Text(
                text = "PROFILES",
                color =
                    VueoPalette.Accent,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
        }

        items(
            items = profiles,
            key = {
                it.id
            },
        ) {
            profile ->
            ManageProfileCard(
                profile = profile,
                selected =
                    profile.id ==
                        active.id,
                canDelete =
                    profile.id !=
                        ProfileStore
                            .DEFAULT_PROFILE_ID,
                onSelect = {
                    if (
                        profile.id !=
                            active.id &&
                        profileStore
                            .setActiveProfile(
                                profile.id
                            )
                    ) {
                        onActiveProfileChanged()
                        onProfilesChanged()
                    }
                },
                onEdit = {
                    editor =
                        ProfileEditorState(
                            profile =
                                profile
                        )
                },
                onDelete = {
                    deleteCandidate =
                        profile
                },
            )
        }

        if (
            profiles.size <
                ProfileStore.MAX_PROFILES
        ) {
            item {
                OutlinedButton(
                    onClick = {
                        editor =
                            ProfileEditorState(
                                profile = null
                            )
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription =
                            null,
                    )
                    Text(
                        " Add Profile"
                    )
                }
            }
        }

        item {
            Text(
                text = "STARTUP",
                color =
                    VueoPalette.Accent,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                VueoPalette.Surface,
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                16.dp
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Who's Watching on Startup",
                            color =
                                Color.White,
                            fontWeight =
                                FontWeight.SemiBold,
                        )

                        Text(
                            text =
                                if (
                                    profiles.size >
                                        1
                                ) {
                                    "Show the profile picker whenever VUEO starts."
                                } else {
                                    "Add another profile before the startup picker is needed."
                                },
                            color =
                                VueoPalette.Muted,
                            fontSize = 13.sp,
                        )
                    }

                    Switch(
                        checked =
                            askOnStartup,
                        enabled =
                            profiles.size >
                                1,
                        onCheckedChange = {
                            enabled ->
                            askOnStartup =
                                enabled
                            profileStore
                                .setAskWhoIsWatchingOnStartup(
                                    enabled
                                )
                            onProfilesChanged()
                        },
                    )
                }
            }
        }

        item {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                VueoPalette.Surface,
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
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
                            6.dp
                        ),
                ) {
                    Text(
                        text = "Local by design",
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                    Text(
                        text = "Profiles live only on this device unless you include them in a VUEO backup. Content Manager, providers, TMDB and MDBList remain shared across the app.",
                        color =
                            VueoPalette.Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private data class ProfileEditorState(
    val profile: VueoProfile?,
)

@Composable
private fun ProfileEditorDialog(
    state: ProfileEditorState,
    onDismiss: () -> Unit,
    onSave: (
        String,
        String,
        Boolean,
    ) -> Unit,
) {
    val profile =
        state.profile

    var name by remember(
        profile?.id
    ) {
        mutableStateOf(
            profile?.name.orEmpty()
        )
    }

    var avatar by remember(
        profile?.id
    ) {
        mutableStateOf(
            profile?.avatar
                ?: PROFILE_AVATARS.first().id
        )
    }

    var isKids by remember(
        profile?.id
    ) {
        mutableStateOf(
            profile?.isKids
                ?: false
        )
    }

    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {
            Text(
                if (profile == null) {
                    "Add Profile"
                } else {
                    "Edit Profile"
                }
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    ),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name =
                            it.take(24)
                    },
                    singleLine = true,
                    label = {
                        Text("Name")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Avatar",
                    fontWeight =
                        FontWeight.SemiBold,
                )

                LazyRow(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        ),
                ) {
                    items(
                        items = PROFILE_AVATARS,
                        key = {
                            it.id
                        },
                    ) {
                        choice ->
                        val selected =
                            avatar ==
                                choice.id

                        Surface(
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clickable {
                                        avatar =
                                            choice.id
                                    },
                            shape = CircleShape,
                            color =
                                VueoPalette.SurfaceStrong,
                            border =
                                BorderStroke(
                                    width =
                                        if (selected) {
                                            3.dp
                                        } else {
                                            1.dp
                                        },
                                    color =
                                        if (selected) {
                                            VueoPalette.Accent
                                        } else {
                                            VueoPalette.Stroke
                                        },
                                ),
                        ) {
                            Image(
                                painter =
                                    painterResource(
                                        choice.drawableRes
                                    ),
                                contentDescription =
                                    "Choose profile avatar",
                                contentScale =
                                    ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Kids Profile",
                            fontWeight =
                                FontWeight.SemiBold,
                        )
                        Text(
                            text = "Labels this profile for future age-aware controls. It does not filter content yet.",
                            color =
                                VueoPalette.Muted,
                            fontSize = 12.sp,
                        )
                    }

                    Switch(
                        checked = isKids,
                        onCheckedChange = {
                            isKids = it
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    name.trim()
                        .isNotEmpty(),
                onClick = {
                    onSave(
                        name.trim(),
                        avatar,
                        isKids,
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun WatchingProfileCard(
    profile: VueoProfile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.SurfaceElevated,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileAvatar(
                profile = profile,
                size = 74,
            )
            Text(
                profile.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
            )
            Text(
                if (profile.isKids) "Kids" else "Profile",
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun AddProfileCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.SurfaceElevated,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = VueoPalette.SurfaceStrong,
            ) {
                Box(
                    modifier = Modifier.size(74.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = VueoPalette.Accent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                "Add Profile",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(
                "Local",
                color = VueoPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}
@Composable
private fun ManageProfileCard(
    profile: VueoProfile,
    selected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onSelect
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        VueoPalette
                            .SurfaceStrong
                    } else {
                        VueoPalette.Surface
                    },
            ),
        shape =
            RoundedCornerShape(
                18.dp
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        14.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                profile = profile,
                size = 46,
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            horizontal =
                                12.dp
                        ),
            ) {
                Text(
                    text = profile.name,
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text = buildString {
                        append(
                            if (selected) {
                                "Active"
                            } else {
                                "Tap to switch"
                            }
                        )
                        if (profile.isKids) {
                            append(" • Kids")
                        }
                    },
                    color =
                        if (selected) {
                            VueoPalette.Accent
                        } else {
                            VueoPalette.Muted
                        },
                    fontSize = 12.sp,
                )
            }

            IconButton(
                onClick = onEdit,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription =
                        "Edit ${profile.name}",
                    tint =
                        Color.White,
                )
            }

            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Delete ${profile.name}",
                        tint =
                            VueoPalette.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSummaryCard(
    profile: VueoProfile,
    profileCount: Int,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    VueoPalette.SurfaceElevated,
            ),
        shape =
            RoundedCornerShape(
                20.dp
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        18.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    14.dp
                ),
        ) {
            ProfileAvatar(
                profile = profile,
                size = 54,
            )

            Column {
                Text(
                    text = "Watching as ${profile.name}",
                    color =
                        Color.White,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Text(
                    text = "$profileCount local ${if (profileCount == 1) "profile" else "profiles"}",
                    color =
                        VueoPalette.Muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    profile: VueoProfile,
    size: Int,
) {
    val stockDrawable =
        stockAvatarDrawable(
            profile.avatar
        )

    Box(
        modifier =
            Modifier
                .size(
                    size.dp
                )
                .clip(
                    CircleShape
                )
                .background(
                    VueoPalette.SurfaceStrong
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        if (stockDrawable != null) {
            Image(
                painter =
                    painterResource(
                        stockDrawable
                    ),
                contentDescription =
                    "${profile.name} profile avatar",
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        } else {
            // Keeps old emoji avatars working after upgrade.
            Text(
                text =
                    profile.avatar
                        .ifBlank {
                            "🙂"
                        },
                fontSize =
                    (size / 2).sp,
            )
        }
    }
}
