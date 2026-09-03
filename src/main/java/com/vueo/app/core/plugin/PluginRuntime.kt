package com.vueo.app.core.plugin

import android.content.Context
import android.util.Base64
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import com.vueo.app.core.model.StreamSource
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
    val streams: List<StreamSource>,
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

class PluginSourceEngine(
    context: Context,
    private val store: PluginStore,
) {
    private val concurrency = Semaphore(5)

    private val healthStore =
        PluginHealthStore(
            context.applicationContext
        )

    private val codeStore =
        ProviderCodeStore(
            context.applicationContext
        )


suspend fun discover(
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
): PluginDiscoveryResult =
    discoverProgressive(
        tmdbId = tmdbId,
        mediaType = mediaType,
        season = season,
        episode = episode,
        onProgress = {},
    )

suspend fun discoverProgressive(
    tmdbId: String,
    mediaType: String,
    season: Int?,
    episode: Int?,
    onProgress: suspend (PluginDiscoveryProgress) -> Unit,
): PluginDiscoveryResult =
    coroutineScope {

        if (!store.pluginsEnabled()) {
            return@coroutineScope emptyDiscoveryResult()
        }

        val knownHealth =
            healthStore.records()
                .associateBy {
                    it.repositoryManifestUrl to
                        it.providerId
                }

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
                                mediaType in
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
                    > {
                        val record =
                            knownHealth[
                                it.first.manifestUrl to
                                    it.second.id
                            ]

                        providerPriority(
                            record?.status
                        )
                    }.thenBy {
                        knownHealth[
                            it.first.manifestUrl to
                                it.second.id
                        ]?.responseMs
                            ?: Long.MAX_VALUE
                    }
                )

        if (targets.isEmpty()) {
            return@coroutineScope emptyDiscoveryResult()
        }

        val runs =
            mutableListOf<ProviderRun>()

        val mutex = Mutex()

        targets.map {
            (repository, provider) ->

            async {
                val run =
                    concurrency.withPermit {
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
                        )
                    }

                saveHealth(run)

                val snapshot =
                    mutex.withLock {
                        runs += run

                        PluginDiscoveryProgress(
                            result =
                                buildDiscoveryResult(
                                    runs
                                ),
                            completedProviders =
                                runs.size,
                            totalProviders =
                                targets.size,
                        )
                    }

                onProgress(snapshot)
            }
        }.awaitAll()

        mutex.withLock {
            buildDiscoveryResult(
                runs
            )
        }
    }

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

