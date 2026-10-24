from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) An Nhien remains a Level-0-origin character, but encounter is no longer mandatory or Level-0-only.
canon_path = CORE / "AnNhienCanon.kt"
canon = canon_path.read_text(encoding="utf-8")
_ann_old = '        "mandatoryEncounter" to "true",\n        "encounterChance" to "100%",\n'
_ann_new = '        "mandatoryEncounter" to "false",\n        "encounterChance" to "0.25%",\n        "encounterLevels" to "0-6",\n'
if _ann_old in canon:
    canon = replace_once(canon, _ann_old, _ann_new, "An Nhien optional 0.25 percent metadata")
elif _ann_new not in canon:
    raise RuntimeError("An Nhien optional 0.25 percent metadata: neither old nor new state found")
canon_path.write_text(canon, encoding="utf-8")


# 2) Iris and Syvial become first-class core followers. Do not invent avatar assets or an Iris combat tier.
special_path = CORE / "SpecialFollowersCanon.kt"
special_path.write_text(r'''package com.rabpit.backroom.core

const val LUC_TRAM_ID = "luc-tram"
const val SYVIAL_ID = "syvial"

const val LUC_TRAM_TICH_QUANG_ID = "luc-tram:tich-quang"
const val LUC_TRAM_KIEM_KHAI_ID = "luc-tram:thien-co-bach-kim-kiem-khai"
const val SYVIAL_GODKILLER_ID = "syvial:godkiller"
const val SYVIAL_LUCIFER_ARMOR_ID = "syvial:lucifer-armor"

object SpecialFollowersCanon {
  const val ENCOUNTER_CHANCE = "0.25%"
  const val ENCOUNTER_LEVELS = "0-6"

  val lucTramEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to LUC_TRAM_TICH_QUANG_ID,
    "armor" to LUC_TRAM_KIEM_KHAI_ID
  )

  val syvialEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to SYVIAL_GODKILLER_ID,
    "armor" to SYVIAL_LUCIFER_ARMOR_ID
  )

  fun lucTramCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = LUC_TRAM_ID,
      name = "Lục Trầm",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = LUC_TRAM_ID,
      name = "Lục Trầm",
      inventoryId = LUC_TRAM_ID,
      equipmentId = LUC_TRAM_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "role" to "Chính Đạo kiếm tu / Bạch Hồng Kiếm Nữ",
        "combatStyle" to "Kiếm tu",
        "signatureWeapon" to "Tịch Quang",
        "armor" to "Thiên Cơ Bạch Kim Kiếm Khải",
        "canonRef" to "LUC_TRAM_CODEX R05 + PROLOGUE ADDENDUM R06",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun syvialCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = SYVIAL_ID,
      name = "Syvial",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = SYVIAL_ID,
      name = "Syvial",
      inventoryId = SYVIAL_ID,
      equipmentId = SYVIAL_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "combatTier" to "UR+",
        "role" to "High-level supernatural swordswoman",
        "signatureWeapon" to "GodKiller",
        "armor" to "Lucifer Armor",
        "canonRef" to "SYVIAL-LUCIFER-CODEX-20260816-R03",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState {
    val lucTram = lucTramCharacter(state.characters[LUC_TRAM_ID])
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val lucTramInventory = state.inventories[LUC_TRAM_ID] ?: InventoryState(LUC_TRAM_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val lucTramEquipment = state.equipment[LUC_TRAM_ID]?.slots.orEmpty() + lucTramEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    return state.copy(
      characters = state.characters + (LUC_TRAM_ID to lucTram) + (SYVIAL_ID to syvial),
      inventories = state.inventories + (LUC_TRAM_ID to lucTramInventory) + (SYVIAL_ID to syvialInventory),
      equipment = state.equipment +
        (LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID, lucTramEquipment)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment))
    )
  }
}
''', encoding="utf-8")


# 3) Seed both followers in fresh state, outside Party, so a successful reunion can become an authoritative ADD.
state_path = CORE / "GameState.kt"
state = state_path.read_text(encoding="utf-8")
state = replace_once(
    state,
    '''        AN_NHIEN_ID to AnNhienCanon.character()
      ),
      inventories = mapOf(
        CAO_MINH_ID to InventoryState(CAO_MINH_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory()
      ),
      equipment = mapOf(
        CAO_MINH_ID to EquipmentState(CAO_MINH_ID, CaoMinhStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment()
      )
''',
    '''        AN_NHIEN_ID to AnNhienCanon.character(),
        LUC_TRAM_ID to SpecialFollowersCanon.lucTramCharacter(),
        SYVIAL_ID to SpecialFollowersCanon.syvialCharacter()
      ),
      inventories = mapOf(
        CAO_MINH_ID to InventoryState(CAO_MINH_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory(),
        LUC_TRAM_ID to InventoryState(LUC_TRAM_ID),
        SYVIAL_ID to InventoryState(SYVIAL_ID)
      ),
      equipment = mapOf(
        CAO_MINH_ID to EquipmentState(CAO_MINH_ID, CaoMinhStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment(),
        LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID, SpecialFollowersCanon.lucTramEquipmentSlots),
        SYVIAL_ID to EquipmentState(SYVIAL_ID, SpecialFollowersCanon.syvialEquipmentSlots)
      )
''',
    "fresh Iris and Syvial follower seed",
)
state_path.write_text(state, encoding="utf-8")


