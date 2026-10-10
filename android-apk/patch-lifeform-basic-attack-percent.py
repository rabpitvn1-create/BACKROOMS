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

scaled_token = "EntityPowerScaling.scale(lifeformAttack(profile.attack, lifeformSkill), c.progressionRank)"
base_token = "EntityPowerScaling.scale(profile.attack, c.progressionRank)"
if combat.count(scaled_token) != 1:
    raise RuntimeError("Lifeform scaled attack token count changed: " + str(combat.count(scaled_token)))
token_pos = combat.index(scaled_token)
line_start = combat.rfind("\n", 0, token_pos) + 1
line_end = combat.find("\n", token_pos)
if line_end < 0:
    raise RuntimeError("Lifeform attack line terminator missing")
attack_line = combat[line_start:line_end]
indent = attack_line[: len(attack_line) - len(attack_line.lstrip())]

if attack_line.startswith(indent + "val damage = "):
    reverted_line = attack_line.replace(scaled_token, base_token, 1)
    prefix = indent + "val damage = "
    basic_expression = reverted_line[len(prefix):]
    replacement = (
        indent + "val basicDamage = " + basic_expression + "\n" +
        indent + "val damage = if (lifeformSkill == null) basicDamage\n" +
        indent + "  else CharacterStatCore.scaleByPercent(basicDamage, lifeformSkill.basicAttackPercent)"
    )
    combat = combat[:line_start] + replacement + combat[line_end:]
elif attack_line.startswith(indent + "val rawIncoming = "):
    combat = combat[:line_start] + attack_line.replace(scaled_token, base_token, 1) + combat[line_end:]
    damage_pos = combat.find("val damage =", line_end)
    if damage_pos < 0 or damage_pos - line_end > 800:
        raise RuntimeError("Lifeform defended damage line missing after rawIncoming")
    damage_start = combat.rfind("\n", 0, damage_pos) + 1
    damage_end = combat.find("\n", damage_pos)
    if damage_end < 0:
        raise RuntimeError("Lifeform defended damage line terminator missing")
    damage_line = combat[damage_start:damage_end]
    damage_indent = damage_line[: len(damage_line) - len(damage_line.lstrip())]
    prefix = damage_indent + "val damage = "
    if not damage_line.startswith(prefix):
        raise RuntimeError("Unexpected defended Lifeform damage assignment: " + damage_line)
    basic_expression = damage_line[len(prefix):]
    replacement = (
        damage_indent + "val basicDamage = " + basic_expression + "\n" +
        damage_indent + "val damage = if (lifeformSkill == null) basicDamage\n" +
        damage_indent + "  else CharacterStatCore.scaleByPercent(basicDamage, lifeformSkill.basicAttackPercent)"
    )
    combat = combat[:damage_start] + replacement + combat[damage_end:]
else:
    raise RuntimeError("Unexpected Lifeform scaled attack line: " + attack_line)

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
