package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Save-persistent 5D6 Poker Dice control state ported from BACKROOMsV2.
 *
 * This object owns dice values, HOLD state, reroll budget, finalization and deterministic replay.
 * CombatRuntime remains authoritative for the actual BACKROOMS damage/party/canon rules.
 */
object PokerDiceRuntime {
  const val DICE_COUNT = 5
  const val MAX_REROLLS = 3

  private const val ROOT = "combat.poker."
  private const val VALUES = ROOT + "values"
  private const val HELD = ROOT + "held"
  private const val HAS_ROLLED = ROOT + "hasRolled"
  private const val REROLLS_USED = ROOT + "rerollsUsed"
  private const val FINALIZED = ROOT + "finalized"
  private const val RESOLVED = ROOT + "resolved"
  private const val HAND = ROOT + "hand"
  private const val RNG_SEQUENCE = ROOT + "rngSequence"

  data class DiceState(
    val values: List<Int>,
    val held: List<Boolean>,
    val hasRolled: Boolean,
    val rerollsUsed: Int,
    val finalized: Boolean,
    val resolved: Boolean,
    val hand: String
  )

  fun ensureInitialRoll(state: GameState): GameState {
    val combat = CombatRuntime.active(state) ?: return state
    val existing = decode(state)
    if (existing?.hasRolled == true) return state

    val empty = existing ?: DiceState(
      values = List(DICE_COUNT) { 0 },
      held = List(DICE_COUNT) { false },
      hasRolled = false,
      rerollsUsed = 0,
      finalized = false,
      resolved = false,
      hand = ""
    )
    return rollDice(state, combat.seed, empty, respectHeld = false, rerollsUsed = 0)
  }

  fun setHold(state: GameState, dieIndex: Int, held: Boolean): GameState {
    requireActive(state)
    require(dieIndex in 0 until DICE_COUNT) { "Die index không hợp lệ." }
    val dice = decode(state) ?: throw IllegalStateException("Combat dice state bị thiếu.")
    check(dice.hasRolled) { "Phải ROLL trước khi HOLD." }
    check(!dice.finalized) { "Hand đã được chốt." }

    val nextHeld = dice.held.toMutableList()
    nextHeld[dieIndex] = held
    return encode(state, dice.copy(held = nextHeld))
  }

  fun roll(state: GameState): GameState {
    val combat = requireActive(state)
    val dice = decode(ensureInitialRoll(state))
      ?: throw IllegalStateException("Combat dice state bị thiếu.")
    if (dice.finalized || dice.rerollsUsed >= MAX_REROLLS || dice.held.all { it }) return state
    return rollDice(state, combat.seed, dice, respectHeld = true, rerollsUsed = dice.rerollsUsed + 1)
  }

  fun finish(state: GameState): GameState {
    requireActive(state)
    val dice = decode(ensureInitialRoll(state))
      ?: throw IllegalStateException("Combat dice state bị thiếu.")
    check(dice.hasRolled) { "Dice chưa có Initial Roll." }
    if (dice.finalized) return state
    return encode(state, dice.copy(finalized = true, hand = classify(*dice.values.toIntArray())))
  }

  fun markResolved(state: GameState): GameState {
    requireActive(state)
    val dice = decode(state) ?: throw IllegalStateException("Combat dice state bị thiếu.")
    check(dice.finalized) { "Hand chưa được FINISH." }
    if (dice.resolved) return state
    return encode(state, dice.copy(resolved = true))
  }

  fun currentResolvedHand(state: GameState): String {
    if (CombatRuntime.active(state) == null) return ""
    val dice = decode(state) ?: return ""
    return if (dice.finalized && dice.resolved) dice.hand else ""
  }

  fun prepareNext(state: GameState): GameState {
    if (CombatRuntime.active(state) == null) return state
    return ensureInitialRoll(clearHand(state))
  }

  fun toJson(state: GameState): JSONObject? {
    if (CombatRuntime.active(state) == null) return null
    val dice = decode(state) ?: return null
    return JSONObject().apply {
      put("values", JSONArray().apply { dice.values.forEach { put(it) } })
      put("held", JSONArray().apply { dice.held.forEach { put(it) } })
      put("hasRolled", dice.hasRolled)
      put("rerollsUsed", dice.rerollsUsed)
      put("maxRerolls", MAX_REROLLS)
      put("finalized", dice.finalized)
      put("resolved", dice.resolved)
      put("hand", dice.hand)
    }
  }

