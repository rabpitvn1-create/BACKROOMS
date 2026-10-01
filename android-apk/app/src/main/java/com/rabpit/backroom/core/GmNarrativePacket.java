package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Builds the compact read-only narrative packet sent to the text provider. */
public final class GmNarrativePacket {
  public static final int MAX_RECENT_CONTEXT_CHARS = 2800;

  private GmNarrativePacket() {}

  public static JSONObject projectState(JSONObject state) throws Exception {
    return EpistemicView.forActor(state, "cao_minh");
  }

  public static String build(
      String levelContext,
      String entityContext,
      String itemContext,
      String characterContext,
      String recentContext,
      JSONObject state,
      String action,
      String gmStyleExamples) throws Exception {
    return build(levelContext, entityContext, itemContext, characterContext,
        recentContext, state, action, gmStyleExamples, "");
  }

  public static String build(
      String levelContext, String entityContext, String itemContext, String characterContext,
      String recentContext, JSONObject state, String action, String gmStyleExamples,
      String canonText) throws Exception {
    return build(levelContext, entityContext, itemContext, characterContext,
        recentContext, state, action, gmStyleExamples, canonText, new JSONObject());
  }

  public static String build(
      String levelContext, String entityContext, String itemContext, String characterContext,
      String recentContext, JSONObject state, String action, String gmStyleExamples,
      String canonText, JSONObject committedTurnEvidence) throws Exception {
    JSONObject promptState = projectState(state);
    String recent = clip(recentContext, MAX_RECENT_CONTEXT_CHARS);
    String style = clip(gmStyleExamples, 1800);

    return "Bạn là Game Master của text game Backrooms (xianxia x Backrooms).\n"
        + GmNarratorContract.promptContext() + "\n"
        + GmNarratorContract.caoMinhNarrativeCard() + "\n"
        + "VAI TRÒ GM: thế giới và kết quả cơ học của lượt này ĐÃ ĐƯỢC JAVA CORE COMMIT. "
        + "Bạn chỉ kể lại đúng kết quả đã commit và viết thoại/mô tả tự nhiên; không được quyết thêm sự kiện, outcome, spawn, loot, Party, Level hay vị trí authoritative. "
        + "Không có cốt truyện, chương hay diễn biến định sẵn cần bám theo. Không ép người chơi quay về một tuyến cố định.\n"
        + "NGÔN NGỮ HIỂN THỊ: reply, choices và encounterDialogue phải là tiếng Việt tự nhiên. "
        + "Chỉ giữ tiếng Anh cho tên riêng/tên chính thức cần thiết. Mỗi choices[].text phải viết hoàn toàn bằng tiếng Việt; "
        + "không trộn động từ, chỉ hướng hoặc mô tả môi trường tiếng Anh vào câu lựa chọn.\n"
        + style + "\n"
        + "CORE-OWNED: Java Core sở hữu toàn bộ world outcome: Level/route, Entity spawn, Loot, Inventory, Party, Survival, Progression, Combat, Fact và Thread. "
        + "Không đề xuất transitionTarget/sceneLabel để thay đổi state. Nếu Core context không xác nhận một sự kiện, không được kể nó như đã xảy ra.\n"
        + "COMMITTED-EVIDENCE RULE: mọi phát biểu rằng gameplay state vừa THAY ĐỔI trong lượt này phải được chứng minh bởi COMMITTED TURN EVIDENCE của đúng turn. "
        + "Post-state chỉ cho biết hiện trạng; nó không tự chứng minh rằng item, Level, Party, stat hoặc combat vừa thay đổi trong lượt này.\n"
        + "EPISTEMIC: READ-ONLY STATE đã được lọc theo góc nhìn Cao Minh. Belief confidence=CONFIRMED chỉ có nghĩa actor tin chắc; "
        + "không tự coi belief là objective truth nếu không có confirmedFactId/fact tương ứng. Không suy ra hidden state bị thiếu khỏi context.\n"
        + "EXPLORER CHOICES: trả 0-3 gợi ý hành động ngắn, cụ thể và phù hợp với tình huống hiện tại; "
        + "đây là gợi ý của GM, không phải nhánh kịch bản cố định. Nếu Entity đang đối đầu trực tiếp thì choices=[].\n"
        + "ENCOUNTER DIALOGUE: chỉ khi Character Core có pending intro; khi đó trả đúng 2-5 câu thoại. Nếu không thì [].\n"
        + "CLAIMS: claims[] là bắt buộc. Mỗi câu khẳng định gameplay state vừa thay đổi trong lượt này phải có một claim "
        + "{eventId,kind,subject} khớp nguyên văn với COMMITTED TURN EVIDENCE. Không có mutation claim thì claims=[]. "
        + "Không được tự tạo eventId/kind/subject và không dùng post-state để suy ra mutation.\n"
        + safe(levelContext) + "\n"
        + safe(entityContext) + "\n"
        + safe(itemContext) + "\n"
        + safe(characterContext) + "\n"
        + "MARKDOWN CANON (read-only; apply only to committed scene, never override Core state):\n"
        + safe(canonText) + "\n"
        + situationContext(state) + "\n"
        + "COMMITTED TURN EVIDENCE (read-only; only these events justify current-turn mutation claims):\n"
        + (committedTurnEvidence == null ? "{}" : committedTurnEvidence.toString()) + "\n"
        + "RECENT CONTEXT (chỉ giữ continuity, không lặp nguyên văn):\n" + recent + "\n"
        + "READ-ONLY STATE: " + promptState.toString() + "\n"
        + "PLAYER ACTION: " + safe(action) + "\n"
        + "OUTPUT: chỉ JSON hợp lệ, không markdown. JSON không có quyền thay đổi state.\n"
        + "{\"reply\":\"phản hồi Game Master\",\"choices\":[{\"text\":\"Gợi ý 1\"}],\"encounterDialogue\":[],"
        + "\"claims\":[{\"eventId\":\"turn:e1\",\"kind\":\"ITEM_ACQUIRED\",\"subject\":\"Almond Water\"}]}";
  }

  private static String situationContext(JSONObject state) {
    if (state == null) return "WORLD SITUATION: unavailable; do not invent one.";
    JSONObject root = state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONObject selection = root == null ? null : root.optJSONObject("lastSelection");
    if (selection == null || selection.optBoolean("selectedNone", false)) {
      return "WORLD SITUATION ĐÃ COMMIT: không có biến cố chủ động mới trong lượt này.";
    }
    String summary = selection.optString("publicSummary", "").trim();
    JSONObject proposal = selection.optJSONObject("worldProposal");
    String tactic = proposal == null ? "" : proposal.optString("actionType", "").trim();
    return "WORLD SITUATION ĐÃ COMMIT: "
        + (summary.isEmpty() ? selection.optString("situationKey", "") : summary)
        + (tactic.isEmpty() ? "" : "\nWORLD TACTIC ĐÃ COMMIT: " + tactic);
  }

  private static String clip(String value, int max) {
    String text = safe(value);
    if (text.length() <= max) return text;
    return text.substring(0, max);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
