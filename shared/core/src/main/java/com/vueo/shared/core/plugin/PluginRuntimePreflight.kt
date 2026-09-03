package com.vueo.shared.core.plugin

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Result of making enabled provider code ready before QuickJS execution. */
data class PluginRuntimePreparation(
    val targetProviders: Int,
    val alreadyReadyProviders: Int,
    val repairedProviders: Int,
    val failedRepairs: Int,
    val errors: List<String>,
)

/**
 * Repairs missing provider scripts automatically.
 *
 * This removes a common failure mode where a repository exists and is enabled,
 * but source discovery fails only because its JavaScript file was never cached
 * locally (or the manifest now points at a new provider version/filename).
 */
internal class PluginRuntimePreflight(
    context: Context,
) {
    private val codeStore = ProviderCodeStore(context.applicationContext)
    private val syncManager = ProviderCodeSyncManager(context.applicationContext)
    private val repositoryConcurrency = Semaphore(3)

    suspend fun prepare(
        targets: List<
            Pair<
                PluginRepositoryDescriptor,
                PluginProviderDescriptor
            >
        >,
    ): PluginRuntimePreparation = coroutineScope {
        if (targets.isEmpty()) {
            return@coroutineScope PluginRuntimePreparation(
                targetProviders = 0,
                alreadyReadyProviders = 0,
                repairedProviders = 0,
                failedRepairs = 0,
                errors = emptyList(),
            )
        }

        val ready =
            targets.count { (repository, provider) ->
                codeStore.isReady(repository, provider)
            }

        val missingByRepository =
            targets
                .filterNot { (repository, provider) ->
                    codeStore.isReady(repository, provider)
                }
                .groupBy(
                    keySelector = { it.first.manifestUrl },
                    valueTransform = { it },
                )

        val syncResults =
            missingByRepository.values
                .map { repositoryTargets ->
                    async {
                        repositoryConcurrency.withPermit {
                            val repository = repositoryTargets.first().first
                            val providers = repositoryTargets.map { it.second }

                            syncManager.syncProviders(
                                repository = repository,
                                providers = providers,
                                force = false,
                            )
                        }
                    }
                }
                .awaitAll()

        val repaired =
            targets.count { (repository, provider) ->
                codeStore.isReady(repository, provider)
            } - ready

        PluginRuntimePreparation(
            targetProviders = targets.size,
            alreadyReadyProviders = ready,
            repairedProviders = repaired.coerceAtLeast(0),
            failedRepairs =
                syncResults.sumOf { it.failedProviders },
            errors =
                syncResults
                    .flatMap { result -> result.errors }
                    .distinct()
                    .take(8),
        )
    }
}
