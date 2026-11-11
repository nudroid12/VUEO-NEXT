package com.vueo.tv.channels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannelHelper
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

/** Removes the app channels published by older VUEO builds. */
object TvHomeChannels {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private const val PREFIX = "vueo.home.v1."
    fun accepts(uri: Uri?): Boolean = uri?.scheme == "vueo" && uri.host == "channel"
    private fun key(item: MediaItem) = "${item.type}:${item.id}"
    private fun prefs(context: Context) = context.getSharedPreferences("vueo_settings", Context.MODE_PRIVATE)
    fun schedule(context: Context, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch { try { guarded { synchronized(lock) { removeLegacyChannels(app) } } } finally { onComplete() } }
    }
    private fun guarded(block: () -> Unit) { try { block() } catch (e: Exception) { Log.w("VueoHomeChannels", "Legacy channel removal failed; retry on next launch", e) } }
    private fun cached(context: Context, profile: String): List<MediaItem> = runCatching {
        val age = System.currentTimeMillis() - prefs(context).getLong(ProfileStore.scopedPreferenceKey(profile, "launcher_for_you_at_v1"), 0L)
        if (age !in 0L..86_400_000L) return@runCatching emptyList()
        JSONArray(prefs(context).getString(ProfileStore.scopedPreferenceKey(profile, "launcher_for_you_v1"), "[]")).toChannelMediaItems()
    }.getOrDefault(emptyList())

    fun resolve(context: Context, link: String): MediaItem? = synchronized(lock) {
        val uri = Uri.parse(link)
        if (!accepts(uri)) return@synchronized null
        val profile = ProfileStore(context).activeProfileId()
        if (uri.getQueryParameter("profile") != profile) return@synchronized null
        val kind = uri.getQueryParameter("kind")
        val items = when (kind) { "watchlist" -> LibraryStore(context).watchlist(); "for_you" -> cached(context, profile); else -> emptyList() }
        items.firstOrNull { key(it) == uri.getQueryParameter("media") }
    }

    private fun removeLegacyChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val helper = PreviewChannelHelper(context)
        for (channel in helper.allChannels) {
            when (channel.internalProviderId) {
                PREFIX + "watchlist", PREFIX + "for_you" -> helper.deletePreviewChannel(channel.id)
            }
        }
    }
}

class TvHomeChannelsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.media.tv.action.INITIALIZE_PROGRAMS") return
        val pending = goAsync()
        TvHomeChannels.schedule(context) { pending.finish() }
    }
}

private fun JSONArray.toChannelMediaItems(): List<MediaItem> = (0 until minOf(length(), 24)).mapNotNull { index ->
    val row = optJSONObject(index) ?: return@mapNotNull null
    val id = row.channelString("id") ?: return@mapNotNull null
    val type = row.channelString("type") ?: return@mapNotNull null
    val name = row.channelString("name") ?: return@mapNotNull null
    MediaItem(id = id, type = type, name = name, poster = row.channelString("poster"),
        background = row.channelString("background"), description = row.channelString("description"),
        releaseInfo = row.channelString("releaseInfo"), originalTitle = row.channelString("originalTitle"),
        originalLanguage = row.channelString("originalLanguage"), certification = row.channelString("certification"),
        sourceExtensionId = row.channelString("sourceExtensionId"), tmdbId = row.channelString("tmdbId"),
        genres = row.channelStrings("genres"), catalogSources = row.channelStrings("catalogSources"))
}
private fun JSONObject.channelString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.channelStrings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until minOf(array.length(), 100)).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}
