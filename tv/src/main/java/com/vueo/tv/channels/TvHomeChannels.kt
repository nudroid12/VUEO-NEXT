package com.vueo.tv.channels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaTypePolicy
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.dna.UserDnaPreferences
import com.vueo.tv.MainActivity
import com.vueo.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

/** Two stable app channels; profile switches replace programs, never channel IDs or visibility. */
object TvHomeChannels {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private const val PREFIX = "vueo.home.v1."
    fun accepts(uri: Uri?): Boolean = uri?.scheme == "vueo" && uri.host == "channel"
    private fun key(item: MediaItem) = "${item.type}:${item.id}"
    private fun prefs(context: Context) = context.getSharedPreferences("vueo_settings", Context.MODE_PRIVATE)
    private fun uri(profile: String, kind: String, media: MediaItem? = null): Uri = Uri.Builder()
        .scheme("vueo").authority("channel").appendQueryParameter("profile", profile)
        .appendQueryParameter("kind", kind).apply { media?.let { appendQueryParameter("media", key(it)) } }.build()

    /** Called with the already-visible, profile-filtered For You feed; never loads recommendations. */
    fun updateForYou(context: Context, profile: String, items: List<MediaItem>) {
        val app = context.applicationContext
        scope.launch { guarded {
            synchronized(lock) {
                if (ProfileStore(app).activeProfileId() != profile) return@synchronized
                prefs(app).edit().putLong(ProfileStore.scopedPreferenceKey(profile, "launcher_for_you_at_v1"), System.currentTimeMillis()).putString(ProfileStore.scopedPreferenceKey(profile, "launcher_for_you_v1"), items.take(24).map { it.copy(episodes = emptyList()) }.toChannelJson().toString()).apply()
                syncLocked(app)
            }
        } }
    }
    fun schedule(context: Context, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch { try { guarded { synchronized(lock) { syncLocked(app) } } } finally { onComplete() } }
    }
    private fun guarded(block: () -> Unit) { try { block() } catch (e: Exception) { Log.w("VueoHomeChannels", "Channel sync failed; retry on next app update", e) } }
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

    private fun syncLocked(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val profiles = ProfileStore(context).apply { ensureDefaultProfile() }
        val profile = profiles.activeProfileId()
        val library = LibraryStore(context)
        val watchlist = library.watchlist().take(24)
        val personalized = UserDnaPreferences(context).shouldPersonalizeRecommendations(profile)
        val limit = profiles.kidsAgeLimit()
        val watched = library.history().filter(com.vueo.shared.core.storage.LibraryViewingPolicy::isMeaningfullyWatched).map { key(it.media) }.toSet() + library.traktMarkedWatchedMedia().map(::key)
        val forYou = if (personalized) cached(context, profile).filter { item ->
            key(item) !in watched && (limit == null || com.vueo.shared.core.profile.KidsContentPolicy.allows(item.certification, limit))
        }.take(24) else emptyList()
        val helper = PreviewChannelHelper(context)
        val existing = helper.allChannels.associateBy { it.internalProviderId }
        listOf("watchlist" to watchlist, "for_you" to forYou).forEachIndexed { index, (kind, items) ->
            val internalId = PREFIX + kind
            val known = existing[internalId]
            val channelId = known?.id ?: run {
                val logo = requireNotNull(ContextCompat.getDrawable(context, R.drawable.vueo_tv_logo)).toBitmap(256, 256)
                val channel = PreviewChannel.Builder().setDisplayName(if (kind == "watchlist") "Watchlist" else "For You")
                    .setDescription("VUEO ${if (kind == "watchlist") "saved titles" else "personal recommendations"}")
                    .setInternalProviderId(internalId)
                    .setAppLinkIntent(Intent(Intent.ACTION_VIEW, Uri.parse("vueo://channel?kind=$kind"), context, MainActivity::class.java))
                    .setLogo(logo).build()
                helper.publishChannel(channel).also { id ->
                    // Only creation requests default visibility; never re-enable a hidden channel.
                    if (id >= 0 && index == 0) TvContractCompat.requestChannelBrowsable(context, id)
                }
            }
            if (channelId < 0) return@forEachIndexed
            val rows = mutableMapOf<String, Long>()
            context.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                arrayOf("_id", "internal_provider_id"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) rows[cursor.getString(1).orEmpty()] = cursor.getLong(0)
            }
            items.distinctBy(::key).forEachIndexed { position, media ->
                val id = "$PREFIX$profile:$kind:${key(media)}"
                val program = PreviewProgram.Builder().setChannelId(channelId).setInternalProviderId(id)
                    .setTitle(media.name).setDescription(media.description.orEmpty())
                    .setType(if (MediaTypePolicy.isSeries(media.type)) TvContractCompat.PreviewPrograms.TYPE_TV_SERIES else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                    .setWeight(1000 - position)
                    .setIntent(Intent(Intent.ACTION_VIEW, uri(profile, kind, media), context, MainActivity::class.java))
                    .apply {
                        media.poster?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let {
                            setPosterArtUri(Uri.parse(it))
                            setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                        }
                    }.build()
                val previous = rows.remove(id)
                if (previous == null) helper.publishPreviewProgram(program) else helper.updatePreviewProgram(previous, program)
            }
            // Includes stale entries belonging to the previously active profile.
            rows.values.forEach(helper::deletePreviewProgram)
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

/** Channel snapshots only need presentation and navigation metadata, never stream data. */
private fun List<MediaItem>.toChannelJson(): JSONArray = JSONArray().also { array ->
    take(24).forEach { media ->
        array.put(JSONObject().put("id", media.id).put("type", media.type).put("name", media.name)
            .put("poster", media.poster).put("background", media.background)
            .put("description", media.description).put("releaseInfo", media.releaseInfo)
            .put("originalTitle", media.originalTitle).put("originalLanguage", media.originalLanguage)
            .put("certification", media.certification).put("sourceExtensionId", media.sourceExtensionId)
            .put("tmdbId", media.tmdbId).put("genres", JSONArray(media.genres))
            .put("catalogSources", JSONArray(media.catalogSources)))
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
