package com.rabpit.backroom.core

/** Party health is a projection of canonical character vitals, not an extra HP pool. */
object PartyCombatHealth {
  data class DamageResult(
    val state: GameState,
    val appliedDamage: Int,
    val damageByMember: Map<String, Int>,
    val dodgedIds: List<String> = emptyList(),
    val criticalIds: List<String> = emptyList()
  )

  /** Includes fallen members in the max-HP projection until the encounter roster changes. */
  fun members(state: GameState): List<String> =
    state.party.memberIds.distinct().take(state.party.maxMembers.coerceIn(1, 4)).filter { id ->
      state.characters[id]?.presence == CharacterPresence.ACTIVE
    }

  fun actingMembers(state: GameState): List<String> = members(state).filter { id ->
    (state.characters[id]?.vitalState?.currentHp ?: 0) > 0
  }

  fun currentHp(state: GameState): Int = members(state).sumOf { id ->
    state.characters.getValue(id).vitalState.currentHp.coerceAtLeast(0)
  }

  fun maxHp(state: GameState): Int = members(state).sumOf { id ->
    CharacterStatEngine.effective(state, id).maxHp
  }

  /**
   * Distributes an Entity's ordinary physical hit once, then resolves each defender's
   * EVA, DEX critical resistance and VIT-based DEF. Dodged or blocked damage is not
   * redistributed to teammates; otherwise evasion would have no protective value.
   * Caller supplies deterministic 0..9999 rolls for reproducible combat resolution.
   */
  fun applyStatAdjustedDamage(
    state: GameState,
    requestedDamage: Int,
    round: Int,
    dodgeRoll: (Int) -> Int,
    criticalRoll: (Int) -> Int,
    leaderEvasionBonusPercent: Int = 0
  ): DamageResult {
    if (requestedDamage <= 0) return DamageResult(state, 0, emptyMap())
    val ids = actingMembers(state)
    if (ids.isEmpty()) return DamageResult(state, 0, emptyMap())
    val base = requestedDamage / ids.size
    val remainder = requestedDamage % ids.size
    val first = Math.floorMod(round, ids.size)
    var next = state
    var applied = 0
    val losses = linkedMapOf<String, Int>()
    val dodged = mutableListOf<String>()
    val critical = mutableListOf<String>()
    for (position in ids.indices) {
      val index = (first + position) % ids.size
      val id = ids[index]
      val raw = base + if (position < remainder) 1 else 0
      if (raw == 0) continue
      val effective = CharacterStatEngine.effective(next, id)
      val dodgeChance = (effective.evasionBasisPoints +
        if (id == KAI_ID) leaderEvasionBonusPercent.coerceAtLeast(0) * 100 else 0).coerceIn(0, 10000)
      if (dodgeRoll(index) < dodgeChance) {
        dodged += id
        continue
      }
      // Entities have a 5% baseline critical chance; DEX offsets it per target.
      val isCritical = criticalRoll(index) < (500 - effective.resCriticalBasisPoints).coerceAtLeast(0)
      if (isCritical) critical += id
      val incoming = if (isCritical) CharacterStatCore.criticalDamage(raw) else raw
      val mitigated = CharacterStatCore.defendedIncomingDamage(incoming, effective.def)
      val hp = next.characters.getValue(id).vitalState.currentHp
      val loss = mitigated.coerceAtMost(hp)
      if (loss > 0) {
        next = CharacterStatEngine.setCurrentHp(next, id, hp - loss)
        losses[id] = loss
        applied += loss
      }
    }
    return DamageResult(next, applied, losses, dodged, critical)
  }

  /**
   * Spreads one resolved ordinary hit across living party members. The sum of
   * individual losses is exactly min(requestedDamage, current party HP).
   * A rotating remainder avoids always charging the leader for odd damage.
   * Targeted skills and per-character status ticks must NOT call this helper.
   */
  fun applySharedDamage(state: GameState, requestedDamage: Int, round: Int): DamageResult {
    if (requestedDamage <= 0) return DamageResult(state, 0, emptyMap())
    val ids = actingMembers(state)
    if (ids.isEmpty()) return DamageResult(state, 0, emptyMap())

    val remainingHp = ids.associateWithTo(linkedMapOf()) { id ->
      state.characters.getValue(id).vitalState.currentHp.coerceAtLeast(0)
    }
    var remainingDamage = requestedDamage.toLong().coerceAtMost(remainingHp.values.sumOf { it.toLong() })
    val applied = remainingDamage.toInt()
    while (remainingDamage > 0) {
      val live = ids.filter { remainingHp.getValue(it) > 0 }
      if (live.isEmpty()) break
      val count = live.size
      val start = ((round % count) + count) % count
      val share = remainingDamage / count
      val excess = (remainingDamage % count).toInt()
      var spent = 0L
      for (index in live.indices) {
        val id = live[(start + index) % count]
        val requested = share + if (index < excess) 1L else 0L
        val deducted = minOf(remainingHp.getValue(id).toLong(), requested).toInt()
        remainingHp[id] = remainingHp.getValue(id) - deducted
        spent += deducted
      }
      check(spent > 0L) { "Shared combat damage made no progress" }
      remainingDamage -= spent
    }

    var next = state
    val damageByMember = linkedMapOf<String, Int>()
    for (id in ids) {
      val loss = state.characters.getValue(id).vitalState.currentHp - remainingHp.getValue(id)
      if (loss > 0) {
        next = CharacterStatEngine.setCurrentHp(next, id, remainingHp.getValue(id))
        damageByMember[id] = loss
      }
    }
    return DamageResult(next, applied, damageByMember)
  }
}
