package com.vueo.shared.core.plugin

import android.app.ActivityManager
import android.content.Context
import android.util.Base64
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRequest
import com.vueo.shared.core.source.SourceResolveResult
import com.vueo.shared.core.source.SourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

data class ProviderDiagnostic(
    val repositoryManifestUrl: String,
    val repositoryName: String,
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus,
    val responseMs: Long,
    val streamCount: Int,
    val requestTmdbId: String? = null,
    val requestMediaType: String? = null,
    val requestSeason: Int? = null,
    val requestEpisode: Int? = null,
    val timeoutMs: Long? = null,
    val errorType: String? = null,
    val error: String? = null,
    val logs: List<String> = emptyList(),
)

data class PluginDiscoveryProgress(
    val result: PluginDiscoveryResult,
    val completedProviders: Int,
    val totalProviders: Int,
)

data class PluginDiscoveryResult(
    val streams: List<SourceCandidate>,
    val attemptedProviders: Int,
    val successfulProviders: Int,
    val slowProviders: Int,
    val noResultProviders: Int,
    val needsSetupProviders: Int,
    val unavailableProviders: Int,
    val blockedProviders: Int,
    val timeoutProviders: Int,
    val failedProviders: Int,
    val diagnostics: List<ProviderDiagnostic>,
    val fromCache: Boolean = false,
    val coalesced: Boolean = false,
    val readyProviders: Int = 0,
    val repairedProviders: Int = 0,
    val preflightErrors: List<String> = emptyList(),
)

class PluginSourceEngine(
    context: Context,
    private val store: PluginStore,
) {
    private val appContext =
        context.applicationContext

    private val activityManager =
        appContext.getSystemService(
            Context.ACTIVITY_SERVICE
        ) as? ActivityManager

    private val memoryClassMb =
        activityManager?.memoryClass
            ?: DEFAULT_MEMORY_CLASS_MB

    /*
     * Some devices report ActivityManager.memoryClass above the process'
     * actual Java heap growth limit. Runtime.maxMemory() is the limit that
     * matters to QuickJS/OkHttp allocations.
     */
    private val heapLimitMb =
        (
            Runtime.getRuntime().maxMemory() /
                (1024L * 1024L)
        ).toInt()

    private val lowMemoryDevice =
        activityManager?.isLowRamDevice == true ||
            memoryClassMb <= LOW_MEMORY_CLASS_MB ||
            heapLimitMb <= LOW_MEMORY_HEAP_LIMIT_MB

    /*
     * QuickJS instances are memory-heavy. Five simultaneous providers could
     * push a 256 MB heap close to OOM. Use a fast-lane queue on low-memory phones: up to three providers may
     * run, but only one long-running/heavy provider may occupy the queue.
     * That leaves two slots available for lighter/direct providers so first
     * results can arrive quickly without allowing several heavy QuickJS/WebView
     * jobs to spike the heap together.
     *
     * Higher-memory devices get four total slots, still below the old five.
     */
    private val providerConcurrencyLimit =
        if (lowMemoryDevice) 3 else 4

    private val webViewConcurrencyLimit =
        if (lowMemoryDevice) 1 else 2

    private val sourceScanBudgetMs =
        if (lowMemoryDevice) {
            LOW_MEMORY_SCAN_BUDGET_MS
        } else {
            DEFAULT_SCAN_BUDGET_MS
        }

    private val concurrency =
        Semaphore(providerConcurrencyLimit)

    private val webViewConcurrency =
        Semaphore(webViewConcurrencyLimit)

    private val webViewResolver =
        PluginWebViewResolver(
            appContext
        )

    private val healthStore =
        PluginHealthStore(
            appContext
        )

    private val codeStore =
        ProviderCodeStore(
            appContext
        )

    private val preflight =
        PluginRuntimePreflight(
            appContext
        )

    init {
        RuntimeDiagnostics.install(appContext)
    }

suspend fun discover(
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
    mediaTitle: String? = null,
    mediaOriginalTitle: String? = null,
    mediaAliases: List<String> = emptyList(),
    mediaYear: String? = null,
    mediaExternalId: String? = null,
    mediaOriginalLanguage: String? = null,
): PluginDiscoveryResult =
    discoverProgressive(
        tmdbId = tmdbId,
        mediaType = mediaType,
        season = season,
        episode = episode,
        mediaTitle = mediaTitle,
        mediaOriginalTitle = mediaOriginalTitle,
        mediaAliases = mediaAliases,
        mediaYear = mediaYear,
        mediaExternalId = mediaExternalId,
        mediaOriginalLanguage = mediaOriginalLanguage,
        onProgress = {},
    )

suspend fun discoverProgressive(
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
    mediaTitle: String? = null,
    mediaOriginalTitle: String? = null,
    mediaAliases: List<String> = emptyList(),
    mediaYear: String? = null,
    mediaExternalId: String? = null,
    mediaOriginalLanguage: String? = null,
    onProgress: suspend (PluginDiscoveryProgress) -> Unit,
): PluginDiscoveryResult =
    supervisorScope {

        if (!store.pluginsEnabled()) {
            return@supervisorScope emptyDiscoveryResult()
        }

        val knownHealth =
            healthStore.records()
                .associateBy {
                    it.repositoryManifestUrl to
                        it.providerId
                }
        val knownHealthSortKeys =
            knownHealth.mapValues {
                (_, record) ->

                providerHealthSortKey(
                    record
                )
            }
        val defaultHealthSortKey =
            providerHealthSortKey(
                null
            )

        val targets =
            store.repositories()
                .filter(store::isRepositoryEnabled)
                .flatMap { repository ->
                    repository.providers
                        .filter { provider ->
                            store.isProviderEnabled(
                                repository,
                                provider,
                            )
                        }
                        .filter { provider ->
                            provider.supportedTypes
                                .isEmpty() ||
                                mediaType.lowercase() in
                                provider.supportedTypes
                        }
                        .filter { provider ->
                            "android" !in
                                provider.disabledPlatforms
                        }
                        .map { provider ->
                            repository to provider
                        }
                }
                .sortedWith(
                    compareBy<
                        Pair<
                            PluginRepositoryDescriptor,
                            PluginProviderDescriptor
                        >
                    > { target ->
                        knownHealthSortKeys[
                            target.first.manifestUrl to
                                target.second.id
                        ]?.availabilityTier
                            ?: defaultHealthSortKey
                                .availabilityTier
                    }.thenByDescending { target ->
                        knownHealthSortKeys[
                            target.first.manifestUrl to
                                target.second.id
                        ]?.performanceScore
                            ?: defaultHealthSortKey
                                .performanceScore
                    }.thenBy { target ->
                        knownHealthSortKeys[
                            target.first.manifestUrl to
                                target.second.id
                        ]?.statusTier
                            ?: defaultHealthSortKey
                                .statusTier
                    }.thenBy { target ->
                        knownHealthSortKeys[
                            target.first.manifestUrl to
                                target.second.id
                        ]?.responseMs
                            ?: defaultHealthSortKey
                                .responseMs
                    }
                )

        if (targets.isEmpty()) {
            return@supervisorScope emptyDiscoveryResult()
        }

        val cacheKey =
            PluginRuntimeCache.key(
                store = store,
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
            )

        PluginRuntimeCache.get(cacheKey)
            ?.let { cached ->
                onProgress(
                    PluginDiscoveryProgress(
                        result = cached,
                        completedProviders = targets.size,
                        totalProviders = targets.size,
                    )
                )
                return@supervisorScope cached
            }

        val flight =
            PluginRuntimeCache.acquireFlight(
                cacheKey
            )

        if (!flight.owner) {
            val joined =
                flight.deferred
                    .await()
                    .copy(
                        coalesced = true
                    )

            onProgress(
                PluginDiscoveryProgress(
                    result = joined,
                    completedProviders = targets.size,
                    totalProviders = targets.size,
                )
            )

            return@supervisorScope joined
        }

        val runtimeDiagnosticScanId =
            RuntimeDiagnostics.beginSourceScan(
                requestLabel = buildString {
                    append(mediaType.lowercase())
                    append(" • TMDB ")
                    append(tmdbId)
                    if (season != null && episode != null) {
                        append(" • S")
                        append(season.toString().padStart(2, '0'))
                        append(" E")
                        append(episode.toString().padStart(2, '0'))
                    }
                },
                targetProviders = targets.size,
            )
        val runtimeDiagnosticCompletedProviders = java.util.concurrent.atomic.AtomicInteger(0)
        val discoveryContextBroker =
            PluginDiscoveryContextBroker(
                scanId = runtimeDiagnosticScanId,
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                seedTitle = mediaTitle,
                seedOriginalTitle = mediaOriginalTitle,
                seedAliases = mediaAliases,
                seedYear = mediaYear,
                seedExternalId = mediaExternalId,
                seedOriginalLanguage = mediaOriginalLanguage,
            )

        RuntimeDiagnostics.recordDiscoveryTrace(
            scanId = runtimeDiagnosticScanId,
            stage = "CONTEXT_INIT",
            details = "tmdb=$tmdbId type=${mediaType.lowercase()} season=${season ?: 0} episode=${episode ?: 0}",
        )

        try {
            val preparation =
                preflight.prepare(targets)

            val runs =
                mutableListOf<ProviderRun>()

            val mutex = Mutex()

            val providerJobs =
                targets.map {
                    (repository, provider) ->

                    async {
                        val providerStartedNs =
                            System.nanoTime()

                        val run =
                            try {
                                val execute: suspend () -> ProviderRun = {
                                    concurrency.withPermit {
                                        withContext(Dispatchers.Default) {
                                            val providerDiagnosticToken =
                                                RuntimeDiagnostics.beginProvider(
                                                    scanId = runtimeDiagnosticScanId,
                                                    providerName = provider.name,
                                                )
                                            try {
                                                runProvider(
                                                    repository =
                                                        repository,
                                                    provider =
                                                        provider,
                                                    tmdbId =
                                                        tmdbId,
                                                    mediaType =
                                                        mediaType,
                                                    season =
                                                        season,
                                                    episode =
                                                        episode,
                                                    runtimeDiagnosticScanId =
                                                        runtimeDiagnosticScanId,
                                                    discoveryContextBroker =
                                                        discoveryContextBroker,
                                                ).also { providerRun ->
                                                    RuntimeDiagnostics.finishProvider(
                                                        token = providerDiagnosticToken,
                                                        status = providerRun.diagnostic.status.name,
                                                        streamCount = providerRun.diagnostic.streamCount,
                                                        errorType = providerRun.diagnostic.errorType,
                                                    )
                                                }
                                            } catch (error: CancellationException) {
                                                RuntimeDiagnostics.finishProvider(
                                                    token = providerDiagnosticToken,
                                                    status = "CANCELLED",
                                                    streamCount = 0,
                                                    errorType = error::class.java.simpleName,
                                                )
                                                throw error
                                            } catch (error: Throwable) {
                                                RuntimeDiagnostics.finishProvider(
                                                    token = providerDiagnosticToken,
                                                    status = "THREW",
                                                    streamCount = 0,
                                                    errorType = error::class.java.simpleName,
                                                )
                                                throw error
                                            }
                                        }
                                    }
                                }

                                /*
                                 * Keep direct/API phases concurrent. Only the
                                 * actual WebView resolver is serialized on
                                 * low-memory devices.
                                 */
                                execute()
                            } catch (error: CancellationException) {
                                // Caller cancellation or the overall source-scan budget.
                                throw error
                            } catch (error: Throwable) {
                                failedProviderRun(
                                    repository = repository,
                                    provider = provider,
                                    tmdbId = tmdbId,
                                    mediaType = mediaType,
                                    season = season,
                                    episode = episode,
                                    elapsedMs =
                                        (
                                            System.nanoTime() -
                                                providerStartedNs
                                        ) / 1_000_000L,
                                    error = error,
                                )
                            }

                        saveHealth(run)
                        runtimeDiagnosticCompletedProviders.incrementAndGet()

                        val snapshot =
                            mutex.withLock {
                                runs += run

                                PluginDiscoveryProgress(
                                    result =
                                        buildDiscoveryResult(
                                            runs
                                        ).copy(
                                            readyProviders =
                                                preparation.alreadyReadyProviders +
                                                    preparation.repairedProviders,
                                            repairedProviders =
                                                preparation.repairedProviders,
                                            preflightErrors =
                                                preparation.errors,
                                        ),
                                    completedProviders =
                                        runs.size,
                                    totalProviders =
                                        targets.size,
                                )
                            }

                        onProgress(snapshot)
                    }
                }

            val completedWithinBudget =
                withTimeoutOrNull(
                    sourceScanBudgetMs
                ) {
                    providerJobs.awaitAll()
                    true
                } ?: false

            if (!completedWithinBudget) {
                providerJobs
                    .filter { it.isActive }
                    .forEach { job ->
                        job.cancel(
                            CancellationException(
                                "Plugin source scan budget reached"
                            )
                        )
                    }

                /*
                 * supervisorScope waits for children before returning anyway;
                 * joining here makes the cancellation point explicit and keeps
                 * native fetch/WebView cleanup deterministic.
                 */
                providerJobs.forEach { job ->
                    job.join()
                }
            }

            val result =
                mutex.withLock {
                    buildDiscoveryResult(
                        runs
                    ).copy(
                        readyProviders =
                            preparation.alreadyReadyProviders +
                                preparation.repairedProviders,
                        repairedProviders =
                            preparation.repairedProviders,
                        preflightErrors =
                            preparation.errors,
                    )
                }

            if (completedWithinBudget) {
                PluginRuntimeCache.put(
                    key = cacheKey,
                    result = result,
                )
            }

            /*
             * Coalesced callers still receive useful partial results when the
             * scan budget is reached, but partial runs are not stored in the
             * normal runtime cache so a later refresh can try remaining
             * providers.
             */
            PluginRuntimeCache.completeFlight(
                key = cacheKey,
                result = result,
            )

            RuntimeDiagnostics.finishSourceScan(
                scanId = runtimeDiagnosticScanId,
                streams = result.streams.size,
                completedProviders = result.attemptedProviders,
                outcome =
                    if (completedWithinBudget) {
                        "complete"
                    } else {
                        "budget"
                    },
            )

            result
        } catch (error: Throwable) {
            RuntimeDiagnostics.failSourceScan(
                scanId = runtimeDiagnosticScanId,
                error = error,
                completedProviders = runtimeDiagnosticCompletedProviders.get(),
            )
            PluginRuntimeCache.failFlight(
                key = cacheKey,
                error = error,
            )
            throw error
        }

    }


private fun failedProviderRun(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
    elapsedMs: Long,
    error: Throwable,
): ProviderRun =
    ProviderRun(
        streams = emptyList(),
        diagnostic =
            ProviderDiagnostic(
                repositoryManifestUrl =
                    repository.manifestUrl,
                repositoryName =
                    repository.name,
                providerId =
                    provider.id,
                providerName =
                    provider.name,
                status =
                    ProviderHealthStatus.FAILED,
                responseMs =
                    elapsedMs.coerceAtLeast(0L),
                streamCount = 0,
                requestTmdbId = tmdbId,
                requestMediaType = mediaType,
                requestSeason = season,
                requestEpisode = episode,
                timeoutMs =
                    providerRuntimeTimeoutMs(
                        provider
                    ),
                errorType =
                    error::class.java.simpleName,
                error =
                    error.message
                        ?: error::class.java.simpleName,
                logs = emptyList(),
            ),
    )


private fun saveHealth(
    run: ProviderRun,
) {
    healthStore.save(
        ProviderHealthRecord(
            repositoryManifestUrl =
                run.diagnostic
                    .repositoryManifestUrl,
            repositoryName =
                run.diagnostic
                    .repositoryName,
            providerId =
                run.diagnostic
                    .providerId,
            providerName =
                run.diagnostic
                    .providerName,
            status =
                run.diagnostic
                    .status,
            responseMs =
                run.diagnostic
                    .responseMs,
            streamCount =
                run.diagnostic
                    .streamCount,
            requestTmdbId = run.diagnostic.requestTmdbId,
            requestMediaType = run.diagnostic.requestMediaType,
            requestSeason = run.diagnostic.requestSeason,
            requestEpisode = run.diagnostic.requestEpisode,
            timeoutMs = run.diagnostic.timeoutMs,
            errorType = run.diagnostic.errorType,
            error =
                run.diagnostic
                    .error,
            logs =
                run.diagnostic
                    .logs
                    .takeLast(
                        MAX_STORED_LOGS
                    ),
            lastCheckedEpochMs =
                System.currentTimeMillis(),
        )
    )
}

private fun buildDiscoveryResult(
    runs: List<ProviderRun>,
): PluginDiscoveryResult {
    val diagnostics =
        runs.map {
            it.diagnostic
        }

    return PluginDiscoveryResult(
        streams =
            runs
                .flatMap {
                    it.streams
                }
                .distinctBy {
                    listOf(
                        it.url,
                        it.infoHash,
                        it.fileIndex,
                        it.providerId,
                    )
                },
        attemptedProviders =
            runs.size,
        successfulProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .ONLINE
            },
        slowProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .SLOW
            },
        noResultProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .NO_RESULTS
            },
        needsSetupProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .NEEDS_SETUP
            },
        unavailableProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .UNAVAILABLE
            },
        blockedProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .BLOCKED
            },
        timeoutProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .TIMEOUT
            },
        failedProviders =
            diagnostics.count {
                it.status ==
                    ProviderHealthStatus
                        .FAILED
            },
        diagnostics =
            diagnostics,
    )
}

