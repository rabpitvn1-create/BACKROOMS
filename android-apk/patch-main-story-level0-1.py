from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
QUESTS = ROOT / "app/src/main/assets/campaign_story/level0-to-level1-quests.json"
CATALOG = ROOT / "app/src/main/assets/level_catalog/backrooms-0-6.json"
CAMPAIGN_ID = "BACKROOMS_FANDOM_LEVELS_0_6_R01"
STORY_ID = "MAIN_LEVEL0_TO_LEVEL1_R01"
QUEST_PLAN_ID = "QUEST_PLAN_LEVEL0_TO_LEVEL1_R01"
MISSION_YEAR = 2299


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


def validate_story() -> list[str]:
    story = json.loads(STORY.read_text(encoding="utf-8"))
    quests = json.loads(QUESTS.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))

    if story.get("storyId") != STORY_ID:
        raise RuntimeError("main_story_level0_1_story_id_mismatch")
    if story.get("campaignId") != CAMPAIGN_ID or catalog.get("campaignId") != CAMPAIGN_ID:
        raise RuntimeError("main_story_level0_1_campaign_mismatch")
    if story.get("questPlanRef") != "campaign_story/level0-to-level1-quests.json":
        raise RuntimeError("main_story_quest_plan_ref_missing")
    if quests.get("planId") != QUEST_PLAN_ID or quests.get("storyId") != STORY_ID or quests.get("campaignId") != CAMPAIGN_ID:
        raise RuntimeError("main_story_quest_plan_identity_mismatch")

    entry = story.get("entryEvent") or {}
    if entry.get("year") != MISSION_YEAR:
        raise RuntimeError("main_story_entry_year_mismatch")
    if entry.get("mode") != "SPATIAL_GATE" or entry.get("sameGate") is not True:
        raise RuntimeError("main_story_entry_must_use_one_spatial_gate")
    if entry.get("entryIntent") != "MISSION" or entry.get("voluntaryMissionEntry") is not True:
        raise RuntimeError("main_story_entry_must_be_intentional_mission")
    if entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLevelsDifferent") is not True:
        raise RuntimeError("main_story_entry_must_separate_to_different_levels")
    if entry.get("arrivalLocationsMutuallyUnknown") is not True:
        raise RuntimeError("main_story_arrival_locations_must_be_unknown")
    if entry.get("participants") != ["kai", "iris", "syvial"]:
        raise RuntimeError("main_story_entry_participants_mismatch")

    mission = story.get("officialMission") or {}
    if mission.get("year") != MISSION_YEAR or mission.get("unit") != "SRU" or mission.get("subject") != "Async":
        raise RuntimeError("main_story_sru_async_mission_mismatch")
    if mission.get("entryMethod") != "SPATIAL_GATE" or mission.get("backroomsOriginKnown") is not False:
        raise RuntimeError("main_story_mission_epistemic_boundary_invalid")
    if "Async" not in str(mission.get("objective") or ""):
        raise RuntimeError("main_story_async_mission_objective_missing")

    hidden = (story.get("hiddenStoryFacts") or {}).get("asyncCrossEraNetwork") or {}
    if hidden.get("status") != "BACKEND_ONLY" or hidden.get("knownToKaiAtStart") is not False:
        raise RuntimeError("main_story_async_hidden_fact_boundary_invalid")
    required_operatives = {
        "Jane Doe", "Monster X", "Violet Warden", "Jeff the Killer",
        "Jane the Killer", "Slenderman", "SCP-173",
    }
    if set(hidden.get("operatives") or []) != required_operatives:
        raise RuntimeError("main_story_async_operatives_mismatch")

    locks = story.get("globalLocks") or {}
    for key in (
        "neverConfirmBackroomsConscious",
        "neverConfirmBackroomsOrigin",
        "storyDoesNotOwnRng",
        "storyDoesNotOverrideCoreOutcome",
        "storyDoesNotChooseCampaignTransition",
        "missionBriefIsNotDiscoveryEvidence",
        "neverInventAsyncTrace",
        "companionContinuityStoryOwned",
        "randomCompanionSpawnDisabled",
        "coreOwnsQuestProgression",
        "geminiCannotAdvanceQuest",
        "liteRTCannotAdvanceQuest",
    ):
        if locks.get(key) is not True:
            raise RuntimeError("main_story_required_lock_missing:" + key)

    story_text = json.dumps(story, ensure_ascii=False)
    for obsolete in ("Hứa Thuý Lan", "2267", "Black Blood"):
        if obsolete in story_text:
            raise RuntimeError("obsolete_campaign_canon_survived_story_asset:" + obsolete)

    ordered = sorted(
        [raw for raw in catalog.get("entries", []) if raw.get("campaignOrder") is not None],
        key=lambda raw: int(raw["campaignOrder"]),
    )
    route_ids = [str(raw.get("id")) for raw in ordered]
    try:
        start = route_ids.index("0")
        end = route_ids.index("1", start)
    except ValueError as error:
        raise RuntimeError("main_story_route_endpoints_missing") from error
    expected = route_ids[start : end + 1]
    beats = story.get("beats", [])
    beat_ids = [str(raw.get("areaId")) for raw in beats]
    if beat_ids != expected:
        raise RuntimeError("main_story_route_mismatch:" + ",".join(beat_ids) + " != " + ",".join(expected))
    if len(set(beat_ids)) != len(beat_ids):
        raise RuntimeError("main_story_duplicate_area_beat")
    for beat in beats:
        for key in ("areaId", "title", "phase", "storyPurpose", "visibleObjective", "characterThread"):
            if not str(beat.get(key) or "").strip():
                raise RuntimeError(f"main_story_beat_missing_{key}:{beat.get('areaId')}")
    return beat_ids


