from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
COMPANIONS = ROOT / "app/src/main/assets/campaign_story/companion-continuity.json"
SPECIAL = CORE / "SpecialFollowersCanon.kt"
LUCIA = CORE / "LuciaCanon.kt"
GAME_CORE = CORE / "GameCoreFacade.kt"
ENGINES = CORE / "Engines.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_regex_once(text: str, pattern: str, replacement, label: str, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex anchor, found {count}")
    return updated


def rewrite_character_function(text: str, function_name: str, next_marker: str, presence: str, fixed_level: int) -> str:
    start = text.find(f"  fun {function_name}(existing: CharacterState? = null): CharacterState {{")
    end = text.find(next_marker, start)
    if start < 0 or end < 0:
        raise RuntimeError(f"{function_name}: function block missing")
    block = text[start:end]
    if f"fixedEncounterLevel\" to \"{fixed_level}" not in block:
        block = replace_once(
            block,
            "    return base.copy(\n",
            f"    return base.copy(\n      presence = existing?.presence ?: CharacterPresence.{presence},\n",
            f"{function_name} initial presence",
        )
        block = replace_once(
            block,
            '        "encounterChance" to ENCOUNTER_CHANCE,\n',
            '        "encounterChance" to "0%",\n'
            '        "randomSpawn" to "false",\n'
            '        "storyOwned" to "true",\n'
            f'        "fixedEncounterLevel" to "{fixed_level}",\n',
            f"{function_name} fixed story metadata",
        )
        block = block.replace('        "encounterLevels" to ENCOUNTER_LEVELS,\n', '        "encounterLevels" to "STORY_ONLY",\n', 1)
    return text[:start] + block + text[end:]


# ---------------------------------------------------------------------------
# Canon contract. These are story gates, never encounter probabilities.
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
if entry.get("participants") != ["kai", "iris", "syvial"]:
    raise RuntimeError("story_companion_entry_participants_invalid")
if entry.get("sameGate") is not True or entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLevelsDifferent") is not True:
    raise RuntimeError("story_companion_entry_separation_invalid")
for character_id, (level, event_type) in expected.items():
    raw = (continuity.get("companions") or {}).get(character_id) or {}
    if raw.get("level") != level or raw.get("eventType") != event_type:
        raise RuntimeError("story_companion_gate_invalid:" + character_id)
    if raw.get("randomSpawn") is not False or raw.get("storyOwned") is not True:
        raise RuntimeError("story_companion_random_spawn_must_be_disabled:" + character_id)
if (continuity.get("companions") or {}).get("lucia", {}).get("requiresQuest") is not False:
    raise RuntimeError("lucia_fixed_encounter_must_not_require_quest")


# ---------------------------------------------------------------------------
# Rewrite the Level-0 -> Level-1 story asset emitted by the older story patch.
# The older patch is left intact only because this repository composes runtime
# finalizers; this file is the final authority after that patch.
# ---------------------------------------------------------------------------
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

