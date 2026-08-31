from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
SPECIAL = ROOT / "app/src/main/java/com/rabpit/backroom/core/SpecialFollowersCanon.kt"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
CATALOG = ROOT / "app/src/main/assets/level_catalog/backrooms-0-6.json"
CAMPAIGN_ID = "BACKROOMS_FANDOM_LEVELS_0_6_R01"
STORY_ID = "MAIN_LEVEL0_TO_LEVEL1_R01"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


story = json.loads(STORY.read_text(encoding="utf-8"))
catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
if story.get("storyId") != STORY_ID:
    raise RuntimeError("main_story_level0_1_story_id_mismatch")
if story.get("campaignId") != CAMPAIGN_ID or catalog.get("campaignId") != CAMPAIGN_ID:
    raise RuntimeError("main_story_level0_1_campaign_mismatch")
entry = story.get("entryEvent") or {}
if entry.get("mode") != "SPATIAL_GATE" or entry.get("sameGate") is not True:
    raise RuntimeError("main_story_entry_must_use_one_spatial_gate")
if entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLocationsMutuallyUnknown") is not True:
    raise RuntimeError("main_story_entry_must_separate_all_three")
if entry.get("participants") != ["kai", "iris", "syvial"]:
    raise RuntimeError("main_story_entry_participants_mismatch")

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
expected_story_route = route_ids[start : end + 1]
beat_ids = [str(raw.get("areaId")) for raw in story.get("beats", [])]
if beat_ids != expected_story_route:
    raise RuntimeError(
        "main_story_route_mismatch:" + ",".join(beat_ids) + " != " + ",".join(expected_story_route)
    )
if len(set(beat_ids)) != len(beat_ids):
    raise RuntimeError("main_story_duplicate_area_beat")
for beat in story.get("beats", []):
    for key in ("areaId", "title", "phase", "storyPurpose", "visibleObjective", "characterThread"):
        if not str(beat.get(key) or "").strip():
            raise RuntimeError(f"main_story_beat_missing_{key}:{beat.get('areaId')}")

# Fresh-run Core state: Iris and Syvial exist from the opening event but are not physically with Kai.
# Existing saves keep their current presence because the default is changed only for newly created states.
special = SPECIAL.read_text(encoding="utf-8")
special = replace_once(
    special,
    '''      id = IRIS_ID,
      name = "Iris",
      physiology = PhysiologyState.freshRunBaseline()
''',
    '''      id = IRIS_ID,
      name = "Iris",
      presence = CharacterPresence.SEPARATED,
      physiology = PhysiologyState.freshRunBaseline()
''',
    "fresh Iris separated presence",
)
special = replace_once(
    special,
    '''      id = SYVIAL_ID,
      name = "Syvial",
      physiology = PhysiologyState.freshRunBaseline()
''',
    '''      id = SYVIAL_ID,
      name = "Syvial",
      presence = CharacterPresence.SEPARATED,
      physiology = PhysiologyState.freshRunBaseline()
''',
    "fresh Syvial separated presence",
)
SPECIAL.write_text(special, encoding="utf-8")

# Retcon the New Game opening from accidental no-clip to one spatial gate that takes all three
# participants, then resolves them to mutually separated arrival locations.
index = INDEX.read_text(encoding="utf-8")
start_marker = "Chiếc ly rơi xuống được nửa quãng rồi biến mất."
end_marker = "Trọng lực trở lại đột ngột."
start_pos = index.find(start_marker)
end_pos = index.find(end_marker, start_pos)
if start_pos < 0 or end_pos < 0:
    raise RuntimeError("main_story_spatial_gate_prologue_anchor_missing")
