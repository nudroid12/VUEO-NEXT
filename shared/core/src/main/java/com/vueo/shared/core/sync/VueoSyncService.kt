package com.vueo.shared.core.sync

import android.content.Context
import android.content.SharedPreferences
import com.vueo.shared.core.storage.ProfileStore
import com.vueo.shared.core.trakt.TraktAccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Local-first account sync. CAS conflicts choose cloud; a new user edit can supersede that revision. */
class VueoSyncService private constructor(private val context: Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val accountGate=Mutex()
    private val syncGate=Mutex()
    private val tokenGate=Mutex()
    private val traktGate=Mutex()
    private val lock=Any()
    private val scheduleLock=Any()
    private val authFile=SyncSecureFile(context,"auth")
    private val options=context.getSharedPreferences("vueo_sync_options",Context.MODE_PRIVATE)
    private val adapter=SyncLocalAdapter(context)
    private val trakt=TraktAccountStore(context)
    @Volatile private var session: JSONObject? = null
    @Volatile private var ready=false
    @Volatile private var foreground=false
    @Volatile private var playerActive=false
    @Volatile private var pauseUntil=0L
    private var resyncRequested=false
    private var timer: Job?=null
    private var scheduled: Job?=null
    private var captureJob: Job?=null
    private var journalFile: SyncSecureFile?=null
    private var baseline=linkedMapOf<String,JSONObject>()
    private var revisions=linkedMapOf<String,Long>()
    private var pending=linkedMapOf<String,JSONObject>()
    private var tokenPending=linkedMapOf<String,JSONObject>()
    private var sequence=0L
    private var pullCursor="0"
    private val _state=MutableStateFlow("Not connected")
    val state: StateFlow<String> = _state
    private val _revision=MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision
    private val _dataRevision=MutableStateFlow(0L)
    val dataRevision: StateFlow<Long> = _dataRevision
    private val _configurationRevision=MutableStateFlow(0L)
    val configurationRevision: StateFlow<Long> = _configurationRevision
    val categories: Map<String,String> get()=VueoSyncConfig.categories
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (ready && connected()) scheduleCapture()
    }
    init {
        (context as? android.app.Application)?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { setForeground(true) }
            override fun onActivityStopped(activity: android.app.Activity) { setForeground(false) }
            override fun onActivityCreated(activity: android.app.Activity,state: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) { setForeground(true) }
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity,state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
        scope.launch {
            try {
                val saved=authFile.read()
                if (saved!=null) { session=saved; loadJournal(saved.getJSONObject("user").getString("id")) }
                adapter.files.forEach { context.getSharedPreferences(it,Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener) }
                ready=true
                _state.value=if (connected()) "Ready to sync" else "Not connected"
                changed()
                if (foreground) requestSync()
            } catch (_: Exception) { _state.value="Unable to read encrypted sync state. Sign in again."; ready=true; changed() }
        }
    }
    fun connected()=session!=null
    fun email()=session?.optJSONObject("user")?.optString("email").orEmpty()
    private fun uid()=session?.optJSONObject("user")?.optString("id").orEmpty()
    fun enabled(kind: String)=options.getBoolean("enabled:$kind",false)
    fun setPlayerActive(active: Boolean) {
        playerActive=active
        if(!active) { pauseUntil=android.os.SystemClock.elapsedRealtime()+3_000; requestSync(3_000) }
    }
    fun setEnabled(kind: String,value: Boolean) {
        require(kind in categories)
        if(!ready || !connected()) return
        val edit=options.edit().putBoolean("enabled:$kind",value)
        if(value && kind!="profiles") edit.putBoolean("enabled:profiles",true)
        if(!value && kind=="profiles") categories.keys.forEach { edit.putBoolean("enabled:$it",false) }
        edit.apply()
        scope.launch {
            syncGate.withLock {
                synchronized(lock) {
                    pullCursor="0"
                    pending.entries.removeAll { !enabled(it.value.optString("kind")) || it.value.optString("kind")==kind }
                    if (!enabled("trakt")) tokenPending.clear()
                    if (value && connected()) {
                        adapter.snapshot().filterValues { it.optString("kind")==kind || it.optString("kind")=="profiles" }.forEach { (k,row) -> enqueue(k,row,false) }
                    }
                    persistJournal()
                }
            }
            changed(); requestSync()
        }
    }
    fun setForeground(active: Boolean) {
        foreground=active
        if (active) {
            if (timer?.isActive!=true) timer=scope.launch {
                while (foreground) { if (ready) requestSync(); delay(60_000) }
            }
        } else { timer?.cancel(); timer=null }
    }
    private fun changed() { synchronized(_revision) { _revision.value+=1 } }
    private fun scheduleCapture() {
        synchronized(scheduleLock) {
            captureJob?.cancel()
            captureJob=scope.launch { delay(750); syncGate.withLock { capture() }; requestSync(3_000) }
        }
    }
    fun requestSync(delayMs: Long=0) {
        if (!ready || !connected() || !foreground) return
        synchronized(scheduleLock) {
            if (scheduled?.isActive==true) { resyncRequested=true; return }
            scheduled=scope.launch {
                delay(delayMs)
                try { syncNow() } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { _state.value=message(error); changed() }
                finally {
                    val again=synchronized(scheduleLock) { scheduled=null; resyncRequested.also { resyncRequested=false } }
                    if(again) requestSync(3_000)
                }
            }
        }
    }
    suspend fun signIn(email: String,password: String,register: Boolean=false): String = withContext(Dispatchers.IO) {
        accountGate.withLock {
            check(ready) { "Sync is starting. Try again." }
            check(!connected()) { "Sign out before changing accounts." }
            require(email.trim().contains('@') && password.isNotBlank()) { "Enter your email and password." }
            val result=SyncHttp.post(if(register) "/auth/v1/signup" else "/auth/v1/token?grant_type=password",
                JSONObject().put("email",email.trim()).put("password",password))
            if (result.optString("access_token").isBlank()) return@withLock "Check your email to confirm the account, then sign in."
            syncGate.withLock {
                val user=result.getJSONObject("user").getString("id")
                loadJournal(user)
                if(options.getString("owner",null)!=user) {
                    val edit=options.edit().putString("owner",user)
                    categories.keys.forEach { edit.putBoolean("enabled:$it",false) }; edit.commit()
                }
                if(!result.has("expires_at")) result.put("expires_at",System.currentTimeMillis()/1000+result.optLong("expires_in",3600))
                authFile.write(result)
                session=result
                capture()
            }
            _state.value="Connected. Choose what to sync."; changed(); requestSync()
            "Connected. Choose what to sync."
        }
    }
    suspend fun signOut()=withContext(Dispatchers.IO) {
        accountGate.withLock {
            syncGate.withLock {
                tokenGate.withLock {
                    session=null; authFile.delete()
                    synchronized(lock) { pending.clear(); tokenPending.clear(); baseline.clear(); revisions.clear(); journalFile=null }
                }
            }
            _state.value="Signed out. Local data stays on this device."; changed()
        }
    }
    private fun loadJournal(user: String) {
        val hash=MessageDigest.getInstance("SHA-256").digest(user.toByteArray()).joinToString("") { "%02x".format(it) }
        journalFile=SyncSecureFile(context,hash)
        val saved=journalFile!!.read()
        baseline=linkedMapOf(); revisions=linkedMapOf(); pending=linkedMapOf(); tokenPending=linkedMapOf(); pullCursor="0"; sequence=0
        if(saved!=null) {
            fun readMap(name: String): LinkedHashMap<String,JSONObject> {
                val obj=saved.optJSONObject(name) ?: JSONObject()
                return obj.keys().asSequence().associateWith { obj.getJSONObject(it) }.toMap(linkedMapOf())
            }
            baseline=readMap("baseline"); pending=readMap("pending"); tokenPending=readMap("tokens")
            val rev=saved.optJSONObject("revisions") ?: JSONObject()
            rev.keys().forEach { revisions[it]=rev.getLong(it) }
            sequence=saved.optLong("sequence"); pullCursor=saved.optString("cursor","0")
        }
    }
    private fun persistJournal() {
        if (session==null) return
        journalFile?.write(JSONObject().put("baseline",JSONObject(baseline as Map<*,*>))
            .put("pending",JSONObject(pending as Map<*,*>)).put("tokens",JSONObject(tokenPending as Map<*,*>))
            .put("revisions",JSONObject(revisions as Map<*,*>)).put("sequence",sequence).put("cursor",pullCursor))
    }
    private fun enqueue(key: String,row: JSONObject,deleted: Boolean) {
        sequence+=1
        pending[key]=JSONObject(row.toString()).put("deleted",deleted)
            .put("revision",revisions[key] ?: 0L).put("generation",sequence)
    }
    private fun capture() = synchronized(lock) {
        if (!connected()) return@synchronized
        val current=adapter.snapshot()
        (current.keys+baseline.keys).forEach { k ->
            val now=current[k]; val old=baseline[k]
            val kind=(now ?: old)?.optString("kind") ?: return@forEach
            if (enabled(kind) && now?.optJSONObject("value")?.toString()!=old?.optJSONObject("value")?.toString())
                enqueue(k,now ?: old!!,now==null)
        }
        baseline=LinkedHashMap(current)
        persistJournal()
    }
    private suspend fun bearer(): String = tokenGate.withLock {
        val current=session ?: throw IOException("Sign in to VUEO first.")
        val expires=current.optLong("expires_at",current.optLong("created_at")+current.optLong("expires_in"))
        if (expires*1000L > System.currentTimeMillis()+60_000) return@withLock current.getString("access_token")
        val refreshed=SyncHttp.post("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",current.getString("refresh_token")))
        check(refreshed.getJSONObject("user").getString("id")==current.getJSONObject("user").getString("id"))
        if(!refreshed.has("expires_at")) refreshed.put("expires_at",System.currentTimeMillis()/1000+refreshed.optLong("expires_in",3600))
        authFile.write(refreshed); session=refreshed
        refreshed.getString("access_token")
    }
    private suspend fun rpc(name: String,body: JSONObject,user: String): JSONObject {
        check(user.isNotBlank() && uid()==user) { "VUEO account changed." }
        val token=bearer()
        check(uid()==user) { "VUEO account changed." }
        val result=SyncHttp.post("/rest/v1/rpc/$name",body,token)
        check(uid()==user) { "VUEO account changed." }
        return result
    }
    suspend fun syncNow()=withContext(Dispatchers.IO) {
        syncGate.withLock {
            if(!ready || !connected()) return@withLock
            val user=uid(); val kinds=categories.keys.filter(::enabled)
            if(kinds.isEmpty()) { _state.value="Connected. All sync options are off."; changed(); return@withLock }
            _state.value="Syncing…"; changed()
            capture()
            if(playerActive || android.os.SystemClock.elapsedRealtime()<pauseUntil) { _state.value="Playback active. Changes saved locally; sync resumes on exit."; changed(); return@withLock }
            var cursor=pullCursor
            do {
                val before=adapter.snapshot()
                val reply=rpc("vueo_sync_exchange",JSONObject().put("p_kinds",JSONArray(kinds)).put("p_after",cursor).put("p_limit",100),user)
                val rows=reply.getJSONArray("rows")
                synchronized(lock) {
                    val apply=mutableListOf<JSONObject>()
                    for(i in 0 until rows.length()) {
                        val row=rows.getJSONObject(i); val k=row.getString("key")
                        if(!enabled(row.getString("kind"))) continue
                        if(k !in pending && row.getLong("revision")>(revisions[k] ?: 0)) apply+=row
                        // A dirty record keeps its expected revision until CAS resolves it.
                        if(k !in pending) revisions[k]=row.getLong("revision")
                    }
                    applyRemote(apply,before)
                    pullCursor=reply.optString("cursor",pullCursor)
                    persistJournal()
                }
                cursor=reply.optString("next")
            } while(cursor.isNotBlank())
            // A bounded batch count prevents a constantly-playing device monopolizing IO.
            repeat(20) {
                val batch=synchronized(lock) { run {
                    var bytes=0
                    pending.values.filter { enabled(it.getString("kind")) }.take(50).takeWhile {
                        bytes+=it.toString().toByteArray(Charsets.UTF_8).size; bytes<=1_500_000
                    }.map { JSONObject(it.toString()) }
                } }
                if(batch.isEmpty()) return@repeat
                val before=adapter.snapshot()
                val reply=rpc("vueo_sync_exchange",JSONObject().put("p_changes",JSONArray(batch)),user)
                synchronized(lock) {
                    val apply=mutableListOf<JSONObject>(); val acks=reply.getJSONArray("acks")
                    for(i in 0 until acks.length()) {
                        val ack=acks.getJSONObject(i); val k=ack.getString("key")
                        revisions[k]=ack.getLong("revision")
                        val sent=batch.first { it.getString("key")==k }; val queued=pending[k]
                        if(queued?.optLong("generation")==sent.optLong("generation")) {
                            pending.remove(k)
                            if(!ack.optBoolean("accepted")) apply+=ack
                        } else queued?.put("revision",ack.getLong("revision"))
                    }
                    applyRemote(apply,before); persistJournal()
                }
            }
            if(enabled("trakt")) {
                val profileIds=ProfileStore(context).profiles().map { it.id }
                for(profile in profileIds) reconcileTrakt(profile,user)
            }
            _state.value=if(synchronized(lock){pending.isEmpty() && tokenPending.isEmpty()}) "Synced" else "Changes saved locally. More sync pending."
            changed()
        }
    }
    private fun applyRemote(rows: List<JSONObject>,before: Map<String,JSONObject>) {
        if(adapter.apply(rows,before)) {
            _dataRevision.value+=1
            if(rows.any { it.optString("kind") in setOf("addons","catalogs","integrations") }) _configurationRevision.value+=1
            if(rows.any { it.optString("kind")=="trakt" }) com.vueo.shared.core.trakt.TraktService.get(context).onVueoSyncApplied()
        }
        // Preserve concurrent local edits rather than marking them as remote-applied.
        val after=adapter.snapshot()
        (after.keys+baseline.keys).forEach { k ->
            val now=after[k]; val old=baseline[k]
            val remote=rows.firstOrNull { it.optString("key")==k }
            val matches=if(remote?.optBoolean("deleted")==true) now==null else
                remote!=null && now?.optJSONObject("value")?.toString()==remote.optJSONObject("value")?.toString()
            if(matches) { if(now==null) baseline.remove(k) else baseline[k]=now }
            else if(now?.optJSONObject("value")?.toString()!=old?.optJSONObject("value")?.toString()) {
                val row=now ?: old ?: return@forEach
                if(enabled(row.getString("kind"))) enqueue(k,row,now==null)
                if(now==null) baseline.remove(k) else baseline[k]=now
            }
        }
    }
    internal fun queueTraktSession(profile: String,tokens: JSONObject?) {
        if(!ready || !connected() || !enabled("trakt")) return
        val owner=uid()
        scope.launch {
            syncGate.withLock {
                if(uid()!=owner || !enabled("trakt")) return@withLock
                synchronized(lock) {
                    tokenPending[profile]=JSONObject().put("delete",tokens==null).put("value",tokens ?: JSONObject.NULL)
                    persistJournal()
                }
            }
            requestSync()
        }
    }
    private suspend fun claim(profile: String,user: String,lease: String): JSONObject {
        repeat(20) {
            val result=rpc("vueo_trakt_claim",JSONObject().put("p_profile",profile).put("p_lease",lease),user)
            if(result.optBoolean("acquired")) return result
            delay(750)
        }
        throw IOException("Trakt is syncing on another device. Try again shortly.")
    }
    private suspend fun release(profile: String,user: String,lease: String,tokens: JSONObject?=null,delete: Boolean=false) =
        rpc("vueo_trakt_release",JSONObject().put("p_profile",profile).put("p_lease",lease)
            .put("p_session",tokens ?: JSONObject.NULL).put("p_delete",delete),user)
    private suspend fun reconcileTrakt(profile: String,user: String) = traktGate.withLock {
        val lease=UUID.randomUUID().toString(); val cloud=claim(profile,user,lease)
        try {
            val intent=synchronized(lock) { tokenPending[profile] }
            val tokens=when {
                intent!=null -> intent.optJSONObject("value")
                cloud.optLong("revision")==0L -> trakt.read(profile)
                else -> cloud.optJSONObject("value")
            }
            val changed=intent!=null || (cloud.optLong("revision")==0L && tokens!=null)
            val result=release(profile,user,lease,if(changed) tokens else null,intent?.optBoolean("delete")==true)
            if(result.optBoolean("deleted")) {
                if(trakt.connected(profile)) { trakt.clear(profile,publishCloud=false); _dataRevision.value+=1; com.vueo.shared.core.trakt.TraktService.get(context).onVueoSessionImported(profile) }
            } else {
                val remote=result.getJSONObject("value")
                if(trakt.read(profile)?.toString()!=remote.toString()) { trakt.save(profile,remote,publishCloud=false); _dataRevision.value+=1; com.vueo.shared.core.trakt.TraktService.get(context).onVueoSessionImported(profile) }
            }
            synchronized(lock) { if(tokenPending[profile]===intent) tokenPending.remove(profile); persistJournal() }
        } catch(error: Exception) {
            try { release(profile,user,lease) } catch (_: Exception) { /* Lease expires on the server. */ }
            throw error
        }
    }
    internal fun sharesTrakt()=ready && connected() && enabled("trakt")
    /** A server lease serializes rotating refresh tokens across all devices. No local fallback while shared. */
    internal suspend fun cloudTraktToken(profile: String,rejected: String?,refresh: suspend (JSONObject)->JSONObject): String? {
        if(!sharesTrakt()) return null
        return syncGate.withLock {
            val user=uid()
            if(synchronized(lock) { profile in tokenPending }) reconcileTrakt(profile,user)
            traktGate.withLock {
                val lease=UUID.randomUUID().toString(); val cloud=claim(profile,user,lease)
                try {
                    val latest=cloud.optJSONObject("value") ?: (if(cloud.optLong("revision")==0L) trakt.read(profile) else null)
                        ?: throw IOException("Connect your Trakt account first.")
                    val access=latest.getString("access_token")
                    val expires=(latest.optLong("created_at")+latest.optLong("expires_in",604800))*1000
                    val needsRefresh=if(rejected==null) expires<=System.currentTimeMillis()+60000 else access==rejected
                    val rotated=if(needsRefresh) refresh(latest) else latest
                    release(profile,user,lease,if(needsRefresh || cloud.optLong("revision")==0L) rotated else null)
                    trakt.save(profile,rotated,publishCloud=false)
                    rotated.getString("access_token")
                } catch(error: Exception) {
                    try { release(profile,user,lease) } catch (_: Exception) { }
                    throw error
                }
            }
        }
    }
    companion object {
        @Volatile private var instance: VueoSyncService?=null
        fun get(context: Context): VueoSyncService=instance ?: synchronized(this) {
            instance ?: VueoSyncService(context.applicationContext).also { instance=it }
        }
        fun message(error: Throwable): String = when {
            error is SyncHttpError && error.code==429 -> "Too many requests. Try again shortly. Local changes are saved."
            error is SyncHttpError && error.reason=="email_not_confirmed" -> "Confirm your email, then sign in."
            error is SyncHttpError && error.code in setOf(400,401) -> "Unable to sign in or refresh the session. Check your account details."
            error is SyncHttpError && error.code in setOf(404,403) -> "Sync setup is missing or access is denied. Run the VUEO sync v2 SQL."
            else -> "Sync unavailable. Local changes are saved; retry when online."
        }
    }
}
