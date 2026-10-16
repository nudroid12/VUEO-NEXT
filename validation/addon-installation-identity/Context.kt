package android.content

class Context {
    val applicationContext: Context get() = this
    private val stores = mutableMapOf<String, SharedPreferences>()
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(stores) {
        stores.getOrPut(name) { SharedPreferences() }
    }
    companion object { const val MODE_PRIVATE = 0 }
}
class SharedPreferences {
    private val values = mutableMapOf<String, Any>()
    @Synchronized fun contains(key: String) = values.containsKey(key)
    @Synchronized fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    @Synchronized fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    @Synchronized fun getString(key: String, default: String?) = values[key] as? String ?: default
    @Suppress("UNCHECKED_CAST") @Synchronized fun getStringSet(key: String, default: Set<String>?) = (values[key] as? Set<String>)?.toSet() ?: default
    fun edit() = Editor()
    inner class Editor {
        private val changes = mutableMapOf<String, Any?>()
        fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        fun putInt(key: String, value: Int) = apply { changes[key] = value }
        fun putString(key: String, value: String?) = apply { changes[key] = value }
        fun putStringSet(key: String, value: Set<String>?) = apply { changes[key] = value?.toSet() }
        fun remove(key: String) = apply { changes[key] = null }
        fun apply() = synchronized(this@SharedPreferences) {
            changes.forEach { (key,value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
