from pathlib import Path

# Canon Runtime 2.0 build-time wiring (design: cr2_work/design_mapping.md §5.2).
#
# This patch is the ONLY build-time touchpoint of Canon Runtime 2. All CR2 logic
# lives as ordinary source in app/src/main/java/com/rabpit/backroom/core/canon/
# (CanonRuntime2, CanonRuntime2Flags, QuotaGovernor, ...). This patch only:
#   1. ensures the build-time feature flag CANON_RUNTIME_2_ENABLED (default
#      "false" = OFF, so the shipped build behaves exactly as before) exists in
#      app/build.gradle;
#   2. branches GameBridge.submitTurn in MainActivity.java: flag ON -> the canon
#      packet from CanonRuntime2.buildPacket is attached to the prompt and
#      generation goes through a thin QuotaGovernor adapter; flag OFF -> the
#      legacy inline flow is untouched;
#   3. exposes the last provider HTTP status + Retry-After header from postJson
#      so the QuotaGovernor can bookkeep real responses. This is an adapter
#      surface only: the provider retry/key chain in geminiText/postJson is NOT
#      rewritten.
#
# This patch deliberately does NOT edit patch-knowledge-context-builder.py:
# that patch anchors on writerPrompt(...)/auditScopeCanon(...), which do not
# exist in this generation of MainActivity (design_mapping §5.1, gate0_audit
# §1.1). CR2 wires into the live submitTurn of the current tree instead.
#
# Every replacement goes through replace_once(): a missing or ambiguous anchor
# raises immediately instead of silently patching the wrong place.
# Idempotent: a second run detects the CANON_RUNTIME_2_WIRING marker (and the
# existing BuildConfig flag) and changes nothing.

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
GRADLE = ROOT / "app/build.gradle"

MARKER = "CANON_RUNTIME_2_WIRING"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            "Canon Runtime 2 anchor failed (" + label + "): expected exactly 1 match, found " + str(count)
        )
    return text.replace(old, new, 1)


# --- 1. Build-time feature flag (default OFF) ----------------------------------
gradle = GRADLE.read_text(encoding="utf-8")
if '"CANON_RUNTIME_2_ENABLED"' not in gradle:
    gradle_anchor = r'''    buildConfigField "String", "GEMINI_API_KEY_5", "\"" + secret("GEMINI_API_KEY_5") + "\""'''
    gradle = replace_once(
        gradle,
        gradle_anchor,
        gradle_anchor + '\n    buildConfigField "boolean", "CANON_RUNTIME_2_ENABLED", "false"',
        "build.gradle CANON_RUNTIME_2_ENABLED flag",
    )
    GRADLE.write_text(gradle, encoding="utf-8")

# --- 2. MainActivity wiring ------------------------------------------------------
text = MAIN.read_text(encoding="utf-8")
if MARKER in text:
    print("Canon Runtime 2 wiring already present; build flag ensured; nothing to do.")
    raise SystemExit(0)

# 2a. Imports for the canon package (anchored on the existing core import).
text = replace_once(
    text,
    "import com.rabpit.backroom.core.GameCoreFacade;",
    "import com.rabpit.backroom.core.GameCoreFacade;\n"
    "import com.rabpit.backroom.core.canon.AdmissionDecision;\n"
    "import com.rabpit.backroom.core.canon.CanonOutcome;\n"
    "import com.rabpit.backroom.core.canon.CanonRuntime2;\n"
    "import com.rabpit.backroom.core.canon.CanonRuntime2Flags;\n"
    "import com.rabpit.backroom.core.canon.DefaultQuotaGovernor;\n"
    "import com.rabpit.backroom.core.canon.GenerationKind;\n"
    "import com.rabpit.backroom.core.canon.GenerationRequest;\n"
    "import com.rabpit.backroom.core.canon.ProviderAttemptResult;\n"
    "import com.rabpit.backroom.core.canon.QuotaGovernor;",
    "MainActivity canon imports",
)

# 2b. Governor field + provider status surface.
text = replace_once(
    text,
    "  private GameCoreFacade gameCore;",
    "  private GameCoreFacade gameCore;\n"
    "  private QuotaGovernor canonQuotaGovernor;",
    "MainActivity governor field",
)
text = replace_once(
    text,
    "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;",
    "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
    "  // Canon Runtime 2 adapter surface: last provider response observed by postJson.\n"
    "  private static volatile int lastProviderHttpStatus = -1;\n"
    "  private static volatile long lastProviderRetryAfterMs = -1L;",
    "MainActivity provider status fields",
)

