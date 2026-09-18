package com.vueo.mobile.ui

// VUEO DNA OPTIONAL SETTINGS PATCH - 2026-08-29

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vueo.mobile.BuildConfig
import com.vueo.mobile.core.extensions.CatalogDiscoveryCache
import com.vueo.mobile.core.extensions.SourceDiscoveryCache
import com.vueo.mobile.core.extensions.UnifiedMediaEngine
import com.vueo.mobile.core.enrichment.MdblistClient
import com.vueo.mobile.core.enrichment.TmdbEnhancementClient
import com.vueo.mobile.core.dna.UserDnaEngine
import com.vueo.mobile.core.dna.UserDnaPreferences
import com.vueo.mobile.core.dna.UserDnaSnapshot
import com.vueo.mobile.core.plugin.PluginStore
import com.vueo.mobile.core.storage.AppAccent
import com.vueo.mobile.core.storage.AppTheme
import com.vueo.mobile.core.storage.LibraryStore
import com.vueo.mobile.core.storage.ProfileStore
import com.vueo.mobile.core.storage.PreferredQuality
import com.vueo.mobile.core.storage.PlayerOrientation
import com.vueo.mobile.core.storage.SettingsStore
import com.vueo.mobile.core.storage.SubtitleLanguage
import com.vueo.mobile.core.storage.SubtitleSize
import com.vueo.mobile.core.storage.SubtitleVisibility
import com.vueo.shared.core.storage.VueoBackupManager
import com.vueo.mobile.core.update.VueoUpdateManager
import com.vueo.mobile.core.update.VueoUpdateStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PersonalizationSettingsScreen(
    profileStore: ProfileStore,
    onBack: () -> Unit,
    onViewDna: () -> Unit,
) {
    val context = LocalContext.current
    val activeProfile =
        remember(
            profileStore
        ) {
            profileStore.activeProfile()
        }

    val userDnaPreferences =
        remember {
            UserDnaPreferences(
                context.applicationContext
            )
        }

    var userDnaEnabled by remember(
        activeProfile.id
    ) {
        mutableStateOf(
            userDnaPreferences
                .userDnaEnabled(
                    activeProfile.id
                )
        )
    }

    var showDnaMatch by remember(
        activeProfile.id
    ) {
        mutableStateOf(
            userDnaPreferences
                .showDnaMatchEnabled(
                    activeProfile.id
                )
        )
    }

    var personalizedRecommendations by remember(
        activeProfile.id
    ) {
        mutableStateOf(
            userDnaPreferences
                .personalizedRecommendationsEnabled(
                    activeProfile.id
                )
        )
    }

    VueoSettingsPage(
        title = "Personalization",
        subtitle =
            "Local, per-profile controls for how VUEO adapts to you.",
        onBack = onBack,
    ) {
        item(key = "personalization-profile") {
            VueoProfileContextStrip(
                profileName = activeProfile.name,
                status = if (userDnaEnabled) "User DNA on" else "User DNA off",
                detail = "These controls apply only to this profile and stay on this device.",
            )
        }

        item(key = "personalization-dna") {
            VueoSettingsHubGroup(
                label = "USER DNA"
            ) {
                VueoSettingsToggleRow(
                    title = "User DNA",
                    subtitle = "Build a local taste profile from History, playback progress and My List.",
                    checked = userDnaEnabled,
                    onCheckedChange = { enabled ->
                        userDnaEnabled = enabled
                        userDnaPreferences.setUserDnaEnabled(
                            profileId = activeProfile.id,
                            enabled = enabled,
                        )
                    },
                )
                VueoSettingsHubDivider()
                VueoSettingsToggleRow(
                    title = "Show DNA Match",
                    subtitle = "Show a local taste-match score on supported movie and series details.",
                    checked = showDnaMatch,
                    enabled = userDnaEnabled,
                    onCheckedChange = { enabled ->
                        showDnaMatch = enabled
                        userDnaPreferences.setShowDnaMatchEnabled(
                            profileId = activeProfile.id,
                            enabled = enabled,
                        )
                    },
                )
                VueoSettingsHubDivider()
                VueoSettingsToggleRow(
                    title = "Personalized Recommendations",
                    subtitle = "Use User DNA for For You and Because You Watched recommendations.",
                    checked = personalizedRecommendations,
                    enabled = userDnaEnabled,
                    onCheckedChange = { enabled ->
                        personalizedRecommendations = enabled
                        userDnaPreferences.setPersonalizedRecommendationsEnabled(
                            profileId = activeProfile.id,
                            enabled = enabled,
                        )
                    },
                )
            }
        }

        item(key = "personalization-local") {
            VueoCompactInfo(
                title = "Local by design",
                text = "Turning User DNA off keeps History, My List and playback progress. It only stops those signals from shaping DNA Match and recommendations.",
            )
        }
    }
}

