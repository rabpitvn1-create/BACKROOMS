from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

old_signature = '''    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
'''
new_signature = '''    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      submitAction(stateJson, "EXECUTE", action);
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      submitTurnInternal(stateJson, actionKind, action);
    }

    private void submitTurnInternal(String stateJson, String actionKind, String action) {
      io.execute(() -> {
'''
if "@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)" not in main:
    if old_signature not in main:
        raise RuntimeError("MainActivity submitTurn anchor missing")
    main = main.replace(old_signature, new_signature, 1)

core_call = "requireGameCore()" if "requireGameCore().processRule(stateJson, action)" in main else "gameCore"
local_line = f'''          JSONObject localResult = new JSONObject({core_call}.processRule(stateJson, action));
'''
if "beginAction(stateJson, actionKind, action)" not in main:
    if local_line not in main:
        raise RuntimeError("MainActivity processRule anchor missing")
    begin_block = f'''          JSONObject actionStart = new JSONObject({core_call}.beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean("handled", false)) {{
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }}
          JSONObject localResult = new JSONObject({core_call}.processRule(stateJson, action));
'''
    main = main.replace(local_line, begin_block, 1)

# The final patch chain no longer builds the GM prompt inside submitTurn. It calls writerPrompt().
# Pull the typed ActionRuntime context there so the initial writer and any repair pass receive the
# exact same Search / Execute / Explore semantics.
writer_sig = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
if "String actionRuntimeContext =" not in main:
    writer_pos = main.find(writer_sig)
    if writer_pos < 0:
        raise RuntimeError("MainActivity final writerPrompt anchor missing")
    writer_body = writer_pos + len(writer_sig)
    directive = f'''    String actionRuntimeContext = {core_call}.currentActionContext();
    String actionKindForPrompt = new JSONObject(actionRuntimeContext).optString("kind", "EXECUTE");
    String actionDirective = "ACTION TYPE = " + actionKindForPrompt + ". " +
      ("SEARCH".equals(actionKindForPrompt) ? "SEARCH HARD LOCK: khảo sát có hệ thống location hiện tại, không tự chuyển sang location mới; có thể gặp Entity hoặc Survivor, tìm resource/clue/hazard/exit evidence nhưng không đảm bảo có kết quả hay loot. " :
       "EXPLORE".equals(actionKindForPrompt) ? "EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. " :
       "EXECUTE HARD LOCK: đây là freeform intent của người chơi; phân giải đúng hành động đã nhập, không tự đổi mục tiêu. ");
'''
    main = main[:writer_body] + directive + main[writer_body:]

    return_anchor = '    return "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +\n'
    return_pos = main.find(return_anchor, writer_body)
    if return_pos < 0:
        raise RuntimeError("MainActivity writerPrompt return anchor missing")
    main = main[:return_pos] + '    return actionDirective + "\\nACTION_RUNTIME: " + actionRuntimeContext + "\\n" +\n' + main[return_pos:]

# Technical/provider failure does not represent successful in-world progress. End the active session
# as interrupted so the next player decision cannot inherit a stale action lock.
if "abortAction(\"pipeline_error\")" not in main:
    submit_pos = main.find("    private void submitTurnInternal(String stateJson, String actionKind, String action) {")
    if submit_pos < 0:
        raise RuntimeError("typed submitTurnInternal missing before catch patch")
    catch_anchor = '''        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
'''
    catch_pos = main.find(catch_anchor, submit_pos)
    if catch_pos < 0:
        raise RuntimeError("MainActivity gameplay catch anchor missing")
    replacement = f'''        }} catch (Exception e) {{
          try {{ {core_call}.abortAction("pipeline_error"); }} catch (Exception ignored) {{}}
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
'''
    main = main[:catch_pos] + replacement + main[catch_pos + len(catch_anchor):]

for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    'submitAction(stateJson, "EXECUTE", action);',
    '.beginAction(stateJson, actionKind, action)',
    'String actionRuntimeContext =',
    '.currentActionContext()',
    'SEARCH HARD LOCK:',
    'EXPLORE HARD LOCK:',
    '.abortAction("pipeline_error")',
):
    if marker not in main:
        raise RuntimeError(f"typed action bridge missing: {marker}")

MAIN.write_text(main, encoding="utf-8")
print("Step 2 typed Android action bridge applied.")
