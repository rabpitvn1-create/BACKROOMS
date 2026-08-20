from pathlib import Path


ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

helpers = r'''  private String canonSection(String source, String start, String end) {
    if (source == null || start == null) return "";
    int from = source.indexOf(start);
    if (from < 0) return "";
    int to = end == null ? -1 : source.indexOf(end, from + start.length());
    return source.substring(from, to >= 0 ? to : source.length()).trim();
  }

  private String canonLineStarting(String source, String prefix) {
    if (source == null || prefix == null) return "";
    String[] lines = source.split("\\n");
    for (String line : lines) if (line.trim().startsWith(prefix)) return line.trim();
    return "";
  }

  private boolean actionDialogue(String action) {
    return containsAny(action, "hỏi", "nói", "trả lời", "gọi", "bảo", "thuyết phục", "xin lỗi", "cảm ơn", "talk", "ask", "tell");
  }

  private boolean actionCombat(String action) {
    return containsAny(action, "bắn", "đánh", "đấm", "đá", "tấn công", "phản công", "né", "chiến đấu", "devil trigger", "guilty crown", "white wraith", "magnum", "talon", "phantom", "shoot", "attack", "fight");
  }

  private boolean actionOmnivault(String action) {
    return containsAny(action, "omnivault", "nhẫn vạn tàng", "scan", "copy", "restore", "upgrade", "hoàn nguyên", "nâng cấp", "sao chép", "quét");
  }

  private boolean actionItem(String action) {
    return actionOmnivault(action) || containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "nhận", "cất", "inventory", "đồ", "vật phẩm", "chai", "nước", "almond", "loot", "crate", "liquid pain", "greek fire", "madgod");
  }

  private boolean actionEntity(String action) {
    return containsAny(action, "entity", "hound", "clump", "duller", "deathmoth", "faceling", "smiler", "skin-stealer", "skin stealer", "beast", "wretch", "cable mimic", "jeff", "quái", "thực thể", "sinh vật", "kẻ săn");
  }

  private boolean presentCharacter(JSONObject state, String key) {
    if (partyHas(state, key)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    String continuity = record != null ? lower(record.optString("continuity", "")) : "";
    return containsAny(continuity, "reunited", "with kai", "together", "present");
  }

  private String compactDriveCanon(JSONObject state, String action, JSONObject rolls) {
    StringBuilder out = new StringBuilder();
    String scope = canonSection(DRIVE_CANON, "PHẠM VI", "VĂN PHONG VÀ KINH DỊ");
    String writing = canonSection(DRIVE_CANON, "VĂN PHONG VÀ KINH DỊ", "THẾ GIỚI");
    String world = canonSection(DRIVE_CANON, "THẾ GIỚI", "LEVEL 0–6");
    String gameplay = canonSection(DRIVE_CANON, "GAMEPLAY HARD LOCK", "END DRIVE CANON R06");
    String levelLine = canonLineStarting(DRIVE_CANON, "- Level " + currentLevel(state) + " /");
    out.append(scope).append("\n\n").append(writing).append("\n\n").append(world);
    if (!levelLine.isEmpty()) out.append("\n\nCURRENT LEVEL HARD CANON\n").append(levelLine);

    boolean entity = actionEntity(action) || rollSuccess(rolls, "entityEncounter") ||
      (state.optJSONObject("flags") != null && state.optJSONObject("flags").optInt("entitiesConfirmedLocal", 0) > 0);
    boolean item = actionItem(action) || rollSuccess(rolls, "loot") || rollSuccess(rolls, "almondWater") || rollSuccess(rolls, "madGodSet");
    if (entity || item) {
      String resources = canonSection(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "IRIS / SYVIAL");
      if (!resources.isEmpty()) out.append("\n\n").append(resources);
    }

    boolean character = actionDialogue(action) || presentCharacter(state, "iris") || presentCharacter(state, "syvial") ||
      rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");
    if (character) {
      String characterCanon = canonSection(DRIVE_CANON, "IRIS / SYVIAL", "GAMEPLAY HARD LOCK");
      if (!characterCanon.isEmpty()) out.append("\n\n").append(characterCanon);
    } else {
      out.append("\n\nIRIS / SYVIAL SEPARATION KERNEL\n- Khi continuity còn SEPARATED, Kai không biết vị trí/tình trạng hiện tại của Iris hoặc Syvial và không được dùng dữ kiện hậu trường về họ.");
    }
    out.append("\n\n").append(gameplay);
    return out.toString();
  }

  private String compactKaiCanon(String action) {
    StringBuilder out = new StringBuilder();
    out.append(canonSection(KAI_CANON, "1. ĐỊNH DANH", "2. NGOẠI HÌNH"));
    out.append("\n\n").append(canonSection(KAI_CANON, "3. TÍNH CÁCH / NGUYÊN TẮC", "4. PHONG CÁCH GIAO TIẾP"));
    out.append("\n\n").append(canonSection(KAI_CANON, "4. PHONG CÁCH GIAO TIẾP", "5. NĂNG LỰC CHIẾN ĐẤU"));
    out.append("\n\n").append(canonSection(KAI_CANON, "5. NĂNG LỰC CHIẾN ĐẤU", "6. SPARDA CORE"));
    out.append("\n\n").append(canonSection(KAI_CANON, "6. SPARDA CORE", "7. DEVIL TRIGGER"));
    out.append("\n\n").append(canonSection(KAI_CANON, "10. BLACKBLOOD ARMOR & MODULES", "11. OMNIVAULT RING / NHẪN VẠN TÀNG"));
    out.append("\n\n").append(canonSection(KAI_CANON, "13. GIỚI HẠN THỰC SỰ", "14. ACTION LOCKS / CẤM MODEL TỰ BỊA"));
    out.append("\n\n").append(canonSection(KAI_CANON, "14. ACTION LOCKS / CẤM MODEL TỰ BỊA", "END OF KAI OPERATIONAL CODEX"));
    if (actionCombat(action)) {
      out.append("\n\n").append(canonSection(KAI_CANON, "7. DEVIL TRIGGER", "10. BLACKBLOOD ARMOR & MODULES"));
      out.append("\n\n").append(canonSection(KAI_CANON, "12. PHONG CÁCH CHIẾN ĐẤU", "13. GIỚI HẠN THỰC SỰ"));
    }
    if (actionOmnivault(action) || actionItem(action)) {
      out.append("\n\n").append(canonSection(KAI_CANON, "11. OMNIVAULT RING / NHẪN VẠN TÀNG", "12. PHONG CÁCH CHIẾN ĐẤU"));
    }
    return out.toString();
  }

  private JSONObject compactStateForPrompt(JSONObject state) throws Exception {
    JSONObject compact = new JSONObject(state.toString());
    compact.remove("snapshotUrl");
    compact.remove("_snapshotEvent");
    JSONArray log = state.optJSONArray("log");
    if (log != null) {
      JSONArray recent = new JSONArray();
      int start = Math.max(0, log.length() - 6);
      for (int i = start; i < log.length(); i++) recent.put(log.get(i));
      compact.put("log", recent);
    }
    return compact;
  }

  private int arrayIndexByName(JSONArray array, String name) {
    if (array == null || name == null) return -1;
    String needle = lower(name).trim();
    for (int i = 0; i < array.length(); i++) {
      if (lower(itemName(array.opt(i))).trim().equals(needle)) return i;
    }
    return -1;
  }

  private String levelName(int number) {
    String[] names = {"The Lobby", "Parking Zone", "Pipe Dreams", "The Electrical Station", "The Abandoned Office", "Terror Hotel", "Lights Out"};
    int safe = Math.max(0, Math.min(6, number));
    return names[safe];
  }

  private boolean acquisitionIntent(String action) {
    return containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "tịch thu", "nhận", "cất", "bỏ vào", "đưa vào omnivault", "store", "sao chép", "copy");
  }

  private boolean removalIntent(String action) {
    return containsAny(action, "trao", "đưa cho", "vứt", "bỏ lại", "ném", "uống", "tiêu thụ", "dùng hết", "phá hủy", "làm mất", "mất ");
  }

  private boolean characterAddAllowed(JSONObject before, String name, JSONObject rolls) {
    String value = lower(name);
    if (value.contains("iris")) return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion");
    if (value.contains("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    return rollSuccess(rolls, "survivor");
  }

  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls) {
    if (root == null) return false;
    if (root.equals("exploration") || root.equals("communication") || root.equals("omnivault") || root.equals("visualAreaKey") ||
        root.equals("visualEventKey") || root.equals("reunionPath")) return true;
    if (root.equals("iris")) return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion");
    if (root.equals("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    if (root.equals("jeff") || root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
    if (root.equals("survivorRegistry") || root.equals("survivorsConfirmed")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "survivor") || (flags != null && flags.optInt("survivorsConfirmed", 0) > 0);
    }
    if (root.equals("madGod")) {
      JSONObject flags = before.optJSONObject("flags");
      JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
      return rollSuccess(rolls, "madGodSet") || (madGod != null && madGod.optBoolean("spawned", false));
    }
    return false;
  }

  private JSONObject applyModelOperations(JSONObject before, JSONArray ops, JSONObject rolls, String action) throws Exception {
    JSONObject state = new JSONObject(before.toString());
    if (ops == null) return state;
    int limit = Math.min(24, ops.length());
    for (int i = 0; i < limit; i++) {
      JSONObject op = ops.optJSONObject(i);
      if (op == null) continue;
      String type = lower(op.optString("type", "")).trim();

      if (type.equals("set_location")) {
        String value = op.optString("value", "").trim();
        if (!value.isEmpty() && value.length() <= 700) state.put("location", value);
        continue;
      }

      if (type.equals("set_level")) {
        JSONObject level = op.optJSONObject("level");
        if (level == null || !canTransition(before, rolls)) continue;
        int number = Math.max(0, Math.min(6, level.optInt("number", currentLevel(before))));
        if (number == currentLevel(before)) continue;
        JSONObject safeLevel = new JSONObject().put("number", number).put("name", levelName(number));
        state.put("level", safeLevel).put("title", "Level " + number + " – " + levelName(number));
        continue;
      }

      if (type.equals("patch_player")) {
        JSONObject patch = op.optJSONObject("patch");
        if (patch == null) continue;
        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        for (String key : new String[] {"hp", "condition", "weapon", "armor"}) if (patch.has(key)) current.put(key, patch.get(key));
        if (patch.optJSONObject("needs") != null) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          mergeObject(needs, patch.optJSONObject("needs"));
          current.put("needs", needs);
        }
        JSONObject oldPlayer = before.optJSONObject("player");
        current.put("name", oldPlayer != null ? oldPlayer.optString("name", "Kai Akechi") : "Kai Akechi");
        if (oldPlayer != null && oldPlayer.has("codename")) current.put("codename", oldPlayer.get("codename"));
        state.put("player", current);
        continue;
      }

      if (type.equals("inventory_upsert")) {
        JSONObject item = op.optJSONObject("item");
        if (item == null) continue;
        String name = item.optString("name", "").trim();
        if (name.isEmpty()) continue;
        JSONArray inventory = state.optJSONArray("inventory");
        if (inventory == null) inventory = new JSONArray();
        int existing = arrayIndexByName(inventory, name);
        boolean madGod = lower(name).contains("madgod");
        boolean almond = lower(name).contains("almond water");
        boolean allowedNew = acquisitionIntent(action);
        if (madGod && !before.optJSONObject("flags").optJSONObject("madGod").optBoolean("spawned", false)) allowedNew = false;
        if (almond) {
          JSONObject waterRoll = rolls.optJSONObject("almondWater");
          if (waterRoll != null && waterRoll.optBoolean("eligible", false) && !waterRoll.optBoolean("success", false) && existing < 0) allowedNew = false;
        }
        if (existing >= 0) inventory.put(existing, new JSONObject(item.toString()));
        else if (allowedNew) inventory.put(new JSONObject(item.toString()));
        state.put("inventory", inventory);
        continue;
      }

      if (type.equals("inventory_remove")) {
        String name = op.optString("name", "").trim();
        JSONArray inventory = state.optJSONArray("inventory");
        int existing = arrayIndexByName(inventory, name);
        boolean consequence = "world_consequence".equals(lower(op.optString("basis", ""))) &&
          (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter"));
        if (inventory != null && existing >= 0 && (removalIntent(action) || consequence)) inventory.remove(existing);
        continue;
      }

      if (type.equals("party_upsert")) {
        JSONObject member = op.optJSONObject("member");
        if (member == null) continue;
        String name = member.optString("name", "").trim();
        if (name.isEmpty()) continue;
        JSONArray party = state.optJSONArray("party");
        if (party == null) party = new JSONArray();
        int existing = arrayIndexByName(party, name);
        if (existing >= 0) party.put(existing, new JSONObject(member.toString()));
        else if (characterAddAllowed(before, name, rolls)) party.put(new JSONObject(member.toString()));
        state.put("party", party);
        continue;
      }

      if (type.equals("party_remove")) {
        String name = op.optString("name", "").trim();
        JSONArray party = state.optJSONArray("party");
        int existing = arrayIndexByName(party, name);
        if (party != null && existing >= 0 && containsAny(action, "rời", "tách", "ở lại", "đuổi", "chia nhóm", "mất dấu")) party.remove(existing);
        continue;
      }

      if (type.equals("flag_patch")) {
        String root = op.optString("root", "").trim();
        if (!flagRootAllowed(before, root, rolls) || !op.has("value")) continue;
        JSONObject flags = state.optJSONObject("flags");
        if (flags == null) flags = new JSONObject();
        Object value = op.get("value");
        Object current = flags.opt(root);
        if (current instanceof JSONObject && value instanceof JSONObject) {
          JSONObject merged = new JSONObject(current.toString());
          mergeObject(merged, (JSONObject) value);
          flags.put(root, merged);
        } else {
          flags.put(root, value);
        }
        state.put("flags", flags);
      }
    }

    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject oldFlags = before.optJSONObject("flags");
    JSONObject oldMadGod = oldFlags != null ? oldFlags.optJSONObject("madGod") : null;
    JSONObject madGod = flags.optJSONObject("madGod");
    if (madGod == null) madGod = oldMadGod == null ? new JSONObject() : new JSONObject(oldMadGod.toString());
    if (oldMadGod != null && oldMadGod.optBoolean("spawned", false)) madGod.put("spawned", true);
    else if (rollSuccess(rolls, "madGodSet")) madGod.put("spawned", true).put("discoveryRouteRevealed", true).put("acquired", false);
    flags.put("madGod", madGod).put("lastRolls", rolls);
    state.put("flags", flags);
    return state;
  }

'''

