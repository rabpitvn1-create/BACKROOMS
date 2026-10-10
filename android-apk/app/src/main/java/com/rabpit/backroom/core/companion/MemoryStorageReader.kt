package com.rabpit.backroom.core.companion

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.IOException
import java.util.Collections

/** Actor-private native SQLite history. Never reads GM event payloads. */
internal object MemoryStorageReader {
  private const val MAX_HISTORY = 4096
  private const val SQL = """
SELECT m.memory_id,m.observation_id,m.created_turn_id,m.committed_revision,
 m.subjective_summary,m.topic,m.salience,m.supersedes_memory_id,
 o.event_id,o.access_kind,o.certainty,o.scene_id,o.policy_version,
 o.public_payload,o.observation_digest,o.created_turn_id,o.committed_revision,
 mm.memory_id,om.observation_id,t.turn_id,m.interpretation_source,m.status
FROM actor_memory m
LEFT JOIN actor_observation o ON o.slot_id=m.slot_id
 AND o.observation_id=m.observation_id AND o.actor_id=m.actor_id
LEFT JOIN memory_manifest mm ON mm.slot_id=m.slot_id AND mm.memory_id=m.memory_id
 AND mm.actor_id=m.actor_id AND mm.turn_id=m.created_turn_id
 AND mm.committed_revision=m.committed_revision
LEFT JOIN observation_manifest om ON om.slot_id=o.slot_id
 AND om.observation_id=o.observation_id AND om.turn_id=o.created_turn_id
 AND om.committed_revision=o.committed_revision
LEFT JOIN turn_control t ON t.turn_id=m.created_turn_id
 AND t.committed_revision=m.committed_revision AND t.phase='COMMITTED'
WHERE m.slot_id=? AND m.actor_id=?
ORDER BY m.committed_revision ASC,m.memory_id ASC LIMIT ?
"""

  @JvmStatic @Throws(IOException::class)
  fun read(db: SQLiteDatabase,slotId:String,actorId:String,revision:Long):List<MemoryRetrieval.MemoryView> {
    if(!db.inTransaction() || revision<0 || !slotId.matches(Regex("[0-9a-f]{32}")) ||
      !actorId.matches(Regex("[A-Za-z0-9_.:-]{1,160}")))
      throw IOException("memory_read_scope_invalid")
    val result=ArrayList<MemoryRetrieval.MemoryView>()
    try {
      db.rawQuery(SQL,arrayOf(slotId,actorId,(MAX_HISTORY+1).toString())).use { c ->
        while(c.moveToNext()) {
          if(result.size>=MAX_HISTORY) throw IOException("memory_history_bound")
          if((17..19).any { c.isNull(it) } || (8..16).any { c.isNull(it) })
            throw IOException("memory_provenance_missing")
          val id=c.getString(0)
          val obs=c.getString(1)
          val turn=c.getString(2)
          val rev=c.getLong(3)
          val summary=c.getString(4)
          val topic=c.getString(5)
          val salience=c.getString(6)
          val parent=if(c.isNull(7)) null else c.getString(7)
          val event=c.getString(8)
          val access=c.getString(9)
          val certainty=c.getString(10)
          val scene=c.getString(11)
          val policy=c.getString(12)
          val payload=c.getString(13)
          val digest=c.getString(14)
          if(rev !in 1..revision || turn!=c.getString(15) || rev!=c.getLong(16) ||
            id!=c.getString(17) || obs!=c.getString(18) || turn!=c.getString(19) ||
            c.getString(20)!="NATIVE" || c.getString(21)!="ACTIVE" ||
            salience!="ORDINARY" || policy!=CompanionExposurePolicy.VERSION ||
            !topic.matches(Regex("[A-Za-z0-9_.:-]{1,64}")))
            throw IOException("memory_native_provenance_invalid")
          val candidate=ObservationCandidate(obs,actorId,event,
            ObservationCandidate.AccessKind.valueOf(access),
            ObservationCandidate.Certainty.valueOf(certainty),
            slotId,turn,rev,scene,policy,JSONObject(payload))
          if(candidate.publicPayloadJson!=payload ||
            ObservationPublisherDigest.of(candidate)!=digest ||
            EpisodicMemory.summarize(candidate)!=summary)
            throw IOException("memory_public_template_mismatch")
          result.add(MemoryRetrieval.MemoryView(id,slotId,actorId,obs,turn,rev,summary,
            topic,EpisodicMemory.Salience.ORDINARY,parent,scene,setOf(actorId)))
        }
      }
      // Validation of missing parents, branching and cross-revision corrections
      // happens on the FULL owned history, before ranking/budget.
      val old=EpisodicMemory.latest(result.map { r ->
        EpisodicMemory.MemoryRecord(r.memoryId,r.slotId,r.ownerActorId,r.observationId,
          r.createdTurnId,r.committedRevision,r.summary,r.topic,r.salience,
          EpisodicMemory.InterpretationSource.NATIVE,EpisodicMemory.Status.ACTIVE,r.supersedesMemoryId)
      })
      check(old.size<=result.size)
    } catch(e: RuntimeException) { throw IOException("memory_read_invalid",e) }
    return Collections.unmodifiableList(result)
  }
}
