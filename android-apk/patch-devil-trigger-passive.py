from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
ENGINE = CORE / "DevilTriggerPassive.kt"
ENGINE_TEST = TESTS / "DevilTriggerPassiveTest.kt"
INTEGRATION_TEST = TESTS / "DevilTriggerCombatIntegrationTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Pure state machine. Gameplay-only balance layer:
# READY -> ACTIVE(3) -> COOLDOWN(5) -> READY.
# Cooldown turns never perform the 30% roll. No backlash/debuff/resource cost.
# ---------------------------------------------------------------------------
ENGINE.write_text(r'''package com.rabpit.backroom.core

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
  const val TRIGGER_PERCENT = 30
  const val ACTIVE_TURNS = 3
  const val COOLDOWN_TURNS = 5
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
      else DevilTriggerState(cooldownTurns = COOLDOWN_TURNS)
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
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Skill catalog. Replace Syvial's old unlimited STATE implementation rather
# than stacking a second Devil Trigger. Kai receives the matching passive.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
old_lucifer_core = '    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Miễn cơ chế cạn Mana/Energy/Overheat nội tại; hồi 2% Max HP mỗi turn, 4% khi Devil Trigger.", "Không hồi từ 0 HP."),\n'
new_lucifer_core = '    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Miễn cơ chế cạn Mana/Energy/Overheat nội tại; hồi 2% Max HP mỗi combat turn khi Devil Trigger không hoạt động. Trong Devil Trigger, tick hồi riêng của Passive thay thế bằng 5% Max HP.", "Không hồi từ 0 HP."),\n'
catalog = replace_once(catalog, old_lucifer_core, new_lucifer_core, "Syvial Lucifer Core regen description")

old_syvial_dt = '    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% outgoing DMG, +20% Evasion, -20% incoming DMG theo vai trò cá nhân; hồi phục Lucifer Core tăng lên 4% Max HP/turn.", "Không cooldown nội tại, không giới hạn thời gian canon."),\n'
new_syvial_dt = '    s("DEVIL TRIGGER — Lucifer Core", "PASSIVE", "READY: 30% mỗi combat turn; ACTIVE 3 turn; sau đó COOLDOWN 5 turn không roll", "+100% Evasion, DMG ×5 và hồi đúng 5% Max HP một lần ở mỗi turn Devil Trigger đang hoạt động.", "Gameplay lock: READY → 30% Trigger → DEVIL TRIGGER (3 Turns) → COOLDOWN (5 Turns) → READY. Không thêm tiêu hao HP, phản phệ, mất kiểm soát, giới hạn quỷ lực hoặc debuff."),\n'
catalog = replace_once(catalog, old_syvial_dt, new_syvial_dt, "Syvial Devil Trigger catalog override")

kai_anchor = '  private val kai = listOf(\n'
kai_new = '  private val kai = listOf(\n    s("DEVIL TRIGGER — Sparda Core", "PASSIVE", "READY: 30% mỗi combat turn; ACTIVE 3 turn; sau đó COOLDOWN 5 turn không roll", "+100% Evasion, DMG ×5 và hồi đúng 5% Max HP một lần ở mỗi turn Devil Trigger đang hoạt động.", "Gameplay lock: READY → 30% Trigger → DEVIL TRIGGER (3 Turns) → COOLDOWN (5 Turns) → READY. Không thêm tiêu hao HP, phản phệ, mất kiểm soát, giới hạn quỷ lực hoặc debuff."),\n'
catalog = replace_once(catalog, kai_anchor, kai_new, "Kai Devil Trigger catalog entry")
CATALOG.write_text(catalog, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Final CombatRuntime layer, applied after all existing combat transforms.
# State uses non-combat metadata so cooldown survives encounter cleanup.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val AN_NHIEN_ULTIMATE_INTERVAL_TURNS = 5\n'
constants = '''  private const val DEVIL_TRIGGER_KAI_ACTIVE_KEY = "passive.devilTrigger.kai.activeTurns"
  private const val DEVIL_TRIGGER_KAI_COOLDOWN_KEY = "passive.devilTrigger.kai.cooldownTurns"
  private const val DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY = "passive.devilTrigger.syvial.activeTurns"
  private const val DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY = "passive.devilTrigger.syvial.cooldownTurns"
'''
if 'DEVIL_TRIGGER_KAI_ACTIVE_KEY' not in combat:
    combat = replace_once(combat, constant_anchor, constant_anchor + constants, "Devil Trigger metadata constants")

helper_anchor = '  // PARTY_ACTIONS_V1: authoritative roster for one simultaneous Party command.\n'
helpers = r'''  private fun devilTriggerState(state: GameState, characterId: String): DevilTriggerState {
    val activeKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_ACTIVE_KEY else DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY
    val cooldownKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_COOLDOWN_KEY else DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY
    return DevilTriggerState(
      activeTurns = state.metadata[activeKey]?.toIntOrNull() ?: 0,
      cooldownTurns = state.metadata[cooldownKey]?.toIntOrNull() ?: 0
    )
  }

  private fun withDevilTriggerState(state: GameState, characterId: String, value: DevilTriggerState): GameState {
    val activeKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_ACTIVE_KEY else DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY
    val cooldownKey = if (characterId == KAI_ID) DEVIL_TRIGGER_KAI_COOLDOWN_KEY else DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY
    val metadata = state.metadata.toMutableMap()
    if (value.activeTurns > 0) metadata[activeKey] = value.activeTurns.toString() else metadata.remove(activeKey)
    if (value.cooldownTurns > 0) metadata[cooldownKey] = value.cooldownTurns.toString() else metadata.remove(cooldownKey)
    return state.copy(metadata = metadata)
  }

  private fun finishDevilTriggerTurns(
    state: GameState,
    kaiTurn: DevilTriggerTurn?,
    syvialTurn: DevilTriggerTurn?
  ): GameState {
    var next = state
    if (kaiTurn != null) next = withDevilTriggerState(next, KAI_ID, DevilTriggerPassive.endTurn(kaiTurn))
    if (syvialTurn != null) next = withDevilTriggerState(next, SYVIAL_ID, DevilTriggerPassive.endTurn(syvialTurn))
    return next
  }

  private fun healCharacterForDevilTrigger(state: GameState, characterId: String): Pair<GameState, Int> {
    val character = state.characters[characterId] ?: return state to 0
    if (character.vitalState.currentHp <= 0) return state to 0
    val maxHp = CharacterStatEngine.effective(state, characterId).maxHp
    val before = character.vitalState.currentHp
    val next = CharacterStatEngine.setCurrentHp(state, characterId, before + DevilTriggerPassive.healAmount(maxHp))
    val after = next.characters[characterId]?.vitalState?.currentHp ?: before
    return next to max(0, after - before)
  }

'''
if 'private fun devilTriggerState(' not in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "Devil Trigger runtime helpers")

# Finalizer converts the old strict Boolean parser to an equals() compatibility read.
old_syvial_local_candidates = (
    '    var syvialDevilTrigger = state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.equals("true", ignoreCase = true) == true\n',
    '    var syvialDevilTrigger = state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.toBooleanStrictOrNull() ?: false\n',
)
for candidate in old_syvial_local_candidates:
    if candidate in combat:
        combat = combat.replace(candidate, '    var syvialDevilTrigger = false\n', 1)
        break
else:
    if '    var syvialDevilTrigger = false\n' not in combat:
        raise RuntimeError("Syvial legacy Devil Trigger local state anchor missing")

start_anchor = '    var syvialDevilTrigger = false\n'
start_block = r'''    val kaiEligibleForDevilTrigger = resolvedState.characters[KAI_ID]?.let {
      it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0
    } == true
    val kaiDevilTriggerTurn = if (kaiEligibleForDevilTrigger) DevilTriggerPassive.beginTurn(
      devilTriggerState(resolvedState, KAI_ID),
      roll(c.copy(eventCounter = c.eventCounter + 401), 100)
    ) else null
    val kaiDevilTriggerActive = kaiDevilTriggerTurn?.activeThisTurn == true
    if (kaiDevilTriggerTurn != null) {
      resolvedState = withDevilTriggerState(resolvedState, KAI_ID, kaiDevilTriggerTurn.stateAtStart)
      if (kaiDevilTriggerTurn.triggeredThisTurn) log += "DEVIL TRIGGER — Sparda Core kích hoạt trong 3 turn."
      if (kaiDevilTriggerActive) {
        val healed = healCharacterForDevilTrigger(resolvedState, KAI_ID)
        resolvedState = healed.first
        val kaiMaxHp = CharacterStatEngine.effective(resolvedState, KAI_ID).maxHp
        val kaiHp = resolvedState.characters[KAI_ID]?.vitalState?.currentHp ?: c.playerHp
        c = c.copy(playerHp = kaiHp, playerMaxHp = kaiMaxHp)
        log += "DEVIL TRIGGER — Sparda Core hồi Kai +${healed.second} HP (5% Max HP; $kaiHp/$kaiMaxHp)."
      }
    }

    val syvialEligibleForDevilTrigger = activePartyCharacter(resolvedState, SYVIAL_ID) != null
    val syvialDevilTriggerTurn = if (syvialEligibleForDevilTrigger) DevilTriggerPassive.beginTurn(
      devilTriggerState(resolvedState, SYVIAL_ID),
      roll(c.copy(eventCounter = c.eventCounter + 409), 100)
    ) else null
    syvialDevilTrigger = syvialDevilTriggerTurn?.activeThisTurn == true
    if (syvialDevilTriggerTurn != null) {
      resolvedState = withDevilTriggerState(resolvedState, SYVIAL_ID, syvialDevilTriggerTurn.stateAtStart)
      if (syvialDevilTriggerTurn.triggeredThisTurn) log += "DEVIL TRIGGER — Lucifer Core kích hoạt trong 3 turn."
      val syvial = resolvedState.characters[SYVIAL_ID]
      if (syvial != null && syvial.vitalState.currentHp > 0) {
        if (syvialDevilTrigger) {
          val healed = healCharacterForDevilTrigger(resolvedState, SYVIAL_ID)
          resolvedState = healed.first
          val maxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
          val hp = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvial.vitalState.currentHp
          log += "DEVIL TRIGGER — Lucifer Core hồi Syvial +${healed.second} HP (5% Max HP; $hp/$maxHp)."
        } else {
          // Preserve the pre-existing Lucifer Core 2% passive outside Devil Trigger.
          val maxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
          val before = syvial.vitalState.currentHp
          if (before < maxHp) {
            val heal = max(1, (maxHp * 2 + 99) / 100)
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, before + heal)
            val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: before
            log += "Lucifer Core hồi Syvial +${after - before} HP ($after/$maxHp)."
          }
        }
      }
    }
