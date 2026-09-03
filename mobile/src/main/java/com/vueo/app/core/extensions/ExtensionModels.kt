package com.vueo.app.core.extensions

enum class ExtensionKind {
    STREMIO_ADDON,
    PROVIDER_PLUGIN,
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
    MULTI_PURPOSE("Multi-purpose"),
    OTHER("Other"),
}

fun ExtensionDescriptor.primaryAddonCategory(): AddonCategory {
    val hasCatalog = "catalog" in resources
    val hasMeta = "meta" in resources
    val hasStream = "stream" in resources
    val hasSubtitles = "subtitles" in resources

    val capabilityGroups = listOf(
        hasCatalog || hasMeta,
        hasStream,
        hasSubtitles,
    ).count { it }

    return when {
        capabilityGroups >= 2 -> AddonCategory.MULTI_PURPOSE
        hasStream -> AddonCategory.STREAMS
        hasSubtitles -> AddonCategory.SUBTITLES
        hasCatalog || hasMeta -> AddonCategory.CATALOG_METADATA
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
) {
    val canLoadWithoutExtras: Boolean
        get() = extras.none { it.isRequired }
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
    val health: ExtensionHealth = ExtensionHealth.UNKNOWN,
)
