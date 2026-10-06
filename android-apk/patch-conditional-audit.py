from pathlib import Path


MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

text = replace_once(
    text,
    "import java.util.concurrent.Executors;\n",
    "import java.util.concurrent.Executors;\nimport java.util.concurrent.Future;\n",
    "Future import",
)

field_anchor = "  private final ExecutorService imageIo = Executors.newSingleThreadExecutor();\n"
field_block = field_anchor + "  private final ExecutorService auditIo = Executors.newFixedThreadPool(2);\n"
text = replace_once(text, field_anchor, field_block, "audit executor")

shutdown_anchor = "    imageIo.shutdownNow();\n"
shutdown_block = shutdown_anchor + "    auditIo.shutdownNow();\n"
text = replace_once(text, shutdown_anchor, shutdown_block, "audit executor shutdown")

helpers = r'''  private int proposedTurnRisk(JSONObject before, JSONObject generated) {
    int score = 0;
    JSONArray ops = generated.optJSONArray("ops");
    if (ops != null) {
      int limit = Math.min(24, ops.length());
      for (int i = 0; i < limit; i++) {
        JSONObject op = ops.optJSONObject(i);
        if (op == null) continue;
        String type = lower(op.optString("type", ""));
        if ("set_level".equals(type)) score += 4;
        else if ("party_upsert".equals(type) || "party_remove".equals(type)) score += 3;
        else if ("inventory_upsert".equals(type) || "inventory_remove".equals(type) || "patch_player".equals(type)) score += 1;
        else if ("flag_patch".equals(type)) {
          String root = op.optString("root", "");
          if (containsAny(root, "iris", "syvial", "survivorRegistry", "entityRegistry", "survivorsConfirmed", "entitiesConfirmedLocal", "madGod", "reunionPath")) score += 3;
          else if (containsAny(root, "omnivault", "communication", "exploration", "visualAreaKey", "visualEventKey", "entityEncounterKey")) score += 1;
        }
      }
    }

    String reply = generated.optString("reply", "");
    JSONArray party = before.optJSONArray("party");
    boolean hasParty = party != null && party.length() > 0;
    if (hasParty && containsAny(reply, "biết", "nhớ", "nhận ra", "hiểu rằng", "tiết lộ", "bí mật", "nguồn gốc", "thật ra", "kể rằng", "knows", "knew", "secret", "origin")) score += 2;
    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    return score;
  }

  private String auditScopeCanon(JSONObject before, String action, JSONObject rolls, String scope) {
    if ("character".equals(scope)) return compactKaiCanon(action) + "\n\n" + compactDriveCanon(before, action, rolls);
    return compactDriveCanon(before, action, rolls) + "\n\n" + compactKaiCanon(action);
  }

  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, String scope, int excludedWorker) throws Exception {
    JSONObject compact = compactStateForPrompt(before);
    String reply = generated.optString("reply", "");
    if (reply.length() > 7000) reply = reply.substring(0, 7000);
    String prompt = "Bạn là auditor độc lập cho một lượt text game Backrooms. Không viết lại truyện, không tạo state, không thêm canon. " +
      "Chỉ báo HARD khi có xung đột cụ thể chứng minh được từ canon/state/dice dưới đây. Không báo lỗi vì sở thích văn phong. Trả DUY NHẤT JSON.\n\n" +
      "AUDIT SCOPE: " + scope + "\n\n" +
      "AUTHORITATIVE CANON SLICE:\n" + auditScopeCanon(before, action, rolls, scope) + "\n\n" +
      "CURRENT STATE:\n" + compact.toString() + "\n\n" +
      "PLAYER ACTION:\n" + action + "\n\n" +
      "LOCKED DICE:\n" + rolls.toString() + "\n\n" +
      "PROPOSED OPS:\n" + (generated.optJSONArray("ops") == null ? "[]" : generated.optJSONArray("ops").toString()) + "\n\n" +
      "PROPOSED REPLY:\n" + reply + "\n\n" +
      "Rule hợp lệ: canon_conflict, knowledge_leak, state_narrative_mismatch, unsupported_claim, character_voice. " +
      "JSON: {\"pass\":true,\"issues\":[]} hoặc {\"pass\":false,\"issues\":[{\"rule\":\"knowledge_leak\",\"severity\":\"hard\",\"claim\":\"...\",\"reason\":\"...\"}]}";
    JSONObject result = parseModelJson(geminiAuditText(prompt, excludedWorker));
    JSONArray issues = result.optJSONArray("issues");
    if (issues == null) issues = new JSONArray();
    return new JSONObject().put("scope", scope).put("issues", issues);
  }

  private JSONArray hardAuditIssues(JSONArray audits) throws Exception {
    JSONArray hard = new JSONArray();
    if (audits == null) return hard;
    for (int i = 0; i < audits.length(); i++) {
      JSONObject audit = audits.optJSONObject(i);
      JSONArray issues = audit != null ? audit.optJSONArray("issues") : null;
      if (issues == null) continue;
      for (int j = 0; j < issues.length(); j++) {
        JSONObject issue = issues.optJSONObject(j);
        if (issue != null && "hard".equalsIgnoreCase(issue.optString("severity", ""))) hard.put(issue);
      }
    }
    return hard;
  }

  private JSONArray auditsForRisk(JSONObject before, String action, JSONObject rolls, JSONObject generated, int risk, int writerWorker) throws Exception {
    JSONArray audits = new JSONArray();
    if (risk < 4) return audits;
    if (risk < 7) {
      audits.put(runAudit(before, action, rolls, generated, "canon", writerWorker));
      return audits;
    }

    Future<JSONObject> canon = auditIo.submit(() -> runAudit(before, action, rolls, generated, "canon", writerWorker));
    Future<JSONObject> character = auditIo.submit(() -> runAudit(before, action, rolls, generated, "character", writerWorker));
    audits.put(canon.get());
    audits.put(character.get());
    return audits;
  }

  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {
    JSONObject promptState = compactStateForPrompt(before);
    String drivePacket = compactDriveCanon(before, action, rolls);
    String kaiPacket = compactKaiCanon(action);
    String feedback = auditFeedback != null && auditFeedback.length() > 0
      ? "\n\nAUDIT FEEDBACK HARD — sửa đúng các lỗi này, không thay đổi dữ kiện khác:\n" + auditFeedback.toString()
      : "";
    return "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
      "Canon packet bên dưới là HARD LOCK đã được router chọn theo dependency của lượt này. State là source of truth động. UNKNOWN phải giữ UNKNOWN. " +
      "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +
      "Bạn KHÔNG được trả state hoàn chỉnh. Chỉ đề xuất state change bằng ops; Android sẽ kiểm và có thể từ chối từng operation. " +
      "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc canon/state/roll/prompt trong văn xuôi.\n\n" +
      "CANON PACKET:\n" + drivePacket +
      "\n\nKAI PACKET:\n" + kaiPacket +
      "\n\nCURRENT STATE (RECENT LOG ONLY):\n" + promptState.toString() +
      "\n\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
      "\n\nPLAYER INPUT:\n" + action +
      feedback +
      "\n\nOPERATION TYPES: set_location{value}; set_level{level}; patch_player{patch}; inventory_upsert{item,basis}; inventory_remove{name,basis}; " +
      "party_upsert{member}; party_remove{name}; flag_patch{root,value}. " +
      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, madGod, omnivault, survivorRegistry, entityRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +
      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +
      "JSON bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt tự nhiên\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
  }

'''

