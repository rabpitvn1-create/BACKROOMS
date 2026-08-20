import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def sub_once(pattern: str, replacement: str, text: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return updated


main = MAIN.read_text(encoding="utf-8")

# Snapshot intentionally has no configured provider. Keep the UI affordance, but
# stop requests in JavaScript and keep the native bridge network-free as defense
# in depth for any older or externally-triggered call.
main = replace_once(
    main,
    "var snapshotBusy=false;function requestSnapshot(){if(snapshotBusy)return;if(!window.Android||typeof Android.requestSnapshot!=='function'){var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android snapshot bridge.';return;}snapshotBusy=true;var s=document.getElementById('status');if(s)s.textContent='AI đang tạo snapshot…';Android.requestSnapshot(JSON.stringify(state));}",
    "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot chưa được cấu hình.';}",
    "disable Snapshot JavaScript request",
)
main = replace_once(
    main,
    "<b>AI SNAPSHOT</b>",
    "<b>SNAPSHOT</b>",
    "neutral Snapshot label",
)
main = replace_once(
    main,
    "b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',requestSnapshot);",
    "b.id='snapshotButton';b.type='button';b.textContent='Snapshot chưa cấu hình';b.disabled=true;",
    "disable Snapshot button",
)

request_method = r'''  private void requestSnapshotInternal(String stateJson) {
    try {
      JSONObject state = new JSONObject(stateJson);
      int turn = state.optInt("turn", 1);
      JSONObject payload = new JSONObject()
        .put("turn", turn)
        .put("message", "Snapshot chưa được cấu hình.");
      emit("backroomSnapshotError", payload.toString());
    } catch (Exception ignored) {
      emit("backroomSnapshotError", "{\"turn\":0,\"message\":\"Snapshot chưa được cấu hình.\"}");
    }
  }

'''
main = sub_once(
    r'  private void requestSnapshotInternal\(String stateJson\) \{.*?\n  \}\n\n(?=  private void emit)',
    lambda _: request_method,
    main,
    "disable native Snapshot provider",
)

MAIN.write_text(main, encoding="utf-8")
print("Snapshot provider is intentionally unconfigured; no image API is called.")
