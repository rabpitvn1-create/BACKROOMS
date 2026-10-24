package com.rabpit.backroom.core

import kotlin.math.max
import kotlin.math.min

/**
 * Canon special-follower attack resolution. Existing VietnameseFollowerCombat
 * remains the authority for Nam/Lan, and noncombatant An Nhiên never attacks.
 * Proc rolls depend on encounter seed and turn, not the narrator or WebView.
 */
internal object RestoredCompanionCombat {
  data class Result(
    val state: GameState,
    val combat: CombatRuntime.Snapshot,
    val log: List<String> = emptyList(),
    val feedback: List<CombatRuntime.DamageFeedback> = emptyList()
  )

  fun beforeEnemyResponse(
    state: GameState,
    snapshot: CombatRuntime.Snapshot,
    profile: CombatRuntime.Profile,
    intent: CombatRuntime.Intent,
    actionKind: String,
    action: String
  ): Result {
    if (!VietnameseFollowerCombat.isPartyAction(actionKind, action) ||
      intent != CombatRuntime.Intent.ATTACK || snapshot.entityHp <= 0) return Result(state, snapshot)

    var combat = snapshot
    val log = mutableListOf<String>()
    val feedback = mutableListOf<CombatRuntime.DamageFeedback>()
    for (id in listOf(LUC_TRAM_ID, SYVIAL_ID, "lucia", "catherine", "janet", "trac_lam")) {
      val character = state.characters[id] ?: continue
      if (id !in state.party.memberIds ||
        character.presence != CharacterPresence.ACTIVE ||
        character.vitalState.currentHp <= 0 || combat.entityHp <= 0) continue
      if (id == "trac_lam" && character.metadata["combatant"] == "false") continue
      val weaponId = state.equipment[character.equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: continue
      val weapon = EquipmentCatalog.definition(weaponId)?.weapon?.dmg ?: continue
      if (weapon <= 0) continue
      val shot = roll(snapshot, "$id:accuracy", 100)
      val accuracy = CharacterStatEngine.attackAccuracy(state, id,
        58 + combat.opening * 10 + combat.momentum * 5).coerceIn(1, 99)
      if (shot >= accuracy) {
        log += "${character.name} tấn công nhưng không trúng ${combat.entityName}."
        continue
      }
      val crit = roll(snapshot, "$id:critical", 100) <
        CharacterStatEngine.effective(state, id).criticalChancePercent
      var raw = CharacterStatCore.basicDamage(weapon,
        CharacterStatEngine.effective(state, id).str)
      if (crit) raw = CharacterStatCore.criticalDamage(raw)
      var dealt = min(combat.entityHp, max(1, raw - profile.armor))
      combat = combat.copy(entityHp = combat.entityHp - dealt)
      feedback += CombatRuntime.DamageFeedback("entity", dealt, crit, id)
      log += "${character.name} đánh trúng ${combat.entityName}: -$dealt HP."
      if (combat.entityHp <= 0) break

      val procPercent = when(id) {
        SYVIAL_ID -> 30
        LUC_TRAM_ID -> 30
        "catherine" -> 30
        "janet" -> 30
        "trac_lam" -> 30
        else -> 0
      }
      if (procPercent > 0 && roll(snapshot, "$id:proc", 100) < procPercent) {
        val multiplier = when(id) {
          SYVIAL_ID -> 175
          LUC_TRAM_ID -> 130
          "catherine", "janet" -> 125
          "trac_lam" -> 120
          else -> 100
        }
        val armor = if (id == SYVIAL_ID) profile.armor * 80 / 100 else profile.armor
        val scaled = (weapon.toLong() * multiplier.toLong() / 100L)
          .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        dealt = min(combat.entityHp, max(1, scaled - armor))
        if (dealt > 0) {
          combat = combat.copy(entityHp = combat.entityHp - dealt)
          val skill = when(id) {
            SYVIAL_ID -> "Rift Sever"
            LUC_TRAM_ID -> "Thiên Kiếm Thất Thức"
            "catherine" -> "Khúc Đạn Hồi Âm"
            "janet" -> "Bayou Swing"
            else -> "Song Nòng Tiếp Ứng"
          }
          feedback += CombatRuntime.DamageFeedback("entity", dealt, actorId=id, skill=skill)
          log += "${character.name} — $skill: -$dealt HP."
        }
      }
    }
    return Result(state, combat, log, feedback)
  }

  fun afterEnemyMiss(
    state: GameState,
    snapshot: CombatRuntime.Snapshot,
    profile: CombatRuntime.Profile
  ): Result {
    val id = SYVIAL_ID
    val actor = state.characters[id] ?: return Result(state, snapshot)
    if (id !in state.party.memberIds || actor.presence != CharacterPresence.ACTIVE ||
      actor.vitalState.currentHp <= 0 || snapshot.entityHp <= 0) return Result(state, snapshot)
    val weaponId = state.equipment[actor.equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key)
      ?: return Result(state, snapshot)
    val weapon = EquipmentCatalog.definition(weaponId)?.weapon?.dmg
      ?.takeIf { it > 0 } ?: return Result(state, snapshot)
    if (roll(snapshot, "$id:counterphase", 100) >= 30) return Result(state, snapshot)
    val raw = (weapon.toLong() * 125L / 100L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    val dealt = min(snapshot.entityHp, max(1, raw - profile.armor))
    val next = snapshot.copy(entityHp = snapshot.entityHp - dealt)
    return Result(state, next,
      listOf("Syvial — Counterphase: -$dealt HP."),
      listOf(CombatRuntime.DamageFeedback("entity", dealt, actorId=id, skill="Counterphase")))
  }

  private fun roll(c: CombatRuntime.Snapshot, salt: String, bound: Int): Int {
    var value = c.seed xor (c.eventCounter.toLong() * -7046029254386353131L) xor salt.hashCode().toLong()
    value = (value xor (value ushr 30)) * -4658895280553007687L
    value = (value xor (value ushr 27)) * -7723592293110705685L
    return (((value xor (value ushr 31)) and Long.MAX_VALUE) % bound).toInt()
  }
}
