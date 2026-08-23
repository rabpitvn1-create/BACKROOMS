from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Core bridge: all three UI entry points share ActionRuntime + TurnCoordinator.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")

runtime_methods = r'''  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val kindName = kindRaw.trim().uppercase()
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindName }
      ?: return actionStartResponse(false, null, "action_kind_invalid")
    val existing = ActionRuntime.activeSession(state)
    if (existing != null) {
      return if (existing.kind == kind && existing.input == action) {
        actionStartResponse(true, existing, null)
      } else {
        actionStartResponse(false, existing, "action_session_already_active")
      }
    }
    val turnId = nextTurnId(legacy, state)
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
    val planned = TimeCostPolicy.estimateMinutes(action)
    val started = ActionRuntime.start(
      state = state,
      sessionId = sessionId,
      turnId = turnId,
      actorId = KAI_ID,
      kind = kind,
      input = action,
      locationKey = state.world["location"] ?: legacy.optString("location").takeIf(String::isNotBlank),
      plannedMinutes = planned,
      searchDepth = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
    )
    if (!started.applied) return actionStartResponse(false, started.session, started.error ?: "action_start_failed")
    repository.save(started.state)
    return actionStartResponse(true, started.session, null)
  }

  fun currentActionContext(): String {
    val state = repository.load()
    val active = ActionRuntime.activeSession(state)
    return JSONObject().apply {
      put("active", active != null)
      if (active != null) {
        put("sessionId", active.sessionId)
        put("turnId", active.turnId)
        put("kind", active.kind.name)
        put("phase", active.phase.name)
        put("location", active.locationKey ?: JSONObject.NULL)
        put("elapsedMinutes", active.elapsedMinutes)
        put("plannedMinutes", active.plannedMinutes ?: JSONObject.NULL)
        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
          put("searchCoverage", JSONArray(ActionRuntime.searchCoverage(state, active.locationKey).sorted()))
        }
      }
    }.toString()
  }

  fun abortAction(reason: String): Boolean {
    if (!repository.exists()) return false
    val state = repository.load()
    val active = ActionRuntime.activeSession(state) ?: return false
    val interrupted = ActionRuntime.interrupt(state, active.sessionId, reason.ifBlank { "pipeline_error" })
    if (!interrupted.applied) return false
    repository.save(interrupted.state)
    return true
  }

  private fun actionStartResponse(handled: Boolean, session: ActionSessionSnapshot?, error: String?): String = JSONObject().apply {
    put("handled", handled)
    if (session != null) {
      put("sessionId", session.sessionId)
      put("turnId", session.turnId)
      put("kind", session.kind.name)
    }
    if (error != null) put("error", error)
  }.toString()

  private fun commitActionRuntime(
    state: GameState,
    commands: MutableList<GameCommand>,
    action: String,
    turnId: String
  ): TurnResult {
    val active = ActionRuntime.activeSession(state)
    if (active == null) {
      commands += timeAdvanceCommand(turnId, action)
      return TurnCoordinator.commit(state, commands)
    }
    if (active.turnId != turnId) return TurnResult(state, error = "action_turn_mismatch")

    val minutes = active.plannedMinutes ?: TimeCostPolicy.estimateMinutes(action)
    val progressed = ActionRuntime.advance(state, active.sessionId, "resolve", minutes)
    if (!progressed.applied && !progressed.duplicate) {
      return TurnResult(state, error = progressed.error ?: "action_time_rejected")
    }
    val progressedState = if (progressed.duplicate) state else progressed.state
    val committed = TurnCoordinator.commit(progressedState, commands)
    if (committed.error != null) return committed

    var finalState = committed.state
    if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
      val depth = active.searchDepth ?: SearchDepth.NORMAL
      val coverage = ActionRuntime.markSearchCoverage(
        finalState,
        active.sessionId,
        setOf("depth:${depth.name.lowercase()}")
      )
      if (coverage.applied) finalState = coverage.state
    }

    val completed = ActionRuntime.complete(finalState, active.sessionId)
    if (!completed.applied) return TurnResult(finalState, committed.execution, completed.error ?: "action_complete_failed")
    return TurnResult(
      completed.state,
      committed.execution?.copy(state = completed.state)
    )
  }

'''

if "fun beginAction(legacyStateJson: String, kindRaw: String, action: String)" not in facade:
    anchor = "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n"
    if anchor not in facade:
        raise RuntimeError("GameCoreFacade currentCoreState anchor missing")
    facade = facade.replace(anchor, runtime_methods + anchor, 1)

# The release chain adds a third time helper path that is not one of the two pending-turn commit
# sites. Route exactly the authoritative pending-turn sites through ActionRuntime and leave unrelated
# helper paths intact.
time_line = "    commands += timeAdvanceCommand(turnId, action)\n"
commit_line = "    val committed = TurnCoordinator.commit(pending.state, commands)"
commit_count = facade.count(commit_line)
time_count = facade.count(time_line)
if commit_count < 2 or time_count < commit_count:
    raise RuntimeError(f"GameCoreFacade commit routing anchors invalid: time={time_count}, commit={commit_count}")
