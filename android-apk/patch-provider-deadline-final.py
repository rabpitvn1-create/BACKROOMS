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
# lanes look unavailable on real routed-canon prompts. Keep this compatibility
# helper bounded; the final provider router applies its own per-key lane timeout.
replace_once("    connection.setReadTimeout(5000);\n", "    connection.setReadTimeout(18000);\n", "Gemini read timeout")

old_gemini_config = '''              JSONObject config = new JSONObject()\n                .put("responseMimeType", "application/json")\n                .put("temperature", temperature);\n'''
new_gemini_config = '''              JSONObject config = new JSONObject()\n                .put("responseMimeType", "application/json")\n                .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));\n'''
replace_once(old_gemini_config, new_gemini_config, "Gemini 3.6 generation config")

# The five credential lanes already provide retries. Do not repeat the same lane.
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

# Make successful lane selection visible at runtime. The final router will replace
# this method again, but preserving the label here keeps the intermediate source sane.
old_generate = '''    emit("backroomProvider", "Gemini");\n    Exception geminiFailure;\n    try {\n      return geminiText(prompt);\n    } catch (Exception error) {\n      geminiFailure = error;\n    }\n\n    emit("backroomProvider", "Luna");\n'''
new_generate = '''    emit("backroomProvider", "Gemini");\n    Exception geminiFailure;\n    try {\n      String geminiResult = geminiText(prompt);\n      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));\n      return geminiResult;\n    } catch (Exception error) {\n      geminiFailure = error;\n    }\n\n    emit("backroomProvider", "Luna fallback");\n'''
replace_once(old_generate, new_generate, "runtime provider lane label")

luna_http = r'''  private String postJsonLunaFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(22000);
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

# One Luna attempt is enough after Gemini key fallback.
replace_once("    for (int attempt = 0; attempt < 3; attempt++) {\n", "    for (int attempt = 0; attempt < 1; attempt++) {\n", "single Luna attempt")
replace_once(
    "        if (attempt < 2 && (transport || code == 0 || retryable(code))) {\n",
    "        if (false && (transport || code == 0 || retryable(code))) {\n",
    "disable Luna retry branch",
)

for required in [
    "private String postJsonLunaFast(",
    "setConnectTimeout(5000)",
    "setReadTimeout(22000)",
    "setReadTimeout(18000)",
    'new JSONObject().put("thinkingLevel", "low")',
    'emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1))',
    'emit("backroomProvider", "Luna fallback")',
]:
    if required not in text:
        raise RuntimeError(f"provider deadline/runtime marker missing: {required}")

policy = text[gemini_start:gemini_end]
if '.put("temperature", temperature)' in policy:
    raise RuntimeError("Gemini 3.6 text policy still sends deprecated temperature")

MAIN.write_text(text, encoding="utf-8")
print("Android provider runtime: bounded Gemini compatibility helper and one Luna fallback attempt with 5s connect / 22s read timeout.")
