from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
STATS = CORE / "CharacterEquipmentSystem.kt"
TRIGGER = CORE / "DevilTriggerPassive.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Kai-only Devil Trigger: 35% READY roll, three active turns, then immediately READY.
trigger = TRIGGER.read_text(encoding="utf-8")
trigger = replace_once(trigger, "  const val TRIGGER_PERCENT = 30\n", "  const val TRIGGER_PERCENT = 35\n", "Devil Trigger 35%")
trigger = replace_once(trigger, "  const val COOLDOWN_TURNS = 5\n", "  const val COOLDOWN_TURNS = 0\n", "Devil Trigger no cooldown")
trigger = replace_once(
    trigger,
    "      else DevilTriggerState(cooldownTurns = COOLDOWN_TURNS)\n",
    "      else DevilTriggerState()\n",
    "Devil Trigger returns directly to READY",
)
TRIGGER.write_text(trigger, encoding="utf-8")


catalog = CATALOG.read_text(encoding="utf-8")
catalog = replace_once(
    catalog,
    '    s("DEVIL TRIGGER — Lucifer Core", "PASSIVE", "READY: 30% mỗi combat turn; ACTIVE 3 turn; sau đó COOLDOWN 5 turn không roll", "+100% Evasion, DMG ×5 và hồi đúng 5% Max HP một lần ở mỗi turn Devil Trigger đang hoạt động.", "Gameplay lock: READY → 30% Trigger → DEVIL TRIGGER (3 Turns) → COOLDOWN (5 Turns) → READY. Không thêm tiêu hao HP, phản phệ, mất kiểm soát, giới hạn quỷ lực hoặc debuff."),\n',
    '    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% outgoing DMG, +20% Evasion, -20% incoming DMG theo vai trò cá nhân; hồi phục Lucifer Core tăng lên 4% Max HP/turn.", "Không dùng Kai Devil Trigger Passive; state này thuộc riêng canon Syvial."),\n',
    "remove shared Syvial Devil Trigger passive",
)
catalog = replace_once(
    catalog,
    '    s("DEVIL TRIGGER — Sparda Core", "PASSIVE", "READY: 30% mỗi combat turn; ACTIVE 3 turn; sau đó COOLDOWN 5 turn không roll", "+100% Evasion, DMG ×5 và hồi đúng 5% Max HP một lần ở mỗi turn Devil Trigger đang hoạt động.", "Gameplay lock: READY → 30% Trigger → DEVIL TRIGGER (3 Turns) → COOLDOWN (5 Turns) → READY. Không thêm tiêu hao HP, phản phệ, mất kiểm soát, giới hạn quỷ lực hoặc debuff."),\n',
    '    s("DEVIL TRIGGER — Sparda Core", "PASSIVE", "READY: 35% mỗi combat turn; ACTIVE 3 turn; không cooldown", "+100% Evasion, DMG ×5 và hồi đúng 5% Max HP một lần ở mỗi turn Devil Trigger đang hoạt động.", "Độc quyền Kai. Hết 3 turn trở lại READY ngay; không tiêu hao HP, phản phệ, mất kiểm soát hoặc debuff."),\n    s("Devil Blessing", "PASSIVE", "Khi đồng đội ACTIVE chiến đấu cùng Kai", "+10% DMG và +10% Max HP cho đồng đội; không áp dụng lên Kai.", "Chỉ hoạt động trong combat có Kai."),\n',
    "Kai Devil Trigger and Devil Blessing catalog",
)
CATALOG.write_text(catalog, encoding="utf-8")


combat = COMBAT.read_text(encoding="utf-8")
syvial_start = combat.index("    val syvialEligibleForDevilTrigger =")
syvial_end = combat.index("    when (intent) {", syvial_start)
combat = combat[:syvial_start] + '''    // Kai's probabilistic Passive is exclusive. Syvial keeps her separate canon STATE:
    // HP <= 50% or a Diệp Minh encounter, with no duration or cooldown.
    val syvialDevilTriggerTurn: DevilTriggerTurn? = null
    resolvedState = resolvedState.copy(metadata = resolvedState.metadata -
      setOf(DEVIL_TRIGGER_SYVIAL_ACTIVE_KEY, DEVIL_TRIGGER_SYVIAL_COOLDOWN_KEY))
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    if (syvialCharacter != null) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
      val syvialHp = syvialCharacter.vitalState.currentHp.coerceIn(0, syvialMaxHp)
      val wasActive = resolvedState.metadata[SYVIAL_DEVIL_TRIGGER_KEY].equals("true", true)
      syvialDevilTrigger = wasActive || syvialHp * 2 <= syvialMaxHp || c.entityKey == DIEP_MINH_KEY
      if (syvialDevilTrigger) {
        resolvedState = resolvedState.copy(metadata = resolvedState.metadata + (SYVIAL_DEVIL_TRIGGER_KEY to "true"))
        if (!wasActive) log += "Syvial kích hoạt Devil Trigger theo Lucifer Core."
      }
      val regenPercent = if (syvialDevilTrigger) 4 else 2
      if (syvialHp > 0 && syvialHp < syvialMaxHp) {
        val heal = percentDamage(syvialMaxHp, regenPercent)
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, syvialHp + heal)
        val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvialHp
        log += "Lucifer Core hồi Syvial +${after - syvialHp} HP ($after/$syvialMaxHp)."
      }
    }

''' + combat[syvial_end:]

