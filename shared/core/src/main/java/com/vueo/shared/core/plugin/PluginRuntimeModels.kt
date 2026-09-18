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

