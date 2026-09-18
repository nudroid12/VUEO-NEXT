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
