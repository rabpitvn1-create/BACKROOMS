from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
COMPANIONS = ROOT / "app/src/main/assets/campaign_story/companion-continuity.json"
GAME_STATE = CORE / "GameState.kt"
GAME_CORE = CORE / "GameCoreFacade.kt"
ENGINES = CORE / "Engines.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_regex_once(text: str, pattern: str, replacement: str, label: str, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex anchor, found {count}")
    return updated


# ---------------------------------------------------------------------------
# 1) Canon assets: 2299 / SRU / Async. Hứa Thuý Lan is removed from this
# campaign. Companion encounter levels remain backend-only story authority.
# ---------------------------------------------------------------------------
continuity = json.loads(COMPANIONS.read_text(encoding="utf-8"))
expected = {
    "lucia": (0, "FIXED_ENCOUNTER"),
    "syvial": (37, "FIXED_REUNION"),
    "iris": (94, "FIXED_REUNION"),
}
if continuity.get("missionYear") != 2299 or continuity.get("unit") != "SRU" or continuity.get("missionSubject") != "Async":
    raise RuntimeError("story_companion_mission_canon_invalid")
entry = continuity.get("entry") or {}
if entry.get("participants") != ["kai", "iris", "syvial"] or entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLevelsDifferent") is not True:
    raise RuntimeError("story_companion_entry_separation_invalid")
for character_id, (level, event_type) in expected.items():
    raw = (continuity.get("companions") or {}).get(character_id) or {}
    if raw.get("level") != level or raw.get("eventType") != event_type:
        raise RuntimeError("story_companion_gate_invalid:" + character_id)
    if raw.get("randomSpawn") is not False or raw.get("storyOwned") is not True:
        raise RuntimeError("story_companion_random_spawn_must_be_disabled:" + character_id)
if (continuity.get("companions") or {}).get("lucia", {}).get("requiresQuest") is not False:
    raise RuntimeError("lucia_fixed_encounter_must_not_require_quest")

story = json.loads(STORY.read_text(encoding="utf-8"))
story["sourceRefs"] = [
    "TEXT.GAME.MAIN.STORY",
    "TEXT.GAME.RULES",
    "TEXT.GAME.GM.RULES",
    "BACKROOMS-WORLD-CORE-R2",
    "BACKROOMS-LEVELS-R2-L0-6",
    "KAI-AKECHI-TWILIGHT-CODEX-20260830-R08",
    "IRIS-BELIAL-SRU-CODEX-20260830-R06",
    "SYVIAL-LUCIFER-CODEX-20260830-R04",
    "STORY_COMPANIONS_R01",
]
story["entryEvent"].update({
    "year": 2299,
    "locationBefore": "SRU mission staging area, Frontrooms",
    "arrivalLevelsDifferent": True,
})
story["officialMission"].update({
    "year": 2299,
    "unit": "SRU",
    "subject": "Async",
    "objective": "Điều tra hoạt động của Async, cổng không gian và nguy cơ của Backrooms đối với Frontrooms.",
})
story.pop("kaiPrivateObjective", None)
locks = story.setdefault("globalLocks", {})
locks.pop("neverConfirmHuaThuyLanInBackroomsWithoutDiscoveredEvidence", None)
locks.update({
    "companionContinuityStoryOwned": True,
    "randomCompanionSpawnDisabled": True,
    "companionContinuityRef": "campaign_story/companion-continuity.json",
})
story["longTermObjective"] = "Kai phải hoàn thành nhiệm vụ SRU điều tra Async, sống sót, hiểu đủ quy luật cục bộ để tiếp tục tiến sâu và tìm lại Iris cùng Syvial bằng continuity hợp lệ."

