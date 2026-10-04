package com.vueo.shared.core.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

/** Owned by one getStreams invocation; never cancels the QuickJS caller or another provider. */
internal class ProviderExecutionTasks {
    private val lock = Any()
    private val jobs = mutableMapOf<Job, String?>()
    private val cancelledTimers = mutableSetOf<String>()
    @Volatile var isFinished = false
        private set

    suspend fun <T> run(timerId: String? = null, block: suspend () -> T): T? = supervisorScope {
        val task = async(start = CoroutineStart.LAZY) { block() }
        val accepted = synchronized(lock) {
            if (isFinished || timerId in cancelledTimers) false
            else { jobs[task] = timerId; true }
        }
        if (!accepted) task.cancel()
        try {
            task.await()
        } catch (error: CancellationException) {
            // A cancelled child is expected during cleanup. Caller/deadline cancellation is not.
            currentCoroutineContext().ensureActive()
            null
        } finally {
            synchronized(lock) { jobs.remove(task) }
        }
    }

    fun cancelTimer(timerId: String) {
        val pending = synchronized(lock) {
            // Handles clearTimeout before the async binding has registered its job.
            cancelledTimers.add(timerId)
            jobs.filterValues { it == timerId }.keys.toList()
        }
        pending.forEach { it.cancel() }
    }

    fun finish() {
        val pending = synchronized(lock) {
            isFinished = true
            jobs.keys.toList()
        }
        pending.forEach { it.cancel() }
    }
}

internal val PROVIDER_TIMER_SCRIPT = """
    var __vueoExecutionFinished = false;
    var __vueoNextTimerId = 0;
    var __vueoTimers = Object.create(null);
    globalThis.setTimeout = function (callback, millis) {
      var id = String(++__vueoNextTimerId);
      var args = Array.prototype.slice.call(arguments, 2);
      if (__vueoExecutionFinished) return Number(id);
      __vueoTimers[id] = true;
      __vueoTimerDelay(JSON.stringify({ id: id, millis: Number(millis || 0) }))
        .then(function (fired) {
          if (!fired || __vueoExecutionFinished || !__vueoTimers[id]) return;
          delete __vueoTimers[id];
          if (typeof callback === "function") callback.apply(globalThis, args);
        });
      return Number(id);
    };
    globalThis.clearTimeout = function (id) {
      id = String(id);
      if (!__vueoTimers[id]) return;
      delete __vueoTimers[id];
      __vueoCancelTimer(id);
    };
""".trimIndent()