facade = facade.replace(time_line, "", commit_count)
facade = facade.replace(commit_line, "    val committed = commitActionRuntime(pending.state, commands, action, turnId)", commit_count)

for marker in [
    "fun beginAction(legacyStateJson: String, kindRaw: String, action: String)",
    "fun currentActionContext(): String",
    "fun abortAction(reason: String): Boolean",
    "private fun commitActionRuntime(",
    "ActionRuntime.advance(state, active.sessionId, \"resolve\", minutes)",
    "ActionRuntime.markSearchCoverage(",
    "ActionRuntime.complete(finalState, active.sessionId)",
]:
    if marker not in facade:
        raise RuntimeError(f"GameCoreFacade ActionRuntime bridge missing: {marker}")

FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Android bridge: preserve submitTurn compatibility, add typed submitAction.
# ---------------------------------------------------------------------------
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

state_prompt_anchor = '''          JSONObject state = new JSONObject(stateJson);
          String prompt = '''
if "String actionContext =" not in main:
    if state_prompt_anchor not in main:
        raise RuntimeError("MainActivity GM prompt state anchor missing")
    directive_block = f'''          JSONObject state = new JSONObject(stateJson);
          String actionContext = {core_call}.currentActionContext();
          String actionDirective = "ACTION TYPE = " + actionKind + ". " +
            ("SEARCH".equals(actionKind) ? "SEARCH HARD LOCK: khảo sát có hệ thống location hiện tại, không tự chuyển sang location mới; có thể gặp Entity hoặc Survivor, tìm resource/clue/hazard/exit evidence nhưng không đảm bảo có kết quả hay loot. " :
             "EXPLORE".equals(actionKind) ? "EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. " :
             "EXECUTE HARD LOCK: đây là freeform intent của người chơi; phân giải đúng hành động đã nhập, không tự đổi mục tiêu. ");
          String prompt = actionDirective + "\\nACTION_RUNTIME: " + actionContext + "\\n" + '''
    main = main.replace(state_prompt_anchor, directive_block, 1)

# A provider/validation failure is technical failure, not in-world elapsed time. Clear the still-active session.
error_anchor = '''        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
'''
if "abortAction(\"pipeline_error\")" not in main:
    if error_anchor not in main:
        raise RuntimeError("MainActivity gameplay catch anchor missing")
    error_block = f'''        }} catch (Exception e) {{
          try {{ {core_call}.abortAction("pipeline_error"); }} catch (Exception ignored) {{}}
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
'''
    main = main.replace(error_anchor, error_block, 1)

for marker in [
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    'submitAction(stateJson, "EXECUTE", action);',
    '.beginAction(stateJson, actionKind, action)',
    '.currentActionContext()',
    'SEARCH HARD LOCK:',
    'EXPLORE HARD LOCK:',
    '.abortAction("pipeline_error")',
]:
    if marker not in main:
        raise RuntimeError(f"MainActivity typed action bridge missing: {marker}")

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) WebView UI: three independent horizontal buttons, one bridge/pipeline.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")

button_row = '''<div class="primary-action-row" id="primaryActionRow">
<button type="button" class="primary-action" id="searchActionButton" aria-label="Tìm kiếm">
<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="5.5"></circle><path d="M14.7 14.7 20 20"></path></svg><span>Tìm kiếm</span>
</button>
<button type="submit" class="primary-action execute-action" id="submit" aria-label="Thực hiện">
<svg class="action-icon ai-action-icon" viewBox="0 0 28 24" aria-hidden="true"><path d="M3.5 5.5A2.5 2.5 0 0 1 6 3h11a2.5 2.5 0 0 1 2.5 2.5v7A2.5 2.5 0 0 1 17 15H6a2.5 2.5 0 0 1-2.5-2.5z"></path><text x="7" y="11.8">AI</text><path class="spark" d="M22 2v5m-2.5-2.5h5M23.5 9v3m-1.5-1.5h3"></path></svg><span>Thực hiện</span>
</button>
<button type="button" class="primary-action" id="exploreActionButton" aria-label="Khám phá">
<svg class="action-icon footprint-icon" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="8" cy="8" rx="3" ry="4.2" transform="rotate(-20 8 8)"></ellipse><ellipse cx="15.8" cy="15.5" rx="3" ry="4.2" transform="rotate(18 15.8 15.5)"></ellipse><circle cx="5.2" cy="3.4" r="1"></circle><circle cx="18.5" cy="10.3" r="1"></circle></svg><span>Khám phá</span>
</button>
</div>'''

if 'id="searchActionButton"' not in html:
    button_pattern = re.compile(r'<button\s+id="submit"[^>]*>.*?</button>', re.IGNORECASE | re.DOTALL)
    html, count = button_pattern.subn(button_row, html, count=1)
    if count != 1:
        raise RuntimeError(f"UI submit button anchor expected 1 match, found {count}")