anchor = "  private class GameBridge {\n"
if main.count(anchor) != 1:
    raise RuntimeError(f"GameBridge anchor expected once, found {main.count(anchor)}")
main = main.replace(anchor, helpers + anchor, 1)

bridge_start = main.index(anchor)
bridge_end = main.index("\n  private static class SnapshotImage", bridge_start)
new_bridge = r'''  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, action, meta);
          JSONObject promptState = compactStateForPrompt(before);
          String drivePacket = compactDriveCanon(before, action, rolls);
          String kaiPacket = compactKaiCanon(action);

          String prompt = "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
            "Canon packet bên dưới là HARD LOCK đã được router chọn theo dependency của lượt này. State là source of truth động. UNKNOWN phải giữ UNKNOWN. " +
            "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +
            "Bạn KHÔNG được trả state hoàn chỉnh. Chỉ đề xuất state change bằng ops; Android sẽ kiểm và có thể từ chối từng operation. " +
            "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc canon/state/roll/prompt trong văn xuôi.\n\n" +
            "CANON PACKET:\n" + drivePacket +
            "\n\nKAI PACKET:\n" + kaiPacket +
            "\n\nCURRENT STATE (RECENT LOG ONLY):\n" + promptState.toString() +
            "\n\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
            "\n\nPLAYER INPUT:\n" + action +
            "\n\nOPERATION TYPES: " +
            "set_location{value}; set_level{level}; patch_player{patch}; inventory_upsert{item,basis}; inventory_remove{name,basis}; " +
            "party_upsert{member}; party_remove{name}; flag_patch{root,value}. " +
            "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, madGod, omnivault, survivorRegistry, entityRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +
            "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +
            "JSON bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt tự nhiên\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";

          JSONObject generated = parseModelJson(generateText(prompt));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");

          JSONObject state = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);

          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          boolean levelChanged = oldLevel != newLevel;
          boolean transitionAccepted = !levelChanged || canTransition(before, rolls);
          if (!transitionAccepted) {
            if (before.optJSONObject("level") != null) state.put("level", new JSONObject(before.optJSONObject("level").toString()));
            state.put("title", before.optString("title", "Level " + oldLevel + " – " + levelName(oldLevel)));
            newLevel = oldLevel;
            levelChanged = false;
          }

          if (!meta) {
            state.put("turn", before.optInt("turn", 1) + 1).put("mode", "ai · canon R06 · routed ops");
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            flags.put("currentLevel", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
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

MAIN.write_text(main, encoding="utf-8")
print("APK AI orchestrator applied: dependency-routed canon, bounded recent log, validated state operations.")
