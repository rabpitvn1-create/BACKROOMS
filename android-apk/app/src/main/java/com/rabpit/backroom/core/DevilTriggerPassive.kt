package com.rabpit.backroom.core

import kotlin.math.max

data class DevilTriggerState(
  val activeTurns: Int = 0,
  val cooldownTurns: Int = 0
)

data class DevilTriggerTurn(
  val stateAtStart: DevilTriggerState,
  val activeThisTurn: Boolean,
  val triggeredThisTurn: Boolean,
  val cooldownThisTurn: Boolean
)

object DevilTriggerPassive {
  const val TRIGGER_PERCENT = 35
  const val ACTIVE_TURNS = 3
  const val COOLDOWN_TURNS = 0
  const val EVASION_BONUS_PERCENT = 100
  const val DAMAGE_MULTIPLIER = 5
  const val HEAL_MAX_HP_PERCENT = 5

  private fun normalized(state: DevilTriggerState): DevilTriggerState {
    val active = state.activeTurns.coerceIn(0, ACTIVE_TURNS)
    val cooldown = if (active > 0) 0 else state.cooldownTurns.coerceIn(0, COOLDOWN_TURNS)
    return DevilTriggerState(activeTurns = active, cooldownTurns = cooldown)
  }

  fun beginTurn(current: DevilTriggerState, rollPercent: Int): DevilTriggerTurn {
    val state = normalized(current)
    if (state.activeTurns > 0) {
      return DevilTriggerTurn(state, activeThisTurn = true, triggeredThisTurn = false, cooldownThisTurn = false)
    }
    if (state.cooldownTurns > 0) {
      // HARD GAMEPLAY RULE: no trigger roll is evaluated while cooldown is active.
      return DevilTriggerTurn(state, activeThisTurn = false, triggeredThisTurn = false, cooldownThisTurn = true)
    }
    val triggered = rollPercent.coerceIn(0, 99) < TRIGGER_PERCENT
    val started = if (triggered) DevilTriggerState(activeTurns = ACTIVE_TURNS) else state
    return DevilTriggerTurn(started, activeThisTurn = triggered, triggeredThisTurn = triggered, cooldownThisTurn = false)
  }

  fun endTurn(turn: DevilTriggerTurn): DevilTriggerState {
    if (turn.activeThisTurn) {
      val remaining = max(0, turn.stateAtStart.activeTurns - 1)
      return if (remaining > 0) DevilTriggerState(activeTurns = remaining)
      else DevilTriggerState()
    }
    if (turn.cooldownThisTurn) {
      return DevilTriggerState(cooldownTurns = max(0, turn.stateAtStart.cooldownTurns - 1))
    }
    return normalized(turn.stateAtStart)
  }

  fun damage(baseDamage: Int, active: Boolean): Int {
    val safe = max(0, baseDamage)
    if (!active) return safe
    return (safe.toLong() * DAMAGE_MULTIPLIER).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  fun evasionBonus(active: Boolean): Int = if (active) EVASION_BONUS_PERCENT else 0

  fun healAmount(maxHp: Int): Int {
    val safeMax = max(1, maxHp)
    return max(1, (safeMax * HEAL_MAX_HP_PERCENT + 99) / 100)
  }
}
