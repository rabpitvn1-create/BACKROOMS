from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

# New inventory must be supported by structured state or a locked discovery roll.
old_inventory = r'''        boolean allowedNew = acquisitionIntent(action);
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (madGod && !madGodAlreadySpawned) allowedNew = false;
        if (almond) {
          JSONObject waterRoll = rolls.optJSONObject("almondWater");
          if (waterRoll != null && waterRoll.optBoolean("eligible", false) && !waterRoll.optBoolean("success", false) && existing < 0) allowedNew = false;
        }
'''
new_inventory = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (existing >= 0) allowedNew = true;
        else if (acquisitionIntent(action)) {
          if (madGod) allowedNew = madGodAlreadySpawned && establishedStructured;
          else if (almond) allowedNew = establishedStructured || rollSuccess(rolls, "almondWater");
          else if (containsAny(action, "copy", "sao chép")) allowedNew = establishedStructured;
          else allowedNew = establishedStructured || rollSuccess(rolls, "loot");
        }
'''
replace_once(old_inventory, new_inventory, "structured inventory acquisition")

# Player state may not invent HP systems or arbitrary equipment.
old_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        for (String key : new String[] {"hp", "condition", "weapon", "armor"}) if (patch.has(key)) current.put(key, patch.get(key));
        if (patch.optJSONObject("needs") != null) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          mergeObject(needs, patch.optJSONObject("needs"));
          current.put("needs", needs);
        }
'''
new_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        boolean worldConsequence = rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter");
        boolean recoveryIntent = containsAny(action, "ăn", "uống", "nghỉ", "ngủ", "băng bó", "chữa", "hồi phục", "eat", "drink", "rest", "sleep", "heal");
        boolean gearIntent = containsAny(action, "rút", "cất", "trang bị", "mặc", "cởi", "tháo", "đeo", "draw", "equip", "unequip", "wear");
        if (patch.has("hp") && current.has("hp") && !current.isNull("hp")) {
          double beforeHp = current.optDouble("hp", Double.NaN);
          double afterHp = patch.optDouble("hp", Double.NaN);
          if (!Double.isNaN(beforeHp) && !Double.isNaN(afterHp) && afterHp >= 0 &&
              ((afterHp < beforeHp && worldConsequence) || (afterHp >= beforeHp && recoveryIntent))) current.put("hp", afterHp);
        }
        if (patch.has("condition") && (worldConsequence || recoveryIntent)) current.put("condition", patch.optString("condition", current.optString("condition", "")));
        if (patch.optJSONObject("needs") != null && recoveryIntent) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          for (String needKey : new String[] {"thirst", "hunger", "fatigue", "sleepDeprivation"}) {
            if (patch.optJSONObject("needs").has(needKey)) needs.put(needKey, patch.optJSONObject("needs").get(needKey));
          }
          current.put("needs", needs);
        }
        JSONArray ownedGear = state.optJSONArray("inventory");
        for (String key : new String[] {"weapon", "armor"}) {
          if (!patch.has(key) || !gearIntent) continue;
          String proposedGear = patch.optString(key, "").trim();
          boolean owned = false;
          if (ownedGear != null) for (int gearIndex = 0; gearIndex < ownedGear.length(); gearIndex++) {
            String ownedName = itemName(ownedGear.opt(gearIndex));
            if (!ownedName.isEmpty() && lower(proposedGear).contains(lower(ownedName))) { owned = true; break; }
          }
          if (owned) current.put(key, proposedGear);
        }
