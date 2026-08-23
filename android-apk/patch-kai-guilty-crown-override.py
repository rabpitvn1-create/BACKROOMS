from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")

# Kai's gameplay Override is deterministic. Every third active combat turn, after the player's
# chosen action resolves but before the Entity can retaliate, Kai automatically fires all 24 shots.
# This path intentionally performs no accuracy/evasion roll: Accuracy is locked to 200% and all
# dodge/evasion effects are bypassed. Damage is exact HP damage: 24 * 10 = 240 HP.
constants_old = '  private const val ENTITY_REGEN_PER_TURN = 1\n'
constants_new = '''  private const val ENTITY_REGEN_PER_TURN = 1
  private const val KAI_GUILTY_CROWN_INTERVAL_TURNS = 3
  private const val KAI_GUILTY_CROWN_SHOTS = 24
  private const val KAI_GUILTY_CROWN_ACCURACY_PERCENT = 200
  private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10
'''
combat = replace_once(combat, constants_old, constants_new, "Guilty Crown constants")

ultimate_anchor = '''    if (c.escapeProgress >= 100) {
      val persisted = encode(state, c.copy(phase = Phase.RESOLVED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " Kai cắt được truy đuổi và thoát khỏi encounter.", escaped = true)
    }

    // Enemy response. READ/guard/evasion reduce expected incoming damage; attacking blindly is riskier.
'''
ultimate_block = '''    if (c.escapeProgress >= 100) {
      val persisted = encode(state, c.copy(phase = Phase.RESOLVED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " Kai cắt được truy đuổi và thoát khỏi encounter.", escaped = true)
    }

    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0) {
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT
      val hp = max(0, c.entityHp - totalDamage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Guilty Crown Override tự động kích hoạt ở combat turn ${c.eventCounter}: $KAI_GUILTY_CROWN_SHOTS/" +
        "$KAI_GUILTY_CROWN_SHOTS phát trúng liên tiếp, Accuracy $KAI_GUILTY_CROWN_ACCURACY_PERCENT%, bỏ qua toàn bộ hiệu ứng né; " +
        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."
      if (c.entityHp <= 0) {
        val persisted = encode(state, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
        val cleared = clearCombatOnly(persisted)
        return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
      }
    }

    // Enemy response. READ/guard/evasion reduce expected incoming damage; attacking blindly is riskier.
'''
combat = replace_once(combat, ultimate_anchor, ultimate_block, "automatic third-turn Guilty Crown Override")

for marker in (
    'private const val KAI_GUILTY_CROWN_INTERVAL_TURNS = 3',
    'private const val KAI_GUILTY_CROWN_SHOTS = 24',
    'private const val KAI_GUILTY_CROWN_ACCURACY_PERCENT = 200',
    'private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10',
    'if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0)',
    'val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT',
    'bỏ qua toàn bộ hiệu ứng né',
    'entityDestroyed = true',
):
    if marker not in combat:
        raise RuntimeError("Guilty Crown combat contract missing: " + marker)

ultimate_start = combat.find('    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0)')
ultimate_end = combat.find('    // Enemy response.', ultimate_start)
if ultimate_start < 0 or ultimate_end < 0:
    raise RuntimeError("Guilty Crown final combat section missing")
ultimate_section = combat[ultimate_start:ultimate_end]
if 'roll(' in ultimate_section or 'entityEvaded' in ultimate_section or 'ENTITY_EVASION_PERCENT' in ultimate_section:
    raise RuntimeError("Guilty Crown Override must not use accuracy RNG or Entity evasion")

COMBAT.write_text(combat, encoding="utf-8")

