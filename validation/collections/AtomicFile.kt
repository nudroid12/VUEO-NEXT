// JVM stand-in for Android AtomicFile. Used only to exercise the production
// CollectionStore IO/locking. This is not a test of Android's AtomicFile itself.
package android.util
import java.io.*
class AtomicFile(val baseFile: File) {
    fun openRead(): FileInputStream = FileInputStream(baseFile)
    fun startWrite(): FileOutputStream = FileOutputStream(File(baseFile.path + ".new"))
    fun finishWrite(stream: FileOutputStream) { stream.close(); check(File(baseFile.path + ".new").renameTo(baseFile)) }
    fun failWrite(stream: FileOutputStream) { stream.close(); File(baseFile.path + ".new").delete() }
}
