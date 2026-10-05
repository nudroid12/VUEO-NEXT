package com.vueo.shared.core.source

import kotlinx.coroutines.Job

/** Source branch controls for one scan. Subtitle jobs are never attached here. */
class SourceDiscoveryControl {
    private val lock = Any()
    private var addonJob: Job? = null
    @Volatile var sourcesStopped: Boolean = false
        private set

    internal fun attachAddons(job: Job) {
        val stop = synchronized(lock) { addonJob = job; sourcesStopped }
        if (stop) job.cancel()
    }

    internal fun detachAddons(job: Job) {
        synchronized(lock) { if (addonJob === job) addonJob = null }
    }

    /** Stop source branches only. Subtitle discovery is deliberately independent. */
    fun stopSources() {
        val job = synchronized(lock) { sourcesStopped = true; addonJob }
        stopPlugins()
        job?.cancel()
    }

    private var pluginJob: Job? = null
    private var pluginCompleted = false
    @Volatile var pluginsStopped: Boolean = false
        private set

    internal fun attachPlugins(job: Job) {
        val stop = synchronized(lock) {
            pluginJob = job
            pluginsStopped
        }
        if (stop) job.cancel()
    }

    internal fun detachPlugins(job: Job) {
        synchronized(lock) { if (pluginJob === job) { pluginJob = null; pluginCompleted = true } }
    }

    fun stopPlugins() {
        val job = synchronized(lock) {
            if (pluginsStopped || pluginCompleted) return
            pluginsStopped = true
            pluginJob
        }
        job?.cancel()
    }
}