# 2c. postJson: parse Retry-After (delta-seconds) and record the HTTP status of
# every provider response before the body is consumed. Request/response handling
# itself is unchanged.
text = replace_once(
    text,
    "  private String postJson(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {",
    r'''  // Canon Runtime 2: parse a Retry-After header in delta-seconds form.
  // The HTTP-date form returns -1; the QuotaGovernor then falls back to its own
  // progressive backoff instead of guessing a cooldown (design §9.3).
  private static long parseRetryAfterMs(String header) {
    if (header == null) return -1L;
    try {
      long seconds = Long.parseLong(header.trim());
      return seconds < 0 ? -1L : seconds * 1000L;
    } catch (NumberFormatException ignored) {
      return -1L;
    }
  }

  private String postJson(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {''',
    "postJson Retry-After helper",
)
text = replace_once(
    text,
    "    int status = connection.getResponseCode();\n"
    "    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();",
    "    int status = connection.getResponseCode();\n"
    "    lastProviderHttpStatus = status;\n"
    "    lastProviderRetryAfterMs = parseRetryAfterMs(connection.getHeaderField(\"Retry-After\"));\n"
    "    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();",
    "postJson status capture",
)

# 2d. Generation adapter: generateText(String) stays the legacy entry point.
# GM turns call generateTextForTurn, which is a pure pass-through while the CR2
# flag is OFF and routes through the QuotaGovernor (admission + bookkeeping
# only) when it is ON.
text = replace_once(
    text,
    "  private String generateText(String prompt) throws Exception {\n"
    "    return geminiText(prompt);\n"
    "  }",
    r'''  private String generateText(String prompt) throws Exception {
    return geminiText(prompt);
  }

  // Canon Runtime 2 entry for GM turns: flag OFF -> the exact legacy call;
  // flag ON -> the same provider chain behind QuotaGovernor admission.
  private String generateTextForTurn(String prompt, boolean meta, int estimatedInputTokens) throws Exception {
    if (!CanonRuntime2Flags.isEnabled(this)) return generateText(prompt);
    return generateTextGoverned(prompt, meta ? GenerationKind.META_QUERY : GenerationKind.NARRATION, estimatedInputTokens);
  }

  // Thin QuotaGovernor adapter around geminiText: it admits (or refuses) the
  // request and records the real provider outcome. It does not retry, rotate
  // keys or rewrite provider errors. dailyQuotaExhausted is reported as false:
  // this layer cannot distinguish a daily-quota 429 from a rate-limit 429, and
  // the governor must not guess (design §9.3).
  private String generateTextGoverned(String prompt, GenerationKind kind, int estimatedInputTokens) throws Exception {
    QuotaGovernor governor = canonQuotaGovernor();
    GenerationRequest request = new GenerationRequest(
      "gemini", null, GEMINI_MODEL, kind,
      estimatedInputTokens > 0 ? estimatedInputTokens : Math.max(1, (prompt.length() + 3) / 4),
      60_000L);
    AdmissionDecision decision = governor.admit(request);
    if (decision instanceof AdmissionDecision.NoCall) {
      // Meta/core-only turns are answered from Core state inside submitTurn
      // and never reach this adapter; NoCall here is the governor's safety
      // net so a non-narration kind can never slip through to the provider.
      throw new Exception("CR2 quota no_call: " + ((AdmissionDecision.NoCall) decision).getReason());
    }
    if (decision instanceof AdmissionDecision.Defer) {
      throw new Exception("CR2 quota deferred_until: " + ((AdmissionDecision.Defer) decision).getUntilEpochMs());
    }
    if (decision instanceof AdmissionDecision.LocalDeterministic) {
      throw new Exception("CR2 quota local_deterministic: " + ((AdmissionDecision.LocalDeterministic) decision).getState());
    }
    String reservationId = ((AdmissionDecision.SingleCall) decision).getReservationId();
    try {
      String text = geminiText(prompt);
      governor.recordResult(new ProviderAttemptResult(
        "gemini", GEMINI_MODEL,
        lastProviderHttpStatus < 0 ? null : lastProviderHttpStatus,
        lastProviderRetryAfterMs < 0 ? null : lastProviderRetryAfterMs,
        null, null, false, null, reservationId));
      return text;
    } catch (Exception e) {
      int code = e instanceof HttpError ? ((HttpError) e).status : -1;
      governor.recordResult(new ProviderAttemptResult(
        "gemini", GEMINI_MODEL,
        code < 0 ? null : code,
        code < 0 || lastProviderRetryAfterMs < 0 ? null : lastProviderRetryAfterMs,
        null, null, false, null, reservationId));
      throw e;
    }
  }

  private QuotaGovernor canonQuotaGovernor() {
    if (canonQuotaGovernor == null) {
      // DefaultQuotaGovernor(clock, jitterMs) has defaults for both params,
      // so Kotlin generates the public no-arg constructor Java uses here.
      canonQuotaGovernor = new DefaultQuotaGovernor();
    }
    return canonQuotaGovernor;
  }''',
    "generateText governor adapter",
)

