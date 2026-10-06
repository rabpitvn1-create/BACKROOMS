from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

replace_once("    connection.setReadTimeout(5000);\n", "    connection.setReadTimeout(18000);\n", "Gemini read timeout")

old_gemini_config = '''              JSONObject config = new JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", temperature);
'''
new_gemini_config = '''              JSONObject config = new JSONObject()
                .put("responseMimeType", "application/json")
                .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
'''
replace_once(old_gemini_config, new_gemini_config, "Gemini generation config")

gemini_start = text.index("  private String geminiTextPolicy(String prompt")
gemini_end = text.index("\n  private String geminiText(String prompt)", gemini_start)
gemini_block = text[gemini_start:gemini_end]
old_attempt = "          for (int attempt = 0; attempt < 2; attempt++) {\n"
if gemini_block.count(old_attempt) != 1:
    raise RuntimeError(f"Gemini per-key attempt loop: expected 1 match, found {gemini_block.count(old_attempt)}")
gemini_block = gemini_block.replace(old_attempt, "          for (int attempt = 0; attempt < 1; attempt++) {\n", 1)
gemini_block = gemini_block.replace(
    "              boolean retry = attempt == 0 && code != 401 && code != 403 && code != 429 && (code == 0 || retryable(code));\n",
    "              boolean retry = false;\n", 1)
text = text[:gemini_start] + gemini_block + text[gemini_end:]

