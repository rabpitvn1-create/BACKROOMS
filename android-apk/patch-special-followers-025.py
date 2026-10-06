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
canon = replace_once(
    canon,
    '        "mandatoryEncounter" to "true",\n        "encounterChance" to "100%",\n',
    '        "mandatoryEncounter" to "false",\n        "encounterChance" to "0.25%",\n        "encounterLevels" to "0-6",\n',
    "An Nhien optional 0.25 percent metadata",
)
canon_path.write_text(canon, encoding="utf-8")


# 2) Iris and Syvial become first-class core followers. Do not invent avatar assets or an Iris combat tier.
special_path = CORE / "SpecialFollowersCanon.kt"
special_path.write_text(r'''package com.rabpit.backroom.core

const val IRIS_ID = "iris"
const val SYVIAL_ID = "syvial"

const val IRIS_IVORY_ID = "iris:ivory"
const val IRIS_EBONY_ID = "iris:ebony"
const val IRIS_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"
const val SYVIAL_GODKILLER_ID = "syvial:godkiller"
const val SYVIAL_LUCIFER_ARMOR_ID = "syvial:lucifer-armor"

object SpecialFollowersCanon {
  const val ENCOUNTER_CHANCE = "0.25%"
  const val ENCOUNTER_LEVELS = "0-6"

  val irisEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon_primary" to IRIS_IVORY_ID,
    "weapon_secondary" to IRIS_EBONY_ID,
    "armor" to IRIS_RECON_FRAME_ID
  )

  val syvialEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to SYVIAL_GODKILLER_ID,
    "armor" to SYVIAL_LUCIFER_ARMOR_ID
  )

  fun irisCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = IRIS_ID,
      name = "Iris",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = IRIS_ID,
      name = "Iris",
      inventoryId = IRIS_ID,
      equipmentId = IRIS_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "role" to "Scout / Target Eliminator",
        "combatStyle" to "Gunslinger",
        "signatureWeapons" to "Ivory & Ebony",
        "armor" to "Blackblood Recon Frame R03",
        "canonRef" to "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05",
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
    val iris = irisCharacter(state.characters[IRIS_ID])
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val irisInventory = state.inventories[IRIS_ID] ?: InventoryState(IRIS_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val irisEquipment = state.equipment[IRIS_ID]?.slots.orEmpty() + irisEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    return state.copy(
      characters = state.characters + (IRIS_ID to iris) + (SYVIAL_ID to syvial),
      inventories = state.inventories + (IRIS_ID to irisInventory) + (SYVIAL_ID to syvialInventory),
      equipment = state.equipment +
        (IRIS_ID to EquipmentState(IRIS_ID, irisEquipment)) +
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
        KAI_ID to InventoryState(KAI_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory()
      ),
      equipment = mapOf(
        KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment()
      )
''',
    '''        AN_NHIEN_ID to AnNhienCanon.character(),
        IRIS_ID to SpecialFollowersCanon.irisCharacter(),
        SYVIAL_ID to SpecialFollowersCanon.syvialCharacter()
      ),
      inventories = mapOf(
        KAI_ID to InventoryState(KAI_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory(),
        IRIS_ID to InventoryState(IRIS_ID),
        SYVIAL_ID to InventoryState(SYVIAL_ID)
      ),
      equipment = mapOf(
        KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment(),
        IRIS_ID to EquipmentState(IRIS_ID, SpecialFollowersCanon.irisEquipmentSlots),
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
    '    rolls.put("irisReunion", thresholdRoll("irisReunion", 1000000, 25, reunionEligibleAndroid(state, "iris"), ""));\n',
    '    rolls.put("irisReunion", thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris"), " follower encounter"));\n',
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
tail_replacement = '''    if (rollSuccess(rolls, "irisReunion")) {
      JSONObject iris = flags.optJSONObject("iris");
      if (iris == null) iris = new JSONObject();
      boolean irisJoined = ensureSpecialFollowerInLegacyParty(state, "iris", "Iris", false);
      iris.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !irisJoined);
      flags.put("iris", iris);
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
              'sau khi gặp cô bé luôn theo Kai, không chiến đấu, không dùng vũ khí, không tự tách nhóm. '
              'Cô chỉ có +10% loot chance và +2% exit chance khi đang theo Kai, đúng như GAMEPLAY_ROLLS. '
              'Không tự thêm năng lực hoặc lore. " +\n')
