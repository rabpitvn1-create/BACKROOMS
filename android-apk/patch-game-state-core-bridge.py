from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
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
print("Final Game State Core local-command bridge applied.")
