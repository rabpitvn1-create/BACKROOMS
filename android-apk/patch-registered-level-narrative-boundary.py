from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")


def replace_facade_once(old: str, new: str, label: str) -> None:
    global facade
    count = facade.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    facade = facade.replace(old, new, 1)


def replace_main_once(old: str, new: str, label: str) -> None:
    global main
    count = main.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    main = main.replace(old, new, 1)


# Registered-Level Core resolution owns canonical state only. It must not also write the GM prose
# into the legacy transcript because MainActivity will now run a presentation-only Gemini pass after
# resolution. A failed presentation therefore cannot rerun the Level action or reroll anything.
error_log_anchor = '''      val reply = "[Warning] Hành động Level không thể commit: ${result.error}."
      appendLog(output, action, reply)
      return response(true, output, result.error, "registered_level_rejected", reply)
'''
error_log_replacement = '''      val reply = "[Warning] Hành động Level không thể commit: ${result.error}."
      return JSONObject(response(true, output, result.error, "registered_level_rejected", reply))
        .put("progressed", false)
        .put("escaped", false)
        .put("evidenceIds", JSONArray())
        .put("evidenceTexts", JSONArray())
        .toString()
'''
replace_facade_once(error_log_anchor, error_log_replacement, "registered rejection transcript separation")

# The evidence highlighter already computes the exact surfaced evidence text. Keep a per-turn array
# as part of the visible presentation contract so Gemini never needs hidden LevelInstance evidence.
evidence_anchor = '''    val evidenceHighlights = linkedSetOf<String>()
    legacy.optJSONObject("flags")?.optJSONArray("evidenceHighlights")?.let { existing ->
'''
evidence_replacement = '''    val evidenceHighlights = linkedSetOf<String>()
    val surfacedEvidence = JSONArray()
    legacy.optJSONObject("flags")?.optJSONArray("evidenceHighlights")?.let { existing ->
'''
replace_facade_once(evidence_anchor, evidence_replacement, "surface evidence presentation array")

evidence_text_anchor = '''        val text = instanceReplies["evidence:$evidenceId"] ?: definitionReplies["evidence:$evidenceId"]
        text?.trim()?.takeIf(String::isNotEmpty)?.let(evidenceHighlights::add)
'''
evidence_text_replacement = '''        val text = instanceReplies["evidence:$evidenceId"] ?: definitionReplies["evidence:$evidenceId"]
        text?.trim()?.takeIf(String::isNotEmpty)?.let { visible ->
          evidenceHighlights.add(visible)
          surfacedEvidence.put(visible)
        }
'''
replace_facade_once(evidence_text_anchor, evidence_text_replacement, "surface evidence text capture")

success_log_anchor = '''    outputFlags.put("evidenceHighlights", highlightArray)
    appendLog(output, action, reply)
    logger.log(PipelineLogEvent(
'''
success_log_replacement = '''    outputFlags.put("evidenceHighlights", highlightArray)
    logger.log(PipelineLogEvent(
'''
replace_facade_once(success_log_anchor, success_log_replacement, "registered success transcript separation")

success_return_anchor = '''    return JSONObject(response(true, output, null, "registered_level_committed", reply))
      .put("progressed", result.progressed)
      .put("escaped", result.escaped)
      .toString()
'''
success_return_replacement = '''    return JSONObject(response(true, output, null, "registered_level_committed", reply))
      .put("progressed", result.progressed)
      .put("escaped", result.escaped)
      .put("evidenceIds", JSONArray(result.evidenceIds.sorted()))
      .put("evidenceTexts", surfacedEvidence)
      .toString()
'''
replace_facade_once(success_return_anchor, success_return_replacement, "registered visible outcome contract")


core_call = "requireGameCore()" if "requireGameCore().processRegisteredLevelAction(stateJson, actionKind, action)" in main else "gameCore"