new_prompt = ('            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. '
              'anNhienEncounter là roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi chưa gặp; '
              'không còn là cuộc gặp bắt buộc ở Level 0 và không được chặn việc rời Level 0. '
              'Chỉ khi success=true mới được cho An Nhiên xuất hiện trong lượt đó. Sau khi gặp, cô là follower của Kai nếu Party còn chỗ; '
              'nếu Party đầy thì giữ cô present với joinPending=true thay vì đuổi một thành viên khác. Cô không chiến đấu, không dùng vũ khí, '
              'và chỉ có +10% loot chance cùng +2% exit chance khi thực sự đang theo Kai. Không tự thêm năng lực hoặc lore. " +\n'
              '            "IRIS / SYVIAL FOLLOWER LOCK: irisReunion và syvialReunion là hai roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6. '
              'Chỉ success=true mới cho nhân vật tương ứng xuất hiện lần đầu/reunion. Khi gặp và Party còn chỗ, Iris hoặc Syvial tự gia nhập với role=follower; '
              'nếu Party đầy thì đánh dấu present + joinPending, tuyệt đối không tự đuổi thành viên khác. Iris giữ canon Scout / Target Eliminator, Gunslinger với Ivory & Ebony và Blackblood Recon Frame R03; '
              'không tự gán cấp chiến lực cho Iris. Syvial giữ canon UR+, GodKiller và Lucifer Armor. Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay. " +\n')
main = replace_once(main, old_prompt, new_prompt, "special follower GM lock")

for marker in [
    'thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered',
    'thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris")',
    'thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial")',
    'ensureSpecialFollowerInLegacyParty(state, "iris", "Iris", false)',
    'ensureSpecialFollowerInLegacyParty(state, "syvial", "Syvial", false)',
    'IRIS / SYVIAL FOLLOWER LOCK:',
]:
    if marker not in main:
        raise RuntimeError(f"special follower runtime contract missing: {marker}")

for forbidden in [
    'thresholdRoll("anNhienEncounter", 1, 1',
    'currentLevel(before) == 0 && !anNhienEncountered(before)',
    'thresholdRoll("irisReunion", 1000000, 25',
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
  @Test fun irisAndSyvialAreSeededAsOptionalFollowersOutsideParty() {
    val state = GameState.initial()
    val iris = state.characters[IRIS_ID]!!
    val syvial = state.characters[SYVIAL_ID]!!

    assertEquals("follower", iris.metadata["npcType"])
    assertEquals("true", iris.metadata["joinEligible"])
    assertEquals("0.25%", iris.metadata["encounterChance"])
    assertEquals("0-6", iris.metadata["encounterLevels"])
    assertEquals("Scout / Target Eliminator", iris.metadata["role"])
    assertNull(iris.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.irisEquipmentSlots, state.equipment[IRIS_ID]!!.slots)

    assertEquals("follower", syvial.metadata["npcType"])
    assertEquals("true", syvial.metadata["joinEligible"])
    assertEquals("0.25%", syvial.metadata["encounterChance"])
    assertEquals("UR+", syvial.metadata["combatTier"])
    assertEquals(SpecialFollowersCanon.syvialEquipmentSlots, state.equipment[SYVIAL_ID]!!.slots)

    assertFalse(IRIS_ID in state.party.memberIds)
    assertFalse(SYVIAL_ID in state.party.memberIds)
    assertEquals("false", state.characters[AN_NHIEN_ID]!!.metadata["mandatoryEncounter"])
    assertEquals("0.25%", state.characters[AN_NHIEN_ID]!!.metadata["encounterChance"])
  }

  @Test fun decodeBackfillsSpecialFollowersWithoutForcingThemIntoParty() {
    val base = GameState.initial()
    val stripped = base.copy(
      characters = base.characters - IRIS_ID - SYVIAL_ID,
      inventories = base.inventories - IRIS_ID - SYVIAL_ID,
      equipment = base.equipment - IRIS_ID - SYVIAL_ID
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(stripped))
    assertTrue(IRIS_ID in decoded.characters)
    assertTrue(SYVIAL_ID in decoded.characters)
    assertTrue(IRIS_ID in decoded.inventories)
    assertTrue(SYVIAL_ID in decoded.inventories)
    assertFalse(IRIS_ID in decoded.party.memberIds)
    assertFalse(SYVIAL_ID in decoded.party.memberIds)
  }

  @Test fun kaiAndAllThreeSpecialFollowersFitExactlyInParty() {
    var state = GameState.initial()
    for ((index, id) in listOf(AN_NHIEN_ID, IRIS_ID, SYVIAL_ID).withIndex()) {
      val result = PartyEngine.execute(state, PartyCommand(
        commandId = "add-special-$index",
        turnId = state.turn.currentTurnId,
        actorId = KAI_ID,
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
    assertEquals(listOf(KAI_ID, AN_NHIEN_ID, IRIS_ID, SYVIAL_ID), state.party.memberIds)
  }
}
''', encoding="utf-8")

required_files = {
    special_path: ["object SpecialFollowersCanon", '"Scout / Target Eliminator"', '"combatTier" to "UR+"'],
    test_path: ["kaiAndAllThreeSpecialFollowersFitExactlyInParty", 'assertNull(iris.metadata["combatTier"])'],
}
for path, markers in required_files.items():
    content = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in content:
            raise RuntimeError(f"{path.name}: missing marker {marker}")

print("Special followers updated: An Nhiên/Iris/Syvial each use independent 0.25% Level 0-6 physical encounter rolls; Iris and Syvial are authoritative followers with canon equipment.")
