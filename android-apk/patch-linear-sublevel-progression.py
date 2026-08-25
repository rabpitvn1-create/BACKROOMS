from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# Source order follows Backrooms Wiki:Levels 0-8. The game deliberately treats that
# published list as one deterministic campaign route instead of inventing branching.
# Main Levels remain the authoritative parent level for combat/difficulty/save rules.
ROUTE = [
    (0, "0", "The Lobby", "MAIN"),
    (0, "epsilon", "Incessant Hum-Buzz", "SPECIAL"),
    (0, "0.01", "The Exit ?", "SUBLEVEL"),
    (0, "0.1", "Deep Emptiness", "SUBLEVEL"),
    (0, "0.11", "Water Damage", "SUBLEVEL"),
    (0, "0.22", "Fully Remodeled", "SUBLEVEL"),
    (0, "0.23", "Half Finished", "SUBLEVEL"),
    (0, "0.41", "Disease", "SUBLEVEL"),
    (0, "0.5", "Chaotic Structure", "SUBLEVEL"),
    (0, "0.66", "The Lobby Went COLD", "SUBLEVEL"),
    (0, "0.7", "Claustrophobia", "SUBLEVEL"),
    (0, "0.8", "Inundation", "SUBLEVEL"),
    (0, "0.99", "Deeper Regions", "SUBLEVEL"),
    (0, "LS-2", "LS-2", "SPECIAL"),
    (0, "Dullness", "Dullness", "SPECIAL"),
    (0, "Red Rooms", "Red Rooms", "SPECIAL"),
    (1, "1", "Parking Zone", "MAIN"),
    (1, "1.01", "The Basement of Level 1", "SUBLEVEL"),
    (1, "1.1", "Fallen Vehicle", "SUBLEVEL"),
    (1, "1.5", "Lurking Danger", "SUBLEVEL"),
    (1, "1.618033988749894...", "Midas’ Touch", "SUBLEVEL"),
    (2, "2", "Pipe Dreams", "MAIN"),
    (2, "2.1", "The Subterranean Complex", "SUBLEVEL"),
    (2, "2.71828182845...", "Euler’s Imagination", "SUBLEVEL"),
    (2, "2.2", "The Red Flood", "SUBLEVEL"),
    (3, "3", "Electrical Station", "MAIN"),
    (3, "3.14159265358...", "satuЯation", "SUBLEVEL"),
    (3, "3.53", "The Cacophony of Corrosion", "SUBLEVEL"),
    (4, "4", "The Abandoned Office", "MAIN"),
    (4, "4.3", "The Cubicles", "SUBLEVEL"),
    (4, "4.4", "Intrusive Configuration", "SUBLEVEL"),
    (4, "4.11", "Insubstantial Skywalks", "SUBLEVEL"),
    (5, "5", "Terror Hotel", "MAIN"),
    (5, "5.1", "Summer Resort", "SUBLEVEL"),
    (5, "5.2", "The Gilded Atrium", "SUBLEVEL"),
    (5, "5.55", "Can’t Stop Watching", "SUBLEVEL"),
    (6, "6", "Lights Out", "MAIN"),
    (6, "6.1", "Silva Subterraneus", "SUBLEVEL"),
    (6, "6.2", "Eyes On The Road", "SUBLEVEL"),
    (6, "6.28318530718...", "Amaxophobia", "SUBLEVEL"),
    (6, "6.5", "Blinding Lights", "SUBLEVEL"),
    (6, "6.66", "Cryophobia", "SUBLEVEL"),
    (6, "6.99", "Umbral Light", "SUBLEVEL"),
]

if len(ROUTE) != 43:
    raise RuntimeError(f"Linear sublevel route must contain exactly 43 areas, found {len(ROUTE)}")
if [area_id for level, area_id, _, _ in ROUTE if level == 0] != [
    "0", "epsilon", "0.01", "0.1", "0.11", "0.22", "0.23", "0.41", "0.5", "0.66", "0.7", "0.8", "0.99", "LS-2", "Dullness", "Red Rooms"
]:
    raise RuntimeError("Level 0 wiki route order drifted")
if [area_id for level, area_id, _, _ in ROUTE if level == 2] != ["2", "2.1", "2.71828182845...", "2.2"]:
    raise RuntimeError("Level 2 must preserve Backrooms Wiki listing order")
if ROUTE[-1][:3] != (6, "6.99", "Umbral Light"):
    raise RuntimeError("Linear campaign must terminate at Level 6.99")


