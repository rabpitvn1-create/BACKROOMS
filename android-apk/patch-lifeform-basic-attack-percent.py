from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")

combat = replace_once(
    combat,
    "  private data class LifeformSkillSpec(\n",
    "  // LIFEFORM_BASIC_ATTACK_PERCENT_R03\n  private data class LifeformSkillSpec(\n",
    "Lifeform Basic Attack percent marker",
)
combat = replace_once(combat, "    val attackBonusPercent: Int,\n", "    val basicAttackPercent: Int,\n", "Lifeform skill percent field")

for old, new in (
    ('LifeformSkillSpec("Thorned Hemorrhage", 25, 15,', 'LifeformSkillSpec("Thorned Hemorrhage", 25, 115,'),
    ('LifeformSkillSpec("Blight Sap", 20, 25,', 'LifeformSkillSpec("Blight Sap", 20, 125,'),
    ('LifeformSkillSpec("Crimson Mycotoxin", 10, 40,', 'LifeformSkillSpec("Crimson Mycotoxin", 10, 140,'),
    ('LifeformSkillSpec("Tendon Ripper", 25, 15,', 'LifeformSkillSpec("Tendon Ripper", 25, 115,'),
    ('LifeformSkillSpec("Septic Thread", 20, 25,', 'LifeformSkillSpec("Septic Thread", 20, 125,'),
    ('LifeformSkillSpec("Venomous Flay", 10, 40,', 'LifeformSkillSpec("Venomous Flay", 10, 140,'),
    ('LifeformSkillSpec("Hollow Laceration", 25, 15,', 'LifeformSkillSpec("Hollow Laceration", 25, 115,'),
    ('LifeformSkillSpec("Carrion Toxin", 20, 25,', 'LifeformSkillSpec("Carrion Toxin", 20, 125,'),
    ('LifeformSkillSpec("Necrotic Clutch", 10, 40,', 'LifeformSkillSpec("Necrotic Clutch", 10, 140,'),
):
    combat = replace_once(combat, old, new, "Lifeform Basic Attack coefficient")

combat = replace_once(
    combat,
    '''  private fun lifeformAttack(baseAttack: Int, skill: LifeformSkillSpec?): Int =
    if (skill == null) baseAttack else max(1, baseAttack * (100 + skill.attackBonusPercent) / 100)

''',
    "",
    "remove Lifeform ATK-stat modifier",
)

combat = replace_once(
    combat,
    "EntityPowerScaling.scale(lifeformAttack(profile.attack, lifeformSkill), c.progressionRank)",
    "EntityPowerScaling.scale(profile.attack, c.progressionRank)",
    "remove Lifeform ATK-stat multiplier from base attack",
)
raw_pos = combat.find("val rawIncoming =")
if raw_pos < 0:
    raise RuntimeError("Lifeform Basic Attack rawIncoming anchor missing")
damage_pos = combat.find("val damage =", raw_pos)
if damage_pos < 0 or damage_pos - raw_pos > 800:
    raise RuntimeError("Lifeform Basic Attack damage line missing after rawIncoming")
line_start = combat.rfind("\n", 0, damage_pos) + 1
line_end = combat.find("\n", damage_pos)
if line_end < 0:
    raise RuntimeError("Lifeform Basic Attack damage line terminator missing")
damage_line = combat[line_start:line_end]
indent = damage_line[: len(damage_line) - len(damage_line.lstrip())]
prefix = indent + "val damage = "
if not damage_line.startswith(prefix):
    raise RuntimeError("Unexpected Lifeform damage assignment: " + damage_line)
basic_expression = damage_line[len(prefix):]
replacement = (
    indent + "val basicDamage = " + basic_expression + "\n" +
    indent + "val damage = if (lifeformSkill == null) basicDamage\n" +
    indent + "  else CharacterStatCore.scaleByPercent(basicDamage, lifeformSkill.basicAttackPercent)"
)
combat = combat[:line_start] + replacement + combat[line_end:]

combat = replace_once(
    combat,
    '          log += lifeformSkill.name + " proc: +" + lifeformSkill.attackBonusPercent + "% ATK; " + effects + "."\n',
    '          log += lifeformSkill.name + " proc: " + lifeformSkill.basicAttackPercent + "% Basic Attack; " + effects + "."\n',
    "Lifeform combat log Basic Attack wording",
)

for required in (
    "LIFEFORM_BASIC_ATTACK_PERCENT_R03",
    "basicAttackPercent",
    'LifeformSkillSpec("Blight Sap", 20, 125,',
    "CharacterStatCore.scaleByPercent(basicDamage, lifeformSkill.basicAttackPercent)",
    "% Basic Attack",
):
    if required not in combat:
        raise RuntimeError("Lifeform Basic Attack contract missing: " + required)

for forbidden in ("attackBonusPercent", "lifeformAttack(", "+% ATK"):
    if forbidden in combat:
        raise RuntimeError("Retired Lifeform ATK-stat semantics survived: " + forbidden)

COMBAT.write_text(combat, encoding="utf-8")


main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    "Mỗi Lifeform có đúng 3 skill; skill chỉ tăng %ATK và chỉ gây Bleed/Poison.",
    "Mỗi Lifeform có đúng 3 skill; damage skill là % của Basic Attack, không buff stat ATK, và chỉ gây Bleed/Poison.",
    "GM Lifeform Basic Attack wording",
)
main = replace_once(
    main,
    "tương ứng +15% / +25% / +40% ATK;",
    "tương ứng 115% / 125% / 140% Basic Attack;",
    "GM Lifeform Basic Attack coefficients",
)
MAIN.write_text(main, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
old = '''        wanted.forEach { skill -> if (result.reply.contains(skill)) seen += skill }
'''
new = '''        wanted.forEach { skill ->
          if (result.reply.contains(skill)) {
            seen += skill
            val expectedPercent = when (skill) {
              "Thorned Hemorrhage", "Tendon Ripper", "Hollow Laceration" -> 115
              "Blight Sap", "Septic Thread", "Carrion Toxin" -> 125
              else -> 140
            }
            assertTrue(result.reply.contains(expectedPercent.toString() + "% Basic Attack"))
          }
        }
'''
test = replace_once(test, old, new, "Lifeform Basic Attack regression assertion")
TEST.write_text(test, encoding="utf-8")

print("Lifeform skill damage corrected: 115/125/140% of Basic Attack; no ATK-stat buff.")
