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
import com.vueo.shared.core.storage.ContinueWatchingPolicy
import com.vueo.tv.MainActivity
import com.vueo.tv.R
import com.vueo.tv.watchnext.TvWatchNextDeepLink
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

/** Publishes the active profile's Continue Watching row in the TV launcher. */
object TvHomeChannels {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private const val PREFIX = "vueo.home.v1."
    private const val CONTINUE_WATCHING_ID = PREFIX + "continue_watching"

    fun accepts(uri: Uri?): Boolean = uri?.scheme == "vueo" && uri.host == "channel"
    private fun key(item: MediaItem) = "${item.type}:${item.id}"
    private fun prefs(context: Context) = context.getSharedPreferences("vueo_settings", Context.MODE_PRIVATE)
    fun schedule(context: Context, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch { try { guarded { synchronized(lock) { syncLauncherChannel(app) } } } finally { onComplete() } }
    }
    private fun guarded(block: () -> Unit) { try { block() } catch (e: Exception) { Log.w("VueoHomeChannels", "Continue Watching channel sync failed", e) } }
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

    private fun syncLauncherChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val profiles = ProfileStore(context).apply { ensureDefaultProfile() }
        val profileId = profiles.activeProfileId()
        val entries = LibraryStore(context, profileStore = profiles).continueWatching().take(24)
        if (profiles.activeProfileId() != profileId) return

        val helper = PreviewChannelHelper(context)
        val channels = helper.allChannels
        val existingChannel = channels.firstOrNull { it.internalProviderId == CONTINUE_WATCHING_ID }
        val channelId = existingChannel?.id ?: helper.publishChannel(
            PreviewChannel.Builder()
                .setDisplayName("Continue Watching")
                .setDescription("Resume playback in VUEO")
                .setInternalProviderId(CONTINUE_WATCHING_ID)
                .setAppLinkIntent(Intent(context, MainActivity::class.java))
                .setLogo(requireNotNull(ContextCompat.getDrawable(context, R.drawable.vueo_tv_logo)).toBitmap(256, 256))
                .build(),
        )
        if (channelId < 0L) return
        if (existingChannel == null) TvContractCompat.requestChannelBrowsable(context, channelId)

        val programsUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
        val rows = mutableMapOf<String, MutableList<Long>>()
        val cursor = context.contentResolver.query(
            programsUri, arrayOf("_id", "internal_provider_id"), null, null, null,
        ) ?: return
        cursor.use {
            while (it.moveToNext()) {
                rows.getOrPut(it.getString(1).orEmpty()) { mutableListOf() }.add(it.getLong(0))
            }
        }

        for ((position, entry) in entries.distinctBy { it.mediaKey }.withIndex()) {
            val id = "$CONTINUE_WATCHING_ID:$profileId:${entry.mediaKey}"
            val media = entry.media
            val backdrop = media.background?.takeIf { it.isNotBlank() }
            val artwork = backdrop ?: media.poster?.takeIf { it.isNotBlank() }
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setInternalProviderId(id)
                .setContentId(entry.mediaKey)
                .setTitle(media.name)
                .setDescription(entry.episodeTitle?.takeIf { it.isNotBlank() } ?: media.description.orEmpty())
                .setType(if (ContinueWatchingPolicy.isSeries(media)) {
                    TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                } else {
                    TvContractCompat.PreviewPrograms.TYPE_MOVIE
                })
                .setWeight(1000 - position)
                .setIntent(Intent(Intent.ACTION_VIEW, TvWatchNextDeepLink.build(entry.mediaKey), context, MainActivity::class.java))
                .apply {
                    if (artwork != null) {
                        setPosterArtUri(Uri.parse(artwork))
                        setPosterArtAspectRatio(if (backdrop != null) {
                            TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
                        } else {
                            TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
                        })
                    }
                    if (entry.positionMs > 0 && entry.durationMs > 0) {
                        setLastPlaybackPositionMillis(entry.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        setDurationMillis(entry.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                }
                .build()

            val previous = rows.remove(id).orEmpty()
            if (previous.isEmpty()) {
                if (helper.publishPreviewProgram(program) < 0L) return
            } else {
                helper.updatePreviewProgram(previous.first(), program)
                previous.drop(1).forEach(helper::deletePreviewProgram)
            }
        }
        rows.values.flatten().forEach(helper::deletePreviewProgram)

        for (channel in channels) {
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
