from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
MARKER = "GEMINI_FOUNDATION_AUTHORITY_R02"


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

# Retire the temporary Gemini lock after every compatibility patch has run.
main = replace_once(
    main,
    "  private static final boolean GEMINI_RUNTIME_ENABLED = false;\n",
    "  private static final boolean GEMINI_RUNTIME_ENABLED = true;\n",
    "Gemini runtime enable flag",
)
lock_guard = '    if (!GEMINI_RUNTIME_ENABLED) throw new HttpError(503, "Gemini runtime intentionally locked.");\n'
if lock_guard not in main:
    raise RuntimeError("Gemini lock guard missing before authority finalization")
main = main.replace(lock_guard, "")

# Canonical six-key pool follows the retired Hua-s-Family project naming.
gemini_keys = r'''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5,
      BuildConfig.GEMINI_API_KEY_6
    };
  }'''
main = replace_method(main, "  private String[] geminiKeys() ", gemini_keys)

key_start, key_end = method_bounds(
    main,
    "  private String geminiKeyFallbackText(String prompt, int excludedIndex, int maxOutputTokens, boolean rememberWorker) throws Exception ",
)
key_method = main[key_start:key_end]
key_method = replace_once(
    key_method,
    "int keyCount = Math.min(5, keys.length);",
    "int keyCount = Math.min(6, keys.length);",
    "six-key Gemini pool",
)
main = main[:key_start] + key_method + main[key_end:]

# Gemini must share the same monotonic turn budget as Haku and Luna.
gemini_post_start, gemini_post_end = method_bounds(
    main,
    "  private String postJsonGeminiLane(String endpoint, String key, JSONObject payload) throws Exception ",
)
gemini_post = main[gemini_post_start:gemini_post_end]
gemini_post = replace_once(
    gemini_post,
    "connection.setConnectTimeout(4000);",
    "connection.setConnectTimeout(providerTimeout(4000, 250));",
    "Gemini connect timeout budget",
)
gemini_post = replace_once(
    gemini_post,
    "connection.setReadTimeout(8000);",
    "connection.setReadTimeout(providerTimeout(8000, 500));",
    "Gemini read timeout budget",
)
main = main[:gemini_post_start] + gemini_post + main[gemini_post_end:]

provider_authority = r'''  /* GEMINI_FOUNDATION_AUTHORITY_R02 */
  private boolean providerFallbackEligible(Exception error) {
    TurnBudget budget = activeTurnBudget.get();
    // Provider-specific HTTP/schema differences are allowed to fall through. The
    // only hard stop is an exhausted shared turn deadline.
    return budget == null || budget.remainingMs() > 0L;
  }

  private String providerErrorSummary(String provider, Exception error) {
    String category = "runtime";
    int status = 0;
    if (error instanceof HttpError) {
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
    return provider + " category=" + category + (status > 0 ? " status=" + status : "") + " type=" + type;
  }

  private AiProviderRouter.Observer providerObserver() {
    return new AiProviderRouter.Observer() {
      @Override public void onSelected(String provider) {
        emit("backroomProvider", "AI provider selected: " + provider);
      }

      @Override public void onFallback(String fromProvider, String toProvider, Exception error) {
        emit("backroomProvider", fromProvider + " failed; fallback to " + toProvider);
      }

      @Override public void onFailure(String provider, Exception error) {
        emit("backroomProviderError", providerErrorSummary(provider, error));
      }
    };
  }

  private String geminiFoundationText(String prompt) throws Exception {
    return geminiKeyFallbackText(prompt, -1, 3200, true);
  }'''
main = replace_method(
    main,
    "  private boolean providerFallbackEligible(Exception error) ",
    provider_authority,
)

generate = r'''  private String generateText(String prompt) throws Exception {
    return AiProviderRouter.route(
      prompt,
      this::geminiText,
      this::hakuFallbackText,
      this::lunaText,
      this::providerFallbackEligible,
      (provider, response) -> parseModelJson(response).toString(),
      providerObserver()
    );
  }'''
main = replace_method(main, "  private String generateText(String prompt) throws Exception ", generate)

structured = r'''  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception {
    budget.throwIfExpired();
    activeTurnBudget.set(budget);
    try {
      return AiProviderRouter.route(
        prompt,
        this::geminiFoundationText,
        this::hakuFallbackText,
        this::lunaText,
        this::providerFallbackEligible,
        (provider, response) -> AiResponseSchemas.validate(role, parseModelJson(response).toString()),
        providerObserver()
      );
    } finally {
      activeTurnBudget.remove();
    }
  }'''
main = replace_method(
    main,
    "  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception ",
    structured,
)

