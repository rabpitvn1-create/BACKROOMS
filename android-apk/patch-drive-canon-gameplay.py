from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
CANON = ROOT / "drive-canon.txt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")
canon = CANON.read_text(encoding="utf-8").strip()

if "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON" not in canon:
    raise RuntimeError("Drive canon: wrong or missing R06 source marker")
if len(canon) < 5000:
    raise RuntimeError(f"Drive canon unexpectedly short: {len(canon)} chars")

for old, new, label in (
    ("Kênh nội bộ Black Blood im lặng.", "Kênh nội bộ SRU Force im lặng.", "prologue internal channel"),
    ("Không biết Black Blood còn có thể tìm thấy dấu vết của ba người từ phía bên kia hay không.", "Không biết SRU Force còn có thể tìm thấy dấu vết của ba người từ phía bên kia hay không.", "prologue recovery question"),
    ("Không có liên lạc với Iris, Syvial hay Black Blood.", "Không có liên lạc với Iris, Syvial hay SRU Force.", "prologue first turn status"),
):
    count = index.count(old)
    if count > 1:
        raise RuntimeError(f"{label}: expected at most 1 legacy match, found {count}")
    if count == 1:
        index = index.replace(old, new, 1)

# Source-clean builds no longer contain the legacy restaurant/Black Blood prologue,
# so the compatibility rewrite above is intentionally allowed to be a no-op.
prologue_start = index.find("const prologue=`")
initial_start = index.find("const initial={", prologue_start)
if prologue_start < 0 or initial_start < 0:
    raise RuntimeError("Drive canon: prologue boundary missing")
prologue_block = index[prologue_start:initial_start]
if "Black Blood" in prologue_block:
    raise RuntimeError("Drive canon: legacy Black Blood prologue survived")

java_canon = json.dumps(canon, ensure_ascii=False)
constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = constant_anchor + (
    '  private static final String DRIVE_CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06";\n'
    f"  private static final String DRIVE_CANON = {java_canon};\n"
    "  private static final SecureRandom GAME_RNG = new SecureRandom();\n"
)
main = replace_once(main, constant_anchor, constant_block, "Drive canon Java constants")

