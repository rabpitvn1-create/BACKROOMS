from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
EVOLUTION = ROOT / "app/src/main/assets/campaign_story/hourly-story-evolution.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


story = json.loads(STORY.read_text(encoding="utf-8"))
evolution = json.loads(EVOLUTION.read_text(encoding="utf-8"))

if evolution.get("schemaVersion") != 1:
    raise RuntimeError("hourly_story_schema_invalid")
if evolution.get("evolutionId") != "MAIN_STORY_HOURLY_EVOLUTION_R01":
    raise RuntimeError("hourly_story_id_invalid")
if evolution.get("storyId") != story.get("storyId") or evolution.get("campaignId") != story.get("campaignId"):
    raise RuntimeError("hourly_story_campaign_identity_mismatch")

beat_ids = [str(raw.get("areaId")) for raw in story.get("beats", [])]
expected_sequence = ["PROLOGUE"] + beat_ids
sequence = [str(value) for value in evolution.get("sequence", [])]
if sequence != expected_sequence:
    raise RuntimeError("hourly_story_sequence_mismatch:" + ",".join(sequence))

completed = [str(value) for value in evolution.get("completedSteps", [])]
if not completed or completed != expected_sequence[: len(completed)]:
    raise RuntimeError("hourly_story_completed_steps_must_be_prefix")
if len(completed) >= len(expected_sequence):
    expected_next = "COMPLETE"
else:
    expected_next = expected_sequence[len(completed)]
if str(evolution.get("nextStep") or "") != expected_next:
    raise RuntimeError("hourly_story_next_step_invalid")

steps = evolution.get("steps") or {}
for step_id in completed:
    raw = steps.get(step_id)
    if not isinstance(raw, dict):
        raise RuntimeError("hourly_story_completed_step_missing:" + step_id)
    for key in ("phase", "storyUpdate", "survivalFocus", "npcPolicy", "itemPolicy", "relationshipStage", "endingState"):
        if not str(raw.get(key) or "").strip():
            raise RuntimeError(f"hourly_story_step_missing_{key}:{step_id}")
for step_id in steps:
    if step_id not in expected_sequence:
        raise RuntimeError("hourly_story_unknown_step:" + step_id)

rules = evolution.get("globalRules") or {}
for key in ("sourceReload", "survival", "proceduralNpc", "items", "dialogue", "horror"):
    if not str(rules.get(key) or "").strip():
        raise RuntimeError("hourly_story_global_rule_missing:" + key)
relationship = rules.get("relationship") or {}
for key in ("direction", "kaiAwareness", "progressionRule", "addressRule", "knowledgeRule"):
    if not str(relationship.get(key) or "").strip():
        raise RuntimeError("hourly_story_relationship_rule_missing:" + key)

prologue = steps.get("PROLOGUE") or {}
prologue_text = json.dumps(prologue, ensure_ascii=False)
for required in ("2299", "Async", "Kai", "Iris", "Syvial", "Level 0", "Lucia"):
    if required not in prologue_text:
        raise RuntimeError("hourly_story_prologue_marker_missing:" + required)
for forbidden in ("Hứa Thuý Lan", "2267", "Black Blood", "nhà hàng"):
    if forbidden in prologue_text:
        raise RuntimeError("hourly_story_obsolete_prologue_marker:" + forbidden)

main = MAIN.read_text(encoding="utf-8")

load_helper = r'''  private JSONObject loadHourlyStoryEvolution() throws Exception {
    StringBuilder content = new StringBuilder();
    try (InputStream stream = getAssets().open("campaign_story/hourly-story-evolution.json");
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) content.append(line).append('\n');
    }
    return new JSONObject(content.toString());
  }

'''
area_anchor = '  private String currentStoryAreaId(JSONObject state) {\n'
if "private JSONObject loadHourlyStoryEvolution()" not in main:
    main = replace_once(main, area_anchor, load_helper + area_anchor, "hourly story loader")

root_anchor = '''      JSONObject root = loadLevel01Story();
      String areaId = currentStoryAreaId(state);
'''
root_replacement = '''      JSONObject root = loadLevel01Story();
      JSONObject evolution = loadHourlyStoryEvolution();
      String areaId = currentStoryAreaId(state);
'''
if "JSONObject evolution = loadHourlyStoryEvolution();" not in main:
    main = replace_once(main, root_anchor, root_replacement, "hourly story root load")

