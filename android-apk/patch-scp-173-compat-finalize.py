from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

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
):
    if marker not in combat:
        raise RuntimeError("SCP-173 compatibility contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")
print("SCP-173 compatibility finalizer applied: existing Guilty Crown narration preserved for non-SCP Entities.")
