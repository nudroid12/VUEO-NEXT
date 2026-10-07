package com.vueo.shared.core.extensions

enum class ExtensionKind {
    STREMIO_ADDON,
    PROVIDER_PLUGIN,
}

enum class MediaBrowseKind {
    MOVIE,
    SERIES,
    ANIME,
}

enum class ExtensionHealth {
    ONLINE,
    SLOW,
    OFFLINE,
    UNKNOWN,
}

enum class AddonCategory(
    val label: String,
) {
    CATALOG_METADATA("Catalog & Metadata"),
    STREAMS("Streams"),
    SUBTITLES("Subtitles"),
    OTHER("Other"),
}

fun ExtensionDescriptor.primaryAddonCategory(): AddonCategory {
    // One canonical category per addon. Stream capability wins even when the
    // same addon also exposes catalogs/meta/subtitles, matching the TV behavior.
    return when {
        "stream" in resources -> AddonCategory.STREAMS
        "subtitles" in resources -> AddonCategory.SUBTITLES
        "catalog" in resources || "meta" in resources -> AddonCategory.CATALOG_METADATA
        else -> AddonCategory.OTHER
    }
}

data class CatalogExtraDescriptor(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
)

data class CatalogDescriptor(
    val type: String,
    val id: String,
    val name: String? = null,
    val extras: List<CatalogExtraDescriptor> = emptyList(),
    val showInHome: Boolean? = null,
) {
    val canLoadWithoutExtras: Boolean
        get() = extras.none { it.isRequired }

    val isSearchOnly: Boolean
        get() =
            extras.any {
                it.isRequired && it.name.equals("search", ignoreCase = true)
            }

    val shouldShowOnHome: Boolean
        get() = !isSearchOnly && showInHome != false
}

data class ExtensionDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val kind: ExtensionKind,
    val baseUrl: String,
    val description: String? = null,
    val resources: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val catalogs: List<CatalogDescriptor> = emptyList(),
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val health: ExtensionHealth = ExtensionHealth.UNKNOWN,
    val logo: String? = null,
)
