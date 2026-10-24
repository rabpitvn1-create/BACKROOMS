package com.rabpit.backroom.core

/**
 * Resolves Core-owned character statuses once per committed combat action.
 * Typed effects live in GameState.statuses and character.statusIds; no UI/GM
 * narration can award damage or extend a duration.
 */
object CombatStatusLifecycle {
  data class Tick(val state: GameState, val feedback: List<CombatRuntime.DamageFeedback>)

  fun advance(input: GameState): Tick {
    var next = input
    val feedback = mutableListOf<CombatRuntime.DamageFeedback>()
    val removed = mutableSetOf<String>()
    val changed = mutableMapOf<String, StatusEffect>()

    for ((id, character) in input.characters) {
      if (id !in input.party.memberIds || character.presence != CharacterPresence.ACTIVE) continue
      for (statusId in character.statusIds) {
        val effect = input.statuses[statusId] ?: continue
        val duration = effect.durationTurns
        if (duration != null && duration <= 0) { removed += statusId; continue }

        val kind = effect.type.trim().uppercase(java.util.Locale.ROOT)
        val defaultPercent = when (kind) {
          "BLEED", "BLEEDING" -> 3
          "POISON", "POISONED" -> 2
          else -> 0
        }
        val percent = if (defaultPercent > 0)
          effect.metadata["damagePercentMaxHp"]?.toIntOrNull()?.coerceIn(1, 25)
            ?: defaultPercent
        else 0
        if (percent > 0) {
          val current = next.characters[id]?.vitalState?.currentHp ?: 0
          if (current > 0) {
            val maxHp = CharacterStatEngine.effective(next, id).maxHp
            val raw = (maxHp.toLong() * percent.toLong() / 100L).coerceAtLeast(1L)
            val dealt = minOf(current, raw.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            next = CharacterStatEngine.setCurrentHp(next, id, current - dealt)
            feedback += CombatRuntime.DamageFeedback("actor", dealt, actorId = id, phase = "status",
              skill = if (kind.startsWith("BLEED")) "Chảy máu" else "Trúng độc",
              status = if (kind.startsWith("BLEED")) "Chảy máu" else "Trúng độc")
          }
        }
        if (!effect.persistent && duration != null) {
          if (duration <= 1) removed += statusId
          else changed[statusId] = effect.copy(durationTurns = duration - 1)
        }
      }
    }
    if (changed.isEmpty() && removed.isEmpty()) return Tick(next, feedback)
    val statuses = (next.statuses + changed) - removed
    val characters = next.characters.mapValues { (_, character) ->
      if (character.statusIds.any { it in removed })
        character.copy(statusIds = character.statusIds - removed)
      else character
    }
    return Tick(next.copy(statuses = statuses, characters = characters), feedback)
  }
}
