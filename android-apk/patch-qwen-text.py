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

main = replace_once(
    main,
    '  private static final String OPENAI_MODEL = "gpt-5.4-mini";\n',
    '  private static final String QWEN_MODEL = "qwen3.7-plus";\n'
    '  private static final String OPENAI_MODEL = "gpt-5.4-mini";\n',
    "Qwen model constant",
)

qwen_method = r'''  private String qwenText(String prompt) throws Exception {
    if (BuildConfig.QWEN_API_KEY == null || BuildConfig.QWEN_API_KEY.isEmpty()) {
      throw new HttpError(401, "QwenCloud key chưa được cấu hình.");
    }
    JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
    JSONObject body = new JSONObject()
      .put("model", QWEN_MODEL)
      .put("messages", new JSONArray().put(message))
      .put("temperature", 0.75)
      .put("max_tokens", 1800)
      .put("enable_thinking", false);
    JSONObject result = new JSONObject(postJson(
      "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
      BuildConfig.QWEN_API_KEY,
      "Authorization",
      body
    ));
    JSONArray choices = result.optJSONArray("choices");
    JSONObject first = choices != null ? choices.optJSONObject(0) : null;
    JSONObject responseMessage = first != null ? first.optJSONObject("message") : null;
    String text = responseMessage != null ? responseMessage.optString("content", "").trim() : "";
    if (text.isEmpty()) throw new Exception("QwenCloud không trả nội dung.");
    return text;
  }

'''
main = replace_once(
    main,
    '  private String geminiText(String prompt) throws Exception {\n',
    qwen_method + '  private String geminiText(String prompt) throws Exception {\n',
    "Qwen text method",
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
    emit("backroomProvider", "Qwen");
    Exception qwenFailure;
    try {
      return qwenText(prompt);
    } catch (Exception error) {
      qwenFailure = error;
    }

    int qwenCode = qwenFailure instanceof HttpError ? ((HttpError)qwenFailure).status : 0;
    if (!networkFailure(qwenFailure) && (qwenCode == 0 || retryable(qwenCode))) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return qwenText(prompt);
      } catch (Exception secondFailure) {
        qwenFailure = secondFailure;
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
      if (networkFailure(qwenFailure) && networkFailure(gptFailure) && networkFailure(geminiFailure)) {
        throw new Exception(networkFailureMessage());
      }
      String qwenMessage = qwenFailure != null && qwenFailure.getMessage() != null ? qwenFailure.getMessage() : "QwenCloud không khả dụng";
      String gptMessage = gptFailure != null && gptFailure.getMessage() != null ? gptFailure.getMessage() : "GPT không khả dụng";
      String geminiMessage = geminiFailure.getMessage() != null ? geminiFailure.getMessage() : "Gemini không khả dụng";
      throw new Exception("QwenCloud: " + qwenMessage + "; GPT fallback: " + gptMessage + "; Gemini fallback: " + geminiMessage);
    }
  }
'''
main = replace_once(main, old_generate, new_generate, "Qwen-GPT-Gemini provider switch")

main = replace_once(
    main,
    "window.__backroomProvider='GPT';",
    "window.__backroomProvider='Qwen';",
    "default text provider label",
)
main = replace_once(
    main,
    "GPT đang xử lý lượt…",
    "Qwen đang xử lý lượt…",
    "pending text provider label",
)
index = replace_once(
    index,
    'statusEl.textContent="GPT đang xử lý lượt…";',
    'statusEl.textContent="Qwen đang xử lý lượt…";',
    "initial Qwen status",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Game Master text provider order: QwenCloud qwen3.7-plus -> GPT -> Gemini. Qwen thinking remains disabled for lower latency/cost.")
