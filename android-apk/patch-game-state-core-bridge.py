from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
INTENT = ROOT / "app/src/main/java/com/rabpit/backroom/core/IntentPipeline.kt"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
text = MAIN.read_text(encoding="utf-8")

core_import = "import com.rabpit.backroom.core.GameCoreFacade;\n"
if core_import not in text:
    anchor = "import android.webkit.WebViewClient;\n"
    if anchor not in text:
        raise RuntimeError("Game State Core import anchor not found")
    text = text.replace(anchor, anchor + core_import, 1)

field = "  private GameCoreFacade gameCore;\n"
if field not in text:
    anchor = "  private WebView webView;\n"
    if anchor not in text:
        raise RuntimeError("Game State Core field anchor not found")
    text = text.replace(anchor, anchor + field, 1)

initialization = "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n"
if initialization not in text:
    anchor = "    super.onCreate(savedInstanceState);\n"
    if anchor not in text:
        raise RuntimeError("Game State Core initialization anchor not found")
    text = text.replace(anchor, anchor + initialization, 1)

close_line = "    if (gameCore != null) gameCore.close();\n"
if close_line not in text:
    anchor = "  @Override protected void onDestroy() {\n"
    if anchor not in text:
        raise RuntimeError("Game State Core close anchor not found")
    text = text.replace(anchor, anchor + close_line, 1)

local_pass = '''          JSONObject localResult = new JSONObject(gameCore.processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }
'''
if local_pass not in text:
    bridge = text.index("  private class GameBridge {")
    submit = text.index("    @JavascriptInterface public void submitTurn(String stateJson, String action) {", bridge)
    try_anchor = "        try {\n"
    position = text.index(try_anchor, submit) + len(try_anchor)
    text = text[:position] + local_pass + text[position:]

gemini_commit = '''          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(before.toString(), candidateState.toString(), action));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối Gemini delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          candidateState = coreCommit.getJSONObject("state");

'''
if "gameCore.processValidatedCandidate(" not in text:
    anchor = "          JSONObject state = candidateState;\n"
    if anchor not in text:
        raise RuntimeError("validated Gemini candidate anchor not found")
    text = text.replace(anchor, gemini_commit + anchor, 1)

for required in [core_import.strip(), field.strip(), initialization.strip(), close_line.strip(), "gameCore.processRule(stateJson, action)", "gameCore.processValidatedCandidate("]:
    if required not in text:
        raise RuntimeError(f"Game State Core integration missing: {required}")

MAIN.write_text(text, encoding="utf-8")

# Do not keep conversational filler such as "được" inside a newly resolved item name.
intent = INTENT.read_text(encoding="utf-8")
if "|nhặt|được|lượm|" not in intent:
    anchor = "|nhặt|lượm|"
    if anchor not in intent:
        raise RuntimeError("Item resolver noise anchor not found")
    intent = intent.replace(anchor, "|nhặt|được|lượm|", 1)
INTENT.write_text(intent, encoding="utf-8")

# Every deterministic gameplay rejection becomes a visible [Warning]. Keep technical reason codes
# internal; the player gets concise messages. Scan and Copy share the user-facing wording requested
# by the game design.
facade = FACADE.read_text(encoding="utf-8")
warning_marker = 'return "[Warning] $message"'
if warning_marker not in facade:
    start_anchor = "  private fun validationReply(reason: String): String = when (reason) {"
    end_anchor = "\n\n  companion object {"
    start = facade.find(start_anchor)
    end = facade.find(end_anchor, start)
    if start < 0 or end < 0:
        raise RuntimeError("validationReply anchors not found")
    warning_reply = '''  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."
      "precise_content_amount_forbidden" -> "This action is not available."
      "item_content_empty" -> "This action is not available."
      "insufficient_item_quantity", "item_not_owned" -> "This action is not available."
      "party_full" -> "Party đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được NPC xác nhận."
      "living_target_forbidden" -> "Omnivault không thể tác động lên sinh vật sống."
      "restore_cooldown_active" -> "Vật phẩm này vẫn đang trong cooldown Hoàn Nguyên 24 giờ."
      else -> "This action is not available."
    }
    return "[Warning] $message"
  }'''
    facade = facade[:start] + warning_reply + facade[end:]
