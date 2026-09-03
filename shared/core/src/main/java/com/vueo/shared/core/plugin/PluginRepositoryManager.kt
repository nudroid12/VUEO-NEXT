package com.vueo.shared.core.plugin

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PluginRepositoryInstallResult(
    val repository: PluginRepositoryDescriptor,
    val codeSync: ProviderCodeSyncResult,
)

data class PluginRepositoryRefreshSummary(
    val refreshedRepositories: Int,
    val failedRepositories: Int,
    val readyProviders: Int,
    val failedProviders: Int,
    val errors: List<String>,
)

class PluginRepositoryManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = PluginStore(appContext)
    private val codeSync = ProviderCodeSyncManager(appContext)
    private val refreshConcurrency = Semaphore(3)

    suspend fun installOrRefresh(
        inputUrl: String,
        forceCodeRefresh: Boolean = true,
    ): PluginRepositoryInstallResult {
        val repository = PluginRepositoryClient.fetch(inputUrl)
        val sync = codeSync.syncRepository(repository, force = forceCodeRefresh)
        store.upsert(repository)
        PluginRuntimeCache.clear()
        return PluginRepositoryInstallResult(repository, sync)
    }

    /** Refresh every installed manifest while keeping existing enable/disable preferences. */
    suspend fun refreshInstalled(
        forceCodeRefresh: Boolean = false,
    ): PluginRepositoryRefreshSummary = coroutineScope {
        val installed = store.repositories()

        if (installed.isEmpty()) {
            return@coroutineScope PluginRepositoryRefreshSummary(
                refreshedRepositories = 0,
                failedRepositories = 0,
                readyProviders = 0,
                failedProviders = 0,
                errors = emptyList(),
            )
        }

        val outcomes =
            installed.map { existing ->
                async {
                    refreshConcurrency.withPermit {
                        runCatching {
                            installOrRefresh(
                                inputUrl = existing.manifestUrl,
                                forceCodeRefresh = forceCodeRefresh,
                            )
                        }
                    }
                }
            }.awaitAll()

        val successes = outcomes.mapNotNull { it.getOrNull() }
        val failures = outcomes.mapNotNull { it.exceptionOrNull() }

        PluginRepositoryRefreshSummary(
            refreshedRepositories = successes.size,
            failedRepositories = failures.size,
            readyProviders = successes.sumOf { it.codeSync.readyProviders },
            failedProviders = successes.sumOf { it.codeSync.failedProviders },
            errors =
                (
                    successes.flatMap { it.codeSync.errors } +
                        failures.map {
                            it.message ?: it::class.java.simpleName
                        }
                )
                    .distinct()
                    .take(8),
        )
    }
}