# 4) Backfill existing core saves as well. SharedPreferences load goes through GameStateCodec.decode().
codec_path = CORE / "GameStateCodec.kt"
codec = codec_path.read_text(encoding="utf-8")
codec = replace_once(
    codec,
    "    return AnNhienCanon.ensure(decoded)\n",
    "    return SpecialFollowersCanon.ensure(AnNhienCanon.ensure(decoded))\n",
    "special follower save backfill",
)
codec_path.write_text(codec, encoding="utf-8")


# 5) Runtime encounter policy: three independent 0.25% rolls on eligible physical turns in Level 0-6.
main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    '    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 1, 1, level == 0 && physical && !anNhienEncountered, " mandatory Level 0 follower"));\n',
    '    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered, " follower encounter"));\n',
    "An Nhien 0.25 percent encounter roll",
)
main = replace_once(
    main,
    '    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed && !(level == 0 && !anNhienEncountered), ""));\n',
    '    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));\n',
    "remove An Nhien Level 0 survivor suppression",
)
main = replace_once(
    main,
    '    rolls.put("lucTramReunion", thresholdRoll("lucTramReunion", 1000000, 25, reunionEligibleAndroid(state, "luc-tram"), ""));\n',
    '    rolls.put("lucTramReunion", thresholdRoll("lucTramReunion", 10000, 25, physical && reunionEligibleAndroid(state, "luc-tram"), " follower encounter"));\n',
    "Iris 0.25 percent reunion roll",
)
main = replace_once(
    main,
    '    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 1000000, 25, reunionEligibleAndroid(state, "syvial"), ""));\n',
    '    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));\n',
    "Syvial 0.25 percent reunion roll",
)

# Level 0 must be escapable whether or not An Nhien has appeared.
main = replace_once(
    main,
    '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    if (currentLevel(before) == 0 && !anNhienEncountered(before)) return false;
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
''',
    '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
''',
    "remove mandatory An Nhien Level 0 exit gate",
)

# Shared helper: a successful special encounter automatically becomes a follower when a slot exists.
helper_anchor = '''  private boolean reunionEligibleAndroid(JSONObject state, String key) {
'''
follower_helper = r'''  private boolean ensureSpecialFollowerInLegacyParty(JSONObject state, String id, String name, boolean nonCombat) throws Exception {
    JSONArray party = state.optJSONArray("party");
    if (party == null) party = new JSONArray();
    String targetId = lower(id).trim();
    String targetName = lower(name).trim();
    for (int i = 0; i < party.length(); i++) {
      Object item = party.opt(i);
      if (!(item instanceof JSONObject)) continue;
      JSONObject member = (JSONObject)item;
      if (lower(member.optString("id", "")).trim().equals(targetId) ||
          lower(member.optString("name", "")).trim().equals(targetName)) {
        state.put("party", party);
        return true;
      }
    }
    // Legacy party excludes Kai, so three entries means the authoritative 4-member party is full.
    if (party.length() >= 3) {
      state.put("party", party);
      return false;
    }
    party.put(new JSONObject()
      .put("id", id)
      .put("name", name)
      .put("present", true)
      .put("joinConfirmed", true)
      .put("presence", "ACTIVE")
      .put("role", "follower")
      .put("nonCombat", nonCombat));
    state.put("party", party);
    return true;
  }

'''
if follower_helper not in main:
    if helper_anchor not in main:
        raise RuntimeError("special follower helper anchor missing")
    main = main.replace(helper_anchor, follower_helper + helper_anchor, 1)

# An Nhien can now be encountered on whichever Level the 0.25% roll succeeds.
main = replace_once(
    main,
    '        .put("levelEncountered", 0)\n',
    '        .put("levelEncountered", currentLevel(before))\n',
    "An Nhien encounter level",
)