provider_helpers = r'''  private String openAiProviderText(String raw) throws Exception {
    JSONObject result = new JSONObject(raw);
    JSONArray choices = result.optJSONArray("choices");
    JSONObject first = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
    JSONObject message = first != null ? first.optJSONObject("message") : null;
    Object content = message != null ? message.opt("content") : null;
    StringBuilder output = new StringBuilder();
    if (content instanceof String) {
      output.append(((String) content).trim());
    } else if (content instanceof JSONArray) {
      JSONArray parts = (JSONArray) content;
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        String piece = part != null ? part.optString("text", "").trim() : "";
        if (!piece.isEmpty()) {
          if (output.length() > 0) output.append('\n');
          output.append(piece);
        }
      }
    }
    if (output.length() == 0) throw new Exception("Provider không trả nội dung.");
    return output.toString();
  }

  private JSONObject openAiProviderBody(String model, String prompt) throws Exception {
    return new JSONObject()
      .put("model", model)
      .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)))
      .put("temperature", 0.6)
      .put("max_tokens", 2048)
      .put("stream", false);
  }

  private String gehihiText(String prompt) throws Exception {
    if (BuildConfig.GEHIHI_API_KEY == null || BuildConfig.GEHIHI_API_KEY.trim().isEmpty()) {
      throw new Exception("GEHIHI_API_KEY chưa được cấu hình.");
    }
    String model = BuildConfig.GEHIHI_MODEL == null ? "" : BuildConfig.GEHIHI_MODEL.trim();
    if (model.isEmpty()) throw new Exception("GEHIHI_MODEL chưa được cấu hình.");
    String base = BuildConfig.GEHIHI_BASE_URL == null ? "" : BuildConfig.GEHIHI_BASE_URL.trim();
    base = base.isEmpty() ? "https://api.vilao.ai/v1" : base;
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) throw new Exception("GEHIHI_BASE_URL phải dùng HTTPS.");
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    String output = openAiProviderText(postJson(base + "/chat/completions", BuildConfig.GEHIHI_API_KEY, "Authorization", openAiProviderBody(model, prompt)));
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
    } else connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    try (OutputStream output = connection.getOutputStream()) { output.write(payload.toString().getBytes("UTF-8")); }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
      String line; while ((line = reader.readLine()) != null) body.append(line);
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Haku HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String hakuText(String prompt) throws Exception {
    if (BuildConfig.HAKU_API_KEY == null || BuildConfig.HAKU_API_KEY.trim().isEmpty()) throw new Exception("HAKU_API_KEY chưa được cấu hình.");
    String model = BuildConfig.HAKU_MODEL == null ? "" : BuildConfig.HAKU_MODEL.trim();
    if (model.isEmpty()) model = "claude-haiku-4-5-20251001";
    String base = BuildConfig.HAKU_BASE_URL == null ? "" : BuildConfig.HAKU_BASE_URL.trim();
    if (base.isEmpty()) base = "https://api.anthropic.com/v1/messages";
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) throw new Exception("HAKU_BASE_URL phải dùng HTTPS.");
    while (base.endsWith("/") && base.length() > "https://".length()) base = base.substring(0, base.length() - 1);
    String output;
    boolean anthropic = base.contains("api.anthropic.com") || base.endsWith("/messages");
    if (anthropic) {
      String endpoint = base.endsWith("/messages") ? base : (base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages");
      JSONObject body = new JSONObject().put("model", model).put("max_tokens", 2048).put("temperature", 0.6)
        .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
      JSONObject result = new JSONObject(hakuPost(endpoint, body, true));
      JSONArray content = result.optJSONArray("content");
      StringBuilder out = new StringBuilder();
      if (content != null) for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        String piece = part != null ? part.optString("text", "").trim() : "";
        if (!piece.isEmpty()) { if (out.length() > 0) out.append('\n'); out.append(piece); }
      }
      if (out.length() == 0) throw new Exception("Haku không trả nội dung.");
      output = out.toString();
    } else {
      String endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
      output = openAiProviderText(hakuPost(endpoint, openAiProviderBody(model, prompt), false));
    }
    parseModelJson(output);
    return output;
  }

  private String solText(String prompt) throws Exception {
    if (BuildConfig.SOL_API_KEY == null || BuildConfig.SOL_API_KEY.trim().isEmpty()) throw new Exception("SOL_API_KEY chưa được cấu hình.");
    JSONObject body = openAiProviderBody("vgpt/gpt-6.1-sol", prompt).put("reasoning_effort", "low");
    String output = openAiProviderText(postJson("https://api.vilao.ai/v1/chat/completions", BuildConfig.SOL_API_KEY, "Authorization", body));
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
    emit("backroomProvider", "Gehihi");
    Exception gehihiFailure;
    try { return gehihiText(prompt); } catch (Exception error) { gehihiFailure = error; }

    emit("backroomProvider", "Gemini");
    Exception geminiFailure;
    try {
      String geminiResult = geminiText(prompt);
      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));
      return geminiResult;
    } catch (Exception error) { geminiFailure = error; }

    emit("backroomProvider", "Haku");
    Exception hakuFailure;
    try { return hakuText(prompt); } catch (Exception error) { hakuFailure = error; }

    emit("backroomProvider", "SOL");
    try { return solText(prompt); } catch (Exception solFailure) {
      if (networkFailure(gehihiFailure) && networkFailure(geminiFailure) && networkFailure(hakuFailure) && networkFailure(solFailure))
        throw new Exception(networkFailureMessage());
      throw new Exception("Gehihi: " + providerFailureMessage(gehihiFailure)
        + "; Gemini: " + providerFailureMessage(geminiFailure)
        + "; Haku: " + providerFailureMessage(hakuFailure)
        + "; SOL: " + providerFailureMessage(solFailure));
    }
  }
'''
text = text[:generate_start] + provider_helpers + new_generate + text[generate_end:]

provider = text[text.index("  private String generateText(String prompt)"):text.index("\n  private JSONObject parseModelJson", text.index("  private String generateText(String prompt)"))]
if not (provider.index('"Gehihi"') < provider.index('"Gemini"') < provider.index('"Haku"') < provider.index('"SOL"')): raise RuntimeError("provider order")
if ("LU" + "NA_") in text or ("lu" + "naText") in text: raise RuntimeError("retired provider residue")
MAIN.write_text(text, encoding="utf-8")
print("Android provider runtime: Gehihi primary -> five-key Gemini fallback -> Haku -> SOL.")
