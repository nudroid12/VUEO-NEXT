package com.vueo.shared.core.plugin

import android.content.Context
import android.util.Base64
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRequest
import com.vueo.shared.core.source.SourceResolveResult
import com.vueo.shared.core.source.SourceResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** Diagnostic emitted for every provider attempt. */
data class ProviderDiagnostic(
    val repositoryManifestUrl: String,
    val repositoryName: String,
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus,
    val responseMs: Long,
    val streamCount: Int,
    val error: String? = null,
    val logs: List<String> = emptyList(),
)

data class PluginDiscoveryProgress(
    val result: PluginDiscoveryResult,
    val completedProviders: Int,
    val totalProviders: Int,
)

data class PluginDiscoveryResult(
    val sources: List<SourceCandidate>,
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
)

/**
 * Shared QuickJS provider engine.
 *
 * This is deliberately UI-free. Mobile and TV consume exactly the same runtime,
 * provider store, provider code and health state.
 */
class PluginSourceEngine(
    context: Context,
    private val store: PluginStore,
) {
    private val appContext = context.applicationContext
    private val concurrency = Semaphore(MAX_PROVIDER_CONCURRENCY)
    private val healthStore = PluginHealthStore(appContext)
    private val codeStore = ProviderCodeStore(appContext)

    suspend fun discover(
        tmdbId: String,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
    ): PluginDiscoveryResult = discoverProgressive(
        tmdbId = tmdbId,
        mediaType = mediaType,
        season = season,
        episode = episode,
        onProgress = {},
    )

    suspend fun discoverProgressive(
        tmdbId: String,
        mediaType: String,
        season: Int? = null,
        episode: Int? = null,
        onProgress: suspend (PluginDiscoveryProgress) -> Unit,
    ): PluginDiscoveryResult = coroutineScope {
        if (!store.pluginsEnabled()) return@coroutineScope emptyDiscoveryResult()

        val knownHealth = healthStore.records().associateBy {
            it.repositoryManifestUrl to it.providerId
        }

        val targets = store.repositories()
            .filter(store::isRepositoryEnabled)
            .flatMap { repository ->
                repository.providers
                    .asSequence()
                    .filter { provider -> store.isProviderEnabled(repository, provider) }
                    .filter { provider ->
                        provider.supportedTypes.isEmpty() ||
                            mediaType in provider.supportedTypes ||
                            (mediaType == "series" && "tv" in provider.supportedTypes)
                    }
                    .filter { provider -> "android" !in provider.disabledPlatforms }
                    .map { provider -> repository to provider }
                    .toList()
            }
            .sortedWith(
                compareBy<Pair<PluginRepositoryDescriptor, PluginProviderDescriptor>> {
                    providerPriority(knownHealth[it.first.manifestUrl to it.second.id]?.status)
                }.thenBy {
                    knownHealth[it.first.manifestUrl to it.second.id]?.responseMs ?: Long.MAX_VALUE
                }
            )

        if (targets.isEmpty()) return@coroutineScope emptyDiscoveryResult()

        val runs = mutableListOf<ProviderRun>()
        val mutex = Mutex()

        targets.map { (repository, provider) ->
            async {
                val run = concurrency.withPermit {
                    runProvider(repository, provider, tmdbId, mediaType, season, episode)
                }
                saveHealth(run)

                val snapshot = mutex.withLock {
                    runs += run
                    PluginDiscoveryProgress(
                        result = buildDiscoveryResult(runs),
                        completedProviders = runs.size,
                        totalProviders = targets.size,
                    )
                }
                onProgress(snapshot)
            }
        }.awaitAll()

        mutex.withLock { buildDiscoveryResult(runs) }
    }

    private suspend fun runProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): ProviderRun {
        val started = System.nanoTime()
        val execution = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
            runCatching {
                executeProvider(repository, provider, tmdbId, mediaType, season, episode)
            }.getOrElse { error ->
                ProviderExecution(
                    sources = emptyList(),
                    error = error.message ?: error::class.java.simpleName,
                    logs = emptyList(),
                )
            }
        } ?: ProviderExecution(
            sources = emptyList(),
            error = "Timed out after ${PROVIDER_TIMEOUT_MS / 1000}s",
            logs = emptyList(),
        )

        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        val consoleError = execution.logs
            .lastOrNull { it.startsWith("ERROR: ") }
            ?.removePrefix("ERROR: ")
        val error = execution.error ?: consoleError

        val status = when {
            execution.sources.isNotEmpty() && elapsedMs >= SLOW_THRESHOLD_MS -> ProviderHealthStatus.SLOW
            execution.sources.isNotEmpty() -> ProviderHealthStatus.ONLINE
            error != null -> classifyProviderFailure(error)
            else -> ProviderHealthStatus.NO_RESULTS
        }
        val rankBoost = when (status) {
            ProviderHealthStatus.ONLINE -> if (elapsedMs < 1_000L) 30 else 22
            ProviderHealthStatus.SLOW -> 8
            else -> 0
        }

        return ProviderRun(
            sources = execution.sources.map { source ->
                source.copy(rankBoost = source.rankBoost + rankBoost)
            },
            diagnostic = ProviderDiagnostic(
                repositoryManifestUrl = repository.manifestUrl,
                repositoryName = repository.name,
                providerId = provider.id,
                providerName = provider.name,
                status = status,
                responseMs = elapsedMs,
                streamCount = execution.sources.size,
                error = error,
                logs = execution.logs.takeLast(MAX_STORED_LOGS),
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
    ): ProviderExecution {
        val providerScript = codeStore.read(repository, provider)
            ?: return ProviderExecution(
                sources = emptyList(),
                error = "Provider code is not installed locally. Refresh the repository first.",
                logs = emptyList(),
            )

        val logs = CopyOnWriteArrayList<String>()

        return try {
            val resultJson = quickJs {
                evaluationTimeoutMillis = PROVIDER_TIMEOUT_MS

                define("console") {
                    function("log") { args -> logs += logLine("LOG", args) }
                    function("info") { args -> logs += logLine("LOG", args) }
                    function("warn") { args -> logs += logLine("WARN", args) }
                    function("error") { args -> logs += logLine("ERROR", args) }
                }

                asyncFunction<String, String>("__vueoNativeFetch") { requestJson ->
                    PluginHttp.executeJson(requestJson)
                }
                function<String, String>("__vueoBase64") { value ->
                    Base64.encodeToString(
                        value.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    )
                }
                function<String, String>("__vueoBase64Decode") { value ->
                    String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
                }
                asyncFunction<Double, Boolean>("__vueoDelay") { millis ->
                    delay(millis.toLong().coerceIn(0L, 30_000L))
                    true
                }

                evaluate<String>(
                    buildRuntimeScript(
                        providerScript = providerScript,
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        season = season,
                        episode = episode,
                    ),
                    filename = "${provider.id}.js",
                )
            }

            ProviderExecution(
                sources = parseProviderSources(repository, provider, resultJson),
                error = null,
                logs = logs.toList(),
            )
        } catch (error: Throwable) {
            ProviderExecution(
                sources = emptyList(),
                error = error.message ?: error::class.java.simpleName,
                logs = logs.toList(),
            )
        }
    }

    private fun saveHealth(run: ProviderRun) {
        val diagnostic = run.diagnostic
        healthStore.save(
            ProviderHealthRecord(
                repositoryManifestUrl = diagnostic.repositoryManifestUrl,
                repositoryName = diagnostic.repositoryName,
                providerId = diagnostic.providerId,
                providerName = diagnostic.providerName,
                status = diagnostic.status,
                responseMs = diagnostic.responseMs,
                streamCount = diagnostic.streamCount,
                error = diagnostic.error,
                logs = diagnostic.logs.takeLast(MAX_STORED_LOGS),
                lastCheckedEpochMs = System.currentTimeMillis(),
            )
        )
    }

    private fun buildDiscoveryResult(runs: List<ProviderRun>): PluginDiscoveryResult {
        val diagnostics = runs.map { it.diagnostic }
        return PluginDiscoveryResult(
            sources = runs
                .flatMap { it.sources }
                .distinctBy { listOf(it.url, it.infoHash, it.fileIndex, it.providerId) },
            attemptedProviders = runs.size,
            successfulProviders = diagnostics.count { it.status == ProviderHealthStatus.ONLINE },
            slowProviders = diagnostics.count { it.status == ProviderHealthStatus.SLOW },
            noResultProviders = diagnostics.count { it.status == ProviderHealthStatus.NO_RESULTS },
            needsSetupProviders = diagnostics.count { it.status == ProviderHealthStatus.NEEDS_SETUP },
            unavailableProviders = diagnostics.count { it.status == ProviderHealthStatus.UNAVAILABLE },
            blockedProviders = diagnostics.count { it.status == ProviderHealthStatus.BLOCKED },
            timeoutProviders = diagnostics.count { it.status == ProviderHealthStatus.TIMEOUT },
            failedProviders = diagnostics.count { it.status == ProviderHealthStatus.FAILED },
            diagnostics = diagnostics,
        )
    }

    companion object {
        private const val MAX_PROVIDER_CONCURRENCY = 5
        private const val PROVIDER_TIMEOUT_MS = 18_000L
        private const val SLOW_THRESHOLD_MS = 7_000L
        private const val MAX_STORED_LOGS = 16
        private const val MAX_LOG_LENGTH = 700
    }
}

