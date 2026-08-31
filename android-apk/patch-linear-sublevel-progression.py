from pathlib import Path
import json
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CATALOG_ROOT = ROOT / "app/src/main/assets/level_catalog"
CAMPAIGN_ID = "BACKROOMS_FANDOM_LEVELS_0_6_R01"


def catalog_documents():
    if not CATALOG_ROOT.is_dir():
        raise RuntimeError("Level catalog asset directory missing")
    return sorted(
        path for path in CATALOG_ROOT.rglob("*.json")
        if path.is_file() and not path.name.startswith("_")
    )


def decode_catalog_document(path: Path):
    raw = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(raw, list):
        return raw
    if not isinstance(raw, dict):
        raise RuntimeError(f"Level catalog document must be object or array: {path}")
    entries = raw.get("entries")
    if entries is None:
        return [raw]
    if not isinstance(entries, list):
        raise RuntimeError(f"Level catalog entries must be an array: {path}")
    inherited_campaign = str(raw.get("campaignId") or "").strip()
    inherited_schema = int(raw.get("schemaVersion", 1))
    resolved = []
    for entry in entries:
        if not isinstance(entry, dict):
            raise RuntimeError(f"Level catalog entry must be an object: {path}")
        item = dict(entry)
        item.setdefault("schemaVersion", inherited_schema)
        if inherited_campaign:
            item.setdefault("campaignId", inherited_campaign)
        resolved.append(item)
    return resolved


catalog_entries = []
for document in catalog_documents():
    catalog_entries.extend(decode_catalog_document(document))

if not catalog_entries:
    raise RuntimeError("Level catalog contains no entries")

by_id = {}
for entry in catalog_entries:
    area_id = str(entry.get("id") or "").strip()
    if not area_id:
        raise RuntimeError("Level catalog entry is missing id")
    if area_id in by_id:
        raise RuntimeError(f"Duplicate Level catalog id: {area_id}")
    by_id[area_id] = entry

route_entries = [
    entry for entry in catalog_entries
    if str(entry.get("campaignId") or "").strip() == CAMPAIGN_ID
    and entry.get("campaignOrder") is not None
]
if not route_entries:
    raise RuntimeError(f"Level catalog campaign has no ordered entries: {CAMPAIGN_ID}")

orders = {}
for entry in route_entries:
    try:
        order = int(entry["campaignOrder"])
    except (TypeError, ValueError, KeyError) as error:
        raise RuntimeError(f"Invalid campaignOrder for {entry.get('id')}") from error
    if order < 0:
        raise RuntimeError(f"Negative campaignOrder for {entry.get('id')}")
    if order in orders:
        raise RuntimeError(f"Duplicate campaignOrder {order}: {orders[order]} / {entry.get('id')}")
    orders[order] = str(entry.get("id"))
    entry["_campaignOrder"] = order

route_entries.sort(key=lambda entry: (entry["_campaignOrder"], str(entry.get("id"))))

main_names = {}
for entry in catalog_entries:
    if str(entry.get("kind") or "").strip().upper() != "MAIN":
        continue
    parent = entry.get("parentMainLevel")
    if isinstance(parent, bool):
        continue
    try:
        parent_number = int(parent)
    except (TypeError, ValueError):
        continue
    main_names[parent_number] = str(entry.get("name") or entry.get("id") or f"Level {parent_number}")

ROUTE = []
PARENT_NAMES = []
for entry in route_entries:
    area_id = str(entry.get("id") or "").strip()
    name = str(entry.get("name") or "").strip()
    kind = str(entry.get("kind") or "").strip().upper()
    if kind not in {"MAIN", "SUBLEVEL", "SPECIAL"}:
        raise RuntimeError(f"Unsupported Level kind for {area_id}: {kind}")
    if not name:
        raise RuntimeError(f"Level catalog entry is missing name: {area_id}")
    try:
        parent_level = int(entry["parentMainLevel"])
    except (TypeError, ValueError, KeyError) as error:
        raise RuntimeError(f"Level catalog entry is missing parentMainLevel: {area_id}") from error
    if parent_level < 0:
        raise RuntimeError(f"Invalid parentMainLevel for {area_id}: {parent_level}")
    ROUTE.append((parent_level, area_id, name, kind))
    PARENT_NAMES.append(main_names.get(parent_level, f"Level {parent_level}"))