'''
if 'val kaiDevilTriggerTurn =' not in combat:
    combat = replace_once(combat, start_anchor, start_anchor + start_block, "Devil Trigger begin-turn state and healing")

# Remove the old HP<=50% / Diệp Minh permanent Syvial trigger and duplicate regen.
old_syvial_runtime = r'''      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
      val syvialHp = syvialCharacter!!.vitalState.currentHp
      if (!syvialDevilTrigger && (syvialHp * 2 <= syvialMaxHp || c.entityKey == DIEP_MINH_KEY)) {
        syvialDevilTrigger = true
        val metadata = resolvedState.metadata.toMutableMap()
        metadata[SYVIAL_DEVIL_TRIGGER_KEY] = "true"
        resolvedState = resolvedState.copy(metadata = metadata)
        log += "Syvial kích hoạt Devil Trigger."
      }
      val regenPercent = if (syvialDevilTrigger) 4 else 2
      if (syvialHp > 0 && syvialHp < syvialMaxHp) {
        val heal = percentDamage(syvialMaxHp, regenPercent)
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, syvialHp + heal)
        val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvialHp
        log += "Lucifer Core hồi Syvial +${after - syvialHp} HP ($after/$syvialMaxHp)."
      }
'''
if old_syvial_runtime in combat:
    combat = combat.replace(old_syvial_runtime, '', 1)
elif 'syvialHp * 2 <= syvialMaxHp' in combat or 'regenPercent = if (syvialDevilTrigger) 4 else 2' in combat:
    raise RuntimeError("Syvial legacy permanent Devil Trigger block changed unexpectedly")

# Kai direct attack damage.
combat = replace_once(
    combat,
    '          val damage = min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100))\n',
    '          val damage = DevilTriggerPassive.damage(min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100)), kaiDevilTriggerActive)\n',
    "Kai base attack Devil Trigger multiplier",
)

# Kai automatic gun skills.
for old, new, label in (
    ('        val damage = weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor)\n', '        val damage = DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive)\n', 'The Last Requiem multiplier'),
    ('        val damage = weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor)\n', '        val damage = DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive)\n', 'Silent Lullaby multiplier'),
    ('        val damage = weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor)\n', '        val damage = DevilTriggerPassive.damage(weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor), kaiDevilTriggerActive)\n', 'Salvation multiplier'),
):
    combat = replace_once(combat, old, new, label)

combat = replace_once(
    combat,
    '      val bleedDamage = percentDamage(c.entityMaxHp, KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT)\n',
    '      val bleedDamage = DevilTriggerPassive.damage(percentDamage(c.entityMaxHp, KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT), kaiDevilTriggerActive)\n',
    "Kai bleed damage multiplier while transformed",
)

# Guilty Crown remains deterministic/evasion-bypassing, but its exact per-shot damage is x5 during DT.
gco_old = '      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT\n'
gco_new = '''      val perShotDamage = DevilTriggerPassive.damage(KAI_GUILTY_CROWN_DAMAGE_PER_SHOT, kaiDevilTriggerActive)
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * perShotDamage
'''
combat = replace_once(combat, gco_old, gco_new, "Guilty Crown Devil Trigger multiplier")
combat = replace_once(
    combat,
    '        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n',
    '        "mỗi phát -$perShotDamage HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n',
    "Guilty Crown transformed damage log",
)

# Syvial base Party attack and all Devil Trigger-gated damage.
syvial_base_old = '            val potential = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 100, profile.armor)\n'
syvial_base_new = '            val potential = DevilTriggerPassive.damage(companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 100, profile.armor), syvialDevilTrigger)\n'
combat = replace_once(combat, syvial_base_old, syvial_base_new, "Syvial base attack Devil Trigger multiplier")
combat = replace_once(
    combat,
    '      val dtMultiplier = if (syvialDevilTrigger) 125 else 100\n',
    '      val dtMultiplier = if (syvialDevilTrigger) 500 else 100\n',
    "Syvial skill Devil Trigger x5 multiplier",
)

syvial_ult_old = '''        val damage = min(c.entityHp, 24 * 10)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
        log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x 10 HP = -$damage HP; bỏ qua Evasion."
