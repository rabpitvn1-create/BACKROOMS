from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
VERIFY = ROOT / "ci_verify_issue311_debug_log.py"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def method_end(text: str, signature: str) -> int:
    start = text.find(signature)
    if start < 0:
        raise RuntimeError(f"method signature missing: {signature}")
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"method opening brace missing: {signature}")
    depth = 0
    for index in range(brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index + 1
    raise RuntimeError(f"method closing brace missing: {signature}")


main = MAIN.read_text(encoding="utf-8")

# Storage Access Framework export needs no broad storage permission. The player chooses
# the final destination and Android gives this Activity a one-shot content URI.
if "import android.content.Intent;" not in main:
    main = replace_once(
        main,
        "import android.app.Activity;\n",
        "import android.app.Activity;\nimport android.content.Intent;\nimport android.net.Uri;\n",
        "debug log Android imports",
    )

fields = '''  private static final int DEBUG_LOG_EXPORT_REQUEST = 311;
  private static final int DEBUG_EVENT_LIMIT = 80;
  private final Object debugLogLock = new Object();
  private final java.util.ArrayDeque<String> debugEvents = new java.util.ArrayDeque<>();
  private volatile String pendingDebugLog = "";
'''
if "DEBUG_LOG_EXPORT_REQUEST = 311" not in main:
    main = replace_once(
        main,
        "  private static final boolean GEMINI_RUNTIME_ENABLED = false;\n",
        "  private static final boolean GEMINI_RUNTIME_ENABLED = false;\n" + fields,
        "debug log fields",
    )

helpers = r'''  private String debugTimestamp() {
    return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
      .format(new java.util.Date());
  }

  private String sanitizeDebugText(String input) {
    String safe = input == null ? "" : input;
    String[] configuredSecrets = {
      BuildConfig.HAKU_API_KEY,
      BuildConfig.LUNA_API_KEY,
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };
    for (String secret : configuredSecrets) {
      if (secret != null && secret.length() >= 4) safe = safe.replace(secret, "[REDACTED]");
    }
    safe = safe.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [REDACTED]");
    safe = safe.replaceAll("(?i)sk-[A-Za-z0-9_-]{8,}", "[REDACTED]");
    return safe;
  }

  private void recordDebugEvent(String kind, String detail) {
    String line = debugTimestamp() + " | " + String.valueOf(kind) + " | " + sanitizeDebugText(detail);
    if (line.length() > 6000) line = line.substring(0, 6000) + "…";
    synchronized (debugLogLock) {
      debugEvents.addLast(line);
      while (debugEvents.size() > DEBUG_EVENT_LIMIT) debugEvents.removeFirst();
    }
  }

  private void requestDebugLogExport(String contextJson) {
    StringBuilder output = new StringBuilder();
    output.append("BACKROOMS IN-GAME DEBUG LOG\n");
    output.append("appVersion=").append(BuildConfig.VERSION_NAME)
      .append(" (code ").append(BuildConfig.VERSION_CODE).append(")\n");
    output.append("exportedAt=").append(debugTimestamp()).append("\n");
    output.append("androidApi=").append(android.os.Build.VERSION.SDK_INT).append("\n\n");
    output.append("[GAME_CONTEXT]\n");
    output.append(sanitizeDebugText(contextJson == null ? "{}" : contextJson)).append("\n\n");
    output.append("[RECENT_RUNTIME_EVENTS]\n");
    synchronized (debugLogLock) {
      if (debugEvents.isEmpty()) output.append("(none)\n");
      else for (String line : debugEvents) output.append(line).append('\n');
    }
    pendingDebugLog = sanitizeDebugText(output.toString());
    final String fileName = "backrooms-log-" +
      new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".txt";
    runOnUiThread(() -> {
      try {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, DEBUG_LOG_EXPORT_REQUEST);
      } catch (Exception error) {
        pendingDebugLog = "";
        emit("backroomDebugLogError", "Không thể mở trình lưu file: " + sanitizeDebugText(error.getMessage()));
      }
    });
  }

'''
if "private String sanitizeDebugText(String input)" not in main:
    main = replace_once(main, "  private boolean retryable(int code) {\n", helpers + "  private boolean retryable(int code) {\n", "debug log helpers")

if "@Override protected void onActivityResult(int requestCode, int resultCode, Intent data)" not in main:
    destroy_end = method_end(main, "  @Override protected void onDestroy() ")
    activity_result = r'''

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != DEBUG_LOG_EXPORT_REQUEST) return;
    final String payload = pendingDebugLog;
    pendingDebugLog = "";
    if (resultCode != RESULT_OK || data == null || data.getData() == null) {
      emit("backroomDebugLogError", "Đã hủy xuất log TXT.");
      return;
    }
    final Uri uri = data.getData();
    io.execute(() -> {
      try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
        if (output == null) throw new Exception("Không mở được file đích.");
        output.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
        emit("backroomDebugLogSaved", "Đã lưu log TXT.");
      } catch (Exception error) {
        emit("backroomDebugLogError", "Không thể ghi log TXT: " + sanitizeDebugText(error.getMessage()));
      }
    });
  }
'''
    main = main[:destroy_end] + activity_result + main[destroy_end:]

# Capture provider routing and runtime errors at the native boundary without storing
# entire successful turn payloads. This keeps the ring useful and bounded.
if 'function.endsWith("Error")' not in main:
    emit_start = main.find("  private void emit(String function, String json) ")
    if emit_start < 0:
        raise RuntimeError("emit method missing for debug event capture")
    brace = main.find("{", emit_start)
    main = main[:brace + 1] + '''
    if ("backroomProvider".equals(function) || function.endsWith("Error")) {
      recordDebugEvent(function, json);
    }''' + main[brace + 1:]

# Replace the obsolete disabled Snapshot control with the log export control. Snapshot
# rendering itself stays untouched because Level background snapshots are still used.
main = replace_once(
    main,
    "document.getElementById('snapshotButton')",
    "document.getElementById('debugLogButton')",
    "debug log button presence guard",
)
main = replace_once(
    main,
    "b.id='snapshotButton';b.type='button';b.textContent='Snapshot chưa cấu hình';b.disabled=true;",
    "b.id='debugLogButton';b.type='button';b.textContent='Xuất log TXT';b.addEventListener('click',requestDebugLog);",
    "replace disabled Snapshot button",
)

js_anchor = '      "var actions=document.querySelector(\'.actions\');'
if "function debugLogContext()" not in main:
    pos = main.find(js_anchor)
    if pos < 0:
        raise RuntimeError("debug log JavaScript insertion anchor missing")
    js = '''      "function debugLastAction(){var a=document.getElementById('action'),pending=a?String(a.value||'').trim():'';if(pending)return pending;var log=state&&Array.isArray(state.log)?state.log:[];for(var i=log.length-1;i>=0;i--){var e=log[i];if(e&&e.role==='player')return String(e.text||'');}return '';}" +\n      "function debugPartySummary(){var d=state&&state.partyDetails,m=d&&Array.isArray(d.members)?d.members:[];if(m.length)return m.map(function(x){return {id:String(x&&x.id||''),name:String(x&&x.name||'')};});var p=state&&Array.isArray(state.party)?state.party:[];return p.map(function(x){var o=x&&typeof x==='object'?x:{};return {id:String(o.id||''),name:String(o.name||x||'')};});}" +\n      "function debugLogContext(){var s=state||{},c=s.combat&&s.combat.active===true?s.combat:null,pt=c&&c.partyTurn||null,f=s.flags||{},st=document.getElementById('status'),logs=Array.isArray(s.log)?s.log.slice(-24).map(function(e){return {role:String(e&&e.role||''),text:String(e&&e.text||'')};}):[];return {exportedAt:new Date().toISOString(),turn:s.turn||null,location:String(s.location||''),mode:String(s.mode||''),lastAction:debugLastAction(),party:debugPartySummary(),activeCharacter:pt?{id:String(pt.actorId||''),name:String(pt.actorName||'')}:{id:'kai',name:String(s.player&&s.player.name||'Kai Akechi')},combat:c?{active:true,encounterId:String(c.encounterId||''),entityKey:String(c.entityKey||f.entityEncounterKey||''),entityName:String(c.entityName||''),entityHp:c.entityHp,entityMaxHp:c.entityMaxHp,partyTurn:pt?{round:pt.round,ap:pt.ap,maxAp:pt.maxAp,actorId:pt.actorId,actorName:pt.actorName}:null}:{active:false,entityKey:String(f.entityEncounterKey||'')},provider:{selected:String(window.__backroomProvider||'UNKNOWN'),status:st?String(st.textContent||''):''},recentLog:logs};}" +\n      "function requestDebugLog(){var s=document.getElementById('status');if(!window.Android||typeof Android.requestDebugLog!=='function'){if(s)s.textContent='Không tìm thấy Android log bridge.';return;}try{Android.requestDebugLog(JSON.stringify(debugLogContext()));if(s)s.textContent='Chọn vị trí lưu file log TXT…';}catch(e){if(s)s.textContent='Không thể chuẩn bị log TXT.';}}" +\n      "window.requestDebugLog=requestDebugLog;window.backroomDebugLogSaved=function(message){var s=document.getElementById('status');if(s)s.textContent=message||'Đã lưu log TXT.';};window.backroomDebugLogError=function(message){var s=document.getElementById('status');if(s)s.textContent=message||'Xuất log TXT thất bại.';};window.addEventListener('error',function(e){try{if(window.Android&&typeof Android.recordDebugEvent==='function')Android.recordDebugEvent('jsError',String(e&&e.message||'JavaScript error'));}catch(_){}});window.addEventListener('unhandledrejection',function(e){try{if(window.Android&&typeof Android.recordDebugEvent==='function')Android.recordDebugEvent('jsPromise',String(e&&e.reason||'Unhandled promise rejection'));}catch(_){}});" +\n'''
    main = main[:pos] + js + main[pos:]

bridge_start = main.find("  private class GameBridge {")
bridge_end = main.find("\n  }\n\n  private static class SnapshotImage", bridge_start)
if bridge_start < 0 or bridge_end < 0:
    raise RuntimeError("GameBridge boundary missing for debug log methods")
if "@JavascriptInterface public void requestDebugLog(String contextJson)" not in main[bridge_start:bridge_end]:
    bridge_methods = r'''

    @JavascriptInterface public void requestDebugLog(String contextJson) {
      requestDebugLogExport(contextJson);
    }

    @JavascriptInterface public void recordDebugEvent(String kind, String detail) {
      MainActivity.this.recordDebugEvent(kind == null ? "web" : "web:" + kind, detail);
    }
'''
    main = main[:bridge_end] + bridge_methods + main[bridge_end:]

for marker in (
    "Intent.ACTION_CREATE_DOCUMENT",
    'intent.setType("text/plain")',
    "DEBUG_EVENT_LIMIT = 80",
    "BuildConfig.HAKU_API_KEY",
    "BuildConfig.LUNA_API_KEY",
    "BuildConfig.GEMINI_API_KEY_5",
    "function debugLogContext()",
    "b.id='debugLogButton'",
    "b.textContent='Xuất log TXT'",
    "@JavascriptInterface public void requestDebugLog(String contextJson)",
    'function.endsWith("Error")',
):
    if marker not in main:
        raise RuntimeError("Issue #311 debug log contract missing: " + marker)
if "b.id='snapshotButton'" in main:
    raise RuntimeError("Obsolete Snapshot action button survived issue #311 finalizer")

MAIN.write_text(main, encoding="utf-8")
print("Issue #311 debug log export installed: bounded/redacted diagnostics via Android SAF TXT export.")
runpy.run_path(str(VERIFY), run_name="__main__")
