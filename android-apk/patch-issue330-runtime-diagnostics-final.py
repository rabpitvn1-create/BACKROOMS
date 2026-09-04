from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
VERIFY = ROOT / "ci_verify_issue330_runtime_diagnostics.py"
MARKER = "ISSUE330_STRUCTURED_DIAGNOSTICS_R01"


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"method signature missing: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"method opening brace missing: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                while end < len(source) and source[end] in "\r\n":
                    end += 1
                return start, end
    raise RuntimeError(f"method closing brace missing: {signature}")


def replace_method(source: str, signature: str, replacement: str) -> str:
    start, end = method_bounds(source, signature)
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
if MARKER in main:
    raise SystemExit(0)

# ---------------------------------------------------------------------------
# Structured bounded diagnostics used by the TXT export.
# ---------------------------------------------------------------------------
field_anchor = '  private volatile String pendingDebugLog = "";\n'
main = replace_once(
    main,
    field_anchor,
    field_anchor + '  private final RuntimeDiagnostics runtimeDiagnostics = new RuntimeDiagnostics(160);\n',
    "runtime diagnostics field",
)

helpers = r'''  /* ISSUE330_STRUCTURED_DIAGNOSTICS_R01 */
  private String diagnosticThrowable(Throwable error) {
    if (error == null) return "";
    StringBuilder out = new StringBuilder();
    Throwable cursor = error;
    int depth = 0;
    while (cursor != null && depth < 4) {
      if (out.length() > 0) out.append(" <- ");
      String message = cursor.getMessage() == null ? "" : cursor.getMessage();
      out.append(cursor.getClass().getSimpleName()).append(':').append(message);
      Throwable[] suppressed = cursor.getSuppressed();
      for (int i = 0; suppressed != null && i < Math.min(2, suppressed.length); i++) {
        Throwable child = suppressed[i];
        out.append(" [suppressed ").append(child.getClass().getSimpleName()).append(':')
          .append(child.getMessage() == null ? "" : child.getMessage()).append(']');
      }
      cursor = cursor.getCause();
      depth++;
    }
    return sanitizeDebugText(out.toString());
  }

  private void diagnostic(
      String component,
      String phase,
      String result,
      String provider,
      String credential,
      Throwable error,
      String message
  ) {
    String errorType = error == null ? "" : error.getClass().getSimpleName();
    String detail = message == null ? "" : message;
    String chain = diagnosticThrowable(error);
    if (!chain.isEmpty()) detail = detail.isEmpty() ? chain : detail + "; cause=" + chain;
    runtimeDiagnostics.record(
      component, phase, result, provider, credential, errorType, sanitizeDebugText(detail));
  }
'''
if helpers.strip() not in main:
    main = replace_once(main, "  private String debugTimestamp() {\n", helpers + "\n  private String debugTimestamp() {\n", "diagnostic helpers")

# Reuse the exact secret set already owned by MainActivity, while adding generic API-key
# pattern redaction through the testable RuntimeDiagnostics helper.
sanitize_signature = "  private String sanitizeDebugText(String input) "
sanitize_start, sanitize_end = method_bounds(main, sanitize_signature)
sanitize = main[sanitize_start:sanitize_end]
if "RuntimeDiagnostics.redact" not in sanitize:
    sanitize = sanitize.replace("    return safe;", "    return RuntimeDiagnostics.redact(safe, configuredSecrets);", 1)
    main = main[:sanitize_start] + sanitize + main[sanitize_end:]

# Export a human-readable summary/root cause first, then the structured timeline and the
# legacy native ring for backwards-compatible investigation.
export_signature = "  private void requestDebugLogExport(String contextJson) "
export_start, export_end = method_bounds(main, export_signature)
export_method = main[export_start:export_end]
export_method = replace_once(
    export_method,
    '    output.append("androidApi=").append(android.os.Build.VERSION.SDK_INT).append("\\n\\n");\n    output.append("[GAME_CONTEXT]\\n");',
    '    output.append("androidApi=").append(android.os.Build.VERSION.SDK_INT).append("\\n\\n");\n'
    '    output.append("[ERROR_SUMMARY]\\n").append(sanitizeDebugText(runtimeDiagnostics.renderSummary())).append("\\n\\n");\n'
    '    output.append("[ROOT_CAUSE]\\n").append(sanitizeDebugText(runtimeDiagnostics.renderRootCause())).append("\\n\\n");\n'
    '    output.append("[ERROR_TIMELINE]\\n").append(sanitizeDebugText(runtimeDiagnostics.renderTimeline())).append("\\n\\n");\n'
    '    output.append("[GAME_CONTEXT]\\n");',
    "TXT summary sections",
)
main = main[:export_start] + export_method + main[export_end:]

