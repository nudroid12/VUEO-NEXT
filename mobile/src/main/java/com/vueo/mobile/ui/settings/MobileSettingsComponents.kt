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

internal fun vueoBackupFileName(): String {
    val stamp = SimpleDateFormat(
        "yyyyMMdd-HHmm",
        Locale.US,
    ).format(Date())

    return "VUEO-backup-$stamp.json"
}

internal enum class SubtitleLanguageTarget {
    PRIMARY,
    SECONDARY,
}

internal enum class DataClearAction(
    val title: String,
    val message: String,
) {
    CATALOG_CACHE(
        title = "Clear catalog cache?",
        message = "Home and Search will fetch fresh catalog data again.",
    ),
    SOURCE_CACHE(
        title = "Clear source cache?",
        message = "Recent source results will be discarded. The next Watch action will perform a fresh source search.",
    ),
    CONTINUE_WATCHING(
        title = "Clear Continue Watching?",
        message = "All unfinished playback entries will be removed from Continue Watching.",
    ),
    WATCH_HISTORY(
        title = "Clear Watch History?",
        message = "Previously watched playback history will be removed. My List remains unchanged.",
    ),
}

@Composable
internal fun VueoProfileContextStrip(
    profileName: String,
    status: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = VueoPalette.ProfileSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = profileName,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status,
                    color = VueoPalette.BrandLime,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = detail,
                color = VueoPalette.Muted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
internal fun VueoSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) Color.White else VueoPalette.Muted.copy(alpha = .55f),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = VueoPalette.Muted.copy(alpha = if (enabled) 1f else .48f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.88f),
        )
    }
}

@Composable
internal fun VueoCompactInfo(
    title: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(VueoPalette.BrandLime.copy(alpha = .75f))
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = .86f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = text,
                color = VueoPalette.Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
internal fun VueoSettingsPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VueoPalette.Background),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 132.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    VueoSettingsTitle(
                        title = title,
                        subtitle = subtitle,
                    )
                }
            }
        }

        content()

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun VueoSettingsTitle(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "VUEO",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )

        Text(
            title,
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Black,
        )

        Text(
            subtitle,
            color = VueoPalette.Muted,
            fontSize = 11.sp,
            maxLines = 2,
        )
    }
}

@Composable
internal fun VueoProfileStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = .20f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = label,
                color = VueoPalette.Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal fun vueoViewingClass(
    watchedTitles: Int,
): String =
    when {
        watchedTitles < 10 ->
            "Baby VUEO"

        watchedTitles < 30 ->
            "Explorer"

        watchedTitles < 75 ->
            "Binger"

        watchedTitles < 150 ->
            "Cinephile"

        watchedTitles < 300 ->
            "Screen Veteran"

        else ->
            "VUEO Legend"
    }

internal fun vueoDnaClass(
    snapshot: UserDnaSnapshot,
): String {
    if (
        snapshot.confidencePercent < 20 ||
        snapshot.topGenres.isEmpty()
    ) {
        return "Finding Your Taste"
    }

    val genres =
        snapshot.topGenres
            .associate {
                it.name.lowercase(
                    Locale.US
                ) to it.percent
            }

    fun score(
        vararg names: String,
    ): Int =
        names.sumOf {
            genres[
                it.lowercase(
                    Locale.US
                )
            ] ?: 0
        }

    val topGenrePercent =
        snapshot.topGenres
            .firstOrNull()
            ?.percent
            ?: 0

    if (
        snapshot.topGenres.size >= 5 &&
        topGenrePercent <= 30
    ) {
        return "The Explorer"
    }

    val classes =
        listOf(
            "The Adventurer" to
                score(
                    "Action",
                    "Adventure",
                    "Fantasy",
                ),
            "The Detective" to
                score(
                    "Crime",
                    "Mystery",
                    "Thriller",
                ),
            "The Thrill Seeker" to
                score(
                    "Horror",
                    "Thriller",
                    "Action",
                ),
            "The Romantic" to
                score(
                    "Romance",
                    "Drama",
                ),
            "The Dreamer" to
                score(
                    "Science Fiction",
                    "Fantasy",
                    "Animation",
                ),
            "The Mood Lifter" to
                score(
                    "Comedy",
                    "Family",
                    "Animation",
                ),
            "The Story Hunter" to
                score(
                    "Drama",
                    "History",
                    "Documentary",
                ),
        )

    val best =
        classes.maxByOrNull {
            it.second
        }

    if (
        best != null &&
        best.second >= 20
    ) {
        return best.first
    }

    return when (
        snapshot.topGenres
            .firstOrNull()
            ?.name
            ?.lowercase(
                Locale.US
            )
    ) {
        "crime",
        "mystery" ->
            "The Detective"

        "horror",
        "thriller" ->
            "The Thrill Seeker"

        "romance" ->
            "The Romantic"

        "science fiction",
        "fantasy" ->
            "The Dreamer"

        "comedy" ->
            "The Mood Lifter"

        "action",
        "adventure" ->
            "The Adventurer"

        else ->
            "The Story Hunter"
    }
}

@Composable
internal fun VueoSettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    color =
                        if (enabled) {
                            Color.White
                        } else {
                            Color.White.copy(
                                alpha = .45f
                            )
                        },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color =
                        if (enabled) {
                            VueoPalette.Muted
                        } else {
                            VueoPalette.Muted.copy(
                                alpha = .55f
                            )
                        },
                    fontSize = 10.5.sp,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.width(10.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.scale(0.88f),
            )
        }
    }
}

@Composable
internal fun VueoSettingsValueCard(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 10.5.sp,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = VueoPalette.Accent.copy(alpha = .10f),
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(
                        horizontal = 9.dp,
                        vertical = 5.dp,
                    ),
                    color = VueoPalette.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun VueoSettingsActionCard(
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    subtitle,
                    color = VueoPalette.Muted,
                    fontSize = 10.5.sp,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.width(8.dp))

            TextButton(onClick = onClick) {
                Text(
                    action,
                    color = VueoPalette.Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun VueoInfoCard(
    title: String,
    text: String,
) {
    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
            )

            Text(
                text,
                color = VueoPalette.Muted,
                fontSize = 10.5.sp,
                maxLines = 4,
            )
        }
    }
}

@Composable
internal fun VueoStatusCard(
    title: String,
    value: String,
    text: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VueoPalette.Surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = VueoPalette.Accent.copy(alpha = .10f),
                ) {
                    Text(
                        value,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = VueoPalette.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                    )
                }
            }

            Text(
                text,
                color = VueoPalette.Muted,
                fontSize = 10.5.sp,
                maxLines = 4,
            )
        }
    }
}

@Composable
internal fun VueoChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )

        Spacer(Modifier.width(8.dp))

        Text(label)
    }
}

@Composable
internal fun VueoSectionLabel(
    label: String,
) {
    Text(
        label,
        color = VueoPalette.Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
    )
}
