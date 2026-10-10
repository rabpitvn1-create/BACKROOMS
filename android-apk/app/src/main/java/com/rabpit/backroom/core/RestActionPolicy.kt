package com.rabpit.backroom.core

/** Deterministic authority for deciding whose sleep counter may be reset by a player rest action. */
object RestActionPolicy {
  private val restIntent = Regex(
    "(?:^|\\s)(?:ngủ|ngu|chợp\\s*mắt|chop\\s*mat|nghỉ\\s*ngơi|nghi\\s*ngoi|nghỉ\\s*tạm|nghi\\s*tam|sleep|nap|rest)(?:\\s|$)",
    RegexOption.IGNORE_CASE
  )
  private val partyRest = Regex(
    "(?:cả\\s*hai|ca\\s*hai|cả\\s*nhóm|ca\\s*nhom|cả\\s*party|ca\\s*party|mọi\\s*người|moi\\s*nguoi|luân\\s*phiên|luan\\s*phien|thay\\s*phiên|thay\\s*phien|chia\\s*ca)",
    RegexOption.IGNORE_CASE
  )

  fun targets(state: GameState, action: String): List<String> {
    if (!restIntent.containsMatchIn(action)) return emptyList()
    val activeParty = state.party.memberIds.distinct().filter { id ->
      state.characters[id]?.presence == CharacterPresence.ACTIVE
    }
    if (partyRest.containsMatchIn(action)) return activeParty

    val explicitFollowers = activeParty.filter { it != KAI_ID }.filter { id ->
      val character = state.characters[id] ?: return@filter false
      action.contains(id, ignoreCase = true) || action.contains(character.name, ignoreCase = true)
    }
    return (listOf(KAI_ID) + explicitFollowers).distinct().filter { it in activeParty }
  }
}
