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
import com.vueo.shared.core.storage.CompactLibraryCodec
import java.io.IOException
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
    private var requestedPull=false
    @Volatile private var lastPullAt=0L
    private var initialCapture=true
    private val dirtyKinds=linkedSetOf<String>()
    private val dirtyKeys=linkedSetOf<Pair<String,String?>>()
    private val appliedKinds=linkedSetOf<String>()
    private var timer: Job?=null
    private var scheduled: Job?=null
    private var captureJob: Job?=null
    private var repository: SyncEntityRepository?=null
    private var tokenPending=linkedMapOf<String,JSONObject>()
    private val _state=MutableStateFlow("Not connected")
    val state: StateFlow<String> = _state
    private val _revision=MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision
    private val _dataRevision=MutableStateFlow(0L)
    val dataRevision: StateFlow<Long> = _dataRevision
    private val _configurationRevision=MutableStateFlow(0L)
    val configurationRevision: StateFlow<Long> = _configurationRevision
    private val _profileRevision=MutableStateFlow(0L)
    val profileRevision: StateFlow<Long> = _profileRevision
    private val _integrationRevision=MutableStateFlow(0L)
    val integrationRevision: StateFlow<Long> = _integrationRevision
    val categories: Map<String,String> get()=VueoSyncConfig.categories
    // Strong references: SharedPreferences keeps listeners weakly. Main-thread callbacks only classify keys.
    private val listeners=adapter.files.associateWith { file ->
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if(ready && connected()) {
                val relevant=adapter.preferenceCategories(file,key).filter(::enabled).toSet()
                if(relevant.isNotEmpty()) scheduleCapture(relevant,setOf(file to key))
            }
        }
    }
    private fun hasActiveSync()=ready && connected() && categories.keys.any(::enabled)
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
                if (saved!=null) { session=saved; loadRepositories(saved.getJSONObject("user").getString("id")) }
                listeners.forEach { (file,listener) -> context.getSharedPreferences(file,Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener) }
                ready=true
                _state.value=if (connected()) "Ready to sync" else "Not connected"
                changed()
                if (foreground) restartForegroundSync()
            } catch (_: Exception) { _state.value="Unable to read encrypted sync state. Sign in again."; ready=true; changed() }
        }
    }
    fun connected()=session!=null
    fun email()=session?.optJSONObject("user")?.optString("email").orEmpty()
    private fun uid()=session?.optJSONObject("user")?.optString("id").orEmpty()
    fun enabled(kind: String)=kind in categories && options.getBoolean("enabled:$kind",false)
    fun setPlayerActive(active: Boolean) {
        val wasActive=playerActive
        playerActive=active
        if(wasActive && !active) { pauseUntil=android.os.SystemClock.elapsedRealtime()+3_000; requestSync(3_000,pull=true) }
    }
    fun setEnabled(kind: String,value: Boolean) {
        require(kind in categories)
        if(!ready || !connected()) return
        val owner=uid()
        val edit=options.edit().putBoolean("enabled:$kind",value)
        if(value && kind!="profiles") edit.putBoolean("enabled:profiles",true)
        if(!value && kind=="profiles") categories.keys.forEach { edit.putBoolean("enabled:$it",false) }
        edit.apply()
        scope.launch {
            syncGate.withLock {
                if(uid()!=owner || !connected()) return@withLock
                synchronized(lock) {
                    if(value && repository==null) loadRepositories(uid(),force=true)
                    repository?.transaction {
                        val enabledKinds=categories.keys.filter(::enabled).toSet()
                        repository!!.resetCursor(kind)
                        repository!!.clearPendingExcept(enabledKinds,kind)
                        if (!enabled("trakt")) { tokenPending.clear(); repository!!.clearTokens() }
                        if (value && enabled(kind) && connected()) {
                            adapter.snapshot(setOf(kind,"profiles")).forEach { (_,row) -> repository!!.enqueue(row,false) }
                        }
                    }
                }
            }
            changed(); lastPullAt=0L; restartForegroundSync(); requestSync(pull=true)
        }
    }
    fun setForeground(active: Boolean) {
        val wasForeground=foreground
        foreground=active
        if(!active) { timer?.cancel(); timer=null; return }
        if(!wasForeground) restartForegroundSync()
    }
    private fun restartForegroundSync() {
        timer?.cancel(); timer=null
        if(!foreground || !hasActiveSync()) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(lastPullAt==0L || now-lastPullAt>=120_000L) requestSync(2_500,pull=true)
        else requestSync(500,pull=false)
        timer=scope.launch {
            while(foreground && hasActiveSync()) { delay(15*60_000L); requestSync(pull=true) }
        }
    }
    private fun changed() { synchronized(_revision) { _revision.value+=1 } }
    private fun scheduleCapture(kinds: Set<String>,keys: Set<Pair<String,String?>> = emptySet()) {
        synchronized(scheduleLock) {
            dirtyKinds+=kinds
            dirtyKeys+=keys
            if(captureJob?.isActive==true) return
            captureJob=scope.launch {
                var failed=false
                try {
                    delay(750)
                    val owner=uid()
                    val mutated=syncGate.withLock {
                        if(owner.isBlank() || uid()!=owner || !hasActiveSync()) false else captureDirty()
                    }
                    if(mutated) requestSync(500,pull=false)
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(error: Exception) { failed=true; _state.value=message(error); changed() }
                finally {
                    synchronized(scheduleLock) {
                        captureJob=null
                        if(!failed && dirtyKinds.isNotEmpty() && hasActiveSync()) scheduleCapture(emptySet())
                    }
                }
            }
        }
    }
    fun requestSync(delayMs: Long=0,pull: Boolean=true) {
        if(!hasActiveSync() || !foreground) return
        synchronized(scheduleLock) {
            requestedPull=requestedPull || pull
            if(scheduled?.isActive==true) { resyncRequested=true; return }
            scheduled=scope.launch {
                try {
                    delay(delayMs)
                    if(!foreground || !hasActiveSync()) return@launch
                    val shouldPull=synchronized(scheduleLock) { requestedPull.also { requestedPull=false } }
                    performSync(shouldPull,captureAll=false)
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(error: Exception) { _state.value=message(error); changed() }
                finally {
                    val again=synchronized(scheduleLock) { scheduled=null; resyncRequested.also { resyncRequested=false } }
                    if(again) requestSync(500,pull=false)
                }
            }
        }
    }
    suspend fun signIn(email: String,password: String,register: Boolean=false): String = withContext(Dispatchers.IO) {
        accountGate.withLock {
            check(ready) { "Sync is starting. Try again." }
            check(!connected()) { "Sign out before changing accounts." }
            require(email.trim().contains('@') && password.isNotBlank()) { "Enter your email and password." }
            val result=SyncHttp.post(if(register) "/auth/v1/signup?redirect_to="+android.net.Uri.encode(VueoSyncConfig.EMAIL_REDIRECT) else "/auth/v1/token?grant_type=password",
                JSONObject().put("email",email.trim()).put("password",password))
            if (result.optString("access_token").isBlank()) return@withLock "Check your email to confirm the account, then sign in."
            syncGate.withLock {
                val user=result.getJSONObject("user").getString("id")
                loadRepositories(user)
                if(options.getString("owner",null)!=user) {
                    val edit=options.edit().putString("owner",user)
                    categories.keys.forEach { edit.putBoolean("enabled:$it",false) }; edit.commit()
                }
                if(!result.has("expires_at")) result.put("expires_at",System.currentTimeMillis()/1000+result.optLong("expires_in",3600))
                authFile.write(result)
                session=result
                capture()
            }
            _state.value="Connected. Choose what to sync."; changed(); restartForegroundSync()
            "Connected. Choose what to sync."
        }
    }
    suspend fun signOut()=withContext(Dispatchers.IO) {
        accountGate.withLock {
            syncGate.withLock {
                tokenGate.withLock {
                    session=null; authFile.delete(); timer?.cancel(); timer=null
                    synchronized(scheduleLock) { dirtyKinds.clear(); dirtyKeys.clear(); requestedPull=false }
                    synchronized(lock) { tokenPending.clear(); repository?.close(); repository=null }
                }
            }
            _state.value="Signed out. Local data stays on this device."; changed()
        }
    }
    private fun loadRepositories(user: String,force: Boolean=false) {
        repository?.close(); repository=null; tokenPending.clear()
        initialCapture=true; lastPullAt=0L
        if(!force && categories.keys.none(::enabled)) return
        val opened=SyncEntityRepository(context,user)
        try {
            opened.migrateLegacy(context,adapter::accepts)
            tokenPending=opened.tokens()
            repository=opened
        } catch(error: Exception) { opened.close(); throw error }
    }
    private fun captureDirty(all: Set<String>?=null): Boolean {
        val work=synchronized(scheduleLock) {
            (dirtyKinds.toSet() to dirtyKeys.toSet()).also { dirtyKinds.clear(); dirtyKeys.clear() }
        }
        try { return if(all!=null) capture(all) else capture(work.first,work.second.takeIf { it.isNotEmpty() }) }
        catch(error: Exception) {
            synchronized(scheduleLock) { dirtyKinds+=work.first; dirtyKeys+=work.second }
            throw error
        }
    }
    private fun capture(kinds: Set<String> = categories.keys.filter(::enabled).toSet(),scopes: Set<Pair<String,String?>>?=null): Boolean = synchronized(lock) {
        if(!connected()) return@synchronized false
        val selected=kinds.filter(::enabled).toSet()
        if(selected.isEmpty()) return@synchronized false
        if(repository==null) loadRepositories(uid(),force=true)
        val store=repository!!
        val current=adapter.snapshot(selected,scopes)
        store.transaction {
            store.clearPendingExcept(categories.keys.filter(::enabled).toSet())
            val known=store.states(selected).filterKeys { scopes==null || adapter.matchesScope(it,scopes) }
            var mutated=false
            (current.keys+known.filterValues { it.fingerprint!=null }.keys).forEach { key ->
                val now=current[key]; val old=known[key]
                val hash=syncFingerprint(now?.optJSONObject("value"))
                if(hash!=old?.fingerprint) {
                    val row=now ?: JSONObject().put("kind",old!!.kind).put("key",key).put("value",JSONObject())
                    store.enqueue(row,now==null)
                    mutated=true
                }
            }
            mutated
        }
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
        val result=SyncHttp.post("/rest/v1/rpc/$name",body,token,compact=name=="vueo_sync_exchange")
        check(uid()==user) { "VUEO account changed." }
        return result
    }
    suspend fun syncNow()=performSync(pull=true,captureAll=true)
    private suspend fun performSync(pull: Boolean,captureAll: Boolean)=withContext(Dispatchers.IO) {
        syncGate.withLock {
            appliedKinds.clear()
            try {
                if(!ready || !connected()) return@withLock
                val user=uid(); val kinds=categories.keys.filter(::enabled).toSet()
                if(kinds.isEmpty()) { _state.value="Connected. All sync options are off."; changed(); return@withLock }
                if(repository==null) loadRepositories(user,force=true)
                val store=repository!!
                captureDirty(if(captureAll || initialCapture) kinds else null)
                initialCapture=false
                if(!pull && !store.hasPending(kinds) && (!enabled("trakt") || tokenPending.isEmpty())) return@withLock
                if(playerActive || android.os.SystemClock.elapsedRealtime()<pauseUntil) {
                    _state.value="Playback active. Changes saved locally; sync resumes on exit."; changed(); return@withLock
                }
                _state.value="Syncing…"; changed()
                // Each category owns its delta cursor and outbox, as in Nuvio's category repositories.
                if(pull) {
                    for(kind in kinds) {
                        var cursor=store.cursor(kind)
                        do {
                            if(!enabled(kind)) break
                            val before=linkedMapOf<String,String?>()
                            val reply=rpc("vueo_sync_exchange",JSONObject().put("p_kinds",JSONArray(listOf(kind)))
                                .put("p_after",cursor).put("p_limit",25),user)
                            val rows=reply.getJSONArray("rows")
                            if(rows.length()>0) store.transaction {
                                val apply=mutableListOf<JSONObject>()
                                for(i in 0 until rows.length()) {
                                    val row=CompactLibraryCodec.compactRecord(rows.getJSONObject(i))
                                    if(!enabled(kind) || row.optString("kind")!=kind || !adapter.accepts(row)) continue
                                    val key=row.getString("key"); val state=store.state(key)
                                    before[key]=state?.fingerprint
                                    if(state?.queued==true) continue
                                    if(row.getLong("revision")>(state?.revision ?: 0L)) {
                                        apply+=row
                                        store.observe(key,kind,state?.fingerprint,row.getLong("revision"))
                                    }
                                }
                                applyRemote(apply,before,setOf(kind))
                                store.setCursor(kind,reply.optString("cursor",store.cursor(kind)))
                            }
                            cursor=reply.optString("next")
                        } while(cursor.isNotBlank())
                    }
                    lastPullAt=android.os.SystemClock.elapsedRealtime()
                }
                var batches=0
                for(kind in kinds) {
                    while(enabled(kind) && batches<20) {
                        val batch=store.pendingBatch(kind)
                        if(batch.isEmpty()) break
                        val before=batch.associate { it.getString("key") to store.state(it.getString("key"))?.fingerprint }
                        val reply=rpc("vueo_sync_exchange",JSONObject().put("p_changes",JSONArray(batch)),user)
                        store.transaction {
                            val apply=mutableListOf<JSONObject>(); val acks=reply.getJSONArray("acks")
                            for(i in 0 until acks.length()) {
                                val ack=CompactLibraryCodec.compactRecord(acks.getJSONObject(i))
                                val sent=batch.firstOrNull { it.getString("key")==ack.getString("key") } ?: continue
                                if(store.acknowledge(ack,sent) && !ack.optBoolean("accepted")) apply+=ack
                            }
                            if(enabled(kind)) applyRemote(apply,before,setOf(kind))
                        }
                        batches+=1
                    }
                }
                if(enabled("trakt")) {
                    for(profile in ProfileStore(context).profiles().map { it.id }) {
                        if(pull || synchronized(lock) { profile in tokenPending }) reconcileTrakt(profile,user)
                    }
                }
                val more=store.hasPending(kinds) || (enabled("trakt") && tokenPending.isNotEmpty())
                _state.value=if(more) "Changes saved locally. More sync pending." else "Synced"
                changed()
                if(more) requestSync(1_000,pull=false)
            } finally {
                if(appliedKinds.any { it in setOf("favorites","history","progress") }) _dataRevision.value+=1
                if("profiles" in appliedKinds) _profileRevision.value+=1
                if("addons" in appliedKinds) _configurationRevision.value+=1
                if("integrations" in appliedKinds) _integrationRevision.value+=1
                if("trakt" in appliedKinds) com.vueo.shared.core.trakt.TraktService.get(context).onVueoSyncApplied()
                appliedKinds.clear()
            }
        }
    }
    private fun applyRemote(rows: List<JSONObject>,before: Map<String,String?>,selected: Set<String>) {
        if(rows.isEmpty()) return
        val store=repository!!
        appliedKinds+=adapter.apply(rows,before)
        val after=adapter.snapshot(selected)
        val known=store.states(selected)
        (after.keys+known.filterValues { it.fingerprint!=null }.keys).forEach { key ->
            val now=after[key]; val old=known[key]
            val hash=syncFingerprint(now?.optJSONObject("value"))
            val remote=rows.firstOrNull { it.optString("key")==key }
            val matches=if(remote?.optBoolean("deleted")==true) now==null else
                remote!=null && hash==syncFingerprint(remote.optJSONObject("value"))
            if(matches) store.observe(key,remote!!.getString("kind"),hash)
            else if(hash!=old?.fingerprint) {
                val row=now ?: JSONObject().put("key",key).put("kind",old!!.kind).put("value",JSONObject())
                if(enabled(row.getString("kind"))) store.enqueue(row,now==null)
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
                    if(repository==null) loadRepositories(owner,force=true)
                    tokenPending[profile]=JSONObject().put("delete",tokens==null).put("value",tokens ?: JSONObject.NULL)
                    repository!!.saveToken(profile,tokenPending[profile])
                }
            }
            requestSync(pull=false)
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
                if(trakt.connected(profile)) { trakt.clear(profile,publishCloud=false); changed(); com.vueo.shared.core.trakt.TraktService.get(context).onVueoSessionImported(profile) }
            } else {
                val remote=result.getJSONObject("value")
                if(trakt.read(profile)?.toString()!=remote.toString()) { trakt.save(profile,remote,publishCloud=false); changed(); com.vueo.shared.core.trakt.TraktService.get(context).onVueoSessionImported(profile) }
            }
            synchronized(lock) { if(intent!=null && tokenPending[profile]===intent) { tokenPending.remove(profile); repository!!.saveToken(profile,null) } }
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
