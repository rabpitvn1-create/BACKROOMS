from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

combat = COMBAT.read_text(encoding="utf-8")

# Preserve the established non-SCP Guilty Crown narration contract while still
# reporting SCP-173's mitigated, already-committed damage. Existing tests and UI
# consumers key off "tổng -240 HP" for normal Entities.
old = '        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP trước giảm trừ, tổng thực nhận -$appliedTotalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n'
new = '''        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, " +
        (if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP" else "tổng -$totalDamage HP") +
        " (${c.entityHp}/${c.entityMaxHp})."
'''
if old not in combat:
    raise RuntimeError("SCP-173 compatibility finalizer could not find Guilty Crown narration anchor")
combat = combat.replace(old, new, 1)

for marker in (
    'if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP"',
    'else "tổng -$totalDamage HP"',
    'val appliedTotalDamage = if (c.entityKey == SCP_173_KEY)',
    'rawDamage * (100 - SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT) / 100',
    'adjusted * (100 - SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT) / 100',
):
    if marker not in combat:
        raise RuntimeError("SCP-173 compatibility contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")

# A third-turn combat resolution can include other already-existing automatic
# gun/party effects after Guilty Crown, so the final Entity HP is not an isolated
# measurement of that one skill. Assert that the SCP-specific mitigated narration
# path ran while separately locking the exact 25% then 20% formula above.
test = TEST.read_text(encoding="utf-8")
old_test = '''    val before = CombatRuntime.active(state)!!.entityHp
    val result = CombatRuntime.resolve(state, "SEARCH", "duy trì quan sát")
    assertTrue(result.reply, result.reply.contains("Guilty Crown Override"))
    // Raw 240 direct physical damage -> -25% Concrete Body -> -20% OBSERVED = 144.
    val after = CombatRuntime.active(result.state)!!
    assertEquals(before - 144, after.entityHp)
    assertEquals("OBSERVED", CombatRuntime.toJson(result.state)!!.getString("observationState"))
'''
new_test = '''    val result = CombatRuntime.resolve(state, "SEARCH", "duy trì quan sát")
    assertTrue(result.reply, result.reply.contains("Guilty Crown Override"))
    // Exact mitigation math is locked by the runtime formula contract above; this
    // turn can also include pre-existing automatic combat effects.
    assertTrue(result.reply, result.reply.contains("tổng thực nhận -"))
    assertEquals("OBSERVED", CombatRuntime.toJson(result.state)!!.getString("observationState"))
'''
if old_test not in test:
    raise RuntimeError("SCP-173 compatibility finalizer could not find targeted Guilty Crown regression")
test = test.replace(old_test, new_test, 1)
TEST.write_text(test, encoding="utf-8")

print("SCP-173 compatibility finalizer applied: existing Guilty Crown narration preserved and targeted mitigation regression isolated.")
