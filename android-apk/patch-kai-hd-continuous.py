from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
HD_SOURCE = ROOT / "kai_snapshot_overlay_hd.webp"
OVERLAY = ROOT / "app/src/main/assets/kai_snapshot_overlay.webp"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# Keep the user's HD Kai artwork packaged in the APK.
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
index = INDEX.read_text(encoding="utf-8")

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

# Keep the last meaningful generated snapshot visible across ordinary turns.
main = replace_once(
    main,
    "return r&&Number(r.turn)===Number(state&&state.turn)&&r.dataUri?r:null;",
    "return r&&r.dataUri?r:null;",
    "persistent meaningful Snapshot cache",
)

old_request = (
    "function requestSnapshot(){if(!window.Android||typeof Android.requestSnapshot!=='function')"
    "{var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android snapshot bridge.';"
    "return;}var s=document.getElementById('status');if(s)s.textContent='Gemini đang tạo snapshot…';"
    "Android.requestSnapshot(JSON.stringify(state));}"
)
new_request = (
    "var snapshotBusy=false;"
    "function requestSnapshot(){if(snapshotBusy)return;"
    "if(!window.Android||typeof Android.requestSnapshot!=='function'){var s=document.getElementById('status');"
    "if(s)s.textContent='Không tìm thấy Android snapshot bridge.';return;}"
    "snapshotBusy=true;var s=document.getElementById('status');if(s)s.textContent='AI đang tạo snapshot…';"
    "Android.requestSnapshot(JSON.stringify(state));}"
)
main = replace_once(main, old_request, new_request, "one-shot Snapshot request")

main = replace_once(
    main,
    "window.backroomSnapshot=function(payload){try{",
    "window.backroomSnapshot=function(payload){snapshotBusy=false;try{",
    "Snapshot success unlock",
)
main = replace_once(
    main,
    "window.backroomSnapshotError=function(payload){try{",
    "window.backroomSnapshotError=function(payload){snapshotBusy=false;try{",
    "Snapshot error unlock",
)

# Prior provider patch changes the completion status before this patch runs.
old_turn_tail = (
    "if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+"
    "'. Đang tạo snapshot bằng Gemini…';renderSnapshot();scrollBottom();requestSnapshot();};"
)
new_turn_tail = (
    "var ev=state&&state._snapshotEvent;var allowed={LEVEL_CHANGE:1,SPECIAL_REGION:1,ENTITY_CONFIRMED:1,PERSON_ENCOUNTER:1,MAJOR_VISUAL_EVENT:1};"
    "var should=!!(ev&&ev.shouldGenerate===true&&allowed[String(ev.kind||'').toUpperCase()]);"
    "if(should){if(s)s.textContent='Turn '+state.turn+' có sự kiện hình ảnh đặc biệt. Đang tạo snapshot…';}"
    "else{if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+'. Snapshot cũ được giữ nguyên.';}"
    "renderSnapshot();scrollBottom();if(should)requestSnapshot();};"
)
main = replace_once(main, old_turn_tail, new_turn_tail, "event-only Snapshot after turn")

# Never start a background Snapshot loop on launch, resume or idle.
main = replace_once(
    main,
    "renderSnapshot();scrollBottom();if(typeof state!=='undefined'&&state&&!cachedSnapshot())setTimeout(requestSnapshot,700);",
    "renderSnapshot();scrollBottom();",
    "disable automatic idle Snapshot",
)

prompt_anchor = (
    '            "Viết tiếng Việt tự nhiên, đầy đủ ý. Không trả lời bằng câu rỗng. Không thay đổi dữ kiện chưa có căn cứ. Người chơi chỉ điều khiển Kai Akechi. " +\n'
)
prompt_rules = prompt_anchor + (
    '            "Snapshot chỉ được yêu cầu khi CHÍNH LƯỢT NÀY tạo ra một mốc hình ảnh mới rõ ràng: chuyển Level; Kai bước vào một vùng đặc biệt khác biệt đáng kể; Entity lạ được xác nhận thực sự xuất hiện; gặp survivor/nhân vật; hoặc một sự kiện lớn/hiếm làm thay đổi rõ cảnh hiện tại. " +\n'
    '            "Đi lại, quan sát, tìm kiếm thông thường, hội thoại không đổi cảnh, hành động lặp lại, lượt yên, thay đổi nhỏ, nghi ngờ nguy hiểm hoặc chỉ tăng số turn thì snapshotEvent.shouldGenerate phải false. Khi phân vân, false. " +\n'
    '            "Nếu shouldGenerate=true, snapshotEvent.kind bắt buộc là một trong LEVEL_CHANGE, SPECIAL_REGION, ENTITY_CONFIRMED, PERSON_ENCOUNTER, MAJOR_VISUAL_EVENT. " +\n'
    '            "Trường level chỉ đổi khi Kai thực sự chuyển Level; nếu không đổi thì giữ nguyên level hiện tại hoặc bỏ trường level. " +\n'
)
main = replace_once(main, prompt_anchor, prompt_rules, "Snapshot event rules")