  fun classify(vararg dice: Int): String {
    if (dice.size != DICE_COUNT || dice.any { it !in 1..6 }) return "NO HAND"
    if (dice.all { it == dice[0] }) return "FSF"
    if (dice.contentEquals(intArrayOf(1, 2, 3, 4, 5)) ||
        dice.contentEquals(intArrayOf(5, 4, 3, 2, 1))) return "SSF"
    if (dice.contentEquals(intArrayOf(2, 3, 4, 5, 6)) ||
        dice.contentEquals(intArrayOf(6, 5, 4, 3, 2))) return "STRAIGHT"

    val counts = IntArray(7)
    dice.forEach { counts[it] += 1 }
    if ((1..6).any { counts[it] == 4 }) return "FOUR OF A KIND"
    val triple = (1..6).any { counts[it] == 3 }
    val pairs = (1..6).count { counts[it] == 2 }
    if (triple && pairs == 1) return "FULL HOUSE"
    if (triple) return "THREE OF A KIND"
    if (pairs >= 2) return "TWO PAIR"
    if (pairs == 1) return "ONE PAIR"
    return "NO HAND"
  }

  /** Same hand scaling used by BACKROOMsV2. */
  fun damagePercent(hand: String): Int = when (hand) {
    "ONE PAIR" -> 125
    "STRAIGHT" -> 150
    "FULL HOUSE" -> 200
    "FOUR OF A KIND" -> 250
    "FSF" -> 200
    else -> 100
  }

  fun evadeResponse(hand: String): Boolean = hand == "TWO PAIR"
  fun ultimateHand(hand: String): Boolean = hand == "SSF" || hand == "FSF"
  fun ultimateMultiplier(hand: String): Int = if (hand == "FSF") 2 else 1

  private fun requireActive(state: GameState): CombatRuntime.Snapshot =
    CombatRuntime.active(state) ?: throw IllegalStateException("Không có trận chiến đang hoạt động.")

  private fun rollDice(
    state: GameState,
    seed: Long,
    dice: DiceState,
    respectHeld: Boolean,
    rerollsUsed: Int
  ): GameState {
    val values = dice.values.toMutableList()
    val held = dice.held.toMutableList()
    var sequence = state.metadata[RNG_SEQUENCE]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    for (index in 0 until DICE_COUNT) {
      if (respectHeld && held[index]) continue
      values[index] = deterministicDie(seed, sequence, index)
      sequence += 1
    }

    val hand = classify(*values.toIntArray())
    val counts = IntArray(7)
    values.forEach { if (it in 1..6) counts[it] += 1 }
    for (index in values.indices) {
      val value = values[index]
      if (value in 1..6 && counts[value] >= 2) held[index] = true
    }

    val rolled = dice.copy(
      values = values,
      held = held,
      hasRolled = true,
      rerollsUsed = rerollsUsed.coerceIn(0, MAX_REROLLS),
      finalized = false,
      resolved = false,
      hand = hand
    )
    val metadata = encode(state, rolled).metadata.toMutableMap()
    metadata[RNG_SEQUENCE] = sequence.toString()
    return state.copy(metadata = metadata)
  }

  private fun encode(state: GameState, dice: DiceState): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[VALUES] = dice.values.joinToString(",")
    metadata[HELD] = dice.held.joinToString(",") { if (it) "1" else "0" }
    metadata[HAS_ROLLED] = dice.hasRolled.toString()
    metadata[REROLLS_USED] = dice.rerollsUsed.toString()
    metadata[FINALIZED] = dice.finalized.toString()
    metadata[RESOLVED] = dice.resolved.toString()
    metadata[HAND] = dice.hand
    return state.copy(metadata = metadata)
  }

  private fun decode(state: GameState): DiceState? {
    val rawValues = state.metadata[VALUES] ?: return null
    val rawHeld = state.metadata[HELD] ?: return null
    val values = rawValues.split(",").mapNotNull { it.toIntOrNull() }
    val held = rawHeld.split(",").map { it == "1" || it.equals("true", true) }
    if (values.size != DICE_COUNT || held.size != DICE_COUNT || values.any { it !in 0..6 }) return null
    return DiceState(
      values = values,
      held = held,
      hasRolled = state.metadata[HAS_ROLLED].toBoolean(),
      rerollsUsed = state.metadata[REROLLS_USED]?.toIntOrNull()?.coerceIn(0, MAX_REROLLS) ?: 0,
      finalized = state.metadata[FINALIZED].toBoolean(),
      resolved = state.metadata[RESOLVED].toBoolean(),
      hand = state.metadata[HAND].orEmpty()
    )
  }

  private fun clearHand(state: GameState): GameState {
    val metadata = state.metadata.toMutableMap()
    listOf(VALUES, HELD, HAS_ROLLED, REROLLS_USED, FINALIZED, RESOLVED, HAND).forEach(metadata::remove)
    return state.copy(metadata = metadata)
  }

  private fun deterministicDie(seed: Long, sequence: Int, dieIndex: Int): Int {
    val mixed = mix(seed xor (dieIndex.toLong() * 0x9E3779B9L), sequence)
    return 1 + ((mixed and Long.MAX_VALUE) % 6L).toInt()
  }

  private fun mix(seed: Long, counter: Int): Long {
    var x = seed xor (counter.toLong() * -7046029254386353131L)
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    return x xor (x ushr 31)
  }
}
