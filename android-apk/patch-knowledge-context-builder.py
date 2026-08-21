from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")


def replace_method(source: str, signature: str, next_signature: str, replacement: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


# The OLD compactDriveCanon/compactKaiCanon helpers remain in the patched Java only as
# benchmark/audit-compatible dead code. Runtime writer/auditor no longer calls them.
text = replace_method(
    text,
    "  private String auditScopeCanon(JSONObject before, String action, JSONObject rolls, String scope) {",
    "  private JSONObject runAudit(",
    r'''  private String auditScopeCanon(JSONObject before, String action, JSONObject rolls, String scope) {
    return com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
      MainActivity.this, before.toString(), action, rolls.toString());
  }'''
)

text = replace_method(
    text,
    "  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, String scope, int excludedWorker) throws Exception {",
    "  private JSONArray hardAuditIssues(",
    r'''  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, String scope, int excludedWorker) throws Exception {
    String reply = generated.optString("reply", "");
    if (reply.length() > 7000) reply = reply.substring(0, 7000);
    String packet = auditScopeCanon(before, action, rolls, scope);
    String prompt = "Bạn là auditor độc lập cho một lượt text game Backrooms. Không viết lại truyện, không tạo state, không thêm canon. " +
      "Chỉ báo HARD khi có xung đột cụ thể chứng minh được từ KNOWLEDGE PACKET hoặc dice. Không báo lỗi vì sở thích văn phong. Trả DUY NHẤT JSON.\n\n" +
      "AUDIT SCOPE: " + scope + "\n\n" +
      "BUDGETED KNOWLEDGE PACKET:\n" + packet + "\n\n" +
      "LOCKED DICE:\n" + rolls.toString() + "\n\n" +
      "PROPOSED OPS:\n" + (generated.optJSONArray("ops") == null ? "[]" : generated.optJSONArray("ops").toString()) + "\n\n" +
      "PROPOSED REPLY:\n" + reply + "\n\n" +
      "Rule hợp lệ: canon_conflict, knowledge_leak, state_narrative_mismatch, unsupported_claim, character_voice, address_error, competence_suppression, ability_overreach. " +
      "JSON: {\"pass\":true,\"issues\":[]} hoặc {\"pass\":false,\"issues\":[{\"rule\":\"knowledge_leak\",\"severity\":\"hard\",\"claim\":\"...\",\"reason\":\"...\"}]}";
    JSONObject result = parseModelJson(geminiAuditText(prompt, excludedWorker));
    JSONArray issues = result.optJSONArray("issues");
    if (issues == null) issues = new JSONArray();
    return new JSONObject().put("scope", scope).put("issues", issues);
  }'''
)

text = replace_method(
    text,
    "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {",
    "  private class GameBridge {",
    r'''  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {
    String packet = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
      MainActivity.this, before.toString(), action, rolls.toString());
    String feedback = auditFeedback != null && auditFeedback.length() > 0
      ? "\n\nAUDIT FEEDBACK HARD — sửa đúng các lỗi này, không thay đổi dữ kiện khác:\n" + auditFeedback.toString()
      : "";
    return "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
      "KNOWLEDGE PACKET là context đã được Context Builder chọn từ in-game database theo state/scene/present actors/action/story. " +
      "Source trace trong packet chỉ dùng hậu trường; không để nhân vật nói tên record/file/anchor. UNKNOWN phải giữ UNKNOWN. " +
      "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +
      "Bạn KHÔNG được trả state hoàn chỉnh. Chỉ đề xuất state change bằng ops; Android sẽ kiểm và có thể từ chối từng operation. " +
      "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc database/context/state/roll/prompt trong văn xuôi.\n\n" +
      "BUDGETED KNOWLEDGE PACKET:\n" + packet +
      "\n\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
      "\n\nPLAYER INPUT:\n" + action +
      feedback +
      "\n\nOPERATION TYPES: set_location{value}; set_level{level}; patch_player{patch}; inventory_upsert{item,basis}; inventory_remove{name,basis}; " +
      "party_upsert{member}; party_remove{name}; flag_patch{root,value}. " +
      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, madGod, omnivault, survivorRegistry, entityRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath, storyContinuity. " +
      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +
      "JSON bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt tự nhiên\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
  }'''
)

MAIN.write_text(text, encoding="utf-8")
print("GM and conditional critic now consume the budgeted in-game knowledge packet; legacy canon blobs are no longer runtime prompt inputs.")
