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

  private val segments = Regex("[,;.!?\\n]+|(?<![\\p{L}])trong\\s+khi(?![\\p{L}])",
    RegexOption.IGNORE_CASE)
  private fun mentions(segment: String, subject: String): Boolean =
    Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(subject) +
      "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(segment)

  /** Individual intents retain ownership without introducing another rest system. */
  fun activities(state: GameState, action: String): Map<String, TimeActivity> {
    val activeParty = state.party.memberIds.distinct().filter { id ->
      state.characters[id]?.presence == CharacterPresence.ACTIVE
    }
    if (activity(action) == TimeActivity.NORMAL || activeParty.isEmpty()) return emptyMap()
    if (partyRest.containsMatchIn(action)) return activeParty.associateWith { activity(action) }
    val result = linkedMapOf<String, TimeActivity>()
    for (part in action.split(segments)) {
      val mode = activity(part)
      if (mode == TimeActivity.NORMAL) continue
      val explicit = activeParty.filter { id ->
        if (id == CAO_MINH_ID) listOf("tôi", "mình", "Cao Minh").any { mentions(part, it) }
        else listOf(id, state.characters.getValue(id).name).any { mentions(part, it) }
      }
      for (id in explicit.ifEmpty { listOf(CAO_MINH_ID).filter { it in activeParty } })
        result[id] = mode
    }
    return result
  }

  private val partyRest = Regex(
    "(?:cả\\s*hai|ca\\s*hai|cả\\s*nhóm|ca\\s*nhom|cả\\s*party|ca\\s*party|mọi\\s*người|moi\\s*nguoi|luân\\s*phiên|luan\\s*phien|thay\\s*phiên|thay\\s*phien|chia\\s*ca)",
    RegexOption.IGNORE_CASE
  )

  fun targets(state: GameState, action: String): List<String> =
    activities(state, action).keys.toList()
}