# Existing escape/regen tests predate the automatic third-turn finisher. Keep testing those mechanics
# directly without requiring an ordinary Entity to survive past the new mandatory third-turn Override.
test = TEST.read_text(encoding="utf-8")
escape_old = r'''  @Test fun escapeResolutionClearsEncounterWithoutDestroyingRequirement() {
    var state = CombatRuntime.start(GameState.initial(), "smiler")
    var escaped = false
    repeat(12) {
      if (escaped) return@repeat
      val move = CombatRuntime.resolve(state, "EXPLORE", "lùi vào cover và di chuyển")
      state = move.state
      if (move.escaped) { escaped = true; return@repeat }
      val flee = CombatRuntime.resolve(state, "EXECUTE", "chạy thoát khỏi encounter")
      state = flee.state
      escaped = flee.escaped
    }
    assertTrue(escaped)
    assertNull(CombatRuntime.active(state))
  }
'''
escape_new = r'''  @Test fun escapeResolutionClearsEncounterWithoutDestroyingRequirement() {
    val started = CombatRuntime.start(GameState.initial(), "smiler")
    val state = started.copy(metadata = started.metadata + ("combat.escapeProgress" to "95"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "chạy thoát khỏi encounter")
    assertTrue(result.handled)
    assertTrue(result.escaped)
    assertFalse(result.entityDestroyed)
    assertNull(CombatRuntime.active(result.state))
  }
'''
test = replace_once(test, escape_old, escape_new, "escape regression compatible with third-turn Override")

regen_old = r'''  @Test fun survivingEntityRegeneratesOneHpPerCombatTurnUpToMax() {
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
'''
regen_new = r'''  @Test fun survivingEntityRegeneratesOneHpPerCombatTurnUpToMax() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")
    val full = CombatRuntime.active(state)!!
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to (full.entityMaxHp - 5).toString()))
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát chuyển động")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(before.entityHp + 1, after.entityHp)
  }
'''
test = replace_once(test, regen_old, regen_new, "Entity regen regression compatible with third-turn Override")

ultimate_tests = r'''
  @Test fun guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")

    repeat(2) { index ->
      val result = CombatRuntime.resolve(state, "SEARCH", "quan sát nhịp di chuyển")
      assertTrue(result.handled)
      assertFalse("Override must not fire before combat turn 3", result.reply.contains("Guilty Crown Override"))
      state = result.state
      val active = CombatRuntime.active(state)
      assertNotNull(active)
      assertEquals(index + 1, active!!.eventCounter)
    }

    val third = CombatRuntime.resolve(state, "SEARCH", "tiếp tục quan sát mục tiêu")
    assertTrue(third.handled)
    assertTrue(third.entityDestroyed)
    assertNull(CombatRuntime.active(third.state))
    assertTrue(third.reply.contains("Guilty Crown Override"))
    assertTrue(third.reply.contains("24/24 phát trúng liên tiếp"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
    assertTrue(third.reply.contains("mỗi phát -10 HP"))
    assertTrue(third.reply.contains("tổng -240 HP"))
  }

  @Test fun guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen() {
    var state = CombatRuntime.start(GameState.initial(), "hound")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.entityHp" to "500",
      "combat.entityMaxHp" to "500"
    ))

    repeat(2) {
      val result = CombatRuntime.resolve(state, "SEARCH", "quan sát mục tiêu")
      assertFalse(result.entityDestroyed)
      state = result.state
    }

    val third = CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")
    assertFalse(third.entityDestroyed)
    val after = CombatRuntime.active(third.state)!!
    // 500 - (24 * 10) + the existing surviving-Entity 1 HP end-of-turn regeneration.
    assertEquals(261, after.entityHp)
    assertTrue(third.reply.contains("tổng -240 HP"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
  }
'''
if "guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn" not in test:
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + ultimate_tests + test[close:]

for marker in (
    'guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn',
    'guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen',
    'assertTrue(third.reply.contains("Accuracy 200%"))',
    'assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))',
    'assertTrue(third.reply.contains("tổng -240 HP"))',
    'assertEquals(261, after.entityHp)',
):
    if marker not in test:
        raise RuntimeError("Guilty Crown regression contract missing: " + marker)

TEST.write_text(test, encoding="utf-8")
print("Kai Guilty Crown Override applied: automatic every 3 combat turns, 24 x 10 HP, Accuracy 200%, evasion bypassed.")