quest_anchor = '      JSONObject questVisible = state != null ? state.optJSONObject("storyQuest") : null;\n'
thread_projection = r'''      JSONObject globalRules = evolution.optJSONObject("globalRules");
      if (globalRules == null) globalRules = new JSONObject();
      JSONObject relationshipRules = globalRules.optJSONObject("relationship");
      if (relationshipRules == null) relationshipRules = new JSONObject();
      JSONObject committedSteps = evolution.optJSONObject("steps");
      JSONObject currentHourlyStep = committedSteps != null ? committedSteps.optJSONObject(areaId) : null;
      boolean hourlyStepCommitted = currentHourlyStep != null;
      if (currentHourlyStep == null) currentHourlyStep = new JSONObject();
      boolean luciaPresent = partyHas(state, "lucia");

      JSONObject relationshipVisible = new JSONObject()
        .put("active", hourlyStepCommitted && luciaPresent)
        .put("luciaPresent", luciaPresent)
        .put("stage", currentHourlyStep.optString("relationshipStage", ""))
        .put("direction", relationshipRules.optString("direction", ""))
        .put("kaiAwareness", relationshipRules.optString("kaiAwareness", ""))
        .put("progressionRule", relationshipRules.optString("progressionRule", ""))
        .put("addressRule", relationshipRules.optString("addressRule", ""))
        .put("knowledgeRule", relationshipRules.optString("knowledgeRule", ""));

      JSONObject hourlyVisible = new JSONObject()
        .put("evolutionId", evolution.optString("evolutionId", "MAIN_STORY_HOURLY_EVOLUTION_R01"))
        .put("stepCommitted", hourlyStepCommitted)
        .put("currentArea", areaId)
        .put("phase", currentHourlyStep.optString("phase", ""))
        .put("storyUpdate", currentHourlyStep.optString("storyUpdate", ""))
        .put("survivalFocus", currentHourlyStep.optString("survivalFocus", ""))
        .put("npcPolicy", currentHourlyStep.optString("npcPolicy", ""))
        .put("itemPolicy", currentHourlyStep.optString("itemPolicy", ""))
        .put("endingState", currentHourlyStep.optString("endingState", ""))
        .put("survivalRule", globalRules.optString("survival", ""))
        .put("proceduralNpcRule", globalRules.optString("proceduralNpc", ""))
        .put("itemRule", globalRules.optString("items", ""))
        .put("relationship", relationshipVisible);

'''
if "JSONObject hourlyVisible = new JSONObject()" not in main:
    main = replace_once(main, quest_anchor, thread_projection + quest_anchor, "hourly story prompt projection")

visible_anchor = '''        .put("characterThread", beat.optString("characterThread", ""))
        .put("officialMission", missionVisible);'''
visible_replacement = '''        .put("characterThread", beat.optString("characterThread", ""))
        .put("hourlyNarrative", hourlyVisible)
        .put("officialMission", missionVisible);'''
if '.put("hourlyNarrative", hourlyVisible)' not in main:
    main = replace_once(main, visible_anchor, visible_replacement, "hourly story visible payload")

prompt_anchor = '''        + "không tiết lộ reunion level tương lai, transition hoặc hidden escape data, không sửa Core/RNG/campaign route. CURRENT_STORY_BEAT=" + visible.toString();'''
prompt_replacement = '''        + "không tiết lộ reunion level tương lai, transition hoặc hidden escape data, không sửa Core/RNG/campaign route. "
        + "HOURLY STORY EVOLUTION HARD LOCK: hourlyNarrative chỉ được dùng khi stepCommitted=true; nếu false, giữ base story beat và không tự viết trước bước chưa commit. "
        + "NPC/survivor và item chỉ được kể khi Core/RNG/world-loot/inventory đã xác nhận. Relationship thread chỉ hoạt động khi luciaPresent=true; "
        + "Lucia có thể dần nảy sinh tình cảm với Kai qua continuity đã xảy ra, còn Kai phải nhận ra từ mẫu hành vi quan sát được chứ không đọc ý nghĩ. "
        + "Không tự bịa hoặc đổi xưng hô Lucia-Kai. CURRENT_STORY_BEAT=" + visible.toString();'''
if "HOURLY STORY EVOLUTION HARD LOCK" not in main:
    main = replace_once(main, prompt_anchor, prompt_replacement, "hourly story prompt hard lock")

for required in (
    'getAssets().open("campaign_story/hourly-story-evolution.json")',
    'JSONObject evolution = loadHourlyStoryEvolution();',
    '.put("hourlyNarrative", hourlyVisible)',
    'HOURLY STORY EVOLUTION HARD LOCK',
    'Lucia có thể dần nảy sinh tình cảm với Kai',
    'Không tự bịa hoặc đổi xưng hô Lucia-Kai',
):
    if required not in main:
        raise RuntimeError("hourly_story_runtime_marker_missing:" + required)

MAIN.write_text(main, encoding="utf-8")
print("Applied hourly main-story evolution projection: committed steps only, Core-owned NPC/item outcomes, gradual Lucia-Kai relationship continuity.")
