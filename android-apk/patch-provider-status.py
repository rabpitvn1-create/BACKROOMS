from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
OVERLAY = ROOT / "app/src/main/assets/BESTKAIV2.png"


def replace_required(text: str, old: str, new: str, label: str) -> str:
    pos = text.find(old)
    if pos < 0:
        raise RuntimeError(f"{label}: source anchor not found")
    return text[:pos] + new + text[pos + len(old):]


def replace_if_needed(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    return replace_required(text, old, new, label)


# The overlay is a normal packaged PNG asset. No base64 reconstruction belongs in the build.
raw = OVERLAY.read_bytes()
if len(raw) < 24 or raw[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("BestKai overlay asset is not a valid packaged PNG")

main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

provider_callback = (
    '      "window.requestSnapshot=requestSnapshot;" +\n'
    '      "window.__backroomProvider=\'Gemini\';window.backroomProvider=function(provider){window.__backroomProvider=provider||\'AI\';var s=document.getElementById(\'status\');if(s)s.textContent=window.__backroomProvider+\' đang xử lý lượt…\';var p=document.querySelector(\'[data-pending=\\\\\\"1\\\\\\"]:not(.player) .text\');if(p)p.textContent=window.__backroomProvider+\' đang xử lý lượt…\';};" +\n'
)
if "window.__backroomProvider='Gemini'" not in main:
    main = replace_required(main, '      "window.requestSnapshot=requestSnapshot;" +\n', provider_callback, "provider status callback")

main = replace_if_needed(
    main,
    "if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy. Đang tạo snapshot…';",
    "if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+'. Đang tạo snapshot bằng Gemini…';",
    "completed provider label",
)
main = replace_if_needed(
    main,
    "if(typeof oldError==='function')oldError(message);scrollBottom();",
    "if(typeof oldError==='function')oldError(message);var s=document.getElementById('status');if(s)s.textContent=String(message||'').indexOf('Lỗi mạng/DNS:')===0?message:'Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();",
    "provider error label",
)
main = replace_if_needed(
    main,
    '<div class=\\\"role\\\">GAME MASTER</div><div class=\\\"text\\\">Đang xử lý lượt…</div>',
    '<div class=\\\"role\\\">GAME MASTER</div><div class=\\\"text\\\">Gemini đang xử lý lượt…</div>',
    "pending provider label",
)

old_css = ".snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot{position:relative;overflow:hidden;height:230px}.snapshot .snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot .snapshot-character{position:absolute;right:0;bottom:0;width:46%;height:96%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;filter:drop-shadow(0 4px 8px rgba(0,0,0,.58))}.snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
main = replace_if_needed(main, old_css, new_css, "snapshot layered styles")

old_render = "if(r){var img=document.createElement('img');img.src=r.dataUri;img.alt='Snapshot Turn '+(state.turn||'');box.appendChild(img);}else{"
new_render = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/BestKai.png';kai.alt='Kai Akechi';box.appendChild(kai);}else{"
main = replace_if_needed(main, old_render, new_render, "snapshot character overlay")

old_prompt = '"Show the present scene only, not a montage. Kai Akechi / Twilight is the main character. " +'
new_prompt = (
    '"Show the present scene only, not a montage. Do NOT depict Kai Akechi / Twilight or any player-character body in the generated image; the app overlays Kai separately. " +\n'
    '      "Compose the environment for a fixed character overlay: keep the right 40% visually open and place key environmental details, threats and exits in the left or center. " +'
)
main = replace_if_needed(main, old_prompt, new_prompt, "snapshot background-only composition")
main = replace_if_needed(
    main,
    '"If party is empty, Kai is alone. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +',
    '"If party is empty, do not add any other person or humanoid companion. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +',
    "snapshot no duplicate character",
)

network_helpers = r'''  private boolean networkFailure(Exception error) {
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

'''
if "private boolean networkFailure(Exception error)" not in main:
    anchor = '  private String generateText(String prompt) throws Exception {\n'
    main = replace_required(main, anchor, network_helpers + anchor, "network helpers")

new_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Gemini");
    try {
      return geminiText(prompt);
    } catch (Exception error) {
      if (networkFailure(error)) throw new Exception(networkFailureMessage());
      throw error;
    }
  }
'''
pattern = r'  private String generateText\(String prompt\) throws Exception \{.*?\n  \}\n(?=\n  private JSONObject parseModelJson)'
main, count = re.subn(pattern, lambda _: new_generate.rstrip("\n"), main, count=1, flags=re.S)
if count != 1:
    raise RuntimeError(f"Gemini provider switch: expected 1 generateText method, found {count}")

gemini_start = main.find("  private String geminiText(String prompt) throws Exception {")
network_start = main.find("  private boolean networkFailure(Exception error)")
if gemini_start < 0 or network_start < 0 or network_start <= gemini_start:
    raise RuntimeError("geminiText/network helper boundaries not found")
gemini_block = main[gemini_start:network_start]
old_catch = '''        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
'''
new_catch = '''        } catch (Exception e) {
          last = e;
          if (networkFailure(e)) throw e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
'''
if "if (networkFailure(e)) throw e;" not in gemini_block:
    gemini_block = replace_required(gemini_block, old_catch, new_catch, "Gemini text DNS short-circuit")
main = main[:gemini_start] + gemini_block + main[network_start:]

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(f"Patched Gemini provider labels, socket/DNS handling and packaged Kai snapshot overlay ({len(raw)} bytes).")
