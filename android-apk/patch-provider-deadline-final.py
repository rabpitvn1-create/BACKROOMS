from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

# Gemini 3.6 Flash has thinking enabled by default. A 5s read timeout made healthy
# lanes look unavailable on real routed-canon prompts. Keep connect fail-fast, but
# give response generation enough time and lower thinking latency explicitly.
replace_once("    connection.setReadTimeout(5000);\n", "    connection.setReadTimeout(18000);\n", "Gemini read timeout")

old_gemini_config = '''              JSONObject config = new JSONObject()\n                .put("responseMimeType", "application/json")\n                .put("temperature", temperature);\n'''
new_gemini_config = '''              JSONObject config = new JSONObject()\n                .put("responseMimeType", "application/json")\n                .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));\n'''
replace_once(old_gemini_config, new_gemini_config, "Gemini 3.6 generation config")

# The five credential lanes already provide retries. Do not repeat the same lane
# after an 18s read timeout; rotate immediately to the next healthy key.
gemini_start = text.index("  private String geminiTextPolicy(String prompt")
gemini_end = text.index("\n  private String geminiText(String prompt)", gemini_start)
gemini_block = text[gemini_start:gemini_end]
old_attempt = "          for (int attempt = 0; attempt < 2; attempt++) {\n"
if gemini_block.count(old_attempt) != 1:
    raise RuntimeError(f"Gemini per-key attempt loop: expected 1 match, found {gemini_block.count(old_attempt)}")
gemini_block = gemini_block.replace(old_attempt, "          for (int attempt = 0; attempt < 1; attempt++) {\n", 1)
gemini_block = gemini_block.replace(
    "              boolean retry = attempt == 0 && code != 401 && code != 403 && code != 429 && (code == 0 || retryable(code));\n",
    "              boolean retry = false;\n",
    1,
)
text = text[:gemini_start] + gemini_block + text[gemini_end:]

# Provider order is intentionally kept compatible with the approved fast path:
# LUNA first, then the five-key Gemini matrix, SOL, and Haku.
luna_http = r'''  private String postJsonLunaFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(12000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
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
      throw new HttpError(status, "Provider HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

'''
anchor = "  private String postJsonFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {\n"
if "private String postJsonLunaFast(" not in text:
    if anchor not in text:
        raise RuntimeError("Luna fast HTTP anchor not found")
    text = text.replace(anchor, luna_http + anchor, 1)

old_call = r'''        JSONObject result = new JSONObject(postJson(
          baseUrl + "/chat/completions",
          BuildConfig.LUNA_API_KEY,
          "Authorization",
          body
        ));
'''
new_call = old_call.replace("postJson(", "postJsonLunaFast(")
replace_once(old_call, new_call, "Luna fast HTTP call")

# One Luna attempt is enough after five Gemini lanes.
replace_once("    for (int attempt = 0; attempt < 3; attempt++) {\n", "    for (int attempt = 0; attempt < 1; attempt++) {\n", "single Luna attempt")
replace_once(
    "        if (attempt < 2 && (transport || code == 0 || retryable(code))) {\n",
    "        if (false && (transport || code == 0 || retryable(code))) {\n",
    "disable Luna retry branch",
)

