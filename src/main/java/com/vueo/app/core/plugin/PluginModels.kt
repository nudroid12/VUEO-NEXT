package com.vueo.app.core.plugin

data class PluginProviderDescriptor(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String = "0.0.0",
    val author: String? = null,
    val supportedTypes: Set<String> = emptySet(),
    val filename: String,
    val defaultEnabled: Boolean = true,
    val logo: String? = null,
    val contentLanguages: List<String> = emptyList(),
    val formats: List<String> = emptyList(),
    val limited: Boolean = false,
    val disabledPlatforms: Set<String> = emptySet(),
    val supportsExternalPlayer: Boolean = true,
)

data class PluginRepositoryDescriptor(
    val manifestUrl: String,
    val baseUrl: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val providers: List<PluginProviderDescriptor>,
)
