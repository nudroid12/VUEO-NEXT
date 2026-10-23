package com.vueo.shared.core.storage
import android.content.Context
import org.json.*
class ProfileStore(private val context:Context) {
 fun activeProfileId()=context.activeProfile
 fun kidsAgeLimit():Int?=null
 companion object {
  fun scopedPreferenceKey(profile:String,base:String)=if(profile=="default")base else "profile:$profile:$base"
 }
}
object CompactLibraryCodec {
 fun readArray(raw:String,keepEpisodeIndex:Boolean=false,onEntry:(JSONObject)->Unit={}):List<JSONObject> {
  val a=JSONArray(raw);return (0 until a.length()).map{a.getJSONObject(it).also(onEntry)}
 }
 fun compactEntry(entry:JSONObject)=entry
}
