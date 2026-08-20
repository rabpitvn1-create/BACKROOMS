from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

old_keys = r'''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3
    };
  }
'''
new_keys = r'''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };
  }
'''
main = replace_once(main, old_keys, new_keys, "five Gemini Game Master keys")

luna_method = r'''  private String lunaText(String prompt) throws Exception {
    if (BuildConfig.LUNA_API_KEY == null || BuildConfig.LUNA_API_KEY.trim().isEmpty()) {
      throw new HttpError(401, "Luna API key chưa được cấu hình.");
    }
    String baseUrl = BuildConfig.LUNA_BASE_URL == null ? "" : BuildConfig.LUNA_BASE_URL.trim();
    if (baseUrl.isEmpty()) throw new Exception("Luna Base URL chưa được cấu hình.");
    while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

    String model = BuildConfig.LUNA_MODEL == null ? "" : BuildConfig.LUNA_MODEL.trim();
    if (model.isEmpty()) throw new Exception("Luna model chưa được cấu hình.");

    JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
    JSONObject body = new JSONObject()
      .put("model", model)
      .put("messages", new JSONArray().put(message))
      .put("temperature", 0.75)
      .put("max_tokens", 1800)
      .put("stream", false);

    Exception last = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        JSONObject result = new JSONObject(postJson(
          baseUrl + "/chat/completions",
          BuildConfig.LUNA_API_KEY,
          "Authorization",
          body
        ));
        JSONArray choices = result.optJSONArray("choices");
        JSONObject first = choices != null ? choices.optJSONObject(0) : null;
        JSONObject responseMessage = first != null ? first.optJSONObject("message") : null;
        String text = responseMessage != null ? responseMessage.optString("content", "").trim() : "";
        if (text.isEmpty()) throw new Exception("Luna không trả nội dung.");
        return text;
      } catch (Exception e) {
        last = e;
        int code = e instanceof HttpError ? ((HttpError)e).status : 0;
        boolean transport = networkFailure(e) || e instanceof java.net.SocketException || e instanceof java.io.IOException;
        if (attempt < 2 && (transport || code == 0 || retryable(code))) {
          try { Thread.sleep(450L * (attempt + 1)); } catch (InterruptedException ignored) {}
          continue;
        }
        break;
      }
    }
    throw last != null ? last : new Exception("Luna không khả dụng.");
  }

'''
main = replace_once(
    main,
    '  private String geminiText(String prompt) throws Exception {\n',
    luna_method + '  private String geminiText(String prompt) throws Exception {\n',
    "Luna text method",
)

old_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Gemini");
    try {
      return geminiText(prompt);
    } catch (Exception error) {
      if (networkFailure(error)) throw new Exception(networkFailureMessage());
      throw error;
    }
  }
'''

new_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Gemini");
    Exception geminiFailure;
    try {
      return geminiText(prompt);
    } catch (Exception error) {
      geminiFailure = error;
    }

    emit("backroomProvider", "Luna");
    Exception lunaFailure;
    try {
      return lunaText(prompt);
    } catch (Exception error) {
      lunaFailure = error;
    }

    if (networkFailure(geminiFailure) && networkFailure(lunaFailure)) {
      throw new Exception(networkFailureMessage());
    }
    String geminiMessage = geminiFailure != null && geminiFailure.getMessage() != null ? geminiFailure.getMessage() : "Gemini không khả dụng";
    String lunaMessage = lunaFailure != null && lunaFailure.getMessage() != null ? lunaFailure.getMessage() : "Luna không khả dụng";
    throw new Exception("Gemini: " + geminiMessage + "; Luna fallback: " + lunaMessage);
  }
'''

main = replace_once(main, old_generate, new_generate, "Gemini-Luna provider switch")

MAIN.write_text(main, encoding="utf-8")
print("Game Master provider order: Gemini key 1 -> key 2 -> key 3 -> key 4 -> key 5 -> Luna.")
