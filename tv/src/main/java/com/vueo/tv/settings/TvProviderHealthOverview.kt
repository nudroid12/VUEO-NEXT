package com.vueo.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vueo.shared.core.plugin.PluginHealthStore
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.ProviderCodeStore
import com.vueo.shared.core.plugin.ProviderHealthRecord
import com.vueo.shared.core.plugin.ProviderHealthStatus
import com.vueo.shared.core.plugin.providerHealthSortKey
import com.vueo.tv.core.TvRuntime

private data class TvRankedProviderHealthEntry(
    val repository: PluginRepositoryDescriptor,
    val provider: PluginProviderDescriptor,
    val health: ProviderHealthRecord?,
)

/**
 * TV presentation of Mobile's Provider Health semantics.
 * Ranking data comes from Shared Core; this screen only adapts it for D-pad use.
 */
@Composable
internal fun TvProviderHealthOverview(
    runtime: TvRuntime,
    onNavigate: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val healthStore = remember(context) { PluginHealthStore(context.applicationContext) }
    val providerCodeStore = remember(context) { ProviderCodeStore(context.applicationContext) }
    val repositories = runtime.pluginStore.repositories()
    val knownHealth = remember(repositories) {
        healthStore.records().associateBy { it.repositoryManifestUrl to it.providerId }
    }
    val rankedProviders = remember(repositories, knownHealth) {
        repositories
            .filter(runtime.pluginStore::isRepositoryEnabled)
            .flatMap { repository ->
                repository.providers
                    .filter { provider -> runtime.pluginStore.isProviderEnabled(repository, provider) }
                    .map { provider ->
                        TvRankedProviderHealthEntry(
                            repository = repository,
                            provider = provider,
                            health = knownHealth[repository.manifestUrl to provider.id],
                        )
                    }
            }
            .sortedWith(
                compareBy<TvRankedProviderHealthEntry> { entry ->
                    providerHealthSortKey(entry.health).availabilityTier
                }.thenByDescending { entry ->
                    providerHealthSortKey(entry.health).performanceScore
                }.thenBy { entry ->
                    providerHealthSortKey(entry.health).statusTier
                }.thenBy { entry ->
                    providerHealthSortKey(entry.health).responseMs
                }.thenBy { entry ->
                    entry.provider.name.lowercase()
                }
            )
    }
    val summary = remember(repositories, knownHealth) {
        healthStore.summary(repositories, runtime.pluginStore)
    }
    val measuredProviders = rankedProviders.count {
        healthStore.performance(it.health).historyRuns > 0
    }
    var diagnosticTarget by remember {
        mutableStateOf<TvRankedProviderHealthEntry?>(null)
    }

    diagnosticTarget?.let { entry ->
        TvProviderDiagnosticDialog(
            repository = entry.repository,
            provider = entry.provider,
            health = entry.health,
            currentlyEnabled = runtime.pluginStore.isProviderEnabled(entry.repository, entry.provider),
            providerCodeReady = providerCodeStore.isReady(entry.repository, entry.provider),
            onDismiss = { diagnosticTarget = null },
        )
    }

    val entries = if (rankedProviders.isEmpty()) {
        listOf(
            TvSettingsEntry(
                id = "empty",
                title = "No enabled providers",
                subtitle = "Enable a provider in Plugins & Providers, then run source discovery to build health history.",
                value = "—",
                enabled = false,
                section = "PROVIDER RANKING",
            )
        )
    } else {
        rankedProviders.mapIndexed { index, entry ->
            val performance = healthStore.performance(entry.health)
            val status = entry.health?.status ?: ProviderHealthStatus.UNKNOWN
            val timing = performance.averageResponseMs
                ?.let(::formatTvProviderAverageResponse)
                ?: "No timing"
            val history = if (performance.historyRuns > 0) {
                val hit = performance.hitRatePercent?.let { "$it% hit" } ?: "No hit rate"
                "$hit • $timing • ${performance.historyRuns} runs"
            } else {
                "No scan history yet"
            }

            TvSettingsEntry(
                id = "provider-health-${entry.repository.manifestUrl.hashCode()}-${entry.provider.id}",
                title = "#${index + 1} ${entry.provider.name}",
                subtitle = "${entry.repository.name} • $history • OK diagnostics",
                value = "Score ${performance.score} • ${status.label}",
                onActivate = { diagnosticTarget = entry },
                section = "PROVIDER RANKING",
                icon = Icons.Default.SettingsInputComponent,
            )
        }
    }

    TvSettingsListScreen(
        title = "Provider Health",
        subtitle = "Historical provider performance. Ranking updates automatically after real source scans.",
        entries = entries,
        onNavigate = onNavigate,
        onProfile = onProfile,
        onBack = onBack,
        topLabel = "Content Manager",
        metrics = listOf(
            TvSettingsMetric("$measuredProviders/${rankedProviders.size}", "Measured"),
            TvSettingsMetric(summary.online.toString(), "Online"),
            TvSettingsMetric(summary.slow.toString(), "Slow"),
            TvSettingsMetric(
                (summary.failed + summary.blocked + summary.unavailable + summary.timeout).toString(),
                "Failed",
            ),
        ),
        footer = "Score affects scan order only. Providers are not removed, and No Results is not treated as a hard failure.",
    )
}

private fun formatTvProviderAverageResponse(responseMs: Long): String =
    if (responseMs < 1_000L) {
        "${responseMs} ms avg"
    } else {
        val tenths = ((responseMs + 50L) / 100L) / 10.0
        "${tenths}s avg"
    }