private fun providerPriority(
    status: ProviderHealthStatus?,
): Int =
    when (status) {
        ProviderHealthStatus.ONLINE -> 0
        ProviderHealthStatus.SLOW -> 1
        ProviderHealthStatus.UNKNOWN,
        null -> 2
        ProviderHealthStatus.NO_RESULTS -> 3
        ProviderHealthStatus.TIMEOUT,
        ProviderHealthStatus.BLOCKED,
        ProviderHealthStatus.FAILED -> 4
        ProviderHealthStatus.NEEDS_SETUP,
        ProviderHealthStatus.UNAVAILABLE -> 5
    }

    private suspend fun runProvider(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
    ): ProviderRun {
        val started =
            System.nanoTime()

        val execution =
            withTimeoutOrNull(
                PROVIDER_TIMEOUT_MS
            ) {
                runCatching {
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
                    )
                }.getOrElse { error ->
                    ProviderExecution(
                        streams =
                            emptyList(),
                        error =
                            error.message
                                ?: error::class
                                    .java
                                    .simpleName,
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
                        "${PROVIDER_TIMEOUT_MS / 1000}s",
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
                    logs =
                        emptyList(),
                )

        val logs =
            CopyOnWriteArrayList<String>()

        return try {
            val resultJson =
                quickJs {
                    evaluationTimeoutMillis =
                        PROVIDER_TIMEOUT_MS

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
                        "__vueoNativeFetch"
                    ) { requestJson ->
                        PluginHttp.executeJson(
                            requestJson
                        )
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
                    ),
                error =
                    null,
                logs =
                    logs.toList(),
            )
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
                logs =
                    logs.toList(),
            )
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

            function __vueoString(value) {
              try {
                if (typeof value === "string") return value;
                return JSON.stringify(value);
              } catch (_) {
                return String(value);
              }
            }

            function __vueoHeaders(raw) {
              var normalized = {};
              if (raw && typeof raw === "object") {
                Object.keys(raw).forEach(function (key) {
                  normalized[String(key)] = String(raw[key]);
                });
              }
              return normalized;
            }

            globalThis.fetch = async function (input, init) {
              init = init || {};

              var request = {
                url: String(input && input.url ? input.url : input),
                method: String(init.method || "GET").toUpperCase(),
                headers: __vueoHeaders(init.headers),
                body: init.body == null ? null : String(init.body),
                contentType:
                  init.headers &&
                  (init.headers["Content-Type"] || init.headers["content-type"])
                    ? String(
                        init.headers["Content-Type"] ||
                        init.headers["content-type"]
                      )
                    : null,
                redirect:
                  init.redirect == null
                    ? "follow"
                    : String(init.redirect)
              };

              var raw = await __vueoNativeFetch(
                JSON.stringify(request)
              );

              var response = JSON.parse(raw);

              if (response.error) {
                throw new Error(response.error);
              }

              var responseHeaders = response.headers || {};
              var bodyText = response.body || "";

              return {
                ok:
                  response.status >= 200 &&
                  response.status < 300,
                status: response.status || 0,
                statusText: response.statusText || "",
                url: response.url || request.url,
                headers: {
                  get: function (name) {
                    if (!name) return null;
                    var lower = String(name).toLowerCase();
                    var keys = Object.keys(responseHeaders);
                    for (var i = 0; i < keys.length; i++) {
                      if (keys[i].toLowerCase() === lower) {
                        return String(responseHeaders[keys[i]]);
                      }
                    }
                    return null;
                  },
                  has: function (name) {
                    return this.get(name) !== null;
                  }
                },
                text: async function () {
                  return bodyText;
                },
                json: async function () {
                  return JSON.parse(bodyText || "null");
                },
                clone: function () {
                  return this;
                }
              };
            };

            function __vueoAxiosRequest(config) {
              config = config || {};

              return fetch(config.url, {
                method: config.method || "GET",
                headers: config.headers || {},
                body:
                  config.data == null
                    ? null
                    : (
                        typeof config.data === "string"
                          ? config.data
                          : JSON.stringify(config.data)
                      )
              }).then(async function (response) {
                var text = await response.text();
                var data = text;

                try {
                  data = JSON.parse(text);
                } catch (_) {}

                if (!response.ok) {
                  var error =
                    new Error(
                      "Request failed with status " +
                      response.status
                    );

                  error.response = {
                    data: data,
                    status: response.status,
                    statusText: response.statusText,
                    headers: response.headers,
                    config: config
                  };

                  throw error;
                }

                return {
                  data: data,
                  status: response.status,
                  statusText: response.statusText,
                  headers: response.headers,
                  config: config
                };
              });
            }

            var __vueoAxiosModule = function (config) {
              return __vueoAxiosRequest(config);
            };

            __vueoAxiosModule.request = __vueoAxiosRequest;

            __vueoAxiosModule.get = function (url, config) {
              config = config || {};
              config.url = url;
              config.method = "GET";
              return __vueoAxiosRequest(config);
            };

            __vueoAxiosModule.post = function (url, data, config) {
              config = config || {};
              config.url = url;
              config.method = "POST";
              config.data = data;
              return __vueoAxiosRequest(config);
            };

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

            globalThis.Buffer = globalThis.Buffer || {
              from: function (value) {
                var text = String(value);

                return {
                  toString: function (encoding) {
                    if (encoding === "base64") {
                      return __vueoBase64(text);
                    }
                    return text;
                  }
                };
              }
            };

            globalThis.setTimeout = function (callback, millis) {
              return __vueoDelay(Number(millis || 0))
                .then(function () {
                  return callback();
                });
            };

            globalThis.clearTimeout = function () {};

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

                        globalThis.AbortSignal =
                          globalThis.AbortSignal ||
                          {
                            timeout: function (millis) {
                              return {
                                __vueoTimeoutMs:
                                  Number(millis || 0),
                                aborted: false
                              };
                            }
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
              Array.isArray(__vueoStreams)
                ? __vueoStreams
                : []
            );
        """.trimIndent()
    }

    private data class ProviderExecution(
        val streams: List<StreamSource>,
        val error: String?,
        val logs: List<String>,
    )

    private data class ProviderRun(
        val streams: List<StreamSource>,
        val diagnostic: ProviderDiagnostic,
    )

    companion object {
        private const val PROVIDER_TIMEOUT_MS =
            10_000L

        private const val SLOW_THRESHOLD_MS =
            3_000L

        private const val MAX_STORED_LOGS =
            6

        private const val MAX_LOG_LENGTH =
            500
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
): List<StreamSource> {
    val array =
        runCatching {
            JSONArray(resultJson)
        }.getOrNull()
            ?: return emptyList()

    return (0 until array.length())
        .mapNotNull { index ->
            val item =
                array.optJSONObject(index)
                    ?: return@mapNotNull null

            val url =
                item.optString("url")
                    .takeIf {
                        it.startsWith(
                            "https://"
                        ) ||
                        it.startsWith(
                            "http://"
                        )
                    }
                    ?: return@mapNotNull null

            val headers =
                item.optJSONObject("headers")
                    .toStringMap()

            val quality =
                item.optString("quality")
                    .takeIf {
                        it.isNotBlank()
                    }

            val displayName =
                item.optString("title")
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: item.optString("name")
                        .takeIf {
                            it.isNotBlank()
                        }
                    ?: provider.name

            StreamSource(
                name =
                    displayName,
                url =
                    url,
                quality =
                    quality,
                codec =
                    item.optString("codec")
                        .takeIf { it.isNotBlank() },
                hdr =
                    item.optString("hdr")
                        .takeIf { it.isNotBlank() },
                audio =
                    item.optString("audio")
                        .takeIf { it.isNotBlank() },
                language =
                    listOf(
                        "language",
                        "lang",
                        "audioLanguage",
                        "audio_language",
                    ).firstNotNullOfOrNull { field ->
                        item.optString(field)
                            .trim()
                            .takeIf { it.isNotBlank() }
                    },
                headers =
                    headers,
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
