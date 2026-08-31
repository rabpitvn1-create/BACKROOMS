package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable canon envelope shared by every generated instance of a Level.
 * Runtime generation may vary the concrete layout and puzzle, but not these rules.
 */
data class LevelCanonProfile(
  val environmentTags: Set<String> = emptySet(),
  val requiredZoneTags: Set<String> = emptySet(),
  val allowedPhenomena: Set<String> = emptySet(),
  val forbiddenClaims: Set<String> = emptySet(),
  val transitionTags: Set<String> = emptySet(),
  val metadata: Map<String, String> = emptyMap()
)

data class ProceduralGenerationConstraints(
  val minZones: Int = 1,
  val maxZones: Int = 64,
  val minEvidencePerRequiredFact: Int = 2,
  val minEvidenceSourceTypesPerRequiredFact: Int = 2,
  val maxRequiredActions: Int = 12,
  val allowSurvivors: Boolean = true,
  val allowEntities: Boolean = true,
  val proceduralTopology: Boolean = false,
  val proceduralLandmarks: Boolean = false,
  val proceduralEvidencePlacement: Boolean = false,
  val proceduralEscapeBlueprint: Boolean = false
)

object LevelCanonProfileJson {
  fun encode(value: LevelCanonProfile): JSONObject = JSONObject().apply {
    put("environmentTags", JSONArray(value.environmentTags.sorted()))
    put("requiredZoneTags", JSONArray(value.requiredZoneTags.sorted()))
    put("allowedPhenomena", JSONArray(value.allowedPhenomena.sorted()))
    put("forbiddenClaims", JSONArray(value.forbiddenClaims.sorted()))
    put("transitionTags", JSONArray(value.transitionTags.sorted()))
    put("metadata", stringMap(value.metadata))
  }

  fun decode(json: JSONObject?): LevelCanonProfile {
    if (json == null) return LevelCanonProfile()
    return LevelCanonProfile(
      environmentTags = json.optJSONArray("environmentTags").strings().toSet(),
      requiredZoneTags = json.optJSONArray("requiredZoneTags").strings().toSet(),
      allowedPhenomena = json.optJSONArray("allowedPhenomena").strings().toSet(),
      forbiddenClaims = json.optJSONArray("forbiddenClaims").strings().toSet(),
      transitionTags = json.optJSONArray("transitionTags").strings().toSet(),
      metadata = json.optJSONObject("metadata").stringsMap()
    )
  }

  private fun stringMap(values: Map<String, String>) = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value) }
  }

  private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

  private fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }
}

object ProceduralGenerationConstraintsJson {
  fun encode(value: ProceduralGenerationConstraints): JSONObject = JSONObject().apply {
    put("minZones", value.minZones)
    put("maxZones", value.maxZones)
    put("minEvidencePerRequiredFact", value.minEvidencePerRequiredFact)
    put("minEvidenceSourceTypesPerRequiredFact", value.minEvidenceSourceTypesPerRequiredFact)
    put("maxRequiredActions", value.maxRequiredActions)
    put("allowSurvivors", value.allowSurvivors)
    put("allowEntities", value.allowEntities)
    put("proceduralTopology", value.proceduralTopology)
    put("proceduralLandmarks", value.proceduralLandmarks)
    put("proceduralEvidencePlacement", value.proceduralEvidencePlacement)
    put("proceduralEscapeBlueprint", value.proceduralEscapeBlueprint)
  }

  fun decode(json: JSONObject?): ProceduralGenerationConstraints {
    if (json == null) return ProceduralGenerationConstraints()
    return ProceduralGenerationConstraints(
      minZones = json.optInt("minZones", 1),
      maxZones = json.optInt("maxZones", 64),
      minEvidencePerRequiredFact = json.optInt("minEvidencePerRequiredFact", 2),
      minEvidenceSourceTypesPerRequiredFact = json.optInt("minEvidenceSourceTypesPerRequiredFact", 2),
      maxRequiredActions = json.optInt("maxRequiredActions", 12),
      allowSurvivors = json.optBoolean("allowSurvivors", true),
      allowEntities = json.optBoolean("allowEntities", true),
      proceduralTopology = json.optBoolean("proceduralTopology", false),
      proceduralLandmarks = json.optBoolean("proceduralLandmarks", false),
      proceduralEvidencePlacement = json.optBoolean("proceduralEvidencePlacement", false),
      proceduralEscapeBlueprint = json.optBoolean("proceduralEscapeBlueprint", false)
    )
  }
}
