package com.rabpit.backroom.core

/**
 * Core-derived scene participation evidence, not a list of event witnesses.
 *
 * ACTIVE is the default CharacterState value even for never-met characters,
 * so ACTIVE alone must never place an NPC into a scene. Pending encounter IDs
 * must originate from the current Core action session, never narrator output.
 */
object CoreScenePresence {
  enum class Source { PLAYER, PARTY, ENCOUNTER, PENDING_ENCOUNTER }

  data class Participant(val characterId: String, val source: Source, val inParty: Boolean)

  fun participants(state: GameState, pendingEncounterIds: Set<String> = emptySet()): List<Participant> {
    val result = mutableListOf<Participant>()
    state.characters[CAO_MINH_ID]?.let {
      result += Participant(CAO_MINH_ID, Source.PLAYER, true)
    }
    state.characters.values.sortedBy { it.id }.forEach { character ->
      if (character.id == CAO_MINH_ID || character.presence == CharacterPresence.DEAD) return@forEach
      val party = character.id in state.party.memberIds
      val pending = character.id in pendingEncounterIds
      val active = character.presence == CharacterPresence.ACTIVE
      val source = when {
        pending -> Source.PENDING_ENCOUNTER
        active && party -> Source.PARTY
        active && character.metadata["encountered"] == "true" -> Source.ENCOUNTER
        else -> null
      } ?: return@forEach
      result += Participant(character.id, source, party)
    }
    return result
  }
}