# +200 Max HP applies once to every finalized monster whose current canonical Max HP exceeds 1000.
old_hp = '    val enhancedEntityMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n'
new_hp = '''    val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }
    val enhancedEntityMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000) 200 else 0
'''
combat = replace_once(combat, old_hp, new_hp, "monster HP tier at encounter start")
old_decode = '    val canonicalMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n'
new_decode = '''    val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }
    val canonicalMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000) 200 else 0
'''
combat = replace_once(combat, old_decode, new_decode, "monster HP tier during save migration")

# Monsters below 1000 Max HP gain exactly +10% outgoing direct attack damage.
old_damage = '          max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })\n'
new_damage = '''          val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
          if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
'''
combat = replace_once(combat, old_damage, new_damage, "sub-1000 monster damage bonus")

# Devil Blessing boosts all shared companion damage calculations by 10%.
combat = replace_once(
    combat,
    '''  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))
''',
    '''  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int {
    val resolved = max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))
    return max(1, (resolved * 110 + 99) / 100)
  }
''',
    "Devil Blessing companion damage",
)
combat = replace_once(
    combat,
    "          val luciaRawBurstDamage = LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor\n",
    "          val luciaRawBurstDamage = (LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor * 110 + 99) / 100\n",
    "Devil Blessing Lucia burst",
)
combat = replace_once(
    combat,
    "        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage((min(c.entityHp, 24 * damagePerHit)), scp173ObservedNow) else (min(c.entityHp, 24 * damagePerHit))\n",
    "        val blessedDamage = min(c.entityHp, (24 * damagePerHit * 110 + 99) / 100)\n        val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(blessedDamage, scp173ObservedNow) else blessedDamage\n",
    "Devil Blessing Syvial ultimate",
)

# Give live companions +10% current and effective Max HP at encounter start, then clamp safely on clear.
combat = replace_once(
    combat,
    "    val started = encode(state, snapshot)\n    return if (entityKey == SCP_173_KEY) scp173InitializeEncounter(started) else started\n",
    '''    var started = encode(state, snapshot)
    started.party.memberIds.filter { it != KAI_ID }.distinct().forEach { companionId ->
      val companion = started.characters[companionId] ?: return@forEach
      if (companion.presence == CharacterPresence.ACTIVE && companion.vitalState.currentHp > 0) {
        val blessingHp = CharacterStatEngine.devilBlessingHpBonus(started, companionId)
        started = CharacterStatEngine.setCurrentHp(started, companionId, companion.vitalState.currentHp + blessingHp)
      }
    }
    return if (entityKey == SCP_173_KEY) scp173InitializeEncounter(started) else started
''',
    "Devil Blessing encounter health grant",
)
combat = replace_once(
    combat,
    '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return scp173RemoveAllTransientStatuses(state.copy(metadata = metadata))
  }
''',
    '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    var next = scp173RemoveAllTransientStatuses(state.copy(metadata = metadata))
    next.party.memberIds.filter { it != KAI_ID }.distinct().forEach { companionId ->
      val hp = next.characters[companionId]?.vitalState?.currentHp ?: return@forEach
      next = CharacterStatEngine.setCurrentHp(next, companionId, hp)
    }
    return next
  }
''',
    "Devil Blessing cleanup clamp",
)
COMBAT.write_text(combat, encoding="utf-8")