helper_anchor = "  private class GameBridge {\n"
helpers = r'''  private int currentLevel(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null) return Math.max(0, Math.min(6, level.optInt("number", 0)));
    String title = state.optString("title", "");
    for (int n = 0; n <= 6; n++) if (title.contains("Level " + n)) return n;
    return 0;
  }

  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
    JSONObject result = new JSONObject().put("label", label).put("eligible", eligible).put("chancePercent", chance);
    if (!eligible) return result.put("success", false).put("roll", JSONObject.NULL);
    int roll = GAME_RNG.nextInt(100) + 1;
    return result.put("roll", roll).put("success", roll <= chance);
  }

  private boolean containsAny(String text, String... terms) {
    String value = lower(text);
    for (String term : terms) if (value.contains(lower(term))) return true;
    return false;
  }

  private boolean partyHas(JSONObject state, String needle) {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return false;
    for (int i = 0; i < party.length(); i++) {
      Object item = party.opt(i);
      String name = item instanceof JSONObject ? ((JSONObject)item).optString("name", "") : String.valueOf(item);
      if (lower(name).contains(lower(needle))) return true;
    }
    return false;
  }

  private boolean flagSpawned(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject value = flags != null ? flags.optJSONObject(key) : null;
    return value != null && (value.optBoolean("spawned", false) || value.optBoolean("present", false));
  }

  private boolean isMetaAction(String action) {
    return containsAny(action,
      "xem trạng thái", "trạng thái hiện tại", "xem state", "xem inventory", "xem túi", "kiểm tra inventory",
      "xem party", "xem nhân vật", "xem thuộc tính", "status", "show state", "show inventory", "show party");
  }

  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    int level = currentLevel(state);
    String a = lower(action);
    boolean search = containsAny(a, "tìm", "lục", "khám phá", "quan sát", "kiểm tra", "mở", "search", "loot", "crate");
    boolean water = containsAny(a, "nước", "almond", "chai", "đồ uống", "drink", "liquid");
    boolean encounter = containsAny(a, "đi tiếp", "rẽ", "hành lang", "cửa", "phòng", "khám phá", "tiến", "đi vào", "move", "explore");
    boolean exitIntent = containsAny(a, "lối ra", "thoát", "exit", "chuyển level", "sang level", "đi tiếp level");

    rolls.put("loot", rollSpec("loot", 32, search));
    rolls.put("almondWater", rollSpec("almondWater", 24, search && water));
    rolls.put("entityEncounter", rollSpec("entityEncounter", 28, encounter));
    rolls.put("survivor", rollSpec("survivor", 12, encounter));

    JSONObject flags = state.optJSONObject("flags");
    JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
    boolean madGodAlready = madGod != null && madGod.optBoolean("spawned", false);
    rolls.put("madGodSet", rollSpec("madGodSet", 1, search && !madGodAlready));

    boolean irisPresent = partyHas(state, "iris") || flagSpawned(state, "iris");
    boolean syvialPresent = partyHas(state, "syvial") || flagSpawned(state, "syvial");
    rolls.put("irisReunion", rollSpec("irisReunion", 4, encounter && !irisPresent));
    rolls.put("syvialReunion", rollSpec("syvialReunion", 4, encounter && !syvialPresent));

    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    boolean deterministicExit = exitIntent && confirmedExit != null && !confirmedExit.trim().isEmpty();
    rolls.put("levelExit", rollSpec("levelExit", 18, exitIntent && !deterministicExit));
    if (deterministicExit) {
      rolls.put("levelExit", new JSONObject()
        .put("label", "levelExit")
        .put("eligible", true)
        .put("chancePercent", 100)
        .put("roll", 1)
        .put("success", true)
        .put("deterministic", true)
        .put("basis", confirmedExit));
    }
    return rolls;
  }

  private boolean rollSuccess(JSONObject rolls, String key) {
    JSONObject roll = rolls.optJSONObject(key);
    return roll != null && roll.optBoolean("success", false);
  }

  private String itemName(Object item) {
    if (item instanceof JSONObject) return ((JSONObject)item).optString("name", "");
    return item == null ? "" : String.valueOf(item);
  }

  private boolean arrayHasName(JSONArray array, String name) {
    if (array == null || name == null) return false;
    String target = lower(name).trim();
    for (int i = 0; i < array.length(); i++) if (lower(itemName(array.opt(i))).trim().equals(target)) return true;
    return false;
  }

  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = new JSONArray();
    for (int i = 0; i < proposed.length(); i++) {
      Object item = proposed.opt(i);
      String name = itemName(item);
      boolean existing = arrayHasName(current, name);
      boolean madGod = lower(name).contains("madgod");
      boolean almond = lower(name).contains("almond water");
      boolean allowed = existing || (!madGod && almond && rollSuccess(rolls, "almondWater")) ||
        (!madGod && !almond && rollSuccess(rolls, "loot"));
      if (allowed) safe.put(item);
    }
    return safe;
  }

  private JSONArray sanitizedParty(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = current == null ? new JSONArray() : new JSONArray(current.toString());
    for (int i = 0; i < proposed.length(); i++) {
      Object member = proposed.opt(i);
      String name = itemName(member);
      if (arrayHasName(safe, name)) continue;
      String lowered = lower(name);
      boolean allowed = (lowered.contains("iris") && rollSuccess(rolls, "irisReunion")) ||
        (lowered.contains("syvial") && rollSuccess(rolls, "syvialReunion")) ||
        (!lowered.contains("iris") && !lowered.contains("syvial") && rollSuccess(rolls, "survivor"));
      if (allowed) safe.put(member);
    }
    return safe;
  }

  private void mergeObject(JSONObject target, JSONObject patch) throws Exception {
    if (patch == null) return;
    for (String key : JSONObject.getNames(patch) == null ? new String[0] : JSONObject.getNames(patch)) {
      Object value = patch.opt(key);
      if (value instanceof JSONObject && target.opt(key) instanceof JSONObject) mergeObject(target.optJSONObject(key), (JSONObject)value);
      else target.put(key, value);
    }
  }

  private boolean canTransition(JSONObject before, JSONObject rolls) {
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    return (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
  }

  private JSONObject sanitizedFlags(JSONObject current, JSONObject proposed, JSONObject rolls, boolean transitionAccepted) throws Exception {
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    if (proposed == null) return safe;
    JSONObject patch = new JSONObject(proposed.toString());
    patch.remove("lastRolls");
    if (!transitionAccepted) patch.remove("currentLevel");

    if (patch.optJSONObject("madGod") != null) {
      JSONObject oldMadGod = safe.optJSONObject("madGod");
      JSONObject newMadGod = patch.optJSONObject("madGod");
      if ((oldMadGod == null || !oldMadGod.optBoolean("spawned", false)) && newMadGod.optBoolean("spawned", false) && !rollSuccess(rolls, "madGodSet")) {
        patch.remove("madGod");
      }
    }
    if (patch.optJSONObject("iris") != null) {
      JSONObject oldIris = safe.optJSONObject("iris");
      JSONObject newIris = patch.optJSONObject("iris");
      if ((oldIris == null || !oldIris.optBoolean("present", false)) && newIris.optBoolean("present", false) && !rollSuccess(rolls, "irisReunion")) patch.remove("iris");
    }
    if (patch.optJSONObject("syvial") != null) {
      JSONObject oldSyvial = safe.optJSONObject("syvial");
      JSONObject newSyvial = patch.optJSONObject("syvial");
      if ((oldSyvial == null || !oldSyvial.optBoolean("present", false)) && newSyvial.optBoolean("present", false) && !rollSuccess(rolls, "syvialReunion")) patch.remove("syvial");
    }
    mergeObject(safe, patch);
    return safe;
  }

  private JSONObject sanitizedPlayer(JSONObject current, JSONObject proposed) throws Exception {
    if (proposed == null) return current == null ? new JSONObject() : new JSONObject(current.toString());
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    String name = safe.optString("name", "Kai Akechi");
    String codename = safe.optString("codename", "Twilight");
    for (String key : new String[] {"hp", "condition", "needs", "weapon", "armor"}) if (proposed.has(key)) safe.put(key, proposed.get(key));
    safe.put("name", name).put("codename", codename);
    return safe;
  }

  private JSONObject sanitizedSnapshotEvent(JSONObject generated, JSONObject rolls, boolean transitionAccepted, boolean levelChanged, boolean meta) throws Exception {
    JSONObject event = generated.optJSONObject("snapshotEvent");
    JSONObject safe = new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", "");
    if (meta || event == null || !event.optBoolean("shouldGenerate", false)) return safe;
    String kind = lower(event.optString("kind", ""));
    boolean allowed = false;
    if (kind.equals("level_transition")) allowed = transitionAccepted && levelChanged;
    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");
    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");
    else if (kind.equals("major_event")) allowed = rollSuccess(rolls, "madGodSet");
    else if (kind.equals("special_area")) allowed = true;
    if (!allowed) return safe;
    return new JSONObject().put("shouldGenerate", true).put("kind", kind).put("reason", event.optString("reason", ""));
  }

'''
main = replace_once(main, helper_anchor, helpers + helper_anchor, "gameplay helper injection")

