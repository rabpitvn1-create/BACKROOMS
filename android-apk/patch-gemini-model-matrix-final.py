from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


# Keep the existing writer-worker marker because the conditional audit deliberately
# avoids the key that produced the writer response. The runtime uses one Gemini
# text model and deterministic credential fallback K1 -> K5.
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

new_leaf_methods = r'''  private boolean socketTimeoutFailure(Exception error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.net.SocketTimeoutException) return true;
      cause = cause.getCause();
    }
    return false;
  }

  private String postJsonGeminiLane(String endpoint, String key, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(4000);
    connection.setReadTimeout(8000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("x-goog-api-key", key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Gemini HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String geminiKeyFallbackText(String prompt, int excludedIndex, int maxOutputTokens, boolean rememberWorker) throws Exception {
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

        emit("backroomProvider", "Gemini K" + (keyIndex + 1));
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

          JSONObject result = new JSONObject(postJsonGeminiLane(
            "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
            key,
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

          // A read timeout is a lane failure: immediately try the next credential.
          if (socketTimeoutFailure(error)) continue;

          // DNS/connect/socket failures and model-level 400/404 are host/model
          // failures, so stop burning Gemini credentials and move to Luna/Haku.
          if (networkFailure(error) || code == 400 || code == 404) throw error;

          // Auth/quota/408/5xx/empty-response failures are lane failures.
          // Never retry the same key; continue directly to the next key.
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key khả dụng trong APK.");
  }

  // Compatibility shim for the procedural-Level patch. modelOrder and budgets are
  // intentionally ignored: the runtime has one Gemini text model and key fallback.
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

  private String postJsonHakuFallback(JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL("https://api.vilao.ai/v1/chat/completions").openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(22000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }

    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder response = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = response.length() > 220 ? response.substring(0, 220) : response.toString();
      throw new HttpError(status, "Haku HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return response.toString();
  }

  private String hakuFallbackText(String prompt) throws Exception {
    if (BuildConfig.HAKU_API_KEY == null || BuildConfig.HAKU_API_KEY.trim().isEmpty()) {
      throw new HttpError(401, "Haku API key chưa được cấu hình.");
    }

    JSONArray messages = new JSONArray()
      .put(new JSONObject().put("role", "user").put("content", prompt));
    JSONObject body = new JSONObject()
      .put("model", "claude-haiku-4-5-20251001")
      .put("messages", messages)
      .put("temperature", 0.75)
      .put("max_tokens", 1800)
      .put("stream", false);

    JSONObject result = new JSONObject(postJsonHakuFallback(body));
    JSONArray choices = result.optJSONArray("choices");
    JSONObject first = choices != null ? choices.optJSONObject(0) : null;
    JSONObject message = first != null ? first.optJSONObject("message") : null;
    String responseText = message != null ? message.optString("content", "").trim() : "";
    if (responseText.isEmpty()) throw new Exception("Haku không trả nội dung.");
    return responseText;
  }
'''
replace_once(old_leaf_methods, new_leaf_methods, "provider key fallback methods")

# patch-provider-deadline-final ran immediately before this finalizer. Replace the
# complete dispatch boundary structurally so text routing is:
# Gemini K1 -> K2 -> K3 -> K4 -> K5 -> Luna -> Haku.
new_generate = r'''  private String generateText(String prompt) throws Exception {
    Exception geminiFailure = null;
    Exception lunaFailure = null;
    Exception hakuFailure = null;

    emit("backroomProvider", "Gemini K1");
    try {
      String result = geminiText(prompt);
      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));
      return result;
    } catch (Exception error) {
      geminiFailure = error;
    }

    emit("backroomProvider", "Luna fallback");
    try {
      return lunaText(prompt);
    } catch (Exception error) {
      lunaFailure = error;
    }

    emit("backroomProvider", "Haku fallback");
    try {
      return hakuFallbackText(prompt);
    } catch (Exception error) {
      hakuFailure = error;
    }

    if (networkFailure(geminiFailure) && networkFailure(lunaFailure) && networkFailure(hakuFailure)) {
      throw new Exception(networkFailureMessage());
    }
    String geminiMessage = geminiFailure != null && geminiFailure.getMessage() != null ? geminiFailure.getMessage() : "Gemini không khả dụng";
    String lunaMessage = lunaFailure != null && lunaFailure.getMessage() != null ? lunaFailure.getMessage() : "Luna không khả dụng";
    String hakuMessage = hakuFailure != null && hakuFailure.getMessage() != null ? hakuFailure.getMessage() : "Haku không khả dụng";
    throw new Exception("Gemini: " + geminiMessage + "; Luna: " + lunaMessage + "; Haku: " + hakuMessage);
  }
'''
generate_start = text.find("  private String generateText(String prompt) throws Exception {")
if generate_start < 0:
    raise RuntimeError("provider generateText method not found")
brace = text.find("{", generate_start)
if brace < 0:
    raise RuntimeError("provider generateText opening brace not found")
depth = 0
generate_end = -1
for index in range(brace, len(text)):
    char = text[index]
    if char == "{":
        depth += 1
    elif char == "}":
        depth -= 1
        if depth == 0:
            generate_end = index + 1
            break
if generate_end < 0:
    raise RuntimeError("provider generateText closing brace not found")
while generate_end < len(text) and text[generate_end] in "\r\n":
    generate_end += 1
text = text[:generate_start] + new_generate + text[generate_end:]

generate_start = text.index("  private String generateText(String prompt) throws Exception {")
generate_end = generate_start + len(new_generate)
generate_block = text[generate_start:generate_end]
ordered_markers = [
    "geminiText(prompt)",
    "lunaText(prompt)",
    "hakuFallbackText(prompt)",
]
positions = [generate_block.find(marker) for marker in ordered_markers]
if any(pos < 0 for pos in positions) or positions != sorted(positions):
    raise RuntimeError("provider fallback order must be Gemini -> Luna -> Haku")

for required in [
    "private String postJsonGeminiLane(",
    "connection.setReadTimeout(8000);",
    "private boolean socketTimeoutFailure(",
    'emit("backroomProvider", "Gemini K" + (keyIndex + 1))',
    "private String hakuFallbackText(",
    "BuildConfig.HAKU_API_KEY",
    '"claude-haiku-4-5-20251001"',
    'emit("backroomProvider", "Luna fallback")',
    'emit("backroomProvider", "Haku fallback")',
    'return "Gemini 3.6 Flash";',
]:
    if required not in text:
        raise RuntimeError("provider fallback marker missing: " + required)

if "hakuPolishReply(" in text:
    raise RuntimeError("retired Haku prose-editor call returned")

MAIN.write_text(text, encoding="utf-8")
print("Android text routing: Gemini K1->K5 with timeout lane fallback, then Luna, then Haku provider fallback; no prose-editor pass.")