for beat in story.get("beats", []):
    area = str(beat.get("areaId", ""))
    for key in ("storyPurpose", "visibleObjective", "characterThread", "transitionStory"):
        value = beat.get(key)
        if isinstance(value, str):
            value = value.replace("năm 2267", "năm 2299")
            value = value.replace("Iris, Syvial hoặc Hứa Thuý Lan", "Iris hoặc Syvial")
            value = value.replace("Iris, Syvial và Hứa Thuý Lan", "Iris và Syvial")
            value = value.replace("tìm đồng đội và tìm Hứa Thuý Lan", "tìm đồng đội")
            value = value.replace("tìm đồng đội và tìm bằng chứng thật về Hứa Thuý Lan", "tìm đồng đội")
            value = value.replace("Reunion và tung tích Hứa Thuý Lan vẫn là các tuyến dài hạn", "Reunion với Iris và Syvial vẫn là tuyến dài hạn")
            value = value.replace("cả nhiệm vụ chính thức và mục tiêu cá nhân", "nhiệm vụ SRU và mục tiêu tìm lại đồng đội")
            value = value.replace("dấu của Hứa Thuý Lan", "dấu của đồng đội")
            beat[key] = value
    if area == "0":
        beat["characterThread"] = "Kai biết cả ba thành viên SRU chủ động bước qua cùng một cổng năm 2299 để điều tra Async rồi bị phân tán tới các Level khác nhau. Hắn không biết Iris hoặc Syvial đang ở đâu. Lucia Lục là cuộc gặp cố định của Level 0, không phải quest."
    elif area == "epsilon":
        beat["characterThread"] = "Không được cho Kai nghe thấy Iris hoặc Syvial chỉ vì cốt truyện cần họ; giọng quen thuộc có thể là hiện tượng và phải giữ trạng thái chưa xác nhận."
    elif area == "0.01":
        beat["characterThread"] = "Mục tiêu điều tra Async và tìm lại đồng đội cùng tồn tại, nhưng không mục tiêu nào cho phép Kai coi một dấu chưa kiểm chứng là sự thật."
    elif area == "0.1":
        beat["characterThread"] = "Kai có thể lo cho Iris và Syvial nhưng vẫn hành động có kỷ luật; không biến nỗi lo thành mất phán đoán."
    elif area == "0.22":
        beat["characterThread"] = "Nếu xuất hiện dấu vết con người hoặc Async, phải phân biệt dấu mới/cũ và tuyệt đối không gán cho Iris hoặc Syvial khi chưa có căn cứ."
    elif area == "0.99":
        beat["characterThread"] = "Một tín hiệu giống thiết bị SRU, Async hoặc dấu của đồng đội chỉ được coi là manh mối nếu hệ thống thật sự sinh ra nó; story không tự khẳng định nguồn."
    elif area == "Dullness":
        beat["characterThread"] = "Ký ức về Iris hoặc Syvial có thể bị môi trường gợi lại, nhưng không được biến thành thông tin vị trí thật."
    elif area == "Red Rooms":
        beat["characterThread"] = "Kai rời hệ vùng Level 0 mà chưa bắt buộc phải tìm thấy Iris hoặc Syvial; reunion do continuity cốt truyện ở các Level đã khóa quyết định."
    elif area == "1":
        beat["visibleObjective"] = "Thiết lập vị trí trong mạng gara, tìm nguồn tài nguyên đáng tin, đánh giá blackout, tiếp tục điều tra Async và tìm dấu vết đồng đội mà không giả định kết quả."
        beat["characterThread"] = "Reunion với Iris và Syvial là tuyến dài hạn do story continuity quyết định; sang Level 1 không tự động mở khóa câu trả lời."

story_text = json.dumps(story, ensure_ascii=False, indent=2) + "\n"
if "Hứa Thuý Lan" in story_text or "2267" in story_text or "Black Blood" in story_text:
    raise RuntimeError("obsolete_campaign_canon_survived_story_asset")
STORY.write_text(story_text, encoding="utf-8")


# ---------------------------------------------------------------------------
# New Game opening: SRU, 2299, Async, different arrival Levels.
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
index = replace_once(
    index,
    'entryEvent:{year:2267,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true}',
    'entryEvent:{year:2299,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true,arrivalLevelsDifferent:true}',
    "new-game entry event year and separation",
)
if "Hứa Thuý Lan" in index or "Năm 2267." in index or "đội Black Blood" in index:
    raise RuntimeError("obsolete_campaign_canon_survived_new_game")
INDEX.write_text(index, encoding="utf-8")


# ---------------------------------------------------------------------------
# Existing generated character definitions are retained for equipment/stats,
# but their encounter authority becomes story-owned. Presence is initialized
# only for New Game and preserved after a committed reunion.
# ---------------------------------------------------------------------------
special = SPECIAL.read_text(encoding="utf-8")
special = special.replace('  const val ENCOUNTER_CHANCE = "0.25%"', '  const val ENCOUNTER_CHANCE = "0%"', 1)
special = special.replace('  const val ENCOUNTER_LEVELS = "0-6"', '  const val ENCOUNTER_LEVELS = "STORY_ONLY"', 1)
special = rewrite_character_function(special, "irisCharacter", "\n  fun syvialCharacter", "SEPARATED", 94)
special = rewrite_character_function(special, "syvialCharacter", "\n  fun ensure", "SEPARATED", 37)
SPECIAL.write_text(special, encoding="utf-8")