# ---------------------------------------------------------------------------
# WebView bridge: native diagnostic events must not call undefined JS callbacks.
# The old emit() blindly invoked window.<event>(), which is exactly why every native-only
# backroomFoundation event produced the observed web:jsError "Script error." entry.
# ---------------------------------------------------------------------------
emit = r'''  private void emit(String function, String json) {
    if ("backroomProvider".equals(function)
        || "backroomAudit".equals(function)
        || "backroomFoundation".equals(function)
        || function.endsWith("Error")) {
      recordDebugEvent(function, json);
    }
    if ("backroomError".equals(function)) {
      diagnostic("TURN_PIPELINE", "TURN_RESOLVE", "FAILED_FINAL", "", "", null, json);
    }
    String script = "(function(){var n=" + JSONObject.quote(function)
      + ";var f=window[n];if(typeof f==='function'){f(" + JSONObject.quote(json) + ");}})();";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }'''
main = replace_method(main, "  private void emit(String function, String json) ", emit)

# Preserve actual ErrorEvent source/line/column/stack instead of collapsing everything to
# the useless literal "Script error.". Promise failures get the same treatment.
old_js_error = "Android.recordDebugEvent('jsError',String(e&&e.message||'JavaScript error'))"
new_js_error = "Android.recordDebugEvent('jsError','message='+String(e&&e.message||'JavaScript error')+';source='+String(e&&e.filename||'')+';line='+String(e&&e.lineno||0)+';col='+String(e&&e.colno||0)+';stack='+String(e&&e.error&&e.error.stack||''))"
main = replace_once(main, old_js_error, new_js_error, "rich JS error diagnostics")
old_js_promise = "Android.recordDebugEvent('jsPromise',String(e&&e.reason||'Unhandled promise rejection'))"
new_js_promise = "Android.recordDebugEvent('jsPromise','reason='+String(e&&e.reason||'Unhandled promise rejection')+';stack='+String(e&&e.reason&&e.reason.stack||''))"
main = replace_once(main, old_js_promise, new_js_promise, "rich JS promise diagnostics")

bridge_record = r'''    @JavascriptInterface public void recordDebugEvent(String kind, String detail) {
      String resolvedKind = kind == null ? "web" : kind;
      MainActivity.this.recordDebugEvent("web:" + resolvedKind, detail);
      if ("jsError".equals(resolvedKind) || "jsPromise".equals(resolvedKind)) {
        MainActivity.this.runtimeDiagnostics.record(
          "WEBVIEW", "JS_EXECUTION", "FAILED", "", "",
          "jsError".equals(resolvedKind) ? "JavaScriptError" : "UnhandledPromise",
          MainActivity.this.sanitizeDebugText(detail));
      }
    }'''
main = replace_method(
    main,
    "    @JavascriptInterface public void recordDebugEvent(String kind, String detail) ",
    bridge_record,
)

# ---------------------------------------------------------------------------
# Provider/key/schema diagnostics.
# ---------------------------------------------------------------------------
provider_summary = r'''  private String providerErrorSummary(String provider, Exception error) {
    String category = "runtime";
    int status = 0;
    if (error instanceof AiResponseSchemas.ValidationException) {
      category = "validation";
    } else if (error instanceof HttpError) {
      category = "http";
      status = ((HttpError) error).status;
    } else if (error instanceof java.net.SocketTimeoutException) {
      category = "timeout";
    } else if (networkFailure(error)) {
      category = "network";
    } else {
      String message = error != null && error.getMessage() != null ? error.getMessage() : "";
      if (message.contains("schema violation") || message.contains("JSON")) category = "validation";
      else if (message.contains("không trả")) category = "empty";
    }
    String type = error == null ? "Unknown" : error.getClass().getSimpleName();
    String detail = diagnosticThrowable(error);
    if (detail.length() > 700) detail = detail.substring(0, 700) + "…";
    return provider + " category=" + category + (status > 0 ? " status=" + status : "")
      + " type=" + type + (detail.isEmpty() ? "" : " message=" + detail);
  }'''
main = replace_method(main, "  private String providerErrorSummary(String provider, Exception error) ", provider_summary)

