package android.content
class Context {
 companion object { const val MODE_PRIVATE=0 }
 val applicationContext get()=this
 var activeProfile="default"
 private val files=mutableMapOf<String,SharedPreferences>()
 fun getSharedPreferences(name:String,mode:Int)=files.getOrPut(name){SharedPreferences()}
}
class SharedPreferences {
 private val values=linkedMapOf<String,Any>()
 val all:Map<String,Any> get()=values.toMap()
 fun getString(k:String,d:String?):String?=values[k] as? String ?: d
 fun getLong(k:String,d:Long):Long=values[k] as? Long ?: d
 fun getBoolean(k:String,d:Boolean):Boolean=values[k] as? Boolean ?: d
 @Suppress("UNCHECKED_CAST") fun getStringSet(k:String,d:Set<String>?):Set<String>?=values[k] as? Set<String> ?: d
 fun contains(k:String)=values.containsKey(k)
 fun edit()=Editor()
 inner class Editor {
  private val writes=linkedMapOf<String,Any?>()
  fun putString(k:String,v:String?)=apply{writes[k]=v}
  fun putLong(k:String,v:Long)=apply{writes[k]=v}
  fun putBoolean(k:String,v:Boolean)=apply{writes[k]=v}
  fun putStringSet(k:String,v:Set<String>?)=apply{writes[k]=v?.toSet()}
  fun remove(k:String)=apply{writes[k]=null}
  fun apply(){writes.forEach{(k,v)->if(v==null)values.remove(k) else values[k]=v}}
 }
}
