package com.vueo.mobile.core.storage
import java.util.concurrent.ConcurrentHashMap
class AddonStore {
    val urls = ConcurrentHashMap.newKeySet<String>()
    val disabled = ConcurrentHashMap.newKeySet<String>()
    fun manifestUrls() = urls.toList().sorted()
    fun isAddonEnabled(url: String) = url !in disabled
}