private fun emptyDiscoveryResult():
    PluginDiscoveryResult =
    PluginDiscoveryResult(
        streams = emptyList(),
        attemptedProviders = 0,
        successfulProviders = 0,
        slowProviders = 0,
        noResultProviders = 0,
        needsSetupProviders = 0,
        unavailableProviders = 0,
        blockedProviders = 0,
        timeoutProviders = 0,
        failedProviders = 0,
        diagnostics = emptyList(),
    )

    private suspend fun runProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        runtimeDiagnosticScanId: Long,
        discoveryContextBroker: PluginDiscoveryContextBroker,
    ): ProviderRun {
        val started =
            System.nanoTime()

        val providerTimeoutMs =
            providerRuntimeTimeoutMs(provider)

        val execution =
            withTimeoutOrNull(
                providerTimeoutMs
            ) {
                try {
                    executeProvider(
                        repository =
                            repository,
                        provider =
                            provider,
                        tmdbId =
                            tmdbId,
                        mediaType =
                            mediaType,
                        season =
                            season,
                        episode =
                            episode,
                        runtimeDiagnosticScanId =
                            runtimeDiagnosticScanId,
                        discoveryContextBroker =
                            discoveryContextBroker,
                    )
                } catch (error: CancellationException) {
                    // Preserve timeout/caller cancellation so WebView and provider work can stop.
                    throw error
                } catch (error: Throwable) {
                    ProviderExecution(
                        streams =
                            emptyList(),
                        error =
                            error.message
                                ?: error::class
                                    .java
                                    .simpleName,
                        errorType = error::class.java.simpleName,
                        logs =
                            emptyList(),
                    )
                }
            }
                ?: ProviderExecution(
                    streams =
                        emptyList(),
                    error =
                        "Timed out after " +
                        "${providerTimeoutMs / 1000}s",
                    errorType = "Timeout",
                    logs =
                        emptyList(),
                )

        val elapsedMs =
            (
                System.nanoTime() -
                started
            ) / 1_000_000L

        val consoleError =
            execution.logs
                .lastOrNull {
                    it.startsWith(
                        "ERROR: "
                    )
                }
                ?.removePrefix(
                    "ERROR: "
                )

        val error =
            execution.error
                ?: consoleError

        val status =
            when {
                execution.streams
                    .isNotEmpty() &&
                    elapsedMs >=
                    SLOW_THRESHOLD_MS ->
                    ProviderHealthStatus
                        .SLOW

                execution.streams
                    .isNotEmpty() ->
                    ProviderHealthStatus
                        .ONLINE

                error != null ->
                    classifyProviderFailure(
                        error
                    )

                else ->
                    ProviderHealthStatus
                        .NO_RESULTS
            }

        val rankBoost =
            when (status) {
                ProviderHealthStatus.ONLINE ->
                    if (elapsedMs < 1_000L) 30 else 22

                ProviderHealthStatus.SLOW ->
                    8

                else ->
                    0
            }

        return ProviderRun(
            streams =
                execution.streams.map {
                    it.copy(
                        rankBoost =
                            it.rankBoost +
                                rankBoost
                    )
                },
            diagnostic =
                ProviderDiagnostic(
                    repositoryManifestUrl =
                        repository.manifestUrl,
                    repositoryName =
                        repository.name,
                    providerId =
                        provider.id,
                    providerName =
                        provider.name,
                    status =
                        status,
                    responseMs =
                        elapsedMs,
                    streamCount =
                        execution.streams.size,
                    requestTmdbId = tmdbId,
                    requestMediaType = mediaType,
                    requestSeason = season,
                    requestEpisode = episode,
                    timeoutMs = providerTimeoutMs,
                    errorType = execution.errorType,
                    error =
                        error,
                    logs =
                        execution.logs
                            .takeLast(
                                MAX_STORED_LOGS
                            ),
                ),
        )
    }

    private suspend fun executeProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        runtimeDiagnosticScanId: Long,
        discoveryContextBroker: PluginDiscoveryContextBroker,
    ): ProviderExecution {
        val source =
            codeStore.read(
                repository,
                provider,
            )
                ?: return ProviderExecution(
                    streams =
                        emptyList(),
                    error =
                        "Provider code is not installed locally. " +
                        "Open Content Manager > Plugins and refresh this repository.",
                    errorType = "ProviderCodeMissing",
                    logs =
                        emptyList(),
                )

        val logs =
            CopyOnWriteArrayList<String>()

        val httpTraceCount =
            java.util.concurrent.atomic.AtomicInteger(0)
        val webViewTraceCount =
            java.util.concurrent.atomic.AtomicInteger(0)

        val providerHttpSession =
            PluginHttp.newSession()

        val providerTimeoutMs =
            providerRuntimeTimeoutMs(provider)

        return try {
            val resultJson =
                quickJs {
                    evaluationTimeoutMillis =
                        providerTimeoutMs

                    define("console") {
                        function("log") { args ->
                            logs +=
                                "LOG: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }

                        function("info") { args ->
                            logs +=
                                "LOG: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }

                        function("warn") { args ->
                            logs +=
                                "LOG: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }

                        function("error") { args ->
                            logs +=
                                "ERROR: " +
                                args
                                    .joinToString(" ")
                                    .take(
                                        MAX_LOG_LENGTH
                                    )
                        }
                    }

                    asyncFunction<String, String>(
                        "__vueoDiscoveryContext"
                    ) { requestJson ->
                        discoveryContextBroker
                            .resolveFromProviderRequest(
                                requestJson
                            )
                    }

                    function<String, String>(
                        "__vueoTrace"
                    ) { traceJson ->
                        val trace =
                            runCatching {
                                JSONObject(traceJson)
                            }.getOrNull()

                        RuntimeDiagnostics.recordDiscoveryTrace(
                            scanId = runtimeDiagnosticScanId,
                            providerName = provider.name,
                            stage =
                                trace
                                    ?.optString("stage")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "PROVIDER",
                            details =
                                trace
                                    ?.opt("details")
                                    ?.toString()
                                    ?: traceJson,
                        )
                        "ok"
                    }

                    asyncFunction<String, String>(
                        "__vueoNativeFetch"
                    ) { requestJson ->
                        val startedNs =
                            System.nanoTime()
                        val sharedTmdb =
                            discoveryContextBroker
                                .interceptTmdbFetch(
                                    requestJson
                                )
                        val responseJson =
                            sharedTmdb
                                ?: providerHttpSession.executeJson(
                                    requestJson
                                )
                        val traceIndex =
                            httpTraceCount.incrementAndGet()

                        if (traceIndex <= MAX_HTTP_TRACE_ENTRIES) {
                            RuntimeDiagnostics.recordDiscoveryTrace(
                                scanId = runtimeDiagnosticScanId,
                                providerName = provider.name,
                                stage = "HTTP",
                                details =
                                    summarizeHttpTrace(
                                        requestJson = requestJson,
                                        responseJson = responseJson,
                                        elapsedMs =
                                            (
                                                System.nanoTime() -
                                                    startedNs
                                            ) / 1_000_000L,
                                        sharedTmdb =
                                            sharedTmdb != null,
                                    ),
                            )
                        }

                        responseJson
                    }

                    function<String, Boolean>(
                        "__vueoCancelFetch"
                    ) { requestId ->
                        providerHttpSession.cancel(requestId)
                        true
                    }

                    asyncFunction<String, String>(
                        "__vueoWebViewResolve"
                    ) { requestJson ->
                        val startedNs =
                            System.nanoTime()
                        val responseJson =
                            webViewConcurrency.withPermit {
                                webViewResolver.resolveJson(
                                    requestJson
                                )
                            }
                        val traceIndex =
                            webViewTraceCount.incrementAndGet()

                        if (traceIndex <= MAX_WEBVIEW_TRACE_ENTRIES) {
                            RuntimeDiagnostics.recordDiscoveryTrace(
                                scanId = runtimeDiagnosticScanId,
                                providerName = provider.name,
                                stage = "WEBVIEW",
                                details =
                                    summarizeWebViewTrace(
                                        requestJson = requestJson,
                                        responseJson = responseJson,
                                        elapsedMs =
                                            (
                                                System.nanoTime() -
                                                    startedNs
                                            ) / 1_000_000L,
                                    ),
                            )
                        }

                        responseJson
                    }

                    val htmlBridge =
                        HtmlCompatBridge()

                    function<String, String>(
                        "__vueoHtmlOp"
                    ) { requestJson ->
                        htmlBridge.execute(
                            requestJson
                        )
                    }

                    function<String, String>(
                        "__vueoCryptoOp"
                    ) { requestJson ->
                        CryptoCompatBridge.execute(
                            requestJson
                        )
                    }

                    function<String, String>(
                        "__vueoUrlOp"
                    ) { requestJson ->
                        UrlCompatBridge.execute(
                            requestJson
                        )
                    }

                    function<String, String>(
                        "__vueoBase64"
                    ) { value ->
                        Base64.encodeToString(
                            value.toByteArray(
                                Charsets.UTF_8
                            ),
                            Base64.NO_WRAP,
                        )
                    }

                    function<String, String>(
                        "__vueoBase64Decode"
                    ) { value ->
                        String(
                            Base64.decode(
                                value,
                                Base64.DEFAULT,
                            ),
                            Charsets.UTF_8,
                        )
                    }

                    function<String, String>(
                        "__vueoBinaryToBase64"
                    ) { value ->
                        val bytes =
                            ByteArray(value.length) { index ->
                                (
                                    value[index].code and 0xFF
                                ).toByte()
                            }

                        Base64.encodeToString(
                            bytes,
                            Base64.NO_WRAP,
                        )
                    }

                    function<String, String>(
                        "__vueoBase64ToBinary"
                    ) { value ->
                        val bytes =
                            Base64.decode(
                                value,
                                Base64.DEFAULT,
                            )

                        buildString(
                            bytes.size
                        ) {
                            bytes.forEach { byte ->
                                append(
                                    (
                                        byte.toInt() and 0xFF
                                    ).toChar()
                                )
                            }
                        }
                    }

                    asyncFunction<Double, Boolean>(
                        "__vueoDelay"
                    ) { millis ->
                        delay(
                            millis
                                .toLong()
                                .coerceIn(
                                    0L,
                                    30_000L,
                                )
                        )
                        true
                    }

                    evaluate<String>(
                        buildRuntimeScript(
                            providerScript =
                                source,
                            tmdbId =
                                tmdbId,
                            mediaType =
                                mediaType,
                            season =
                                season,
                            episode =
                                episode,
                        ),
                        filename =
                            "${provider.id}.js",
                    )
                }

            ProviderExecution(
                streams =
                    parseProviderStreams(
                        repository =
                            repository,
                        provider =
                            provider,
                        resultJson =
                            resultJson,
                    ).map { stream ->
                        val cookieHeader =
                            stream.url?.let(providerHttpSession::cookieHeader)
                        if (
                            cookieHeader.isNullOrBlank() ||
                            stream.headers.keys.any {
                                it.equals("Cookie", ignoreCase = true)
                            }
                        ) {
                            stream
                        } else {
                            stream.copy(
                                headers =
                                    stream.headers +
                                        ("Cookie" to cookieHeader)
                            )
                        }
                    },
                error =
                    null,
                logs =
                    logs.toList(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ProviderExecution(
                streams =
                    emptyList(),
                error =
                    buildString {
                        append(
                            error.message
                                ?: error::class
                                    .java
                                    .simpleName
                        )
                    },
                errorType = error::class.java.simpleName,
                logs =
                    logs.toList(),
            )
        } finally {
            providerHttpSession.cancelAll()
        }
    }

    private fun buildRuntimeScript(
        providerScript: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): String {
        val safeTmdbId =
            JSONObject.quote(
                tmdbId
            )

        val safeMediaType =
            JSONObject.quote(
                mediaType
            )

        val seasonValue =
            season?.toString()
                ?: "null"

        val episodeValue =
            episode?.toString()
                ?: "null"

        return """
            globalThis.window = globalThis;
            globalThis.global = globalThis;
            globalThis.self = globalThis;
            globalThis.process = globalThis.process || { env: {} };
            globalThis.SCRAPER_SETTINGS =
              globalThis.SCRAPER_SETTINGS || {};
            globalThis.VUEO_DISCOVERY_CONTEXT = {
              version: 3,
              tmdbId: ${safeTmdbId},
              mediaType: ${safeMediaType},
              season: ${seasonValue},
              episode: ${episodeValue},
              title: "",
              originalTitle: "",
              year: "",
              imdbId: "",
              externalId: "",
              originalLanguage: "",
              aliases: []
            };
            globalThis.VUEO_RUNTIME_CAPABILITIES = {
              version: 4,
              fetch: true,
              binaryBody: true,
              blob: true,
              formData: true,
              abortController: true,
              axios: true,
              webViewResolve: true,
              providerCookieSession: true,
              maxResponseBytes: 4194304,
              maxRequestTimeoutMs: 30000
            };

            globalThis.vueoDiscoveryContext =
              async function (tmdbUrl) {
                var raw = await __vueoDiscoveryContext(
                  JSON.stringify({
                    url: String(tmdbUrl || "")
                  })
                );
                var context = JSON.parse(raw);
                if (context && context.error) {
                  throw new Error(context.error);
                }
                if (context && typeof context === "object") {
                  globalThis.VUEO_DISCOVERY_CONTEXT = context;
                }
                return context;
              };

            globalThis.vueoTrace =
              function (stage, details) {
                try {
                  __vueoTrace(
                    JSON.stringify({
                      stage: String(stage || "PROVIDER"),
                      details:
                        details == null
                          ? ""
                          : details
                    })
                  );
                } catch (_) {}
              };

            function __vueoString(value) {
              try {
                if (typeof value === "string") return value;
                return JSON.stringify(value);
              } catch (_) {
                return String(value);
              }
            }

            function VueoHeaders(initial) {
              this._values = {};
              this._names = {};

              if (initial instanceof VueoHeaders) {
                var existing = initial.entries();
                for (var item of existing) {
                  this.append(item[0], item[1]);
                }
              } else if (Array.isArray(initial)) {
                for (var i = 0; i < initial.length; i++) {
                  if (initial[i] && initial[i].length >= 2) {
                    this.append(initial[i][0], initial[i][1]);
                  }
                }
              } else if (initial && typeof initial === "object") {
                var keys = Object.keys(initial);
                for (var j = 0; j < keys.length; j++) {
                  this.append(keys[j], initial[keys[j]]);
                }
              }
            }

            VueoHeaders.prototype.append = function (name, value) {
              var original = String(name);
              var key = original.toLowerCase();
              var text = String(value);
              this._names[key] = this._names[key] || original;
              this._values[key] = this._values[key]
                ? this._values[key] + ", " + text
                : text;
            };

            VueoHeaders.prototype.set = function (name, value) {
              var original = String(name);
              var key = original.toLowerCase();
              this._names[key] = original;
              this._values[key] = String(value);
            };

            VueoHeaders.prototype.get = function (name) {
              var key = String(name).toLowerCase();
              return Object.prototype.hasOwnProperty.call(this._values, key)
                ? this._values[key]
                : null;
            };

            VueoHeaders.prototype.has = function (name) {
              return this.get(name) !== null;
            };

            VueoHeaders.prototype.delete = function (name) {
              var key = String(name).toLowerCase();
              delete this._values[key];
              delete this._names[key];
            };

            VueoHeaders.prototype.entries = function () {
              var self = this;
              return Object.keys(this._values).map(function (key) {
                return [self._names[key] || key, self._values[key]];
              })[Symbol.iterator]();
            };

            VueoHeaders.prototype.keys = function () {
              var self = this;
              return Object.keys(this._values).map(function (key) {
                return self._names[key] || key;
              })[Symbol.iterator]();
            };

            VueoHeaders.prototype.values = function () {
              var self = this;
              return Object.keys(this._values).map(function (key) {
                return self._values[key];
              })[Symbol.iterator]();
            };

            VueoHeaders.prototype.forEach = function (callback, thisArg) {
              var entries = this.entries();
              for (var item of entries) {
                callback.call(thisArg, item[1], item[0], this);
              }
            };

            VueoHeaders.prototype.toJSON = function () {
              var result = {};
              this.forEach(function (value, name) {
                result[name] = value;
              });
              return result;
            };

            if (typeof Symbol !== "undefined" && Symbol.iterator) {
              VueoHeaders.prototype[Symbol.iterator] =
                VueoHeaders.prototype.entries;
            }

            globalThis.Headers = globalThis.Headers || VueoHeaders;

            function __vueoHeaders(raw) {
              return new Headers(raw).toJSON();
            }

            function __vueoUtf8BodyBytes(value) {
              var encoded = unescape(encodeURIComponent(String(value)));
              var bytes = [];
              for (var i = 0; i < encoded.length; i++) {
                bytes.push(encoded.charCodeAt(i) & 255);
              }
              return bytes;
            }

            function __vueoBytesToBuffer(bytes) {
              return new Uint8Array(bytes).buffer;
            }

            function __vueoBase64BodyBytes(value) {
              var binary = atob(String(value || ""));
              var bytes = [];
              for (var i = 0; i < binary.length; i++) {
                bytes.push(binary.charCodeAt(i) & 255);
              }
              return bytes;
            }

            function __vueoBodyBase64(bytes) {
              var binary = "";
              for (var i = 0; i < bytes.length; i++) {
                binary += String.fromCharCode(bytes[i] & 255);
              }
              return __vueoBinaryToBase64(binary);
            }

            function VueoBlob(parts, options) {
              parts = parts || [];
              options = options || {};
              this.type = String(options.type || "").toLowerCase();
              this._bytes = [];

              for (var i = 0; i < parts.length; i++) {
                var part = parts[i];
                var bytes;
                if (part instanceof VueoBlob) {
                  bytes = part._bytes;
                } else if (part instanceof ArrayBuffer) {
                  bytes = Array.prototype.slice.call(new Uint8Array(part));
                } else if (ArrayBuffer.isView && ArrayBuffer.isView(part)) {
                  bytes = Array.prototype.slice.call(
                    new Uint8Array(part.buffer, part.byteOffset, part.byteLength)
                  );
                } else {
                  bytes = __vueoUtf8BodyBytes(part);
                }
                this._bytes = this._bytes.concat(bytes);
              }

              this.size = this._bytes.length;
            }

            VueoBlob.prototype.arrayBuffer = async function () {
              return __vueoBytesToBuffer(this._bytes);
            };

            VueoBlob.prototype.bytes = async function () {
              return new Uint8Array(this._bytes);
            };

            VueoBlob.prototype.text = async function () {
              return new TextDecoder().decode(new Uint8Array(this._bytes));
            };

            VueoBlob.prototype.slice = function (start, end, type) {
              var size = this._bytes.length;
              var from = start == null ? 0 : Number(start);
              var to = end == null ? size : Number(end);
              if (from < 0) from = Math.max(size + from, 0);
              if (to < 0) to = Math.max(size + to, 0);
              return new VueoBlob(
                [new Uint8Array(this._bytes.slice(from, to))],
                { type: type || "" }
              );
            };

            globalThis.Blob = globalThis.Blob || VueoBlob;

            function VueoFile(parts, name, options) {
              VueoBlob.call(this, parts, options);
              this.name = String(name || "");
              this.lastModified = Number(
                options && options.lastModified != null
                  ? options.lastModified
                  : Date.now()
              );
            }
            VueoFile.prototype = Object.create(VueoBlob.prototype);
            VueoFile.prototype.constructor = VueoFile;
            globalThis.File = globalThis.File || VueoFile;

            function VueoFormData() {
              this._entries = [];
            }

            VueoFormData.prototype.append = function (name, value, filename) {
              this._entries.push({
                name: String(name),
                value: value,
                filename: filename == null ? null : String(filename)
              });
            };

            VueoFormData.prototype.set = function (name, value, filename) {
              this.delete(name);
              this.append(name, value, filename);
            };

            VueoFormData.prototype.get = function (name) {
              name = String(name);
              for (var i = 0; i < this._entries.length; i++) {
                if (this._entries[i].name === name) {
                  return this._entries[i].value;
                }
              }
              return null;
            };

            VueoFormData.prototype.getAll = function (name) {
              name = String(name);
              return this._entries.filter(function (entry) {
                return entry.name === name;
              }).map(function (entry) {
                return entry.value;
              });
            };

            VueoFormData.prototype.has = function (name) {
              return this.get(name) !== null;
            };

            VueoFormData.prototype.delete = function (name) {
              name = String(name);
              this._entries = this._entries.filter(function (entry) {
                return entry.name !== name;
              });
            };

            VueoFormData.prototype.entries = function () {
              return this._entries.map(function (entry) {
                return [entry.name, entry.value];
              })[Symbol.iterator]();
            };

            VueoFormData.prototype.keys = function () {
              return this._entries.map(function (entry) {
                return entry.name;
              })[Symbol.iterator]();
            };

            VueoFormData.prototype.values = function () {
              return this._entries.map(function (entry) {
                return entry.value;
              })[Symbol.iterator]();
            };

            VueoFormData.prototype.forEach = function (callback, thisArg) {
              for (var i = 0; i < this._entries.length; i++) {
                var entry = this._entries[i];
                callback.call(thisArg, entry.value, entry.name, this);
              }
            };

            if (typeof Symbol !== "undefined" && Symbol.iterator) {
              VueoFormData.prototype[Symbol.iterator] =
                VueoFormData.prototype.entries;
            }

            globalThis.FormData = globalThis.FormData || VueoFormData;

            function __vueoMultipart(formData) {
              var boundary =
                "----VueoFormBoundary" +
                Math.random().toString(16).slice(2);
              var bytes = [];

              function appendText(value) {
                bytes = bytes.concat(__vueoUtf8BodyBytes(value));
              }

              for (var i = 0; i < formData._entries.length; i++) {
                var entry = formData._entries[i];
                var escapedName = entry.name.replace(/\"/g, "%22");
                appendText("--" + boundary + "\r\n");
                appendText(
                  "Content-Disposition: form-data; name=\"" +
                  escapedName + "\""
                );

                if (entry.value instanceof Blob) {
                  var filename =
                    entry.filename || entry.value.name || "blob";
                  appendText(
                    "; filename=\"" +
                    String(filename).replace(/\"/g, "%22") +
                    "\"\r\n"
                  );
                  appendText(
                    "Content-Type: " +
                    (entry.value.type || "application/octet-stream") +
                    "\r\n\r\n"
                  );
                  bytes = bytes.concat(entry.value._bytes);
                  appendText("\r\n");
                } else {
                  appendText("\r\n\r\n" + String(entry.value) + "\r\n");
                }
              }

              appendText("--" + boundary + "--\r\n");
              return {
                bodyBase64: __vueoBodyBase64(bytes),
                contentType: "multipart/form-data; boundary=" + boundary
              };
            }

            function VueoAbortSignal() {
              this.aborted = false;
              this.reason = undefined;
              this.onabort = null;
              this.__vueoTimeoutMs = 0;
              this._listeners = [];
            }

            VueoAbortSignal.prototype.addEventListener = function (type, callback) {
              if (type === "abort" && typeof callback === "function") {
                this._listeners.push(callback);
              }
            };

            VueoAbortSignal.prototype.removeEventListener = function (type, callback) {
              if (type !== "abort") return;
              this._listeners = this._listeners.filter(function (listener) {
                return listener !== callback;
              });
            };

            VueoAbortSignal.prototype.throwIfAborted = function () {
              if (this.aborted) throw this.reason;
            };

            function __vueoAbortError(reason) {
              if (reason && typeof reason === "object" && reason.name) {
                return reason;
              }
              var error = new Error(
                reason == null ? "The operation was aborted" : String(reason)
              );
              error.name = "AbortError";
              error.code = "ERR_CANCELED";
              return error;
            }

            function VueoAbortController() {
              this.signal = new VueoAbortSignal();
            }

            VueoAbortController.prototype.abort = function (reason) {
              var signal = this.signal;
              if (signal.aborted) return;
              signal.aborted = true;
              signal.reason = __vueoAbortError(reason);
              if (typeof signal.onabort === "function") {
                signal.onabort.call(signal, { type: "abort", target: signal });
              }
              signal._listeners.slice().forEach(function (listener) {
                listener.call(signal, { type: "abort", target: signal });
              });
            };

            VueoAbortSignal.timeout = function (millis) {
              var controller = new VueoAbortController();
              controller.signal.__vueoTimeoutMs = Math.max(0, Number(millis || 0));
              setTimeout(function () {
                var reason = new Error("The operation timed out");
                reason.name = "TimeoutError";
                controller.abort(reason);
              }, controller.signal.__vueoTimeoutMs);
              return controller.signal;
            };

            globalThis.AbortSignal = globalThis.AbortSignal || VueoAbortSignal;
            globalThis.AbortController =
              globalThis.AbortController || VueoAbortController;
            if (typeof globalThis.AbortSignal.timeout !== "function") {
              globalThis.AbortSignal.timeout = VueoAbortSignal.timeout;
            }

            function VueoResponse(nativeResponse, requestUrl) {
              this.ok =
                nativeResponse.status >= 200 &&
                nativeResponse.status < 300;
              this.status = nativeResponse.status || 0;
              this.statusText = nativeResponse.statusText || "";
              this.url = nativeResponse.url || requestUrl;
              this.redirected = this.url !== requestUrl;
              this.type = "basic";
              this.headers = new Headers(nativeResponse.headers || {});
              this.bodyUsed = false;
              this.bodyTruncated = nativeResponse.bodyTruncated === true;
              this._bodyText = nativeResponse.body || "";
              this._bodyBase64 = nativeResponse.bodyBase64 || "";
            }

            VueoResponse.prototype._consume = function () {
              if (this.bodyUsed) {
                throw new TypeError("Body has already been consumed");
              }
              this.bodyUsed = true;
            };

            VueoResponse.prototype._bytes = function () {
              return this._bodyBase64
                ? __vueoBase64BodyBytes(this._bodyBase64)
                : __vueoUtf8BodyBytes(this._bodyText);
            };

            VueoResponse.prototype.text = async function () {
              this._consume();
              if (this._bodyBase64) {
                return new TextDecoder().decode(new Uint8Array(this._bytes()));
              }
              return this._bodyText;
            };

            VueoResponse.prototype.json = async function () {
              var text = await this.text();
              return JSON.parse(text || "null");
            };

            VueoResponse.prototype.arrayBuffer = async function () {
              this._consume();
              return __vueoBytesToBuffer(this._bytes());
            };

            VueoResponse.prototype.bytes = async function () {
              this._consume();
              return new Uint8Array(this._bytes());
            };

            VueoResponse.prototype.blob = async function () {
              this._consume();
              return new Blob(
                [new Uint8Array(this._bytes())],
                { type: this.headers.get("content-type") || "" }
              );
            };

            VueoResponse.prototype.clone = function () {
              if (this.bodyUsed) {
                throw new TypeError("Body has already been consumed");
              }
              return new VueoResponse({
                status: this.status,
                statusText: this.statusText,
                url: this.url,
                headers: this.headers.toJSON(),
                body: this._bodyText,
                bodyBase64: this._bodyBase64,
                bodyTruncated: this.bodyTruncated
              }, this.url);
            };

            globalThis.Response = globalThis.Response || VueoResponse;

            function VueoRequest(input, init) {
              init = init || {};
              var source = input && typeof input === "object" ? input : {};
              this.url = String(source.url || input || "");
              this.method = String(init.method || source.method || "GET").toUpperCase();
              this.headers = new Headers(init.headers || source.headers || {});
              this.body = init.body !== undefined ? init.body : source.body;
              this.redirect = String(init.redirect || source.redirect || "follow");
              this.signal = init.signal || source.signal || null;
              this.timeout = Number(init.timeout || source.timeout || 0);
              this.binaryResponse =
                init.binaryResponse === true || source.binaryResponse === true;
            }

            VueoRequest.prototype.clone = function () {
              return new VueoRequest(this);
            };

            globalThis.Request = globalThis.Request || VueoRequest;

            function __vueoSerializeBody(body, headers) {
              if (body == null) return { body: null, bodyBase64: null };

              if (body instanceof FormData) {
                var multipart = __vueoMultipart(body);
                if (!headers.has("content-type")) {
                  headers.set("Content-Type", multipart.contentType);
                }
                return { body: null, bodyBase64: multipart.bodyBase64 };
              }

              if (
                typeof URLSearchParams !== "undefined" &&
                body instanceof URLSearchParams
              ) {
                if (!headers.has("content-type")) {
                  headers.set(
                    "Content-Type",
                    "application/x-www-form-urlencoded;charset=UTF-8"
                  );
                }
                return { body: body.toString(), bodyBase64: null };
              }

              if (body instanceof Blob) {
                if (body.type && !headers.has("content-type")) {
                  headers.set("Content-Type", body.type);
                }
                return {
                  body: null,
                  bodyBase64: __vueoBodyBase64(body._bytes)
                };
              }

              if (body instanceof ArrayBuffer) {
                return {
                  body: null,
                  bodyBase64: __vueoBodyBase64(
                    Array.prototype.slice.call(new Uint8Array(body))
                  )
                };
              }

              if (ArrayBuffer.isView && ArrayBuffer.isView(body)) {
                return {
                  body: null,
                  bodyBase64: __vueoBodyBase64(
                    Array.prototype.slice.call(
                      new Uint8Array(body.buffer, body.byteOffset, body.byteLength)
                    )
                  )
                };
              }

              return { body: String(body), bodyBase64: null };
            }

            globalThis.webviewResolve = async function (input, options) {
              options = options || {};

              var request = {};
              if (input && typeof input === "object") {
                Object.keys(input).forEach(function (key) {
                  request[key] = input[key];
                });
              } else {
                request.url = String(input || "");
              }

              Object.keys(options).forEach(function (key) {
                request[key] = options[key];
              });

              var raw = await __vueoWebViewResolve(
                JSON.stringify(request)
              );
              var result = JSON.parse(raw);

              if (result.error) {
                throw new Error(result.error);
              }

              return result;
            };

            globalThis.webViewResolve =
              globalThis.webviewResolve;

            var __vueoFetchSequence = 0;

            globalThis.fetch = async function (input, init) {
              var fetchRequest = new Request(input, init || {});
              var signal = fetchRequest.signal;
              if (signal && signal.aborted) {
                throw signal.reason;
              }

              var headers = fetchRequest.headers;
              var serialized = __vueoSerializeBody(fetchRequest.body, headers);
              var requestId = "fetch-" + (++__vueoFetchSequence);
              var timeoutMs = fetchRequest.timeout;
              if (signal && signal.__vueoTimeoutMs > 0) {
                timeoutMs = timeoutMs > 0
                  ? Math.min(timeoutMs, signal.__vueoTimeoutMs)
                  : signal.__vueoTimeoutMs;
              }

              var request = {
                requestId: requestId,
                url: fetchRequest.url,
                method: fetchRequest.method,
                headers: headers.toJSON(),
                body: serialized.body,
                bodyBase64: serialized.bodyBase64,
                contentType: headers.get("content-type"),
                redirect: fetchRequest.redirect,
                timeoutMs: timeoutMs,
                binaryResponse: fetchRequest.binaryResponse
              };

              var abortListener = function () {
                __vueoCancelFetch(requestId);
              };
              if (signal) signal.addEventListener("abort", abortListener);

              try {
                var raw = await __vueoNativeFetch(JSON.stringify(request));
                if (signal && signal.aborted) {
                  throw signal.reason;
                }

                var response = JSON.parse(raw);
                if (response.error) {
                  var networkError = new TypeError(response.error);
                  networkError.code = /timeout|timed out/i.test(response.error)
                    ? "ETIMEDOUT"
                    : (/cancel/i.test(response.error)
                        ? "ERR_CANCELED"
                        : "ERR_NETWORK");
                  networkError.errorType = response.errorType || "NetworkError";
                  throw networkError;
                }

                return new Response(response, request.url);
              } finally {
                if (signal) {
                  signal.removeEventListener("abort", abortListener);
                }
              }
            };

            function __vueoAxiosParams(url, params) {
              if (!params) return url;
              var query = [];
              Object.keys(params).forEach(function (key) {
                var values = Array.isArray(params[key]) ? params[key] : [params[key]];
                values.forEach(function (value) {
                  if (value == null) return;
                  query.push(
                    encodeURIComponent(key) +
                    (Array.isArray(params[key]) ? "[]" : "") +
                    "=" + encodeURIComponent(String(value))
                  );
                });
              });
              if (!query.length) return url;
              return url + (url.indexOf("?") >= 0 ? "&" : "?") + query.join("&");
            }

            function __vueoAxiosMerge(base, extra) {
              var result = {};
              Object.keys(base || {}).forEach(function (key) { result[key] = base[key]; });
              Object.keys(extra || {}).forEach(function (key) { result[key] = extra[key]; });
              result.headers = Object.assign({}, (base || {}).headers || {}, (extra || {}).headers || {});
              return result;
            }

            function __vueoAxiosHeaders(raw, method) {
              raw = raw || {};
              var merged = {};
              var groups = [raw.common, raw[String(method || "").toLowerCase()]];
              groups.forEach(function (group) {
                Object.keys(group || {}).forEach(function (key) {
                  merged[key] = group[key];
                });
              });
              var reserved = ["common", "get", "delete", "head", "options", "post", "put", "patch"];
              Object.keys(raw).forEach(function (key) {
                if (reserved.indexOf(key.toLowerCase()) < 0) {
                  merged[key] = raw[key];
                }
              });
              return merged;
            }

            function __vueoAxiosTransform(data, headers, transforms, status) {
              if (!transforms) return data;
              var functions = Array.isArray(transforms) ? transforms : [transforms];
              for (var i = 0; i < functions.length; i++) {
                if (typeof functions[i] === "function") {
                  data = functions[i](data, headers, status);
                }
              }
              return data;
            }

            function __vueoInterceptorManager() {
              this.handlers = [];
            }
            __vueoInterceptorManager.prototype.use = function (fulfilled, rejected) {
              this.handlers.push({ fulfilled: fulfilled, rejected: rejected });
              return this.handlers.length - 1;
            };
            __vueoInterceptorManager.prototype.eject = function (id) {
              if (this.handlers[id]) this.handlers[id] = null;
            };

            function __vueoCreateAxios(defaults) {
              var instance = function (config) {
                return instance.request(config);
              };

              instance.defaults = defaults || {};
              instance.interceptors = {
                request: new __vueoInterceptorManager(),
                response: new __vueoInterceptorManager()
              };

              instance.request = function (config) {
                var chain = Promise.resolve(__vueoAxiosMerge(instance.defaults, config || {}));
                instance.interceptors.request.handlers.forEach(function (handler) {
                  if (handler) chain = chain.then(handler.fulfilled, handler.rejected);
                });

                chain = chain.then(async function (finalConfig) {
                  if (finalConfig.cancelToken) {
                    finalConfig.cancelToken.throwIfRequested();
                  }
                  var method = String(finalConfig.method || "GET").toUpperCase();
                  var url = String(finalConfig.url || "");
                  if (finalConfig.baseURL && !/^https?:\/\//i.test(url)) {
                    url = String(finalConfig.baseURL).replace(/\/$/, "") +
                      "/" + url.replace(/^\//, "");
                  }
                  if (finalConfig.paramsSerializer) {
                    var serializedParams =
                      typeof finalConfig.paramsSerializer === "function"
                        ? finalConfig.paramsSerializer(finalConfig.params || {})
                        : finalConfig.paramsSerializer.serialize(finalConfig.params || {});
                    if (serializedParams) {
                      url += (url.indexOf("?") >= 0 ? "&" : "?") + serializedParams;
                    }
                  } else {
                    url = __vueoAxiosParams(url, finalConfig.params);
                  }

                  var headers = new Headers(
                    __vueoAxiosHeaders(finalConfig.headers, method)
                  );
                  if (finalConfig.auth && !headers.has("authorization")) {
                    headers.set(
                      "Authorization",
                      "Basic " + btoa(
                        String(finalConfig.auth.username || "") +
                        ":" + String(finalConfig.auth.password || "")
                      )
                    );
                  }
                  var body = __vueoAxiosTransform(
                    finalConfig.data,
                    headers,
                    finalConfig.transformRequest
                  );
                  var isBodyObject =
                    body &&
                    typeof body === "object" &&
                    !(body instanceof FormData) &&
                    !(body instanceof URLSearchParams) &&
                    !(body instanceof Blob) &&
                    !(body instanceof ArrayBuffer) &&
                    !(ArrayBuffer.isView && ArrayBuffer.isView(body));
                  if (isBodyObject) {
                    body = JSON.stringify(body);
                    if (!headers.has("content-type")) {
                      headers.set("Content-Type", "application/json");
                    }
                  }

                  var requestSignal = finalConfig.signal || null;
                  if (finalConfig.cancelToken) {
                    var cancelController = new AbortController();
                    if (requestSignal) {
                      if (requestSignal.aborted) {
                        cancelController.abort(requestSignal.reason);
                      } else {
                        requestSignal.addEventListener("abort", function () {
                          cancelController.abort(requestSignal.reason);
                        });
                      }
                    }
                    finalConfig.cancelToken.promise.then(function (reason) {
                      cancelController.abort(reason);
                    });
                    requestSignal = cancelController.signal;
                  }

                  var responseType = String(finalConfig.responseType || "json").toLowerCase();
                  var response;
                  try {
                    response = await fetch(url, {
                      method: method,
                      headers: headers,
                      body: body == null ? null : body,
                      signal: requestSignal,
                      timeout: Number(finalConfig.timeout || 0),
                      binaryResponse:
                        responseType === "arraybuffer" ||
                        responseType === "blob",
                      redirect: finalConfig.maxRedirects === 0 ? "manual" : "follow"
                    });
                  } catch (cause) {
                    if (
                      cause &&
                      (cause.name === "AbortError" || cause.code === "ERR_CANCELED")
                    ) {
                      var canceled = new VueoCanceledError(cause.message);
                      canceled.config = finalConfig;
                      canceled.cause = cause;
                      throw canceled;
                    }

                    var networkError = new Error(cause && cause.message
                      ? cause.message
                      : "Network Error");
                    networkError.name = "AxiosError";
                    networkError.code =
                      /timeout|timed out/i.test(networkError.message)
                        ? "ECONNABORTED"
                        : "ERR_NETWORK";
                    networkError.config = finalConfig;
                    networkError.cause = cause;
                    networkError.isAxiosError = true;
                    throw networkError;
                  }

                  var data;
                  if (responseType === "arraybuffer") {
                    data = await response.arrayBuffer();
                  } else if (responseType === "blob") {
                    data = await response.blob();
                  } else {
                    var text = await response.text();
                    if (responseType === "text") {
                      data = text;
                    } else {
                      try { data = JSON.parse(text); } catch (_) { data = text; }
                    }
                  }
                  data = __vueoAxiosTransform(
                    data,
                    response.headers,
                    finalConfig.transformResponse,
                    response.status
                  );

                  var axiosResponse = {
                    data: data,
                    status: response.status,
                    statusText: response.statusText,
                    headers: response.headers,
                    config: finalConfig,
                    request: null
                  };
                  var valid = typeof finalConfig.validateStatus === "function"
                    ? finalConfig.validateStatus(response.status)
                    : response.status >= 200 && response.status < 300;
                  if (!valid) {
                    var error = new Error("Request failed with status " + response.status);
                    error.name = "AxiosError";
                    error.code = "ERR_BAD_RESPONSE";
                    error.config = finalConfig;
                    error.response = axiosResponse;
                    error.isAxiosError = true;
                    throw error;
                  }
                  return axiosResponse;
                });

                instance.interceptors.response.handlers.forEach(function (handler) {
                  if (handler) chain = chain.then(handler.fulfilled, handler.rejected);
                });
                return chain;
              };

              ["get", "delete", "head", "options"].forEach(function (method) {
                instance[method] = function (url, config) {
                  return instance.request(Object.assign({}, config || {}, {
                    url: url,
                    method: method.toUpperCase()
                  }));
                };
              });

              ["post", "put", "patch"].forEach(function (method) {
                instance[method] = function (url, data, config) {
                  return instance.request(Object.assign({}, config || {}, {
                    url: url,
                    method: method.toUpperCase(),
                    data: data
                  }));
                };
              });

              instance.create = function (config) {
                return __vueoCreateAxios(__vueoAxiosMerge(instance.defaults, config || {}));
              };
              instance.all = function (promises) { return Promise.all(promises); };
              instance.spread = function (callback) {
                return function (values) { return callback.apply(null, values); };
              };
              instance.isAxiosError = function (error) {
                return !!(error && error.isAxiosError);
              };
              instance.CancelToken = VueoCancelToken;
              instance.CanceledError = VueoCanceledError;
              instance.Cancel = VueoCanceledError;
              instance.isCancel = function (error) {
                return !!(error && error.__CANCEL__);
              };
              return instance;
            }

            function VueoCanceledError(message) {
              this.name = "CanceledError";
              this.message = message == null ? "canceled" : String(message);
              this.code = "ERR_CANCELED";
              this.__CANCEL__ = true;
              this.isAxiosError = true;
            }
            VueoCanceledError.prototype = Object.create(Error.prototype);
            VueoCanceledError.prototype.constructor = VueoCanceledError;

            function VueoCancelToken(executor) {
              if (typeof executor !== "function") {
                throw new TypeError("executor must be a function");
              }
              var token = this;
              this.reason = null;
              this.promise = new Promise(function (resolve) {
                executor(function (message) {
                  if (token.reason) return;
                  token.reason = new VueoCanceledError(message);
                  resolve(token.reason);
                });
              });
            }
            VueoCancelToken.prototype.throwIfRequested = function () {
              if (this.reason) throw this.reason;
            };
            VueoCancelToken.source = function () {
              var cancel;
              var token = new VueoCancelToken(function (cancelFunction) {
                cancel = cancelFunction;
              });
              return { token: token, cancel: cancel };
            };

            var __vueoAxiosModule = __vueoCreateAxios({
              headers: { common: {} }
            });
            __vueoAxiosModule.default = __vueoAxiosModule;
            __vueoAxiosModule.VERSION = "1.x-vueo";
            globalThis.axios = __vueoAxiosModule;

            globalThis.btoa = function (value) {
              return __vueoBinaryToBase64(
                String(value)
              );
            };

            globalThis.atob = function (value) {
              return __vueoBase64ToBinary(
                String(value)
              );
            };

            function __vueoMakeBuffer(bytes) {
              var result = new Uint8Array(bytes || []);
              result.__vueoBuffer = true;
              result.toString = function (encoding) {
                encoding = String(encoding || "utf8").toLowerCase();
                var values = Array.prototype.slice.call(this);
                if (encoding === "base64") return __vueoBodyBase64(values);
                if (encoding === "hex") {
                  return values.map(function (value) {
                    return (value < 16 ? "0" : "") + value.toString(16);
                  }).join("");
                }
                return new TextDecoder().decode(this);
              };
              result.slice = function (start, end) {
                return __vueoMakeBuffer(
                  Array.prototype.slice.call(this, start, end)
                );
              };
              return result;
            }

            var VueoBuffer = {
              from: function (value, encoding) {
                encoding = String(encoding || "utf8").toLowerCase();
                if (value instanceof ArrayBuffer) {
                  return __vueoMakeBuffer(
                    Array.prototype.slice.call(new Uint8Array(value))
                  );
                }
                if (ArrayBuffer.isView && ArrayBuffer.isView(value)) {
                  return __vueoMakeBuffer(
                    Array.prototype.slice.call(
                      new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
                    )
                  );
                }
                if (Array.isArray(value)) return __vueoMakeBuffer(value);
                if (encoding === "base64") {
                  return __vueoMakeBuffer(__vueoBase64BodyBytes(value));
                }
                if (encoding === "hex") {
                  var bytes = [];
                  var hex = String(value).replace(/\s/g, "");
                  for (var i = 0; i + 1 < hex.length; i += 2) {
                    bytes.push(parseInt(hex.slice(i, i + 2), 16));
                  }
                  return __vueoMakeBuffer(bytes);
                }
                return __vueoMakeBuffer(__vueoUtf8BodyBytes(value));
              },
              alloc: function (size, fill) {
                var bytes = new Array(Math.max(0, Number(size || 0))).fill(0);
                if (fill != null) {
                  var fillBuffer = VueoBuffer.from(fill);
                  for (var i = 0; i < bytes.length; i++) {
                    bytes[i] = fillBuffer.length ? fillBuffer[i % fillBuffer.length] : 0;
                  }
                }
                return __vueoMakeBuffer(bytes);
              },
              concat: function (buffers) {
                var bytes = [];
                (buffers || []).forEach(function (buffer) {
                  bytes = bytes.concat(Array.prototype.slice.call(buffer));
                });
                return __vueoMakeBuffer(bytes);
              },
              isBuffer: function (value) {
                return !!(value && value.__vueoBuffer);
              },
              byteLength: function (value, encoding) {
                return VueoBuffer.from(value, encoding).length;
              }
            };

            globalThis.Buffer = globalThis.Buffer || VueoBuffer;

            var __vueoTimerSequence = 0;
            var __vueoTimers = {};

            globalThis.setTimeout = function (callback, millis) {
              var id = ++__vueoTimerSequence;
              __vueoTimers[id] = true;
              __vueoDelay(Number(millis || 0))
                .then(function () {
                  if (!__vueoTimers[id]) return;
                  delete __vueoTimers[id];
                  callback();
                });
              return id;
            };

            globalThis.clearTimeout = function (id) {
              delete __vueoTimers[id];
            };

            globalThis.setInterval = function (callback, millis) {
              var id = ++__vueoTimerSequence;
              __vueoTimers[id] = true;

              function tick() {
                __vueoDelay(Number(millis || 0)).then(function () {
                  if (!__vueoTimers[id]) return;
                  callback();
                  tick();
                });
              }

              tick();
              return id;
            };

            globalThis.clearInterval = globalThis.clearTimeout;

            function __vueoNativeUrl(input, base) {
                          var raw = __vueoUrlOp(
                            JSON.stringify({
                              input: String(input),
                              base:
                                base == null
                                  ? ""
                                  : String(
                                      base.href ||
                                      base
                                    )
                            })
                          );

                          var parsed = JSON.parse(raw);

                          if (parsed.error) {
                            throw new TypeError(parsed.error);
                          }

                          return parsed;
                        }

                        globalThis.URLSearchParams =
                          function (initial) {
                            this._pairs = [];

                            if (
                              initial &&
                              initial._pairs &&
                              Array.isArray(initial._pairs)
                            ) {
                              this._pairs =
                                initial._pairs.map(
                                  function (pair) {
                                    return [
                                      String(pair[0]),
                                      String(pair[1])
                                    ];
                                  }
                                );
                              return;
                            }

                            if (Array.isArray(initial)) {
                              for (
                                var ai = 0;
                                ai < initial.length;
                                ai++
                              ) {
                                if (
                                  initial[ai] &&
                                  initial[ai].length >= 2
                                ) {
                                  this.append(
                                    initial[ai][0],
                                    initial[ai][1]
                                  );
                                }
                              }
                              return;
                            }

                            if (typeof initial === "string") {
                              var source =
                                initial.charAt(0) === "?"
                                  ? initial.slice(1)
                                  : initial;

                              if (source) {
                                var parts = source.split("&");

                                for (
                                  var i = 0;
                                  i < parts.length;
                                  i++
                                ) {
                                  if (!parts[i]) continue;

                                  var eq =
                                    parts[i].indexOf("=");

                                  var rawKey =
                                    eq >= 0
                                      ? parts[i].slice(0, eq)
                                      : parts[i];

                                  var rawValue =
                                    eq >= 0
                                      ? parts[i].slice(eq + 1)
                                      : "";

                                  this.append(
                                    decodeURIComponent(
                                      rawKey.replace(/\+/g, " ")
                                    ),
                                    decodeURIComponent(
                                      rawValue.replace(/\+/g, " ")
                                    )
                                  );
                                }
                              }
                              return;
                            }

                            if (
                              initial &&
                              typeof initial === "object"
                            ) {
                              var keys =
                                Object.keys(initial);

                              for (
                                var j = 0;
                                j < keys.length;
                                j++
                              ) {
                                this.append(
                                  keys[j],
                                  initial[keys[j]]
                                );
                              }
                            }
                          };

                        URLSearchParams.prototype.append =
                          function (key, value) {
                            this._pairs.push([
                              String(key),
                              String(value)
                            ]);
                          };

                        URLSearchParams.prototype.set =
                          function (key, value) {
                            this.delete(key);
                            this.append(key, value);
                          };

                        URLSearchParams.prototype.get =
                          function (key) {
                            key = String(key);

                            for (
                              var i = 0;
                              i < this._pairs.length;
                              i++
                            ) {
                              if (
                                this._pairs[i][0] === key
                              ) {
                                return this._pairs[i][1];
                              }
                            }

                            return null;
                          };

                        URLSearchParams.prototype.getAll =
                          function (key) {
                            key = String(key);

                            return this._pairs
                              .filter(function (pair) {
                                return pair[0] === key;
                              })
                              .map(function (pair) {
                                return pair[1];
                              });
                          };

                        URLSearchParams.prototype.has =
                          function (key) {
                            return this.get(key) !== null;
                          };

                        URLSearchParams.prototype.delete =
                          function (key) {
                            key = String(key);

                            this._pairs =
                              this._pairs.filter(
                                function (pair) {
                                  return pair[0] !== key;
                                }
                              );
                          };

                        URLSearchParams.prototype.sort =
                          function () {
                            this._pairs = this._pairs
                              .map(function (pair, index) {
                                return { pair: pair, index: index };
                              })
                              .sort(function (left, right) {
                                if (left.pair[0] < right.pair[0]) return -1;
                                if (left.pair[0] > right.pair[0]) return 1;
                                return left.index - right.index;
                              })
                              .map(function (entry) {
                                return entry.pair;
                              });
                          };

                        Object.defineProperty(
                          URLSearchParams.prototype,
                          "size",
                          {
                            get: function () {
                              return this._pairs.length;
                            }
                          }
                        );

                        URLSearchParams.prototype.keys =
                          function () {
                            return this._pairs
                              .map(function (pair) {
                                return pair[0];
                              })
                              [Symbol.iterator]();
                          };

                        URLSearchParams.prototype.values =
                          function () {
                            return this._pairs
                              .map(function (pair) {
                                return pair[1];
                              })
                              [Symbol.iterator]();
                          };

                        URLSearchParams.prototype.entries =
                          function () {
                            return this._pairs
                              .map(function (pair) {
                                return [
                                  pair[0],
                                  pair[1]
                                ];
                              })
                              [Symbol.iterator]();
                          };

                        URLSearchParams.prototype.forEach =
                          function (callback, thisArg) {
                            for (
                              var i = 0;
                              i < this._pairs.length;
                              i++
                            ) {
                              callback.call(
                                thisArg,
                                this._pairs[i][1],
                                this._pairs[i][0],
                                this
                              );
                            }
                          };

                        URLSearchParams.prototype.toString =
                          function () {
                            return this._pairs
                              .map(function (pair) {
                                return (
                                  encodeURIComponent(pair[0])
                                    .replace(/%20/g, "+") +
                                  "=" +
                                  encodeURIComponent(pair[1])
                                    .replace(/%20/g, "+")
                                );
                              })
                              .join("&");
                          };

                        if (
                          typeof Symbol !== "undefined" &&
                          Symbol.iterator
                        ) {
                          URLSearchParams.prototype[
                            Symbol.iterator
                          ] =
                            URLSearchParams.prototype.entries;
                        }

                        globalThis.URL =
                          function (input, base) {
                            var parsed =
                              __vueoNativeUrl(
                                input,
                                base
                              );

                            this.href = parsed.href;
                            this.origin = parsed.origin;
                            this.protocol = parsed.protocol;
                            this.hostname = parsed.hostname;
                            this.host = parsed.host;
                            this.port = parsed.port;
                            this.pathname = parsed.pathname;
                            this.search = parsed.search;
                            this.hash = parsed.hash;
                            this.searchParams =
                              new URLSearchParams(
                                parsed.queryPairs || []
                              );
                          };

                        URL.prototype.toString =
                          function () {
                            return this.href;
                          };

                        URL.prototype.toJSON =
                          function () {
                            return this.href;
                          };

                        function __vueoUtf8Bytes(value) {
                          var text = String(value);
                          var encoded =
                            unescape(
                              encodeURIComponent(text)
                            );

                          var bytes = [];

                          for (
                            var i = 0;
                            i < encoded.length;
                            i++
                          ) {
                            bytes.push(
                              encoded.charCodeAt(i) & 255
                            );
                          }

                          return bytes;
                        }

                        function __vueoUtf8String(bytes) {
                          var binary = "";

                          for (
                            var i = 0;
                            i < bytes.length;
                            i++
                          ) {
                            binary +=
                              String.fromCharCode(
                                bytes[i] & 255
                              );
                          }

                          try {
                            return decodeURIComponent(
                              escape(binary)
                            );
                          } catch (_) {
                            return binary;
                          }
                        }

                        globalThis.TextEncoder =
                          globalThis.TextEncoder ||
                          function () {};

                        TextEncoder.prototype.encode =
                          function (value) {
                            return new Uint8Array(
                              __vueoUtf8Bytes(value)
                            );
                          };

                        globalThis.TextDecoder =
                          globalThis.TextDecoder ||
                          function () {};

                        TextDecoder.prototype.decode =
                          function (value) {
                            if (value == null) {
                              return "";
                            }

                            return __vueoUtf8String(
                              Array.prototype.slice.call(
                                value
                              )
                            );
                          };

                        function __vueoB64ToBytes(value) {
                          var binary =
                            atob(String(value));

                          var bytes = [];

                          for (
                            var i = 0;
                            i < binary.length;
                            i++
                          ) {
                            bytes.push(
                              binary.charCodeAt(i) & 255
                            );
                          }

                          return bytes;
                        }

                        function __vueoBytesToB64(bytes) {
                          var binary = "";

                          for (
                            var i = 0;
                            i < bytes.length;
                            i++
                          ) {
                            binary +=
                              String.fromCharCode(
                                bytes[i] & 255
                              );
                          }

                          return btoa(binary);
                        }

                        function __vueoWordsToBytes(
                          words,
                          sigBytes
                        ) {
                          words = words || [];

                          var count =
                            sigBytes == null
                              ? words.length * 4
                              : Number(sigBytes);

                          var bytes = [];

                          for (
                            var i = 0;
                            i < count;
                            i++
                          ) {
                            bytes.push(
                              (
                                words[i >>> 2] >>>
                                (
                                  24 -
                                  (i % 4) * 8
                                )
                              ) & 255
                            );
                          }

                          return bytes;
                        }

                        function __vueoBytesToWords(bytes) {
                          var words = [];

                          for (
                            var i = 0;
                            i < bytes.length;
                            i++
                          ) {
                            words[i >>> 2] =
                              (
                                words[i >>> 2] || 0
                              ) |
                              (
                                (bytes[i] & 255) <<
                                (
                                  24 -
                                  (i % 4) * 8
                                )
                              );
                          }

                          return words;
                        }

                        function VueoWordArray(
                          bytes
                        ) {
                          this._bytes =
                            (bytes || []).slice();

                          this.sigBytes =
                            this._bytes.length;

                          this.words =
                            __vueoBytesToWords(
                              this._bytes
                            );
                        }

                        VueoWordArray.prototype.concat =
                          function (other) {
                            var incoming =
                              __vueoAsWordArray(
                                other
                              );

                            this._bytes =
                              this._bytes.concat(
                                incoming._bytes
                              );

                            this.sigBytes =
                              this._bytes.length;

                            this.words =
                              __vueoBytesToWords(
                                this._bytes
                              );

                            return this;
                          };

                        VueoWordArray.prototype.clamp =
                          function () {
                            return this;
                          };

                        VueoWordArray.prototype.clone =
                          function () {
                            return new VueoWordArray(
                              this._bytes
                            );
                          };

                        VueoWordArray.prototype.toString =
                          function (encoder) {
                            return (
                              encoder ||
                              __vueoCryptoJsModule.enc.Hex
                            ).stringify(this);
                          };

                        function __vueoAsWordArray(
                          value
                        ) {
                          if (
                            value instanceof
                            VueoWordArray
                          ) {
                            return value;
                          }

                          if (
                            value &&
                            Array.isArray(value._bytes)
                          ) {
                            return new VueoWordArray(
                              value._bytes
                            );
                          }

                          if (
                            value &&
                            Array.isArray(value.words)
                          ) {
                            return new VueoWordArray(
                              __vueoWordsToBytes(
                                value.words,
                                value.sigBytes
                              )
                            );
                          }

                          if (
                            value instanceof Uint8Array
                          ) {
                            return new VueoWordArray(
                              Array.prototype.slice.call(
                                value
                              )
                            );
                          }

                          if (
                            value instanceof ArrayBuffer
                          ) {
                            return new VueoWordArray(
                              Array.prototype.slice.call(
                                new Uint8Array(value)
                              )
                            );
                          }

                          return new VueoWordArray(
                            __vueoUtf8Bytes(
                              String(value)
                            )
                          );
                        }

                        function __vueoCryptoNative(
                          request
                        ) {
                          var raw =
                            __vueoCryptoOp(
                              JSON.stringify(request)
                            );

                          var parsed =
                            JSON.parse(raw);

                          if (parsed.error) {
                            throw new Error(
                              parsed.error
                            );
                          }

                          return parsed;
                        }

                        globalThis.__crypto_aes_decrypt_raw =
                          function (
                            mode,
                            keyArg,
                            ivArg,
                            dataArg
                          ) {
                            function bytesOf(value) {
                              if (!value) {
                                return [];
                              }

                              return Array.prototype
                                .slice.call(value)
                                .map(
                                  function (byte) {
                                    return (
                                      Number(byte) & 255
                                    );
                                  }
                                );
                            }

                            var result =
                              __vueoCryptoNative({
                                op: "decrypt",
                                algorithm: "AES",
                                data:
                                  __vueoBytesToB64(
                                    bytesOf(dataArg)
                                  ),
                                key:
                                  __vueoBytesToB64(
                                    bytesOf(keyArg)
                                  ),
                                iv:
                                  __vueoBytesToB64(
                                    bytesOf(ivArg)
                                  )
                              });

                            return new Uint8Array(
                              __vueoB64ToBytes(
                                result.data
                              )
                            );
                          };

                        function __vueoCryptoHash(
                          algorithm,
                          input
                        ) {
                          var wordArray =
                            __vueoAsWordArray(
                              input
                            );

                          var result =
                            __vueoCryptoNative({
                              op: "hash",
                              algorithm: algorithm,
                              data:
                                __vueoBytesToB64(
                                  wordArray._bytes
                                )
                            });

                          return new VueoWordArray(
                            __vueoB64ToBytes(
                              result.data
                            )
                          );
                        }

                        function __vueoCryptoHmac(
                          algorithm,
                          data,
                          key
                        ) {
                          var dataWa =
                            __vueoAsWordArray(
                              data
                            );

                          var keyWa =
                            __vueoAsWordArray(
                              key
                            );

                          var result =
                            __vueoCryptoNative({
                              op: "hmac",
                              algorithm: algorithm,
                              data:
                                __vueoBytesToB64(
                                  dataWa._bytes
                                ),
                              key:
                                __vueoBytesToB64(
                                  keyWa._bytes
                                )
                            });

                          return new VueoWordArray(
                            __vueoB64ToBytes(
                              result.data
                            )
                          );
                        }

                        function __vueoCipherData(
                          cipher
                        ) {
                          if (
                            typeof cipher === "string"
                          ) {
                            return cipher;
                          }

                          if (
                            cipher &&
                            cipher.ciphertext
                          ) {
                            return __vueoBytesToB64(
                              __vueoAsWordArray(
                                cipher.ciphertext
                              )._bytes
                            );
                          }

                          return __vueoBytesToB64(
                            __vueoAsWordArray(
                              cipher
                            )._bytes
                          );
                        }

                        function __vueoDecrypt(
                          algorithm,
                          cipher,
                          key,
                          options
                        ) {
                          options =
                            options || {};

                          var keyWa =
                            __vueoAsWordArray(
                              key
                            );

                          var ivWa =
                            options.iv
                              ? __vueoAsWordArray(
                                  options.iv
                                )
                              : new VueoWordArray([]);

                          var result =
                            __vueoCryptoNative({
                              op: "decrypt",
                              algorithm: algorithm,
                              data:
                                __vueoCipherData(
                                  cipher
                                ),
                              key:
                                __vueoBytesToB64(
                                  keyWa._bytes
                                ),
                              iv:
                                __vueoBytesToB64(
                                  ivWa._bytes
                                )
                            });

                          return new VueoWordArray(
                            __vueoB64ToBytes(
                              result.data
                            )
                          );
                        }

                        function __vueoEncrypt(
                          algorithm,
                          plaintext,
                          key,
                          options
                        ) {
                          options =
                            options || {};

                          var dataWa =
                            __vueoAsWordArray(
                              plaintext
                            );

                          var keyWa =
                            __vueoAsWordArray(
                              key
                            );

                          var ivWa =
                            options.iv
                              ? __vueoAsWordArray(
                                  options.iv
                                )
                              : new VueoWordArray([]);

                          var result =
                            __vueoCryptoNative({
                              op: "encrypt",
                              algorithm: algorithm,
                              data:
                                __vueoBytesToB64(
                                  dataWa._bytes
                                ),
                              key:
                                __vueoBytesToB64(
                                  keyWa._bytes
                                ),
                              iv:
                                __vueoBytesToB64(
                                  ivWa._bytes
                                )
                            });

                          var ciphertext =
                            new VueoWordArray(
                              __vueoB64ToBytes(
                                result.data
                              )
                            );

                          return {
                            ciphertext: ciphertext,
                            toString: function () {
                              return __vueoCryptoJsModule.enc.Base64
                                .stringify(
                                  ciphertext
                                );
                            }
                          };
                        }

                        var __vueoCryptoJsModule = {
                          lib: {
                            WordArray: {
                              create:
                                function (
                                  words,
                                  sigBytes
                                ) {
                                  if (
                                    words instanceof
                                    VueoWordArray
                                  ) {
                                    return words.clone();
                                  }

                                  if (
                                    words instanceof
                                    Uint8Array
                                  ) {
                                    return new VueoWordArray(
                                      Array.prototype
                                        .slice.call(
                                          words
                                        )
                                    );
                                  }

                                  return new VueoWordArray(
                                    __vueoWordsToBytes(
                                      words || [],
                                      sigBytes
                                    )
                                  );
                                },

                              random:
                                function (count) {
                                  var result =
                                    __vueoCryptoNative({
                                      op: "random",
                                      count:
                                        Number(count || 0)
                                    });

                                  return new VueoWordArray(
                                    __vueoB64ToBytes(
                                      result.data
                                    )
                                  );
                                }
                            },

                            CipherParams: {
                              create:
                                function (value) {
                                  return value || {};
                                }
                            }
                          },

                          enc: {
                            Utf8: {
                              parse:
                                function (value) {
                                  return new VueoWordArray(
                                    __vueoUtf8Bytes(
                                      value
                                    )
                                  );
                                },

                              stringify:
                                function (wordArray) {
                                  return __vueoUtf8String(
                                    __vueoAsWordArray(
                                      wordArray
                                    )._bytes
                                  );
                                }
                            },

                            Base64: {
                              parse:
                                function (value) {
                                  return new VueoWordArray(
                                    __vueoB64ToBytes(
                                      value
                                    )
                                  );
                                },

                              stringify:
                                function (wordArray) {
                                  return __vueoBytesToB64(
                                    __vueoAsWordArray(
                                      wordArray
                                    )._bytes
                                  );
                                }
                            },

                            Hex: {
                              parse:
                                function (value) {
                                  value =
                                    String(value)
                                      .replace(
                                        /[^0-9a-f]/gi,
                                        ""
                                      );

                                  var bytes = [];

                                  for (
                                    var i = 0;
                                    i < value.length;
                                    i += 2
                                  ) {
                                    bytes.push(
                                      parseInt(
                                        value.slice(
                                          i,
                                          i + 2
                                        ),
                                        16
                                      )
                                    );
                                  }

                                  return new VueoWordArray(
                                    bytes
                                  );
                                },

                              stringify:
                                function (wordArray) {
                                  return __vueoAsWordArray(
                                    wordArray
                                  )._bytes
                                    .map(
                                      function (byte) {
                                        return (
                                          "0" +
                                          (
                                            byte & 255
                                          ).toString(16)
                                        ).slice(-2);
                                      }
                                    )
                                    .join("");
                                }
                            },

                            Latin1: {
                              parse:
                                function (value) {
                                  var bytes = [];

                                  value =
                                    String(value);

                                  for (
                                    var i = 0;
                                    i < value.length;
                                    i++
                                  ) {
                                    bytes.push(
                                      value.charCodeAt(i)
                                        & 255
                                    );
                                  }

                                  return new VueoWordArray(
                                    bytes
                                  );
                                },

                              stringify:
                                function (wordArray) {
                                  return __vueoAsWordArray(
                                    wordArray
                                  )._bytes
                                    .map(
                                      function (byte) {
                                        return String
                                          .fromCharCode(
                                            byte & 255
                                          );
                                      }
                                    )
                                    .join("");
                                }
                            }
                          },

                          mode: {
                            CBC: "CBC"
                          },

                          pad: {
                            Pkcs7: "Pkcs7"
                          },

                          MD5:
                            function (value) {
                              return __vueoCryptoHash(
                                "MD5",
                                value
                              );
                            },

                          SHA1:
                            function (value) {
                              return __vueoCryptoHash(
                                "SHA1",
                                value
                              );
                            },

                          SHA256:
                            function (value) {
                              return __vueoCryptoHash(
                                "SHA256",
                                value
                              );
                            },

                          SHA384:
                            function (value) {
                              return __vueoCryptoHash(
                                "SHA384",
                                value
                              );
                            },

                          SHA512:
                            function (value) {
                              return __vueoCryptoHash(
                                "SHA512",
                                value
                              );
                            },

                          HmacMD5:
                            function (data, key) {
                              return __vueoCryptoHmac(
                                "MD5",
                                data,
                                key
                              );
                            },

                          HmacSHA1:
                            function (data, key) {
                              return __vueoCryptoHmac(
                                "SHA1",
                                data,
                                key
                              );
                            },

                          HmacSHA256:
                            function (data, key) {
                              return __vueoCryptoHmac(
                                "SHA256",
                                data,
                                key
                              );
                            },

                          HmacSHA512:
                            function (data, key) {
                              return __vueoCryptoHmac(
                                "SHA512",
                                data,
                                key
                              );
                            },

                          AES: {
                            decrypt:
                              function (
                                cipher,
                                key,
                                options
                              ) {
                                return __vueoDecrypt(
                                  "AES",
                                  cipher,
                                  key,
                                  options
                                );
                              },

                            encrypt:
                              function (
                                plaintext,
                                key,
                                options
                              ) {
                                return __vueoEncrypt(
                                  "AES",
                                  plaintext,
                                  key,
                                  options
                                );
                              }
                          },

                          TripleDES: {
                            decrypt:
                              function (
                                cipher,
                                key,
                                options
                              ) {
                                return __vueoDecrypt(
                                  "TripleDES",
                                  cipher,
                                  key,
                                  options
                                );
                              },

                            encrypt:
                              function (
                                plaintext,
                                key,
                                options
                              ) {
                                return __vueoEncrypt(
                                  "TripleDES",
                                  plaintext,
                                  key,
                                  options
                                );
                              }
                          }
                        };

                        function __vueoHtmlNative(
                          request
                        ) {
                          var raw =
                            __vueoHtmlOp(
                              JSON.stringify(request)
                            );

                          var parsed =
                            JSON.parse(raw);

                          if (parsed.error) {
                            throw new Error(
                              parsed.error
                            );
                          }

                          return parsed;
                        }

                        function __vueoCheerioLoad(
                          html,
                          options,
                          isDocument
                        ) {
                          var parsed =
                            __vueoHtmlNative({
                              op: "parse",
                              html:
                                html == null
                                  ? ""
                                  : String(html),
                              baseUri:
                                options &&
                                options.baseURI
                                  ? String(
                                      options.baseURI
                                    )
                                  : ""
                            });

                          var documentId =
                            parsed.documentId;

                          function Selection(ids) {
                            this._ids =
                              (ids || []).slice();

                            this.length =
                              this._ids.length;

                            for (
                              var i = 0;
                              i < this._ids.length;
                              i++
                            ) {
                              this[i] = {
                                __vueoNodeId:
                                  this._ids[i],
                                __vueoDocumentId:
                                  documentId
                              };
                            }
                          }

                          function idsFromResponse(
                            response
                          ) {
                            return (
                              response.ids ||
                              []
                            );
                          }

                          function opIds(
                            op,
                            ids,
                            extra
                          ) {
                            var request = {
                              op: op,
                              documentId:
                                documentId,
                              ids:
                                ids || []
                            };

                            if (extra) {
                              Object.keys(extra)
                                .forEach(
                                  function (key) {
                                    request[key] =
                                      extra[key];
                                  }
                                );
                            }

                            return idsFromResponse(
                              __vueoHtmlNative(
                                request
                              )
                            );
                          }

                          Selection.prototype.eq =
                            function (index) {
                              index = Number(index);

                              if (index < 0) {
                                index =
                                  this._ids.length +
                                  index;
                              }

                              if (
                                index < 0 ||
                                index >=
                                this._ids.length
                              ) {
                                return new Selection([]);
                              }

                              return new Selection([
                                this._ids[index]
                              ]);
                            };

                          Selection.prototype.first =
                            function () {
                              return this.eq(0);
                            };

                          Selection.prototype.last =
                            function () {
                              return this.eq(-1);
                            };

                          Selection.prototype.get =
                            function (index) {
                              if (
                                index == null
                              ) {
                                return this.toArray();
                              }

                              var selected =
                                this.eq(index);

                              return selected.length
                                ? selected[0]
                                : undefined;
                            };

                          Selection.prototype.toArray =
                            function () {
                              var result = [];

                              for (
                                var i = 0;
                                i < this._ids.length;
                                i++
                              ) {
                                result.push({
                                  __vueoNodeId:
                                    this._ids[i],
                                  __vueoDocumentId:
                                    documentId
                                });
                              }

                              return result;
                            };

                          Selection.prototype.each =
                            function (callback) {
                              for (
                                var i = 0;
                                i < this._ids.length;
                                i++
                              ) {
                                callback.call(
                                  this[i],
                                  i,
                                  this[i]
                                );
                              }

                              return this;
                            };

                          Selection.prototype.map =
                            function (callback) {
                              var values = [];

                              for (
                                var i = 0;
                                i < this._ids.length;
                                i++
                              ) {
                                values.push(
                                  callback.call(
                                    this[i],
                                    i,
                                    this[i]
                                  )
                                );
                              }

                              return {
                                get:
                                  function (index) {
                                    if (
                                      index == null
                                    ) {
                                      return values;
                                    }
                                    return values[index];
                                  },

                                toArray:
                                  function () {
                                    return values.slice();
                                  }
                              };
                            };

                          Selection.prototype.filter =
                            function (selector) {
                              if (
                                typeof selector ===
                                "function"
                              ) {
                                var kept = [];

                                for (
                                  var i = 0;
                                  i < this._ids.length;
                                  i++
                                ) {
                                  if (
                                    selector.call(
                                      this[i],
                                      i,
                                      this[i]
                                    )
                                  ) {
                                    kept.push(
                                      this._ids[i]
                                    );
                                  }
                                }

                                return new Selection(
                                  kept
                                );
                              }

                              return new Selection(
                                opIds(
                                  "filter",
                                  this._ids,
                                  {
                                    selector:
                                      String(selector)
                                  }
                                )
                              );
                            };

                          Selection.prototype.find =
                            function (selector) {
                              return new Selection(
                                opIds(
                                  "find",
                                  this._ids,
                                  {
                                    selector:
                                      String(selector)
                                  }
                                )
                              );
                            };

                          Selection.prototype.parent =
                            function () {
                              return new Selection(
                                opIds(
                                  "parent",
                                  this._ids
                                )
                              );
                            };

                          Selection.prototype.parents =
                            function (selector) {
                              return new Selection(
                                opIds(
                                  "parents",
                                  this._ids,
                                  {
                                    selector:
                                      selector == null
                                        ? ""
                                        : String(
                                            selector
                                          )
                                  }
                                )
                              );
                            };

                          Selection.prototype.children =
                            function (selector) {
                              return new Selection(
                                opIds(
                                  "children",
                                  this._ids,
                                  {
                                    selector:
                                      selector == null
                                        ? ""
                                        : String(
                                            selector
                                          )
                                  }
                                )
                              );
                            };

                          Selection.prototype.closest =
                            function (selector) {
                              return new Selection(
                                opIds(
                                  "closest",
                                  this._ids,
                                  {
                                    selector:
                                      String(selector)
                                  }
                                )
                              );
                            };

                          Selection.prototype.next =
                            function () {
                              return new Selection(
                                opIds(
                                  "next",
                                  this._ids
                                )
                              );
                            };

                          Selection.prototype.prev =
                            function () {
                              return new Selection(
                                opIds(
                                  "prev",
                                  this._ids
                                )
                              );
                            };

                          Selection.prototype.siblings =
                            function (selector) {
                              return new Selection(
                                opIds(
                                  "siblings",
                                  this._ids,
                                  {
                                    selector:
                                      selector == null
                                        ? ""
                                        : String(
                                            selector
                                          )
                                  }
                                )
                              );
                            };

                          Selection.prototype.text =
                            function () {
                              return __vueoHtmlNative({
                                op: "text",
                                ids: this._ids
                              }).value || "";
                            };

                          Selection.prototype.ownText =
                            function () {
                              return __vueoHtmlNative({
                                op: "ownText",
                                ids: this._ids
                              }).value || "";
                            };

                          Selection.prototype.html =
                            function () {
                              return __vueoHtmlNative({
                                op: "html",
                                ids: this._ids
                              }).value || "";
                            };

                          Selection.prototype.attr =
                            function (name) {
                              if (name == null) {
                                return undefined;
                              }

                              return __vueoHtmlNative({
                                op: "attr",
                                ids: this._ids,
                                name: String(name)
                              }).value || undefined;
                            };

                          Selection.prototype.data =
                            function (name) {
                              return this.attr(
                                "data-" +
                                String(name)
                                  .replace(
                                    /[A-Z]/g,
                                    function (letter) {
                                      return (
                                        "-" +
                                        letter.toLowerCase()
                                      );
                                    }
                                  )
                              );
                            };

                          Selection.prototype.val =
                            function () {
                              return this.attr("value");
                            };

                          Selection.prototype.is =
                            function (selector) {
                              return !!__vueoHtmlNative({
                                op: "is",
                                ids: this._ids,
                                selector:
                                  String(selector)
                              }).bool;
                            };

                          Selection.prototype.hasClass =
                            function (name) {
                              return !!__vueoHtmlNative({
                                op: "hasClass",
                                ids: this._ids,
                                name: String(name)
                              }).bool;
                            };

                          Selection.prototype.remove =
                            function () {
                              __vueoHtmlNative({
                                op: "remove",
                                ids: this._ids
                              });
                              return this;
                            };

                          function $(input) {
                            if (
                              input &&
                              input.__vueoNodeId
                            ) {
                              return new Selection([
                                input.__vueoNodeId
                              ]);
                            }

                            if (
                              input instanceof
                              Selection
                            ) {
                              return input;
                            }

                            var selector =
                              String(input == null
                                ? ""
                                : input);

                            if (!selector) {
                              return new Selection([]);
                            }

                            return new Selection(
                              idsFromResponse(
                                __vueoHtmlNative({
                                  op: "select",
                                  documentId:
                                    documentId,
                                  selector:
                                    selector
                                })
                              )
                            );
                          }

                          $.html =
                            function (selection) {
                              if (
                                selection &&
                                selection._ids
                              ) {
                                return __vueoHtmlNative({
                                  op: "outerHtml",
                                  ids:
                                    selection._ids
                                }).value || "";
                              }

                              return __vueoHtmlNative({
                                op: "html",
                                ids: [
                                  parsed.rootId
                                ]
                              }).value || "";
                            };

                          $.root =
                            function () {
                              return new Selection([
                                parsed.rootId
                              ]);
                            };

                          return $;
                        }

                        var __vueoCheerioModule = {
                          load:
                            function (
                              html,
                              options,
                              isDocument
                            ) {
                              return __vueoCheerioLoad(
                                html,
                                options,
                                isDocument
                              );
                            }
                        };

                        globalThis.require =
                          function (name) {
                            if (
                              name === "axios"
                            ) {
                              return __vueoAxiosModule;
                            }

                            if (
                              name === "node-fetch" ||
                              name === "cross-fetch"
                            ) {
                              var fetchModule = globalThis.fetch;
                              fetchModule.default = globalThis.fetch;
                              fetchModule.Headers = globalThis.Headers;
                              fetchModule.Request = globalThis.Request;
                              fetchModule.Response = globalThis.Response;
                              fetchModule.Blob = globalThis.Blob;
                              fetchModule.FormData = globalThis.FormData;
                              return fetchModule;
                            }

                            if (name === "undici") {
                              return {
                                fetch: globalThis.fetch,
                                Headers: globalThis.Headers,
                                Request: globalThis.Request,
                                Response: globalThis.Response,
                                Blob: globalThis.Blob,
                                File: globalThis.File,
                                FormData: globalThis.FormData
                              };
                            }

                            if (name === "form-data") {
                              return globalThis.FormData;
                            }

                            if (name === "buffer") {
                              return { Buffer: globalThis.Buffer };
                            }

                            if (
                              name ===
                              "cheerio-without-node-native"
                            ) {
                              return __vueoCheerioModule;
                            }

                            if (
                              name === "cheerio"
                            ) {
                              return __vueoCheerioModule;
                            }

                            if (
                              name === "crypto-js"
                            ) {
                              return __vueoCryptoJsModule;
                            }

                            throw new Error(
                              "Unsupported runtime require(): " +
                              name
                            );
                          };

            var module = { exports: {} };
            var exports = module.exports;

            ${providerScript}

            var __vueoGetStreams =
              module &&
              module.exports &&
              typeof module.exports.getStreams === "function"
                ? module.exports.getStreams
                : (
                    typeof globalThis.getStreams === "function"
                      ? globalThis.getStreams
                      : null
                  );

            if (!__vueoGetStreams) {
              throw new Error(
                "Provider does not export getStreams"
              );
            }

            function __vueoNormalizeStreams(value) {
              if (Array.isArray(value)) return value;
              if (!value || typeof value !== "object") return [];

              var collectionKeys = [
                "streams",
                "sources",
                "results",
                "items",
                "data",
                "links"
              ];

              for (var i = 0; i < collectionKeys.length; i++) {
                var collection = value[collectionKeys[i]];
                if (Array.isArray(collection)) return collection;
              }

              if (value.stream && typeof value.stream === "object") {
                return [value.stream];
              }

              if (value.result && typeof value.result === "object") {
                return [value.result];
              }

              if (
                value.url ||
                value.streamUrl ||
                value.stream_url ||
                value.playbackUrl ||
                value.playback_url ||
                value.link ||
                value.src ||
                value.file ||
                value.infoHash ||
                value.info_hash ||
                value.hash ||
                value.magnet
              ) {
                return [value];
              }

              return [];
            }

            var __vueoStreams =
              await Promise.resolve(
                __vueoGetStreams(
                  ${safeTmdbId},
                  ${safeMediaType},
                  ${seasonValue},
                  ${episodeValue}
                )
              );

            JSON.stringify(
              __vueoNormalizeStreams(__vueoStreams)
            );
        """.trimIndent()
    }

    private fun summarizeHttpTrace(
        requestJson: String,
        responseJson: String,
        elapsedMs: Long,
        sharedTmdb: Boolean,
    ): String {
        val request =
            runCatching { JSONObject(requestJson) }
                .getOrNull()
        val response =
            runCatching { JSONObject(responseJson) }
                .getOrNull()

        val method =
            request?.optString("method", "GET")
                ?.uppercase()
                ?: "GET"
        val url =
            compactTraceUrl(
                request?.optString("url").orEmpty()
            )
        val status =
            response?.optInt("status", 0)
                ?: 0
        val error =
            response
                ?.optString("error")
                ?.takeIf { it.isNotBlank() }
        val bodyChars =
            response?.optString("body")?.length
                ?: 0
        val truncated =
            response?.optBoolean(
                "bodyTruncated",
                false,
            ) == true

        return buildString {
            append("request=")
            append(method)
            append(' ')
            append(url)
            append(" status=")
            append(status)
            append(" elapsed=")
            append(elapsedMs)
            append("ms bodyChars=")
            append(bodyChars)
            append(" truncated=")
            append(truncated)
            if (sharedTmdb) {
                append(" sharedTmdb=true")
            }
            if (error != null) {
                append(" error=")
                append(error.take(120))
            }
        }
    }

    private fun summarizeWebViewTrace(
        requestJson: String,
        responseJson: String,
        elapsedMs: Long,
    ): String {
        val request =
            runCatching { JSONObject(requestJson) }
                .getOrNull()
        val response =
            runCatching { JSONObject(responseJson) }
                .getOrNull()
        val url =
            compactTraceUrl(
                request?.optString("url").orEmpty()
            )
        val error =
            response
                ?.optString("error")
                ?.takeIf { it.isNotBlank() }
        val streamCount =
            response
                ?.optJSONArray("streams")
                ?.length()
                ?: 0

        return buildString {
            append("url=")
            append(url)
            append(" elapsed=")
            append(elapsedMs)
            append("ms")
            if (error != null) {
                append(" error=")
                append(error.take(120))
            } else {
                append(" result=ok streams=")
                append(streamCount)
            }
        }
    }

    private fun compactTraceUrl(raw: String): String {
        if (raw.isBlank()) return "<empty>"

        return runCatching {
            val uri = java.net.URI(raw)
            buildString {
                append(uri.scheme ?: "https")
                append("://")
                append(uri.host ?: "unknown")
                val path = uri.rawPath.orEmpty()
                if (path.isNotBlank()) {
                    append(path.take(180))
                }
            }
        }.getOrElse {
            raw.substringBefore('?')
                .substringBefore('#')
                .take(220)
        }
    }

    private data class ProviderExecution(
        val streams: List<SourceCandidate>,
        val error: String?,
        val errorType: String? = null,
        val logs: List<String>,
    )

    private data class ProviderRun(
        val streams: List<SourceCandidate>,
        val diagnostic: ProviderDiagnostic,
    )

    private fun providerRuntimeTimeoutMs(
        provider: PluginProviderDescriptor,
    ): Long =
        provider.runtimeTimeoutMs.coerceIn(
            MIN_PROVIDER_TIMEOUT_MS,
            MAX_PROVIDER_TIMEOUT_MS,
        )

    companion object {
        private const val MIN_PROVIDER_TIMEOUT_MS =
            3_000L

        private const val MAX_PROVIDER_TIMEOUT_MS =
            20_000L

        private const val DEFAULT_MEMORY_CLASS_MB =
            256

        private const val LOW_MEMORY_CLASS_MB =
            256

        private const val LOW_MEMORY_HEAP_LIMIT_MB =
            320

        private const val LOW_MEMORY_SCAN_BUDGET_MS =
            135_000L

        private const val DEFAULT_SCAN_BUDGET_MS =
            120_000L

        private const val SLOW_THRESHOLD_MS =
            3_000L

        private const val MAX_STORED_LOGS =
            24

        private const val MAX_HTTP_TRACE_ENTRIES =
            14

        private const val MAX_WEBVIEW_TRACE_ENTRIES =
            4

        private const val MAX_LOG_LENGTH =
            1000
    }
}


private fun classifyProviderFailure(
    error: String,
): ProviderHealthStatus {
    val normalized =
        error.lowercase()

    return when {
        "timed out" in normalized ||
            "timeout" in normalized ->
            ProviderHealthStatus.TIMEOUT

        "nxdomain" in normalized ||
            "unable to resolve host" in normalized ||
            "unknownhost" in normalized ||
            "no address associated" in normalized ->
            ProviderHealthStatus.UNAVAILABLE

        "no token available" in normalized ||
            "token required" in normalized ||
            "requires token" in normalized ||
            "missing token" in normalized ||
            "ui token" in normalized &&
            (
                "expired" in normalized ||
                "required" in normalized ||
                "missing" in normalized
            ) ->
            ProviderHealthStatus.NEEDS_SETUP

        "http 403" in normalized ||
            "status 403" in normalized ||
            "forbidden" in normalized ||
            "cloudflare" in normalized ||
            "captcha" in normalized ->
            ProviderHealthStatus.BLOCKED

        else ->
            ProviderHealthStatus.FAILED
    }
}

private fun parseProviderStreams(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    resultJson: String,
): List<SourceCandidate> {
    val rootObject =
        runCatching { JSONObject(resultJson) }
            .getOrNull()

    val array =
        runCatching { JSONArray(resultJson) }
            .getOrNull()
            ?: rootObject?.let { root ->
                listOf(
                    "streams",
                    "sources",
                    "results",
                    "items",
                    "data",
                    "links",
                )
                    .firstNotNullOfOrNull { key ->
                        root.optJSONArray(key)
                    }
                    ?: listOf("stream", "result")
                        .firstNotNullOfOrNull { key ->
                            root.optJSONObject(key)
                        }
                        ?.let { JSONArray().put(it) }
                    ?: if (root.hasAnyStreamField()) {
                        JSONArray().put(root)
                    } else {
                        null
                    }
            }
            ?: return emptyList()

    return (0 until array.length())
        .mapNotNull { index ->
            val item =
                array.optJSONObject(index)
                    ?: return@mapNotNull null

            val rawUrl =
                item.firstNonBlank(
                    "url",
                    "streamUrl",
                    "stream_url",
                    "playbackUrl",
                    "playback_url",
                    "link",
                    "src",
                    "file",
                )
            val url =
                rawUrl?.takeIf {
                    it.startsWith("https://") ||
                        it.startsWith("http://")
                }

            val infoHash =
                item.firstNonBlank(
                    "infoHash",
                    "info_hash",
                    "hash",
                    "btih",
                    "magnet",
                )?.extractInfoHash()

            if (url == null && infoHash == null) {
                return@mapNotNull null
            }

            val headers =
                item.streamHeaders()

            val quality =
                item.firstNonBlank(
                    "quality",
                    "resolution",
                    "res",
                )

            val displayName =
                item.firstNonBlank(
                    "title",
                    "name",
                    "label",
                )
                    ?: provider.name

            SourceCandidate(
                id =
                    "plugin:" +
                    repository.manifestUrl.hashCode() +
                    ":" + provider.id +
                    ":" + index,
                name =
                    displayName,
                url =
                    url,
                infoHash =
                    infoHash,
                fileIndex =
                    item.firstInt(
                        "fileIndex",
                        "file_index",
                        "index",
                    ),
                quality =
                    quality,
                codec =
                    item.firstNonBlank(
                        "codec",
                        "videoCodec",
                        "video_codec",
                    ),
                hdr =
                    item.firstNonBlank(
                        "hdr",
                        "dynamicRange",
                    ),
                audio =
                    item.firstNonBlank(
                        "audio",
                        "audioCodec",
                        "audio_codec",
                    ),
                language =
                    listOf(
                        "language",
                        "lang",
                        "audioLanguage",
                        "audio_language",
                    ).firstNotNullOfOrNull { field ->
                        item.firstNonBlank(field)
                    },
                sizeBytes =
                    item.firstLong(
                        "sizeBytes",
                        "size_bytes",
                        "size",
                        "fileSize",
                        "file_size",
                    ),
                headers =
                    headers,
                rankBoost =
                    item.firstInt(
                        "rankBoost",
                        "rank_boost",
                    ) ?: 0,
                providerId =
                    "plugin:" +
                    repository.manifestUrl
                        .hashCode() +
                    ":" +
                    provider.id,
                providerName =
                    "${repository.name} / " +
                    provider.name,
            )
        }
}

private fun JSONObject.hasAnyStreamField(): Boolean =
    listOf(
        "url",
        "streamUrl",
        "stream_url",
        "playbackUrl",
        "playback_url",
        "link",
        "src",
        "file",
        "infoHash",
        "info_hash",
        "hash",
        "btih",
        "magnet",
    ).any(::has)

private fun JSONObject.firstNonBlank(
    vararg fields: String,
): String? =
    fields.firstNotNullOfOrNull { field ->
        optString(field)
            .trim()
            .takeIf { it.isNotBlank() }
    }

private fun JSONObject.firstLong(
    vararg fields: String,
): Long? =
    fields.firstNotNullOfOrNull { field ->
        if (!has(field) || isNull(field)) {
            null
        } else {
            when (val value = opt(field)) {
                is Number -> value.toLong().takeIf { it >= 0L }
                else -> value.toString().parseByteSize()
            }
        }
    }

private fun JSONObject.firstInt(
    vararg fields: String,
): Int? =
    fields.firstNotNullOfOrNull { field ->
        if (!has(field) || isNull(field)) {
            null
        } else {
            opt(field).toString().toIntOrNull()
        }
    }

private fun String.parseByteSize(): Long? {
    val match =
        Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*(b|kb|kib|mb|mib|gb|gib)?$", RegexOption.IGNORE_CASE)
            .matchEntire(trim())
            ?: return null
    val amount = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    val multiplier =
        when (unit) {
            "gb", "gib" -> 1024.0 * 1024.0 * 1024.0
            "mb", "mib" -> 1024.0 * 1024.0
            "kb", "kib" -> 1024.0
            else -> 1.0
        }
    return (amount * multiplier)
        .toLong()
        .takeIf { it >= 0L }
}

private fun String.extractInfoHash(): String? {
    val value = trim()
    if (value.isBlank()) return null
    if (!value.startsWith("magnet:", ignoreCase = true)) {
        return value
    }

    return Regex(
        "(?:^|[?&])xt=urn:btih:([^&]+)",
        RegexOption.IGNORE_CASE,
    ).find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
}

private fun JSONObject.streamHeaders(): Map<String, String> {
    val result = linkedMapOf<String, String>()

    fun merge(value: JSONObject?) {
        value.toStringMap().forEach { (key, headerValue) ->
            if (headerValue.isNotBlank()) result[key] = headerValue
        }
    }

    merge(optJSONObject("headers"))
    merge(optJSONObject("requestHeaders"))
    merge(optJSONObject("request_headers"))

    val behaviorHints = optJSONObject("behaviorHints")
    merge(behaviorHints?.optJSONObject("proxyHeaders"))
    merge(behaviorHints?.optJSONObject("proxy_headers"))
    merge(behaviorHints?.optJSONObject("headers"))
    merge(behaviorHints?.optJSONObject("requestHeaders"))

    return result
}

private fun JSONObject?.toStringMap():
    Map<String, String> {

    if (this == null) {
        return emptyMap()
    }

    val result =
        linkedMapOf<String, String>()

    val iterator =
        keys()

    while (
        iterator.hasNext()
    ) {
        val key =
            iterator.next()

        result[key] =
            optString(key)
    }

    return result
}

/** Exposes JavaScript providers through the common shared source contract. */
class PluginSourceResolver(
    context: Context,
    store: PluginStore,
) : SourceResolver {
    private val engine = PluginSourceEngine(
        context = context.applicationContext,
        store = store,
    )

    override val id: String = "js-providers"
    override val name: String = "JavaScript Providers"

    override suspend fun resolve(request: SourceRequest): SourceResolveResult {
        val result = engine.discover(
            tmdbId = request.videoId,
            mediaType = request.mediaType,
            season = request.season,
            episode = request.episode,
            mediaTitle = request.title,
            mediaOriginalTitle = request.originalTitle,
            mediaAliases = request.aliases,
            mediaYear = request.releaseInfo,
            mediaExternalId = request.externalId ?: request.videoId,
            mediaOriginalLanguage = request.originalLanguage,
        )
        return SourceResolveResult(
            sources = result.streams,
            warnings =
                (
                    result.preflightErrors +
                        result.diagnostics
                            .mapNotNull { diagnostic ->
                                diagnostic.error?.let { error ->
                                    "${diagnostic.providerName}: $error"
                                }
                            }
                )
                    .distinct()
                    .take(8),
        )
    }
}
