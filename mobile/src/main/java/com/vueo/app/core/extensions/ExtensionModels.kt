package com.vueo.app.core.extensions

/** Mobile compatibility aliases for the canonical shared extension domain. */
typealias ExtensionKind = com.vueo.shared.core.extensions.ExtensionKind
typealias ExtensionHealth = com.vueo.shared.core.extensions.ExtensionHealth
typealias AddonCategory = com.vueo.shared.core.extensions.AddonCategory
typealias CatalogExtraDescriptor = com.vueo.shared.core.extensions.CatalogExtraDescriptor
typealias CatalogDescriptor = com.vueo.shared.core.extensions.CatalogDescriptor
typealias ExtensionDescriptor = com.vueo.shared.core.extensions.ExtensionDescriptor

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
