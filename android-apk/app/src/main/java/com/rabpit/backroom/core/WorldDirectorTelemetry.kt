package com.rabpit.backroom.core

import android.content.Context
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Privacy-safe, local-only proposal telemetry for WorldDirector.
 *
 * Observational trace collected solely to record on-device proposal behavior (feature text, legal candidate set,
 * LiteRT model suggestion, and Core acceptance decision).
 *
 * CRITICAL ATTRIBUTION RULE:
 * This telemetry is strictly advisory and observation-only. Proposals are NOT applied as authoritative gameplay
 * mutations, and subsequent world changes are NOT causal outcomes or reward labels.
 *
 * DELIBERATELY ABSENT:
 * Level IDs, raw zone IDs, escape/transition tags, solution IDs, escape blueprints, required facts/actions,
 * evidence IDs, undiscovered evidence, Entity identities, item identities, inventory contents, raw player text,
 * provider data, API keys or credentials, and character-private canon.
 */
data class WorldDirectorTelemetryRecord(
  val sessionId: String,
  val actionKind: ActionKind,
  val turnId: String,
  val features: String,
  val legalProposals: List<WorldPressureProposal>,
  val rawProposed: WorldPressureProposal?,
  val acceptedProposal: WorldPressureProposal,
  val reason: String,
  val modelAccepted: Boolean,
  val visitCount: Int,
  val discoveredEvidenceCount: Int,
  val worldRevision: Int,
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
  companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

fun interface WorldDirectorTelemetrySink {
  fun record(record: WorldDirectorTelemetryRecord)
}

interface WorldDirectorTelemetryStore : WorldDirectorTelemetrySink {
  fun exportJsonl(): String
  fun clear(): Boolean
}

object WorldDirectorTelemetryJson {
  fun encode(value: WorldDirectorTelemetryRecord): JSONObject = JSONObject().apply {
    put("schemaVersion", value.schemaVersion)
    put("sessionId", value.sessionId)
    put("actionKind", value.actionKind.name)
    put("turnId", value.turnId)
    put("features", value.features)
    put("legalProposals", JSONArray(value.legalProposals.map { it.name }))
    put("rawProposed", value.rawProposed?.name ?: JSONObject.NULL)
    put("acceptedProposal", value.acceptedProposal.name)
    put("reason", value.reason)
    put("modelAccepted", value.modelAccepted)
    put("visitCount", value.visitCount)
    put("discoveredEvidenceCount", value.discoveredEvidenceCount)
    put("worldRevision", value.worldRevision)
  }

  fun decode(json: JSONObject): WorldDirectorTelemetryRecord {
    val legalArray = json.optJSONArray("legalProposals")
    val legalProposals = if (legalArray != null) {
      (0 until legalArray.length()).mapNotNull { idx ->
        runCatching { WorldPressureProposal.valueOf(legalArray.getString(idx)) }.getOrNull()
      }
    } else emptyList()

    val rawString = json.optString("rawProposed").takeIf { it.isNotBlank() && it != "null" }
    val rawProposed = rawString?.let { runCatching { WorldPressureProposal.valueOf(it) }.getOrNull() }

    val acceptedString = json.optString("acceptedProposal")
    val acceptedProposal = runCatching { WorldPressureProposal.valueOf(acceptedString) }.getOrDefault(WorldPressureProposal.NONE)

    val actionKindString = json.optString("actionKind")
    val actionKind = runCatching { ActionKind.valueOf(actionKindString) }.getOrDefault(ActionKind.EXPLORE)

    return WorldDirectorTelemetryRecord(
      schemaVersion = json.optInt("schemaVersion", WorldDirectorTelemetryRecord.CURRENT_SCHEMA_VERSION),
      sessionId = json.optString("sessionId"),
      actionKind = actionKind,
      turnId = json.optString("turnId"),
      features = json.optString("features"),
      legalProposals = legalProposals,
      rawProposed = rawProposed,
      acceptedProposal = acceptedProposal,
      reason = json.optString("reason"),
      modelAccepted = json.optBoolean("modelAccepted", false),
      visitCount = json.optInt("visitCount", 0).coerceAtLeast(0),
      discoveredEvidenceCount = json.optInt("discoveredEvidenceCount", 0).coerceAtLeast(0),
      worldRevision = json.optInt("worldRevision", 0).coerceAtLeast(0)
    )
  }
}

/** Bounded, local-only storage namespace strictly separate from BackroomsDirector evidence telemetry. */
class SharedPreferencesWorldDirectorTelemetryStore(
  context: Context,
  private val maxRecords: Int = 256
) : WorldDirectorTelemetryStore {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val recordedKeys = mutableSetOf<String>()

  @Synchronized override fun record(record: WorldDirectorTelemetryRecord) {
    if (record.schemaVersion != WorldDirectorTelemetryRecord.CURRENT_SCHEMA_VERSION) return
    val dedupeKey = "${record.sessionId}|${record.turnId}|${record.actionKind.name}"
    if (recordedKeys.contains(dedupeKey)) return

    val records = readRecords()
    val isDuplicateInStore = records.any { json ->
      val recSession = json.optString("sessionId")
      val recTurn = json.optString("turnId")
      val recKind = json.optString("actionKind")
      "$recSession|$recTurn|$recKind" == dedupeKey
    }
    if (isDuplicateInStore) {
      recordedKeys.add(dedupeKey)
      return
    }

    recordedKeys.add(dedupeKey)
    records.add(WorldDirectorTelemetryJson.encode(record))
    while (records.size > maxRecords.coerceAtLeast(1)) records.removeAt(0)
    prefs.edit().putString(RECORDS_KEY, JSONArray(records).toString()).apply()
  }

  @Synchronized override fun exportJsonl(): String = readRecords()
    .joinToString("\n") { it.toString() }

  @Synchronized override fun clear(): Boolean {
    recordedKeys.clear()
    return prefs.edit().remove(RECORDS_KEY).commit()
  }

  private fun readRecords(): MutableList<JSONObject> {
    val raw = prefs.getString(RECORDS_KEY, null).orEmpty()
    if (raw.isBlank()) return mutableListOf()
    val array = runCatching { JSONArray(raw) }.getOrElse { return mutableListOf() }
    return (0 until array.length()).mapNotNull { idx ->
      array.optJSONObject(idx)?.takeIf { json ->
        runCatching { WorldDirectorTelemetryJson.decode(json) }.isSuccess
      }
    }.toMutableList()
  }

  companion object {
    private const val PREFS_NAME = "world_director_telemetry"
    private const val RECORDS_KEY = "world_director_records_v1"
  }
}

object WorldDirectorTelemetryPrivacy {
  private val forbiddenJsonKeys = setOf(
    "levelId", "zoneId", "evidenceId", "evidenceIds", "requiredFacts", "requiredActions",
    "solutionId", "escapeBlueprint", "playerText", "input", "apiKey", "secret",
    "entityId", "itemId", "inventory", "characterCanon"
  )

  fun validateExport(jsonl: String): Boolean = jsonl.lineSequence().filter(String::isNotBlank).all { line ->
    val json = runCatching { JSONObject(line) }.getOrNull() ?: return@all false
    json.keys().asSequence().none(forbiddenJsonKeys::contains) &&
      json.optInt("schemaVersion", -1) == WorldDirectorTelemetryRecord.CURRENT_SCHEMA_VERSION
  }

  fun opaqueSessionId(level: LevelInstanceState): String {
    val material = "${level.runSeed}|${level.generationFingerprint.orEmpty()}|${level.generationId}"
    val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it) }
  }
}
