package com.rabpit.backroom.core

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure generation payload. It deliberately contains no runtime progress fields, so a model cannot
 * manufacture discovered hints, completed actions, revisions or prior world mutations.
 */
data class LevelGenerationCandidate(
  val initialZoneId: String,
  val zones: Map<String, ZoneState>,
  val landmarks: Map<String, String> = emptyMap(),
  val environment: Map<String, String> = emptyMap(),
  val environmentTags: Set<String> = emptySet(),
  val phenomena: Set<String> = emptySet(),
  val canonClaims: Set<String> = emptySet(),
  val escapeBlueprint: EscapeBlueprintState,
  val evidence: Map<String, EvidenceState>,
  val npcKnowledge: Map<String, Set<String>> = emptyMap(),
  val exploreRoute: List<String> = emptyList(),
  val actions: Map<String, LevelActionRule> = emptyMap(),
  val replies: Map<String, String> = emptyMap(),
  val candidateSchemaVersion: Int = CURRENT_CANDIDATE_SCHEMA_VERSION
) {
  companion object {
    const val CURRENT_CANDIDATE_SCHEMA_VERSION = 1
  }
}

object LevelGenerationCandidateJson {
  fun encode(value: LevelGenerationCandidate): JSONObject = JSONObject().apply {
    put("candidateSchemaVersion", value.candidateSchemaVersion)
    put("initialZoneId", value.initialZoneId)
    put("zones", JSONArray().apply { value.zones.values.forEach { put(LevelInstanceJson.encodeZone(it)) } })
    put("landmarks", LevelInstanceJson.encodeStringMap(value.landmarks))
    put("environment", LevelInstanceJson.encodeStringMap(value.environment))
    put("environmentTags", JSONArray(value.environmentTags.sorted()))
    put("phenomena", JSONArray(value.phenomena.sorted()))
    put("canonClaims", JSONArray(value.canonClaims.sorted()))
    put("escapeBlueprint", LevelInstanceJson.encodeBlueprint(value.escapeBlueprint))
    put("evidence", JSONArray().apply { value.evidence.values.forEach { put(LevelInstanceJson.encodeEvidence(it)) } })
    put("npcKnowledge", JSONObject().apply { value.npcKnowledge.forEach { (id, facts) -> put(id, JSONArray(facts.sorted())) } })
    put("exploreRoute", JSONArray(value.exploreRoute))
    put("actions", JSONArray().apply { value.actions.values.forEach { put(LevelInstanceJson.encodeAction(it)) } })
    put("replies", LevelInstanceJson.encodeStringMap(value.replies))
  }

  fun decode(raw: String): LevelGenerationCandidate = decode(JSONObject(raw))

  fun decode(json: JSONObject): LevelGenerationCandidate {
    val zones = linkedMapOf<String, ZoneState>()
    json.optJSONArray("zones").objects().forEach { item ->
      val zone = LevelInstanceJson.decodeZone(item)
      require(zone.id.isNotBlank()) { "candidate_zone_id_missing" }
      require(zone.id !in zones) { "candidate_duplicate_zone:${zone.id}" }
      zones[zone.id] = zone
    }

    val evidence = linkedMapOf<String, EvidenceState>()
    json.optJSONArray("evidence").objects().forEach { item ->
      val value = LevelInstanceJson.decodeEvidence(item)
      require(value.id.isNotBlank()) { "candidate_evidence_id_missing" }
      require(value.id !in evidence) { "candidate_duplicate_evidence:${value.id}" }
      evidence[value.id] = value.copy(discovered = false, discoveredAtRevision = null)
    }

    val actions = linkedMapOf<String, LevelActionRule>()
    json.optJSONArray("actions").objects().forEach { item ->
      val value = LevelInstanceJson.decodeAction(item)
      require(value.id.isNotBlank()) { "candidate_action_id_missing" }
      require(value.id !in actions) { "candidate_duplicate_action:${value.id}" }
      actions[value.id] = value
    }

    val knowledge = linkedMapOf<String, Set<String>>()
    json.optJSONObject("npcKnowledge")?.let { root ->
      root.keys().forEach { id -> knowledge[id] = root.optJSONArray(id).strings().toSet() }
    }

    return LevelGenerationCandidate(
      initialZoneId = json.optString("initialZoneId"),
      zones = zones,
      landmarks = json.optJSONObject("landmarks").stringsMap(),
      environment = json.optJSONObject("environment").stringsMap(),
      environmentTags = json.optJSONArray("environmentTags").strings().toSet(),
      phenomena = json.optJSONArray("phenomena").strings().toSet(),
      canonClaims = json.optJSONArray("canonClaims").strings().toSet(),
      escapeBlueprint = LevelInstanceJson.decodeBlueprint(json.optJSONObject("escapeBlueprint") ?: JSONObject()),
      evidence = evidence,
      npcKnowledge = knowledge,
      exploreRoute = json.optJSONArray("exploreRoute").strings(),
      actions = actions,
      replies = json.optJSONObject("replies").stringsMap(),
      candidateSchemaVersion = json.optInt("candidateSchemaVersion", LevelGenerationCandidate.CURRENT_CANDIDATE_SCHEMA_VERSION)
    )
  }

