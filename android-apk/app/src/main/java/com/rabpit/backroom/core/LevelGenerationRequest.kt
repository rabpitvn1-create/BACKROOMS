package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sanitized request sent to a world generator. It exposes immutable canon and generation bounds,
 * never the fixture escape solution, runtime progress, save state, or previously discovered facts.
 */
object LevelGenerationRequestFactory {
  const val CONTRACT_VERSION = 1

  fun build(definition: LevelDefinition, runSeed: String): JSONObject = JSONObject().apply {
    put("contractVersion", CONTRACT_VERSION)
    put("candidateSchemaVersion", LevelGenerationCandidate.CURRENT_CANDIDATE_SCHEMA_VERSION)
    put("levelId", definition.id)
    put("levelName", definition.name)
    definition.parentId?.let { put("parentId", it) }
    put("runSeed", runSeed)
    put("canonProfile", LevelCanonProfileJson.encode(definition.canonProfile))
    put("generationConstraints", ProceduralGenerationConstraintsJson.encode(definition.generationConstraints))
    put("allowedEvidenceSources", JSONArray(EvidenceSource.values().map { it.name }))
    put("allowedEffectTypes", JSONArray(LevelEffectType.values().map { it.name }))
    put("conditionGrammar", JSONArray(listOf(
      "visit:<zoneId>:<positiveCount>",
      "env:<key>=<value>",
      "zone:<zoneId>",
      "action:<actionId>",
      "fact:<factId>"
    )))
    put("requiredCandidateFields", JSONArray(listOf(
      "candidateSchemaVersion", "initialZoneId", "zones", "environmentTags",
      "escapeBlueprint", "evidence", "exploreRoute", "actions"
    )))
    put("rules", JSONObject().apply {
      put("escapeZoneTag", "escape")
      put("entryZoneTag", "entry")
      put("requiredFactsNeedIndependentEvidence", true)
      put("requiredFactsNeedSourceDiversity", true)
      put("runtimeProgressFieldsForbidden", true)
      put("engineLocksBlueprintAfterValidation", true)
      put("searchExploreMustNotDirectlyCompleteLevel", true)
      put("executeIsOnlyEscapeActionChannel", true)
      put("backroomsConsciousnessMustRemainUnconfirmedUnlessCanonExplicitlyAllowsIt", true)
    })
  }
}
