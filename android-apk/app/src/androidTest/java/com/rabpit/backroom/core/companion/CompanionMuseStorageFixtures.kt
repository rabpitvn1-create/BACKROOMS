package com.rabpit.backroom.core.companion

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONObject

/** Schema/publisher specimen proof on Android; not a wired Core/brain commit. */
internal object CompanionMuseStorageFixtures {
  @JvmStatic fun schemaAndPublication(directory: File) {
    val store = CompanionSlotStore.createIn(directory, byteArrayOf(1), "schema-test")
    val slot = store.slotId
    store.close()
    // Native reopen must recognize exactly this installed schema, not create it on load.
    CompanionSlotStore.openIn(directory,slot,"schema-test").use { check(it.currentRevision() == 0L) }
    val file = File(directory, "slot-$slot.db")
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
      db.setForeignKeyConstraintsEnabled(true)
      db.beginTransaction()
      try {
        // Production fresh-slot creation now installs the observation schema itself.
        // Memory schema is installed by the real slot factory, not the specimen.
        db.execSQL("INSERT INTO turn_control(turn_id,active_slot,expected_revision,phase,record,committed_revision) VALUES('t',1,0,'COMMITTED',X'01',1)")
        db.execSQL("INSERT INTO native_event(event_id,turn_id,revision,ordinal,type,record,digest) VALUES('e','t',1,0,'WAIT_COMPLETED','{}','d')")
        db.setTransactionSuccessful()
      } finally { db.endTransaction() }
      val payload = JSONObject().put("actor", "cao_minh").put("minutes",30).put("location","scene")
      fun candidate(p: JSONObject = payload) = ObservationCandidate("o", "cao_minh", "e",
        ObservationCandidate.AccessKind.SEEN, ObservationCandidate.Certainty.PLAUSIBLE,
        slot,"t",1,"scene",CompanionExposurePolicy.VERSION,p)
      fun publish(c: ObservationCandidate, fault: ObservationPublisher.Fault = ObservationPublisher.Fault {}) {
        db.beginTransaction()
        try {
          ObservationPublisher.publish(db,slot,"t",1,listOf(c),fault)
          db.setTransactionSuccessful()
        } finally { db.endTransaction() }
      }
      for (point in listOf("after_observation_write","after_manifest_write")) {
        rejects { publish(candidate(), ObservationPublisher.Fault { if (it == point) error("fault") }) }
        check(count(db,"actor_observation") == 0 && count(db,"observation_manifest") == 0)
      }
      publish(candidate()); publish(candidate())
      check(count(db,"actor_observation") == 1 && count(db,"observation_manifest") == 1)
      db.beginTransaction()
      try {
        val visible = ObservationStorageReader.read(db,slot,"cao_minh",1,10)
        check(visible.size==1 && visible.single().ownerActorId=="cao_minh")
        check(visible.single().sourceEventId=="e" && visible.single().accessKind=="SEEN")
        check(visible.single().publicPayloadJson==candidate().publicPayloadJson)
        check(ObservationStorageReader.read(db,slot,"luc_tram",1,10).isEmpty())
        check(ObservationStorageReader.read(db,"0".repeat(32),"cao_minh",1,10).isEmpty())
        try { ObservationStorageReader.read(db,slot,"cao_minh",0,10); error("accepted future read") }
        catch (_: java.io.IOException) { }
      } finally { db.endTransaction() }

      rejects { publish(candidate(JSONObject(payload.toString()).put("location","other"))) }
      check(count(db,"actor_observation") == 1)
      rejects { db.execSQL("UPDATE actor_observation SET certainty='CERTAIN'") }
      rejects { db.execSQL("DELETE FROM observation_manifest") }
      // Correct event id with wrong receipt must not pass the composite FK.
      rejects { db.execSQL("INSERT INTO actor_observation SELECT slot_id,'other',actor_id,event_id,'wrong',committed_revision,access_kind,source_actor_id,certainty,scene_id,policy_version,public_payload,observation_digest FROM actor_observation") }
      rejects { db.execSQL("INSERT INTO observation_manifest VALUES(?, 't',1,1,'o',?)",arrayOf(slot,"0".repeat(64))) }
      db.execSQL("INSERT INTO actor_memory VALUES(?, 'm','cao_minh','o','t',1,'topic','summary','NATIVE','ORDINARY','ACTIVE',NULL)",arrayOf(slot))
      rejects { db.execSQL("UPDATE actor_memory SET subjective_summary='edited'") }
      rejects { db.execSQL("DELETE FROM actor_memory") }
      db.execSQL("INSERT INTO memory_manifest VALUES(?,'t',1,0,'m','cao_minh')",arrayOf(slot))
      rejects { db.execSQL("UPDATE memory_manifest SET ordinal=1") }
      rejects { db.execSQL("DELETE FROM memory_manifest") }
      rejects { db.execSQL("INSERT INTO actor_memory VALUES(?, 'foreign','luc_tram','o','t',1,'topic','summary','NATIVE','ORDINARY','ACTIVE',NULL)",arrayOf(slot)) }
      check(count(db,"genesis_pins")==2 && count(db,"initial_brain")==2)
      rejects { db.execSQL("UPDATE genesis_pins SET persona_revision='R18'") }
      rejects { db.execSQL("DELETE FROM genesis_pins") }
      rejects { db.execSQL("UPDATE initial_brain SET state_json='{}'") }
      rejects { db.execSQL("DELETE FROM initial_brain") }
    }
    check(file.exists())
    SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
      check(count(db,"actor_observation") == 1 && count(db,"actor_memory") == 1)
      rejects { db.execSQL("DELETE FROM actor_observation") }
    }
    val corrupt = CompanionSlotStore.createIn(directory, byteArrayOf(1), "schema-test")
    val corruptSlot = corrupt.slotId
    corrupt.close()
    val corruptFile = File(directory, "slot-$corruptSlot.db")
    SQLiteDatabase.openDatabase(corruptFile.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
      db.execSQL("DROP TRIGGER observation_manifest_no_delete")
    }
    try {
      CompanionSlotStore.openIn(directory,corruptSlot,"schema-test").close()
      error("missing immutability trigger accepted")
    } catch (_: java.io.IOException) { /* fail closed, never recreate or migrate */ }
    check(corruptFile.exists())
  }
  /** A failed second actor pin must undo the already-inserted first actor pin. */
  private fun genesisAtomicRollback() {
    SQLiteDatabase.create(null).use { db ->
      db.setForeignKeyConstraintsEnabled(true)
      db.execSQL("CREATE TABLE slot_meta(slot_id TEXT PRIMARY KEY)")
      BrainGenesisSchema.createStatements().forEach { db.execSQL(it) }
      val slot="f".repeat(32)
      db.execSQL("INSERT INTO slot_meta(slot_id) VALUES(?)",arrayOf(slot))
      val records=GenesisPinsStorage.fixtureRecords()
      val bad=records[1].copy(pins=records[1].pins.copy(personaSha256="0".repeat(64)))
      db.beginTransaction()
      try {
        try { GenesisPinsStorage.seed(db,slot,listOf(records[0],bad)); error("invalid source pin accepted") }
        catch (_: java.io.IOException) { /* reject before committing the second actor */ }
      } finally { db.endTransaction() }
      check(count(db,"genesis_pins")==0 && count(db,"initial_brain")==0)
    }
  }

  /** Uses real packaged R17/R05 canon bytes, not a caller-supplied revision/hash. */
  @JvmStatic fun verifiedGenesis(context: Context) {
    genesisAtomicRollback()
    val store=CompanionSlotStore.create(context,byteArrayOf(1),"genesis-verified")
    val id=store.slotId
    store.close()
    CompanionSlotStore.open(context,id,"genesis-verified").use { reopened ->
      check(reopened.currentRevision()==0L)
    }
    val file=File(context.getDir("companion_slots_v4",Context.MODE_PRIVATE),"slot-$id.db")
    SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
      db.rawQuery("SELECT actor_id,persona_revision,knowledge_namespace FROM genesis_pins ORDER BY actor_id",null).use { c ->
        check(c.moveToNext() && c.getString(0)=="cao_minh" && c.getString(1)=="R17" && c.getString(2)=="CHAR.KAI")
        check(c.moveToNext() && c.getString(0)=="luc_tram" && c.getString(1)=="R05" && c.getString(2)=="CHAR.LUC_TRAM")
        check(!c.moveToNext())
      }
      check(count(db,"initial_brain")==2 && count(db,"actor_observation")==0)
      rejects { db.execSQL("UPDATE genesis_pins SET persona_revision='R99'") }
      rejects { db.execSQL("DELETE FROM initial_brain") }
      db.execSQL("DROP TRIGGER genesis_pins_no_update")
    }
    try { CompanionSlotStore.open(context,id,"genesis-verified").close(); error("missing genesis guard accepted") }
    catch (_: java.io.IOException) { }
    check(file.exists())
  }

  private fun count(db: SQLiteDatabase, table: String): Int =
    db.rawQuery("SELECT COUNT(*) FROM $table",null).use { check(it.moveToFirst()); it.getInt(0) }
  private fun rejects(operation: () -> Unit) {
    try { operation() } catch (_: RuntimeException) { return }
    error("expected rejection")
  }
}