'''
syvial_ult_new = '''        val damagePerHit = DevilTriggerPassive.damage(10, syvialDevilTrigger)
        val damage = min(c.entityHp, 24 * damagePerHit)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
        log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x $damagePerHit HP = -$damage HP; bỏ qua Evasion."
'''
combat = replace_once(combat, syvial_ult_old, syvial_ult_new, "Syvial ultimate Devil Trigger x5")

counter_old = '          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), if (syvialDevilTrigger) 157 else 125, profile.armor)\n'
counter_new = '''          val baseDamage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 125, profile.armor)
          val damage = DevilTriggerPassive.damage(baseDamage, syvialDevilTrigger)
'''
combat = replace_once(combat, counter_old, counter_new, "Syvial Counterphase Devil Trigger multiplier")
combat = replace_once(
    combat,
    '      val bleedDamage = percentDamage(c.entityMaxHp, 4)\n',
    '      val bleedDamage = DevilTriggerPassive.damage(percentDamage(c.entityMaxHp, 4), syvialDevilTrigger)\n',
    "Syvial bleed damage multiplier while transformed",
)

# +100% Evasion for Kai against ordinary Entity response.
enemy_chance_old = '      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty).coerceIn(0, 88)\n'
enemy_chance_new = '''      val kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty - kaiDevilTriggerEvasion).coerceIn(0, 88)
'''
combat = replace_once(combat, enemy_chance_old, enemy_chance_new, "Kai Devil Trigger evasion bonus")

# Diệp Minh's Party-wide attack now respects +100% Evasion for Kai/Syvial while DT is active.
helper_sig_old = '  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {\n'
helper_sig_new = '''  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {
    return damageActivePartyByPercent(state, percent, emptySet())
  }

  private fun damageActivePartyByPercent(state: GameState, percent: Int, evadingCharacterIds: Set<String>): PartyPercentDamage {
'''
combat = replace_once(combat, helper_sig_old, helper_sig_new, "Party percent damage evasion parameter")
party_loop_anchor = '      if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return@forEach\n'
party_loop_new = '''      if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return@forEach
      if (characterId in evadingCharacterIds) {
        lines += "${character.name} né hoàn toàn nhờ +100% Evasion của DEVIL TRIGGER"
        return@forEach
      }
'''
combat = replace_once(combat, party_loop_anchor, party_loop_new, "Party percent damage Devil Trigger evasion gate")

pulse_old = '      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)\n'
pulse_new = '''      // Baseline pulse compatibility: damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)
      val devilTriggerEvaders = listOfNotNull(
        KAI_ID.takeIf { kaiDevilTriggerActive },
        SYVIAL_ID.takeIf { syvialDevilTrigger }
      ).toSet()
      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT, devilTriggerEvaders)