# Gemini sees only a projection of the already-resolved visible outcome. It cannot return ops/state,
# and MainActivity validates the explicit claims before accepting prose. Exact surfaced evidence text
# is appended after narration so the existing evidence UI remains grounded and highlightable.
helpers = r'''  private boolean sameStringSet(JSONArray left, JSONArray right) {
    java.util.HashSet<String> a = new java.util.HashSet<>();
    java.util.HashSet<String> b = new java.util.HashSet<>();
    if (left != null) for (int i = 0; i < left.length(); i++) a.add(left.optString(i, "").trim());
    if (right != null) for (int i = 0; i < right.length(); i++) b.add(right.optString(i, "").trim());
    a.remove(""); b.remove("");
    return a.equals(b);
  }

  private boolean containsInternalNarrativeTerm(String reply) {
    String text = reply == null ? "" : reply.toLowerCase(java.util.Locale.ROOT);
    String[] forbidden = {
      "core", "inventoryengine", "engine", "blueprint", "commit", "registered level",
      "level instance", "validator", "action rule", "escape blueprint", "internal id"
    };
    for (String term : forbidden) if (text.contains(term)) return true;
    return false;
  }

  private String stripVisibleEvidence(String cue, JSONArray evidenceTexts) {
    String result = cue == null ? "" : cue.trim();
    if (evidenceTexts != null) {
      for (int i = 0; i < evidenceTexts.length(); i++) {
        String evidence = evidenceTexts.optString(i, "").trim();
        if (!evidence.isEmpty()) result = result.replace(evidence, " ");
      }
    }
    return result.replaceAll("\\s+", " ").trim();
  }

  private String registeredNarrativeFallback(JSONObject resolved) {
    boolean progressed = resolved.optBoolean("progressed", false);
    boolean escaped = resolved.optBoolean("escaped", false);
    StringBuilder grounded = new StringBuilder(
      escaped ? "Bạn nhận ra mình đã thoát khỏi khu vực hiện tại."
        : progressed ? "Bạn nhận thấy hành động vừa rồi đã tạo ra một thay đổi có ý nghĩa trong khu vực."
        : "Bạn quan sát kết quả của hành động vừa thực hiện."
    );
    JSONArray evidenceTexts = resolved.optJSONArray("evidenceTexts");
    if (evidenceTexts != null) {
      for (int i = 0; i < evidenceTexts.length(); i++) {
        String evidence = evidenceTexts.optString(i, "").trim();
        if (evidence.isEmpty()) continue;
        if (grounded.length() > 0) grounded.append(' ');
        grounded.append(evidence);
      }
    }
    return grounded.toString().trim();
  }

  private String narrateRegisteredOutcome(String actionKind, String action, JSONObject state, JSONObject resolved) {
    String fallback = registeredNarrativeFallback(resolved);
    try {
      boolean progressed = resolved.optBoolean("progressed", false);
      boolean escaped = resolved.optBoolean("escaped", false);
      String location = state.optString("location", "").trim();
      JSONArray evidenceIds = resolved.optJSONArray("evidenceIds");
      if (evidenceIds == null) evidenceIds = new JSONArray();
      JSONArray evidenceTexts = resolved.optJSONArray("evidenceTexts");
      if (evidenceTexts == null) evidenceTexts = new JSONArray();
      String cue = stripVisibleEvidence(resolved.optString("reply", ""), evidenceTexts);
      String storyContext = campaignStoryBeatPrompt(state);

      JSONObject visible = new JSONObject()
        .put("actionType", actionKind == null ? "" : actionKind)
        .put("playerAction", action == null ? "" : action)
        .put("progressed", progressed)
        .put("escaped", escaped)
        .put("location", location)
        .put("narrativeCue", cue)
        .put("storyContext", storyContext)
        .put("evidenceIds", evidenceIds)
        .put("evidenceTexts", evidenceTexts);

      String prompt = "Bạn là Narrative Engine của một text game Backrooms. "
        + "Kết quả gameplay bên dưới đã được xác định trước và là sự thật duy nhất của lượt này. "
        + "Chỉ kể lại kết quả đó bằng tiếng Việt tự nhiên, giàu hình ảnh nhưng gọn, tối đa 4 câu. "
        + "POV HARD LOCK: người chơi nhập vai trực tiếp Kai Akechi. Mọi văn xuôi gameplay phải dùng ngôi thứ hai giới hạn và gọi Kai là 'bạn'. "
        + "Không được gọi nhân vật người chơi là 'Kai', 'hắn', 'anh ta' hoặc chuyển sang ngôi thứ nhất 'tôi', trừ lời thoại trực tiếp có người nói rõ ràng. "
        + "Không tự viết suy nghĩ, quyết định, lời thoại hoặc hành động có chủ ý mới thay người chơi. "
        + "storyContext là khóa cốt truyện cũ/canon dùng ở hậu trường: phải bám vào nhưng không được nhắc tên prompt, state, canon, Core hay hệ thống trong lời kể. "
        + "Giữ đúng giọng nhân vật, quan hệ và cách xưng hô đã có; không biến lời kể thành báo cáo kỹ thuật hoặc câu xác nhận máy móc. "
        + "Không được tạo thêm vật phẩm, Entity/NPC, thương tích, combat outcome, cửa/lối đi, vị trí hay chuyển Level không có trong dữ liệu. "
        + "Không được thay đổi progressed/escaped/location/evidenceIds. "
        + "Không nhắc Core, Engine, blueprint, commit, validator, rule, prompt hoặc ID nội bộ trong reply. "
        + "Không diễn giải hoặc chép lại evidenceTexts trong reply vì ứng dụng sẽ gắn nguyên văn chúng sau phần kể. "
        + "Chỉ trả JSON: {\"reply\":\"...\",\"claims\":{\"progressed\":true|false,\"escaped\":true|false,\"location\":\"...\",\"evidenceIds\":[],\"introducedItem\":false,\"introducedEntity\":false}}.\n"
        + "VISIBLE_RESOLVED_OUTCOME=" + visible.toString();

      JSONObject generated = parseModelJson(generateText(prompt));
      String reply = generated.optString("reply", "").trim();
      JSONObject claims = generated.optJSONObject("claims");
      if (reply.isEmpty() || claims == null) throw new Exception("registered_narrative_shape_invalid");
      if (containsInternalNarrativeTerm(reply)) throw new Exception("registered_narrative_internal_term");
      if (claims.optBoolean("progressed", !progressed) != progressed) throw new Exception("registered_narrative_progress_mismatch");
      if (claims.optBoolean("escaped", !escaped) != escaped) throw new Exception("registered_narrative_escape_mismatch");
      if (!claims.optString("location", "").trim().equals(location)) throw new Exception("registered_narrative_location_mismatch");
      if (!sameStringSet(claims.optJSONArray("evidenceIds"), evidenceIds)) throw new Exception("registered_narrative_evidence_mismatch");
      if (claims.optBoolean("introducedItem", true) || claims.optBoolean("introducedEntity", true)) throw new Exception("registered_narrative_ungrounded_claim");
      for (int i = 0; i < evidenceIds.length(); i++) {
        String id = evidenceIds.optString(i, "").trim();
        if (!id.isEmpty() && reply.contains(id)) throw new Exception("registered_narrative_internal_id");
      }

      StringBuilder grounded = new StringBuilder(reply);
      for (int i = 0; i < evidenceTexts.length(); i++) {
        String evidence = evidenceTexts.optString(i, "").trim();
        if (evidence.isEmpty()) continue;
        if (grounded.length() > 0) grounded.append(' ');
        grounded.append(evidence);
      }
      return grounded.toString().trim();
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private void appendRegisteredNarrativeLog(JSONObject state, String action, String reply) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) { log = new JSONArray(); state.put("log", log); }
    log.put(new JSONObject().put("role", "player").put("text", action == null ? "" : action));
    log.put(new JSONObject().put("role", "gm").put("text", reply == null ? "" : reply));
  }

'''
helper_anchor = '''  private boolean hasIncompleteRegisteredLevel() {
'''
if "private String narrateRegisteredOutcome(" not in main:
    replace_main_once(helper_anchor, helpers + helper_anchor, "registered presentation-only Gemini helpers")

