from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def method_bounds(signature: str) -> tuple[int, int]:
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
                end = index + 1
                while end < len(text) and text[end] in "\r\n":
                    end += 1
                return start, end
    raise RuntimeError(f"method closing brace missing: {signature}")


def replace_method(signature: str, replacement: str) -> None:
    global text
    start, end = method_bounds(signature)
    text = text[:start] + replacement.rstrip() + "\n\n" + text[end:]


def insert_method_guard_if_present(signature: str, guard: str) -> bool:
    global text
    start = text.find(signature)
    if start < 0:
        return False
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"guard opening brace missing: {signature}")
    insertion = brace + 1
    nearby = text[insertion:insertion + 320]
    if guard.strip() not in nearby:
        text = text[:insertion] + "\n" + guard + text[insertion:]
    return True


# Keep Gemini integration/configuration reversible, but make its runtime lock explicit.
# This finalizer runs after the whole legacy patch stack, so do not depend on optional
# Gemini image constants that snapshot-disable patches are allowed to remove.
lock_field = "  private static final boolean GEMINI_RUNTIME_ENABLED = false;\n"
if lock_field not in text:
    class_anchor = "public class MainActivity extends Activity {\n"
    if class_anchor not in text:
        raise RuntimeError("MainActivity class anchor missing")
    text = text.replace(class_anchor, class_anchor + lock_field, 1)

# Defense in depth: any dormant Gemini network helper that survives the final patch
# stack must fail before opening a connection. Removed helpers need no guard.
lock_guard = '    if (!GEMINI_RUNTIME_ENABLED) throw new HttpError(503, "Gemini runtime intentionally locked.");\n'
guarded = 0
for signature in [
    "  private String geminiKeyFallbackText(String prompt, int excludedIndex, int maxOutputTokens, boolean rememberWorker) throws Exception ",
    "  private String postJsonGeminiLane(String endpoint, String key, JSONObject payload) throws Exception ",
    "  private SnapshotImage geminiImage(String prompt) throws Exception ",
]:
    if insert_method_guard_if_present(signature, lock_guard):
        guarded += 1
if guarded == 0:
    raise RuntimeError("No dormant Gemini helper remained to guard; inspect provider patch stack")

provider_router = r'''  private boolean providerFallbackEligible(Exception error) {
    if (error instanceof HttpError) {
      int status = ((HttpError) error).status;
      // 400/422 represent request-shape/validation failures. Switching providers
      // would repeat a deterministic bad request, so fail without falling through.
      if (status == 400 || status == 422) return false;
      return status == 401 || status == 403 || status == 404 || retryable(status);
    }
    // Transport failures, malformed provider responses and empty responses are
    // provider/runtime failures. Haku gets one attempt, then Luna gets one attempt.
    return true;
  }

  private String generateText(String prompt) throws Exception {
    return AiProviderRouter.route(
      prompt,
      this::hakuFallbackText,
      this::lunaText,
      this::providerFallbackEligible,
      new AiProviderRouter.Observer() {
        @Override public void onSelected(String provider) {
          emit("backroomProvider", "AI provider selected: " + provider);
        }

        @Override public void onFallback(String fromProvider, String toProvider, Exception error) {
          emit("backroomProvider", fromProvider + " failed; fallback to " + toProvider);
        }
      }
    );
  }'''
replace_method("  private String generateText(String prompt) throws Exception ", provider_router)

# Conditional audit keeps its compatibility signature, but all audit requests use
# the same active Haku -> Luna chain. excludedIndex is retained for patch compatibility.
audit_router = r'''  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return generateText(prompt);
  }'''
replace_method("  private String geminiAuditText(String prompt, int excludedIndex) throws Exception ", audit_router)

# Procedural generation is currently Core-disabled, but keep the helper safe if it
# is re-enabled later: it must obey the same provider policy and never wake Gemini.
if "  private String geminiLevelGenerationText(String prompt) throws Exception " in text:
    level_router = r'''  private String geminiLevelGenerationText(String prompt) throws Exception {
    return generateText(prompt);
  }'''
    replace_method("  private String geminiLevelGenerationText(String prompt) throws Exception ", level_router)

text = text.replace(
    'String generatorVersion = "gemini-procedural-v1:" + geminiModelLabel(lastGeminiModel);',
    'String generatorVersion = "active-provider-procedural-v1";',
)

# Provider status should reflect the real primary and must not claim Gemini is active.
text = text.replace("window.__backroomProvider='Gemini'", "window.__backroomProvider='HAKU'")
text = text.replace("Gemini đang xử lý lượt…", "HAKU đang xử lý lượt…")
text = text.replace("Đang tạo snapshot bằng Gemini…", "Snapshot chưa được cấu hình.")

# Final contract checks are intentionally strict because this patch runs last.
generate_start, generate_end = method_bounds("  private String generateText(String prompt) throws Exception ")
generate_block = text[generate_start:generate_end]
for marker in [
    "AiProviderRouter.route(",
    "this::hakuFallbackText",
    "this::lunaText",
    "this::providerFallbackEligible",
    '"AI provider selected: " + provider',
    'fromProvider + " failed; fallback to " + toProvider',
]:
    if marker not in generate_block:
        raise RuntimeError("active provider router marker missing: " + marker)
if "geminiText(" in generate_block or "geminiKeyFallbackText(" in generate_block:
    raise RuntimeError("Gemini returned to active generateText routing")
if generate_block.find("this::hakuFallbackText") > generate_block.find("this::lunaText"):
    raise RuntimeError("provider order must be HAKU -> LUNA")

for signature in [
    "  private String geminiAuditText(String prompt, int excludedIndex) throws Exception ",
    "  private String geminiLevelGenerationText(String prompt) throws Exception ",
]:
    if signature in text:
        start, end = method_bounds(signature)
        block = text[start:end]
        if "return generateText(prompt);" not in block:
            raise RuntimeError("compatibility provider helper bypasses active router: " + signature.strip())

snapshot_start, snapshot_end = method_bounds("  private void requestSnapshotInternal(String stateJson) ")
snapshot_block = text[snapshot_start:snapshot_end]
if "geminiImage(" in snapshot_block or "generativelanguage.googleapis.com" in snapshot_block:
    raise RuntimeError("Snapshot runtime still calls Gemini")

for forbidden_call in ["geminiText(prompt)", "geminiModelMatrixPolicy(prompt"]:
    if forbidden_call in text:
        raise RuntimeError("active Gemini call site remains: " + forbidden_call)

for required in [
    "private static final boolean GEMINI_RUNTIME_ENABLED = false;",
    "Gemini runtime intentionally locked.",
    "BuildConfig.GEMINI_API_KEY_1",
    "BuildConfig.HAKU_API_KEY",
    "BuildConfig.LUNA_API_KEY",
    '"claude-haiku-4-5-20251001"',
    'baseUrl + "/chat/completions"',
]:
    if required not in text:
        raise RuntimeError("provider lock/reversibility marker missing: " + required)

MAIN.write_text(text, encoding="utf-8")
print("Final AI routing: HAKU primary -> LUNA fallback -> controlled failure; Gemini runtime locked and snapshot network-free.")
