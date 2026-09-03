package com.vueo.app.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.player.PlayerSourcePolicy
import java.util.Locale

private val SourceAccent = Color(0xFFB9FF3A)
private val SourceCardShape = RoundedCornerShape(14.dp)

@Composable
internal fun PlayerSourcesWorkspace(
    title: String,
    sources: List<StreamSource>,
    currentSource: StreamSource,
    currentPlaybackFailed: Boolean,
    failedSourceUrls: Set<String>,
    providerOrder: List<String>,
    originalLanguage: String?,
    switchingSourceUrl: String?,
    onSelect: (StreamSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableProviders = remember(sources) {
        sources
            .map(::sourceProviderKey)
            .distinct()
    }
    val visibleProviders = remember(
        providerOrder,
        availableProviders,
    ) {
        (
            providerOrder.filter { it in availableProviders } +
                availableProviders.filter { it !in providerOrder }
            ).distinct()
    }
    var activeProvider by remember(sources) {
        mutableStateOf<String?>(null)
    }
    val visibleSources = sources.filter {
        activeProvider == null || sourceProviderKey(it) == activeProvider
    }
    val recommended = remember(
        sources,
        currentSource.url,
        currentPlaybackFailed,
        failedSourceUrls,
        originalLanguage,
    ) {
        sources.firstOrNull { candidate ->
            candidate.url?.let { it !in failedSourceUrls } == true &&
                PlayerSourcePolicy
                    .assess(
                        source = candidate,
                        originalLanguage = originalLanguage,
                    ).let { assessment ->
                        assessment.quality
                            .automaticRecoveryEligible &&
                            assessment.audioMatch
                                .recommendationEligible
                    } &&
                (!currentPlaybackFailed || candidate.url != currentSource.url)
        } ?: sources.firstOrNull { candidate ->
            candidate.url?.let { it !in failedSourceUrls } == true
        }
    }
    val orderedSources = remember(
        visibleSources,
        recommended?.url,
    ) {
        val recommendedUrl = recommended?.url
        if (recommendedUrl == null) {
            visibleSources
        } else {
            visibleSources.sortedByDescending {
                it.url == recommendedUrl
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepSourcesDialogImmersive()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .24f))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = .12f),
                        .38f to Color.Black.copy(alpha = .64f),
                        1f to Color.Black.copy(alpha = .97f),
                    )
                )
                .clickable(onClick = onDismiss)
                .padding(start = 24.dp, end = 40.dp, top = 20.dp, bottom = 20.dp),
        ) {
            val workspaceWidth = minOf(maxWidth * .56f, 620.dp)
            Surface(
                modifier = Modifier
                    .width(workspaceWidth)
                    .fillMaxHeight(.90f)
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xF2181A1C),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = .09f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Sources",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$title • ${sources.size} sources",
                        color = Color.White.copy(alpha = .52f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SourceFilterChip(
                            label = "All Sources",
                            selected = activeProvider == null,
                            onClick = { activeProvider = null },
                        )
                        visibleProviders.forEach { provider ->
                            SourceFilterChip(
                                label = sourceProviderDisplayName(provider),
                                selected = activeProvider == provider,
                                onClick = { activeProvider = provider },
                            )
                        }
                    }

                    Spacer(Modifier.height(9.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = orderedSources,
                            key = { it.url ?: "${it.providerId}:${it.name}" },
                        ) { candidate ->
                            val current = candidate.url == currentSource.url
                            val switching =
                                candidate.url == switchingSourceUrl
                            val assessment =
                                PlayerSourcePolicy.assess(
                                    source = candidate,
                                    originalLanguage = originalLanguage,
                                )
                            SourceListRow(
                                source = candidate,
                                originalLanguage = originalLanguage,
                                current = current,
                                switching = switching,
                                recommended =
                                    candidate.url == recommended?.url,
                                automaticRecoveryEligible = assessment
                                    .quality
                                    .automaticRecoveryEligible &&
                                    assessment.audioMatch
                                        .recommendationEligible,
                                playbackFailed =
                                    candidate.url?.let {
                                        it in failedSourceUrls
                                    } == true ||
                                    (current && currentPlaybackFailed),
                                onClick = {
                                    if (!current && !switching) {
                                        onSelect(candidate)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceListRow(
    source: StreamSource,
    originalLanguage: String?,
    current: Boolean,
    switching: Boolean,
    recommended: Boolean,
    automaticRecoveryEligible: Boolean,
    playbackFailed: Boolean,
    onClick: () -> Unit,
) {
    val assessment = PlayerSourcePolicy.assess(
        source = source,
        originalLanguage = originalLanguage,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !current && !switching,
                onClick = onClick,
            ),
        shape = SourceCardShape,
        color = if (current || switching) {
            SourceAccent.copy(alpha = .12f)
        } else {
            Color.White.copy(alpha = .04f)
        },
        border = BorderStroke(
            1.dp,
            if (current || switching) {
                SourceAccent.copy(alpha = .48f)
            } else {
                Color.White.copy(alpha = .07f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        Color.White.copy(alpha = .055f),
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = if (current || switching) {
                        SourceAccent
                    } else {
                        Color.White.copy(alpha = .66f)
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SourceQualityBadge(assessment.quality.label)
                    Text(
                        text = sourceProviderDisplayName(
                            sourceProviderKey(source)
                        ),
                        color = Color.White.copy(alpha = .64f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = sourceDetailLine(source),
                    color = Color.White.copy(alpha = .46f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    playbackFailed -> SourceBadge("Failed")
                    switching -> SourceBadge("Switching", accent = true)
                    current -> SourceBadge("Playing", accent = true)
                    recommended -> SourceBadge("Recommended", accent = true)
                    !automaticRecoveryEligible -> SourceBadge("Manual only")
                    else -> SourceBadge("Direct")
                }
            }
            if (current) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Current source",
                    tint = SourceAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceQualityBadge(label: String) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                Color.White.copy(alpha = .08f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun SourceBadge(
    label: String,
    accent: Boolean = false,
) {
    Text(
        text = label,
        color = if (accent) SourceAccent else Color.White.copy(alpha = .62f),
        fontSize = 8.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (accent) {
                    SourceAccent.copy(alpha = .10f)
                } else {
                    Color.White.copy(alpha = .06f)
                },
                RoundedCornerShape(50),
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun SourceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color(0xFF161A14) else Color.White.copy(alpha = .72f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (selected) SourceAccent else Color.White.copy(alpha = .06f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

private fun sourceProviderKey(source: StreamSource): String =
    source.providerName
        .trim()
        .ifBlank { "Other" }

private fun sourceProviderDisplayName(provider: String): String =
    provider
        .substringAfterLast(" / ", provider)
        .trim()
        .ifBlank { "Other" }

private fun sourceDetailLine(source: StreamSource): String =
    buildList {
        source.codec?.takeIf { it.isNotBlank() }?.let(::add)
        source.hdr?.takeIf { it.isNotBlank() }?.let(::add)
        source.audio?.takeIf { it.isNotBlank() }?.let(::add)
        source.language?.takeIf { it.isNotBlank() }?.let(::add)
        source.sizeBytes?.takeIf { it > 0L }?.let {
            add(formatSourceSize(it))
        }
        add("Direct")
    }.distinct().joinToString(" • ")

private fun formatSourceSize(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GB", gib)
    } else {
        String.format(Locale.US, "%.0f MB", mib)
    }
}

@Composable
private fun KeepSourcesDialogImmersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val decor = window?.decorView
        val previousFlags = decor?.systemUiVisibility ?: 0

        window?.setDimAmount(0f)
        if (Build.VERSION.SDK_INT >= 30) {
            window?.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            decor?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        onDispose {
            if (Build.VERSION.SDK_INT < 30) {
                decor?.systemUiVisibility = previousFlags
            }
        }
    }
}
