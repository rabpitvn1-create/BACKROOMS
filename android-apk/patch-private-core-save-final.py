from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
text = INDEX.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    'const SAVE_KEY="backroom-apk-state";\nconst SNAPSHOT_KEY="backroom-apk-snapshot";',
    'const SAVE_KEY="backroom-apk-state";\nconst CORE_SAVE_KEY="backroom-apk-core-state";\nconst SNAPSHOT_KEY="backroom-apk-snapshot";',
    "private Core save key",
)

clear_helper = '''function clearAuthoritativeCore(){
  try{if(window.Android&&typeof Android.clearCoreState==="function")Android.clearCoreState();return true}
  catch(e){statusEl.textContent="Không thể đồng bộ Game State Core: "+(e&&e.message?e.message:"bridge error");return false}
}
'''
extra_helpers = clear_helper + '''function exportAuthoritativeCore(){
  try{
    if(!window.Android||typeof Android.exportCoreState!=="function")return "";
    const raw=String(Android.exportCoreState()||"");
    if(raw)JSON.parse(raw);
    return raw;
  }catch(e){statusEl.textContent="Không thể đọc Game State Core để lưu: "+(e&&e.message?e.message:"bridge error");return ""}
}
function restoreAuthoritativeCore(){
  let raw="";
  try{raw=String(localStorage.getItem(CORE_SAVE_KEY)||"")}catch(ignore){}
  if(!raw)return false;
  try{
    if(!window.Android||typeof Android.restoreCoreState!=="function")return false;
    return Android.restoreCoreState(raw)===true;
  }catch(e){return false}
}
'''
if "function exportAuthoritativeCore()" not in text:
    replace_once(clear_helper, extra_helpers, "private Core save helpers")

save_old = '''    const checked=JSON.parse(verify);
    if(Number(checked.turn)!==Number(state.turn))throw new Error("Turn sau khi ghi không khớp");
    statusEl.textContent="Đã lưu Turn "+(state.turn||1)+" trên máy.";
'''
save_new = '''    const checked=JSON.parse(verify);
    if(Number(checked.turn)!==Number(state.turn))throw new Error("bộ đếm nội bộ sau khi ghi không khớp");
    const core=exportAuthoritativeCore();
    if(core)localStorage.setItem(CORE_SAVE_KEY,core);
    statusEl.textContent="Đã lưu save trên máy.";
'''
replace_once(save_old, save_new, "save private Core state")

load_old = '''  state=loaded;
  clearAuthoritativeCore();
  render();
  statusEl.textContent="Đã tải save Turn "+(state.turn||1)+" từ máy.";
'''
load_new = '''  state=loaded;
  if(!restoreAuthoritativeCore())clearAuthoritativeCore();
  render();
  statusEl.textContent="Đã tải save từ máy.";
'''
replace_once(load_old, load_new, "restore private Core state")

replace_once(
    'try{localStorage.removeItem(SNAPSHOT_KEY)}catch(ignore){}',
    'try{localStorage.removeItem(SNAPSHOT_KEY);localStorage.removeItem(CORE_SAVE_KEY)}catch(ignore){}',
    "New Game private Core cleanup",
)
text = text.replace(
    'if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1 với Character Core mới.";',
    'if(save())statusEl.textContent="NEW GAME đã tạo và lưu với Character Core mới.";',
)
text = text.replace(
    'if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1. Game State Core và Snapshot cũ đã được xóa.";',
    'if(save())statusEl.textContent="NEW GAME đã tạo và lưu. Game State Core và Snapshot cũ đã được xóa.";',
)

replace_once(
    'localStorage.removeItem(SAVE_KEY);localStorage.removeItem(SNAPSHOT_KEY)',
    'localStorage.removeItem(SAVE_KEY);localStorage.removeItem(CORE_SAVE_KEY);localStorage.removeItem(SNAPSHOT_KEY)',
    "Delete Save private Core cleanup",
)
text = text.replace(
    'statusEl.textContent="Đã xóa save, Game State Core và snapshot trên máy. Trạng thái Turn 1 hiện chỉ ở bộ nhớ tạm.";',
    'statusEl.textContent="Đã xóa save, Game State Core và snapshot trên máy. Trạng thái hiện tại chỉ ở bộ nhớ tạm.";',
)

required = (
    'const CORE_SAVE_KEY="backroom-apk-core-state"',
    'function exportAuthoritativeCore()',
    'function restoreAuthoritativeCore()',
    'Android.exportCoreState()',
    'Android.restoreCoreState(raw)',
    'localStorage.setItem(CORE_SAVE_KEY,core)',
    'localStorage.removeItem(CORE_SAVE_KEY)',
    'Đã lưu save trên máy.',
    'Đã tải save từ máy.',
)
for marker in required:
    if marker not in text:
        raise RuntimeError("private Core save finalizer missing: " + marker)
for forbidden in (
    'Đã lưu Turn ',
    'Đã tải save Turn ',
    'NEW GAME đã tạo và lưu ở Turn 1',
    'Trạng thái Turn 1 hiện chỉ ở bộ nhớ tạm',
):
    if forbidden in text:
        raise RuntimeError("player-facing turn text survived: " + forbidden)

INDEX.write_text(text, encoding="utf-8")
print("Private authoritative Core save finalized after the legacy patch chain; hidden Level blueprints survive Load without entering Gemini-visible state.")