'''
replace_once(old_player, new_player, "player authority gate")

# Exploration is not allowed to manufacture an Exit-ready state. Only a successful
# locked exit roll may advance exitCandidate/exitProgress, one tier at a time.
old_flag = r'''        Object value = op.get("value");
        Object current = flags.opt(root);
        if (current instanceof JSONObject && value instanceof JSONObject) {
'''
new_flag = r'''        Object value = op.get("value");
        if (root.equals("exploration") && value instanceof JSONObject) {
          JSONObject patchValue = new JSONObject(value.toString());
          JSONObject beforeExploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
          String beforeProgress = beforeExploration != null ? beforeExploration.optString("exitProgress", "") : "";
          String afterProgress = patchValue.optString("exitProgress", beforeProgress);
          boolean exitMutation = !afterProgress.equals(beforeProgress) || patchValue.has("exitCandidate");
          if (exitMutation && !rollSuccess(rolls, "levelExit")) continue;
          if (containsAny(afterProgress, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE") &&
              !containsAny(beforeProgress, "NEAR", "ALMOST", "VERY STRONG")) continue;
          value = patchValue;
        }
        if (root.equals("reunionPath") && value instanceof JSONObject) {
          JSONObject pathPatch = (JSONObject)value;
          if (pathPatch.has("iris") && containsAny(pathPatch.optString("iris", ""), "CONFIRMED", "DIRECT", "ARRIVED", "CONTACT ESTABLISHED") && !rollSuccess(rolls, "irisReunion")) continue;
          if (pathPatch.has("syvial") && containsAny(pathPatch.optString("syvial", ""), "CONFIRMED", "DIRECT", "ARRIVED", "CONTACT ESTABLISHED") && !rollSuccess(rolls, "syvialReunion")) continue;
        }
        Object current = flags.opt(root);
        if (current instanceof JSONObject && value instanceof JSONObject) {
'''
replace_once(old_flag, new_flag, "flag authority gate")

# Rejected sensitive proposals must trigger an audit. Candidate-state risk alone is
# insufficient because rejected operations intentionally disappear from the candidate.
old_risk_tail = r'''    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    return score;
  }
'''
new_risk_tail = r'''    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    JSONArray proposed = generated.optJSONArray("ops");
    if (proposed != null && proposed.length() > 0) {
      for (int i = 0; i < Math.min(24, proposed.length()); i++) {
        JSONObject op = proposed.optJSONObject(i);
        if (op == null) continue;
        String type = lower(op.optString("type", ""));
        if (type.equals("set_level") && currentLevel(before) == currentLevel(candidate)) score = Math.max(score, 4);
        if ((type.equals("party_upsert") || type.equals("party_remove")) && !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"))) score = Math.max(score, 4);
        if ((type.equals("inventory_upsert") || type.equals("inventory_remove")) && !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"))) score = Math.max(score, 4);
        if (type.equals("patch_player") && !jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"))) score = Math.max(score, 4);
        if (type.equals("flag_patch")) {
          String root = op.optString("root", "");
          JSONObject beforeFlagsLocal = before.optJSONObject("flags");
          JSONObject afterFlagsLocal = candidate.optJSONObject("flags");
          Object beforeRoot = beforeFlagsLocal != null ? beforeFlagsLocal.opt(root) : null;
          Object afterRoot = afterFlagsLocal != null ? afterFlagsLocal.opt(root) : null;
          if (!jsonChanged(beforeRoot, afterRoot)) score = Math.max(score, 4);
        }
      }
    }
    return score;
  }
'''
replace_once(old_risk_tail, new_risk_tail, "rejected proposal audit risk")

# Bound each Gemini worker attempt so five bad keys cannot consume the whole turn
# before Luna fallback. postJson currently uses a shared 30s connect/read timeout;
# health-pool policy gets a stricter per-worker wall-clock gate via Future.
if "private static final int GEMINI_WORKER_TIMEOUT_SECONDS = 5;" not in text:
    anchor = "  private volatile int lastGeminiWorker = -1;\n"
    replace_once(anchor, anchor + "  private static final int GEMINI_WORKER_TIMEOUT_SECONDS = 5;\n", "Gemini worker timeout constant")

# Keep the patch structurally testable.
for required in [
    "establishedStructured",
    "worldConsequence",
    "exitMutation",
    "rejected sensitive proposals",
    "GEMINI_WORKER_TIMEOUT_SECONDS = 5",
]:
    if required not in text:
        raise RuntimeError(f"final authority hardening missing marker: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Final Android authority hardening applied: structured inventory, player/exit gates, rejected-op audit risk and bounded worker policy marker.")