@Composable
internal fun EnhancementsSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onTmdb: () -> Unit,
    onMdblist: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore =
        remember {
            PluginStore(
                context.applicationContext
            )
        }

    VueoSettingsPage(
        title = "Enhancements",
        subtitle = "Optional services for richer metadata and ratings.",
        onBack = onBack,
    ) {
        item(key = "enhancements-metadata") {
            VueoSettingsHubGroup(
                label = "METADATA & RATINGS"
            ) {
                VueoSettingsHubRow(
                    title = "TMDB",
                    subtitle = "Metadata, discovery, recommendations, similar titles and artwork.",
                    status = if (pluginStore.tmdbApiKey().isNotBlank()) "Configured" else "Not configured",
                    icon = Icons.Default.SettingsInputComponent,
                    onClick = onTmdb,
                )
                VueoSettingsHubDivider()
                VueoSettingsHubRow(
                    title = "MDBList",
                    subtitle = "Ratings and score enrichment for title details.",
                    status = if (settingsStore.mdblistApiKey().isNotBlank()) "Configured" else "Not configured",
                    icon = Icons.Default.SettingsInputComponent,
                    onClick = onMdblist,
                )
            }
        }


        item(key = "enhancements-note") {
            VueoCompactInfo(
                title = "Optional by design",
                text = "VUEO core playback, Library and local Personalization continue to work without these services.",
            )
        }
    }
}


