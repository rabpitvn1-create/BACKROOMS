from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

combat = COMBAT.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Final Entity combat balance authority. CharacterVitalState already owns player HP by this point,
# so anchor Entity-only constants after PREFIX instead of reviving retired combat.playerHp metadata.
constants_old = '  private const val PREFIX = "combat."\n'
constants_new = '''  private const val PREFIX = "combat."
  private const val ENTITY_HP_BONUS = 30
  private const val ENTITY_EVASION_PERCENT = 25
  private const val ENTITY_REGEN_PER_TURN = 1
'''
combat = replace_once(combat, constants_old, constants_new, "Entity combat balance constants")

start_old = '''    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val snapshot = Snapshot(
'''
start_new = '''    val seed = stableSeed(entityKey, state.turn.currentTurnId, state.time.elapsedSubjectiveMinutes)
    val enhancedEntityMaxHp = profile.maxHp + ENTITY_HP_BONUS
    val snapshot = Snapshot(
'''
combat = replace_once(combat, start_old, start_new, "enhanced Entity max HP start")
combat = replace_once(combat, '      entityHp = profile.maxHp,\n      entityMaxHp = profile.maxHp,\n', '      entityHp = enhancedEntityMaxHp,\n      entityMaxHp = enhancedEntityMaxHp,\n', "new encounter Entity HP bonus")

attack_old = '''        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
        if (roll < hitChance) {
'''
attack_new = '''        val hitChance = (58 + rangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
        val evasionRoll = roll(c.copy(eventCounter = c.eventCounter + 13), 100)
        val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT
        if (roll < hitChance && !entityEvaded) {
'''
combat = replace_once(combat, attack_old, attack_new, "25 percent Entity evasion gate")

miss_old = '''          c = c.copy(momentum = max(-3, c.momentum - 1), opening = max(0, c.opening - 1), noise = min(100, c.noise + 28))
          log += "Đòn đánh trượt; ${c.entityName} giành lại áp lực."
'''
miss_new = '''          c = c.copy(momentum = max(-3, c.momentum - 1), opening = max(0, c.opening - 1), noise = min(100, c.noise + 28))
          log += if (entityEvaded) "${c.entityName} né đòn (25% evasion) và giành lại áp lực." else "Đòn đánh trượt; ${c.entityName} giành lại áp lực."
'''
combat = replace_once(combat, miss_old, miss_new, "Entity evasion combat log")

regen_anchor = '''    c = c.copy(
      telegraph = telegraphFor(profile, c.seed, c.eventCounter),
'''
regen_block = '''    val entityHpBeforeRegen = c.entityHp
    val entityHpAfterRegen = min(c.entityMaxHp, c.entityHp + ENTITY_REGEN_PER_TURN)
    if (entityHpAfterRegen > entityHpBeforeRegen) {
      c = c.copy(entityHp = entityHpAfterRegen, entityCondition = condition(entityHpAfterRegen, c.entityMaxHp))
      log += "${c.entityName} hồi +$ENTITY_REGEN_PER_TURN HP (${c.entityHp}/${c.entityMaxHp})."
    }

    c = c.copy(
      telegraph = telegraphFor(profile, c.seed, c.eventCounter),
'''
combat = replace_once(combat, regen_anchor, regen_block, "Entity 1 HP per combat turn regeneration")

# Upgrade an already-active encounter from an older save without losing damage already dealt.
# Player HP lines are intentionally outside this replacement because final combat cleanup routes
# player HP through CharacterVitalState rather than retired combat.playerHp metadata.
decode_old = '''    val profile = profiles[key] ?: return null
    val maxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: profile.maxHp
    val hp = m["${PREFIX}entityHp"]?.toIntOrNull()?.coerceIn(0, maxHp) ?: maxHp
'''
decode_new = '''    val profile = profiles[key] ?: return null
    val canonicalMaxHp = profile.maxHp + ENTITY_HP_BONUS
    val storedMaxHp = m["${PREFIX}entityMaxHp"]?.toIntOrNull()?.coerceAtLeast(1) ?: canonicalMaxHp
    val maxHp = max(storedMaxHp, canonicalMaxHp)
    val storedHp = m["${PREFIX}entityHp"]?.toIntOrNull()?.coerceIn(0, storedMaxHp) ?: storedMaxHp
    val hp = if (storedMaxHp < canonicalMaxHp) min(maxHp, storedHp + (canonicalMaxHp - storedMaxHp)) else storedHp.coerceIn(0, maxHp)
'''
combat = replace_once(combat, decode_old, decode_new, "legacy active-combat HP migration")

