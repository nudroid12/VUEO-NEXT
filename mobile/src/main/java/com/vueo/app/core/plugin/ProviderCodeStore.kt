package com.vueo.app.core.plugin

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

data class ProviderCodeSyncResult(
    val repository: PluginRepositoryDescriptor,
    val readyProviders: Int,
    val failedProviders: Int,
    val errors: List<String>,
)

class ProviderCodeStore(context: Context) {
    private val root = File(
        context.filesDir,
        "nuvio_plugin_scrapers",
    ).apply {
        mkdirs()
    }

    fun read(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
    ): String? {
        val file = fileFor(repository, provider)

        if (!file.isFile) {
            return null
        }

        return runCatching {
            file.readText(Charsets.UTF_8)
        }.getOrNull()
    }

    fun write(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
        source: String,
    ) {
        val file = fileFor(repository, provider)
        file.parentFile?.mkdirs()
        file.writeText(source, Charsets.UTF_8)
    }

    fun has(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
    ): Boolean =
        fileFor(repository, provider).isFile

    fun readyCount(
        repository: PluginRepositoryDescriptor,
    ): Int =
        repository.providers.count {
            has(repository, it)
        }

    fun removeRepository(manifestUrl: String) {
        runCatching {
            File(root, hash(manifestUrl))
                .deleteRecursively()
        }
    }

    private fun fileFor(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
    ): File {
        val repoDir = File(
            root,
            hash(repository.manifestUrl),
        )

        val key = listOf(
            provider.id,
            provider.version,
            provider.filename,
        ).joinToString("|")

        return File(
            repoDir,
            "${hash(key)}.js",
        )
    }

    private fun hash(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") {
                "%02x".format(it)
            }
}

class ProviderCodeSyncManager(
    context: Context,
) {
    private val store = ProviderCodeStore(
        context.applicationContext
    )

    private val concurrency = Semaphore(4)

    suspend fun syncRepository(
        repository: PluginRepositoryDescriptor,
        force: Boolean,
    ): ProviderCodeSyncResult = coroutineScope {
        val errors = mutableListOf<String>()

        val outcomes = repository.providers.map { provider ->
            async {
                concurrency.withPermit {
                    if (
                        !force &&
                        store.has(repository, provider)
                    ) {
                        return@withPermit true
                    }

                    runCatching {
                        val url =
                            PluginRepositoryClient
                                .providerScriptUrl(
                                    repository,
                                    provider,
                                )

                        val source =
                            PluginHttp.getText(url)

                        require(source.isNotBlank()) {
                            "Empty provider script."
                        }

                        store.write(
                            repository,
                            provider,
                            source,
                        )

                        true
                    }.getOrElse { error ->
                        synchronized(errors) {
                            errors +=
                                "${provider.name}: " +
                                (
                                    error.message
                                        ?: error::class.java.simpleName
                                )
                        }
                        false
                    }
                }
            }
        }.awaitAll()

        ProviderCodeSyncResult(
            repository = repository,
            readyProviders = outcomes.count { it },
            failedProviders = outcomes.count { !it },
            errors = errors.take(8),
        )
    }

    suspend fun syncMissing(
        repositories: List<PluginRepositoryDescriptor>,
    ) {
        repositories.forEach { repository ->
            syncRepository(
                repository = repository,
                force = false,
            )
        }
    }
}