old_party_block = '''      JSONArray party = state.optJSONArray("party");
      if (party == null) party = new JSONArray();
      if (!arrayHasName(party, "An Nhiên")) {
        if (party.length() >= 3) party.remove(party.length() - 1);
        party.put(new JSONObject()
          .put("id", "an-nhien")
          .put("name", "An Nhiên")
          .put("present", true)
          .put("joinConfirmed", true)
          .put("presence", "ACTIVE")
          .put("role", "follower")
          .put("nonCombat", true));
      }
      state.put("party", party);
'''
new_party_block = '''      boolean anNhienJoined = ensureSpecialFollowerInLegacyParty(state, "an-nhien", "An Nhiên", true);
      anNhien.put("joinPending", !anNhienJoined);
'''
main = replace_once(main, old_party_block, new_party_block, "An Nhien no-eviction follower join")

# Commit Iris/Syvial encounter state deterministically from their locked rolls, just like An Nhien.
tail_anchor = '''    state.put("flags", flags);
    return state;
  }
'''
tail_replacement = '''    if (rollSuccess(rolls, "lucTramReunion")) {
      JSONObject lucTram = flags.optJSONObject("luc-tram");
      if (lucTram == null) lucTram = new JSONObject();
      boolean lucTramJoined = ensureSpecialFollowerInLegacyParty(state, "luc-tram", "Lục Trầm", false);
      lucTram.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !lucTramJoined);
      flags.put("luc-tram", lucTram);
    }

    if (rollSuccess(rolls, "syvialReunion")) {
      JSONObject syvial = flags.optJSONObject("syvial");
      if (syvial == null) syvial = new JSONObject();
      boolean syvialJoined = ensureSpecialFollowerInLegacyParty(state, "syvial", "Syvial", false);
      syvial.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !syvialJoined);
      flags.put("syvial", syvial);
    }

    state.put("flags", flags);
    return state;
  }
'''
main = replace_once(main, tail_anchor, tail_replacement, "Iris Syvial deterministic follower commit")

# Keep the AN NHIEN prefix because the later Jeff patch inserts its own lock immediately after this line.
old_prompt = ('            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. '
              'anNhienEncounter success=true là cuộc gặp bắt buộc ở Level 0 và phải được kể trong lượt đó; '
              'sau khi gặp cô bé luôn theo Cao Minh, không chiến đấu, không dùng vũ khí, không tự tách nhóm. '
              'Cô chỉ có +10% loot chance và +2% exit chance khi đang theo Cao Minh, đúng như GAMEPLAY_ROLLS. '
              'Không tự thêm năng lực hoặc lore. " +\n')
new_prompt = ('            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. '
              'anNhienEncounter là roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi chưa gặp; '
              'không còn là cuộc gặp bắt buộc ở Level 0 và không được chặn việc rời Level 0. '
              'Chỉ khi success=true mới được cho An Nhiên xuất hiện trong lượt đó. Sau khi gặp, cô là follower của Cao Minh nếu Party còn chỗ; '
              'nếu Party đầy thì giữ cô present với joinPending=true thay vì đuổi một thành viên khác. Cô không chiến đấu, không dùng vũ khí, '
              'và chỉ có +10% loot chance cùng +2% exit chance khi thực sự đang theo Cao Minh. Không tự thêm năng lực hoặc lore. " +\n'
              '            "LỤC TRẦM / SYVIAL FOLLOWER LOCK: lucTramReunion và syvialReunion là hai roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6. '
              'Chỉ success=true mới cho nhân vật tương ứng xuất hiện lần đầu/reunion. Khi gặp và Party còn chỗ, Lục Trầm hoặc Syvial tự gia nhập với role=follower; '
              'nếu Party đầy thì đánh dấu present + joinPending, tuyệt đối không tự đuổi thành viên khác. Lục Trầm giữ canon Chính Đạo kiếm tu, Bạch Hồng Kiếm Nữ, bản mệnh kiếm Tịch Quang và Thiên Cơ Bạch Kim Kiếm Khải; '
              'không tự gán cấp chiến lực cho Lục Trầm. Syvial giữ canon UR+, GodKiller và Lucifer Armor. Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay. " +\n')
main = replace_once(main, old_prompt, new_prompt, "special follower GM lock")

for marker in [
    'thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered',
    'thresholdRoll("lucTramReunion", 10000, 25, physical && reunionEligibleAndroid(state, "luc-tram")',
    'thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial")',
    'ensureSpecialFollowerInLegacyParty(state, "luc-tram", "Lục Trầm", false)',
    'ensureSpecialFollowerInLegacyParty(state, "syvial", "Syvial", false)',
    'LỤC TRẦM / SYVIAL FOLLOWER LOCK:',
]:
    if marker not in main:
        raise RuntimeError(f"special follower runtime contract missing: {marker}")

