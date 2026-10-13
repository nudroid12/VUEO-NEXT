package com.vueo.shared.core.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import com.vueo.shared.core.storage.CompactLibraryCodec
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal fun syncFingerprint(value: JSONObject?): String? = value?.let {
    MessageDigest.getInstance("SHA-256").digest(it.toString().toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
}

internal data class SyncEntityState(val key: String,val kind: String,val fingerprint: String?,
    val revision: Long,val generation: Long,val queued: Boolean)

/** Account-scoped category state and durable per-entity outbox. No full metadata baseline/journal. */
internal class SyncEntityRepository(context: Context,user: String) {
    private val crypto=SyncRecordCipher()
    private val hash=MessageDigest.getInstance("SHA-256").digest(user.toByteArray()).joinToString("") { "%02x".format(it) }
    private val db=SQLiteDatabase.openOrCreateDatabase(File(context.noBackupFilesDir,"vueo-sync-$hash.db"),null)
    init {
        db.execSQL("CREATE TABLE IF NOT EXISTS entities (entity_key TEXT PRIMARY KEY, kind TEXT NOT NULL, fingerprint TEXT, revision INTEGER NOT NULL DEFAULT 0, generation INTEGER NOT NULL DEFAULT 0, payload BLOB)")
        db.execSQL("CREATE INDEX IF NOT EXISTS entities_kind ON entities(kind)")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tokens (profile TEXT PRIMARY KEY, payload BLOB NOT NULL)")
    }
    fun close()=db.close()
    fun <T> transaction(block: ()->T): T {
        db.beginTransaction()
        try { val result=block(); db.setTransactionSuccessful(); return result }
        finally { db.endTransaction() }
    }
    private fun meta(name: String,fallback: String="0"): String =
        db.rawQuery("SELECT value FROM meta WHERE name=?",arrayOf(name)).use { if(it.moveToFirst()) it.getString(0) else fallback }
    private fun putMeta(name: String,value: String) {
        db.insertWithOnConflict("meta",null,ContentValues().apply { put("name",name); put("value",value) },SQLiteDatabase.CONFLICT_REPLACE)
            .also { check(it!=-1L) { "Unable to save sync metadata" } }
    }
    fun cursor(kind: String)=meta("cursor:$kind")
    fun setCursor(kind: String,value: String) { putMeta("cursor:$kind",value) }
    fun resetCursor(kind: String) { setCursor(kind,"0") }
    fun states(kinds: Set<String>): Map<String,SyncEntityState> {
        if(kinds.isEmpty()) return emptyMap()
        val placeholders=kinds.joinToString(",") { "?" }
        return db.rawQuery("SELECT entity_key,kind,fingerprint,revision,generation,payload IS NOT NULL FROM entities WHERE kind IN ($placeholders)",kinds.toTypedArray()).use { rows ->
            buildMap { while(rows.moveToNext()) {
                val state=SyncEntityState(rows.getString(0),rows.getString(1),if(rows.isNull(2)) null else rows.getString(2),rows.getLong(3),rows.getLong(4),rows.getInt(5)!=0)
                put(state.key,state)
            } }
        }
    }
    fun state(key: String): SyncEntityState? = db.rawQuery(
        "SELECT entity_key,kind,fingerprint,revision,generation,payload IS NOT NULL FROM entities WHERE entity_key=?",arrayOf(key)).use { rows ->
        if(!rows.moveToFirst()) null else SyncEntityState(rows.getString(0),rows.getString(1),if(rows.isNull(2)) null else rows.getString(2),rows.getLong(3),rows.getLong(4),rows.getInt(5)!=0)
    }
    fun fingerprint(value: JSONObject?)=syncFingerprint(value)
    private fun ensure(key: String,kind: String) {
        db.insertWithOnConflict("entities",null,ContentValues().apply { put("entity_key",key); put("kind",kind) },SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun observe(key: String,kind: String,fingerprint: String?,revision: Long?=null) {
        ensure(key,kind)
        val values=ContentValues().apply {
            if(fingerprint==null) putNull("fingerprint") else put("fingerprint",fingerprint)
            revision?.let { put("revision",it) }
        }
        db.update("entities",values,"entity_key=?",arrayOf(key))
    }
    fun enqueue(raw: JSONObject,deleted: Boolean) {
        val row=CompactLibraryCodec.compactRecord(raw)
        val key=row.getString("key"); val kind=row.getString("kind")
        val generation=meta("sequence").toLong()+1
        val payload=JSONObject(row.toString()).put("deleted",deleted)
            .put("revision",state(key)?.revision ?: 0L).put("generation",generation)
        val bytes=payload.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=512_000) { "One sync item is too large; local data has been retained" }
        ensure(key,kind)
        val values=ContentValues().apply {
            put("generation",generation); put("payload",crypto.encrypt(bytes))
            if(deleted) putNull("fingerprint") else put("fingerprint",fingerprint(row.optJSONObject("value")))
        }
        check(db.update("entities",values,"entity_key=?",arrayOf(key))==1)
        putMeta("sequence",generation.toString())
    }
    fun hasPending(kinds: Set<String>): Boolean {
        if(kinds.isEmpty()) return false
        val placeholders=kinds.joinToString(",") { "?" }
        return db.rawQuery("SELECT 1 FROM entities WHERE kind IN ($placeholders) AND payload IS NOT NULL LIMIT 1",kinds.toTypedArray()).use { it.moveToFirst() }
    }
    fun clearPendingExcept(kinds: Set<String>,resetKind: String?=null) {
        states(VueoSyncConfig.categories.keys).values.filter { it.queued && (it.kind !in kinds || it.kind==resetKind) }.forEach {
            db.update("entities",ContentValues().apply { putNull("payload") },"entity_key=?",arrayOf(it.key))
        }
    }
    fun restrictEntities(accepts: (JSONObject) -> Boolean) {
        if (meta("credentials_only_v1") == "true") return
        val rejected = states(VueoSyncConfig.categories.keys).values.filter { state ->
            !accepts(JSONObject().put("key", state.key).put("kind", state.kind).put("deleted", true))
        }
        rejected.forEach { db.delete("entities", "entity_key=?", arrayOf(it.key)) }
        putMeta("credentials_only_v1", "true")
    }
    /** Bounded decrypt/load: at most 25 changed records and 750 KB per exchange. */
    fun pendingBatch(kind: String): List<JSONObject> = db.rawQuery(
        "SELECT payload FROM entities WHERE kind=? AND payload IS NOT NULL ORDER BY generation LIMIT 25",arrayOf(kind)).use { rows ->
        val result=mutableListOf<JSONObject>(); var bytes=0
        while(rows.moveToNext()) {
            val plain=crypto.decrypt(rows.getBlob(0))
            if(bytes+plain.size>750_000 && result.isNotEmpty()) break
            bytes+=plain.size; result+=JSONObject(String(plain,Charsets.UTF_8))
        }
        result
    }
    /** Only the sent generation is acknowledged. A newer local edit retains its outbox slot. */
    fun acknowledge(ack: JSONObject,sent: JSONObject): Boolean {
        val key=ack.getString("key"); val current=state(key) ?: return false
        val exact=current.queued && current.generation==sent.getLong("generation")
        val values=ContentValues().apply { put("revision",ack.getLong("revision")); if(exact) putNull("payload") }
        db.update("entities",values,"entity_key=?",arrayOf(key))
        if(current.queued && !exact) {
            db.rawQuery("SELECT payload FROM entities WHERE entity_key=?",arrayOf(key)).use { rows ->
                if(rows.moveToFirst()) {
                    val queued=JSONObject(String(crypto.decrypt(rows.getBlob(0)),Charsets.UTF_8)).put("revision",ack.getLong("revision"))
                    db.update("entities",ContentValues().apply { put("payload",crypto.encrypt(queued.toString().toByteArray(Charsets.UTF_8))) },"entity_key=?",arrayOf(key))
                }
            }
        }
        return exact
    }
    fun tokens(): LinkedHashMap<String,JSONObject> = db.rawQuery("SELECT profile,payload FROM tokens",null).use { rows ->
        linkedMapOf<String,JSONObject>().apply { while(rows.moveToNext()) put(rows.getString(0),JSONObject(String(crypto.decrypt(rows.getBlob(1)),Charsets.UTF_8))) }
    }
    fun saveToken(profile: String,value: JSONObject?) {
        if(value==null) db.delete("tokens","profile=?",arrayOf(profile))
        else check(db.insertWithOnConflict("tokens",null,ContentValues().apply {
            put("profile",profile); put("payload",crypto.encrypt(value.toString().toByteArray(Charsets.UTF_8)))
        },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
    }
    fun clearTokens() { db.delete("tokens",null,null) }

    /** Streaming migration: retains tombstones/token intents without materializing a giant journal. */
    fun migrateLegacy(context: Context,accept: (JSONObject)->Boolean) {
        if(meta("migrated","false")=="true") return
        val legacy=SyncSecureFile(context,hash)
        transaction {
            val legacyRevisions=linkedMapOf<String,Long>()
            legacy.readStreaming { reader ->
                reader.beginObject()
                while(reader.hasNext()) when(reader.nextName()) {
                    "baseline" -> readMap(reader) { key,row ->
                        val compact=CompactLibraryCodec.compactRecord(row)
                        if(accept(compact)) observe(key,compact.getString("kind"),fingerprint(compact.optJSONObject("value")))
                    }
                    "pending" -> readMap(reader) { key,row -> if(accept(row)) {
                        observe(key,row.getString("kind"),state(key)?.fingerprint,row.optLong("revision"))
                        enqueue(row,row.optBoolean("deleted"))
                    } }
                    "revisions" -> {
                        reader.beginObject()
                        while(reader.hasNext()) {
                            val key=reader.nextName(); val revision=reader.nextLong()
                            legacyRevisions[key]=revision
                        }
                        reader.endObject()
                    }
                    "tokens" -> readMap(reader) { profile,row -> saveToken(profile,row) }
                    // Restart delta bootstrap; queued local changes still take precedence and use CAS.
                    else -> reader.skipValue()
                }
                reader.endObject()
            }
            legacyRevisions.forEach { (key,revision) ->
                // A queued mutation retains the exact expected revision it was captured against.
                if(state(key)?.queued!=true) {
                    db.update("entities",ContentValues().apply { put("revision",revision) },"entity_key=?",arrayOf(key))
                }
            }
            VueoSyncConfig.categories.keys.forEach(::resetCursor); putMeta("migrated","true")
        }
        legacy.delete()
    }
    private fun readMap(reader: JsonReader,consume: (String,JSONObject)->Unit) {
        reader.beginObject()
        while(reader.hasNext()) {
            val key=reader.nextName()
            consume(key,CompactLibraryCodec.readValue(reader) as JSONObject)
        }
        reader.endObject()
    }
}
