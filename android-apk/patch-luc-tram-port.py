"""Final runtime port: V2 Lục Trầm stats/skills/encounter; no V2 equipment or lore."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
ASSETS = ROOT / "app/src/main/assets"

def replace(path, old, new):
    text = path.read_text()
    if new in text:
        return
    if text.count(old) != 1:
        raise RuntimeError(f"{path.name}: expected one port anchor: {old[:80]}")
    path.write_text(text.replace(old, new, 1))

(CORE / "LucTramFollower.kt").write_text('''package com.rabpit.backroom.core

object LucTramFollower {
  const val ID = "luc_tram"
  const val AVATAR = "avatars/luctram_avatar.png"
  const val ENCOUNTER_THRESHOLD = 25 // 25 / 10000 = V2's 0.25%.

  fun ensure(state: GameState): GameState {
    val old = state.characters[ID] ?: CharacterState(ID, "Lục Trầm",
      physiology = PhysiologyState.freshRunBaseline())
    val character = old.copy(name = "Lục Trầm", avatarRef = AVATAR,
      metadata = old.metadata + mapOf("npcType" to "follower", "combatant" to "true", "joinEligible" to "true",
        "encounterChance" to "0.25%", "encounterLevels" to "1-6"))
    return state.copy(characters = state.characters + (ID to character))
  }
}
''')
replace(CORE / "CharacterEquipmentSystem.kt",
        "private fun normalizeInternal(source: GameState, seedStarting: Boolean): GameState {",
        "private fun normalizeInternal(rawSource: GameState, seedStarting: Boolean): GameState {\n    val source = LucTramFollower.ensure(rawSource)")
replace(CORE / "CharacterEquipmentSystem.kt",
        "val weaponId = state.equipment[equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return 18",
        "val weaponId = state.equipment[equipmentId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return if (characterId == LucTramFollower.ID) 24 else 18")
main = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
anchor = '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia"), " Level 0 Lucia follower encounter"));'
replace(main, anchor, anchor + '\n    rolls.put("lucTramEncounter", thresholdRoll("lucTramEncounter", 10000, 25, exploreAction && level > 0 && !flagSpawned(state, "luc_tram"), " Lục Trầm follower 0.25% after Level 0"));')
anchor = '    if (rollSuccess(rolls, "luciaEncounter")) {\n      JSONObject lucia = flags.optJSONObject("lucia");'
block = '''    if (rollSuccess(rolls, "lucTramEncounter")) {
      JSONObject follower = flags.optJSONObject("luc_tram");
      if (follower == null) follower = new JSONObject();
      boolean joined = ensureSpecialFollowerInLegacyParty(state, "luc_tram", "Lục Trầm", false);
      follower.put("exists", true).put("encountered", true).put("present", true)
        .put("spawned", true).put("follower", true).put("joinPending", !joined)
        .put("levelEncountered", currentLevel(before));
      flags.put("luc_tram", follower);
    }

'''
replace(main, anchor, block + anchor)
replace(main, '\\nACTION_RUNTIME: ',
        '\\nLUC TRAM FOLLOWER: lucTramEncounter is Core-owned, 0.25% on EXPLORE after Level 0. Only success authorizes appearance. Follower luc_tram is distinct from hostile Entity luc_tram_hac_hoa. Do not invent V2 equipment or relationships.\\nACTION_RUNTIME: ')
sprite = "value.id==='syvial'?'syvial_overlay.png':''"
replace(ASSETS / "combat-93-snapshot.js", sprite,
        "value.id==='syvial'?'syvial_overlay.png':value.id==='luc_tram'?'luctram_overlay.png':''")
replace(ASSETS / "index.html", sprite,
        "value.id==='syvial'?'syvial_overlay.png':value.id==='luc_tram'?'luctram_overlay.png':''")
print("Lục Trầm V2 follower port installed: 50 HP, 5/5/5/5, 24 base damage, existing exact V2 skill kit, 0.25%.")

# One shared rare-Entity roll: 1..100 Diệp Minh, 101..300 Lục Trầm.
# Disjoint intervals preserve exactly 1% and 2%, without losing a spawn to priority.
anchor = '    rolls.put("diepMinhEncounter", diepMinhRoll);'
replace(main, anchor, anchor + '''
    int rareEntityRoll = diepMinhRoll.optInt("roll", 0);
    JSONObject lucTramEntityRoll = new JSONObject(diepMinhRoll.toString())
      .put("label", "lucTramEntityEncounter").put("threshold", 200)
      .put("rangeStart", 101).put("rangeEnd", 300)
      .put("chancePercent", 2.0).put("chance", "2.0000% Lục Trầm Hắc Hoá")
      .put("success", diepMinhRoll.optBoolean("eligible") && rareEntityRoll > 100 && rareEntityRoll <= 300);
    rolls.put("lucTramEntityEncounter", lucTramEntityRoll);''')
replace(main, '    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");',
        '''    JSONObject dark = rolls.optJSONObject("lucTramEntityEncounter");
    if (dark != null && dark.optBoolean("success", false)) {
      rolls.put("roamingEntityKey", "luc_tram_hac_hoa");
      rolls.getJSONObject("entityEncounter").put("success", true).put("selectedBy", "lucTramEntityEncounter");
      entityKey = "luc_tram_hac_hoa";
    }
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");''')
replace(main, 'case "slenderman": case "diep_minh":',
        'case "slenderman": case "diep_minh": case "luc_tram_hac_hoa":')
replace(main, '      case "hound": name = "Hound"; break;',
        '      case "luc_tram_hac_hoa": name = "Lục Trầm Hắc Hoá"; break;\n      case "hound": name = "Hound"; break;')
replace(main, 'ENTITY ROAMING HARD LOCK: mọi Entity',
        'LUC TRAM ENTITY HARD LOCK: lucTramEntityEncounter selects luc_tram_hac_hoa at exactly 2% on eligible EXPLORE/SEARCH/EXECUTE. It is hostile, uses Entity art, and cannot join Party or replace Follower luc_tram. Rare-Entity selection overrides normal roaming selection. ENTITY ROAMING HARD LOCK: mọi Entity')

replace(CORE / "CompanionSkillCatalog.kt", '    LUCIA_ID -> lucia', '''    LucTramFollower.ID -> listOf(
      s("Tịch Quang Hợp Kích", "SKILL", "Poker Dice Skill", "150% Weapon DMG."),
      s("Tịch Quang Phản Kiếm", "AUTO", "52% sau đòn thường/Skill", "+25% DMG; Trúng độc 2 lượt x 3% Max HP."),
      s("Nhất Tuyến Phá Vọng", "AUTO", "55% sau đòn thường/Skill", "+20% DMG; Xuyên giáp 10% trong 2 lượt."),
      s("Thiên Kiếm Chấn", "AUTO", "46% sau đòn thường/Skill", "+15% DMG; Choáng 1 lượt."),
      s("Bạch Hồng Quán Nhật", "AUTO", "49% sau đòn thường/Skill", "+20% DMG; Chảy máu 2 lượt x 3% Max HP."),
      s("Vạn Kiếm Quy Tâm", "AUTO", "51% sau đòn thường/Skill", "+20% DMG; Trúng độc 2 lượt x 4% Max HP."),
      s("Thiên Kiếm Định Giới", "ULTIMATE", "SSF / FSF", "60 hit x 115% current DMG; FSF nhân 200%.")
    )
    LUCIA_ID -> lucia''')

replace(main, '    int luciaScoutBonus = (partyHas(state, "lucia")', '''    if (lucTramEntityRoll.optBoolean("success")) {
      rolls.put("roamingEntityKey", "luc_tram_hac_hoa");
      normalEntityRoll.put("success", true).put("selectedBy", "lucTramEntityEncounter");
    }
    int luciaScoutBonus = (partyHas(state, "lucia")''')

replace(CORE / "CharacterStats.kt", '    "lucia" -> "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER"',
        '    "luc_tram" -> "SWORDSWOMAN / FOLLOWER"\n    "lucia" -> "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER"')

replace(main, '    if (value.contains("iris")) return presentCharacter(before, "iris")', '''    if (value.contains("hắc hoá") || value.contains("hắc hóa") || value.contains("hac_hoa")) return false;
    if (value.contains("lục trầm") || value.contains("luc tram") || value.contains("luc_tram"))
      return presentCharacter(before, "luc_tram") || rollSuccess(rolls, "lucTramEncounter");
    if (value.contains("iris")) return presentCharacter(before, "iris")''')
replace(main, 'else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter")',
        'else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "lucTramEncounter") || rollSuccess(rolls, "anNhienEncounter")')

replace(main, '        JSONObject member = op.optJSONObject("member");\n        if (member == null) continue;', '''        JSONObject member = op.optJSONObject("member");
        if (member == null) continue;
        String memberId = lower(member.optString("id", "")).trim();
        String memberName = lower(member.optString("name", "")).trim();
        if (memberId.equals("luc_tram_hac_hoa") || memberName.contains("hắc hoá") || memberName.contains("hắc hóa")) continue;
        if (memberId.equals("luc_tram") || memberName.equals("lục trầm") || memberName.equals("luc tram")) {
          if (!characterAddAllowed(before, "luc_tram", rolls)) continue;
          member.put("id", "luc_tram").put("name", "Lục Trầm");
        }''')