beat_ids = validate_story()

# New Game opening is source-clean: the checked-in seed already carries the SRU / Async mission premise.
index = INDEX.read_text(encoding="utf-8")
prologue_start = index.find("const prologue=`")
initial_start = index.find("const initial={", prologue_start)
if prologue_start < 0 or initial_start < 0:
    raise RuntimeError("main_story_source_clean_prologue_anchor_missing")
portal_scene = r'''Năm 2299.

Cổng không gian trước mặt đội SRU đã ổn định đủ lâu để bắt đầu nhiệm vụ. Lệnh điều tra chỉ rõ mục tiêu: tiến vào, xác minh hoạt động của Async và đánh giá nguy cơ của Backrooms đối với Frontrooms.

Kai kiểm tra lần cuối trang bị. Iris và Syvial đã sẵn sàng ở hai bên. Không ai bị kéo vào ngoài ý muốn. Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ.

Kai vẫn nhìn thấy Iris và Syvial khi vượt qua ranh giới. Rồi khoảng cách giữa ba người mất ý nghĩa. Backrooms phân tán họ tới những Level khác nhau; Kai không biết hai người còn lại đã bị đưa tới đâu.

Cảm giác chuyển tiếp kéo dài chưa tới một nhịp tim. Trọng lực trở lại đột ngột.

Kai bắt đầu một mình tại Level 0. Nhiệm vụ điều tra Async vẫn còn hiệu lực, nhưng mission brief không tự biến bất kỳ dấu vết nào trong Backrooms thành bằng chứng.'''
index = index[:prologue_start] + "const prologue=`" + portal_scene + "`;\n\n" + index[initial_start:]
clean_location = 'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng nhiệm vụ",'
index, location_count = re.subn(
    r'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau [^"]+",',
    clean_location,
    index,
    count=1,
)
if location_count != 1 and clean_location not in index:
    raise RuntimeError("main_story_source_clean_location_anchor_missing")

# Later UI patches add fields inside flags, so mutate only the communication prefix.
initial_start = index.find("const initial={")
initial_end = index.find("log:[", initial_start)
if initial_start < 0 or initial_end < 0:
    raise RuntimeError("main_story_initial_state_anchor_missing")
initial_slice = index[initial_start:initial_end]
match = re.search(r'flags:\{communication:\{[^}]*\}', initial_slice)
if not match:
    raise RuntimeError("main_story_initial_communication_missing")
comm = match.group(0)
if 'sruForce:"OFFLINE"' not in comm:
    comm = comm[:-1] + ',sruForce:"OFFLINE"}'
