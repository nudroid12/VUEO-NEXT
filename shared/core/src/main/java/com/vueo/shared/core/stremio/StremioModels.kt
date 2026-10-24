package com.vueo.shared.core.stremio

/**
 * Minimal Stremio manifest vocabulary needed by the shared source runtime.
 * UI/catalog metadata stays outside this migration slice.
 */
data class StremioManifest(
    val id: String,
    val name: String,
    val version: String,
    val manifestUrl: String,
    val baseUrl: String,
    val description: String? = null,
    val resources: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val idPrefixes: Set<String> = emptySet(),
    val resourceDescriptors: List<StremioResource> = emptyList(),
) {
    fun supportsRequest(resource: String, type: String, id: String): Boolean {
        if (resourceDescriptors.isNotEmpty()) return resourceDescriptors.any {
            it.name.equals(resource, true) && it.matches(type, id)
        }
        return supportsResource(resource) && supportsType(type) &&
            (idPrefixes.isEmpty() || idPrefixes.any(id::startsWith))
    }

    fun supportsResource(resource: String): Boolean =
        resources.isEmpty() || resource in resources

    fun supportsType(type: String): Boolean =
        types.isEmpty() || types.any { StremioUrls.canonicalType(it) == StremioUrls.canonicalType(type) }
}

data class StremioResource(
    val name: String,
    val types: Set<String> = emptySet(),
    val idPrefixes: Set<String> = emptySet(),
) {
    fun matches(type: String, id: String): Boolean =
        (types.isEmpty() || types.any { StremioUrls.canonicalType(it) == StremioUrls.canonicalType(type) }) &&
            (idPrefixes.isEmpty() || idPrefixes.any(id::startsWith))
}