provider_helpers = r'''  private String openAiProviderText(String raw) throws Exception {
    JSONObject result = new JSONObject(raw);
    JSONArray choices = result.optJSONArray("choices");
    JSONObject first = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
    JSONObject message = first != null ? first.optJSONObject("message") : null;
    Object content = message != null ? message.opt("content") : null;
    StringBuilder text = new StringBuilder();
    if (content instanceof String) {
      text.append(((String) content).trim());
    } else if (content instanceof JSONArray) {
      JSONArray parts = (JSONArray) content;
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        String piece = part != null ? part.optString("text", "").trim() : "";
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
    }
    if (text.length() == 0) throw new Exception("Provider không trả nội dung.");
    return text.toString();
  }

  private JSONObject openAiProviderBody(String model, String prompt) throws Exception {
    return new JSONObject()
      .put("model", model)
      .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)))
      .put("temperature", 0.6)
      .put("max_tokens", 2048)
      .put("stream", false);
  }

  private String solText(String prompt) throws Exception {
    if (BuildConfig.SOL_API_KEY == null || BuildConfig.SOL_API_KEY.trim().isEmpty()) {
      throw new Exception("SOL_API_KEY chưa được cấu hình.");
    }
    JSONObject body = openAiProviderBody("vgpt/gpt-6.1-sol", prompt).put("reasoning_effort", "low");
    String output = openAiProviderText(postJson(
      "https://api.vilao.ai/v1/chat/completions",
      BuildConfig.SOL_API_KEY,
      "Authorization",
      body
    ));
    parseModelJson(output);
    return output;
  }

  private String hakuPost(String endpoint, JSONObject payload, boolean anthropic) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    if (anthropic) {
      connection.setRequestProperty("x-api-key", BuildConfig.HAKU_API_KEY);
      connection.setRequestProperty("anthropic-version", "2023-06-01");
    } else {
      connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    }
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
      throw new HttpError(status, "Haku HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String hakuText(String prompt) throws Exception {
    if (BuildConfig.HAKU_API_KEY == null || BuildConfig.HAKU_API_KEY.trim().isEmpty()) {
      throw new Exception("HAKU_API_KEY chưa được cấu hình.");
    }
    String model = BuildConfig.HAKU_MODEL == null ? "" : BuildConfig.HAKU_MODEL.trim();
    if (model.isEmpty()) model = "claude-haiku-4-5-20251001";
    String base = BuildConfig.HAKU_BASE_URL == null ? "" : BuildConfig.HAKU_BASE_URL.trim();
    if (base.isEmpty()) base = "https://api.anthropic.com/v1/messages";
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
      throw new Exception("HAKU_BASE_URL phải dùng HTTPS.");
    }
    while (base.endsWith("/") && base.length() > "https://".length()) base = base.substring(0, base.length() - 1);

    String output;
    boolean anthropic = base.contains("api.anthropic.com") || base.endsWith("/messages");
    if (anthropic) {
      String endpoint = base.endsWith("/messages") ? base : (base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages");
      JSONObject body = new JSONObject()
        .put("model", model)
        .put("max_tokens", 2048)
        .put("temperature", 0.6)
        .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
      JSONObject result = new JSONObject(hakuPost(endpoint, body, true));
      JSONArray content = result.optJSONArray("content");
      StringBuilder text = new StringBuilder();
      if (content != null) for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        String piece = part != null ? part.optString("text", "").trim() : "";
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
      if (text.length() == 0) throw new Exception("Haku không trả nội dung.");
      output = text.toString();
    } else {
      String endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
      output = openAiProviderText(hakuPost(endpoint, openAiProviderBody(model, prompt), false));
    }
    parseModelJson(output);
    return output;
  }

  private String providerFailureMessage(Exception error) {
    String message = error == null || error.getMessage() == null ? "không khả dụng" : error.getMessage();
    return message.length() > 180 ? message.substring(0, 180) : message;
  }

'''

generate_start = text.index("  private String generateText(String prompt) throws Exception {\n")
generate_end = text.index("\n  private JSONObject parseModelJson(String raw) throws Exception {", generate_start)
new_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Luna");
    Exception lunaFailure;
    try {
      return lunaText(prompt);
    } catch (Exception error) {
      lunaFailure = error;
    }

    emit("backroomProvider", "Gemini");
    Exception geminiFailure;
    try {
      String geminiResult = geminiText(prompt);
      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));
      return geminiResult;
    } catch (Exception error) {
      geminiFailure = error;
    }

    emit("backroomProvider", "SOL");
    Exception solFailure;
    try {
      return solText(prompt);
    } catch (Exception error) {
      solFailure = error;
    }

    emit("backroomProvider", "Haku");
    try {
      return hakuText(prompt);
    } catch (Exception hakuFailure) {
      if (networkFailure(lunaFailure) && networkFailure(geminiFailure)
          && networkFailure(solFailure) && networkFailure(hakuFailure)) {
        throw new Exception(networkFailureMessage());
      }
      throw new Exception(
        "Luna: " + providerFailureMessage(lunaFailure)
        + "; Gemini: " + providerFailureMessage(geminiFailure)
        + "; SOL: " + providerFailureMessage(solFailure)
        + "; Haku: " + providerFailureMessage(hakuFailure)
      );
    }
  }
'''
text = text[:generate_start] + provider_helpers + new_generate + text[generate_end:]

for required in [
    "private String postJsonLunaFast(",
    "setConnectTimeout(12000)",
    "setReadTimeout(12000)",
    "setReadTimeout(18000)",
    'new JSONObject().put("thinkingLevel", "low")',
    'emit("backroomProvider", "Luna")',
    'emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1))',
    'emit("backroomProvider", "SOL")',
    'emit("backroomProvider", "Haku")',
    "BuildConfig.SOL_API_KEY",
    "BuildConfig.HAKU_API_KEY",
]:
    if required not in text:
        raise RuntimeError(f"provider deadline/runtime marker missing: {required}")

policy = text[gemini_start:gemini_end]
if '.put("temperature", temperature)' in policy:
    raise RuntimeError("Gemini 3.6 text policy still sends deprecated temperature")

MAIN.write_text(text, encoding="utf-8")
print("Android provider runtime: LUNA primary -> five-key Gemini matrix -> SOL -> Haku, with credentials injected from CI secrets.")
