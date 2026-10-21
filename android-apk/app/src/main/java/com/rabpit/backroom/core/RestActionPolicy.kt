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

    // Mentioning a companion does not mean they also slept. Require a subject
    // followed by a rest/sleep verb before assigning that companion any recovery.
    val namedSleep = Regex(
      "(?:ngủ|ngu|chợp\\s*mắt|chop\\s*mat|nghỉ|nghi|sleep|rest)",
      RegexOption.IGNORE_CASE
    )
    val explicitFollowers = activeParty.filter { it != CAO_MINH_ID }.filter { id ->
      val character = state.characters.getValue(id)
      val names = listOf(id, character.name)
      names.any { name ->
        Regex(Regex.escape(name) + "\\s+(?:đang\\s+|sẽ\\s+|muốn\\s+)?"+
          namedSleep.pattern, RegexOption.IGNORE_CASE).containsMatchIn(action)
      }
    }
    val playerExplicit = Regex(
      "(?<![\\p{L}\\p{N}])(?:tôi|mình|Cao\\s*Minh)\\s+(?:đang\\s+|sẽ\\s+|muốn\\s+)?" +
        namedSleep.pattern, RegexOption.IGNORE_CASE
    ).containsMatchIn(action)
    val actors = if (explicitFollowers.isNotEmpty() && !playerExplicit) explicitFollowers
      else listOf(CAO_MINH_ID) + explicitFollowers
    return actors.distinct().filter { it in activeParty }
  }
}
