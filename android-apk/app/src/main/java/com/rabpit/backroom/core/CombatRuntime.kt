package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** Bridges the pure combat engine to persisted GameState without changing the save schema. */
data class CombatRuntimeResult(
  val state: GameState,
  val resolution: CombatResolution?
)

object CombatRuntime {
  private const val LAST_ENCOUNTER_KEY = "combat.lastEncounterId"

  fun resolveEncounter(
    state: GameState,
    candidate: JSONObject,
    encounterId: String,
    random: CombatRandom = DefaultCombatRandom()
  ): CombatRuntimeResult {
    val entityIds = encounterIds(candidate)
    if (entityIds.isEmpty()) return CombatRuntimeResult(state, null)
    if (state.metadata[LAST_ENCOUNTER_KEY] == encounterId) return CombatRuntimeResult(state, null)

    val level = levelNumber(candidate, state)
    val baseState = state.copy(metadata = state.metadata + (LAST_ENCOUNTER_KEY to encounterId))
    val party = baseState.party.memberIds.mapNotNull { id ->
      val character = baseState.characters[id] ?: return@mapNotNull null
      if (character.presence != CharacterPresence.ACTIVE) return@mapNotNull null
      CombatantState(
        id = character.id,
        name = character.name,
        isEntity = false,
        stats = CombatProgression.read(character),
        baseDamage = CombatProfiles.partyBaseDamage(character.id)
      )
    }
    if (party.none { it.id.equals(KAI_ID, true) }) return CombatRuntimeResult(baseState, null)

    val resolution = AutoTurnCombatEngine(random).resolve(
      encounterId = encounterId,
      partyInput = party,
      entityIds = entityIds,
      level = level
    )

    val updatedCharacters = baseState.characters.toMutableMap()
    resolution.party.forEach { fighter ->
      val character = updatedCharacters[fighter.id] ?: return@forEach
      updatedCharacters[fighter.id] = CombatProgression.write(character, fighter.stats)
    }
    return CombatRuntimeResult(baseState.copy(characters = updatedCharacters), resolution)
  }

  fun encounterIds(candidate: JSONObject): List<String> {
    val encounter = candidate.optJSONObject("flags")
      ?.optJSONObject("lastRolls")
      ?.optJSONObject("entityEncounter")
      ?: return emptyList()
    val ids = encounter.optJSONArray("successIds") ?: return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until ids.length()) {
      val value = ids.optString(index, "").trim()
      if (value.isNotEmpty() && value !in result) result += value
    }
    return result
  }

  fun levelNumber(candidate: JSONObject, state: GameState): Int {
    val direct = candidate.optJSONObject("level")?.optInt("number", Int.MIN_VALUE) ?: Int.MIN_VALUE
    if (direct != Int.MIN_VALUE) return direct.coerceAtLeast(0)
    val stored = state.world["levelJson"]?.let {
      runCatching { JSONObject(it).optInt("number", 0) }.getOrDefault(0)
    } ?: 0
    return stored.coerceAtLeast(0)
  }
}

object CombatJson {
  fun encode(resolution: CombatResolution): JSONObject = JSONObject().apply {
    put("id", resolution.encounterId)
    put("outcome", resolution.outcome.name)
    put("level", resolution.level)
    put("entityQueue", JSONArray(resolution.entityQueue))
    put("defeatedEntities", JSONArray(resolution.defeatedEntities))
    put("timeline", JSONArray().apply {
      resolution.timeline.forEach { event -> put(JSONObject().apply {
        put("kind", event.kind)
        event.actorId?.let { put("actorId", it) }
        event.targetId?.let { put("targetId", it) }
        event.enemyId?.let { put("enemyId", it) }
        put("text", event.text)
      }) }
    })
  }
}