string_replacements = (
    ("năm 2267", "năm 2299"),
    ("Iris, Syvial hoặc Hứa Thuý Lan", "Iris hoặc Syvial"),
    ("Iris, Syvial và Hứa Thuý Lan", "Iris và Syvial"),
    ("Iris, Syvial hoặc bằng chứng về Hứa Thuý Lan", "Iris hoặc Syvial"),
    ("tìm đồng đội và tìm Hứa Thuý Lan", "tìm đồng đội"),
    ("tìm đồng đội và tìm bằng chứng thật về Hứa Thuý Lan", "tìm đồng đội"),
    ("Reunion và tung tích Hứa Thuý Lan vẫn là các tuyến dài hạn", "Reunion với Iris và Syvial vẫn là tuyến dài hạn"),
    ("cả nhiệm vụ chính thức và mục tiêu cá nhân", "nhiệm vụ SRU và mục tiêu tìm lại đồng đội"),
    ("dấu của Hứa Thuý Lan", "dấu của đồng đội"),
)
for beat in story.get("beats", []):
    for key in ("storyPurpose", "visibleObjective", "characterThread", "transitionStory"):
        value = beat.get(key)
        if not isinstance(value, str):
            continue
        for old, new in string_replacements:
            value = value.replace(old, new)
        beat[key] = value

for beat in story.get("beats", []):
    if beat.get("areaId") == "0":
        beat["characterThread"] = "Kai biết cả ba thành viên SRU chủ động bước qua cùng một cổng năm 2299 để điều tra Async rồi bị phân tán tới các Level khác nhau. Hắn không biết Iris hoặc Syvial đang ở đâu. Lucia Lục là một cuộc gặp cố định của Level 0 nhưng không phải quest."
    elif beat.get("areaId") == "epsilon":
        beat["characterThread"] = "Không được cho Kai nghe thấy Iris hoặc Syvial chỉ vì cốt truyện cần họ; giọng quen thuộc có thể là hiện tượng và phải giữ trạng thái chưa xác nhận."
    elif beat.get("areaId") == "0.01":
        beat["characterThread"] = "Mục tiêu điều tra Async và tìm lại đồng đội cùng tồn tại, nhưng không mục tiêu nào cho phép Kai coi một dấu chưa kiểm chứng là sự thật."
    elif beat.get("areaId") == "0.1":
        beat["characterThread"] = "Kai có thể lo cho Iris và Syvial nhưng vẫn hành động có kỷ luật; không biến nỗi lo thành mất phán đoán."
    elif beat.get("areaId") == "0.22":
        beat["characterThread"] = "Nếu xuất hiện dấu vết con người hoặc Async, phải phân biệt dấu mới/cũ và tuyệt đối không gán cho Iris hoặc Syvial khi chưa có căn cứ."
    elif beat.get("areaId") == "0.99":
        beat["characterThread"] = "Một tín hiệu giống thiết bị SRU, Async hoặc dấu của đồng đội chỉ được coi là manh mối nếu hệ thống thật sự sinh ra nó; story không tự khẳng định nguồn."
    elif beat.get("areaId") == "Dullness":
        beat["characterThread"] = "Ký ức về Iris hoặc Syvial có thể bị môi trường gợi lại, nhưng không được biến thành thông tin vị trí thật."
    elif beat.get("areaId") == "Red Rooms":
        beat["characterThread"] = "Kai rời hệ vùng Level 0 mà chưa bắt buộc phải tìm thấy Iris hoặc Syvial; reunion của họ do continuity cốt truyện ở các Level đã khóa quyết định."
    elif beat.get("areaId") == "1":
        beat["visibleObjective"] = "Thiết lập vị trí trong mạng gara, tìm nguồn tài nguyên đáng tin, đánh giá blackout, tiếp tục điều tra Async và tìm dấu vết đồng đội mà không giả định kết quả."
        beat["characterThread"] = "Reunion với Iris và Syvial là tuyến dài hạn do Core story continuity quyết định; sang Level 1 không tự động mở khóa câu trả lời."

story_text = json.dumps(story, ensure_ascii=False, indent=2) + "\n"
if "Hứa Thuý Lan" in story_text or "2267" in story_text or "Black Blood" in story_text:
    raise RuntimeError("obsolete_campaign_canon_survived_story_asset")
