"""Restore four 1.1.93a Entity assets/encounters; keep Combat93 stats and skills."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

def replace_one(source, before, after):
    if source.count(before) != 1:
        raise RuntimeError("Four-Entity port anchor missing or repeated: " + before[:85])
    return source.replace(before, after, 1)

java = MAIN.read_text(encoding="utf-8")

# Share the 1..10000 rare-Entity draw with Diệp Minh (1..100) and dark Lục Trầm
# (101..300). Four disjoint 200-number windows each mean precisely 2% per
# eligible roll without priority silently reducing any Entity's spawn chance.
roll_anchor = '    rolls.put("lucTramEntityEncounter", lucTramEntityRoll);'
roll_block = """
    String[] restoredEntityKeys = {"the_lifeform_bacteria_01", "the_lifeform_bacteria_02",
      "the_lifeform_bacteria_03", "async_member_rifle_aim_right_01"};
    String[] restoredRollLabels = {"bacterialStalkerEncounter", "bacterialStriderEncounter",
      "bacterialWeaverEncounter", "researchAsyncMemberEncounter"};
    for (int i = 0; i < restoredEntityKeys.length; i++) {
      int rangeStart = 301 + i * 200, rangeEnd = rangeStart + 199;
      JSONObject restoredRoll = new JSONObject(diepMinhRoll.toString())
        .put("label", restoredRollLabels[i]).put("threshold", 200)
        .put("rangeStart", rangeStart).put("rangeEnd", rangeEnd)
        .put("chancePercent", 2.0)
        .put("success", diepMinhRoll.optBoolean("eligible")
          && rareEntityRoll >= rangeStart && rareEntityRoll <= rangeEnd);
      rolls.put(restoredRollLabels[i], restoredRoll);
    }"""
java = replace_one(java, roll_anchor, roll_anchor + roll_block)

# Select the winning rare Entity after the normal encounter roll exists.
# This uses the same encounter path as dark Lục Trầm, never a second encounter.
selection_anchor = '    int luciaScoutBonus = (partyHas(state, "lucia")'
selection_block = """    for (int i = 0; i < restoredEntityKeys.length; i++) {
      if (rollSuccess(rolls, restoredRollLabels[i])) {
        rolls.put("roamingEntityKey", restoredEntityKeys[i]);
        normalEntityRoll.put("success", true).put("selectedBy", restoredRollLabels[i]);
      }
    }
"""
java = replace_one(java, selection_anchor, selection_block + selection_anchor)

# Accept canonical local keys in the Android bridge and return correct overlay names.
java = replace_one(java,
    'case "slenderman": case "diep_minh": case "luc_tram_hac_hoa":',
    'case "slenderman": case "diep_minh": case "luc_tram_hac_hoa":\n'
    '      case "the_lifeform_bacteria_01": case "the_lifeform_bacteria_02":\n'
    '      case "the_lifeform_bacteria_03": case "async_member_rifle_aim_right_01":')
java = replace_one(java, '      case "hound": name = "Hound"; break;',
    '      case "the_lifeform_bacteria_01": name = "Bacterial Stalker"; break;\n'
    '      case "the_lifeform_bacteria_02": name = "Bacterial Strider"; break;\n'
    '      case "the_lifeform_bacteria_03": name = "Bacterial Weaver"; break;\n'
    '      case "async_member_rifle_aim_right_01": name = "Research Async Member"; break;\n'
    '      case "hound": name = "Hound"; break;')

# The Snapshot's JS allowlist and the GM's canonical keys must agree with Java.
java = replace_one(java, "'slenderman','diep_minh'];",
    "'slenderman','diep_minh','the_lifeform_bacteria_01','the_lifeform_bacteria_02',"
    "'the_lifeform_bacteria_03','async_member_rifle_aim_right_01'];")
java = replace_one(java, 'LOCAL ROAMING POOL: hound, clump,',
    'LOCAL ROAMING POOL: hound, the_lifeform_bacteria_01, the_lifeform_bacteria_02, '
    'the_lifeform_bacteria_03, async_member_rifle_aim_right_01, clump,')
MAIN.write_text(java, encoding="utf-8")
print("Restored four 1.1.93a Entity encounter keys: 2% each, original Combat93 skills unchanged.")
