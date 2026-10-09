package com.rabpit.backroom.core.companion

import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONObject

/** Schema/publisher specimen proof on Android; not a wired Core/brain commit. */
internal object CompanionMuseStorageFixtures {
  @JvmStatic fun schemaAndPublication(directory: File) {
    val store = CompanionSlotStore.createIn(directory, byteArrayOf(1), "schema-test")
    val slot = store.slotId
    store.close()
    val file = File(directory, "slot-$slot.db")
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
      db.setForeignKeyConstraintsEnabled(true)
      db.beginTransaction()
      try {
        (ObservationSchema.createStatements() + BrainGenesisSchema.createStatements() +
          EpisodicMemorySchema.createStatements()).forEach { db.execSQL(it) }
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
      rejects { db.execSQL("INSERT INTO actor_memory VALUES(?, 'foreign','luc_tram','o','t',1,'topic','summary','NATIVE','ORDINARY','ACTIVE',NULL)",arrayOf(slot)) }
      db.execSQL("INSERT INTO genesis_pins VALUES(1,?,'cao_minh','CHAR.KAI','source','R17',?,'rule_table.v1',1,'companion_exposure.v1')",arrayOf(slot,"0".repeat(64)))
      rejects { db.execSQL("UPDATE genesis_pins SET persona_revision='R18'") }
      rejects { db.execSQL("DELETE FROM genesis_pins") }
    }
    check(file.exists())
    SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
      check(count(db,"actor_observation") == 1 && count(db,"actor_memory") == 1)
      rejects { db.execSQL("DELETE FROM actor_observation") }
    }
  }
  private fun count(db: SQLiteDatabase, table: String): Int =
    db.rawQuery("SELECT COUNT(*) FROM $table",null).use { check(it.moveToFirst()); it.getInt(0) }
  private fun rejects(operation: () -> Unit) {
    try { operation() } catch (_: RuntimeException) { return }
    error("expected rejection")
  }
}