provider_observer = r'''  private AiProviderRouter.Observer providerObserver() {
    return new AiProviderRouter.Observer() {
      @Override public void onSelected(String provider) {
        diagnostic("AI_PROVIDER", "PROVIDER_ROUTE", "SELECTED", provider, "", null, "selected");
        emit("backroomProvider", "AI provider selected: " + provider);
      }

      @Override public void onFallback(String fromProvider, String toProvider, Exception error) {
        diagnostic("AI_ROUTER", "FALLBACK", "FALLBACK", fromProvider + "->" + toProvider, "", error,
          providerErrorSummary(fromProvider, error));
        emit("backroomProvider", fromProvider + " failed (" + providerErrorSummary(fromProvider, error)
          + "); fallback to " + toProvider);
      }

      @Override public void onFailure(String provider, Exception error) {
        String phase = error instanceof AiResponseSchemas.ValidationException ? "SCHEMA_VALIDATE" : "PROVIDER_RESPONSE";
        diagnostic("AI_PROVIDER", phase, "FAILED", provider, "", error, providerErrorSummary(provider, error));
        emit("backroomProviderError", providerErrorSummary(provider, error));
      }
    };
  }'''
main = replace_method(main, "  private AiProviderRouter.Observer providerObserver() ", provider_observer)

# Instrument every Gemini credential lane. These events are metadata-only: slot K1..K6,
# HTTP/exception category and success/failure, never the secret value or response body.
gemini_signature = "  private String geminiKeyFallbackText(String prompt, int excludedIndex, int maxOutputTokens, boolean rememberWorker) throws Exception "
gemini_start, gemini_end = method_bounds(main, gemini_signature)
gemini = main[gemini_start:gemini_end]
gemini = replace_once(
    gemini,
    '        emit("backroomProvider", "Gemini K" + (keyIndex + 1));\n        try {',
    '        emit("backroomProvider", "Gemini K" + (keyIndex + 1));\n'
    '        diagnostic("GEMINI", "PROVIDER_REQUEST", "START", "GEMINI", "K" + (keyIndex + 1), null, "request started");\n'
    '        try {',
    "Gemini request start diagnostic",
)
gemini = replace_once(
    gemini,
    '          if (rememberWorker) lastGeminiWorker = keyIndex;\n          return responseText.toString();',
    '          if (rememberWorker) lastGeminiWorker = keyIndex;\n'
    '          diagnostic("GEMINI", "PROVIDER_RESPONSE", "OK", "GEMINI", "K" + (keyIndex + 1), null, "response accepted");\n'
    '          return responseText.toString();',
    "Gemini success diagnostic",
)
gemini = replace_once(
    gemini,
    '          int code = error instanceof HttpError ? ((HttpError)error).status : 0;\n',
    '          int code = error instanceof HttpError ? ((HttpError)error).status : 0;\n'
    '          diagnostic("GEMINI", "PROVIDER_RESPONSE", "FAILED_TRANSIENT", "GEMINI", "K" + (keyIndex + 1), error, providerErrorSummary("Gemini K" + (keyIndex + 1), error));\n',
    "Gemini failure diagnostic",
)
main = main[:gemini_start] + gemini + main[gemini_end:]

# ---------------------------------------------------------------------------
# Foundation diagnostics. Foundation still falls back safely, but the reason is no longer
# swallowed: FoundationRuntime exposes the build/activate/slice failure that triggered it.
# ---------------------------------------------------------------------------
foundation = r'''  private String foundationPacket(JSONObject before, String action, JSONObject rolls, String role, String turnId) throws Exception {
    diagnostic("FOUNDATION", "FOUNDATION_SLICE", "START", "", "", null, "role=" + role);
    try {
      String packet = com.rabpit.backroom.core.foundation.FoundationRuntime.buildSlice(
        MainActivity.this,
        turnId,
        requireGameCore().foundationStateProjection(),
        before.toString(),
        action,
        rolls.toString(),
        role);
      if (packet == null || packet.trim().isEmpty()) {
        String failure = com.rabpit.backroom.core.foundation.FoundationRuntime.lastFailure(MainActivity.this);
        String phase = "FOUNDATION_SLICE";
        String errorType = "FoundationFallback";
        String message = "empty Foundation slice";
        if (failure != null && !failure.trim().isEmpty()) {
          try {
            JSONObject detail = new JSONObject(failure);
            phase = detail.optString("phase", phase);
            errorType = detail.optString("errorType", errorType);
            message = detail.optString("message", failure);
          } catch (Exception ignored) {
            message = failure;
          }
        }
        runtimeDiagnostics.record("FOUNDATION", phase, "FAILED_TRANSIENT", "", "", errorType, sanitizeDebugText(message));
        diagnostic("FOUNDATION", "LEGACY_FALLBACK", "FALLBACK", "", "", null, "role=" + role);
        emit("backroomFoundation", "legacy-fallback role=" + role + "; phase=" + phase + "; reason=" + sanitizeDebugText(message));
        return com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
          MainActivity.this, before.toString(), action, rolls.toString());
      }
      diagnostic("FOUNDATION", "FOUNDATION_SLICE", "OK", "", "", null, "role=" + role + " slice=v1");
      emit("backroomFoundation", "active role=" + role + " slice=v1");
      return packet;
    } catch (Exception error) {
      diagnostic("FOUNDATION", "FOUNDATION_SLICE", "FAILED", "", "", error, "role=" + role);
      throw error;
    }
  }'''