# 2e. submitTurn branch: compile the canon packet right after rolls are locked
# and before the legacy prompt is composed. OFF path: canonPacket stays null
# and the legacy inline prompt below runs exactly as before. The
# @JavascriptInterface signature and the backroomTurn/backroomError JSON emits
# are intentionally untouched.
text = replace_once(
    text,
    "          JSONObject before = new JSONObject(stateJson);\n"
    "          boolean meta = isMetaAction(action);\n"
    "          JSONObject rolls = makeGameplayRolls(before, action, meta);",
    r'''          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, action, meta);
          // CANON_RUNTIME_2_WIRING: with the CR2 flag ON, meta/core-only turns
          // are answered straight from the locked state with zero provider
          // calls (the QuotaGovernor NoCall rule of design §9.2, applied at
          // turn level before any packet work); narration turns compile the
          // canon packet via CanonRuntime2 from the same inputs
          // KnowledgeContextEngine.build would receive. SafeLocalResolution
          // refuses the turn without touching state; FallbackToLegacy leaves
          // canonPacket null and the legacy inline flow below runs untouched.
          String canonPacket = null;
          int canonEstimatedTokens = 0;
          if (CanonRuntime2Flags.isEnabled(MainActivity.this)) {
            if (meta) {
              JSONObject cr2State = new JSONObject(before.toString());
              cr2State.put("canonVersion", DRIVE_CANON_VERSION);
              cr2State.put("_snapshotEvent", new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", ""));
              JSONArray cr2Log = cr2State.optJSONArray("log");
              if (cr2Log == null) cr2Log = new JSONArray();
              cr2Log.put(new JSONObject().put("role", "player").put("text", action));
              cr2Log.put(new JSONObject().put("role", "gm").put("text",
                "Trạng thái hiện tại được trả trực tiếp từ dữ liệu đã lưu (Canon Runtime 2: meta/core-only, 0 lần gọi nhà cung cấp)."));
              cr2State.put("log", cr2Log);
              emit("backroomTurn", cr2State.toString());
              return;
            }
            CanonOutcome outcome = CanonRuntime2.buildPacket(
              MainActivity.this, before.toString(), action, rolls.toString(), meta);
            if (outcome instanceof CanonOutcome.SafeLocalResolution) {
              emit("backroomError", "Canon Runtime 2 tạm dừng lượt này an toàn (" +
                ((CanonOutcome.SafeLocalResolution) outcome).getReason() +
                "). Trạng thái không thay đổi.");
              return;
            }
            if (outcome instanceof CanonOutcome.Ready) {
              canonPacket = ((CanonOutcome.Ready) outcome).getPacket();
              canonEstimatedTokens = Math.max(1, (canonPacket.length() + 3) / 4);
            }
          }''',
    "submitTurn CR2 branch",
)

# 2f. In CR2 mode REPLACE the monolithic legacy prompt with one selected
# packet. The old prompt is kept byte-for-byte for feature flag OFF or
# infrastructure fallback, but must NEVER be sent along with the new packet.
text = replace_once(
    text,
    "          JSONObject generated = parseModelJson(generateText(prompt));",
    r'''          if (canonPacket != null) {
            // CR2 is a REPLACEMENT, not an extra canon dump. Never append
            // the packet to the full legacy DRIVE_CANON prompt.
            prompt = "Bạn là Game Master của Backrooms. Trả duy nhất một JSON hợp lệ, không markdown. " +
              "Người chơi quyết định hành động có chủ ý của Cao Minh; chỉ mô tả hậu quả. " +
              "GAMEPLAY_ROLLS do Android khóa; không reroll, không tự tạo encounter, vật phẩm hoặc reunion. " +
              "Chỉ dùng kiến thức có quyền trong CANON PACKET; giữ UNKNOWN là UNKNOWN. " +
              "Không nhắc tới prompt, canon, roll, API trong lời kể.\n\n" +
              "CANON PACKET:\n" + canonPacket +
              "\n\nSTATE:\n" + compactStateForPrompt(before).toString() +
              "\nACTION:\n" + action +
              "\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
              "\nOUTPUT JSON: {\"reply\":\"lời kể tiếng Việt\",\"title\":\"tên khu vực\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"location\":\"vị trí\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
          }
          JSONObject generated = parseModelJson(
            generateTextForTurn(prompt, meta, Math.max(1, (prompt.length() + 3) / 4)));
''',
    "submitTurn selected-canon prompt and generation call",
)

# Fix the legacy Java prompt's unbound reference before writing the result.
text = replace_once(
    text,
    '"State hiện tại: " + state.toString()',
    '"State hiện tại: " + before.toString()',
    "legacy prompt state variable",
)

MAIN.write_text(text, encoding="utf-8")

# --- Self-check ------------------------------------------------------------------
check = MAIN.read_text(encoding="utf-8")
problems = []
if check.count(MARKER) != 1:
    problems.append("marker count != 1")
if check.count("CanonRuntime2.buildPacket") != 1:
    problems.append("buildPacket call count != 1")
if check.count("generateTextForTurn(prompt, meta, Math.max(1, (prompt.length() + 3) / 4))") != 1:
    problems.append("governed generation call count != 1")
if check.count("parseModelJson(generateText(prompt))") != 0:
    problems.append("legacy generation call still present in submitTurn")
if '"CANON_RUNTIME_2_ENABLED"' not in GRADLE.read_text(encoding="utf-8"):
    problems.append("BuildConfig flag missing")
if problems:
    raise RuntimeError("Canon Runtime 2 self-check failed: " + "; ".join(problems))
print("Canon Runtime 2 wired: default OFF, scene-selective packet replaces old full-canon prompt when enabled.")