lucia = LUCIA.read_text(encoding="utf-8")
lucia = lucia.replace('  const val ENCOUNTER_CHANCE = "50%"', '  const val ENCOUNTER_CHANCE = "0%"', 1)
if '"fixedEncounterLevel" to "0"' not in lucia:
    lucia = replace_once(
        lucia,
        "    return base.copy(\n",
        "    return base.copy(\n      presence = existing?.presence ?: CharacterPresence.MISSING,\n",
        "Lucia initial story presence",
    )
    lucia = replace_once(
        lucia,
        '        "encounterChance" to ENCOUNTER_CHANCE,\n',
        '        "encounterChance" to "0%",\n'
        '        "randomSpawn" to "false",\n'
        '        "storyOwned" to "true",\n'
        '        "fixedEncounterLevel" to "0",\n'
        '        "requiresQuest" to "false",\n',
        "Lucia fixed encounter metadata",
    )
    lucia = lucia.replace('        "encounterAction" to "EXPLORE",\n', '        "encounterAction" to "STORY",\n', 1)
LUCIA.write_text(lucia, encoding="utf-8")


# ---------------------------------------------------------------------------
# Core party commit can activate a story-owned SEPARATED/MISSING character at
# the exact fixed Level. PartyEngine makes presence ACTIVE atomically.
# ---------------------------------------------------------------------------
core = GAME_CORE.read_text(encoding="utf-8")
if "val candidateLevel = candidate.optJSONObject" not in core:
    core = replace_once(
        core,
        '    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()\n',
        '    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()\n'
        '    val candidateLevel = candidate.optJSONObject("level")?.optInt("number", -1) ?: -1\n',
        "story companion candidate level",
    )
    core = replace_once(
        core,
        '      val known = pending.state.characters[id]\n      commands += PartyCommand(\n',
        '      val known = pending.state.characters[id]\n'
        '      val storyJoin = StoryCompanionContinuity.canMaterialize(id, candidateLevel, id in currentFollowers)\n'
        '      commands += PartyCommand(\n',
        "story companion materialization gate",
    )
    core = replace_once(
        core,
        '        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE\n',
        '        targetPresent = member.optBoolean("present", false) && (known?.presence == CharacterPresence.ACTIVE || storyJoin)\n',
        "story companion target presence",
    )