main = replace_method(
    main,
    "  private String foundationPacket(JSONObject before, String action, JSONObject rolls, String role, String turnId) ",
    foundation,
)

# ---------------------------------------------------------------------------
# Canon audit: preserve claim/reason/source and stop AI-only state mismatch opinions from
# hard-locking a turn after repair. A state_narrative_mismatch remains blocking when Android's
# deterministic reducer proves an operation was rejected.
# ---------------------------------------------------------------------------
run_signature = "  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, String scope, int excludedWorker, String turnId, TurnBudget budget) throws Exception "
run_start, run_end = method_bounds(main, run_signature)
run_audit = main[run_start:run_end]
run_audit = replace_once(
    run_audit,
    '    JSONArray issues = result.optJSONArray("issues");\n    if (issues == null) issues = new JSONArray();\n    return new JSONObject().put("scope", scope).put("issues", issues);',
    '    JSONArray issues = result.optJSONArray("issues");\n'
    '    if (issues == null) issues = new JSONArray();\n'
    '    for (int i = 0; i < issues.length(); i++) {\n'
    '      JSONObject issue = issues.optJSONObject(i);\n'
    '      if (issue != null) issue.put("source", "semantic_audit").put("scope", scope);\n'
    '    }\n'
    '    return new JSONObject().put("scope", scope).put("issues", issues);',
    "semantic audit source",
)
main = main[:run_start] + run_audit + main[run_end:]

rejected_signature = "  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception "
rejected_start, rejected_end = method_bounds(main, rejected_signature)
rejected = main[rejected_start:rejected_end]
rejected = replace_once(
    rejected,
    '          .put("severity", "hard")\n          .put("claim", type)',
    '          .put("severity", "hard")\n          .put("source", "android_reducer")\n          .put("claim", type)',
    "Android reducer issue source",
)
main = main[:rejected_start] + rejected + main[rejected_end:]

local_knowledge = r'''  private JSONArray localKnowledgeIssues(JSONObject before, JSONObject generated) throws Exception {
    JSONObject result = new JSONObject(com.rabpit.backroom.core.knowledge.KnowledgeLocalValidator.validate(
      MainActivity.this, before.toString(), generated.toString()));
    JSONArray issues = result.optJSONArray("issues");
    if (issues == null) issues = new JSONArray();
    for (int i = 0; i < issues.length(); i++) {
      JSONObject issue = issues.optJSONObject(i);
      if (issue != null && !issue.has("source")) issue.put("source", "local_validator");
    }
    return issues;
  }'''
main = replace_method(main, "  private JSONArray localKnowledgeIssues(JSONObject before, JSONObject generated) ", local_knowledge)

blocking = r'''  private JSONArray blockingAuditIssues(JSONArray hardIssues) {
    JSONArray blocking = new JSONArray();
    if (hardIssues == null) return blocking;
    for (int i = 0; i < hardIssues.length(); i++) {
      JSONObject issue = hardIssues.optJSONObject(i);
      if (issue == null || !"hard".equalsIgnoreCase(issue.optString("severity", ""))) continue;
      String rule = lower(issue.optString("rule", "")).trim();
      if (!auditRuleCanBlock(rule)) continue;
      // Semantic auditors may request repair for a narrative/state mismatch, but only the
      // deterministic Android reducer is authoritative enough to hard-block the whole turn.
      if ("state_narrative_mismatch".equals(rule)
          && !"android_reducer".equals(issue.optString("source", ""))) continue;
      String claim = issue.optString("claim", "").trim();
      String reason = issue.optString("reason", "").trim();
      if (claim.isEmpty() || reason.isEmpty()) continue;
      blocking.put(issue);
    }
    return blocking;
  }'''
main = replace_method(main, "  private JSONArray blockingAuditIssues(JSONArray hardIssues) ", blocking)

