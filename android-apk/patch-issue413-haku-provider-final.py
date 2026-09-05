from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"method signature missing: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"method opening brace missing: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return start, index + 1
    raise RuntimeError(f"method closing brace missing: {signature}")


def replace_method(source: str, signature: str, replacement: str) -> str:
    start, end = method_bounds(source, signature)
    return source[:start] + replacement.rstrip() + source[end:]


# Restore the previously established runtime policy: HAKU is the active writer, LUNA is
# the fallback, and the old Gemini machinery remains compiled only for compatibility.
lock_field = "  private static final boolean GEMINI_RUNTIME_ENABLED = false;\n"
if lock_field not in text:
    anchor = "public class MainActivity extends Activity {\n"
    if anchor not in text:
        raise RuntimeError("MainActivity class anchor missing")
    text = text.replace(anchor, anchor + lock_field, 1)

helper_marker = "  private String hakuText(String prompt) throws Exception {"
if helper_marker not in text:
    generate_signature = "  private String generateText(String prompt) throws Exception {"
    generate_start = text.find(generate_signature)
    if generate_start < 0:
        raise RuntimeError("generateText anchor missing")
    helpers = r'''  private String postJsonHaku(JSONObject payload) throws Exception {
    String key = BuildConfig.HAKU_API_KEY;
    if (key == null || key.trim().isEmpty()) throw new HttpError(503, "HAKU credential is not configured.");
    HttpURLConnection connection = (HttpURLConnection) new URL("https://api.vilao.ai/v1/chat/completions").openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Authorization", "Bearer " + key);
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
    if (status < 200 || status >= 300) throw new HttpError(status, "HAKU HTTP " + status);
    return body.toString();
  }

  private String hakuText(String prompt) throws Exception {
    JSONArray messages = new JSONArray()
      .put(new JSONObject()
        .put("role", "system")
        .put("content", "Return exactly one valid JSON object. No markdown, no code fences, no text outside JSON. Preserve the schema and required keys specified by the user prompt."))
      .put(new JSONObject().put("role", "user").put("content", prompt));
    JSONObject body = new JSONObject()
      .put("model", "claude-haiku-4-5-20251001")
      .put("messages", messages)
      .put("temperature", 0.2)
      .put("max_tokens", 3200)
      .put("stream", false);
    JSONObject result = new JSONObject(postJsonHaku(body));
    JSONArray choices = result.optJSONArray("choices");
    if (choices == null || choices.length() == 0) throw new Exception("HAKU returned no choices.");
    JSONObject choice = choices.optJSONObject(0);
    JSONObject message = choice != null ? choice.optJSONObject("message") : null;
    String content = message != null ? message.optString("content", "").trim() : "";
    if (content.isEmpty()) throw new Exception("HAKU returned empty content.");
    return content;
  }

  private boolean hakuFallbackEligible(Exception error) {
    if (error instanceof HttpError) {
      int status = ((HttpError) error).status;
      if (status == 400 || status == 422) return false;
      return status == 401 || status == 403 || status == 404 || retryable(status);
    }
    return true;
  }

'''
    text = text[:generate_start] + helpers + text[generate_start:]

provider_router = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "HAKU");
    Exception hakuFailure;
    try {
      return parseModelJson(hakuText(prompt)).toString();
    } catch (Exception error) {
      hakuFailure = error;
      if (!hakuFallbackEligible(error)) throw error;
    }

    emit("backroomProvider", "LUNA fallback");
    try {
      return parseModelJson(lunaText(prompt)).toString();
    } catch (Exception lunaFailure) {
      Exception combined = new Exception("HAKU -> LUNA provider chain failed.", lunaFailure);
      combined.addSuppressed(hakuFailure);
      throw combined;
    }
  }'''
text = replace_method(text, "  private String generateText(String prompt) throws Exception {", provider_router)

# Audits/procedural compatibility helpers must obey the same final provider policy instead of
# bypassing it through dormant Gemini helpers.
for signature, replacement in [
    (
      "  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {",
      '''  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {\n    return generateText(prompt);\n  }''',
    ),
    (
      "  private String geminiLevelGenerationText(String prompt) throws Exception {",
      '''  private String geminiLevelGenerationText(String prompt) throws Exception {\n    return generateText(prompt);\n  }''',
    ),
]:
    if signature in text:
        text = replace_method(text, signature, replacement)

# Provider labels must reflect the real primary. Do not rewrite Snapshot labels that describe
# image generation; Snapshot is handled by its own finalizers.
text = text.replace("window.__backroomProvider='Gemini'", "window.__backroomProvider='HAKU'")
text = text.replace("<div class=\\\"role\\\">GAME MASTER</div><div class=\\\"text\\\">Gemini đang xử lý lượt…</div>",
                    "<div class=\\\"role\\\">GAME MASTER</div><div class=\\\"text\\\">HAKU đang xử lý lượt…</div>")

# Strict final contract checks.
for required in [
    "BuildConfig.HAKU_API_KEY",
    'new URL("https://api.vilao.ai/v1/chat/completions")',
    '"claude-haiku-4-5-20251001"',
    '"Return exactly one valid JSON object.',
    '.put("temperature", 0.2)',
    '.put("max_tokens", 3200)',
    'emit("backroomProvider", "HAKU")',
    'emit("backroomProvider", "LUNA fallback")',
    'parseModelJson(hakuText(prompt)).toString()',
    'parseModelJson(lunaText(prompt)).toString()',
    "private static final boolean GEMINI_RUNTIME_ENABLED = false;",
]:
    if required not in text:
        raise RuntimeError("Issue #413 HAKU contract missing: " + required)

start, end = method_bounds(text, "  private String generateText(String prompt) throws Exception {")
generate = text[start:end]
if generate.find("hakuText(prompt)") > generate.find("lunaText(prompt)"):
    raise RuntimeError("Provider order must be HAKU -> LUNA")
if "geminiText(" in generate or "geminiKeyFallbackText(" in generate or "geminiModelMatrixPolicy(" in generate:
    raise RuntimeError("Gemini returned to active generateText routing")

MAIN.write_text(text, encoding="utf-8")
print("Issue #413 fixed: HAKU primary -> LUNA fallback, strict JSON validation, Gemini runtime locked from active text routing.")