if 'frontrooms:"OFFLINE"' not in comm:
    comm = comm[:-1] + ',frontrooms:"OFFLINE"}'
insertion = (
    ',entryEvent:{year:2299,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true,arrivalLevelsDifferent:true}'
    ',iris:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}'
    ',syvial:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}'
)
replacement = comm + insertion
absolute_start = initial_start + match.start()
absolute_end = initial_start + match.end()
index = index[:absolute_start] + replacement + index[absolute_end:]
INDEX.write_text(index, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")

# Runtime projection deliberately omits hiddenStoryFacts, transitionStory and hidden escape data.
# The current Core-owned quest projection, when present, replaces the beat's generic objective text.
story_helpers = r'''  private JSONObject loadLevel01Story() throws Exception {
    StringBuilder content = new StringBuilder();
    try (InputStream stream = getAssets().open("campaign_story/level0-to-level1.json");
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) content.append(line).append('\n');
    }
    return new JSONObject(content.toString());
  }

  private String currentStoryAreaId(JSONObject state) {
    JSONObject flags = state != null ? state.optJSONObject("flags") : null;
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    String areaId = exploration != null ? exploration.optString("areaId", "").trim() : "";
    if (!areaId.isEmpty()) return areaId;
    return String.valueOf(currentLevel(state == null ? new JSONObject() : state));
  }

  private String campaignStoryBeatPrompt(JSONObject state) {
    try {
      JSONObject root = loadLevel01Story();
      String areaId = currentStoryAreaId(state);
      JSONArray beats = root.optJSONArray("beats");
      JSONObject beat = null;
      if (beats != null) {
        for (int i = 0; i < beats.length(); i++) {
          JSONObject candidate = beats.optJSONObject(i);
          if (candidate != null && areaId.equals(candidate.optString("areaId", ""))) { beat = candidate; break; }
        }
      }
      if (beat == null) return "MAIN STORY HARD LOCK: giữ nhiệm vụ SRU điều tra Async và continuity hiện tại; không tự bịa cốt truyện, vị trí hay reunion của đồng đội.";

      JSONObject entry = root.optJSONObject("entryEvent");
      JSONObject mission = root.optJSONObject("officialMission");
      if (entry == null) entry = new JSONObject();
      if (mission == null) mission = new JSONObject();

      JSONObject missionVisible = new JSONObject()
        .put("year", mission.optInt("year", entry.optInt("year", 2299)))
        .put("unit", mission.optString("unit", "SRU"))
        .put("subject", mission.optString("subject", "Async"))
        .put("objective", mission.optString("objective", ""))
        .put("entryMethod", mission.optString("entryMethod", "SPATIAL_GATE"))
        .put("backroomsOriginKnown", false);

      JSONObject questVisible = state != null ? state.optJSONObject("storyQuest") : null;
      String coreObjective = questVisible != null ? questVisible.optString("objectiveTitle", "").trim() : "";
      String visibleObjective = coreObjective.isEmpty() ? beat.optString("visibleObjective", "") : coreObjective;

      JSONObject visible = new JSONObject()
        .put("storyId", root.optString("storyId", "MAIN_LEVEL0_TO_LEVEL1_R01"))
        .put("areaId", areaId)
        .put("phase", beat.optString("phase", ""))
        .put("storyPurpose", beat.optString("storyPurpose", ""))
        .put("visibleObjective", visibleObjective)
        .put("discoveryThemes", beat.optJSONArray("discoveryThemes") == null ? new JSONArray() : beat.optJSONArray("discoveryThemes"))
        .put("characterThread", beat.optString("characterThread", ""))
        .put("officialMission", missionVisible);
      if (questVisible != null) visible.put("quest", questVisible);

      return "MAIN STORY HARD LOCK: năm 2299 Kai, Iris và Syvial thuộc SRU chủ động đi qua cùng một cổng không gian để điều tra Async rồi bị phân tán tới các Level khác nhau. "
        + "Kai không biết vị trí Iris hoặc Syvial cho tới khi story continuity xác nhận reunion. "
        + "Mission brief, story beat và quest text KHÔNG phải discovery evidence: không tự tạo dấu vết Async, hồ sơ Async, giọng nói, vật chứng hay vị trí đồng đội. "
        + "Chỉ bằng chứng đã được Core/Discovery surfacing mới được dùng để xác nhận. Gemini không được advance quest, không tự teleport reunion, không tự khôi phục liên lạc, "
        + "không tiết lộ reunion level tương lai, transition hoặc hidden escape data, không sửa Core/RNG/campaign route. CURRENT_STORY_BEAT=" + visible.toString();
    } catch (Exception ignored) {
      return "MAIN STORY HARD LOCK: năm 2299 đội SRU của Kai, Iris và Syvial đi qua cùng một cổng để điều tra Async rồi bị phân tán tới các Level khác nhau; "
        + "giữ vị trí Iris và Syvial chưa xác định cho tới khi Core story continuity xác nhận.";
    }
  }

'''
helper_anchor = '  private int levelTurns(JSONObject state) {\n'
if "private String campaignStoryBeatPrompt(" not in main:
    main = replace_once(main, helper_anchor, story_helpers + helper_anchor, "campaign story runtime helpers")
main = replace_once(
    main,
    '      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối campaign route đã khai báo; không tự tạo khu kế tiếp.";\n',
    '      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối campaign route đã khai báo; không tự tạo khu kế tiếp.\\n" + campaignStoryBeatPrompt(state);\n',
    "terminal story prompt",
)
main = replace_once(
    main,
    '    return "TRANSITION GRAPH HARD LOCK: khu hiện tại = " + currentLabel + ". Target authoritative đã khai báo là " + nextLabel + ". Model không được tự chọn target ngoài graph.";\n',
    '    return "TRANSITION GRAPH HARD LOCK: khu hiện tại = " + currentLabel + ". Target authoritative đã khai báo là " + nextLabel + ". Model không được tự chọn target ngoài graph.\\n" + campaignStoryBeatPrompt(state);\n',
    "active story prompt",
)
main = replace_once(
    main,
    '        + "VISIBLE_RESOLVED_OUTCOME=" + visible.toString();\n',
    '        + "MAIN_STORY_CONTEXT=" + campaignStoryBeatPrompt(state) + "\\n"\n        + "VISIBLE_RESOLVED_OUTCOME=" + visible.toString();\n',
    "registered narration story context",
)
for marker in (
    'getAssets().open("campaign_story/level0-to-level1.json")',
    'MAIN STORY HARD LOCK:',
    'CURRENT_STORY_BEAT=',
    'MAIN_STORY_CONTEXT=',
    'Mission brief, story beat và quest text KHÔNG phải discovery evidence',
    'Gemini không được advance quest',
):
    if marker not in main:
        raise RuntimeError("main_story_runtime_marker_missing:" + marker)
for obsolete in ("Hứa Thuý Lan", "năm 2267", 'unit", "Black Blood"'):
    if obsolete in main:
        raise RuntimeError("obsolete_campaign_canon_survived_main_activity:" + obsolete)
if "hiddenStoryFacts" in story_helpers:
    raise RuntimeError("main_story_hidden_facts_must_not_enter_narrator_projection")
MAIN.write_text(main, encoding="utf-8")

final_index = INDEX.read_text(encoding="utf-8")
if "Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ." not in final_index:
    raise RuntimeError("intentional_spatial_gate_prologue_not_applied")
if "Năm 2299." not in final_index or "xác minh hoạt động của Async" not in final_index or "đội SRU" not in final_index:
    raise RuntimeError("mission_prologue_required_canon_missing")
for obsolete in ("Hứa Thuý Lan", "Năm 2267.", "đội Black Blood", "sau no-clip"):
    if obsolete in final_index:
        raise RuntimeError("obsolete_new_game_canon_survived:" + obsolete)
for marker in (
    'entryEvent:{year:2299,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true,arrivalLevelsDifferent:true}',
    'iris:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}',
    'syvial:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}',
):
    if marker not in final_index:
        raise RuntimeError("fresh_separation_state_missing:" + marker)

print(
    f"Integrated {STORY_ID}: 2299 SRU Async mission, three-way Level separation, "
    f"Core-owned quest projection, and {len(beat_ids)} story beats through Level 1."
)
