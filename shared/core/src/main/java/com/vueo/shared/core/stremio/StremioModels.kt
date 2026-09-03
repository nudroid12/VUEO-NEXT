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
) {
    fun supportsResource(resource: String): Boolean =
        resources.isEmpty() || resource in resources

    fun supportsType(type: String): Boolean =
        types.isEmpty() || type in types
}