@Composable
internal fun TmdbEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pluginStore = remember {
        PluginStore(context.applicationContext)
    }

    var apiKey by remember {
        mutableStateOf(pluginStore.tmdbApiKey())
    }
    var saved by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    var testing by remember {
        mutableStateOf(false)
    }
    var connectionStatus by remember {
        mutableStateOf<String?>(null)
    }
    var metadata by remember {
        mutableStateOf(settingsStore.tmdbMetadataEnrichmentEnabled())
    }
    var recommendations by remember {
        mutableStateOf(settingsStore.tmdbRecommendationsEnabled())
    }
    var similar by remember {
        mutableStateOf(settingsStore.tmdbSimilarTitlesEnabled())
    }
    var artwork by remember {
        mutableStateOf(settingsStore.tmdbArtworkEnrichmentEnabled())
    }

    VueoSettingsPage(
        title = "TMDB",
        subtitle = "Optional metadata and discovery enhancement.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value = connectionStatus
                    ?: if (apiKey.trim().isNotEmpty()) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                text = "The key is stored locally on this device. VUEO core does not depend on TMDB.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.Surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("TMDB v3 API Key") },
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                pluginStore
                                    .setTmdbApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus = null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                val key = apiKey.trim()

                                if (key.isBlank()) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val ok =
                                            TmdbEnhancementClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            if (ok) {
                                                "Connected"
                                            } else {
                                                "Connection failed"
                                            }

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (testing) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            "TMDB configuration saved locally.",
                            color = VueoPalette.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel("FEATURES")
        }

        item {
            VueoSettingsToggleCard(
                title = "Metadata Enrichment",
                subtitle = "Allow richer title information when TMDB enrichment is available.",
                checked = metadata,
                onCheckedChange = {
                    metadata = it
                    settingsStore.setTmdbMetadataEnrichmentEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Recommendations",
                subtitle = "Use TMDB recommendations for discovery when configured.",
                checked = recommendations,
                onCheckedChange = {
                    recommendations = it
                    settingsStore.setTmdbRecommendationsEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Similar Titles",
                subtitle = "Use TMDB similar titles as an additional discovery signal.",
                checked = similar,
                onCheckedChange = {
                    similar = it
                    settingsStore.setTmdbSimilarTitlesEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Artwork Enrichment",
                subtitle = "Allow better poster and backdrop fallback when available.",
                checked = artwork,
                onCheckedChange = {
                    artwork = it
                    settingsStore.setTmdbArtworkEnrichmentEnabled(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Discovery connection",
                text = "TMDB now enriches Details metadata and powers More Like This with Recommendations, Similar titles, and the VUEO catalog fallback.",
            )
        }
    }
}

@Composable
internal fun MdblistEnhancementSettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    var apiKey by remember {
        mutableStateOf(settingsStore.mdblistApiKey())
    }
    var saved by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    var testing by remember {
        mutableStateOf(false)
    }
    var connectionStatus by remember {
        mutableStateOf<String?>(null)
    }
    var ratings by remember {
        mutableStateOf(settingsStore.mdblistRatingsEnabled())
    }
    var imdb by remember {
        mutableStateOf(settingsStore.mdblistImdbEnabled())
    }
    var rt by remember {
        mutableStateOf(settingsStore.mdblistRottenTomatoesEnabled())
    }
    var metacritic by remember {
        mutableStateOf(settingsStore.mdblistMetacriticEnabled())
    }
    var tmdb by remember {
        mutableStateOf(settingsStore.mdblistTmdbRatingEnabled())
    }
    var trakt by remember {
        mutableStateOf(settingsStore.mdblistTraktEnabled())
    }

    VueoSettingsPage(
        title = "MDBList",
        subtitle = "Optional ratings and score enrichment.",
        onBack = onBack,
    ) {
        item {
            VueoStatusCard(
                title = "Status",
                value = connectionStatus
                    ?: if (apiKey.trim().isNotEmpty()) {
                        "Configured"
                    } else {
                        "Not configured"
                    },
                text = "MDBList is optional. Without it, VUEO simply shows the information available from core metadata sources.",
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VueoPalette.Surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "API Key",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            saved = false
                            connectionStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("MDBList API Key") },
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                settingsStore
                                    .setMdblistApiKey(
                                        apiKey
                                    )
                                saved = true
                                connectionStatus = null
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                val key = apiKey.trim()

                                if (key.isBlank()) {
                                    connectionStatus =
                                        "Enter API key"
                                } else {
                                    testing = true
                                    connectionStatus =
                                        "Testing..."

                                    scope.launch {
                                        val ok =
                                            MdblistClient
                                                .testConnection(
                                                    key
                                                )

                                        connectionStatus =
                                            if (ok) {
                                                "Connected"
                                            } else {
                                                "Connection failed"
                                            }

                                        testing = false
                                    }
                                }
                            },
                        ) {
                            Text(
                                if (testing) {
                                    "Testing..."
                                } else {
                                    "Test Connection"
                                }
                            )
                        }
                    }

                    if (saved) {
                        Text(
                            "MDBList configuration saved locally.",
                            color = VueoPalette.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            VueoSectionLabel("RATINGS")
        }

        item {
            VueoSettingsToggleCard(
                title = "Ratings Enrichment",
                subtitle = "Master switch for MDBList rating information.",
                checked = ratings,
                onCheckedChange = {
                    ratings = it
                    settingsStore.setMdblistRatingsEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "IMDb",
                subtitle = "Show IMDb score when available.",
                checked = imdb,
                onCheckedChange = {
                    imdb = it
                    settingsStore.setMdblistImdbEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Rotten Tomatoes",
                subtitle = "Show Rotten Tomatoes score when available.",
                checked = rt,
                onCheckedChange = {
                    rt = it
                    settingsStore.setMdblistRottenTomatoesEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Metacritic",
                subtitle = "Show Metacritic score when available.",
                checked = metacritic,
                onCheckedChange = {
                    metacritic = it
                    settingsStore.setMdblistMetacriticEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "TMDB Rating",
                subtitle = "Show TMDB rating through MDBList when available.",
                checked = tmdb,
                onCheckedChange = {
                    tmdb = it
                    settingsStore.setMdblistTmdbRatingEnabled(it)
                },
            )
        }

        item {
            VueoSettingsToggleCard(
                title = "Trakt",
                subtitle = "Show Trakt score when available.",
                checked = trakt,
                onCheckedChange = {
                    trakt = it
                    settingsStore.setMdblistTraktEnabled(it)
                },
            )
        }

        item {
            VueoInfoCard(
                title = "Ratings connection",
                text = "MDBList ratings now appear on Details when configured. VUEO fetches one rating bundle and shows only the rating sources you enable.",
            )
        }
    }
}

