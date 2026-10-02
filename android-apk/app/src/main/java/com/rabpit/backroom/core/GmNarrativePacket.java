package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds the compact read-only narrative packet sent to the text provider. */
public final class GmNarrativePacket {
  public static final int MAX_RECENT_CONTEXT_CHARS = 2800;

  private GmNarrativePacket() {}

  public static JSONObject projectState(JSONObject state) throws Exception {
    return EpistemicView.forActor(state, "cao_minh");
  }

  static JSONObject projectSceneState(JSONObject state) throws Exception {
    JSONObject actorView = projectState(state);
    JSONObject scene = new JSONObject();
    copySceneField(actorView, scene, "turn");

    JSONObject player = actorView.optJSONObject("player");
    if (player != null) {
      JSONObject compactPlayer = new JSONObject();
      for (String key : new String[] {"name", "condition", "hp", "maxHp"}) {
        copySceneField(player, compactPlayer, key);
      }
      if (compactPlayer.length() > 0) scene.put("player", compactPlayer);
    }

    JSONArray party = actorView.optJSONArray("party");
    if (party != null) {
      JSONArray present = new JSONArray();
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        if (member == null || !member.optBoolean("present", true)) continue;
        JSONObject compact = new JSONObject();
        for (String key : new String[] {"id", "name", "present", "injury", "depletion", "location"}) {
          copySceneField(member, compact, key);
        }
        if (compact.length() > 0) present.put(compact);
      }
      if (present.length() > 0) scene.put("party", present);
    }

    JSONObject time = actorView.optJSONObject("gameTime");
    if (time != null) {
      JSONObject compactTime = new JSONObject();
      for (String key : new String[] {"elapsedSubjectiveMinutes", "day", "hour", "minute"}) {
        copySceneField(time, compactTime, key);
      }
      if (compactTime.length() > 0) scene.put("gameTime", compactTime);
    }

