package com.vueo.mobile.core.extensions

typealias MediaExtension = com.vueo.shared.core.extensions.MediaExtension
class UnifiedMediaEngine {
    private val registry = com.vueo.shared.core.extensions.InstalledExtensionRegistry()
    fun stremioAddons() = registry.installed()
    fun install(extension: MediaExtension, enabledState: (() -> Boolean)? = null) = registry.install(extension, enabledState)
    fun uninstall(id: String) { registry.uninstall(id) }
    fun setExtensionEnabled(id: String, enabled: Boolean) { registry.setEnabled(id, enabled) }
    fun isExtensionEnabled(id: String) = registry.isEnabled(id)
}
object ExtensionInstaller {
    suspend fun installStremioAddon(url: String): MediaExtension = error("Inject the manifest loader")
}
