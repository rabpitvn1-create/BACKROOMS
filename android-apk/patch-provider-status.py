from pathlib import Path
import base64

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
PARTS_DIR = ROOT / "kai-overlay-parts"
OVERLAY = ROOT / "app/src/main/assets/kai_snapshot_overlay.webp"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


parts = sorted(PARTS_DIR.glob("part*.b64"))
if len(parts) != 5:
    raise RuntimeError(f"Kai overlay: expected 5 base64 parts, found {len(parts)}")

encoded = "".join(part.read_text(encoding="ascii").strip() for part in parts)
overlay_bytes = base64.b64decode(encoded, validate=True)
if len(overlay_bytes) < 16 or overlay_bytes[:4] != b"RIFF" or overlay_bytes[8:12] != b"WEBP":
    raise RuntimeError("Kai overlay: reconstructed file is not a valid WebP container")
OVERLAY.write_bytes(overlay_bytes)

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
    '      "window.__backroomProvider=\'GPT\';window.backroomProvider=function(provider){window.__backroomProvider=provider||\'AI\';var s=document.getElementById(\'status\');if(s)s.textContent=window.__backroomProvider+\' đang xử lý lượt…\';var p=document.querySelector(\'[data-pending=\\\\\\"1\\\\\\"]:not(.player) .text\');if(p)p.textContent=window.__backroomProvider+\' đang xử lý lượt…\';};" +\n',
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
    "if(typeof oldError==='function')oldError(message);scrollBottom();",
    "if(typeof oldError==='function')oldError(message);var s=document.getElementById('status');if(s)s.textContent=String(message||'').indexOf('Lỗi mạng/DNS:')===0?message:'Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();",
    "provider error label",
)

main = replace_once(
    main,
    "Đang xử lý lượt…",
    "GPT đang xử lý lượt…",
    "pending provider label",
)

main = replace_once(
    main,
    ".snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}",
    ".snapshot{position:relative;overflow:hidden;height:230px}.snapshot .snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot .snapshot-character{position:absolute;right:0;bottom:0;width:46%;height:96%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;filter:drop-shadow(0 4px 8px rgba(0,0,0,.58))}.snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}",
    "snapshot layered styles",
)

main = replace_once(
    main,
    "if(r){var img=document.createElement('img');img.src=r.dataUri;img.alt='Snapshot Turn '+(state.turn||'');box.appendChild(img);}else{",
    "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{",
    "snapshot character overlay",
)

main = replace_once(
    main,
    '"Show the present scene only, not a montage. Kai Akechi / Twilight is the main character. " +',
    '"Show the present scene only, not a montage. Do NOT depict Kai Akechi / Twilight or any player-character body in the generated image; the app overlays Kai separately. " +\n'
    '      "Compose the environment for a fixed character overlay: keep the right 40% visually open and place key environmental details, threats and exits in the left or center. " +',
    "snapshot background-only composition",
)

main = replace_once(
    main,
    '"If party is empty, Kai is alone. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +',
    '"If party is empty, do not add any other person or humanoid companion. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +',
    "snapshot no duplicate character",
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

new_generate = '''  private boolean networkFailure(Exception error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.net.UnknownHostException ||
          cause instanceof java.net.ConnectException ||
          cause instanceof java.net.SocketTimeoutException ||
          cause instanceof java.net.SocketException ||
          cause instanceof java.io.IOException) return true;
      cause = cause.getCause();
    }
    return false;
  }

  private String networkFailureMessage() {
    return "Lỗi mạng/DNS: không thể kết nối tới máy chủ AI. Kiểm tra Wi-Fi/4G, Private DNS hoặc VPN.";
  }

  private String generateText(String prompt) throws Exception {
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

main = replace_once(main, old_generate, new_generate, "native provider switch")

gemini_start = main.find("  private String geminiText(String prompt) throws Exception {")
generate_start = main.find("  private boolean networkFailure(Exception error)")
if gemini_start < 0 or generate_start < 0 or generate_start <= gemini_start:
    raise RuntimeError("geminiText block boundaries not found")
gemini_block = main[gemini_start:generate_start]
old_catch = '''        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
'''
new_catch = '''        } catch (Exception e) {
          last = e;
          if (networkFailure(e)) throw e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
'''
gemini_block = replace_once(gemini_block, old_catch, new_catch, "Gemini text DNS short-circuit")
main = main[:gemini_start] + gemini_block + main[generate_start:]

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(
    f"Patched APK provider labels, socket/DNS-aware fallback and Kai snapshot overlay "
    f"({len(overlay_bytes)} bytes)."
)