audit_rules_start, audit_rules_end = method_bounds(main, "  private String auditIssueRules(JSONArray issues) ")
audit_details = r'''

  private String auditIssueDetails(JSONArray issues) {
    if (issues == null || issues.length() == 0) return "none";
    StringBuilder out = new StringBuilder();
    int limit = Math.min(4, issues.length());
    for (int i = 0; i < limit; i++) {
      JSONObject issue = issues.optJSONObject(i);
      if (issue == null) continue;
      if (out.length() > 0) out.append(" || ");
      out.append("source=").append(issue.optString("source", "unknown"))
        .append(" rule=").append(issue.optString("rule", "unknown"))
        .append(" claim=").append(issue.optString("claim", ""))
        .append(" reason=").append(issue.optString("reason", ""));
    }
    String value = sanitizeDebugText(out.toString());
    return value.length() <= 1800 ? value : value.substring(0, 1800) + "…";
  }
'''
main = main[:audit_rules_end] + audit_details + main[audit_rules_end:]

old_audit_emit = '            emit("backroomAudit", "risk=" + risk + "; repaired=" + repaired + "; hard=" + hardIssues.length() + "; blocking=" + blockingIssues.length() + "; rules=" + auditIssueRules(hardIssues));'
new_audit_emit = '            String auditDetails = auditIssueDetails(hardIssues);\n' \
    '            emit("backroomAudit", "risk=" + risk + "; repaired=" + repaired + "; hard=" + hardIssues.length() + "; blocking=" + blockingIssues.length() + "; rules=" + auditIssueRules(hardIssues) + "; details=" + auditDetails);\n' \
    '            diagnostic("CANON_AUDIT", "CANON_GATE", blockingIssues.length() > 0 ? "BLOCKED" : "REPAIR_ONLY", "GEMINI", "", null, auditDetails);'
main = replace_once(main, old_audit_emit, new_audit_emit, "audit claim/reason diagnostics")

# Start a correlation as soon as the ActionRuntime gives us the stable turn id.
turn_anchor = '          final String foundationTurnId = actionStart.optString("turnId", "");\n'
main = replace_once(
    main,
    turn_anchor,
    turn_anchor
    + '          runtimeDiagnostics.beginTurn(foundationTurnId.isEmpty() ? "turn-" + before.optInt("turn", 0) : foundationTurnId);\n'
    + '          diagnostic("TURN_PIPELINE", "TURN_RESOLVE", "START", "", "", null, "actionKind=" + actionKind);\n',
    "turn diagnostic correlation",
)

# A successful commit clears transient provider/key failures from ERROR_SUMMARY while keeping
# them visible in the timeline/fallbackPath.
commit_anchor = '''          com.rabpit.backroom.core.foundation.FoundationRuntime.warm(
            getApplicationContext(), requireGameCore().foundationStateProjection());
          emit("backroomTurn", state.toString());
'''
commit_new = '''          com.rabpit.backroom.core.foundation.FoundationRuntime.warm(
            getApplicationContext(), requireGameCore().foundationStateProjection());
          diagnostic("TURN_PIPELINE", "STATE_COMMIT", "OK", "", "", null, "turn committed");
          emit("backroomTurn", state.toString());
'''
main = replace_once(main, commit_anchor, commit_new, "successful state commit diagnostic")

for required in (
    MARKER,
    "new RuntimeDiagnostics(160)",
    "[ERROR_SUMMARY]",
    "[ROOT_CAUSE]",
    "[ERROR_TIMELINE]",
    "RuntimeDiagnostics.redact",
    "typeof f==='function'",
    "source='+String(e&&e.filename||'')",
    "AiResponseSchemas.ValidationException",
    'diagnostic("GEMINI", "PROVIDER_RESPONSE", "FAILED_TRANSIENT"',
    "FoundationRuntime.lastFailure",
    '.put("source", "semantic_audit")',
    '.put("source", "android_reducer")',
    '"state_narrative_mismatch".equals(rule)',
    '"android_reducer".equals(issue.optString("source", ""))',
    "auditIssueDetails(hardIssues)",
    'diagnostic("TURN_PIPELINE", "STATE_COMMIT", "OK"',
):
    if required not in main:
        raise RuntimeError("Issue #330 final diagnostic marker missing: " + required)

if 'String script = "window." + function + "(" + JSONObject.quote(json) + ")";' in main:
    raise RuntimeError("unsafe emit still invokes undefined JS callbacks")

MAIN.write_text(main, encoding="utf-8")
print("Issue #330 fixed: structured root-cause diagnostics, safe WebView emit, per-key/provider/schema/Foundation traces, and reducer-authoritative state mismatch blocking.")
runpy.run_path(str(VERIFY), run_name="__main__")