/** Adapter exposing JS providers through the common VUEO source contract. */
class PluginSourceResolver(
    context: Context,
    store: PluginStore,
) : SourceResolver {
    private val engine = PluginSourceEngine(context.applicationContext, store)

    override val id: String = "js-providers"
    override val name: String = "JavaScript Providers"

    override suspend fun resolve(request: SourceRequest): SourceResolveResult {
        val result = engine.discover(
            tmdbId = request.videoId,
            mediaType = request.mediaType,
            season = request.season,
            episode = request.episode,
        )
        return SourceResolveResult(
            sources = result.sources,
            warnings = result.diagnostics
                .mapNotNull { diagnostic ->
                    diagnostic.error?.let { "${diagnostic.providerName}: $it" }
                }
                .take(8),
        )
    }
}

private data class ProviderExecution(
    val sources: List<SourceCandidate>,
    val error: String?,
    val logs: List<String>,
)

private data class ProviderRun(
    val sources: List<SourceCandidate>,
    val diagnostic: ProviderDiagnostic,
)

private fun emptyDiscoveryResult() = PluginDiscoveryResult(
    sources = emptyList(),
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

private fun providerPriority(status: ProviderHealthStatus?): Int = when (status) {
    ProviderHealthStatus.ONLINE -> 0
    ProviderHealthStatus.SLOW -> 1
    ProviderHealthStatus.UNKNOWN, null -> 2
    ProviderHealthStatus.NO_RESULTS -> 3
    ProviderHealthStatus.TIMEOUT,
    ProviderHealthStatus.BLOCKED,
    ProviderHealthStatus.FAILED -> 4
    ProviderHealthStatus.NEEDS_SETUP,
    ProviderHealthStatus.UNAVAILABLE -> 5
}

private fun classifyProviderFailure(message: String): ProviderHealthStatus {
    val normalized = message.lowercase()
    return when {
        "timed out" in normalized || "timeout" in normalized -> ProviderHealthStatus.TIMEOUT
        "not installed" in normalized || "not available" in normalized -> ProviderHealthStatus.UNAVAILABLE
        "token required" in normalized ||
            "missing token" in normalized ||
            "api key" in normalized && ("required" in normalized || "missing" in normalized) ->
            ProviderHealthStatus.NEEDS_SETUP
        "http 403" in normalized ||
            "status 403" in normalized ||
            "forbidden" in normalized ||
            "cloudflare" in normalized ||
            "captcha" in normalized -> ProviderHealthStatus.BLOCKED
        else -> ProviderHealthStatus.FAILED
    }
}

private fun logLine(level: String, args: List<Any?>): String =
    "$level: ${args.joinToString(" ") { it?.toString().orEmpty() }}".take(700)

private fun parseProviderSources(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    resultJson: String,
): List<SourceCandidate> {
    val array = runCatching { JSONArray(resultJson) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val url = item.optString("url").takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        } ?: return@mapNotNull null
        val displayName = item.optString("title").takeIf(String::isNotBlank)
            ?: item.optString("name").takeIf(String::isNotBlank)
            ?: provider.name
        val sizeBytes = when {
            item.has("sizeBytes") -> item.optLong("sizeBytes").takeIf { it > 0L }
            item.has("size") && item.opt("size") is Number -> item.optLong("size").takeIf { it > 0L }
            else -> null
        }
        SourceCandidate(
            id = "plugin:${repository.name}:${provider.id}:$index:${url.hashCode()}",
            name = displayName,
            url = url,
            quality = item.optString("quality").takeIf(String::isNotBlank),
            codec = item.optString("codec").takeIf(String::isNotBlank),
            hdr = item.optString("hdr").takeIf(String::isNotBlank),
            audio = item.optString("audio").takeIf(String::isNotBlank),
            language = item.optString("language").takeIf(String::isNotBlank),
            sizeBytes = sizeBytes,
            headers = item.optJSONObject("headers").toStringMap(),
            rankBoost = item.optInt("rankBoost", 0),
            providerId = provider.id,
            providerName = provider.name,
        )
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        optString(key).takeIf(String::isNotBlank)?.let { result[key] = it }
    }
    return result
}

