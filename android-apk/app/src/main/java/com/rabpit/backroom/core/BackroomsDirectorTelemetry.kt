package com.rabpit.backroom.core

import android.content.Context
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Privacy-safe training trace for BackroomsDirector.
 *
 * Deliberately absent: Level/zone/evidence IDs, required facts/actions, solution IDs, raw blueprints,
 * player text, character canon, account identity, API/provider data and secrets.
 */
data class DirectorTelemetryRecord(
  val sessionId: String,
  val actionKind: ActionKind,
  val features: String,
  val candidateSourceCounts: Map<EvidenceSource, Int>,
  val modelPreferredSource: EvidenceSource?,
  val modelAccepted: Boolean,
  val fallbackUsed: Boolean,
  val selectedSource: EvidenceSource,
  val surfacedCount: Int,
  val discoveredEvidenceBefore: Int,
  val discoveredEvidenceAfter: Int,
  val discoveredFactBefore: Int,
  val discoveredFactAfter: Int,
  val unlockedFact: Boolean,
  val worldRevisionBefore: Int,
  val worldRevisionAfter: Int,
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
  companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

data class DirectorDecisionTrace(
  val sessionId: String,
  val actionKind: ActionKind,
  val features: String,
  val candidateSourceCounts: Map<EvidenceSource, Int>,
  val modelPreferredSource: EvidenceSource?,
  val modelAccepted: Boolean,
  val selectedSource: EvidenceSource,
  val discoveredEvidenceBefore: Int,
  val discoveredFactBefore: Int,
  val worldRevisionBefore: Int
)

fun interface BackroomsDirectorTelemetrySink {
  fun record(record: DirectorTelemetryRecord)
}

interface BackroomsDirectorTelemetryStore : BackroomsDirectorTelemetrySink {
  fun exportJsonl(): String
  fun clear(): Boolean
}

object DirectorTelemetryJson {
  fun encode(value: DirectorTelemetryRecord): JSONObject = JSONObject().apply {
    put("schemaVersion", value.schemaVersion)
    put("sessionId", value.sessionId)
    put("actionKind", value.actionKind.name)
    put("features", value.features)
    put("candidateSourceCounts", JSONObject().apply {
      EvidenceSource.values().forEach { source -> put(source.name, value.candidateSourceCounts[source] ?: 0) }
    })
    put("modelPreferredSource", value.modelPreferredSource?.name ?: JSONObject.NULL)
    put("modelAccepted", value.modelAccepted)
    put("fallbackUsed", value.fallbackUsed)
    put("selectedSource", value.selectedSource.name)
    put("surfacedCount", value.surfacedCount)
    put("discoveredEvidenceBefore", value.discoveredEvidenceBefore)
    put("discoveredEvidenceAfter", value.discoveredEvidenceAfter)
    put("discoveredFactBefore", value.discoveredFactBefore)
    put("discoveredFactAfter", value.discoveredFactAfter)
    put("unlockedFact", value.unlockedFact)
    put("worldRevisionBefore", value.worldRevisionBefore)
    put("worldRevisionAfter", value.worldRevisionAfter)
  }

  fun decode(json: JSONObject): DirectorTelemetryRecord {
    val counts = EvidenceSource.values().associateWith { source ->
      json.optJSONObject("candidateSourceCounts")?.optInt(source.name, 0)?.coerceAtLeast(0) ?: 0
    }.filterValues { it > 0 }
    val preferred = json.optString("modelPreferredSource")
      .takeIf(String::isNotBlank)
      ?.let { raw -> runCatching { EvidenceSource.valueOf(raw) }.getOrNull() }
    return DirectorTelemetryRecord(
      schemaVersion = json.optInt("schemaVersion", DirectorTelemetryRecord.CURRENT_SCHEMA_VERSION),
      sessionId = json.optString("sessionId"),
      actionKind = runCatching { ActionKind.valueOf(json.optString("actionKind")) }.getOrDefault(ActionKind.EXPLORE),
      features = json.optString("features"),
      candidateSourceCounts = counts,
      modelPreferredSource = preferred,
      modelAccepted = json.optBoolean("modelAccepted", false),
      fallbackUsed = json.optBoolean("fallbackUsed", true),
      selectedSource = runCatching { EvidenceSource.valueOf(json.optString("selectedSource")) }.getOrDefault(EvidenceSource.ANOMALY),
      surfacedCount = json.optInt("surfacedCount", 0).coerceAtLeast(0),
      discoveredEvidenceBefore = json.optInt("discoveredEvidenceBefore", 0).coerceAtLeast(0),
      discoveredEvidenceAfter = json.optInt("discoveredEvidenceAfter", 0).coerceAtLeast(0),
      discoveredFactBefore = json.optInt("discoveredFactBefore", 0).coerceAtLeast(0),
      discoveredFactAfter = json.optInt("discoveredFactAfter", 0).coerceAtLeast(0),
      unlockedFact = json.optBoolean("unlockedFact", false),
      worldRevisionBefore = json.optInt("worldRevisionBefore", 0).coerceAtLeast(0),
      worldRevisionAfter = json.optInt("worldRevisionAfter", 0).coerceAtLeast(0)
    )
  }
}

/** Bounded, local-only storage. It is intentionally separate from the authoritative game save. */
class SharedPreferencesBackroomsDirectorTelemetryStore(
  context: Context,
  private val maxRecords: Int = 256
) : BackroomsDirectorTelemetryStore {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  @Synchronized override fun record(record: DirectorTelemetryRecord) {
    require(record.schemaVersion == DirectorTelemetryRecord.CURRENT_SCHEMA_VERSION) { "director_telemetry_schema" }
    val records = readRecords().apply { add(DirectorTelemetryJson.encode(record)) }
    while (records.size > maxRecords.coerceAtLeast(1)) records.removeAt(0)
    prefs.edit().putString(RECORDS_KEY, JSONArray(records).toString()).apply()
  }

  @Synchronized override fun exportJsonl(): String = readRecords()
    .joinToString("\n") { it.toString() }

  @Synchronized override fun clear(): Boolean = prefs.edit().remove(RECORDS_KEY).commit()

  private fun readRecords(): MutableList<JSONObject> {
    val raw = prefs.getString(RECORDS_KEY, null).orEmpty()
    if (raw.isBlank()) return mutableListOf()
    val array = runCatching { JSONArray(raw) }.getOrElse { return mutableListOf() }
    return (0 until array.length()).mapNotNull(array::optJSONObject).toMutableList()
  }

  companion object {
    private const val PREFS_NAME = "backrooms_director_telemetry"
    private const val RECORDS_KEY = "records_v1"
  }
}

object BackroomsDirectorTelemetryPrivacy {
  private val forbiddenJsonKeys = setOf(
    "levelId", "zoneId", "evidenceId", "evidenceIds", "requiredFacts", "requiredActions",
    "solutionId", "escapeBlueprint", "playerText", "input", "apiKey", "secret"
  )

  fun validateExport(jsonl: String): Boolean = jsonl.lineSequence().filter(String::isNotBlank).all { line ->
    val json = runCatching { JSONObject(line) }.getOrNull() ?: return@all false
    json.keys().asSequence().none(forbiddenJsonKeys::contains) &&
      json.optInt("schemaVersion", -1) == DirectorTelemetryRecord.CURRENT_SCHEMA_VERSION
  }

  fun opaqueSessionId(level: LevelInstanceState): String {
    val material = "${level.runSeed}|${level.generationFingerprint.orEmpty()}|${level.generationId}"
    val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it) }
  }
}
