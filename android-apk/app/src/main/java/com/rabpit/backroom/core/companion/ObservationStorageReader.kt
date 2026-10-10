package com.rabpit.backroom.core.companion

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Collections

/**
 * Native SQLite read boundary. Query is scoped by slot and owner before LIMIT.
 * Only committed, manifest-listed, event-backed public projections are returned.
 * Never reads a native event record, GM context, or another actor's observations.
 */
internal object ObservationStorageReader {
  private const val MAX_ROWS = 256
  private const val SELECT = """
SELECT o.slot_id,o.observation_id,o.actor_id,o.event_id,o.created_turn_id,
       o.committed_revision,o.access_kind,o.source_actor_id,o.certainty,
       o.scene_id,o.policy_version,o.public_payload,o.observation_digest,
       m.observation_digest,e.event_id,t.committed_revision
FROM actor_observation o
LEFT JOIN observation_manifest m ON m.slot_id=o.slot_id
  AND m.observation_id=o.observation_id AND m.turn_id=o.created_turn_id
  AND m.committed_revision=o.committed_revision
LEFT JOIN native_event e ON e.event_id=o.event_id
  AND e.turn_id=o.created_turn_id AND e.revision=o.committed_revision
LEFT JOIN turn_control t ON t.turn_id=o.created_turn_id
  AND t.committed_revision=o.committed_revision AND t.phase='COMMITTED'
WHERE o.slot_id=? AND o.actor_id=?
ORDER BY o.committed_revision ASC,o.created_turn_id ASC,o.observation_id ASC
LIMIT ?
"""

  @JvmStatic @Throws(IOException::class)
  fun read(db: SQLiteDatabase, slotId: String, ownerActorId: String,
           currentRevision: Long, limit: Int): List<ObservationReader.Row> {
    if (!db.inTransaction() || limit !in 1..MAX_ROWS || currentRevision < 0 ||
        !slotId.matches(Regex("[0-9a-f]{32}")) ||
        !ownerActorId.matches(Regex("[A-Za-z0-9_.:-]{1,160}")))
      throw IOException("observation_read_scope_invalid")
    val result=ArrayList<ObservationReader.Row>()
    try {
      db.rawQuery(SELECT,arrayOf(slotId,ownerActorId,limit.toString())).use { c ->
        while(c.moveToNext()) {
          val revision=c.getLong(5)
          val turn=c.getString(4)
          val id=c.getString(1)
          val event=c.getString(3)
          val access=c.getString(6)
          val certainty=c.getString(8)
          val scene=c.getString(9)
          val policy=c.getString(10)
          val payload=c.getString(11)
          val digest=c.getString(12)
          if(c.getString(0)!=slotId || c.getString(2)!=ownerActorId ||
             revision !in 1..currentRevision || c.isNull(13) || c.isNull(14) || c.isNull(15) ||
             digest!=c.getString(13) || event!=c.getString(14) || revision!=c.getLong(15))
            throw IOException("observation_read_provenance_invalid")
          if(access !in setOf("SEEN","HEARD","TOLD","INFERRED") ||
             certainty !in setOf("CERTAIN","PLAUSIBLE","UNCERTAIN") ||
             policy!=CompanionExposurePolicy.VERSION)
            throw IOException("observation_read_contract_invalid")
          val json=JSONObject(payload)
          if(CompanionWaitCapture.canonical(json)!=payload)
            throw IOException("observation_read_payload_noncanonical")
          val expected=CompanionDigests.sha256(CompanionWaitCapture.canonical(
            JSONArray(listOf(slotId,id,ownerActorId,event,access,certainty,turn,revision,scene,policy,json))))
          if(digest!=expected) throw IOException("observation_read_digest_mismatch")
          result.add(ObservationReader.Row(id,ownerActorId,event,turn,revision,access,digest,slotId,
            certainty,scene,policy,payload,c.getString(7)))
        }
      }
    } catch(e: RuntimeException) {
      throw IOException("observation_read_invalid",e)
    }
    return Collections.unmodifiableList(result)
  }
}