  private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

  private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)

  private fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }
}

object LevelInstanceGenerator {
  const val DEFINITION_GENERATOR_VERSION = "definition-fixture-v2"
  const val GENERATION_SCHEMA_VERSION = 1

  fun fromDefinition(definition: LevelDefinition, seed: String): LevelInstanceState {
    val definitionValidation = LevelDefinitionValidator.validate(definition)
    require(definitionValidation.valid) {
      "invalid_level:${definition.id}:${definitionValidation.errors.joinToString(",")}" 
    }
    val candidate = LevelGenerationCandidate(
      initialZoneId = definition.initialZoneId,
      zones = definition.zones,
      landmarks = definition.landmarks,
      environment = definition.environment,
      environmentTags = definition.canonProfile.environmentTags,
      escapeBlueprint = definition.escapeBlueprint,
      evidence = definition.evidence,
      npcKnowledge = definition.npcKnowledge,
      exploreRoute = definition.exploreRoute,
      actions = definition.actions,
      replies = definition.replies
    )
    return commitCandidate(definition, seed, candidate, DEFINITION_GENERATOR_VERSION)
  }

  fun commitCandidate(
    definition: LevelDefinition,
    seed: String,
    candidate: LevelGenerationCandidate,
    generatorVersion: String
  ): LevelInstanceState {
    require(candidate.candidateSchemaVersion == LevelGenerationCandidate.CURRENT_CANDIDATE_SCHEMA_VERSION) {
      "unsupported_generation_candidate_schema:${candidate.candidateSchemaVersion}"
    }
    require(generatorVersion.isNotBlank()) { "generator_version_missing" }

    val cleanEvidence = candidate.evidence.mapValues { (_, evidence) ->
      evidence.copy(discovered = false, discoveredAtRevision = null)
    }
    val fingerprint = fingerprint(candidate.copy(evidence = cleanEvidence))
    val instance = LevelInstanceState(
      runSeed = seed,
      levelId = definition.id,
      generationId = "${definition.id}:$seed",
      currentZoneId = candidate.initialZoneId,
      zones = candidate.zones,
      landmarks = candidate.landmarks,
      environment = candidate.environment + ("exploreStep" to "0"),
      environmentTags = candidate.environmentTags,
      phenomena = candidate.phenomena,
      canonClaims = candidate.canonClaims,
      escapeBlueprint = candidate.escapeBlueprint.copy(locked = true),
      evidence = cleanEvidence,
      npcKnowledge = candidate.npcKnowledge,
      exploreRoute = candidate.exploreRoute,
      actions = candidate.actions,
      replies = candidate.replies,
      generatorVersion = generatorVersion,
      generationSchemaVersion = GENERATION_SCHEMA_VERSION,
      generationFingerprint = fingerprint
    )

    val validation = BlueprintValidator.validate(instance, definition)
    require(validation.valid) {
      "invalid_generated_level:${definition.id}:${validation.errors.joinToString(",")}" 
    }
    return instance
  }

  private fun fingerprint(candidate: LevelGenerationCandidate): String {
    val bytes = LevelGenerationCandidateJson.encode(candidate).toString().toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  }
}
