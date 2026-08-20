from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# Game Master uses the two configured Gemini slots in order before falling back.
old_keys = '''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4
    };
  }
'''
new_keys = '''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2
    };
  }
'''
main = replace_once(main, old_keys, new_keys, "two ordered Gemini Game Master keys")

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
    emit("backroomProvider", "GPT");
    Exception openAiFailure;
    try {
      return openAiText(prompt);
    } catch (Exception error) {
      openAiFailure = error;
    }

    int code = openAiFailure instanceof HttpError ? ((HttpError)openAiFailure).status : 0;
    if (!networkFailure(openAiFailure) && (code == 0 || retryable(code))) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return openAiText(prompt);
      } catch (Exception secondFailure) {
        openAiFailure = secondFailure;
      }
    }

    emit("backroomProvider", "Gemini");
    try {
      return geminiText(prompt);
    } catch (Exception geminiFailure) {
      if (networkFailure(openAiFailure) && networkFailure(geminiFailure)) {
        throw new Exception(networkFailureMessage());
      }
      throw geminiFailure;
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

    Exception gptFailure = null;
    if (BuildConfig.OPENAI_API_KEY != null && !BuildConfig.OPENAI_API_KEY.trim().isEmpty()) {
      emit("backroomProvider", "GPT");
      try {
        return openAiText(prompt);
      } catch (Exception error) {
        gptFailure = error;
      }
      int gptCode = gptFailure instanceof HttpError ? ((HttpError)gptFailure).status : 0;
      if (gptCode == 0 || retryable(gptCode)) {
        try { Thread.sleep(350); } catch (InterruptedException ignored) {}
        try {
          return openAiText(prompt);
        } catch (Exception secondFailure) {
          gptFailure = secondFailure;
        }
      }
    }

    String geminiMessage = geminiFailure != null && geminiFailure.getMessage() != null ? geminiFailure.getMessage() : "Gemini không khả dụng";
    String lunaMessage = lunaFailure != null && lunaFailure.getMessage() != null ? lunaFailure.getMessage() : "Luna không khả dụng";
    if (gptFailure != null) {
      String gptMessage = gptFailure.getMessage() != null ? gptFailure.getMessage() : "GPT không khả dụng";
      throw new Exception("Gemini: " + geminiMessage + "; Luna fallback: " + lunaMessage + "; GPT fallback: " + gptMessage);
    }
    throw new Exception("Gemini: " + geminiMessage + "; Luna fallback: " + lunaMessage);
  }
'''

main = replace_once(main, old_generate, new_generate, "Gemini-Gemini-Luna-GPT provider switch")

main = replace_once(
    main,
    "window.__backroomProvider='GPT';",
    "window.__backroomProvider='Gemini';",
    "default text provider label",
)
main = replace_once(
    main,
    "GPT đang xử lý lượt…",
    "Gemini đang xử lý lượt…",
    "pending text provider label",
)
index = replace_once(
    index,
    'statusEl.textContent="GPT đang xử lý lượt…";',
    'statusEl.textContent="Gemini đang xử lý lượt…";',
    "initial Gemini status",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Game Master provider order: Gemini key 1 -> Gemini key 2 -> Luna -> GPT only when configured.")
