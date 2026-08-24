from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

combat = COMBAT.read_text(encoding="utf-8")

# SCP-173 has now installed target mitigation. Reapply the Devil Trigger x5 raw
# Guilty Crown damage before that mitigation, preserving ordinary Entity output
# and SCP-173's committed-damage narration.
old_damage = '      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT\n      val appliedTotalDamage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(totalDamage, scp173ObservedNow) else totalDamage\n'
new_damage = '''      // Baseline inactive contract remains KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 24 * 10.
      val perShotDamage = DevilTriggerPassive.damage(KAI_GUILTY_CROWN_DAMAGE_PER_SHOT, kaiDevilTriggerActive)
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * perShotDamage
      val appliedTotalDamage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(totalDamage, scp173ObservedNow) else totalDamage
'''
if old_damage not in combat:
    raise RuntimeError("Devil Trigger SCP finalizer could not find SCP-173 Guilty Crown mitigation block")
combat = combat.replace(old_damage, new_damage, 1)

old_log = '''        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, " +
        (if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP" else "tổng -$totalDamage HP") +
        " (${c.entityHp}/${c.entityMaxHp})."
'''
new_log = '''        "mỗi phát -$perShotDamage HP, " +
        (if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP" else "tổng -$totalDamage HP") +
        " (${c.entityHp}/${c.entityMaxHp})."
'''
if old_log not in combat:
    raise RuntimeError("Devil Trigger SCP finalizer could not find SCP-173 compatibility narration")
combat = combat.replace(old_log, new_log, 1)

for marker in (
    "val perShotDamage = DevilTriggerPassive.damage(KAI_GUILTY_CROWN_DAMAGE_PER_SHOT, kaiDevilTriggerActive)",
    "val appliedTotalDamage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(totalDamage, scp173ObservedNow) else totalDamage",
    "KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 24 * 10",
    'if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP"',
    'else "tổng -$totalDamage HP"',
):
    if marker not in combat:
        raise RuntimeError("Devil Trigger post-SCP contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")
print("Devil Trigger post-SCP finalizer applied: Guilty Crown x5 restored before SCP-173 mitigation.")
