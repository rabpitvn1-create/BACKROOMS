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

index = replace_once(
    index,
    'statusEl.textContent="Gemini đang xử lý lượt…";window.Android.submitTurn(JSON.stringify(state),a)',
    'statusEl.textContent="GPT đang xử lý lượt…";window.Android.submitTurn(JSON.stringify(state),a)',
    "initial provider label",
)

main = replace_once(
    main,
    '      "window.requestSnapshot=requestSnapshot;" +\n',
    '      "window.requestSnapshot=requestSnapshot;" +\n'
    '      "window.__backroomProvider=\'GPT\';window.backroomProvider=function(provider){window.__backroomProvider=provider||\'AI\';var s=document.getElementById(\'status\');if(s)s.textContent=window.__backroomProvider+\' đang xử lý lượt…\';var p=document.querySelector(\'[data-pending=\\\"1\\\"]:not(.player) .text\');if(p)p.textContent=window.__backroomProvider+\' đang xử lý lượt…\';};" +\n',
    "provider status callback",
)

main = replace_once(
    main,
    "if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy. Đang tạo snapshot…';",
    "if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+'. Đang tạo snapshot bằng Gemini…';",
    "completed provider label",
)

main = replace_once(
    main,
    '<div class=\\\"role\\\">GAME MASTER</div><div class=\\\"text\\\">Đang xử lý lượt…</div>',
    '<div class=\\\"role\\\">GAME MASTER</div><div class=\\\"text\\\">GPT đang xử lý lượt…</div>',
    "pending provider label",
)

old_generate = '''  private String generateText(String prompt) throws Exception {
    Exception openAiFailure;
    try {
      return openAiText(prompt);
    } catch (Exception error) {
      openAiFailure = error;
    }

    int code = openAiFailure instanceof HttpError ? ((HttpError)openAiFailure).status : 0;
    if (code == 0 || retryable(code)) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return openAiText(prompt);
      } catch (Exception ignored) {
        // OpenAI unavailable or exhausted; Gemini is the configured fallback.
      }
    }
    return geminiText(prompt);
  }
'''

new_generate = '''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "GPT");
    Exception openAiFailure;
    try {
      return openAiText(prompt);
    } catch (Exception error) {
      openAiFailure = error;
    }

    int code = openAiFailure instanceof HttpError ? ((HttpError)openAiFailure).status : 0;
    if (code == 0 || retryable(code)) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return openAiText(prompt);
      } catch (Exception ignored) {
        // OpenAI unavailable or exhausted; Gemini is the configured fallback.
      }
    }
    emit("backroomProvider", "Gemini");
    return geminiText(prompt);
  }
'''

main = replace_once(main, old_generate, new_generate, "native provider switch")

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Patched APK provider labels: GPT first, Gemini only on actual fallback.")
