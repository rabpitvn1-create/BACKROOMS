package com.rabpit.backroom.core

import kotlin.random.Random

/**
 * Authoritative EXP rewards for a confirmed Entity defeat.
 * One persistent award marker per Entity instance, including each wave.
 * Living ACTIVE Party members each receive the full award, without splitting.
 */
object EntityExpRewards {
  private const val AWARDED_PREFIX = "exp.awarded."
  const val BASE_EXP = 2
  const val EXP_PER_BACKROOMS_LEVEL = 2

  data class Reward(
    val state: GameState,
    val expPerCharacter: Int,
    val recipients: List<String>,
    val leveledUp: Map<String, Int>,
    val newlyAwarded: Boolean
  ) {
    val text: String get() = when {
      !newlyAwarded -> ""
      recipients.isEmpty() -> "Không có Character đang chiến đấu đủ điều kiện nhận EXP."
      else -> buildString {
        append("+").append(expPerCharacter).append(" EXP cho mỗi Character: ")
        append(recipients.map { state.characters[it]?.name ?: it }.joinToString(", "))
        if (leveledUp.isNotEmpty()) {
          append(". LEVEL UP: ")
          append(leveledUp.entries.joinToString(", ") { (id, gained) ->
            "${state.characters[id]?.name ?: id} +$gained Level"
          })
        }
        append(".")
      }
    }
  }

  fun expForRank(progressionRank: Long): Int {
    val rank = EntityPowerScaling.requireRank(progressionRank)
    val worldLevel = rank / EntityPowerScaling.RANK_PER_FULL_LEVEL
    return (BASE_EXP.toLong() + EXP_PER_BACKROOMS_LEVEL.toLong() * worldLevel).toInt()
  }

  fun award(
    state: GameState, entityInstanceId: String, progressionRank: Long,
    multiplier: Int = 1, fixedExp: Int? = null
  ): Reward {
    require(multiplier in 1..24) { "entity_exp_multiplier_invalid" }
    require(fixedExp == null || fixedExp > 0) { "entity_fixed_exp_invalid" }
    require(entityInstanceId.isNotBlank()) { "Entity instance ID required for EXP." }
    val marker = AWARDED_PREFIX + entityInstanceId
    if (marker in state.metadata) return Reward(state, 0, emptyList(), emptyMap(), false)

    val amount = fixedExp?.also { EntityPowerScaling.requireRank(progressionRank) }
      ?: (expForRank(progressionRank).toLong() * multiplier)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val recipients = PartyCombatHealth.actingMembers(state)
    var next = state
    val levels = linkedMapOf<String, Int>()
    for (id in recipients) {
      val result = CharacterProgressionCore.grantExp(next, id, amount,
        Random(("$entityInstanceId:$id").hashCode()))
      next = result.state
      if (result.levelsGained > 0) levels[id] = result.levelsGained
    }
    // The marker and all character updates are committed in the same save transaction.
    next = next.copy(metadata = next.metadata + (marker to amount.toString()))
    return Reward(next, amount, recipients, levels, true)
  }
}