def j(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def java_string_array(values) -> str:
    return ", ".join(j(value) for value in values)


ids = java_string_array([item[1] for item in ROUTE])
names = java_string_array([item[2] for item in ROUTE])
types = java_string_array([item[3] for item in ROUTE])
levels = ", ".join(str(item[0]) for item in ROUTE)
parent_names = java_string_array(PARENT_NAMES)

text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    text = text.replace(old, new, 1)


level_turn_anchor = '''  private int levelTurns(JSONObject state) {
'''
helpers = r'''  private static final String LINEAR_SUBLEVEL_ROUTE_VERSION = "__ROUTE_VERSION__";
  private static final String[] LINEAR_AREA_IDS = { __IDS__ };
  private static final String[] LINEAR_AREA_NAMES = { __NAMES__ };
  private static final String[] LINEAR_AREA_TYPES = { __TYPES__ };
  private static final int[] LINEAR_AREA_LEVELS = { __LEVELS__ };
  private static final String[] LINEAR_AREA_PARENT_NAMES = { __PARENT_NAMES__ };

  private int mainRouteIndex(int level) {
    for (int i = 0; i < LINEAR_AREA_IDS.length; i++) {
      if (LINEAR_AREA_LEVELS[i] == level && "MAIN".equals(LINEAR_AREA_TYPES[i])) return i;
    }
    return -1;
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
    int fallback = mainRouteIndex(Math.max(0, currentLevel(state)));
    return fallback >= 0 ? fallback : 0;
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
    String parentName = LINEAR_AREA_PARENT_NAMES[index];
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
    flags.put("currentLevel", new JSONObject().put("number", parentLevel).put("name", parentName));
    state.put("flags", flags);
    state.put("level", new JSONObject().put("number", parentLevel).put("name", parentName));
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
      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối campaign route đã khai báo; không tự tạo khu kế tiếp.";
    }
    String nextLabel = linearAreaLabel(current + 1);
    return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Một Exit hợp lệ chỉ được tiến đúng một bước tới " + nextLabel + ". Không được bỏ qua, đảo thứ tự, chọn nhánh khác hoặc nhảy thẳng sang Level chính kế tiếp.";
  }

'''
helpers = (helpers
    .replace("__ROUTE_VERSION__", CAMPAIGN_ID)
    .replace("__IDS__", ids)
    .replace("__NAMES__", names)
    .replace("__TYPES__", types)
    .replace("__LEVELS__", levels)
    .replace("__PARENT_NAMES__", parent_names))
if "LINEAR_SUBLEVEL_ROUTE_VERSION" not in text:
    replace_once(level_turn_anchor, helpers + level_turn_anchor, "catalog route helper insertion")

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
''', "catalog route terminal gate")

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

old_prompt_return = r'''    return actionDirective + '''
new_prompt_return = r'''    return actionDirective + "\n" + linearAreaPrompt(before) + '''
replace_once(old_prompt_return, new_prompt_return, "catalog route Game Master lock")

MAIN.write_text(text, encoding="utf-8")

final = MAIN.read_text(encoding="utf-8")
required = [
    CAMPAIGN_ID,
    'LINEAR_AREA_IDS',
    'LINEAR_AREA_PARENT_NAMES',
    'return hasNextLinearArea(state) && levelTurns(state) >= 6;',
    'boolean areaAdvanced = false;',
    'advanceLinearArea(before, state)',
    'recordLevelProgress(state, before, oldLevel, newLevel, areaAdvanced)',
    'LINEAR SUBLEVEL HARD LOCK:',
    'linearAreaPrompt(before)',
    'return exitFound && progressionReady(before);',
]
for marker in required:
    if marker not in final:
        raise RuntimeError("Catalog campaign contract missing: " + marker)

for forbidden in [
    'return explicitlyReady || levelTurns(state) >= 6;',
    'recordLevelProgress(state, oldLevel, newLevel);',
    'newLevel = mentioned;',
    'Đây là cuối route Level 0–6',
]:
    if forbidden in final:
        raise RuntimeError("Legacy fixed-route contract survived: " + forbidden)

print(f"Installed data-driven campaign route from Level catalog: {len(ROUTE)} areas ({CAMPAIGN_ID}).")

# GM item gain is deliberately the final release-chain layer. It runs after progression,
# combat and Entity finalizers so no later patch can restore read-only GM gains or erase
# the compact notification from the packaged gameplay UI.
runpy.run_path(str(ROOT / "patch-gm-item-gain-finalize.py"), run_name="__main__")