stats = STATS.read_text(encoding="utf-8")
stats = replace_once(
    stats,
    '''    return EffectiveCharacterStats(
      maxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1),
''',
    '''    val unblessedMaxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1)
    val devilBlessingHp = devilBlessingHpBonus(state, characterId, unblessedMaxHp)
    return EffectiveCharacterStats(
      maxHp = unblessedMaxHp + devilBlessingHp,
''',
    "Devil Blessing effective Max HP",
)
weapon_anchor = "  fun weaponDamage(state: GameState, characterId: String): Int {\n"
weapon_helper = '''  fun devilBlessingHpBonus(state: GameState, characterId: String, unblessedMaxHp: Int? = null): Int {
    if (characterId == KAI_ID || state.metadata["combat.entityKey"].isNullOrBlank() || characterId !in state.party.memberIds) return 0
    val kai = state.characters[KAI_ID] ?: return 0
    val companion = state.characters[characterId] ?: return 0
    if (kai.presence != CharacterPresence.ACTIVE || kai.vitalState.currentHp <= 0 || companion.presence != CharacterPresence.ACTIVE || companion.vitalState.currentHp <= 0) return 0
    val base = unblessedMaxHp ?: run {
      val equipmentHp = state.equipment[companion.equipmentId]?.slots.orEmpty().values
        .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }.sumOf { it.bonuses.hp }
      (companion.statProfile.baseMaxHp + equipmentHp).coerceAtLeast(1)
    }
    return maxOf(1, (base * 10 + 99) / 100)
  }

'''
stats = replace_once(stats, weapon_anchor, weapon_helper + weapon_anchor, "Devil Blessing HP helper")
STATS.write_text(stats, encoding="utf-8")


# +10 percentage points to the already-final roaming Entity thresholds, capped by the 10000 roll bound.
main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    "    int[] entityThresholds = {805, 1000, 1150, 1150, 810, 1200, 805};\n",
    "    int[] entityThresholds = {1805, 2000, 2150, 2150, 1810, 2200, 1805};\n",
    "roaming monster pool +10 percentage points",
)
MAIN.write_text(main, encoding="utf-8")


TESTS.joinpath("DevilTriggerPassiveTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class DevilTriggerPassiveTest {
  @Test fun readyUsesExactlyThirtyFivePercentThreshold() {
    assertTrue(DevilTriggerPassive.beginTurn(DevilTriggerState(), 34).triggeredThisTurn)
    assertFalse(DevilTriggerPassive.beginTurn(DevilTriggerState(), 35).triggeredThisTurn)
  }

  @Test fun activeLastsThreeTurnsThenReturnsReadyWithoutCooldown() {
    var turn = DevilTriggerPassive.beginTurn(DevilTriggerState(), 0)
    var state = DevilTriggerPassive.endTurn(turn)
    assertEquals(2, state.activeTurns)
    turn = DevilTriggerPassive.beginTurn(state, 99); state = DevilTriggerPassive.endTurn(turn)
    assertEquals(1, state.activeTurns)
    turn = DevilTriggerPassive.beginTurn(state, 99); state = DevilTriggerPassive.endTurn(turn)
    assertEquals(DevilTriggerState(), state)
    assertEquals(0, DevilTriggerPassive.COOLDOWN_TURNS)
    assertTrue(DevilTriggerPassive.beginTurn(state, 0).triggeredThisTurn)
  }

  @Test fun activeEffectsRemainKaiDevilTriggerEffects() {
    assertEquals(500, DevilTriggerPassive.damage(100, true))
    assertEquals(100, DevilTriggerPassive.evasionBonus(true))
    assertEquals(5, DevilTriggerPassive.healAmount(100))
  }
}
''', encoding="utf-8")

integration = TESTS.joinpath("DevilTriggerCombatIntegrationTest.kt")
integration.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class DevilTriggerCombatIntegrationTest {
  @Test fun kaiPassiveIsExclusiveAndSyvialKeepsHerSeparateState() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID)))
    state = CombatRuntime.start(state, "diep_minh").copy(metadata = CombatRuntime.start(state, "diep_minh").metadata + mapOf(
      "passive.devilTrigger.kai.activeTurns" to "3",
      "passive.devilTrigger.syvial.activeTurns" to "3"
    ))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertEquals("2", result.state.metadata["passive.devilTrigger.kai.activeTurns"])
    assertNull(result.state.metadata["passive.devilTrigger.syvial.activeTurns"])
    assertEquals("true", result.state.metadata["combat.syvialDevilTrigger"])
    assertTrue(result.reply.contains("Syvial kích hoạt Devil Trigger theo Lucifer Core"))
    assertFalse(result.reply.contains("DEVIL TRIGGER — Lucifer Core"))
  }

  @Test fun devilBlessingAddsTenPercentCompanionMaxHpButNotKaiHp() {
    var state = SpecialFollowersCanon.ensure(GameState.initial()).copy(party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID)))
    val kaiBefore = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val irisBefore = CharacterStatEngine.effective(state, IRIS_ID).maxHp
    state = CombatRuntime.start(state, "hound")
    assertEquals(kaiBefore, CharacterStatEngine.effective(state, KAI_ID).maxHp)
    assertEquals(irisBefore + (irisBefore * 10 + 99) / 100, CharacterStatEngine.effective(state, IRIS_ID).maxHp)
  }
}
''', encoding="utf-8")