registered_block_anchor = f'''          JSONObject registeredLevelResult = new JSONObject({core_call}.processRegisteredLevelAction(stateJson, actionKind, action));
          if (registeredLevelResult.optBoolean("handled", false)) {{
            JSONObject registeredState = registeredLevelResult.getJSONObject("state");
            if (registeredLevelResult.optBoolean("escaped", false) && advanceLinearArea(new JSONObject(stateJson), registeredState)) {{
              JSONObject flags = registeredState.optJSONObject("flags");
              JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
              String nextAreaId = exploration != null ? exploration.optString("areaId", "") : "";
              if (!nextAreaId.isEmpty()) {core_call}.handoffCompletedRegisteredLevel(nextAreaId);
            }}
            emit("backroomTurn", registeredState.toString());
            return;
          }}
'''
registered_block_replacement = f'''          JSONObject registeredLevelResult = new JSONObject({core_call}.processRegisteredLevelAction(stateJson, actionKind, action));
          if (registeredLevelResult.optBoolean("handled", false)) {{
            JSONObject registeredState = registeredLevelResult.getJSONObject("state");
            if (registeredLevelResult.optBoolean("escaped", false) && advanceLinearArea(new JSONObject(stateJson), registeredState)) {{
              JSONObject flags = registeredState.optJSONObject("flags");
              JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
              String nextAreaId = exploration != null ? exploration.optString("areaId", "") : "";
              if (!nextAreaId.isEmpty()) {core_call}.handoffCompletedRegisteredLevel(nextAreaId);
            }}
            String registeredReply = narrateRegisteredOutcome(actionKind, action, registeredState, registeredLevelResult);
            appendRegisteredNarrativeLog(registeredState, action, registeredReply);
            emit("backroomTurn", registeredState.toString());
            return;
          }}
'''
replace_main_once(registered_block_anchor, registered_block_replacement, "registered outcome then narration flow")

# Hard assertions make the patch itself an executable architecture contract.
registered_start = facade.index("fun processRegisteredLevelAction(")
registered_end = facade.index("fun restoreCoreState(", registered_start)
registered_slice = facade[registered_start:registered_end]
if "appendLog(output, action, reply)" in registered_slice:
    raise RuntimeError("registered Level Core still writes player-facing transcript")
for marker in (
    '.put("evidenceIds", JSONArray(result.evidenceIds.sorted()))',
    '.put("evidenceTexts", surfacedEvidence)',
):
    if marker not in facade:
        raise RuntimeError("registered visible outcome contract missing: " + marker)
for marker in (
    "private String narrateRegisteredOutcome(",
    "VISIBLE_RESOLVED_OUTCOME=",
    "registered_narrative_location_mismatch",
    "appendRegisteredNarrativeLog(registeredState, action, registeredReply);",
):
    if marker not in main:
        raise RuntimeError("registered narrative boundary missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Registered Level narrative boundary applied: Core resolves once, Gemini receives only visible resolved facts, validated prose cannot mutate canonical state, and retries fall back without rerolling gameplay.")
