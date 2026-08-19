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

# Keep exactly one Gemini key in the final APK source.
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
    return new String[] { BuildConfig.GEMINI_API_KEY_1 };
  }
'''
main = replace_once(main, old_keys, new_keys, "single Gemini key")

claude_method = r'''  private String claudeText(String prompt) throws Exception {
    if (BuildConfig.CLAUDE_API_KEY == null || BuildConfig.CLAUDE_API_KEY.trim().isEmpty()) {
      throw new HttpError(401, "Claude API key chưa được cấu hình.");
    }
    String baseUrl = BuildConfig.CLAUDE_BASE_URL == null ? "" : BuildConfig.CLAUDE_BASE_URL.trim();
    if (baseUrl.isEmpty()) throw new Exception("Claude Base URL chưa được cấu hình.");
    while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

    String model = BuildConfig.CLAUDE_MODEL == null ? "" : BuildConfig.CLAUDE_MODEL.trim();
    if (model.isEmpty()) throw new Exception("Claude model chưa được cấu hình.");

    JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
    JSONObject body = new JSONObject()
      .put("model", model)
      .put("messages", new JSONArray().put(message))
      .put("temperature", 0.75)
      .put("max_tokens", 1800)
      .put("stream", false);

    JSONObject result = new JSONObject(postJson(
      baseUrl + "/chat/completions",
      BuildConfig.CLAUDE_API_KEY,
      "Authorization",
      body
    ));
    JSONArray choices = result.optJSONArray("choices");
    JSONObject first = choices != null ? choices.optJSONObject(0) : null;
    JSONObject responseMessage = first != null ? first.optJSONObject("message") : null;
    String text = responseMessage != null ? responseMessage.optString("content", "").trim() : "";
    if (text.isEmpty()) throw new Exception("Claude không trả nội dung.");
    return text;
  }

'''
main = replace_once(
    main,
    '  private String geminiText(String prompt) throws Exception {\n',
    claude_method + '  private String geminiText(String prompt) throws Exception {\n',
    "Claude text method",
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
    emit("backroomProvider", "Claude");
    Exception claudeFailure;
    try {
      return claudeText(prompt);
    } catch (Exception error) {
      claudeFailure = error;
    }

    int claudeCode = claudeFailure instanceof HttpError ? ((HttpError)claudeFailure).status : 0;
    if (!networkFailure(claudeFailure) && (claudeCode == 0 || retryable(claudeCode))) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return claudeText(prompt);
      } catch (Exception secondFailure) {
        claudeFailure = secondFailure;
      }
    }

    emit("backroomProvider", "GPT");
    Exception gptFailure;
    try {
      return openAiText(prompt);
    } catch (Exception error) {
      gptFailure = error;
    }

    int gptCode = gptFailure instanceof HttpError ? ((HttpError)gptFailure).status : 0;
    if (!networkFailure(gptFailure) && (gptCode == 0 || retryable(gptCode))) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return openAiText(prompt);
      } catch (Exception secondFailure) {
        gptFailure = secondFailure;
      }
    }

    emit("backroomProvider", "Gemini");
    try {
      return geminiText(prompt);
    } catch (Exception geminiFailure) {
      if (networkFailure(claudeFailure) && networkFailure(gptFailure) && networkFailure(geminiFailure)) {
        throw new Exception(networkFailureMessage());
      }
      String claudeMessage = claudeFailure != null && claudeFailure.getMessage() != null ? claudeFailure.getMessage() : "Claude không khả dụng";
      String gptMessage = gptFailure != null && gptFailure.getMessage() != null ? gptFailure.getMessage() : "GPT không khả dụng";
      String geminiMessage = geminiFailure.getMessage() != null ? geminiFailure.getMessage() : "Gemini không khả dụng";
      throw new Exception("Claude: " + claudeMessage + "; GPT fallback: " + gptMessage + "; Gemini fallback: " + geminiMessage);
    }
  }
'''

main = replace_once(main, old_generate, new_generate, "Claude-GPT-Gemini provider switch")

main = replace_once(
    main,
    "window.__backroomProvider='GPT';",
    "window.__backroomProvider='Claude';",
    "default text provider label",
)
main = replace_once(
    main,
    "GPT đang xử lý lượt…",
    "Claude đang xử lý lượt…",
    "pending text provider label",
)
index = replace_once(
    index,
    'statusEl.textContent="GPT đang xử lý lượt…";',
    'statusEl.textContent="Claude đang xử lý lượt…";',
    "initial Claude status",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Game Master provider order: Claude via secret Base URL -> GPT -> one Gemini key.")