foundation_packet = r'''  private String foundationPacket(JSONObject before, String action, JSONObject rolls, String role, String turnId) throws Exception {
    String packet = com.rabpit.backroom.core.foundation.FoundationRuntime.buildSlice(
      MainActivity.this,
      turnId,
      requireGameCore().foundationStateProjection(),
      before.toString(),
      action,
      rolls.toString(),
      role);
    if (packet == null || packet.trim().isEmpty()) {
      emit("backroomFoundation", "legacy-fallback role=" + role);
      return com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
        MainActivity.this, before.toString(), action, rolls.toString());
    }
    emit("backroomFoundation", "active role=" + role + " slice=v1");
    return packet;
  }'''
main = replace_method(
    main,
    "  private String foundationPacket(JSONObject before, String action, JSONObject rolls, String role, String turnId) throws Exception ",
    foundation_packet,
)

# Make Foundation/provider diagnostics visible in the bounded, redacted TXT log.
# The canon-audit finalizer already extends the provider capture condition, so preserve
# that telemetry and add Foundation instead of assuming the pre-audit form still exists.
capture_with_audit = '    if ("backroomProvider".equals(function) || "backroomAudit".equals(function) || function.endsWith("Error")) {'
capture_without_audit = '    if ("backroomProvider".equals(function) || function.endsWith("Error")) {'
new_capture = '    if ("backroomProvider".equals(function) || "backroomAudit".equals(function) || "backroomFoundation".equals(function) || function.endsWith("Error")) {'
if new_capture not in main:
    if capture_with_audit in main:
        main = replace_once(main, capture_with_audit, new_capture, "Foundation debug event capture after audit")
    elif capture_without_audit in main:
        main = replace_once(main, capture_without_audit, new_capture, "Foundation debug event capture")
    else:
        raise RuntimeError("Foundation debug event capture anchor missing")

old_secrets = '''    String[] configuredSecrets = {
      BuildConfig.HAKU_API_KEY,
      BuildConfig.LUNA_API_KEY,
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };'''
new_secrets = '''    String[] configuredSecrets = {
      BuildConfig.HAKU_API_KEY,
      BuildConfig.LUNA_API_KEY,
      BuildConfig.GEMINI_API_KEY,
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5,
      BuildConfig.GEMINI_API_KEY_6
    };'''
main = replace_once(main, old_secrets, new_secrets, "six-key debug redaction")

main = main.replace("window.__backroomProvider='HAKU'", "window.__backroomProvider='Gemini'")
main = main.replace("HAKU đang xử lý lượt…", "Gemini đang xử lý lượt…")

# Final authority checks operate on the generated runtime, not intermediate patch state.
if "private static final boolean GEMINI_RUNTIME_ENABLED = false;" in main:
    raise RuntimeError("Gemini runtime lock survived final authority")
if "Gemini runtime intentionally locked." in main:
    raise RuntimeError("Gemini network guard survived final authority")
for required in [
    "private static final boolean GEMINI_RUNTIME_ENABLED = true;",
    "GEMINI_FOUNDATION_AUTHORITY_R02",
    "BuildConfig.GEMINI_API_KEY",
    "BuildConfig.GEMINI_API_KEY_6",
    "Math.min(6, keys.length)",
    "this::geminiFoundationText",
    'emit("backroomFoundation", "active role=" + role + " slice=v1")',
    'emit("backroomProviderError", providerErrorSummary(provider, error))',
]:
    if required not in main:
        raise RuntimeError("Gemini/Foundation authority marker missing: " + required)

normal_start, normal_end = method_bounds(main, "  private String generateText(String prompt) throws Exception ")
normal = main[normal_start:normal_end]
normal_order = [normal.find("this::geminiText"), normal.find("this::hakuFallbackText"), normal.find("this::lunaText")]
if any(value < 0 for value in normal_order) or normal_order != sorted(normal_order):
    raise RuntimeError("normal provider order must be GEMINI -> HAKU -> LUNA")

structured_start, structured_end = method_bounds(
    main,
    "  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception ",
)
structured_block = main[structured_start:structured_end]
structured_order = [
    structured_block.find("this::geminiFoundationText"),
    structured_block.find("this::hakuFallbackText"),
    structured_block.find("this::lunaText"),
]
if any(value < 0 for value in structured_order) or structured_order != sorted(structured_order):
    raise RuntimeError("Foundation provider order must be GEMINI -> HAKU -> LUNA")

MAIN.write_text(main, encoding="utf-8")
print("Final AI authority: Persistent Foundation -> Gemini K1-K6 high priority -> HAKU -> LUNA -> controlled failure; sanitized provider/Foundation diagnostics active.")