end_pos += len(end_marker)
portal_scene = '''Chiếc ly rơi xuống được nửa quãng thì không khí phía dưới nó lõm vào như một mặt kính mềm. Ánh sáng của nhà hàng kéo thành những vệt cong quanh một điểm tối không có chiều sâu rõ ràng.

Kai đứng bật dậy.

“Rời khỏi đây.”

Điểm tối mở rộng nhanh hơn phản ứng của bất kỳ ai trong phòng. Nó không giống một cánh cửa gắn trên tường; khoảng không tự tách ra giữa các bàn ăn, tạo thành một cổng không gian méo ánh sáng ở cả hai phía.

Iris đã rời ghế. Syvial xoay người về phía lối đi. Cả ba cùng lùi khỏi vùng biến dạng, nhưng lực kéo không đến từ gió hay áp suất. Khoảng cách giữa họ và cánh cổng đơn giản bị rút ngắn.

Kai chộp lấy mép bàn. Iris nắm được cổ tay hắn trong một khoảnh khắc. Syvial vươn tay về phía cả hai.

Mặt bàn, ánh đèn và những thực khách phía sau kéo dài thành những dải sáng.

Cả ba bị kéo qua cùng một cổng không gian.

Kai vẫn nhìn thấy Iris và Syvial khi vượt qua ranh giới. Rồi không gian giữa ba người tách thành những hướng không thể quy về trái, phải, trên hay dưới. Bàn tay Iris tuột khỏi tay hắn dù khoảng cách trông chưa tới một gang. Syvial biến mất khỏi tầm nhìn theo một hướng khác ngay sau đó.

Không có cảm giác rơi tự do. Không có gió quất vào người, không có lực kéo tăng dần, cũng không còn khái niệm rõ ràng về trên hay dưới. Cơ thể Kai vẫn ở đó, nhưng khoảng cách xung quanh hắn dường như không còn được đo theo cách quen thuộc.

Cảm giác ấy kéo dài chưa tới một nhịp tim.

Trọng lực trở lại đột ngột.'''
index = index[:start_pos] + portal_scene + index[end_pos:]
index = replace_once(
    index,
    'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau no-clip",',
    'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng không gian",',
    "fresh location portal wording",
)
index = replace_once(
    index,
    'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"}},',
    'flags:{communication:{blackBlood:"OFFLINE",sruForce:"OFFLINE",frontrooms:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"},entryEvent:{mode:"SPATIAL_GATE",sameGate:true,allSeparatedOnArrival:true},iris:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false},syvial:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}},',
    "fresh separation flags",
)
INDEX.write_text(index, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")

# This projection deliberately omits transitionStory and every hidden Level escape blueprint.
# Gemini gets only the current story beat and player-visible continuity. Core remains authoritative.
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
          if (candidate != null && areaId.equals(candidate.optString("areaId", ""))) {
            beat = candidate;
            break;
          }
        }
      }
      if (beat == null) return "MAIN STORY HARD LOCK: không có story beat cho khu hiện tại; giữ continuity, không tự bịa cốt truyện mới.";
      JSONObject visible = new JSONObject()
        .put("storyId", root.optString("storyId", "MAIN_LEVEL0_TO_LEVEL1_R01"))
        .put("areaId", areaId)
        .put("phase", beat.optString("phase", ""))
        .put("storyPurpose", beat.optString("storyPurpose", ""))
        .put("visibleObjective", beat.optString("visibleObjective", ""))
        .put("discoveryThemes", beat.optJSONArray("discoveryThemes") == null ? new JSONArray() : beat.optJSONArray("discoveryThemes"))
        .put("characterThread", beat.optString("characterThread", ""));
      return "MAIN STORY HARD LOCK: Kai, Iris và Syvial đã đi qua CÙNG MỘT cổng không gian từ Frontrooms nhưng bị tách khỏi nhau khi tới Backrooms. "
        + "Kai không biết vị trí Iris hoặc Syvial cho tới khi gameplay tạo bằng chứng/continuity hợp lệ. Không tự teleport reunion, không tự khôi phục liên lạc, "
        + "không tiết lộ transition tương lai, không sửa Core/RNG/campaign route. CURRENT_STORY_BEAT=" + visible.toString();
    } catch (Exception ignored) {
      return "MAIN STORY HARD LOCK: Kai, Iris và Syvial đi qua cùng một cổng không gian rồi bị tách khỏi nhau; vị trí Iris và Syvial vẫn chưa biết nếu state chưa chứng minh ngược lại.";
    }
  }

'''
helper_anchor = '''  private int levelTurns(JSONObject state) {
'''
if "private String campaignStoryBeatPrompt(" not in main:
    main = replace_once(main, helper_anchor, story_helpers + helper_anchor, "campaign story runtime helpers")

main = replace_once(
    main,
    '''      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối campaign route đã khai báo; không tự tạo khu kế tiếp.";
''',
    '''      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối campaign route đã khai báo; không tự tạo khu kế tiếp.\\n" + campaignStoryBeatPrompt(state);
''',
    "terminal story prompt",
)
main = replace_once(
    main,
    '''    return "TRANSITION GRAPH HARD LOCK: khu hiện tại = " + currentLabel + ". Target authoritative đã khai báo là " + nextLabel + ". Model không được tự chọn target ngoài graph.";
''',
    '''    return "TRANSITION GRAPH HARD LOCK: khu hiện tại = " + currentLabel + ". Target authoritative đã khai báo là " + nextLabel + ". Model không được tự chọn target ngoài graph.\\n" + campaignStoryBeatPrompt(state);
''',
    "active story prompt",
)
main = replace_once(
    main,
    '''        + "VISIBLE_RESOLVED_OUTCOME=" + visible.toString();
''',
    '''        + "MAIN_STORY_CONTEXT=" + campaignStoryBeatPrompt(state) + "\\n"
        + "VISIBLE_RESOLVED_OUTCOME=" + visible.toString();
''',
    "registered narration story context",
)

for marker in (
    'getAssets().open("campaign_story/level0-to-level1.json")',
    'MAIN STORY HARD LOCK:',
    'CURRENT_STORY_BEAT=',
    'MAIN_STORY_CONTEXT=',
    'campaignStoryBeatPrompt(state)',
):
    if marker not in main:
        raise RuntimeError("main_story_runtime_marker_missing:" + marker)
MAIN.write_text(main, encoding="utf-8")

final_index = INDEX.read_text(encoding="utf-8")
final_special = SPECIAL.read_text(encoding="utf-8")
if "Cả ba bị kéo qua cùng một cổng không gian." not in final_index:
    raise RuntimeError("spatial_gate_prologue_not_applied")
if "sau no-clip" in final_index:
    raise RuntimeError("obsolete_no_clip_location_survived")
if final_special.count("presence = CharacterPresence.SEPARATED") < 2:
    raise RuntimeError("fresh_special_followers_not_separated")

print(
    f"Integrated {STORY_ID}: one spatial-gate entry, fresh Iris/Syvial separation, "
    f"and {len(beat_ids)} data-driven story beats from Level 0 through Level 1."
)
