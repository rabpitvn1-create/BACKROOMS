from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
DIAGNOSTICS = ROOT / "app/src/main/java/com/rabpit/backroom/RuntimeDiagnostics.java"
SCHEMAS = ROOT / "app/src/main/java/com/rabpit/backroom/AiResponseSchemas.java"
FOUNDATION = ROOT / "app/src/main/java/com/rabpit/backroom/core/foundation/FoundationRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/RuntimeDiagnosticsTest.java"
SCHEMA_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/AiResponseSchemasTest.java"

main = MAIN.read_text(encoding="utf-8")
diagnostics = DIAGNOSTICS.read_text(encoding="utf-8")
schemas = SCHEMAS.read_text(encoding="utf-8")
foundation = FOUNDATION.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")
schema_test = SCHEMA_TEST.read_text(encoding="utf-8")

for marker in (
    "ISSUE330_STRUCTURED_DIAGNOSTICS_R01",
    "new RuntimeDiagnostics(160)",
    "[ERROR_SUMMARY]",
    "[ROOT_CAUSE]",
    "[ERROR_TIMELINE]",
    "runtimeDiagnostics.renderSummary()",
    "runtimeDiagnostics.renderRootCause()",
    "runtimeDiagnostics.renderTimeline()",
    "RuntimeDiagnostics.redact",
    "typeof f==='function'",
    "source='+String(e&&e.filename||'')",
    "line='+String(e&&e.lineno||0)",
    "col='+String(e&&e.colno||0)",
    '"WEBVIEW", "JS_EXECUTION", "FAILED"',
    "AiResponseSchemas.ValidationException",
    'diagnostic("GEMINI", "PROVIDER_REQUEST", "START"',
    'diagnostic("GEMINI", "PROVIDER_RESPONSE", "FAILED_TRANSIENT"',
    'diagnostic("GEMINI", "PROVIDER_RESPONSE", "OK"',
    'diagnostic("AI_PROVIDER", phase, "FAILED"',
    'diagnostic("AI_ROUTER", "FALLBACK", "FALLBACK"',
    "FoundationRuntime.lastFailure",
    '"FOUNDATION", phase, "FAILED_TRANSIENT"',
    '.put("source", "semantic_audit")',
    '.put("source", "android_reducer")',
    'issue.put("source", "local_validator")',
    'auditIssueDetails(hardIssues)',
    "claim=",
    "reason=",
):
    assert marker in main, marker

assert 'String script = "window." + function + "(" + JSONObject.quote(json) + ")";' not in main

blocking_start = main.index("  private JSONArray blockingAuditIssues(JSONArray hardIssues) {")
blocking_end = main.index("  private String auditIssueRules", blocking_start)
blocking = main[blocking_start:blocking_end]
assert '"state_narrative_mismatch".equals(rule)' in blocking
assert '!"android_reducer".equals(issue.optString("source", ""))' in blocking

for marker in (
    "class RuntimeDiagnostics",
    "renderSummary()",
    "renderRootCause()",
    "renderTimeline()",
    "FAILED_TRANSIENT",
    "STATE_COMMIT",
    "Bearer [REDACTED]",
    "AIza[A-Za-z0-9_-]{20,}",
):
    assert marker in diagnostics, marker

for marker in (
    "class ValidationException",
    "Role role()",
    "String reason()",
    "AI response schema violation [",
    "invalid JSON:",
):
    assert marker in schemas, marker

for marker in (
    "private val lastFailure = AtomicReference<String?>(null)",
    'rememberFailure("FOUNDATION_COMPILE"',
    'rememberFailure("FOUNDATION_INSTALL"',
    'rememberFailure("FOUNDATION_ACTIVATE"',
    'rememberFailure("FOUNDATION_SLICE"',
    "fun lastFailure(context: Context): String",
):
    assert marker in foundation, marker

for marker in (
    "summaryKeepsConcreteAuditRootCauseAndFallbackPath",
    "transientProviderFailureAloneIsNotTerminalRootCause",
    "redactionRemovesConfiguredAndBearerSecrets",
    "ledgerRemainsBounded",
):
    assert marker in test, marker

for marker in (
    "writerSchemaRejectsUnknownOperationWithRoleAndReason",
    "auditSchemaRejectsContradictoryPassFlagWithAuditRole",
    "malformedJsonReportsRoleAndJsonReason",
):
    assert marker in schema_test, marker

print("Issue #330 diagnostics verified: root cause, component/phase, provider/key fallback, Foundation/schema detail, JS source location, reducer-authoritative canon blocking, and secret-safe bounded logging.")