FACADE.write_text(facade, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")
old_chips = 'function chips(items){return items&&items.length?items.map(x=>"<span>"+esc(typeof x==="string"?x:x.name||"—")+"</span>").join(""):"<span>Trống.</span>"}'
quantity_chips = 'function chips(items){return items&&items.length?items.map(x=>{if(typeof x==="string")return "<span>"+esc(x)+"</span>";const q=Math.max(1,Number(x.quantity)||1);return "<span>"+esc(x.name||"—")+" ×"+q+"</span>"}).join(""):"<span>Trống.</span>"}'
clean_chips = 'function itemDisplayName(x){let n=String((x&&x.name)||"—").replace(/^\\s*được\\s+/i,"").trim();if(n)n=n.charAt(0).toLocaleUpperCase("vi-VN")+n.slice(1);return n} function chips(items){return items&&items.length?items.map(x=>{if(typeof x==="string")return "<span>"+esc(x)+"</span>";const q=Math.max(1,Number(x.quantity)||1);return "<span>"+esc(itemDisplayName(x))+" ×"+q+"</span>"}).join(""):"<span>Trống.</span>"}'
if clean_chips not in html:
    if quantity_chips in html:
        html = html.replace(quantity_chips, clean_chips, 1)
    elif old_chips in html:
        html = html.replace(old_chips, clean_chips, 1)
    else:
        raise RuntimeError("Inventory item renderer anchor not found")

warning_css = ".message.warning{border-left-color:#d99a2b;background:#231a0b}.message.warning .role{color:#e3a83a}.message.warning .text{color:#ffd27a;font-weight:650}"
if warning_css not in html:
    css_anchor = ".chips span{border:1px solid #313940;padding:5px 7px;font-size:12px}"
    if css_anchor not in html:
        raise RuntimeError("Warning CSS anchor not found")
    html = html.replace(css_anchor, css_anchor + warning_css, 1)

old_render = 'function render(){titleEl.textContent=state.title;turnEl.textContent=state.turn;locationEl.textContent=state.location;modeEl.textContent=state.mode;playerEl.textContent=state.player?.name||"Kai Akechi";partyEl.innerHTML=chips(state.party);inventoryEl.innerHTML=chips(state.inventory);logEl.innerHTML=(state.log||[]).map(x=>"<article class=\'message "+(x.role==="player"?"player":"")+"\'><div class=\'role\'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class=\'text\'>"+esc(x.text)+"</div></article>").join("");logEl.scrollTop=logEl.scrollHeight;statusEl.textContent="Save được lưu riêng trên thiết bị này."}'
warning_render = 'function render(){titleEl.textContent=state.title;turnEl.textContent=state.turn;locationEl.textContent=state.location;modeEl.textContent=state.mode;playerEl.textContent=state.player?.name||"Kai Akechi";partyEl.innerHTML=chips(state.party);inventoryEl.innerHTML=chips(state.inventory);logEl.innerHTML=(state.log||[]).map(x=>{const w=x.role!=="player"&&String(x.text||"").trim().startsWith("[Warning]");return "<article class=\'message "+(x.role==="player"?"player":"")+(w?" warning":"")+"\'><div class=\'role\'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class=\'text\'>"+esc(x.text)+"</div></article>"}).join("");logEl.scrollTop=logEl.scrollHeight;statusEl.textContent="Save được lưu riêng trên thiết bị này."}'
if warning_render not in html:
    if old_render not in html:
        raise RuntimeError("Warning renderer anchor not found")
    html = html.replace(old_render, warning_render, 1)

INDEX.write_text(html, encoding="utf-8")

print("Final Game State Core bridge, item cleanup, quantity UI and warning feedback applied.")