css = r'''
/* STEP2_THREE_ACTIONS */
.primary-action-row{display:grid;grid-template-columns:1fr 1.12fr 1fr;gap:7px;width:100%}
.primary-action{min-width:0;min-height:46px;border-radius:9px;display:flex;align-items:center;justify-content:center;gap:7px;padding:10px 8px;white-space:nowrap}
.primary-action.execute-action{font-weight:800;border-color:#56616a;background:#20272d}
.primary-action .action-icon{width:19px;height:19px;flex:0 0 19px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.primary-action .footprint-icon ellipse,.primary-action .footprint-icon circle{fill:currentColor;stroke:none}
.primary-action .ai-action-icon{width:22px;flex-basis:22px}
.primary-action .ai-action-icon text{font:700 6.5px system-ui,sans-serif;fill:currentColor;stroke:none;letter-spacing:.2px}
.primary-action .ai-action-icon .spark{stroke-width:1.4}
@media(max-width:390px){.primary-action-row{gap:5px}.primary-action{font-size:12px;padding:9px 5px;gap:5px}.primary-action .action-icon{width:17px;height:17px;flex-basis:17px}.primary-action .ai-action-icon{width:20px;flex-basis:20px}}
'''
if "STEP2_THREE_ACTIONS" not in html:
    if "</style>" not in html:
        raise RuntimeError("UI style closing tag missing")
    html = html.replace("</style>", css + "\n</style>", 1)

# The freeform button uses typed ActionRuntime rather than bypassing the session layer.
submit_pattern = re.compile(r'(?:window\.)?Android\.submitTurn\(JSON\.stringify\(state\),a\)')
if 'Android.submitAction(JSON.stringify(state),"EXECUTE",a)' not in html:
    html, count = submit_pattern.subn('window.Android.submitAction(JSON.stringify(state),"EXECUTE",a)', html, count=1)
    if count != 1:
        raise RuntimeError(f"UI submitTurn call expected 1 match, found {count}")

js = r'''
// STEP2_TYPED_ACTIONS
const searchActionButton=byId("searchActionButton"),exploreActionButton=byId("exploreActionButton");
function syncPrimaryActions(){
  const hasText=!!(actionEl&&actionEl.value.trim());
  if(submitEl)submitEl.disabled=busy||!hasText;
  if(searchActionButton)searchActionButton.disabled=busy;
  if(exploreActionButton)exploreActionButton.disabled=busy;
}
function appendMacroPending(label){
  if(!logEl)return;
  const player=document.createElement("article");player.className="message player pending";player.setAttribute("data-pending","1");player.innerHTML="<div class='role'>BẠN</div><div class='text'></div>";player.querySelector(".text").textContent=label;logEl.appendChild(player);
  const gm=document.createElement("article");gm.className="message pending";gm.setAttribute("data-pending","1");gm.innerHTML="<div class='role'>GAME MASTER</div><div class='text'>Đang xử lý lượt…</div>";logEl.appendChild(gm);logEl.scrollTop=logEl.scrollHeight;
}
function submitMacroAction(kind,label){
  if(busy)return;
  if(!window.Android||typeof window.Android.submitAction!=="function"){statusEl.textContent="Không tìm thấy Android action bridge.";return}
  busy=true;syncPrimaryActions();statusEl.textContent=kind==="SEARCH"?"Đang tìm kiếm khu vực hiện tại…":"Đang khám phá khu vực chưa khảo sát…";appendMacroPending(label);window.Android.submitAction(JSON.stringify(state),kind,label);
}
if(searchActionButton)searchActionButton.addEventListener("click",()=>submitMacroAction("SEARCH","Tìm kiếm"));
if(exploreActionButton)exploreActionButton.addEventListener("click",()=>submitMacroAction("EXPLORE","Khám phá"));
if(actionEl)actionEl.addEventListener("input",syncPrimaryActions);
syncPrimaryActions();
'''
if "STEP2_TYPED_ACTIONS" not in html:
    anchor = "window.backroomTurn="
    pos = html.find(anchor)
    if pos < 0:
        raise RuntimeError("UI backroomTurn anchor missing")
    html = html[:pos] + js + "\n" + html[pos:]

# Keep all three buttons locked while a turn is in flight and restore correct empty-input state after it ends.
html = html.replace("busy=true;submitEl.disabled=true;", "busy=true;syncPrimaryActions();")
html = html.replace("busy=false;submitEl.disabled=false;", "busy=false;syncPrimaryActions();")

for marker in [
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    'class="primary-action execute-action"',
    'Android.submitAction(JSON.stringify(state),"EXECUTE",a)',
    'submitMacroAction("SEARCH","Tìm kiếm")',
    'submitMacroAction("EXPLORE","Khám phá")',
    'submitEl.disabled=busy||!hasText',
    'STEP2_THREE_ACTIONS',
]:
    if marker not in html:
        raise RuntimeError(f"Three-action UI contract missing: {marker}")

if re.search(r'<button\s+id="submit"[^>]*>\s*THỰC HIỆN\s*</button>', html, re.IGNORECASE):
    raise RuntimeError("Legacy single execute button still present")

INDEX.write_text(html, encoding="utf-8")
print("Step 2 applied: Search / Execute / Explore UI wired through one ActionRuntime pipeline.")
