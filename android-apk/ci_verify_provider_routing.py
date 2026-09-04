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


# Canonical active text router.
generate = method_block("  private String generateText(String prompt) throws Exception ")
assert "AiProviderRouter.route(" in generate
assert "this::hakuFallbackText" in generate
assert "this::lunaText" in generate
assert generate.index("this::hakuFallbackText") < generate.index("this::lunaText")
assert "(provider, response) -> parseModelJson(response).toString()" in generate
assert generate.index("this::providerFallbackEligible") < generate.index("parseModelJson(response)")
assert "geminiText(" not in generate
assert "geminiKeyFallbackText(" not in generate
assert '"AI provider selected: " + provider' in generate
assert 'fromProvider + " failed; fallback to " + toProvider' in generate

# A malformed successful HTTP response must fail inside the provider route so Haku
# can fall through to Luna instead of escaping later from parseModelJson().
assert "AI trả JSON không hợp lệ." in java

# HAKU primary should be configured to produce parseable JSON directly, not merely
# rely on Luna after a malformed/truncated completion.
haku = method_block("  private String hakuFallbackText(String prompt) throws Exception ")
assert '"role", "system"' in haku
assert "Return exactly one valid JSON object." in haku
assert '.put("temperature", 0.2)' in haku
assert '.put("max_tokens", 3200)' in haku
assert '.put("temperature", 0.75)' not in haku
assert '.put("max_tokens", 1800)' not in haku
haku_post = method_block("  private String postJsonHakuFallback(JSONObject payload) throws Exception ")
assert (
    "connection.setReadTimeout(30000);" in haku_post
    or "connection.setReadTimeout(providerTimeout(30000, 500));" in haku_post
)
assert "connection.setReadTimeout(22000);" not in haku_post

# When the persistent runtime finalizer is installed, semantic validation happens
# inside each provider attempt and transport timeouts are capped by one turn budget.
if "PERSISTENT_FOUNDATION_RUNTIME_R01" in java:
    structured = method_block(
        "  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception "
    )
    assert "AiProviderRouter.route(" in structured
    assert "AiResponseSchemas.validate(role" in structured
    assert structured.index("this::hakuFallbackText") < structured.index("this::lunaText")
    assert "activeTurnBudget.set(budget)" in structured
    assert "activeTurnBudget.remove()" in structured

# Audit and procedural helpers must route through the same policy, never Gemini.
audit = method_block("  private String geminiAuditText(String prompt, int excludedIndex) throws Exception ")
assert "return generateText(prompt);" in audit
assert "geminiKeyFallbackText(" not in audit
if "  private String geminiLevelGenerationText(String prompt) throws Exception " in java:
    level_generation = method_block("  private String geminiLevelGenerationText(String prompt) throws Exception ")
    assert "return generateText(prompt);" in level_generation
    assert "geminiModelMatrixPolicy(" not in level_generation

# Snapshot is intentionally network-free while Gemini is locked.
snapshot = method_block("  private void requestSnapshotInternal(String stateJson) ")
assert "geminiImage(" not in snapshot
assert "generativelanguage.googleapis.com" not in snapshot

# Defense-in-depth lock remains explicit and reversible.
assert "private static final boolean GEMINI_RUNTIME_ENABLED = false;" in java
assert "Gemini runtime intentionally locked." in java
assert "geminiText(prompt)" not in java
assert "geminiModelMatrixPolicy(prompt" not in java

# Credentials/config stay packaged for reversible re-enable, but they are not router inputs.
for marker in [
    'buildConfigField "String", "HAKU_API_KEY"',
    'buildConfigField "String", "LUNA_API_KEY"',
    'buildConfigField "String", "LUNA_BASE_URL"',
    'buildConfigField "String", "LUNA_MODEL"',
    'buildConfigField "String", "GEMINI_API_KEY_1"',
    'buildConfigField "String", "GEMINI_API_KEY_5"',
]:
    assert marker in gradle, marker
assert '"claude-haiku-4-5-20251001"' in java
assert 'baseUrl + "/chat/completions"' in java
assert '"patch-provider-haku-luna-lock-gemini-final.py"' in chain
assert '"patch-haku-json-reliability-final.py"' in chain

# Local/Core validation remains in front of provider generation. A handled rejection
# returns before any provider request, so invalid deterministic actions cannot consume AI.
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
first_provider = min(provider_calls)
assert local_call < handled < handled_return < first_provider

print("Provider routing verified: HAKU -> LUNA -> controlled failure; HAKU strict JSON reliability contract active; malformed provider JSON falls back; Gemini locked; validation precedes provider calls.")
