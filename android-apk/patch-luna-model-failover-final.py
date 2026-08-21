from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

start = text.find("  private String lunaText(String prompt) throws Exception {")
if start < 0:
    raise RuntimeError("lunaText anchor not found")
brace = text.find("{", start)
if brace < 0:
    raise RuntimeError("lunaText opening brace not found")
depth = 0
end = -1
for i in range(brace, len(text)):
    ch = text[i]
    if ch == "{":
        depth += 1
    elif ch == "}":
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end < 0:
    raise RuntimeError("lunaText closing brace not found")
while end < len(text) and text[end] in "\r\n":
    end += 1

replacement = r'''  private java.util.List<String> lunaModelCandidates(String baseUrl) {
    java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<>();
    String configured = BuildConfig.LUNA_MODEL == null ? "" : BuildConfig.LUNA_MODEL.trim();
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/models").openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.LUNA_API_KEY);
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
      if (status >= 200 && status < 300) {
        JSONObject root = new JSONObject(body.toString());
        JSONArray data = root.optJSONArray("data");
        java.util.ArrayList<String> active = new java.util.ArrayList<>();
        if (data != null) {
          for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            String id = item != null ? item.optString("id", "").trim() : "";
            if (id.isEmpty()) continue;
            String lower = id.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("embedding") || lower.contains("image") || lower.contains("tts") || lower.contains("whisper") || lower.contains("audio")) continue;
            active.add(id);
          }
        }
        if (!configured.isEmpty() && active.contains(configured)) models.add(configured);
        for (String id : active) {
          String lower = id.toLowerCase(java.util.Locale.ROOT);
          if (lower.contains("gpt-5.6") || lower.contains("gpt-5") || lower.contains("claude") || lower.contains("gemini")) models.add(id);
        }
        for (String id : active) models.add(id);
      }
    } catch (Exception ignored) {}

    if (models.isEmpty() && !configured.isEmpty()) models.add(configured);
    models.add("gpt-5.6-sol");
    return new java.util.ArrayList<>(models);
  }

  private boolean lunaInactiveModel(Exception error) {
    String message = error != null && error.getMessage() != null ? error.getMessage() : "";
    String lower = message.toLowerCase(java.util.Locale.ROOT);
    int code = error instanceof HttpError ? ((HttpError)error).status : 0;
    return (code == 400 || code == 404 || code == 503) &&
      (lower.contains("model_inactive") || lower.contains("model") && (lower.contains("inactive") || lower.contains("not found") || lower.contains("unavailable")));
  }

  private String lunaText(String prompt) throws Exception {
    if (BuildConfig.LUNA_API_KEY == null || BuildConfig.LUNA_API_KEY.trim().isEmpty()) {
      throw new HttpError(401, "Luna API key chưa được cấu hình.");
    }
    String baseUrl = BuildConfig.LUNA_BASE_URL == null ? "" : BuildConfig.LUNA_BASE_URL.trim();
    if (baseUrl.isEmpty()) throw new Exception("Luna Base URL chưa được cấu hình.");
    while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

    Exception last = null;
    java.util.List<String> models = lunaModelCandidates(baseUrl);
    for (String model : models) {
      if (model == null || model.trim().isEmpty()) continue;
      JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
      JSONObject body = new JSONObject()
        .put("model", model.trim())
        .put("messages", new JSONArray().put(message))
        .put("temperature", 0.75)
        .put("max_tokens", 1800)
        .put("stream", false);
      try {
        JSONObject result = new JSONObject(postJsonLunaFast(
          baseUrl + "/chat/completions",
          BuildConfig.LUNA_API_KEY,
          "Authorization",
          body
        ));
        JSONArray choices = result.optJSONArray("choices");
        JSONObject first = choices != null ? choices.optJSONObject(0) : null;
        JSONObject responseMessage = first != null ? first.optJSONObject("message") : null;
        String responseText = responseMessage != null ? responseMessage.optString("content", "").trim() : "";
        if (responseText.isEmpty()) throw new Exception("Luna không trả nội dung.");
        emit("backroomProvider", "Luna fallback / " + model.trim());
        return responseText;
      } catch (Exception error) {
        last = error;
        if (lunaInactiveModel(error)) continue;
        int code = error instanceof HttpError ? ((HttpError)error).status : 0;
        boolean transport = networkFailure(error) || error instanceof java.net.SocketException || error instanceof java.io.IOException;
        if (transport || code == 408 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504) continue;
        break;
      }
    }
    throw last != null ? last : new Exception("Luna không có model chat khả dụng.");
  }

'''

text = text[:start] + replacement + text[end:]
for marker in [
    "private java.util.List<String> lunaModelCandidates(",
    'new URL(baseUrl + "/models")',
    'models.add("gpt-5.6-sol")',
    "private boolean lunaInactiveModel(",
    'emit("backroomProvider", "Luna fallback / " + model.trim())',
    "private String postJsonLunaFast(",
    "private String postJsonFast(",
    "private int chooseGeminiWorker(",
]:
    if marker not in text:
        raise RuntimeError(f"Provider helper missing after Luna patch: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Luna fallback now discovers active provider models and skips MODEL_INACTIVE candidates without removing provider helpers.")
