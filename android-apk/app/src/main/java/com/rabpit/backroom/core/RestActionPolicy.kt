package com.rabpit.backroom.core

/** Deterministic authority for deciding which active party members actually rest or sleep. */
object RestActionPolicy {
  private val sleepIntent = Regex(
    "(?<![\\p{L}\\p{N}])(?:ngủ|ngu|chợp\\s*mắt|chop\\s*mat|sleep|nap)(?![\\p{L}\\p{N}])",
    RegexOption.IGNORE_CASE
  )
  private val restIntent = Regex(
    "(?<![\\p{L}\\p{N}])(?:nghỉ\\s*ngơi|nghi\\s*ngoi|nghỉ\\s*tạm|nghi\\s*tam|nghỉ|nghi|rest|thư\\s*giãn)(?![\\p{L}\\p{N}])",
    RegexOption.IGNORE_CASE
  )
  // A mention of sleep is not necessarily an intent to sleep ("không ngủ").
  private val negation = Regex(
    "(?:không|chưa|chẳng|đừng|ko|khong|chua|don't|do not)\\s+" +
      "(?:(?:muốn|thể|định|cần|dám|được|phải|muon|duoc|want to)\\s+){0,2}$",
    RegexOption.IGNORE_CASE
  )

  private fun affirmed(regex: Regex, action: String): Boolean = regex.findAll(action).any { match ->
    val prefix = action.substring(0, match.range.first).takeLast(45)
    !negation.containsMatchIn(prefix)
  }

  fun activity(action: String): TimeActivity = when {
    affirmed(sleepIntent, action) -> TimeActivity.SLEEP
    affirmed(restIntent, action) -> TimeActivity.REST
    else -> TimeActivity.NORMAL
  }

  private val partyRest = Regex(
    "(?:cả\\s*hai|ca\\s*hai|cả\\s*nhóm|ca\\s*nhom|cả\\s*party|ca\\s*party|mọi\\s*người|moi\\s*nguoi|luân\\s*phiên|luan\\s*phien|thay\\s*phiên|thay\\s*phien|chia\\s*ca)",
    RegexOption.IGNORE_CASE
  )

  fun targets(state: GameState, action: String): List<String> {
    if (activity(action) == TimeActivity.NORMAL) return emptyList()
    val activeParty = state.party.memberIds.distinct().filter { id ->
      state.characters[id]?.presence == CharacterPresence.ACTIVE
    }
    if (partyRest.containsMatchIn(action)) return activeParty

    val explicitFollowers = activeParty.filter { it != CAO_MINH_ID }.filter { id ->
      val character = state.characters[id] ?: return@filter false
      action.contains(id, ignoreCase = true) || action.contains(character.name, ignoreCase = true)
    }
    return (listOf(CAO_MINH_ID) + explicitFollowers).distinct().filter { it in activeParty }
  }
}
