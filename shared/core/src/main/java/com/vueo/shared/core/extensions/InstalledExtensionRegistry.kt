package com.vueo.shared.core.extensions

/** Publish one immutable snapshot: replacement and its enabled state become visible together. */
internal class InstalledExtensionRegistry {
    private data class State(val entries: List<MediaExtension>, val disabled: Set<String>)
    @Volatile private var state = State(emptyList(), emptySet())

    fun installed(): List<MediaExtension> = state.entries.toList()

    private fun find(snapshot: State, id: String): MediaExtension? =
        snapshot.entries.firstOrNull { it.descriptor.installationId == id }
            ?: snapshot.entries.filter { it.descriptor.id == id }.singleOrNull()

    fun extension(id: String?): MediaExtension? = id?.let { find(state, it) }

    fun isEnabled(id: String): Boolean {
        val snapshot = state
        val entry = find(snapshot, id) ?: return false
        return entry.descriptor.installationId !in snapshot.disabled
    }

    @Synchronized
    fun install(extension: MediaExtension, enabledState: (() -> Boolean)? = null) {
        val snapshot = state
        val key = extension.descriptor.installationId
        val index = snapshot.entries.indexOfFirst { it.descriptor.installationId == key }
        val entries = if (index < 0) snapshot.entries + extension else snapshot.entries.toMutableList().apply {
            set(index, extension)
        }.toList()
        val enabled = enabledState?.invoke()
        val disabled = when (enabled) {
            true -> snapshot.disabled - key
            false -> snapshot.disabled + key
            null -> snapshot.disabled
        }
        state = State(entries, disabled)
    }

    @Synchronized
    fun uninstall(id: String): Boolean {
        val snapshot = state
        val entry = find(snapshot, id) ?: return false
        val key = entry.descriptor.installationId
        state = State(snapshot.entries.filterNot { it.descriptor.installationId == key }, snapshot.disabled - key)
        return true
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val snapshot = state
        val entry = find(snapshot, id) ?: return false
        val key = entry.descriptor.installationId
        val disabled = if (enabled) snapshot.disabled - key else snapshot.disabled + key
        if (disabled == snapshot.disabled) return false
        state = State(snapshot.entries, disabled)
        return true
    }
}
