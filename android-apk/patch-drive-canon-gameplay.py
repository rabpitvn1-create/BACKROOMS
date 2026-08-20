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

if "END DRIVE CANON R06" not in canon or len(canon) < 5000:
    raise RuntimeError("Drive R06 canon is missing, truncated or has the wrong marker")

main = replace_once(
    main,
    "import java.util.concurrent.atomic.AtomicInteger;\n",
    "import java.util.concurrent.atomic.AtomicInteger;\nimport java.security.SecureRandom;\n",
    "SecureRandom import",
)

constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = (
    constant_anchor
    + '  private static final String DRIVE_CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06";\n'
    + f"  private static final String DRIVE_CANON = {json.dumps(canon, ensure_ascii=False)};\n"
    + "  private static final SecureRandom GAME_RNG = new SecureRandom();\n"
)
main = replace_once(main, constant_anchor, constant_block, "Drive canon Java constants")

helpers = r'''  private String lower(String value) {
    return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private boolean containsAny(String value, String... needles) {
    String text = lower(value);
    for (String needle : needles) if (text.contains(needle)) return true;
    return false;
  }

  private boolean isMetaAction(String action) {
    String text = lower(action).trim();
    if (text.startsWith("/meta") || text.startsWith("/status") || text.startsWith("/state") ||
        text.startsWith("/inventory") || text.startsWith("/party") || text.startsWith("/rule") ||
        text.startsWith("/help") || text.startsWith("/save")) return true;
    boolean asksToShow = text.startsWith("xem ") || text.startsWith("hiện ") ||
      text.startsWith("kiểm tra ") || text.startsWith("nhắc lại ") || text.startsWith("cho tôi xem ");
    return asksToShow && containsAny(text, "trạng thái", "state", "inventory", "túi đồ", "party", "đội hình", "save", "luật", "dice", "roll", "canon");
  }

  private int currentLevel(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    int number = level != null ? level.optInt("number", 0) : 0;
    return Math.max(0, Math.min(6, number));
  }

  private boolean partyHas(JSONObject state, String expected) {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return false;
    String needle = lower(expected);
    for (int i = 0; i < party.length(); i++) {
      Object member = party.opt(i);
      String name = member instanceof JSONObject ? ((JSONObject) member).optString("name", "") : String.valueOf(member);
      if (lower(name).contains(needle)) return true;
    }
    return false;
  }

  private boolean reunionEligible(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    if (record == null || !record.optBoolean("exists", false)) return false;
    String continuity = lower(record.optString("continuity", ""));
    if (containsAny(continuity, "reunited", "with kai", "together", "present")) return false;
    if (record.has("reunionEligible") && !record.optBoolean("reunionEligible", true)) return false;
    return !partyHas(state, key);
  }

  private JSONObject rollRecord(String dice, int max, int threshold, boolean eligible) throws Exception {
    JSONObject record = new JSONObject()
      .put("dice", dice)
      .put("threshold", threshold)
      .put("eligible", eligible)
      .put("chance", String.format(java.util.Locale.ROOT, threshold < 100 ? "%.4f%%" : "%.2f%%", threshold * 100.0 / max));
    if (!eligible || threshold <= 0) return record.put("raw", JSONObject.NULL).put("success", false);
    int raw = GAME_RNG.nextInt(max) + 1;
    return record.put("raw", raw).put("success", raw <= threshold);
  }

  private int exitThreshold(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) return 20;
    int explicit = flags.optInt("exitChanceThreshold", -1);
    if (explicit >= 0 && explicit <= 10000) return explicit;
    String progress = lower(flags.optString("exitProgress", ""));
    if (containsAny(progress, "ready", "guaranteed", "condition met", "transition available")) return 10000;
    if (containsAny(progress, "near", "almost", "very strong")) return 300;
    if (containsAny(progress, "strong", "correct route")) return 200;
    if (containsAny(progress, "clue", "candidate", "opened", "observed", "tracked")) return 100;
    return 20;
  }

  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("meta", meta);
    if (meta) return rolls;
    boolean physical = containsAny(action, "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm", "kiểm tra", "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo", "tiến", "lùi", "bò", "nhảy", "đào", "tháo", "đập", "vượt");
    boolean search = containsAny(action, "tìm", "lục", "khám phá", "khảo sát", "kiểm tra", "quét", "scan", "mở", "tháo", "quan sát kỹ", "rà");
    boolean water = search && containsAny(action, "nước", "water", "almond", "uống", "khát", "chai", "vòi", "hồ", "fountain");
    boolean exit = (physical || search) && containsAny(action, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "đường ra");
    int level = currentLevel(state);
    int[] hazard = {400, 700, 1000, 1200, 300, 1000, 1200};
    int[] entity = {5, 200, 350, 350, 10, 400, 5};
    int[] loot = {35, 120, 100, 150, 180, 100, 45};
    int[] waterChance = {20, 70, 35, 20, 120, 60, 35};
    JSONObject flags = state.optJSONObject("flags");
    boolean survivorAllowed = flags == null || flags.optBoolean("survivorEncountersAllowed", true);
    boolean entityAllowed = flags == null || flags.optBoolean("entityEncountersAllowed", true);
    JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
    boolean madGodAllowed = search && (madGod == null || !madGod.optBoolean("spawned", false)) &&
      (flags == null || flags.optBoolean("madGodDiscoveryAllowed", true));
    rolls.put("survivor", rollRecord("d10000", 10000, 200, survivorAllowed));
    rolls.put("irisReunion", rollRecord("d1000000", 1000000, 25, reunionEligible(state, "iris")));
    rolls.put("syvialReunion", rollRecord("d1000000", 1000000, 25, reunionEligible(state, "syvial")));
    rolls.put("hazard", rollRecord("d10000", 10000, hazard[level], physical));
    rolls.put("entityEncounter", rollRecord("d10000", 10000, entity[level], physical && entityAllowed));
    rolls.put("loot", rollRecord("d10000", 10000, loot[level], search));
    rolls.put("madGodSet", rollRecord("d10000", 10000, 1, madGodAllowed));
    rolls.put("almondWater", rollRecord("d10000", 10000, waterChance[level], water));
    rolls.put("exitProbe", rollRecord("d10000", 10000, exitThreshold(state), exit));
    return rolls;
  }

  private boolean rollSuccess(JSONObject rolls, String key) {
    JSONObject record = rolls.optJSONObject(key);
    return record != null && record.optBoolean("success", false);
  }

  private boolean canTransition(JSONObject state, JSONObject rolls) {
    if (rollSuccess(rolls, "exitProbe")) return true;
    JSONObject flags = state.optJSONObject("flags");
    return flags != null && (flags.optBoolean("transitionReady", false) || flags.optBoolean("exitReady", false));
  }

  private String itemName(Object item) {
    if (item instanceof JSONObject) return ((JSONObject) item).optString("name", "");
    return item == null ? "" : String.valueOf(item);
  }

  private boolean arrayHasName(JSONArray array, String name) {
    if (array == null) return false;
    String needle = lower(name);
    for (int i = 0; i < array.length(); i++) if (lower(itemName(array.opt(i))).equals(needle)) return true;
    return false;
  }

  private JSONArray sanitizedParty(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = new JSONArray();
    for (int i = 0; i < proposed.length(); i++) {
      Object member = proposed.opt(i);
      String name = itemName(member);
      boolean existing = arrayHasName(current, name);
      boolean allowed = existing ||
        (lower(name).contains("iris") && rollSuccess(rolls, "irisReunion")) ||
        (lower(name).contains("syvial") && rollSuccess(rolls, "syvialReunion")) ||
        (!lower(name).contains("iris") && !lower(name).contains("syvial") && rollSuccess(rolls, "survivor"));
      if (allowed) safe.put(member);
    }
    return safe;
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

  private String proposedLevel(JSONObject generated) {
    JSONObject level = generated.optJSONObject("level");
    if (level != null && level.has("number")) return String.valueOf(level.opt("number"));
    String text = generated.optString("location", "") + " " + generated.optString("title", "");
    java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?i)\\bLevel\\s+([0-9]+)").matcher(text);
    return match.find() ? match.group(1) : null;
  }

  private JSONObject sanitizedSnapshotEvent(JSONObject generated, JSONObject rolls, boolean transitionAccepted, boolean levelChanged, boolean meta) throws Exception {
    JSONObject event = generated.optJSONObject("snapshotEvent");
    if (meta || event == null || !event.optBoolean("shouldGenerate", false))
      return new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", "");
    String kind = event.optString("kind", "").toUpperCase(java.util.Locale.ROOT);
    boolean supported =
      ("LEVEL_CHANGE".equals(kind) && transitionAccepted && levelChanged) ||
      ("ENTITY_CONFIRMED".equals(kind) && rollSuccess(rolls, "entityEncounter")) ||
      ("PERSON_ENCOUNTER".equals(kind) && (rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion"))) ||
      ("SPECIAL_REGION".equals(kind) && rollSuccess(rolls, "hazard")) ||
      ("MAJOR_VISUAL_EVENT".equals(kind) && (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "madGodSet")));
    if (!supported) return new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", "");
    return new JSONObject(event.toString());
  }

  private void restoreFlag(JSONObject target, JSONObject previous, String key) throws Exception {
    if (previous != null && previous.has(key)) target.put(key, previous.get(key));
    else target.remove(key);
  }

'''

