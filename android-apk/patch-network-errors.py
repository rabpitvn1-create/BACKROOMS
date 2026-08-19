from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")


def sub_once(pattern: str, replacement, text: str, label: str, flags=0) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return out

# Helpers classify DNS/transport failures separately from provider quota/auth errors.
network_helpers = r'''  private boolean networkFailure(Exception error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.net.UnknownHostException ||
          cause instanceof java.net.ConnectException ||
          cause instanceof java.net.SocketTimeoutException) return true;
      cause = cause.getCause();
    }
    return false;
  }

  private String networkFailureMessage() {
    return "Lỗi mạng/DNS: không thể kết nối tới máy chủ AI. Kiểm tra Wi-Fi/4G, Private DNS hoặc VPN.";
  }

'''
main = sub_once(
    r'(?=  private String generateText\(String prompt\) throws Exception \{)',
    network_helpers,
    main,
    "insert network helpers",
)

# Text: GPT remains first, Gemini remains fallback. DNS on both is reported as network, not "Gemini error".
new_generate = r'''  private String generateText(String prompt) throws Exception {
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
main = sub_once(
    r'  private String generateText\(String prompt\) throws Exception \{.*?\n  \}\n\n(?=  private JSONObject parseModelJson)',
    lambda m: new_generate,
    main,
    "network-aware text fallback",
    flags=re.S,
)

# Gemini text keys all use the same hostname. A DNS failure should not waste time trying every key.
match = re.search(r'(  private String geminiText\(String prompt\) throws Exception \{.*?)(\n  \}\n\n  private boolean networkFailure)', main, flags=re.S)
if not match:
    raise RuntimeError("geminiText block not found for DNS short-circuit")
gemini_block = match.group(1)
old = '        } catch (Exception e) {\n          last = e;\n          int code = e instanceof HttpError ? ((HttpError)e).status : 0;'
new = '        } catch (Exception e) {\n          last = e;\n          if (networkFailure(e)) throw e;\n          int code = e instanceof HttpError ? ((HttpError)e).status : 0;'
if gemini_block.count(old) != 1:
    raise RuntimeError(f"geminiText DNS catch: expected 1 match, found {gemini_block.count(old)}")
gemini_block = gemini_block.replace(old, new, 1)
main = main[:match.start(1)] + gemini_block + main[match.end(1):]

# Snapshot provider pipeline: if a provider hostname cannot resolve, cross providers immediately.
main = sub_once(
    r'(private String friendlyImageFailure\(String provider, Exception error\) \{\n    if \(error == null\) return provider \+ ": không khả dụng";\n)',
    r'\1    if (networkFailure(error)) return provider + ": lỗi mạng/DNS";\n',
    main,
    "friendly image DNS error",
)

# First catch is Gemini family, second is GPT family. Both should stop trying sibling models on DNS failure.
if main.count('        geminiFailure = e;\n') != 1:
    raise RuntimeError("Gemini snapshot failure assignment not found exactly once")
main = main.replace(
    '        geminiFailure = e;\n',
    '        geminiFailure = e;\n        if (networkFailure(e)) break;\n',
    1,
)
if main.count('        gptFailure = e;\n') != 1:
    raise RuntimeError("GPT snapshot failure assignment not found exactly once")
main = main.replace(
    '        gptFailure = e;\n',
    '        gptFailure = e;\n        if (networkFailure(e)) break;\n',
    1,
)

# Also stop retrying the same model on DNS inside each image provider method.
for method_name in ("geminiImageModel", "openAiImageModel"):
    pattern = rf'(  private SnapshotImage {method_name}\(.*?\n  \}}\n)'
    m = re.search(pattern, main, flags=re.S)
    if not m:
        raise RuntimeError(f"{method_name} block not found")
    block = m.group(1)
    needle = '      } catch (Exception e) {\n        last = e;\n        int code = e instanceof HttpError ? ((HttpError)e).status : 0;'
    repl = '      } catch (Exception e) {\n        last = e;\n        if (networkFailure(e)) throw e;\n        int code = e instanceof HttpError ? ((HttpError)e).status : 0;'
    if block.count(needle) != 1:
        raise RuntimeError(f"{method_name} DNS catch: expected 1 match, found {block.count(needle)}")
    patched = block.replace(needle, repl, 1)
    main = main[:m.start(1)] + patched + main[m.end(1):]

# Do not prefix a genuine network error with the last provider name in the UI.
old_ui = "if(s)s.textContent='Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();"
new_ui = "if(s)s.textContent=String(message||'').indexOf('Lỗi mạng/DNS:')===0?message:'Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();"
if main.count(old_ui) != 1:
    raise RuntimeError(f"network UI error label: expected 1 match, found {main.count(old_ui)}")
main = main.replace(old_ui, new_ui, 1)

MAIN.write_text(main, encoding="utf-8")
print("Network handling patched: DNS failures cross providers immediately and show a network/DNS error.")
