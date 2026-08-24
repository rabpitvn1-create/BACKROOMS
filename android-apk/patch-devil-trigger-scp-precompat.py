from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

combat = COMBAT.read_text(encoding="utf-8")

# SCP-173's compatibility layer owns the target-specific Guilty Crown mitigation
# transform and expects the established raw 24 x 10 block. Temporarily restore
# only that block; patch-devil-trigger-scp-finalize.py reapplies x5 after SCP-173
# has finished adding Concrete Body / OBSERVED mitigation.
old_damage = '''      val perShotDamage = DevilTriggerPassive.damage(KAI_GUILTY_CROWN_DAMAGE_PER_SHOT, kaiDevilTriggerActive)
      val totalDamage = KAI_GUILTY_CROWN_SHOTS * perShotDamage
'''
new_damage = '      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT\n'
if old_damage not in combat:
    raise RuntimeError("Devil Trigger SCP precompat could not find transformed Guilty Crown damage block")
combat = combat.replace(old_damage, new_damage, 1)

old_log = '        "mỗi phát -$perShotDamage HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n'
new_log = '        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n'
if old_log not in combat:
    raise RuntimeError("Devil Trigger SCP precompat could not find transformed Guilty Crown narration")
combat = combat.replace(old_log, new_log, 1)
COMBAT.write_text(combat, encoding="utf-8")

# The app intentionally depends on JUnit 4 only. The Devil Trigger generator uses
# Kotlin-style assertions for readability, so normalize those generated imports to
# the repository's actual test dependency before Gradle compiles the test suite.
for name in ("DevilTriggerPassiveTest.kt", "DevilTriggerCombatIntegrationTest.kt"):
    path = TESTS / name
    text = path.read_text(encoding="utf-8")
    text = text.replace("import kotlin.test.Test\n", "import org.junit.Test\n")
    text = text.replace("import kotlin.test.assertEquals\n", "import org.junit.Assert.assertEquals\n")
    text = text.replace("import kotlin.test.assertFalse\n", "import org.junit.Assert.assertFalse\n")
    text = text.replace("import kotlin.test.assertTrue\n", "import org.junit.Assert.assertTrue\n")
    if "kotlin.test." in text:
        raise RuntimeError(f"Devil Trigger JUnit compatibility incomplete for {name}")
    path.write_text(text, encoding="utf-8")

print("Devil Trigger pre-SCP compatibility applied: Guilty Crown raw block restored for SCP-173 and generated tests normalized to JUnit 4.")