# Existing boss/bleed regressions intentionally asserted the previous exact HP values.
# Move those expectations to the new final balance without weakening their original mechanics.
combat_test_path = TESTS / "CombatRuntimeTest.kt"
combat_test = combat_test_path.read_text(encoding="utf-8")
for old, new in (
    ("assertEquals(2999, started.entityMaxHp)", "assertEquals(3199, started.entityMaxHp)"),
    ("assertEquals(2999, started.entityHp)", "assertEquals(3199, started.entityHp)"),
    ('contains("Bleeding từ The Last Requiem gây -150 HP")', 'contains("Bleeding từ The Last Requiem gây -160 HP")'),
    ("assertEquals(3456, combat.entityMaxHp)", "assertEquals(3656, combat.entityMaxHp)"),
    ("assertEquals(3456, combat.entityHp)", "assertEquals(3656, combat.entityHp)"),
    ("assertEquals(1234, CombatRuntime.active(state)!!.entityMaxHp)", "assertEquals(1434, CombatRuntime.active(state)!!.entityMaxHp)"),
    ("assertEquals(minOf(1234, before + 30), CombatRuntime.active(next.state)!!.entityHp)", "assertEquals(minOf(1434, before + 30), CombatRuntime.active(next.state)!!.entityHp)"),
    ("assertEquals(1730, active.entityMaxHp)", "assertEquals(1930, active.entityMaxHp)"),
    ("assertEquals(1730, active.entityHp)", "assertEquals(1930, active.entityHp)"),
):
    if old not in combat_test:
        raise RuntimeError("Updated combat regression anchor missing: " + old)
    combat_test = combat_test.replace(old, new, 1)

neck_start = combat_test.index("  @Test fun scp173NeckSnapExecutesOnlyAtOrBelowFifteenPercent() {")
neck_end = combat_test.index("  @Test fun scp173SnapStrikeStunUsesStatusEngineForOneTurn() {", neck_start)
neck_test = r'''  @Test fun scp173NeckSnapExecutesOnlyAtOrBelowFifteenPercent() {
    var verified = false
    for (counter in 0..200) {
      if (verified) break
      val initial = GameState.initial()
      val blindEffect = StatusEffect("test:blind:kai:neck:$counter", "BLIND", "test", durationTurns = 5)
      var state = StatusEngine.execute(initial, StatusCommand(
        commandId = "test:blind:neck:$counter", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
        source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
      )).state
      state = CombatRuntime.start(state, "scp_173")
      val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
      // Start at 9% so Kai remains <=15% even if Devil Trigger heals 5% before SCP-173 resolves.
      val threshold = maxOf(1, maxHp * 9 / 100)
      state = CharacterStatEngine.setCurrentHp(state, KAI_ID, threshold)
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.range" to CombatRuntime.RangeBand.CLOSE.name,
        "combat.eventCounter" to counter.toString()
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "không thể quan sát SCP-173")
      if (!result.reply.contains("Neck Snap")) continue
      assertEquals(0, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
      assertTrue(result.reply, result.reply.contains("Execution hợp lệ"))
      verified = true
    }
    assertTrue("Expected a deterministic turn where Kai Devil Trigger does not evade Neck Snap", verified)
  }

'''
combat_test = combat_test[:neck_start] + neck_test + combat_test[neck_end:]
combat_test_path.write_text(combat_test, encoding="utf-8")

TESTS.joinpath("KaiMonsterBalanceTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class KaiMonsterBalanceTest {
  @Test fun monstersAboveOneThousandGainTwoHundredHp() {
    assertEquals(3199, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "diep_minh"))!!.entityMaxHp)
    assertEquals(3656, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "monster_x"))!!.entityMaxHp)
    assertEquals(1434, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "john_doe"))!!.entityMaxHp)
    assertEquals(1930, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "scp_173"))!!.entityMaxHp)
  }

  @Test fun monstersBelowOneThousandDoNotGainHpTierBonus() {
    assertEquals(110, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)
  }
}
''', encoding="utf-8")

combined = trigger + catalog + combat + stats + main
for marker in (
    "const val TRIGGER_PERCENT = 35", "const val COOLDOWN_TURNS = 0",
    's("Devil Blessing", "PASSIVE"', "syvialDevilTriggerTurn: DevilTriggerTurn? = null",
    "syvialHp * 2 <= syvialMaxHp", 'SYVIAL_DEVIL_TRIGGER_KEY to "true"',
    "balancedEntityBaseHp > 1000", "baseMonsterDamage * 110", "resolved * 110",
    "devilBlessingHpBonus", "int[] entityThresholds = {1805, 2000, 2150, 2150, 1810, 2200, 1805}",
):
    if marker not in combined:
        raise RuntimeError("1.1.71 Kai/monster balance contract missing: " + marker)

print("Kai/monster balance applied: Kai-only Devil Trigger 35% with no cooldown, Devil Blessing +10% companion DMG/HP, >1000 HP +200, <1000 DMG +10%, roaming pool +10 percentage points.")