STORY.write_text(story_text, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) New Game prose/state: SRU enters in 2299 and the three team members land
# on different Backrooms Levels. No private missing-person subplot remains.
# ---------------------------------------------------------------------------
index = INDEX.read_text(encoding="utf-8")
prologue = '''Năm 2299.

Cổng không gian trước mặt đội SRU đã ổn định đủ lâu để bắt đầu nhiệm vụ. Lệnh điều tra chỉ rõ mục tiêu: tiến vào, xác minh hoạt động của Async và đánh giá nguy cơ của Backrooms đối với Frontrooms.

Kai kiểm tra lần cuối trang bị. Iris và Syvial đã sẵn sàng ở hai bên. Không ai bị kéo vào. Không ai no-clip. Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ.

Kai vẫn nhìn thấy Iris và Syvial khi vượt qua ranh giới. Rồi khoảng cách giữa ba người mất ý nghĩa. Backrooms phân tán họ tới những Level khác nhau; Kai không biết hai người còn lại đã bị ném tới đâu.

Không có cảm giác rơi tự do. Không có gió quất vào người, không có lực kéo tăng dần, cũng không còn khái niệm rõ ràng về trên hay dưới. Cơ thể Kai vẫn ở đó, nhưng khoảng cách xung quanh hắn dường như không còn được đo theo cách quen thuộc.

Cảm giác ấy kéo dài chưa tới một nhịp tim.

Trọng lực trở lại đột ngột.'''
index = replace_regex_once(
    index,
    r'Năm 2267\..*?Trọng lực trở lại đột ngột\.',
    lambda _: prologue,
    "replace obsolete 2267 prologue",
    re.S,
)
index = index.replace('entryEvent:{year:2267,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true}',
                      'entryEvent:{year:2299,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true,arrivalLevelsDifferent:true}')
if "Hứa Thuý Lan" in index or "Năm 2267." in index or "đội Black Blood" in index:
    raise RuntimeError("obsolete_campaign_canon_survived_new_game")
