package android.text

/** JVM-only string plumbing for Media3 model objects. Never copy into Android source sets. */
object TextUtils {
    @JvmStatic fun isEmpty(value: CharSequence?): Boolean = value == null || value.isEmpty()
    @JvmStatic fun equals(first: CharSequence?, second: CharSequence?): Boolean = first?.toString() == second?.toString()
}