for marker in (
    'private const val ENTITY_HP_BONUS = 30',
    'private const val ENTITY_EVASION_PERCENT = 25',
    'private const val ENTITY_REGEN_PER_TURN = 1',
    'val enhancedEntityMaxHp = profile.maxHp + ENTITY_HP_BONUS',
    'val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT',
    'c.entityHp + ENTITY_REGEN_PER_TURN',
    'val canonicalMaxHp = profile.maxHp + ENTITY_HP_BONUS',
    'CharacterStatEngine.effective(state, KAI_ID).maxHp',
):
    if marker not in combat:
        raise RuntimeError("Entity combat durability contract missing: " + marker)

for forbidden in ('PLAYER_HP', 'PLAYER_MAX_HP'):
    if forbidden in combat:
        raise RuntimeError("Entity durability resurrected retired player combat metadata: " + forbidden)

COMBAT.write_text(combat, encoding="utf-8")

# Regression tests run after the final patch chain, so they validate the generated runtime rather
# than a hand-edited intermediate file.
test = TEST.read_text(encoding="utf-8")
test = replace_once(test, '    assertEquals(80, combat.entityMaxHp)\n    assertEquals(80, combat.entityHp)\n', '    assertEquals(110, combat.entityMaxHp)\n    assertEquals(110, combat.entityHp)\n', "Hound +30 HP expectation")

regen_test = r'''
  @Test fun survivingEntityRegeneratesOneHpPerCombatTurnUpToMax() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")
    var damaged = false
    repeat(20) {
      if (damaged) return@repeat
      val result = CombatRuntime.resolve(state, "EXECUTE", "bắn Slenderman bằng Magnum")
      state = result.state
      val active = CombatRuntime.active(state)
      if (active != null && active.entityHp < active.entityMaxHp) damaged = true
    }
    assertTrue("Entity should take damage before regen check", damaged)
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát chuyển động")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(minOf(before.entityMaxHp, before.entityHp + 1), after.entityHp)
  }

  @Test fun allEntityProfilesReceiveThirtyBonusHp() {
    val expected = mapOf(
      "hound" to 110, "clump" to 135, "duller" to 120, "deathmoth" to 95,
      "hostile_faceling" to 105, "false_puddle" to 125, "paintings" to 100,
      "smiler" to 115, "skin-stealer" to 130, "predatory_window" to 145,
      "biological_pipeline" to 150, "wretch" to 115, "cable_mimic" to 130,
      "the_beast_of_level_5" to 175, "hotel_corpse_lure" to 140,
      "jeff_the_killer" to 150, "jane_the_killer" to 150, "slenderman" to 190
    )
    expected.forEach { (key, hp) ->
      assertEquals("+30 HP must apply to $key", hp, CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!.entityMaxHp)
    }
  }
'''
if "survivingEntityRegeneratesOneHpPerCombatTurnUpToMax" not in test:
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + regen_test + test[close:]

for marker in (
    'assertEquals(110, combat.entityMaxHp)',
    'survivingEntityRegeneratesOneHpPerCombatTurnUpToMax',
    'allEntityProfilesReceiveThirtyBonusHp',
    '"slenderman" to 190',
):
    if marker not in test:
        raise RuntimeError("Entity combat durability test contract missing: " + marker)

TEST.write_text(test, encoding="utf-8")
print("Entity combat durability applied: +30 max HP, 25% evasion, +1 HP regeneration per surviving combat turn.")
