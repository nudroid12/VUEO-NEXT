package android.content
import java.io.File
class Context(val filesDir: File) { val applicationContext: Context get() = this }