main = replace_once(main, "  private class GameBridge {\n", helpers + "  private class GameBridge {\n", "gameplay helpers")

bridge_start = main.index("  private class GameBridge {\n")
bridge_end = main.index("\n  private static class SnapshotImage", bridge_start)
new_bridge = r'''  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject state = new JSONObject(stateJson);
          JSONObject before = new JSONObject(state.toString());
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, action, meta);
          String prompt = "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
            "Viết tiếng Việt tự nhiên, đầy đủ ý. Không trả lời bằng câu rỗng. Không thay đổi dữ kiện chưa có căn cứ. Người chơi chỉ điều khiển Kai Akechi. " +
            "DRIVE_CANON và KAI_CANON là HARD LOCK. GAMEPLAY_ROLLS do lớp Android sinh là kết quả cuối: không reroll, không thay success/raw/chance, không bù một kết quả thất bại bằng biến cố tương đương. Không nhắc roll hoặc canon trong văn xuôi. " +
            "Nếu meta=true, chỉ trả thông tin được hỏi; không tạo biến cố, không đổi state và snapshotEvent phải false. " +
            "Snapshot chỉ được yêu cầu khi chính lượt này tạo mốc hình ảnh mới đã được roll/state cho phép: chuyển Level; vùng đặc biệt rõ rệt; Entity xác nhận; gặp người; hoặc sự kiện lớn hiếm. Khi phân vân, false. " +
            "Trường level chỉ đổi khi exitProbe success hoặc state đã transitionReady/exitReady. Reunion, survivor, Entity, loot, Almond Water và MadGod chỉ xuất hiện khi roll tương ứng success. MadGod success chỉ mở đường/vị trí khám phá, không đặt vật vào inventory.\n\n" +
            DRIVE_CANON + "\n\nKAI CANON:\n" + KAI_CANON +
            "\n\nGAMEPLAY_ROLLS: " + rolls.toString() +
            "\nState hiện tại: " + before.toString() + "\nHành động: " + action +
            "\nJSON bắt buộc: {\"reply\":\"phản hồi Game Master\",\"title\":\"giữ nguyên hoặc cập nhật\",\"location\":\"vị trí sau lượt\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
          JSONObject generated = parseModelJson(generateText(prompt));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");

          boolean transitionAllowed = canTransition(before, rolls);
          String oldLevel = String.valueOf(currentLevel(before));
          String requestedLevel = proposedLevel(generated);
          boolean levelChanged = requestedLevel != null && !oldLevel.equals(requestedLevel);
          boolean transitionAccepted = !levelChanged || transitionAllowed;

          if (!meta) {
            state.put("turn", state.optInt("turn", 1) + 1).put("mode", "ai · canon R06");
            String title = generated.optString("title", "").trim();
            String location = generated.optString("location", "").trim();
            if (transitionAccepted) {
              if (!title.isEmpty()) state.put("title", title);
              if (!location.isEmpty()) state.put("location", location);
              if (generated.optJSONObject("level") != null) state.put("level", generated.optJSONObject("level"));
            }
            if (generated.optJSONObject("player") != null) {
              JSONObject player = new JSONObject(generated.optJSONObject("player").toString());
              JSONObject oldPlayer = before.optJSONObject("player");
              player.put("name", oldPlayer != null ? oldPlayer.optString("name", "Kai Akechi") : "Kai Akechi");
              state.put("player", player);
            }
            state.put("party", sanitizedParty(before.optJSONArray("party"), generated.optJSONArray("party"), rolls));
            state.put("inventory", sanitizedInventory(before.optJSONArray("inventory"), generated.optJSONArray("inventory"), rolls));

            JSONObject oldFlags = before.optJSONObject("flags");
            JSONObject flags = oldFlags == null ? new JSONObject() : new JSONObject(oldFlags.toString());
            if (generated.optJSONObject("flags") != null) mergeObject(flags, generated.optJSONObject("flags"));
            boolean irisPreviouslyPresent = partyHas(before, "iris");
            boolean syvialPreviouslyPresent = partyHas(before, "syvial");
            if (!irisPreviouslyPresent && !rollSuccess(rolls, "irisReunion")) restoreFlag(flags, oldFlags, "iris");
            if (!syvialPreviouslyPresent && !rollSuccess(rolls, "syvialReunion")) restoreFlag(flags, oldFlags, "syvial");
            if (!rollSuccess(rolls, "entityEncounter")) {
              restoreFlag(flags, oldFlags, "entity");
              restoreFlag(flags, oldFlags, "entityEncounter");
              restoreFlag(flags, oldFlags, "encounter");
            }
            JSONObject oldMadGod = oldFlags != null ? oldFlags.optJSONObject("madGod") : null;
            JSONObject madGod = oldMadGod == null ? new JSONObject() : new JSONObject(oldMadGod.toString());
            if (oldMadGod != null && oldMadGod.optBoolean("spawned", false)) madGod.put("spawned", true);
            else if (rollSuccess(rolls, "madGodSet")) madGod.put("spawned", true).put("discoveryRouteRevealed", true).put("acquired", false);
            else madGod.put("spawned", false);
            flags.put("madGod", madGod).put("lastRolls", rolls);
            state.put("flags", flags);
            state.put("_snapshotEvent", sanitizedSnapshotEvent(generated, rolls, transitionAccepted, levelChanged, false));
          } else {
            state.put("_snapshotEvent", new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", ""));
          }

          state.put("canonVersion", DRIVE_CANON_VERSION);
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
    'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;let busy=false;',
    'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;state.canonVersion="NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06";state.flags=state.flags||{};state.flags.iris=state.flags.iris||{exists:true,continuity:"SEPARATED",reunionEligible:true};state.flags.syvial=state.flags.syvial||{exists:true,continuity:"SEPARATED",reunionEligible:true};state.flags.madGod=state.flags.madGod||{spawned:false,acquired:false};let busy=false;',
    "existing save migration",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(f"Injected Drive R06 canon and Android-authoritative gameplay gates ({len(canon)} chars).")
