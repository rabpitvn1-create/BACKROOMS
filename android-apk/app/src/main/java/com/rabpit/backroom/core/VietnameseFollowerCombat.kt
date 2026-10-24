package com.rabpit.backroom.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Executes the two followers' approved codex effects against authoritative Party state. */
internal object VietnameseFollowerCombat {
  // Existing gameplay value from patch-entity-combat-durability.py; only the new
  // followers use this restored gate here, leaving Cao Minh's current path intact.
  private const val ENTITY_EVASION_PERCENT = 25
  private const val FIRST_AID_KEY = "combat.namFirstAidUsed"

  data class Result(
    val state: GameState,
    val combat: CombatRuntime.Snapshot,
    val enemyAccuracyPenalty: Int = 0,
    val log: List<String> = emptyList(),
    val feedback: List<CombatRuntime.DamageFeedback> = emptyList()
  )

  fun isPartyAction(actionKind: String, action: String): Boolean {
    if (actionKind.uppercase(Locale.ROOT) in setOf("PARTY_ATTACK", "PARTY_GUARD", "PARTY_EVADE", "PARTY_READ")) return true
    val text = action.lowercase(Locale.ROOT)
    if (Regex("(?:đừng|không|chưa|ngừng|dừng)\\s+(?:cùng\\s+)?(?:tấn công|bắn|đánh|phòng thủ|né|quan sát)").containsMatchIn(text)) return false
    return Regex("(?:(?:cả\\s+)?(?:party|nhóm|đội)|mọi người)\\s+(?:cùng\\s+)?(?:tấn công|phòng thủ|né|quan sát|đọc)")
      .containsMatchIn(text) || Regex("cùng\\s+(?:tấn công|phòng thủ|né|quan sát)").containsMatchIn(text)
  }

  fun beforeEnemyResponse(
    input: GameState,
    snapshot: CombatRuntime.Snapshot,
    profile: CombatRuntime.Profile,
    intent: CombatRuntime.Intent,
    actionKind: String,
    action: String,
    random: (String, Int) -> Int = { salt, bound -> roll(snapshot, salt, bound) }
  ): Result {
    if (!isPartyAction(actionKind, action)) return Result(input, snapshot)
    var state = input
    var combat = snapshot
    val log = mutableListOf<String>()
    val feedback = mutableListOf<CombatRuntime.DamageFeedback>()
    val activeIds = listOf(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID).filter { active(input, it) }
    var penalty = 0
    when (intent) {
      CombatRuntime.Intent.READ -> {
        for (id in activeIds) {
          combat = combat.copy(opening = min(3, combat.opening + 1))
          log += if (id == NGUYEN_QUOC_NAM_ID) "Nguyễn Quốc Nam — Đánh Giá Nguy Cơ: +1 Opening (tối đa 3)."
            else "Hứa Tiểu Lan — Ngắm Qua Hoa Cúc: +1 Opening (tối đa 3)."
        }
      }
      CombatRuntime.Intent.EVADE -> {
        penalty = activeIds.size * 12
        for (id in activeIds) log += if (id == NGUYEN_QUOC_NAM_ID)
          "Nguyễn Quốc Nam — Giữ Khoảng Cách: Entity -12 điểm Accuracy trong phản ứng này."
        else "Hứa Tiểu Lan — Bộ Pháp Tà Lụa: Entity -12 điểm Accuracy trong phản ứng này."
      }
      CombatRuntime.Intent.GUARD -> {
        if (NGUYEN_QUOC_NAM_ID in activeIds && state.metadata[FIRST_AID_KEY] != combat.encounterId) {
          val target = woundedTarget(state)
          if (target != null) {
            val hp = target.vitalState.currentHp
            val maxHp = CharacterStatEngine.effective(state, target.id).maxHp
            val heal = min(maxHp - hp, (maxHp.toLong() * 12L / 100L).toInt())
            if (heal > 0) {
              state = CharacterStatEngine.setCurrentHp(state, target.id, hp + heal)
                .let { it.copy(metadata = it.metadata + (FIRST_AID_KEY to combat.encounterId)) }
              if (target.id == CAO_MINH_ID) combat = combat.copy(playerHp = hp + heal)
              log += "Nguyễn Quốc Nam — Sơ Cứu Tại Chỗ: ${target.name} +$heal HP; đã dùng cho encounter này."
            }
          }
        }
      }
      CombatRuntime.Intent.ATTACK -> {
        val ultimate = PokerDiceCore.isFinalizedFor(state, action, combat.encounterId) &&
          PokerDiceCore.diceJson(state)?.optString("hand") in setOf("SSF", "FSF")
        for (id in activeIds) {
          if (combat.entityHp <= 0) break
          val weapon = equippedWeaponDamage(state, id) ?: continue
          val name = state.characters.getValue(id).name
          if (ultimate) {
            val percent = if (id == NGUYEN_QUOC_NAM_ID) 285 else 320
            val skill = if (id == NGUYEN_QUOC_NAM_ID) "Lời Hứa Trở Về" else "Bách Hoa Nhất Tiễn"
            val hit = damage(combat, weapon, percent, profile.armor, id)
            combat = hit.first; hit.second?.let(feedback::add)
            log += "$name — $skill: -${hit.second?.amount ?: 0} HP; bỏ qua Evasion, không Critical hoặc proc bồi."
            continue
          }
          val bonus = if (id == NGUYEN_QUOC_NAM_ID) 8 else 12
          val range = when (combat.range) {
            CombatRuntime.RangeBand.CLOSE -> 18
            CombatRuntime.RangeBand.NEAR -> 10
            CombatRuntime.RangeBand.FAR -> -5
          }
          val accuracy = CharacterStatEngine.attackAccuracy(state, id,
            58 + range + combat.opening * 11 + combat.momentum * 6 + PokerDiceCore.accuracyBonus(state) + bonus)
          val effective = CharacterStatEngine.effective(state, id)
          val evasion = (ENTITY_EVASION_PERCENT - effective.resEvasionPercent).coerceAtLeast(0)
          if (random("$id:accuracy", 100) >= accuracy || random("$id:evasion", 100) < evasion) {
            log += "$name bắn hụt hoặc bị ${combat.entityName} né tránh."
            continue
          }
          val critical = random("$id:critical", 100) < CaoMinhCombatPassive.allyCriticalChance(effective.criticalChancePercent)
          val base = CharacterStatCore.basicDamage(weapon, effective.str)
          val normal = if (critical) CharacterStatCore.criticalDamage(base) else base
          val hit = damage(combat, normal, 100, profile.armor, id, critical)
          combat = hit.first; hit.second?.let(feedback::add)
          log += "$name bắn trúng ${combat.entityName}: -${hit.second?.amount ?: 0} HP."
          if (combat.entityHp > 0 && random("$id:proc", 100) < 25) {
            val armor = if (id == HUA_TIEU_LAN_ID) profile.armor * 80 / 100 else profile.armor
            val percent = if (id == NGUYEN_QUOC_NAM_ID) 120 else 150
            val proc = damage(combat, weapon, percent, armor, id)
            combat = proc.first; proc.second?.let(feedback::add)
            val skill = if (id == NGUYEN_QUOC_NAM_ID) "Phát Bắn Kiềm Chế" else "Một Phát Định Đoạt"
            log += "$name — $skill: -${proc.second?.amount ?: 0} HP."
          }
        }
      }
      else -> Unit
    }
    return Result(state, combat, penalty, log, feedback)
  }