private fun buildRuntimeScript(
    providerScript: String,
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
): String {
    val safeTmdbId = JSONObject.quote(tmdbId)
    val safeMediaType = JSONObject.quote(mediaType)
    val seasonValue = season?.toString() ?: "null"
    val episodeValue = episode?.toString() ?: "null"

    return """
        globalThis.window = globalThis;
        globalThis.global = globalThis;
        globalThis.self = globalThis;
        globalThis.process = globalThis.process || { env: {} };
        globalThis.SCRAPER_SETTINGS = globalThis.SCRAPER_SETTINGS || {};

        function __vueoHeaders(raw) {
          var out = {};
          if (raw && typeof raw === "object") {
            Object.keys(raw).forEach(function (key) {
              out[String(key)] = String(raw[key]);
            });
          }
          return out;
        }

        globalThis.fetch = async function (input, init) {
          init = init || {};
          var headers = __vueoHeaders(init.headers);
          var request = {
            url: String(input && input.url ? input.url : input),
            method: String(init.method || "GET").toUpperCase(),
            headers: headers,
            body: init.body == null ? null : String(init.body),
            contentType: headers["Content-Type"] || headers["content-type"] || null,
            redirect: init.redirect == null ? "follow" : String(init.redirect)
          };
          var raw = await __vueoNativeFetch(JSON.stringify(request));
          var response = JSON.parse(raw);
          if (response.error) throw new Error(response.error);
          var responseHeaders = response.headers || {};
          var bodyText = response.body || "";
          return {
            ok: response.status >= 200 && response.status < 300,
            status: response.status || 0,
            statusText: response.statusText || "",
            url: response.url || request.url,
            headers: {
              get: function (name) {
                var lower = String(name || "").toLowerCase();
                var keys = Object.keys(responseHeaders);
                for (var i = 0; i < keys.length; i++) {
                  if (keys[i].toLowerCase() === lower) return String(responseHeaders[keys[i]]);
                }
                return null;
              },
              has: function (name) { return this.get(name) !== null; }
            },
            text: async function () { return bodyText; },
            json: async function () { return JSON.parse(bodyText || "null"); },
            clone: function () { return this; }
          };
        };

        function __vueoAxiosRequest(config) {
          config = config || {};
          return fetch(config.url, {
            method: config.method || "GET",
            headers: config.headers || {},
            body: config.data == null
              ? null
              : (typeof config.data === "string" ? config.data : JSON.stringify(config.data))
          }).then(async function (response) {
            var text = await response.text();
            var data = text;
            try { data = JSON.parse(text); } catch (_) {}
            if (!response.ok) {
              var error = new Error("Request failed with status " + response.status);
              error.response = { data: data, status: response.status };
              throw error;
            }
            return { data: data, status: response.status, headers: response.headers, config: config };
          });
        }

        var __vueoAxios = function (config) { return __vueoAxiosRequest(config); };
        __vueoAxios.request = __vueoAxiosRequest;
        __vueoAxios.get = function (url, config) {
          config = config || {}; config.url = url; config.method = "GET";
          return __vueoAxiosRequest(config);
        };
        __vueoAxios.post = function (url, data, config) {
          config = config || {}; config.url = url; config.method = "POST"; config.data = data;
          return __vueoAxiosRequest(config);
        };
        globalThis.axios = __vueoAxios;

        globalThis.btoa = function (value) { return __vueoBase64(String(value)); };
        globalThis.atob = function (value) { return __vueoBase64Decode(String(value)); };
        globalThis.Buffer = globalThis.Buffer || {
          from: function (value) {
            var text = String(value);
            return {
              toString: function (encoding) {
                return encoding === "base64" ? __vueoBase64(text) : text;
              }
            };
          }
        };
        globalThis.setTimeout = function (callback, millis) {
          return __vueoDelay(Number(millis || 0)).then(function () { return callback(); });
        };
        globalThis.clearTimeout = function () {};

        var module = { exports: {} };
        var exports = module.exports;
        globalThis.require = function (name) {
          if (name === "axios") return __vueoAxios;
          throw new Error("Unsupported provider module: " + name);
        };

        $providerScript

        (async function () {
          var exported = module && module.exports ? module.exports : {};
          var getStreams = exported.getStreams || globalThis.getStreams;
          if (typeof getStreams !== "function") {
            throw new Error("Provider does not export getStreams().");
          }
          var value = await getStreams($safeTmdbId, $safeMediaType, $seasonValue, $episodeValue);
          if (value && Array.isArray(value.streams)) value = value.streams;
          if (!Array.isArray(value)) value = [];
          return JSON.stringify(value);
        })()
    """.trimIndent()
}