bridge_anchor = "  private class GameBridge {\n"
text = replace_once(text, bridge_anchor, helpers + bridge_anchor, "conditional audit helpers")

bridge_start = text.index(bridge_anchor)
bridge_end = text.index("\n  private static class SnapshotImage", bridge_start)
new_bridge = r'''  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, action, meta);

          JSONObject generated = parseModelJson(generateText(writerPrompt(before, action, rolls, null)));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");

          int risk = meta ? 0 : proposedTurnRisk(before, generated);
          int writerWorker = lastGeminiWorker;
          JSONArray audits = meta ? new JSONArray() : auditsForRisk(before, action, rolls, generated, risk, writerWorker);
          JSONArray hardIssues = hardAuditIssues(audits);
          boolean repaired = false;

          if (hardIssues.length() > 0) {
            generated = parseModelJson(generateText(writerPrompt(before, action, rolls, hardIssues)));
            reply = generated.optString("reply", "").trim();
            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");
            repaired = true;
            risk = proposedTurnRisk(before, generated);
            writerWorker = lastGeminiWorker;
            audits = auditsForRisk(before, action, rolls, generated, risk, writerWorker);
            hardIssues = hardAuditIssues(audits);
          }

          if (hardIssues.length() > 0) {
            throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
          }

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
            state.put("turn", before.optInt("turn", 1) + 1).put("mode", "ai · canon R06 · routed ops · conditional audit");
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            flags.put("currentLevel", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            flags.put("lastAudit", new JSONObject()
              .put("risk", risk)
              .put("count", audits.length())
              .put("repaired", repaired));
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
text = text[:bridge_start] + new_bridge + text[bridge_end:]

MAIN.write_text(text, encoding="utf-8")
print("APK conditional audit enabled: low-risk skip, narrow one audit, critical parallel canon+character audits, one repair maximum.")