for forbidden in [
    'thresholdRoll("anNhienEncounter", 1, 1',
    'currentLevel(before) == 0 && !anNhienEncountered(before)',
    'thresholdRoll("lucTramReunion", 1000000, 25',
    'thresholdRoll("syvialReunion", 1000000, 25',
    'party.remove(party.length() - 1)',
]:
    if forbidden in main:
        raise RuntimeError(f"obsolete special follower behavior still present: {forbidden}")

MAIN.write_text(main, encoding="utf-8")


# 6) Regression coverage for authoritative follower definitions and the four-member party cap.
test_path = TESTS / "SpecialFollowersTest.kt"
test_path.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowersTest {
  @Test fun lucTramAndSyvialAreSeededAsOptionalFollowersOutsideParty() {
    val state = GameState.initial()
    val lucTram = state.characters[LUC_TRAM_ID]!!
    val syvial = state.characters[SYVIAL_ID]!!

    assertEquals("follower", lucTram.metadata["npcType"])
    assertEquals("true", lucTram.metadata["joinEligible"])
    assertEquals("0.25%", lucTram.metadata["encounterChance"])
    assertEquals("0-6", lucTram.metadata["encounterLevels"])
    assertEquals("Chính Đạo kiếm tu / Bạch Hồng Kiếm Nữ", lucTram.metadata["role"])
    assertNull(lucTram.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.lucTramEquipmentSlots, state.equipment[LUC_TRAM_ID]!!.slots)

    assertEquals("follower", syvial.metadata["npcType"])
    assertEquals("true", syvial.metadata["joinEligible"])
    assertEquals("0.25%", syvial.metadata["encounterChance"])
    assertEquals("UR+", syvial.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.syvialEquipmentSlots, state.equipment[SYVIAL_ID]!!.slots)

    assertFalse(LUC_TRAM_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
    assertEquals("false", state.characters[AN_NHIEN_ID]!!.metadata["mandatoryEncounter"])
    assertEquals("0.25%", state.characters[AN_NHIEN_ID]!!.metadata["encounterChance"])
  }

  @Test fun decodeBackfillsSpecialFollowersWithoutForcingThemIntoParty() {
    val base = GameState.initial()
    val stripped = base.copy(
      characters = base.characters - LUC_TRAM_ID - SYVIAL_ID,
      inventories = base.inventories - LUC_TRAM_ID - SYVIAL_ID,
      equipment = base.equipment - LUC_TRAM_ID - SYVIAL_ID
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(stripped))
    assertTrue(LUC_TRAM_ID in decoded.characters)
    assertTrue(SYVIAL_ID in decoded.characters)
    assertTrue(LUC_TRAM_ID in decoded.inventories)
    assertTrue(SYVIAL_ID in decoded.inventories)
    assertFalse(LUC_TRAM_ID in decoded.party.memberIds)
    assertFalse(SYVIAL_ID in decoded.party.memberIds)
  }

  @Test fun caoMinhAndAllThreeSpecialFollowersFitExactlyInParty() {
    var state = GameState.initial()
    for ((index, id) in listOf(AN_NHIEN_ID, LUC_TRAM_ID, SYVIAL_ID).withIndex()) {
      val result = PartyEngine.execute(state, PartyCommand(
        commandId = "add-special-$index",
        turnId = state.turn.currentTurnId,
        actorId = CAO_MINH_ID,
        targetId = id,
        source = CommandSource.SYSTEM,
        operation = PartyCommand.Operation.ADD,
        consentConfirmed = true,
        targetPresent = true
      ))
      assertTrue(result.validation.reason ?: "failed to add $id", result.applied)
      state = result.state
    }
    assertEquals(4, state.party.memberIds.size)
    assertEquals(listOf(CAO_MINH_ID, AN_NHIEN_ID, LUC_TRAM_ID, SYVIAL_ID), state.party.memberIds)
  }
}
''', encoding="utf-8")

required_files = {
    special_path: ["object SpecialFollowersCanon", '"Chính Đạo kiếm tu / Bạch Hồng Kiếm Nữ"', '"combatTier" to "UR+"'],
    test_path: ["caoMinhAndAllThreeSpecialFollowersFitExactlyInParty", 'assertNull(lucTram.metadata["combatTier"])'],
}
for path, markers in required_files.items():
    content = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in content:
            raise RuntimeError(f"{path.name}: missing marker {marker}")

print("Special followers updated: An Nhiên/Iris/Syvial each use independent 0.25% Level 0-6 physical encounter rolls; Iris and Syvial are authoritative followers with canon equipment.")
