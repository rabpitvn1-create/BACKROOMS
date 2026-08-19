from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
HD_SOURCE = ROOT / "kai_snapshot_overlay_hd.webp"
OVERLAY = ROOT / "app/src/main/assets/kai_snapshot_overlay.webp"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# Use the user's current Kai artwork as the authoritative overlay.
# Do not pin the byte size/hash: replacing Kai with another high-quality revision
# must not break the APK build simply because the asset changed.
raw = HD_SOURCE.read_bytes()
if len(raw) < 30 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
    raise RuntimeError("HD Kai asset is not a valid WebP container")

width = height = 0
if raw[12:16] == b"VP8X" and len(raw) >= 30:
    width = 1 + int.from_bytes(raw[24:27], "little")
    height = 1 + int.from_bytes(raw[27:30], "little")
    if width < 512 or height < 768:
        raise RuntimeError(f"HD Kai asset is too small: {width}x{height}; need at least 512x768")

OVERLAY.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(HD_SOURCE, OVERLAY)

main = MAIN.read_text(encoding="utf-8")

old_css = (
    ".snapshot .snapshot-character{position:absolute;right:0;bottom:0;width:46%;height:96%;"
    "object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;"
    "filter:drop-shadow(0 4px 8px rgba(0,0,0,.58))}"
)
new_css = (
    ".snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;"
    "max-width:55%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;"
    "image-rendering:auto}"
)
main = replace_once(main, old_css, new_css, "crisp Kai CSS")

main = replace_once(
    main,
    "kai.src='file:///android_asset/kai_snapshot_overlay.webp';",
    "kai.src='kai_snapshot_overlay.webp';",
    "Kai packaged relative asset path",
)

old_request = (
    "function requestSnapshot(){if(!window.Android||typeof Android.requestSnapshot!=='function')"
    "{var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android snapshot bridge.';"
    "return;}var s=document.getElementById('status');if(s)s.textContent='Gemini đang tạo snapshot…';"
    "Android.requestSnapshot(JSON.stringify(state));}"
)
new_request = (
    "var snapshotBusy=false,snapshotPending=false,snapshotTimer=null;"
    "function scheduleSnapshot(delay){if(snapshotTimer)clearTimeout(snapshotTimer);"
    "snapshotTimer=setTimeout(requestSnapshot,delay);}"
    "function snapshotCycleDone(){snapshotBusy=false;var soon=snapshotPending;"
    "snapshotPending=false;scheduleSnapshot(soon?700:30000);}"
    "function requestSnapshot(){if(snapshotTimer){clearTimeout(snapshotTimer);snapshotTimer=null;}"
    "if(snapshotBusy){snapshotPending=true;return;}"
    "if(!window.Android||typeof Android.requestSnapshot!=='function'){var s=document.getElementById('status');"
    "if(s)s.textContent='Không tìm thấy Android snapshot bridge.';scheduleSnapshot(30000);return;}"
    "snapshotBusy=true;var s=document.getElementById('status');"
    "if(s)s.textContent='AI đang tạo snapshot…';Android.requestSnapshot(JSON.stringify(state));}"
)
main = replace_once(main, old_request, new_request, "continuous Snapshot scheduler")

main = replace_once(
    main,
    "window.backroomSnapshot=function(payload){try{",
    "window.backroomSnapshot=function(payload){snapshotCycleDone();try{",
    "Snapshot success cycle",
)
main = replace_once(
    main,
    "window.backroomSnapshotError=function(payload){try{",
    "window.backroomSnapshotError=function(payload){snapshotCycleDone();try{",
    "Snapshot error cycle",
)

old_tail = (
    "renderSnapshot();scrollBottom();if(typeof state!=='undefined'&&state&&!cachedSnapshot())"
    "setTimeout(requestSnapshot,700);"
)
new_tail = (
    "renderSnapshot();scrollBottom();"
    "if(typeof state!=='undefined'&&state)scheduleSnapshot(700);"
    "document.addEventListener('visibilitychange',function(){"
    "if(!document.hidden&&typeof state!=='undefined'&&state)scheduleSnapshot(700);});"
)
main = replace_once(main, old_tail, new_tail, "always-start Snapshot cycle")

MAIN.write_text(main, encoding="utf-8")
print(
    f"HD Kai installed ({width or 'WebP'}x{height or 'WebP'}, high-quality alpha asset). "
    "Snapshot auto-refresh enabled every 30s after each completion/error, including same-turn idle."
)
