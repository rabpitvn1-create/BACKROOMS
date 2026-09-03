from pathlib import Path
import re

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


# Keep the existing writer-worker marker because the conditional audit deliberately
# avoids the key that produced the writer response. There is no model matrix now:
# the only text model is the fixed GEMINI_MODEL and credentials are tried K1 -> K5.
field_anchor = "  private volatile int lastGeminiWorker = -1;\n"
if field_anchor not in text:
    raise RuntimeError("Gemini writer worker field missing")
if "private volatile int lastGeminiModel" not in text:
    text = text.replace(
        field_anchor,
        field_anchor + "  private volatile int lastGeminiModel = 0;\n",
        1,
    )

old_leaf_methods = r'''  private String geminiText(String prompt) throws Exception {
    return geminiTextPolicy(prompt, -1, 0.8, 1800, true);
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiTextPolicy(prompt, excludedIndex, 0.1, 650, false);
  }
'''

new_leaf_methods = r'''  private String geminiKeyFallbackText(String prompt, int excludedIndex, int maxOutputTokens, boolean rememberWorker) throws Exception {
    String[] keys = geminiKeys();
    Exception last = null;
    if (rememberWorker) lastGeminiWorker = -1;
    lastGeminiModel = 0;
    int keyCount = Math.min(5, keys.length);

    // Normal writer calls are strictly K1 -> K5. Audit calls first skip the key
    // that wrote the response, then use it last only if every other key failed.
    int phases = excludedIndex >= 0 ? 2 : 1;
    for (int phase = 0; phase < phases; phase++) {
      for (int keyIndex = 0; keyIndex < keyCount; keyIndex++) {
        if (excludedIndex >= 0) {
          if (phase == 0 && keyIndex == excludedIndex) continue;
          if (phase == 1 && keyIndex != excludedIndex) continue;
        }
        String key = keys[keyIndex];
        if (key == null || key.trim().isEmpty()) continue;

        try {
          JSONObject part = new JSONObject().put("text", prompt);
          JSONObject contents = new JSONObject()
            .put("role", "user")
            .put("parts", new JSONArray().put(part));
          JSONObject config = new JSONObject()
            .put("responseMimeType", "application/json")
            .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
          if (maxOutputTokens > 0) config.put("maxOutputTokens", maxOutputTokens);
          JSONObject body = new JSONObject()
            .put("contents", new JSONArray().put(contents))
            .put("generationConfig", config);

          JSONObject result = new JSONObject(postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
            key,
            "x-goog-api-key",
            body
          ));
          JSONArray candidates = result.optJSONArray("candidates");
          StringBuilder responseText = new StringBuilder();
          if (candidates != null) {
            for (int c = 0; c < candidates.length(); c++) {
              JSONObject candidate = candidates.optJSONObject(c);
              JSONObject providerContent = candidate != null ? candidate.optJSONObject("content") : null;
              JSONArray parts = providerContent != null ? providerContent.optJSONArray("parts") : null;
              if (parts == null) continue;
              for (int p = 0; p < parts.length(); p++) {
                JSONObject responsePart = parts.optJSONObject(p);
                String piece = responsePart != null ? responsePart.optString("text", "").trim() : "";
                if (!piece.isEmpty()) {
                  if (responseText.length() > 0) responseText.append('\n');
                  responseText.append(piece);
                }
              }
            }
          }
          if (responseText.length() == 0) throw new Exception("Gemini không trả nội dung.");
          if (rememberWorker) lastGeminiWorker = keyIndex;
          return responseText.toString();
        } catch (Exception error) {
          last = error;
          int code = error instanceof HttpError ? ((HttpError)error).status : 0;
          // DNS/socket failures and model-level 400/404 failures are not key-specific.
          // Do not waste all five credentials on the same host/model outage.
          if (networkFailure(error) || code == 400 || code == 404) throw error;
          // Auth/quota/transient failures are credential-lane failures: move once
          // to the next key. No retry on the same key.
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key khả dụng trong APK.");
  }

  // Compatibility shim for the procedural-Level patch. modelOrder and budgets are
  // intentionally ignored: the runtime has one text model and only key fallback.
  private String geminiModelMatrixPolicy(String prompt, int[] modelOrder, int excludedKeyIndex, int maxOutputTokens, boolean rememberWorker, long totalBudgetMs) throws Exception {
    return geminiKeyFallbackText(prompt, excludedKeyIndex, maxOutputTokens, rememberWorker);
  }

  private String geminiModelLabel(int modelIndex) {
    return "Gemini 3.6 Flash";
  }

  private String geminiText(String prompt) throws Exception {
    return geminiKeyFallbackText(prompt, -1, 1800, true);
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiKeyFallbackText(prompt, excludedIndex, 650, false);
  }
'''
replace_once(old_leaf_methods, new_leaf_methods, "single-model Gemini key fallback")

# patch-provider-deadline-final ran immediately before this finalizer and still
# contains the historical Gemini -> Luna provider switch. Replace that final
# dispatch boundary so the completed runtime can only call Gemini key fallback.
new_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Gemini");
    try {
      String result = geminiText(prompt);
      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));
      return result;
    } catch (Exception error) {
      if (networkFailure(error)) throw new Exception(networkFailureMessage());
      throw error;
    }
  }
'''
pattern = r'  private String generateText\(String prompt\) throws Exception \{.*?\n  \}\n(?=\n  private JSONObject parseModelJson)'
text, count = re.subn(pattern, lambda _: new_generate.rstrip("\n"), text, count=1, flags=re.S)
if count != 1:
    raise RuntimeError(f"Gemini-only generateText boundary: expected 1 method, found {count}")

# Final runtime assertions. Historical Luna helpers may still exist until the next
# provider defragmentation batch, but no player-facing text route may call them.
generate_start = text.index("  private String generateText(String prompt) throws Exception {")
generate_end = text.index("\n  private JSONObject parseModelJson", generate_start)
generate_block = text[generate_start:generate_end]
for forbidden in ["lunaText(", "Luna fallback", "geminiModelChain()", "gemini-3.5-flash"]:
    if forbidden in generate_block:
        raise RuntimeError("Gemini-only dispatch still contains: " + forbidden)

for required in [
    "private String geminiKeyFallbackText(",
    "for (int keyIndex = 0; keyIndex < keyCount; keyIndex++)",
    '"https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent"',
    'emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1))',
    'return "Gemini 3.6 Flash";',
]:
    if required not in text:
        raise RuntimeError("Gemini key fallback marker missing: " + required)

MAIN.write_text(text, encoding="utf-8")
print("Android text routing simplified: Gemini 3.6 Flash only, deterministic K1 -> K5 fallback, no Luna dispatch.")
