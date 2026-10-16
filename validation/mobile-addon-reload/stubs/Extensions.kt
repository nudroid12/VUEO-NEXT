package com.vueo.mobile.core.extensions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

data class Descriptor(val id: String, val baseUrl: String)
data class MediaExtension(val descriptor: Descriptor)
class UnifiedMediaEngine {
    private val extensions = CopyOnWriteArrayList<MediaExtension>()
    private val disabled = ConcurrentHashMap.newKeySet<String>()
    fun install(extension: MediaExtension) { extensions.removeAll { it.descriptor.id == extension.descriptor.id }; extensions += extension }
    fun uninstall(id: String) { extensions.removeAll { it.descriptor.id == id }; disabled.remove(id) }
    fun stremioAddons(): List<MediaExtension> = extensions.toList()
    fun setExtensionEnabled(id: String, enabled: Boolean) { if(enabled) disabled.remove(id) else disabled.add(id) }
    fun isExtensionEnabled(id: String) = id !in disabled
}
object ExtensionInstaller {
    suspend fun installStremioAddon(url: String): MediaExtension = error("Unexpected real loader: $url")
}
