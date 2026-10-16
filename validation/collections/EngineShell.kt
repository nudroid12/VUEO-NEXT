package com.vueo.shared.core.extensions
import com.vueo.shared.core.media.*
class StremioAddonExtension(override val descriptor: ExtensionDescriptor, val body: suspend (String, String, Map<String,String>) -> CatalogPage) : MediaExtension {
    override suspend fun catalog(type: String, catalogId: String, extras: Map<String,String>) = body(type, catalogId, extras)
}
class UnifiedMediaEngine(val addons: List<StremioAddonExtension>, val off: MutableSet<String> = mutableSetOf(), val allowed: (MediaItem) -> Boolean = { true }) {
    fun stremioAddons() = addons
    fun isExtensionEnabled(id: String) = id !in off
    fun filterVisible(items: List<MediaItem>) = items.filter(allowed)
}
