from pathlib import Path

INDEX = Path(__file__).resolve().parent / "app/src/main/assets/index.html"
text = INDEX.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


old_buttons = '''<div class="card"><h2>Save / Load</h2><div class="actions"><button onclick="save()">Lưu</button><button onclick="load()">Tải</button><button class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button><button class="wide danger" onclick="clearSave()">Xóa save trên máy</button></div></div>'''
new_buttons = '''<div class="card"><h2>Save / Load</h2><div class="actions"><button type="button" id="saveButton" onclick="save()">Lưu</button><button type="button" id="loadButton" onclick="load()">Tải</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button><button type="button" id="deleteSaveButton" class="wide danger" onclick="clearSave()">Xóa save trên máy</button></div></div>'''
replace_once(old_buttons, new_buttons, "save control buttons")

# Keep the migration code that earlier patches append after this initializer, but
# make the initializer itself safe against corrupted localStorage and avoid ever
# aliasing mutable state directly to the `initial` object.
old_state = 'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;'
new_state = '''let state=(()=>{try{const raw=localStorage.getItem("backroom-apk-state");if(!raw)return JSON.parse(JSON.stringify(initial));const parsed=JSON.parse(raw);return parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed:JSON.parse(JSON.stringify(initial));}catch(e){try{localStorage.removeItem("backroom-apk-state");}catch(ignore){}return JSON.parse(JSON.stringify(initial));}})();'''
replace_once(old_state, new_state, "safe initial save load")

# render() must only render. Previously it overwrote status messages, making Load,
# New Game and Delete Save appear to do nothing even when state had changed.
replace_once(';statusEl.textContent="Save được lưu riêng trên thiết bị này."}', '}', "render status side effect")

old_functions = '''function save(){localStorage.setItem("backroom-apk-state",JSON.stringify(state));statusEl.textContent="Đã lưu save trên máy."}
function load(){state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;render()}
function resetGame(){if(confirm("Bắt đầu lại từ Turn 1?")){state=JSON.parse(JSON.stringify(initial));save();render()}}
function clearSave(){if(confirm("Xóa save trên máy?")){localStorage.removeItem("backroom-apk-state");state=JSON.parse(JSON.stringify(initial));render()}}'''
new_functions = '''const SAVE_KEY="backroom-apk-state";
const SNAPSHOT_KEY="backroom-apk-snapshot";
let destructiveAction="";
let destructiveUntil=0;

function freshInitial(){return JSON.parse(JSON.stringify(initial))}
function savedState(){
  let raw=null;
  try{raw=localStorage.getItem(SAVE_KEY)}catch(e){statusEl.textContent="Không thể đọc bộ nhớ save: "+(e&&e.message?e.message:"storage error");return null}
  if(!raw)return null;
  try{
    const parsed=JSON.parse(raw);
    if(!parsed||typeof parsed!=="object"||Array.isArray(parsed))throw new Error("state không phải object");
    return parsed;
  }catch(e){statusEl.textContent="Save trên máy bị lỗi và không được nạp. Bạn có thể xóa save hoặc tạo New Game.";return null}
}
function save(){
  try{
    const payload=JSON.stringify(state);
    localStorage.setItem(SAVE_KEY,payload);
    const verify=localStorage.getItem(SAVE_KEY);
    if(!verify)throw new Error("không đọc lại được save vừa ghi");
    const checked=JSON.parse(verify);
    if(Number(checked.turn)!==Number(state.turn))throw new Error("Turn sau khi ghi không khớp");
    statusEl.textContent="Đã lưu Turn "+(state.turn||1)+" trên máy.";
    return true;
  }catch(e){statusEl.textContent="Lưu thất bại: "+(e&&e.message?e.message:"storage error");return false}
}
function load(){
  const loaded=savedState();
  if(!loaded){if(!statusEl.textContent||!statusEl.textContent.includes("bị lỗi"))statusEl.textContent="Không có save trên máy để tải.";return false}
  state=loaded;
  render();
  statusEl.textContent="Đã tải save Turn "+(state.turn||1)+" từ máy.";
  return true;
}
function armDestructive(kind,label,perform){
  const now=Date.now();
  if(destructiveAction===kind&&now<destructiveUntil){destructiveAction="";destructiveUntil=0;perform();return}
  destructiveAction=kind;destructiveUntil=now+5000;
  statusEl.textContent='Nhấn "'+label+'" lần nữa trong 5 giây để xác nhận.';
}
function resetGame(){
  armDestructive("new-game","Bắt đầu lại từ đầu",()=>{
    try{localStorage.removeItem(SNAPSHOT_KEY)}catch(ignore){}
    state=freshInitial();
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1. Snapshot cũ đã được xóa.";
  });
}
function clearSave(){
  armDestructive("delete-save","Xóa save trên máy",()=>{
    try{localStorage.removeItem(SAVE_KEY);localStorage.removeItem(SNAPSHOT_KEY)}catch(e){statusEl.textContent="Xóa save thất bại: "+(e&&e.message?e.message:"storage error");return}
    state=freshInitial();
    render();
    statusEl.textContent="Đã xóa save và snapshot trên máy. Trạng thái Turn 1 hiện chỉ ở bộ nhớ tạm; bấm Lưu hoặc chơi tiếp để tạo save mới.";
  });
}'''
replace_once(old_functions, new_functions, "save/load/new-game/delete behavior")

for required in [
    'id="saveButton"',
    'id="loadButton"',
    'id="newGameButton"',
    'id="deleteSaveButton"',
    'const SAVE_KEY="backroom-apk-state"',
    'const SNAPSHOT_KEY="backroom-apk-snapshot"',
    'function savedState()',
    'function armDestructive(',
    'không đọc lại được save vừa ghi',
    'localStorage.removeItem(SNAPSHOT_KEY)',
]:
    if required not in text:
        raise RuntimeError(f"save controls hardening missing marker: {required}")

if 'confirm(' in text:
    raise RuntimeError("APK save controls must not depend on WebView confirm() dialogs")

INDEX.write_text(text, encoding="utf-8")
print("APK save/load/delete/new-game controls hardened without WebView confirm dialogs.")