  fun afterEnemyMiss(
    state: GameState,
    snapshot: CombatRuntime.Snapshot,
    profile: CombatRuntime.Profile,
    random: (String, Int) -> Int = { salt, bound -> roll(snapshot, salt, bound) }
  ): Result {
    if (snapshot.entityHp <= 0 || !active(state, HUA_TIEU_LAN_ID)) return Result(state, snapshot)
    val weapon = equippedWeaponDamage(state, HUA_TIEU_LAN_ID) ?: return Result(state, snapshot)
    if (random("$HUA_TIEU_LAN_ID:counter", 100) >= 20) return Result(state, snapshot)
    val hit = damage(snapshot, weapon, 110, profile.armor, HUA_TIEU_LAN_ID)
    return Result(state, hit.first,
      log = listOf("Hứa Tiểu Lan — Tài Bắn Phản Xạ: -${hit.second?.amount ?: 0} HP."),
      feedback = listOfNotNull(hit.second))
  }

  private fun active(state: GameState, id: String): Boolean {
    val actor = state.characters[id] ?: return false
    return id in state.party.memberIds && actor.presence == CharacterPresence.ACTIVE &&
      actor.vitalState.currentHp > 0 && actor.vitalState.condition != CharacterCondition.DEAD
  }

  private fun equippedWeaponDamage(state: GameState, id: String): Int? {
    val actor = state.characters[id] ?: return null
    val weaponId = state.equipment[actor.equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return null
    val owned = state.inventories[actor.inventoryId]?.items?.get(weaponId) ?: return null
    if (owned.quantity <= 0) return null
    return EquipmentCatalog.definition(weaponId)?.weapon?.dmg?.takeIf { it > 0 }
  }

  private fun woundedTarget(state: GameState): CharacterState? = state.party.memberIds.distinct()
    .mapNotNull(state.characters::get)
    .filter { active(state, it.id) && it.vitalState.currentHp < CharacterStatEngine.effective(state, it.id).maxHp }
    .minWithOrNull { a, b ->
      val aMax = CharacterStatEngine.effective(state, a.id).maxHp
      val bMax = CharacterStatEngine.effective(state, b.id).maxHp
      (a.vitalState.currentHp.toLong() * bMax).compareTo(b.vitalState.currentHp.toLong() * aMax)
    }

  private fun damage(
    snapshot: CombatRuntime.Snapshot, weapon: Int, percent: Int, armor: Int, id: String, critical: Boolean = false
  ): Pair<CombatRuntime.Snapshot, CombatRuntime.DamageFeedback?> {
    val raw = max(1, CharacterStatCore.scaleByPercent(weapon, percent) - armor)
    val dealt = min(snapshot.entityHp, raw)
    return snapshot.copy(entityHp = snapshot.entityHp - dealt) to
      dealt.takeIf { it > 0 }?.let { CombatRuntime.DamageFeedback("entity", it, critical, id) }
  }

  private fun roll(snapshot: CombatRuntime.Snapshot, salt: String, bound: Int): Int {
    var x = snapshot.seed xor (snapshot.eventCounter.toLong() * -7046029254386353131L) xor salt.hashCode().toLong()
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    return (((x xor (x ushr 31)) and Long.MAX_VALUE) % bound.coerceAtLeast(1)).toInt()
  }
}