INDEX.write_text(index, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Core new-game state: story companions are known campaign characters but
# are not co-located with Kai until a fixed story event activates them.
# No old-save migration/backfill is added by design.
# ---------------------------------------------------------------------------
state = GAME_STATE.read_text(encoding="utf-8")
old_initial = '''          metadata = mapOf("inventoryProfile" to "kai")
        )
      ),
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID)),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots))
'''
new_initial = '''          metadata = mapOf("inventoryProfile" to "kai")
        ),
        "lucia" to CharacterState(
          id = "lucia",
          name = "Lucia Lục",
          presence = CharacterPresence.MISSING,
          inventoryId = "lucia",
          equipmentId = "lucia",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf(
            "npcType" to "follower",
            "joinEligible" to "true",
            "storyOwned" to "true",
            "encounterMode" to "FIXED_ENCOUNTER",
            "fixedEncounterLevel" to "0",
            "requiresQuest" to "false",
            "randomSpawn" to "false"
          )
        ),
        "syvial" to CharacterState(
          id = "syvial",
          name = "Syvial",
          presence = CharacterPresence.SEPARATED,
          inventoryId = "syvial",
          equipmentId = "syvial",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf(
            "npcType" to "follower",
            "joinEligible" to "true",
            "storyOwned" to "true",
            "encounterMode" to "FIXED_REUNION",
            "fixedEncounterLevel" to "37",
            "randomSpawn" to "false",
            "canonRef" to "SYVIAL-LUCIFER-CODEX-20260830-R04"
          )
        ),
        "iris" to CharacterState(
          id = "iris",
          name = "Iris",
          presence = CharacterPresence.SEPARATED,
          inventoryId = "iris",
          equipmentId = "iris",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf(
            "npcType" to "follower",
            "joinEligible" to "true",
            "storyOwned" to "true",
            "encounterMode" to "FIXED_REUNION",
            "fixedEncounterLevel" to "94",
            "randomSpawn" to "false",
            "canonRef" to "IRIS-BELIAL-SRU-CODEX-20260830-R06"
          )
        )
      ),
      inventories = mapOf(
        KAI_ID to InventoryState(KAI_ID),
        "lucia" to InventoryState("lucia"),
        "syvial" to InventoryState("syvial"),
        "iris" to InventoryState("iris")
      ),
      equipment = mapOf(
        KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots),
        "lucia" to EquipmentState("lucia"),
        "syvial" to EquipmentState("syvial"),
        "iris" to EquipmentState("iris")
      )
'''
if '"fixedEncounterLevel" to "94"' not in state:
    state = replace_once(state, old_initial, new_initial, "seed new-game story companions")
GAME_STATE.write_text(state, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Core party bridge: a fixed story gate may activate a known SEPARATED or
# MISSING character. Normal followers keep the old ACTIVE/present rules.
# ---------------------------------------------------------------------------
core = GAME_CORE.read_text(encoding="utf-8")
old_party = '''    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
      commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE
      )
    }
'''
new_party = '''    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    val candidateLevel = candidate.optJSONObject("level")?.optInt("number", -1) ?: -1
    (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
      commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      val storyJoin = StoryCompanionContinuity.canMaterialize(id, candidateLevel, id in currentFollowers)
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && (known?.presence == CharacterPresence.ACTIVE || storyJoin)
      )
    }
'''
if "val candidateLevel = candidate.optJSONObject" not in core:
    core = replace_once(core, old_party, new_party, "allow fixed story companion party activation")
GAME_CORE.write_text(core, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
old_add = '''      changed(state.copy(party = state.party.copy(memberIds = state.party.memberIds + command.targetId)), "party_member_added")
'''
new_add = '''      val character = state.characters.getValue(command.targetId)
      changed(
        state.copy(
          party = state.party.copy(memberIds = state.party.memberIds + command.targetId),
          characters = state.characters + (command.targetId to character.copy(presence = CharacterPresence.ACTIVE))
        ),
        "party_member_added"
      )
'''
if "character.copy(presence = CharacterPresence.ACTIVE)" not in engines:
    engines = replace_once(engines, old_add, new_add, "activate character when party add commits")
ENGINES.write_text(engines, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Android authoritative gameplay: replace probabilistic Iris/Syvial rolls
# with deterministic story gates and add Lucia's fixed Level-0 encounter.
# Existing rollSuccess-based authority checks therefore continue to work but
# receive no dice/chance/threshold for these three characters.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
if "import com.rabpit.backroom.core.StoryCompanionContinuity;" not in main:
    main = replace_once(
        main,
        "import com.rabpit.backroom.core.GameCoreFacade;\n",
        "import com.rabpit.backroom.core.GameCoreFacade;\nimport com.rabpit.backroom.core.StoryCompanionContinuity;\n",
        "import story companion continuity",
    )

old_rolls = '''    rolls.put("irisReunion", thresholdRoll("irisReunion", 1000000, 25, reunionEligibleAndroid(state, "iris"), ""));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 1000000, 25, reunionEligibleAndroid(state, "syvial"), ""));
'''
new_rolls = '''    boolean irisStoryGate = StoryCompanionContinuity.canMaterialize("iris", currentLevel(state), partyHas(state, "iris") || flagSpawned(state, "iris"));
    boolean syvialStoryGate = StoryCompanionContinuity.canMaterialize("syvial", currentLevel(state), partyHas(state, "syvial") || flagSpawned(state, "syvial"));
    boolean luciaStoryGate = StoryCompanionContinuity.canMaterialize("lucia", currentLevel(state), partyHas(state, "lucia") || flagSpawned(state, "lucia"));
    rolls.put("irisReunion", new JSONObject().put("label", "irisReunion").put("storyOwned", true).put("fixedLevel", 94).put("eligible", irisStoryGate).put("success", irisStoryGate).put("roll", JSONObject.NULL));
    rolls.put("syvialReunion", new JSONObject().put("label", "syvialReunion").put("storyOwned", true).put("fixedLevel", 37).put("eligible", syvialStoryGate).put("success", syvialStoryGate).put("roll", JSONObject.NULL));
    rolls.put("luciaEncounter", new JSONObject().put("label", "luciaEncounter").put("storyOwned", true).put("fixedLevel", 0).put("requiresQuest", false).put("eligible", luciaStoryGate).put("success", luciaStoryGate).put("roll", JSONObject.NULL));
'''
if 'put("storyOwned", true).put("fixedLevel", 94)' not in main:
    main = replace_once(main, old_rolls, new_rolls, "replace random companion reunion rolls")

story_helpers = r'''  private boolean legacyPartyHasId(JSONObject state, String id) {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return false;
    String needle = lower(id).trim();
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member != null && needle.equals(lower(member.optString("id", "")).trim())) return true;
    }
    return false;
  }

  private void commitFixedCompanion(JSONObject state, JSONObject before, JSONObject rolls, String id, String name, String rollKey, String continuity) throws Exception {
    if (!rollSuccess(rolls, rollKey) || legacyPartyHasId(state, id) || flagSpawned(before, id)) return;
    JSONArray party = state.optJSONArray("party");
    if (party == null) party = new JSONArray();
    boolean joined = party.length() < 3;
    if (joined) {
      party.put(new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("present", true)
        .put("joinConfirmed", true)
        .put("presence", "ACTIVE")
        .put("role", "follower")
        .put("storyOwned", true));
      state.put("party", party);
    }

    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject record = flags.optJSONObject(id);
    if (record == null) record = new JSONObject();
    record.put("exists", true)
      .put("encountered", true)
      .put("present", true)
      .put("spawned", true)
      .put("follower", true)
      .put("storyOwned", true)
      .put("randomSpawn", false)
      .put("continuity", continuity)
      .put("levelEncountered", currentLevel(before))
      .put("joinPending", !joined);
    flags.put(id, record);
    state.put("flags", flags);
  }

  private void applyFixedCompanionContinuity(JSONObject state, JSONObject before, JSONObject rolls) throws Exception {
    commitFixedCompanion(state, before, rolls, "lucia", "Lucia Lục", "luciaEncounter", "FIXED_LEVEL_0_ENCOUNTER");
    commitFixedCompanion(state, before, rolls, "syvial", "Syvial", "syvialReunion", "REUNITED_LEVEL_37");
    commitFixedCompanion(state, before, rolls, "iris", "Iris", "irisReunion", "REUNITED_LEVEL_94");
  }

  private String fixedCompanionNarrationDirective(JSONObject rolls) {
    if (rollSuccess(rolls, "luciaEncounter")) return "STORY EVENT COMMITTED: Kai gặp Lucia Lục tại Level 0. Đây là fixed encounter, không phải quest và không phải random spawn. Chỉ kể cuộc gặp, không bịa lịch sử chưa có dữ kiện.";
    if (rollSuccess(rolls, "syvialReunion")) return "STORY EVENT COMMITTED: Kai gặp lại Syvial tại Level 37. Reunion này do Core story continuity mở khóa; không mô tả như random encounter.";
    if (rollSuccess(rolls, "irisReunion")) return "STORY EVENT COMMITTED: Kai gặp lại Iris tại Level 94. Reunion này do Core story continuity mở khóa; không mô tả như random encounter.";
    return "STORY COMPANION LOCK: Iris và Syvial vẫn SEPARATED; không tự tạo reunion, vị trí, tín hiệu hay sự hiện diện của họ. Lucia Lục chỉ được materialize bởi fixed Level-0 encounter.";
  }

'''
helper_anchor = "  private boolean reunionEligibleAndroid(JSONObject state, String key) {\n"
if "private void applyFixedCompanionContinuity(" not in main:
    main = replace_once(main, helper_anchor, story_helpers + helper_anchor, "insert fixed companion runtime helpers")

# The model receives only a current-turn story event directive. It never sees future reunion levels.
main = main.replace(
    '"\\n\\nGAMEPLAY_ROLLS:\\n" + rolls.toString() +',
    '"\\n\\nGAMEPLAY_ROLLS:\\n" + rolls.toString() + "\\n\\n" + fixedCompanionNarrationDirective(rolls) +',
    1,
)

# Apply the fixed event after model ops but before the candidate is committed/synchronized.
apply_pattern = r'(JSONObject state = meta\s*\? new JSONObject\(before\.toString\(\)\)\s*:\s*applyModelOperations\(before, generated\.optJSONArray\("ops"\), rolls, action\);)'
if "applyFixedCompanionContinuity(state, before, rolls);" not in main:
    main = replace_regex_once(
        main,
        apply_pattern,
        lambda match: match.group(1) + '\n          if (!meta) applyFixedCompanionContinuity(state, before, rolls);',
        "commit fixed companion event before core synchronization",
        re.S,
    )

# Story prompt: 2299 SRU only; remove the retired private-target projection.
main = main.replace('entry.optInt("year", 2267)', 'entry.optInt("year", 2299)')
main = main.replace('mission.optString("unit", "Black Blood")', 'mission.optString("unit", "SRU")')
main = replace_regex_once(
    main,
    r'\n      JSONObject privateVisible = new JSONObject\(\).*?\.put\("currentLocationKnown", false\);\n',
    '\n',
    "remove retired private objective narrator projection",
    re.S,
)
main = main.replace(
    '        .put("officialMission", missionVisible)\n        .put("kaiPrivateObjective", privateVisible);',
    '        .put("officialMission", missionVisible);',
)
main = replace_regex_once(
    main,
    r'return "MAIN STORY HARD LOCK: năm 2267 Kai, Iris và Syvial CHỦ ĐỘNG.*?CURRENT_STORY_BEAT=" \+ visible\.toString\(\);',
    'return "MAIN STORY HARD LOCK: năm 2299 Kai, Iris và Syvial thuộc SRU chủ động đi qua cùng một cổng không gian để điều tra Async rồi bị phân tán tới các Level khác nhau. "\n        + "Kai không biết vị trí Iris hoặc Syvial cho tới khi Core story continuity xác nhận reunion. "\n        + "Mission brief và story beat KHÔNG phải discovery evidence: không tự tạo dấu vết Async, hồ sơ Async, giọng nói, vật chứng hay vị trí đồng đội. "\n        + "Chỉ bằng chứng đã được Core/Discovery surfacing mới được dùng để xác nhận. Không tự teleport reunion, không tự khôi phục liên lạc, "\n        + "không tiết lộ transition hoặc reunion level tương lai, không sửa Core/RNG/campaign route, không xác nhận nguồn gốc hay ý thức của Backrooms. CURRENT_STORY_BEAT=" + visible.toString();',
    "replace narrator campaign hard lock",
    re.S,
)
main = replace_regex_once(
    main,
    r'return "MAIN STORY HARD LOCK: năm 2267 Kai, Iris và Syvial chủ động.*?;\n    \}',
    'return "MAIN STORY HARD LOCK: năm 2299 đội SRU của Kai, Iris và Syvial chủ động đi qua cùng một cổng điều tra Async rồi bị phân tán; chỉ Core story continuity được quyền xác nhận reunion.";\n    }',
    "replace narrator fallback hard lock",
    re.S,
)

if "Hứa Thuý Lan" in main or "năm 2267" in main or 'unit", "Black Blood"' in main:
    raise RuntimeError("obsolete_campaign_canon_survived_main_activity")
if 'thresholdRoll("irisReunion"' in main or 'thresholdRoll("syvialReunion"' in main:
    raise RuntimeError("random_reunion_roll_survived")
for marker in (
    'put("storyOwned", true).put("fixedLevel", 94)',
    'put("storyOwned", true).put("fixedLevel", 37)',
    'put("storyOwned", true).put("fixedLevel", 0).put("requiresQuest", false)',
    'applyFixedCompanionContinuity(state, before, rolls);',
    'fixedCompanionNarrationDirective(rolls)',
):
    if marker not in main:
        raise RuntimeError("fixed_companion_runtime_marker_missing:" + marker)
MAIN.write_text(main, encoding="utf-8")

print("Applied story-owned companion continuity: 2299 SRU mission, Lucia fixed at Level 0, Syvial at 37, Iris at 94, no random companion spawn.")
