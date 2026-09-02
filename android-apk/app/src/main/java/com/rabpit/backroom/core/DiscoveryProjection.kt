package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Player/narrator-safe discovery view.
 * Canonical EvidenceState may support hidden facts, but consumers outside Core receive only
 * already-discovered presentation text plus explicit inference boundaries.
 */
object DiscoveryProjection {
  private const val OBSERVED_ONLY = "OBSERVED_DETAIL_ONLY"

  fun build(
    state: GameState,
    definition: LevelDefinition?,
    surfacedEvidenceIds: Set<String>,
    action: String
  ): JSONObject {
    val level = state.levelInstance ?: return emptyProjection()
    val evidenceOut = JSONArray()

    surfacedEvidenceIds.sorted().forEach { id ->
      val evidence = level.evidence[id] ?: return@forEach
      if (!evidence.discovered) return@forEach
      val text = visibleText(level, definition, id) ?: return@forEach
      evidenceOut.put(JSONObject()
        .put("text", text)
        .put("sources", JSONArray(evidence.sources.map { it.name }.sorted()))
        .put("meaningScope", OBSERVED_ONLY)
        .put("mayConcludeEscapeRoute", false)
        .put("mayConcludeRequiredAction", false)
        .put("mayRevealHiddenFact", false))
    }

    val allowedNpcStatements = JSONArray()
    val normalizedAction = normalize(action)
    level.npcKnowledge.toSortedMap().forEach { (npcId, factIds) ->
      if (!mentionsActor(normalizedAction, npcId)) return@forEach
      level.evidence.values
        .asSequence()
        .filter { it.discovered && it.supports.any(factIds::contains) }
        .mapNotNull { visibleText(level, definition, it.id) }
        .distinct()
        .sorted()
        .forEach { allowedNpcStatements.put(it) }
    }

    return JSONObject()
      .put("evidence", evidenceOut)
      .put("allowedNpcStatements", allowedNpcStatements)
      .put("inferencePolicy", JSONObject()
        .put("evidenceIsObservationNotSolution", true)
        .put("npcMayUseOnlyAllowedStatements", true)
        .put("hiddenTruthUnavailable", true))
  }

  private fun emptyProjection() = JSONObject()
    .put("evidence", JSONArray())
    .put("allowedNpcStatements", JSONArray())
    .put("inferencePolicy", JSONObject()
      .put("evidenceIsObservationNotSolution", true)
      .put("npcMayUseOnlyAllowedStatements", true)
      .put("hiddenTruthUnavailable", true))

  private fun visibleText(level: LevelInstanceState, definition: LevelDefinition?, evidenceId: String): String? =
    (level.replies["evidence:$evidenceId"] ?: definition?.replies?.get("evidence:$evidenceId"))
      ?.trim()?.takeIf(String::isNotEmpty)

  private fun mentionsActor(action: String, npcId: String): Boolean {
    val actor = normalize(npcId)
    if (actor.isBlank()) return false
    return actor in action || actor.split(' ').filter(String::isNotBlank).all(action::contains)
  }

  private fun normalize(value: String): String = value.lowercase()
    .replace('_', ' ').replace('-', ' ')
    .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
}
