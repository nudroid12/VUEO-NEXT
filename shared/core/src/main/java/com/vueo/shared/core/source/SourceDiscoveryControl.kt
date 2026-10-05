package com.vueo.shared.core.source

import kotlinx.coroutines.Job

/** One scan's plugin branch. Addon/subtitle jobs are never attached here. */
class SourceDiscoveryControl {
    private val lock = Any()
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