    JSONObject combat = actorView.optJSONObject("combat");
    if (combat != null && combat.optBoolean("active", false)) {
      scene.put("combat", new JSONObject(combat.toString()));
    }
    return scene;
  }

  private static void copySceneField(JSONObject source, JSONObject target, String key)
      throws Exception {
    if (source != null && source.has(key)) target.put(key, source.get(key));
  }

  public static String buildScene(
      String levelContext,
      String characterContext,
      String continuityContext,
      String recentContext,
      JSONObject state,
      String action,
      String milestoneContext,
      JSONObject committedTurnEvidence) throws Exception {
    JSONObject promptState = projectSceneState(state);
    String recent = clip(recentContext, 1800);

    String packet = "Bạn là Game Master của một sandbox text game Backrooms.\n"
        + "MỤC TIÊU: biến action và các fact đã commit thành một cảnh sống động, tự nhiên và có không khí. "
        + "Bạn được tự do chọn nhịp câu, hình ảnh, cảm giác không gian, ánh sáng, âm thanh, vật liệu và góc quan sát miễn chúng phù hợp CURRENT LEVEL. "
        + "Đừng viết theo template và đừng recap lượt trước.\n"
        + "RANH GIỚI: Core đã quyết định gameplay state. Không tự tạo hoặc thay đổi Level, Entity, item/loot, Party, combat outcome, injury, stat hay relationship state. "
        + "Chi tiết mô tả vô hại không làm thay đổi state được phép sáng tạo.\n"
        + "PLAYER AGENCY: không tự thêm lời nói, suy nghĩ, quyết định hoặc hành động tiếp theo cho Cao Minh ngoài action người chơi vừa nhập.\n"
        + "VOICE: kể ngôi thứ ba hạn định quanh Cao Minh; tiếng Việt tự nhiên; Backrooms là thực tại, xianxia là lăng kính của Cao Minh. "
        + "Không kết mặc định bằng câu hỏi tu từ hay 'Bạn sẽ làm gì tiếp?'.\n"
        + GmNarratorContract.caoMinhNarrativeCard() + "\n"
        + "CURRENT LEVEL — chỉ context của node hiện tại:\n" + safe(levelContext) + "\n"
        + "CURRENT CHARACTERS — chỉ dùng người Core xác nhận đang present/pending:\n"
        + safe(characterContext) + "\n"
        + "CURRENT MILESTONE — chỉ node của Level hiện tại:\n" + safe(milestoneContext) + "\n"
        + (safe(continuityContext).isEmpty() ? "" :
            "RELEVANT LONG-TERM CONTINUITY — chỉ fact cũ có dependency với scene hiện tại:\n"
                + safe(continuityContext) + "\n")
        + situationContext(state) + "\n"
        + "COMMITTED TURN EVIDENCE — fact thay đổi state của đúng lượt này:\n"
        + (committedTurnEvidence == null ? "{}" : committedTurnEvidence.toString()) + "\n"
        + "RECENT CONTEXT — dùng để giữ continuity và tránh lặp cách diễn đạt:\n" + recent + "\n"
        + "SCENE STATUS — compact Core facts only: " + promptState.toString() + "\n"
        + "PLAYER ACTION: " + safe(action) + "\n"
        + "OUTPUT chỉ JSON: {\"reply\":\"...\",\"choices\":[{\"text\":\"...\"}],"
        + "\"encounterDialogue\":[]}. "
        + "choices có 0-3 gợi ý ngắn; encounterDialogue chỉ dùng khi Character Core có pending intro.";
    return SafePresentationView.narrativeText(state, packet);
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
    return build(levelContext, entityContext, itemContext, characterContext,
        recentContext, state, action, gmStyleExamples, canonText, "", committedTurnEvidence);
  }

  public static String build(
      String levelContext, String entityContext, String itemContext, String characterContext,
      String recentContext, JSONObject state, String action, String gmStyleExamples,
      String canonText, String milestoneContext, JSONObject committedTurnEvidence) throws Exception {
    return build(levelContext, entityContext, itemContext, characterContext,
        recentContext, state, action, gmStyleExamples, canonText, milestoneContext, "",
        committedTurnEvidence);
  }

  public static String build(
      String levelContext, String entityContext, String itemContext, String characterContext,
      String recentContext, JSONObject state, String action, String gmStyleExamples,
      String canonText, String milestoneContext, String continuityContext,
      JSONObject committedTurnEvidence) throws Exception {
    JSONObject promptState = projectState(state);
    String recent = clip(recentContext, MAX_RECENT_CONTEXT_CHARS);
    String style = clip(gmStyleExamples, 1800);

    String packet = "Bạn là Game Master của text game Backrooms (xianxia x Backrooms).\n"
        + GmNarratorContract.promptContext() + "\n"
        + KnowledgeContinuityFirewall.promptContext() + "\n"
        + GmNarratorContract.caoMinhNarrativeCard() + "\n"
        + "VAI TRÒ GM: thế giới và kết quả cơ học của lượt này ĐÃ ĐƯỢC JAVA CORE COMMIT. "
        + "Bạn chỉ kể lại đúng kết quả đã commit và viết thoại/mô tả tự nhiên; không được quyết thêm sự kiện, outcome, spawn, loot, Party, Level hay vị trí authoritative. "
        + "Nếu MILESTONE STORY BIBLE được cung cấp, dùng nó làm ràng buộc tone, voice, mystery và hướng phát triển dài hạn; "
        + "story beat không phải bằng chứng rằng sự kiện đã xảy ra. Không ép hành động, scene, quan hệ hay hậu quả chưa được Core/continuity commit.\n"
        + "NGÔN NGỮ HIỂN THỊ: reply, choices và encounterDialogue phải là tiếng Việt tự nhiên. "
        + "Chỉ giữ tiếng Anh cho tên riêng/tên chính thức cần thiết. Mỗi choices[].text phải viết hoàn toàn bằng tiếng Việt; "
        + "không trộn động từ, chỉ hướng hoặc mô tả môi trường tiếng Anh vào câu lựa chọn.\n"
        + style + "\n"
        + "CORE-OWNED: Java Core sở hữu toàn bộ world outcome: Level/route, Entity spawn, Loot, Inventory, Party, Survival, Progression, Combat, Fact và Thread. "
        + "Không đề xuất transitionTarget/sceneLabel để thay đổi state. Nếu Core context không xác nhận một sự kiện, không được kể nó như đã xảy ra.\n"
        + "COMMITTED-EVIDENCE RULE: mọi phát biểu rằng gameplay state vừa THAY ĐỔI trong lượt này phải được chứng minh bởi COMMITTED TURN EVIDENCE của đúng turn. "
        + "Post-state chỉ cho biết hiện trạng; nó không tự chứng minh rằng item, Level, Party, stat hoặc combat vừa thay đổi trong lượt này.\n"
        + "BACKGROUND CANON ≠ TURN EVIDENCE: Level canon là motif/cấu trúc được phép mô tả khi phù hợp hiện trạng, "
        + "không phải bằng chứng một vật hay dấu vết cụ thể vừa xuất hiện. Không biến anomalous audio, variationPool, sceneSeeds "
        + "hoặc canonical possibilities thành sự kiện/clue cụ thể trong lượt này nếu committed evidence không xác nhận. "
        + "Không tạo clue vật lý mới: tiếng cào trong canon không cho phép tự thêm vết cào sâu trên thảm, suy ra răng/móng vuốt "
        + "hay tuổi của dấu vết. Chỉ mô tả dấu vết cụ thể khi Core/evidence đã commit; không tự suy ra nguồn gốc của nó.\n"
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
        + writerGuidance(milestoneContext, continuityContext)
        + "MARKDOWN CANON (read-only; apply only to committed scene, never override Core state):\n"
        + safe(canonText) + "\n"
        + situationContext(state) + "\n"
        + "COMMITTED TURN EVIDENCE (read-only; only these events justify current-turn mutation claims):\n"
        + (committedTurnEvidence == null ? "{}" : committedTurnEvidence.toString()) + "\n"
        + "RECENT CONTEXT — NEGATIVE REPETITION MEMORY (để NHỚ continuity và TRÁNH LẶP; không kể lại hay paraphrase các lượt trước):\n" + recent + "\n"
        + "READ-ONLY STATE: " + promptState.toString() + "\n"
        + "PLAYER ACTION: " + safe(action) + "\n"
        + "OUTPUT: chỉ JSON hợp lệ, không markdown. JSON không có quyền thay đổi state.\n"
        + "{\"reply\":\"phản hồi Game Master\",\"choices\":[{\"text\":\"Gợi ý 1\"}],\"encounterDialogue\":[],"
        + "\"claims\":[{\"eventId\":\"turn:e1\",\"kind\":\"ITEM_ACQUIRED\",\"subject\":\"Almond Water\"}]}";
    return SafePresentationView.narrativeText(state, packet);
  }

  private static String writerGuidance(String milestoneContext, String continuityContext) {
    String milestone = safe(milestoneContext);
    String continuity = safe(continuityContext);
    StringBuilder out = new StringBuilder();
    if (!milestone.isEmpty()) {
      out.append("MILESTONE STORY BIBLE (writer guidance; not actor knowledge; never overrides Core or committed evidence):\n")
          .append(milestone).append('\n');
    }
    if (!continuity.isEmpty()) {
      out.append("LONG-HORIZON CONTINUITY MEMORY (read-only projection of committed history; not current-turn evidence; ")
          .append("do not promote unresolved material to actor knowledge or objective truth):\n")
          .append(continuity).append('\n');
    }
    return out.toString();
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
