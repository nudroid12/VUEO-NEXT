package com.vueo.mobile.core.extensions

import com.vueo.shared.core.extensions.primaryAddonCategory as sharedPrimaryAddonCategory

/** Mobile compatibility aliases for the canonical shared extension domain. */
typealias ExtensionKind = com.vueo.shared.core.extensions.ExtensionKind
typealias ExtensionHealth = com.vueo.shared.core.extensions.ExtensionHealth
typealias AddonCategory = com.vueo.shared.core.extensions.AddonCategory
typealias CatalogExtraDescriptor = com.vueo.shared.core.extensions.CatalogExtraDescriptor
typealias CatalogDescriptor = com.vueo.shared.core.extensions.CatalogDescriptor
typealias ExtensionDescriptor = com.vueo.shared.core.extensions.ExtensionDescriptor

fun ExtensionDescriptor.primaryAddonCategory(): AddonCategory =
    this.sharedPrimaryAddonCategory()
