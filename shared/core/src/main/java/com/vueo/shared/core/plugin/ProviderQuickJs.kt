package com.vueo.shared.core.plugin

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** JNI class globals are shared across instances. Guard lifecycle, never the evaluation block. */
internal object ProviderQuickJs {
    private val lifecycleGate = Mutex()

    suspend fun <T> use(block: suspend QuickJs.() -> T): T {
        val runtime = lifecycleGate.withLock { QuickJs.create(Dispatchers.Default) }
        try {
            return runtime.block()
        } finally {
            withContext(NonCancellable) {
                lifecycleGate.withLock { runtime.close() }
            }
        }
    }
}
