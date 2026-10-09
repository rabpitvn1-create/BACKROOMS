package com.rabpit.backroom.core.companion

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * One native transaction stores immutable self-canon pins and empty brain for both actors.
 * Verified production entry points re-read bundled source bytes at creation AND opening.
 */
internal object GenesisPinsStorage {
  private val actors = listOf("cao_minh","luc_tram")
  /** For package-private SQLite fixtures only; descriptors are not source verification. */
  @JvmStatic fun fixtureRecords(): List<BrainGenesis.GenesisRecord> =
    actors.map { BrainGenesis.genesis(CompanionCanonPersonaRegistry.descriptor(it)) }

  @JvmStatic fun verifiedRecords(context: Context): List<BrainGenesis.GenesisRecord> =
    actors.map { actor ->
      val persona=CompanionCanonPersonaRegistry.load(actor) { path ->
        context.assets.open(path).use { it.readBytes() }
      }
      BrainGenesis.genesis(persona)
    }

  private fun brainJson(record: BrainGenesis.GenesisRecord): String {
    val brain=record.brain
    require(brain.actorId==record.pins.actorId && brain.beliefs.isEmpty() &&
      brain.goals.isEmpty() && brain.relationships.isEmpty() &&
      brain.memoryObservationIds.isEmpty() && brain.mood==BrainGenesis.InitialBrain.Mood.NEUTRAL) {
      "genesis_nonempty_brain"
    }
    return CompanionWaitCapture.canonical(JSONObject()
      .put("actorId",brain.actorId).put("ruleVersion",record.pins.ruleVersion)
      .put("beliefs",JSONArray()).put("goals",JSONArray())
      .put("relationships",JSONArray()).put("memoryObservationIds",JSONArray())
      .put("mood","NEUTRAL"))
  }

  @JvmStatic fun seed(db: SQLiteDatabase,slotId: String,records: List<BrainGenesis.GenesisRecord>) {
    require(db.inTransaction()) { "genesis_requires_transaction" }
    require(records.map { it.pins.actorId }==actors) { "genesis_actor_set_invalid" }
    records.forEach { record ->
      // Validate metadata against the immutable registry without treating actor membership
      // as proof of presence in a scene.
      BrainGenesis.verifyOnLoad(record.pins,CompanionCanonPersonaRegistry.descriptor(record.pins.actorId))
      val p=record.pins
      val row=ContentValues().apply {
        put("slot_id",slotId);put("actor_id",p.actorId)
        put("knowledge_namespace",p.knowledgeNamespace);put("persona_source_path",p.personaSourcePath)
        put("persona_revision",p.personaRevision);put("persona_sha256",p.personaSha256)
        put("rule_version",p.ruleVersion);put("schema_version",p.schemaVersion)
        put("policy_version",p.policyVersion)
      }
      db.insertOrThrow("genesis_pins",null,row)
      val json=brainJson(record)
      db.insertOrThrow("initial_brain",null,ContentValues().apply {
        put("slot_id",slotId);put("actor_id",p.actorId)
        put("state_json",json);put("state_digest",CompanionDigests.sha256(json))
      })
    }
  }

  @JvmStatic @Throws(IOException::class)
  fun verify(db: SQLiteDatabase,slotId: String,records: List<BrainGenesis.GenesisRecord>) {
    if (!db.inTransaction() || records.map { it.pins.actorId }!=actors)
      throw IOException("genesis_verify_scope_invalid")
    val pins=ArrayList<List<String>>()
    db.rawQuery("""SELECT actor_id,knowledge_namespace,persona_source_path,persona_revision,
      persona_sha256,rule_version,schema_version,policy_version FROM genesis_pins
      WHERE slot_id=? ORDER BY actor_id""",arrayOf(slotId)).use { c ->
      while(c.moveToNext()) pins.add((0..7).map { c.getString(it) })
    }
    if(pins.size!=actors.size) throw IOException("genesis_pins_incomplete")
    val byActor=records.associateBy { it.pins.actorId }
    for(row in pins) {
      val record=byActor[row[0]] ?: throw IOException("genesis_actor_unknown")
      val p=record.pins
      val expected=listOf(p.actorId,p.knowledgeNamespace,p.personaSourcePath,p.personaRevision,
        p.personaSha256,p.ruleVersion,p.schemaVersion.toString(),p.policyVersion)
      if(row!=expected) throw IOException("genesis_pins_mismatch")
      BrainGenesis.verifyOnLoad(p,CompanionCanonPersonaRegistry.descriptor(p.actorId))
      val expectedBrain=brainJson(record)
      db.rawQuery("""SELECT state_json,state_digest FROM initial_brain
        WHERE slot_id=? AND actor_id=?""",arrayOf(slotId,p.actorId)).use { c ->
        if(!c.moveToFirst() || c.getString(0)!=expectedBrain ||
          c.getString(1)!=CompanionDigests.sha256(expectedBrain) || c.moveToNext())
          throw IOException("genesis_brain_mismatch")
      }
    }
    db.rawQuery("SELECT COUNT(*) FROM initial_brain WHERE slot_id=?",arrayOf(slotId)).use { c ->
      if(!c.moveToFirst() || c.getInt(0)!=actors.size) throw IOException("genesis_brain_incomplete")
    }
  }
}