main = replace_once(
    main,
    '\\"location\\":\\"vị trí sau lượt\\",\\"player\\":{},',
    '\\"location\\":\\"vị trí sau lượt\\",\\"level\\":{\\"number\\":0,\\"name\\":\\"The Lobby\\"},\\"player\\":{},',
    "Level JSON contract",
)
main = replace_once(
    main,
    '\\"inventory\\":[],\\"flags\\":{}}',
    '\\"inventory\\":[],\\"flags\\":{},\\"snapshotEvent\\":{\\"shouldGenerate\\":false,\\"kind\\":\\"\\",\\"reason\\":\\"\\"}}',
    "Snapshot JSON contract",
)

player_line = '          if (generated.optJSONObject("player") != null) state.put("player", generated.optJSONObject("player"));\n'
main = replace_once(
    main,
    player_line,
    '          if (generated.optJSONObject("level") != null) state.put("level", generated.optJSONObject("level"));\n' + player_line,
    "store Level",
)
flags_line = '          if (generated.optJSONObject("flags") != null) {\n'
main = replace_once(
    main,
    flags_line,
    '          JSONObject snapshotEvent = generated.optJSONObject("snapshotEvent");\n'
    '          if (snapshotEvent == null) snapshotEvent = new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", "");\n'
    '          state.put("_snapshotEvent", snapshotEvent);\n'
    + flags_line,
    "store Snapshot event",
)

# APK-native UI wording/header. This remains part of the standalone build.
index = replace_once(
    index,
    'placeholder="Kai làm gì trong Turn hiện tại?"',
    'placeholder="Kai sẽ làm gì tiếp theo?"',
    "action prompt wording",
)

# Support both the original minified APK source and the readable Prologue rewrite.
# Normalize the opening of `initial` back to the compact form expected by the
# later Drive-canon patch, so the rest of the proven build chain remains intact.
compact_initial = 'const initial={title:"MAIN_BACKROOMS — Kai Akechi",turn:1,'
pretty_initial = 'const initial={\n  title:"MAIN_BACKROOMS — Kai Akechi",\n  turn:1,'
normalized_initial = 'const initial={title:"Level 0 – The Lobby",level:{number:0,name:"The Lobby"},turn:1,'
if compact_initial in index:
    index = replace_once(index, compact_initial, normalized_initial, "initial Level header")
elif pretty_initial in index:
    index = replace_once(index, pretty_initial, normalized_initial, "initial Level header (readable Prologue layout)")
else:
    raise RuntimeError("initial Level header: neither compact nor readable Prologue layout was found")

index = replace_once(
    index,
    'function render(){titleEl.textContent=state.title;',
    'function levelHeader(s){if(s&&s.level&&s.level.number!=null&&s.level.name)return "Level "+s.level.number+" – "+s.level.name;const where=String(s&&s.location||"")+" "+String(s&&s.title||"");const m=where.match(/Level\\s*([^\\s\\/—–-]+)\\s*(?:\\/|—|–|-)\\s*([^—–\\n]+?)(?:\\s+[—–]\\s+|$)/i);if(m)return "Level "+m[1]+" – "+m[2].trim();return s&&s.title||"Backrooms"}function render(){titleEl.textContent=levelHeader(state);',
    "dynamic Level header",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(
    f"HD Kai installed ({width or 'WebP'}x{height or 'WebP'}). "
    "Continuous Snapshot generation disabled; only explicit meaningful visual events trigger an automatic image. "
    "APK header and action prompt updated."
)