'''
combat = replace_once(combat, pulse_old, pulse_new, "Diệp Minh AoE Devil Trigger evasion")

# Persist end-of-turn state on every encoded resolution path. The fifth cooldown
# turn reaches READY but still does not roll; rolling resumes on the next turn.
resolve_start = combat.index('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n')
resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
resolve = combat[resolve_start:resolve_end]
if 'finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn)' not in resolve:
    count = resolve.count('encode(resolvedState,')
    if count < 1:
        raise RuntimeError("Devil Trigger end-turn persistence: no encode(resolvedState,...) paths found")
    resolve = resolve.replace(
      'encode(resolvedState,',
      'encode(finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn),'
    )
combat = combat[:resolve_start] + resolve + combat[resolve_end:]

for marker in (
    'DEVIL_TRIGGER_KAI_ACTIVE_KEY',
    'passive.devilTrigger.kai.cooldownTurns',
    'val kaiDevilTriggerTurn =',
    'DEVIL TRIGGER — Sparda Core kích hoạt trong 3 turn',
    'DEVIL TRIGGER — Lucifer Core kích hoạt trong 3 turn',
    'DevilTriggerPassive.damage(min(max(1, normalized - profile.armor), max(1, profile.maxHp * 70 / 100)), kaiDevilTriggerActive)',
    'val perShotDamage = DevilTriggerPassive.damage(KAI_GUILTY_CROWN_DAMAGE_PER_SHOT, kaiDevilTriggerActive)',
    'val dtMultiplier = if (syvialDevilTrigger) 500 else 100',
    'val damagePerHit = DevilTriggerPassive.damage(10, syvialDevilTrigger)',
    'kaiDevilTriggerEvasion = DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)',
    'damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT, devilTriggerEvaders)',
    'finishDevilTriggerTurns(resolvedState, kaiDevilTriggerTurn, syvialDevilTriggerTurn)',
):
    if marker not in combat:
        raise RuntimeError("Devil Trigger combat contract missing: " + marker)

for forbidden in (
    'syvialHp * 2 <= syvialMaxHp',
    'regenPercent = if (syvialDevilTrigger) 4 else 2',
    '+25% outgoing DMG, +20% Evasion',
):
    if forbidden in combat:
        raise RuntimeError("Legacy Syvial Devil Trigger behavior remains: " + forbidden)

COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Regression tests: pure timing semantics plus CombatRuntime integration.
# ---------------------------------------------------------------------------
ENGINE_TEST.write_text(r'''package com.rabpit.backroom.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevilTriggerPassiveTest {
  @Test fun readyUsesExactlyThirtyPercentThreshold() {
    assertTrue(DevilTriggerPassive.beginTurn(DevilTriggerState(), 29).triggeredThisTurn)
    assertFalse(DevilTriggerPassive.beginTurn(DevilTriggerState(), 30).triggeredThisTurn)
  }

  @Test fun activeLastsExactlyThreeTurnsThenStartsFiveTurnCooldown() {
    var turn = DevilTriggerPassive.beginTurn(DevilTriggerState(), 0)
    assertTrue(turn.activeThisTurn)
    var state = DevilTriggerPassive.endTurn(turn)
    assertEquals(2, state.activeTurns)

    turn = DevilTriggerPassive.beginTurn(state, 0)
    assertTrue(turn.activeThisTurn)
    state = DevilTriggerPassive.endTurn(turn)
    assertEquals(1, state.activeTurns)

    turn = DevilTriggerPassive.beginTurn(state, 0)
    assertTrue(turn.activeThisTurn)
    state = DevilTriggerPassive.endTurn(turn)
    assertEquals(0, state.activeTurns)
    assertEquals(5, state.cooldownTurns)
  }

  @Test fun cooldownNeverRollsAndReadyReturnsOnlyAfterFifthCooldownTurn() {
    var state = DevilTriggerState(cooldownTurns = 5)
    repeat(5) { index ->
      val turn = DevilTriggerPassive.beginTurn(state, 0) // roll=0 would trigger if READY; it must be ignored here.
      assertFalse(turn.triggeredThisTurn)
      assertFalse(turn.activeThisTurn)
      assertTrue(turn.cooldownThisTurn)
      state = DevilTriggerPassive.endTurn(turn)
      assertEquals(4 - index, state.cooldownTurns)
    }
    assertEquals(DevilTriggerState(), state)
    val next = DevilTriggerPassive.beginTurn(state, 0)
    assertTrue(next.triggeredThisTurn)
    assertTrue(next.activeThisTurn)
  }

  @Test fun activeEffectsAreExactlySpecified() {
    assertEquals(500, DevilTriggerPassive.damage(100, active = true))
    assertEquals(100, DevilTriggerPassive.damage(100, active = false))
    assertEquals(100, DevilTriggerPassive.evasionBonus(active = true))
    assertEquals(0, DevilTriggerPassive.evasionBonus(active = false))
    assertEquals(5, DevilTriggerPassive.healAmount(100))
    assertEquals(7, DevilTriggerPassive.healAmount(140))
  }
}
''', encoding="utf-8")

INTEGRATION_TEST.write_text(r'''package com.rabpit.backroom.core

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevilTriggerCombatIntegrationTest {
  private val kaiActiveKey = "passive.devilTrigger.kai.activeTurns"
  private val syvialActiveKey = "passive.devilTrigger.syvial.activeTurns"

  @Test fun kaiActiveTurnHealsFivePercentAndCountsDownOnce() {
    var state = GameState.initial()
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, maxHp - 20)
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + (kaiActiveKey to "3"))
    val before = state.characters.getValue(KAI_ID).vitalState.currentHp

    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    val after = result.state.characters.getValue(KAI_ID).vitalState.currentHp
    assertEquals(min(maxHp, before + DevilTriggerPassive.healAmount(maxHp)), after)
    assertEquals("2", result.state.metadata[kaiActiveKey])
    assertTrue(result.reply.contains("DEVIL TRIGGER — Sparda Core hồi Kai"))
  }

  @Test fun syvialActiveTurnHealsFivePercentAndCountsDownOnce() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID))
    )
    val maxHp = CharacterStatEngine.effective(state, SYVIAL_ID).maxHp
    state = CharacterStatEngine.setCurrentHp(state, SYVIAL_ID, maxHp - 20)
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + (syvialActiveKey to "3"))
    val before = state.characters.getValue(SYVIAL_ID).vitalState.currentHp

    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    val after = result.state.characters.getValue(SYVIAL_ID).vitalState.currentHp
    assertEquals(min(maxHp, before + DevilTriggerPassive.healAmount(maxHp)), after)
    assertEquals("2", result.state.metadata[syvialActiveKey])
    assertTrue(result.reply.contains("DEVIL TRIGGER — Lucifer Core hồi Syvial"))
  }

  @Test fun kaiGuiltyCrownDamageIsFiveTimesDuringDevilTrigger() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "2",
      kaiActiveKey to "3"
    ))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.reply.contains("Guilty Crown Override"))
    assertTrue(result.reply.contains("mỗi phát -50 HP"))
    assertTrue(result.reply.contains("tổng -1200 HP"))
  }
}
''', encoding="utf-8")

print("DEVIL TRIGGER passive applied: Kai/Syvial READY 30% -> ACTIVE 3 -> COOLDOWN 5 -> READY, +100% Evasion, x5 DMG, 5% Max HP heal/active turn.")