def j(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def java_string_array(values) -> str:
    return ", ".join(j(value) for value in values)


ids = java_string_array([item[1] for item in ROUTE])
names = java_string_array([item[2] for item in ROUTE])
types = java_string_array([item[3] for item in ROUTE])
levels = ", ".join(str(item[0]) for item in ROUTE)

text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    text = text.replace(old, new, 1)


level_turn_anchor = '''  private int levelTurns(JSONObject state) {
'''
helpers = r'''  private static final String LINEAR_SUBLEVEL_ROUTE_VERSION = "BACKROOMS_FANDOM_LEVELS_0_6_R01";
  private static final String[] LINEAR_AREA_IDS = { __IDS__ };
  private static final String[] LINEAR_AREA_NAMES = { __NAMES__ };
  private static final String[] LINEAR_AREA_TYPES = { __TYPES__ };
  private static final int[] LINEAR_AREA_LEVELS = { __LEVELS__ };

  private int mainRouteIndex(int level) {
    for (int i = 0; i < LINEAR_AREA_IDS.length; i++) {
      if (LINEAR_AREA_LEVELS[i] == level && "MAIN".equals(LINEAR_AREA_TYPES[i])) return i;
    }
    return 0;
  }

  private int linearAreaIndex(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    if (exploration != null) {
      int stored = exploration.optInt("routeIndex", -1);
      String storedId = exploration.optString("areaId", "");
      if (stored >= 0 && stored < LINEAR_AREA_IDS.length &&
          (storedId.isEmpty() || LINEAR_AREA_IDS[stored].equals(storedId))) return stored;
      if (!storedId.isEmpty()) {
        for (int i = 0; i < LINEAR_AREA_IDS.length; i++) if (LINEAR_AREA_IDS[i].equals(storedId)) return i;
      }
    }
    return mainRouteIndex(Math.max(0, Math.min(6, currentLevel(state))));
  }

  private boolean hasNextLinearArea(JSONObject state) {
    return linearAreaIndex(state) + 1 < LINEAR_AREA_IDS.length;
  }

  private String linearAreaLabel(int index) {
    if (index < 0 || index >= LINEAR_AREA_IDS.length) return "Unknown Area";
    String id = LINEAR_AREA_IDS[index];
    String name = LINEAR_AREA_NAMES[index];
    String type = LINEAR_AREA_TYPES[index];
    if ("MAIN".equals(type)) return "Level " + id + " – " + name;
    if ("epsilon".equals(id)) return "Level ε – " + name;
    if ("SPECIAL".equals(type)) return id.equals(name) ? id : id + " – " + name;
    return "Level " + id + " – " + name;
  }

  private void stampLinearArea(JSONObject state, int index, boolean resetProgress, boolean relocate) throws Exception {
    if (index < 0 || index >= LINEAR_AREA_IDS.length) throw new Exception("linear_area_index_out_of_range");
    int parentLevel = LINEAR_AREA_LEVELS[index];
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();

    exploration.put("routeVersion", LINEAR_SUBLEVEL_ROUTE_VERSION);
    exploration.put("routeIndex", index);
    exploration.put("areaId", LINEAR_AREA_IDS[index]);
    exploration.put("areaName", LINEAR_AREA_NAMES[index]);
    exploration.put("areaType", LINEAR_AREA_TYPES[index]);
    exploration.put("parentLevel", parentLevel);
    exploration.put("minimumTurns", 6);
    if (resetProgress) {
      exploration.put("levelTurns", 0);
      for (String key : new String[] {"confirmedExit", "transitionReady", "exitReady", "exitProgress", "exitCandidate", "exitChanceThreshold"}) {
        exploration.remove(key);
      }
    }

    flags.put("exploration", exploration);
    flags.put("currentLevel", new JSONObject().put("number", parentLevel).put("name", levelName(parentLevel)));
    state.put("flags", flags);
    state.put("level", new JSONObject().put("number", parentLevel).put("name", levelName(parentLevel)));
    if (relocate) {
      String label = linearAreaLabel(index);
      state.put("title", label);
      state.put("location", label);
    }
  }

  private boolean advanceLinearArea(JSONObject before, JSONObject state) throws Exception {
    int current = linearAreaIndex(before);
    int next = current + 1;
    if (next >= LINEAR_AREA_IDS.length) return false;
    stampLinearArea(state, next, true, true);
    return true;
  }

  private String linearAreaPrompt(JSONObject state) {
    int current = linearAreaIndex(state);
    String currentLabel = linearAreaLabel(current);
    if (current + 1 >= LINEAR_AREA_IDS.length) {
      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối route Level 0–6; không tự tạo Level 7 hoặc khu kế tiếp.";
    }
    String nextLabel = linearAreaLabel(current + 1);
    return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Một Exit hợp lệ chỉ được tiến đúng một bước tới " + nextLabel + ". Không được bỏ qua, đảo thứ tự, chọn nhánh khác hoặc nhảy thẳng sang Level chính kế tiếp.";
  }

'''
helpers = helpers.replace("__IDS__", ids).replace("__NAMES__", names).replace("__TYPES__", types).replace("__LEVELS__", levels)
if "LINEAR_SUBLEVEL_ROUTE_VERSION" not in text:
    replace_once(level_turn_anchor, helpers + level_turn_anchor, "linear route helper insertion")

old_ready = '''  private boolean progressionReady(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    boolean explicitlyReady = exploration != null && (exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false));
    return explicitlyReady || levelTurns(state) >= 6;
  }
'''
new_ready = '''  private boolean progressionReady(JSONObject state) {
    return hasNextLinearArea(state) && levelTurns(state) >= 6;
  }
'''
replace_once(old_ready, new_ready, "per-area six-turn gate")

old_record = '''  private void recordLevelProgress(JSONObject state, int oldLevel, int newLevel) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    exploration.put("levelTurns", oldLevel == newLevel ? levelTurns(state) + 1 : 0);
    exploration.put("minimumTurns", 6);
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }
'''
new_record = '''  private void recordLevelProgress(JSONObject state, JSONObject before, int oldLevel, int newLevel, boolean areaAdvanced) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    int trustedIndex = linearAreaIndex(state);
    exploration.put("routeVersion", LINEAR_SUBLEVEL_ROUTE_VERSION);
    exploration.put("routeIndex", trustedIndex);
    exploration.put("areaId", LINEAR_AREA_IDS[trustedIndex]);
    exploration.put("areaName", LINEAR_AREA_NAMES[trustedIndex]);
    exploration.put("areaType", LINEAR_AREA_TYPES[trustedIndex]);
    exploration.put("parentLevel", LINEAR_AREA_LEVELS[trustedIndex]);
    exploration.put("levelTurns", (areaAdvanced || oldLevel != newLevel) ? 0 : levelTurns(before) + 1);
    exploration.put("minimumTurns", 6);
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }
'''
replace_once(old_record, new_record, "trusted per-area progress accounting")

can_transition_sig = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
'''
if "if (!hasNextLinearArea(before)) return false;" not in text:
    replace_once(can_transition_sig, can_transition_sig + '''    if (!hasNextLinearArea(before)) return false;
''', "linear route terminal gate")

old_exit_found = '''    boolean exitFound = (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
'''
new_exit_found = '''    JSONObject currentExitRoll = rolls.optJSONObject("levelExit");
    boolean currentExitIntent = currentExitRoll != null && currentExitRoll.optBoolean("eligible", false);
    boolean exitFound = rollSuccess(rolls, "levelExit") || (currentExitIntent && confirmedExit != null && !confirmedExit.trim().isEmpty());
'''
replace_once(old_exit_found, new_exit_found, "current-action exit eligibility")

old_post_commit = '''          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          int mentioned = mentionedLevel(state);
          if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
            newLevel = mentioned;
            state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
          }
          boolean levelChanged = oldLevel != newLevel;
'''
new_post_commit = '''          int oldLevel = currentLevel(before);
          int proposedLevel = currentLevel(state);
          boolean areaAdvanced = false;
          if (canTransition(before, rolls)) areaAdvanced = advanceLinearArea(before, state);
          if (!areaAdvanced) {
            int currentArea = linearAreaIndex(before);
            stampLinearArea(state, currentArea, false, proposedLevel != oldLevel);
          }
          int newLevel = currentLevel(state);
          boolean levelChanged = oldLevel != newLevel;
'''
replace_once(old_post_commit, new_post_commit, "deterministic one-step area transition")

replace_once(
    '''            recordLevelProgress(state, oldLevel, newLevel);
''',
    '''            recordLevelProgress(state, before, oldLevel, newLevel, areaAdvanced);
''',
    "per-area progress call",
)

old_prompt_return = r'''    return actionDirective + "\nACTION_RUNTIME: " + actionRuntimeContext + "\n" +
'''
new_prompt_return = r'''    return actionDirective + "\n" + linearAreaPrompt(before) + "\nACTION_RUNTIME: " + actionRuntimeContext + "\n" +
'''
replace_once(old_prompt_return, new_prompt_return, "linear route Game Master lock")

MAIN.write_text(text, encoding="utf-8")

final = MAIN.read_text(encoding="utf-8")
required = [
    'BACKROOMS_FANDOM_LEVELS_0_6_R01',
    '"epsilon", "0.01", "0.1", "0.11"',
    '"2", "2.1", "2.71828182845...", "2.2"',
    '"6.66", "6.99"',
    'return hasNextLinearArea(state) && levelTurns(state) >= 6;',
    'boolean areaAdvanced = false;',
    'advanceLinearArea(before, state)',
    'recordLevelProgress(state, before, oldLevel, newLevel, areaAdvanced)',
    'LINEAR SUBLEVEL HARD LOCK:',
    'return exitFound && progressionReady(before);',
]
for marker in required:
    if marker not in final:
        raise RuntimeError("Linear sublevel contract missing: " + marker)

for forbidden in [
    'return explicitlyReady || levelTurns(state) >= 6;',
    'recordLevelProgress(state, oldLevel, newLevel);',
    'newLevel = mentioned;',
]:
    if forbidden in final:
        raise RuntimeError("Legacy Level-skip contract survived: " + forbidden)

print("Installed deterministic 43-area Backrooms Wiki route: every valid Exit advances exactly one area from Level 0 through Level 6.99.")
