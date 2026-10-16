package com.vueo.shared.core.extensions

import java.security.MessageDigest

/** Exact configuration URL is the identity. Never log or expose its embedded secrets. */
object AddonInstallationIdentity {
    private const val PREFIX = "stremio-install-v1-"
    fun isScopedCatalogKey(key: String): Boolean = key.startsWith(PREFIX)

    fun key(kind: ExtensionKind, manifestId: String, manifestUrl: String): String =
        if (kind != ExtensionKind.STREMIO_ADDON) manifestId else {
            val bytes = MessageDigest.getInstance("SHA-256").digest(manifestUrl.trim().toByteArray(Charsets.UTF_8))
            PREFIX + bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        }

    fun catalogKey(installationId: String, type: String, catalogId: String): String =
        "$installationId:$type:$catalogId"

    /** Legacy keys are retained for other configurations which have not loaded yet. */
    fun migrateCatalogPreferences(
        order: List<String>, disabled: Set<String>, descriptor: ExtensionDescriptor,
    ): Pair<List<String>, Set<String>> {
        val aliases = descriptor.catalogs.associate { catalog ->
            catalogKey(descriptor.id, catalog.type, catalog.id) to
                catalogKey(descriptor.installationId, catalog.type, catalog.id)
        }
        val migratedOrder = order.flatMap { key -> listOfNotNull(key, aliases[key]) }.distinct()
        val migratedDisabled = disabled + disabled.mapNotNull(aliases::get)
        return migratedOrder to migratedDisabled
    }
}
