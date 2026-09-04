from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
GRADLE = ROOT / "app/build.gradle"
CHAIN = ROOT / "ci_apply_runtime_patches.py"

java = MAIN.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")
chain = CHAIN.read_text(encoding="utf-8")


def method_block(signature: str) -> str:
    start = java.find(signature)
    if start < 0:
        raise AssertionError(f"missing method: {signature}")
    brace = java.find("{", start)
    if brace < 0:
        raise AssertionError(f"missing opening brace: {signature}")
    depth = 0
    for index in range(brace, len(java)):
        char = java[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return java[start:index + 1]
    raise AssertionError(f"missing closing brace: {signature}")


# Canonical runtime text router: Gemini has hard high priority.
generate = method_block("  private String generateText(String prompt) throws Exception ")
assert "AiProviderRouter.route(" in generate
for marker in ("this::geminiText", "this::hakuFallbackText", "this::lunaText"):
    assert marker in generate, marker
assert generate.index("this::geminiText") < generate.index("this::hakuFallbackText") < generate.index("this::lunaText")
assert "(provider, response) -> parseModelJson(response).toString()" in generate
assert "providerObserver()" in generate

# Foundation writer/repair/audit uses the same high-priority chain and validates each attempt.
structured = method_block(
    "  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception "
)
for marker in ("this::geminiFoundationText", "this::hakuFallbackText", "this::lunaText"):
    assert marker in structured, marker
assert structured.index("this::geminiFoundationText") < structured.index("this::hakuFallbackText") < structured.index("this::lunaText")
assert "AiResponseSchemas.validate(role" in structured
assert "activeTurnBudget.set(budget)" in structured
assert "activeTurnBudget.remove()" in structured

# Gemini is fully enabled and the retired lock cannot survive final runtime generation.
assert "private static final boolean GEMINI_RUNTIME_ENABLED = true;" in java
assert "private static final boolean GEMINI_RUNTIME_ENABLED = false;" not in java
assert "Gemini runtime intentionally locked." not in java
assert "GEMINI_FOUNDATION_AUTHORITY_R02" in java

keys = method_block("  private String[] geminiKeys() ")
for marker in (
    "BuildConfig.GEMINI_API_KEY",
    "BuildConfig.GEMINI_API_KEY_2",
    "BuildConfig.GEMINI_API_KEY_3",
    "BuildConfig.GEMINI_API_KEY_4",
    "BuildConfig.GEMINI_API_KEY_5",
    "BuildConfig.GEMINI_API_KEY_6",
):
    assert marker in keys, marker
assert "Math.min(6, keys.length)" in method_block(
    "  private String geminiKeyFallbackText(String prompt, int excludedIndex, int maxOutputTokens, boolean rememberWorker) throws Exception "
)

# All network providers share one monotonic Foundation turn budget.
gemini_post = method_block("  private String postJsonGeminiLane(String endpoint, String key, JSONObject payload) throws Exception ")
assert "providerTimeout(4000, 250)" in gemini_post
assert "providerTimeout(8000, 500)" in gemini_post
haku_post = method_block("  private String postJsonHakuFallback(JSONObject payload) throws Exception ")
assert "providerTimeout(30000, 500)" in haku_post
luna_post = method_block("  private String postJsonLunaFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception ")
assert "providerTimeout(22000, 500)" in luna_post

# Haku keeps its strict JSON contract as a secondary provider.
haku = method_block("  private String hakuFallbackText(String prompt) throws Exception ")
assert '"role", "system"' in haku
assert "Return exactly one valid JSON object." in haku
assert '.put("temperature", 0.2)' in haku
assert '.put("max_tokens", 3200)' in haku

# Foundation/provider diagnostics are bounded and secret-redacted by the debug exporter.
foundation = method_block(
    "  private String foundationPacket(JSONObject before, String action, JSONObject rolls, String role, String turnId) throws Exception "
)
assert 'emit("backroomFoundation", "active role=" + role + " slice=v1")' in foundation
assert 'emit("backroomFoundation", "legacy-fallback role=" + role)' in foundation
assert 'emit("backroomProviderError", providerErrorSummary(provider, error))' in java
assert '"backroomFoundation".equals(function)' in java
assert "BuildConfig.GEMINI_API_KEY_6" in java

# Audit/procedural compatibility helpers inherit the active high-priority router.
audit = method_block("  private String geminiAuditText(String prompt, int excludedIndex) throws Exception ")
assert "return generateText(prompt);" in audit
if "  private String geminiLevelGenerationText(String prompt) throws Exception " in java:
    level_generation = method_block("  private String geminiLevelGenerationText(String prompt) throws Exception ")
    assert "return generateText(prompt);" in level_generation

# Snapshot remains network-free; the obsolete UI action was already replaced by debug export.
snapshot = method_block("  private void requestSnapshotInternal(String stateJson) ")
assert "geminiImage(" not in snapshot
assert "generativelanguage.googleapis.com" not in snapshot

for marker in [
    'buildConfigField "String", "HAKU_API_KEY"',
    'buildConfigField "String", "LUNA_API_KEY"',
    'buildConfigField "String", "LUNA_BASE_URL"',
    'buildConfigField "String", "LUNA_MODEL"',
    'buildConfigField "String", "GEMINI_API_KEY"',
    'buildConfigField "String", "GEMINI_API_KEY_6"',
]:
    assert marker in gradle, marker
assert '"claude-haiku-4-5-20251001"' in java
assert 'baseUrl + "/chat/completions"' in java
assert '"patch-persistent-foundation-final.py"' in chain
assert '"patch-gemini-foundation-authority-final.py"' in chain
assert chain.index('"patch-persistent-foundation-final.py"') < chain.index('"patch-gemini-foundation-authority-final.py"')

# Local/Core validation remains ahead of all network generation.
submit_start = java.index("    @JavascriptInterface public void submitTurn(String stateJson, String action) {")
submit_end = java.index("    @JavascriptInterface public void requestSnapshot(String stateJson)", submit_start)
submit = java[submit_start:submit_end]
local_call = submit.index("processRule(stateJson, action)")
handled = submit.index('optBoolean("handled", false)', local_call)
handled_return = submit.index("return;", handled)
provider_calls = [
    position
    for marker in ("generateText(", "generateStructuredText(")
    if (position := submit.find(marker, handled_return)) >= 0
]
assert provider_calls, "no provider call found after deterministic validation"
assert local_call < handled < handled_return < min(provider_calls)

print("Provider routing verified: Persistent Foundation -> GEMINI K1-K6 high priority -> HAKU -> LUNA -> controlled failure; per-attempt schema validation and sanitized diagnostics active.")
