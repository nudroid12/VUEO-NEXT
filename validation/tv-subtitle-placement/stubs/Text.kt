package android.text
class Layout { enum class Alignment { ALIGN_CENTER, ALIGN_NORMAL, ALIGN_OPPOSITE } }
class SpannableStringBuilder: CharSequence {
 private val s=StringBuilder()
 fun append(value:CharSequence):SpannableStringBuilder {s.append(value);return this}
 fun append(value:Char):SpannableStringBuilder {s.append(value);return this}
 override val length get()=s.length
 override fun get(index:Int)=s[index]
 override fun subSequence(startIndex:Int,endIndex:Int)=s.subSequence(startIndex,endIndex)
 override fun toString()=s.toString()
}
class SpannedString(value:CharSequence):CharSequence by value {private val s=value.toString();override fun toString()=s}