GAME_CORE.write_text(core, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
if "character.copy(presence = CharacterPresence.ACTIVE)" not in engines:
    engines = replace_once(
        engines,
        '      changed(state.copy(party = state.party.copy(memberIds = state.party.memberIds + command.targetId)), "party_member_added")\n',
        '      val character = state.characters.getValue(command.targetId)\n'
        '      changed(\n'
        '        state.copy(\n'
        '          party = state.party.copy(memberIds = state.party.memberIds + command.targetId),\n'
        '          characters = state.characters + (command.targetId to character.copy(presence = CharacterPresence.ACTIVE))\n'
        '        ),\n'
        '        "party_member_added"\n'
        '      )\n',
        "activate companion on committed party add",
    )
ENGINES.write_text(engines, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android runtime: remove every companion probability roll. Existing validated
# follower commit code still consumes these labels, now as deterministic story
# gates. Future fixed Levels are never included in the Gemini-visible roll JSON.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
if "import com.rabpit.backroom.core.StoryCompanionContinuity;" not in main:
    main = replace_once(
        main,
        "import com.rabpit.backroom.core.GameCoreFacade;\n",
        "import com.rabpit.backroom.core.GameCoreFacade;\nimport com.rabpit.backroom.core.StoryCompanionContinuity;\n",
        "import story companion continuity",
    )

old_rolls = '''    rolls.put("irisReunion", thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris"), " follower encounter"));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));
    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia"), " Level 0 Lucia follower encounter"));
'''
new_rolls = '''    boolean irisStoryGate = StoryCompanionContinuity.canMaterialize("iris", level, partyHas(state, "iris") || flagSpawned(state, "iris"));
    boolean syvialStoryGate = StoryCompanionContinuity.canMaterialize("syvial", level, partyHas(state, "syvial") || flagSpawned(state, "syvial"));
    boolean luciaStoryGate = StoryCompanionContinuity.canMaterialize("lucia", level, partyHas(state, "lucia") || flagSpawned(state, "lucia"));
    rolls.put("irisReunion", new JSONObject().put("label", "irisReunion").put("storyOwned", true).put("eligible", irisStoryGate).put("success", irisStoryGate).put("roll", JSONObject.NULL));
    rolls.put("syvialReunion", new JSONObject().put("label", "syvialReunion").put("storyOwned", true).put("eligible", syvialStoryGate).put("success", syvialStoryGate).put("roll", JSONObject.NULL));
    rolls.put("luciaEncounter", new JSONObject().put("label", "luciaEncounter").put("storyOwned", true).put("requiresQuest", false).put("eligible", luciaStoryGate).put("success", luciaStoryGate).put("roll", JSONObject.NULL));
'''
if 'put("storyOwned", true).put("requiresQuest", false)' not in main:
    main = replace_once(main, old_rolls, new_rolls, "replace random companion rolls with story gates")

# Retire the private missing-person projection and align mission wording.
main = main.replace('entry.optInt("year", 2267)', 'entry.optInt("year", 2299)')
main = main.replace('mission.optString("unit", "Black Blood")', 'mission.optString("unit", "SRU")')
main = replace_regex_once(
    main,
    r'\n      JSONObject privateObjective = root\.optJSONObject\("kaiPrivateObjective"\);\n.*?if \(privateObjective == null\) privateObjective = new JSONObject\(\);\n',
    '\n',
    "remove private objective source",
    re.S,
)
main = replace_regex_once(
    main,
    r'\n      JSONObject privateVisible = new JSONObject\(\).*?\.put\("currentLocationKnown", false\);\n',
    '\n',
    "remove private objective projection",
    re.S,
)
main = main.replace(
    '        .put("officialMission", missionVisible)\n        .put("kaiPrivateObjective", privateVisible);',
    '        .put("officialMission", missionVisible);',
    1,
)
main = replace_regex_once(
    main,
    r'      return "MAIN STORY HARD LOCK: năm 2267 Kai, Iris và Syvial CHỦ ĐỘNG.*?CURRENT_STORY_BEAT=" \+ visible\.toString\(\);',
    '      return "MAIN STORY HARD LOCK: năm 2299 Kai, Iris và Syvial thuộc SRU chủ động đi qua cùng một cổng không gian để điều tra Async rồi bị phân tán tới các Level khác nhau. "\n'
    '        + "Kai không biết vị trí Iris hoặc Syvial cho tới khi story continuity xác nhận reunion. "\n'
    '        + "Mission brief và story beat KHÔNG phải discovery evidence: không tự tạo dấu vết Async, hồ sơ Async, giọng nói, vật chứng hay vị trí đồng đội. "\n'
    '        + "Chỉ bằng chứng đã được Core/Discovery surfacing mới được dùng để xác nhận. Không tự teleport reunion, không tự khôi phục liên lạc, "\n'
    '        + "không tiết lộ reunion level tương lai, transition hoặc hidden escape data, không sửa Core/RNG/campaign route. CURRENT_STORY_BEAT=" + visible.toString();',
    "replace story narrator hard lock",
    re.S,
)
main = main.replace(
    '      if (beat == null) return "MAIN STORY HARD LOCK: không có story beat cho khu hiện tại; giữ continuity, không tự bịa cốt truyện mới.";',
    '      if (beat == null) return "MAIN STORY HARD LOCK: giữ nhiệm vụ SRU điều tra Async và continuity hiện tại; không tự bịa cốt truyện, vị trí hay reunion của đồng đội.";',
    1,
)

if "Hứa Thuý Lan" in main or "năm 2267" in main or 'unit", "Black Blood"' in main:
    raise RuntimeError("obsolete_campaign_canon_survived_main_activity")
for forbidden in (
    'thresholdRoll("irisReunion"',
    'thresholdRoll("syvialReunion"',
    'thresholdRoll("luciaEncounter"',
):
    if forbidden in main:
        raise RuntimeError("random_companion_roll_survived:" + forbidden)
for marker in (
    'StoryCompanionContinuity.canMaterialize("iris", level',
    'StoryCompanionContinuity.canMaterialize("syvial", level',
    'StoryCompanionContinuity.canMaterialize("lucia", level',
):
    if marker not in main:
        raise RuntimeError("story_companion_gate_missing:" + marker)
MAIN.write_text(main, encoding="utf-8")

print("Applied story-owned companion continuity: 2299 SRU/Async mission, Lucia fixed at Level 0, Syvial reunion at 37, Iris reunion at 94, no companion spawn rolls.")