bridge_start = main.index("  private class GameBridge {\n")
bridge_end = main.index("\n  private static class SnapshotImage", bridge_start)
new_bridge = r'''  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, action, meta);
          String prompt = "Bạn là Game Master duy nhất của text game Backrooms, phát ngôn như người kể chuyện trong game. Trả DUY NHẤT một JSON hợp lệ, không markdown. " +
            "Canon R06 dưới đây là HARD LOCK; state hiện tại là source of truth cho continuity đang sống. UNKNOWN phải giữ UNKNOWN. Không tự lấp chỗ trống canon. " +
            "Người chơi chỉ điều khiển hành động có chủ ý của Kai; Game Master không tự quyết lựa chọn thay Kai. " +
            "GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +
            "Inventory chỉ được thêm vật đã tồn tại trong state/cảnh và thực sự được Kai nhặt/lấy/nhận/cất, hoặc kết quả loot hợp lệ. Nhìn thấy không đồng nghĩa sở hữu. " +
            "MadGod Set success chỉ mở đường/vị trí khám phá; acquired mặc định false cho tới khi Kai thực sự tiếp cận và lấy. " +
            "Nếu meta=true, chỉ trả thông tin được hỏi; không tạo biến cố, không đổi state và snapshotEvent phải false. " +
            "Không nhắc tới canon, state, roll, API hoặc prompt trong lời kể.\n\n" +
            "DRIVE CANON:\n" + DRIVE_CANON + "\n\n" +
            "State hiện tại: " + state.toString() + "\nHành động: " + action +
            "\nGAMEPLAY_ROLLS: " + rolls.toString() +
            "\nJSON schema bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt\",\"title\":\"tên khu vực\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"location\":\"vị trí hiện tại\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
          JSONObject generated = parseModelJson(generateText(prompt));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) reply = "Kai giữ nguyên vị trí và quan sát thêm; chưa có kết quả đủ chắc chắn để thay đổi trạng thái.";

          JSONObject state;
          if (meta) {
            state = new JSONObject(before.toString());
          } else {
            state = new JSONObject(before.toString());
            JSONObject proposedLevel = generated.optJSONObject("level");
            int oldLevel = currentLevel(before);
            int proposedLevelNumber = proposedLevel != null ? Math.max(0, Math.min(6, proposedLevel.optInt("number", oldLevel))) : oldLevel;
            boolean levelChanged = proposedLevelNumber != oldLevel;
            boolean transitionAccepted = !levelChanged || canTransition(before, rolls);
            if (transitionAccepted && proposedLevel != null) state.put("level", proposedLevel);
            if (transitionAccepted && generated.has("title")) state.put("title", generated.optString("title", before.optString("title")));
            if (generated.has("location")) state.put("location", generated.optString("location", before.optString("location")));
            state.put("player", sanitizedPlayer(before.optJSONObject("player"), generated.optJSONObject("player")));
            state.put("party", sanitizedParty(before.optJSONArray("party"), generated.optJSONArray("party"), rolls));
            state.put("inventory", sanitizedInventory(before.optJSONArray("inventory"), generated.optJSONArray("inventory"), rolls));
            state.put("flags", sanitizedFlags(before.optJSONObject("flags"), generated.optJSONObject("flags"), rolls, transitionAccepted));
            state.put("_snapshotEvent", sanitizedSnapshotEvent(generated, rolls, transitionAccepted, levelChanged, false));
            state.put("turn", before.optInt("turn", 1) + 1).put("mode", "ai · canon R06");
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            flags.put("lastRolls", rolls);
            state.put("flags", flags);
          }

          state.put("canonVersion", DRIVE_CANON_VERSION);
          if (meta) state.put("_snapshotEvent", new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", ""));
          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          log.put(new JSONObject().put("role", "gm").put("text", reply));
          state.put("log", log);
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void requestSnapshot(String stateJson) {
      imageIo.execute(() -> requestSnapshotInternal(stateJson));
    }
  }
'''
main = main[:bridge_start] + new_bridge + main[bridge_end:]

index = replace_once(
    index,
    'const initial={title:"Level 0 – The Lobby",',
    'const initial={canonVersion:"NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06",title:"Level 0 – The Lobby",',
    "initial canon version",
)
index = replace_once(
    index,
    'mode:"local APK",',
    'mode:"local APK · canon R06",',
    "initial mode",
)
index = replace_once(
    index,
    'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"}},',
    'flags:{communication:{blackBlood:"OFFLINE",iris:"OFFLINE",syvial:"OFFLINE"},iris:{exists:true,continuity:"SEPARATED",reunionEligible:true},syvial:{exists:true,continuity:"SEPARATED",reunionEligible:true},madGod:{spawned:false,acquired:false}},',
    "initial continuity flags",
)
index = replace_once(
    index,
    'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;',
    'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;state.canonVersion="NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06";state.flags=state.flags||{};state.flags.iris=state.flags.iris||{exists:true,continuity:"SEPARATED",reunionEligible:true};state.flags.syvial=state.flags.syvial||{exists:true,continuity:"SEPARATED",reunionEligible:true};state.flags.madGod=state.flags.madGod||{spawned:false,acquired:false};',
    "existing save migration",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(f"Injected Drive R06 canon and Android-authoritative gameplay gates ({len(canon)} chars).")