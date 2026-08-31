from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
CATALOG = ROOT / "app/src/main/assets/level_catalog/backrooms-0-6.json"
CAMPAIGN_ID = "BACKROOMS_FANDOM_LEVELS_0_6_R01"
STORY_ID = "MAIN_LEVEL0_TO_LEVEL1_R01"
MISSION_YEAR = 2267
PRIVATE_TARGET = "Hứa Thuý Lan"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


def validate_story() -> list[str]:
    story = json.loads(STORY.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    if story.get("storyId") != STORY_ID:
        raise RuntimeError("main_story_level0_1_story_id_mismatch")
    if story.get("campaignId") != CAMPAIGN_ID or catalog.get("campaignId") != CAMPAIGN_ID:
        raise RuntimeError("main_story_level0_1_campaign_mismatch")

    entry = story.get("entryEvent") or {}
    if entry.get("year") != MISSION_YEAR:
        raise RuntimeError("main_story_entry_year_mismatch")
    if entry.get("mode") != "SPATIAL_GATE" or entry.get("sameGate") is not True:
        raise RuntimeError("main_story_entry_must_use_one_spatial_gate")
    if entry.get("entryIntent") != "MISSION" or entry.get("voluntaryMissionEntry") is not True:
        raise RuntimeError("main_story_entry_must_be_intentional_mission")
    if entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLocationsMutuallyUnknown") is not True:
        raise RuntimeError("main_story_entry_must_separate_all_three")
    if entry.get("participants") != ["kai", "iris", "syvial"]:
        raise RuntimeError("main_story_entry_participants_mismatch")

    mission = story.get("officialMission") or {}
    if mission.get("year") != MISSION_YEAR or mission.get("subject") != "Async":
        raise RuntimeError("main_story_async_mission_mismatch")
    if mission.get("entryMethod") != "SPATIAL_GATE" or mission.get("backroomsOriginKnown") is not False:
        raise RuntimeError("main_story_mission_epistemic_boundary_invalid")
    if "Async" not in str(mission.get("objective") or ""):
        raise RuntimeError("main_story_async_mission_objective_missing")

    private = story.get("kaiPrivateObjective") or {}
    if private.get("target") != PRIVATE_TARGET or private.get("status") != "MISSING":
        raise RuntimeError("main_story_kai_private_target_mismatch")
    if private.get("relationship") != "người Kai yêu":
        raise RuntimeError("main_story_kai_private_relationship_mismatch")
    if private.get("backroomsPresenceConfirmed") is not False or private.get("currentLocationKnown") is not False:
        raise RuntimeError("main_story_hua_thuy_lan_must_remain_unconfirmed")
    if not str(private.get("beliefBasis") or "").strip():
        raise RuntimeError("main_story_kai_private_belief_basis_missing")

    hidden = (story.get("hiddenStoryFacts") or {}).get("asyncCrossEraNetwork") or {}
    if hidden.get("status") != "BACKEND_ONLY" or hidden.get("knownToKaiAtStart") is not False:
        raise RuntimeError("main_story_async_hidden_fact_boundary_invalid")
    required_operatives = {
        "Jane Doe",
        "Monster X",
        "Violet Warden",
        "Jeff the Killer",
        "Jane the Killer",
        "Slenderman",
        "SCP-173",
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
        "neverConfirmHuaThuyLanInBackroomsWithoutDiscoveredEvidence",
    ):
        if locks.get(key) is not True:
            raise RuntimeError("main_story_required_lock_missing:" + key)

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

# New Game opening: the Black Blood team intentionally crosses one mission gate in 2267.
# Backrooms separates them on arrival. Core keeps Iris/Syvial as known campaign characters while
# co-location is represented by Party + explicit campaign continuity; existing combat semantics stay intact.
index = INDEX.read_text(encoding="utf-8")
start_marker = "Chiếc ly rơi xuống được nửa quãng rồi biến mất."
end_marker = "Trọng lực trở lại đột ngột."
start_pos = index.find(start_marker)
end_pos = index.find(end_marker, start_pos)
if start_pos < 0 or end_pos < 0:
    raise RuntimeError("main_story_spatial_gate_prologue_anchor_missing")
end_pos += len(end_marker)
portal_scene = '''Năm 2267.

Cổng không gian trước mặt đội Black Blood đã ổn định đủ lâu để bắt đầu nhiệm vụ. Lệnh điều tra chỉ rõ mục tiêu: tiến vào, xác minh hoạt động của Async và đánh giá nguy cơ của không gian phía sau đối với Frontrooms.

Kai kiểm tra lần cuối trang bị. Iris và Syvial đã sẵn sàng ở hai bên. Không ai bị kéo vào. Không ai no-clip. Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ.

Riêng Kai còn mang theo một lý do khác mà hồ sơ nhiệm vụ không thể xác nhận. Dữ liệu hắn có trước khi lên đường khiến hắn tin Hứa Thuý Lan, người hắn yêu đã mất tích, có thể đã rơi vào Backrooms. Dữ liệu đó không chứng minh cô còn sống, đang ở đây hay ở đâu. Nó chỉ đủ để hắn tiếp tục tìm.

Kai vẫn nhìn thấy Iris và Syvial khi vượt qua ranh giới. Rồi khoảng cách giữa ba người mất ý nghĩa. Không gian tách thành những hướng không thể quy về trái, phải, trên hay dưới. Iris biến mất khỏi tầm nhìn trước. Syvial mất dấu ngay sau đó.

Không có cảm giác rơi tự do. Không có gió quất vào người, không có lực kéo tăng dần, cũng không còn khái niệm rõ ràng về trên hay dưới. Cơ thể Kai vẫn ở đó, nhưng khoảng cách xung quanh hắn dường như không còn được đo theo cách quen thuộc.

Cảm giác ấy kéo dài chưa tới một nhịp tim.

Trọng lực trở lại đột ngột.'''
index = index[:start_pos] + portal_scene + index[end_pos:]
index = replace_once(
    index,
    'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau no-clip",',
    'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng nhiệm vụ",',
    "fresh location portal wording",
)

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
    ',entryEvent:{year:2267,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true}'
    ',iris:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}'
    ',syvial:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}'
)
replacement = comm + insertion
absolute_start = initial_start + match.start()
absolute_end = initial_start + match.end()
index = index[:absolute_start] + replacement + index[absolute_end:]
INDEX.write_text(index, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")

# Runtime projection deliberately omits hiddenStoryFacts, transitionStory and hidden Level escape data.
# Gemini sees mission context, Kai's belief/motive, and the current beat only. None of those is discovery evidence.
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
      if (beat == null) return "MAIN STORY HARD LOCK: không có story beat cho khu hiện tại; giữ continuity, không tự bịa cốt truyện mới.";

      JSONObject entry = root.optJSONObject("entryEvent");
      JSONObject mission = root.optJSONObject("officialMission");
      JSONObject privateObjective = root.optJSONObject("kaiPrivateObjective");
      if (entry == null) entry = new JSONObject();
      if (mission == null) mission = new JSONObject();
      if (privateObjective == null) privateObjective = new JSONObject();

      JSONObject missionVisible = new JSONObject()
        .put("year", mission.optInt("year", entry.optInt("year", 2267)))
        .put("unit", mission.optString("unit", "Black Blood"))
        .put("subject", mission.optString("subject", "Async"))
        .put("objective", mission.optString("objective", ""))
        .put("entryMethod", mission.optString("entryMethod", "SPATIAL_GATE"))
        .put("backroomsOriginKnown", false);

      JSONObject privateVisible = new JSONObject()
        .put("target", privateObjective.optString("target", "Hứa Thuý Lan"))
        .put("relationship", privateObjective.optString("relationship", "người Kai yêu"))
        .put("status", privateObjective.optString("status", "MISSING"))
        .put("motive", privateObjective.optString("motive", ""))
        .put("beliefBasis", privateObjective.optString("beliefBasis", ""))
        .put("backroomsPresenceConfirmed", false)
        .put("currentLocationKnown", false);

      JSONObject visible = new JSONObject()
        .put("storyId", root.optString("storyId", "MAIN_LEVEL0_TO_LEVEL1_R01"))
        .put("areaId", areaId)
        .put("phase", beat.optString("phase", ""))
        .put("storyPurpose", beat.optString("storyPurpose", ""))
        .put("visibleObjective", beat.optString("visibleObjective", ""))
        .put("discoveryThemes", beat.optJSONArray("discoveryThemes") == null ? new JSONArray() : beat.optJSONArray("discoveryThemes"))
        .put("characterThread", beat.optString("characterThread", ""))
        .put("officialMission", missionVisible)
        .put("kaiPrivateObjective", privateVisible);

      return "MAIN STORY HARD LOCK: năm 2267 Kai, Iris và Syvial CHỦ ĐỘNG đi qua CÙNG MỘT cổng không gian theo nhiệm vụ điều tra Async rồi bị tách khỏi nhau khi tới Backrooms. "
        + "Kai không biết vị trí Iris hoặc Syvial cho tới khi gameplay tạo bằng chứng/continuity hợp lệ. "
        + "Kai tin từ dữ liệu trước nhiệm vụ rằng Hứa Thuý Lan có thể đã rơi vào Backrooms, nhưng đây chỉ là niềm tin và động cơ của Kai, KHÔNG phải sự thật đã được xác nhận. "
        + "Mission brief và story beat KHÔNG phải discovery evidence: không tự tạo dấu vết Async, hồ sơ Async, giọng nói, vật chứng, vị trí hay tình trạng Hứa Thuý Lan. "
        + "Chỉ bằng chứng đã được Core/Discovery surfacing mới được dùng để xác nhận. Không tự teleport reunion, không tự khôi phục liên lạc, "
        + "không tiết lộ transition tương lai, không sửa Core/RNG/campaign route, không xác nhận nguồn gốc hay ý thức của Backrooms. CURRENT_STORY_BEAT=" + visible.toString();
    } catch (Exception ignored) {
      return "MAIN STORY HARD LOCK: năm 2267 Kai, Iris và Syvial chủ động đi qua cùng một cổng nhiệm vụ điều tra Async rồi bị tách khỏi nhau; "
        + "Hứa Thuý Lan vẫn là người mất tích mà Kai tin có thể ở Backrooms, chưa phải sự hiện diện hay vị trí đã được xác nhận.";
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
    'Mission brief và story beat KHÔNG phải discovery evidence',
    'Hứa Thuý Lan',
):
    if marker not in main:
        raise RuntimeError("main_story_runtime_marker_missing:" + marker)
if "hiddenStoryFacts" in story_helpers:
    raise RuntimeError("main_story_hidden_facts_must_not_enter_narrator_projection")
MAIN.write_text(main, encoding="utf-8")

final_index = INDEX.read_text(encoding="utf-8")
if "Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ." not in final_index:
    raise RuntimeError("intentional_spatial_gate_prologue_not_applied")
if "Năm 2267." not in final_index or "điều tra Async" not in final_index or "Hứa Thuý Lan" not in final_index:
    raise RuntimeError("mission_prologue_required_canon_missing")
if "Chiếc ly rơi xuống được nửa quãng" in final_index:
    raise RuntimeError("obsolete_restaurant_portal_scene_survived")
if "sau no-clip" in final_index:
    raise RuntimeError("obsolete_no_clip_location_survived")
for marker in (
    'entryEvent:{year:2267,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true}',
    'iris:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}',
    'syvial:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}',
):
    if marker not in final_index:
        raise RuntimeError("fresh_separation_state_missing:" + marker)

print(
    f"Integrated {STORY_ID}: intentional 2267 Async mission, three-way arrival separation, "
    f"Kai's unconfirmed Hứa Thuý Lan objective, and {len(beat_ids)} data-driven story beats through Level 1."
)
