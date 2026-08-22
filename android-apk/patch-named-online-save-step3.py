from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
DRIVE = ROOT / "app/src/main/java/com/rabpit/backroom/DriveOnlineSaveManager.java"

java = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")
drive = DRIVE.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def replace_function(source: str, name: str, replacement: str) -> str:
    marker = f"function {name}("
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"function {name} not found")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"function {name} body not found")
    depth = 0
    quote = None
    escaped = False
    i = brace
    while i < len(source):
        ch = source[i]
        if quote is not None:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
        else:
            if ch in ('"', "'", '`'):
                quote = ch
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return source[:start] + replacement + source[i + 1:]
        i += 1
    raise RuntimeError(f"function {name} closing brace not found")


old_controls = '''<div class="card"><h2>Online Save</h2><div class="actions"><button type="button" id="driveConnectButton" class="wide" onclick="connectDrive()">Kết nối Google Drive</button><button type="button" id="driveSaveButton" class="wide" onclick="saveOnline()">Lưu Online</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button></div></div>'''
new_controls = '''<div class="card"><h2>Online Save</h2><div class="actions"><label class="save-name-field wide" for="saveNameInput"><span>Tên save</span><input id="saveNameInput" type="text" maxlength="80" autocomplete="off" placeholder="Ví dụ: Level 2 - Kai"></label><button type="button" id="driveConnectButton" class="wide" onclick="connectDrive()">Kết nối Google Drive</button><button type="button" id="driveSaveButton" class="wide" onclick="saveOnline()">Lưu Online</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button></div></div>'''
if old_controls in html:
    html = html.replace(old_controls, new_controls, 1)
elif 'id="saveNameInput"' not in html:
    raise RuntimeError("Step 3 named save controls anchor missing")

if 'textarea,button,input{font:inherit}' not in html:
    if 'textarea,button{font:inherit}' not in html:
        raise RuntimeError("Step 3 input font style anchor missing")
    html = html.replace('textarea,button{font:inherit}', 'textarea,button,input{font:inherit}', 1)

save_name_css = '.save-name-field{display:grid;gap:6px;color:#8f9aa4;font-size:12px}.save-name-field input{width:100%;background:#090c0f;color:#eef1f3;border:1px solid #30373e;padding:11px}.save-name-field input:focus{outline:1px solid #6e7a84;border-color:#6e7a84}'
if save_name_css not in html:
    anchor = '.danger{background:#241616;color:#e6b1b1;border-color:#663b3b}'
    if anchor not in html:
        raise RuntimeError("Step 3 CSS anchor missing")
    html = html.replace(anchor, anchor + save_name_css, 1)

named_js = '''function currentSaveName(){
  const input=document.getElementById("saveNameInput");
  return input?String(input.value||"").trim():"";
}
function saveOnline(){
  if(!window.Android||typeof Android.driveSave!=="function"){statusEl.textContent="Bản APK này chưa có Google Drive bridge.";return false}
  if(typeof Android.driveSignedIn==="function"&&!Android.driveSignedIn()){statusEl.textContent="Hãy kết nối Google Drive trước khi lưu online.";return false}
  const saveName=currentSaveName();
  if(!saveName){statusEl.textContent="Hãy nhập tên save trước khi lưu.";const input=document.getElementById("saveNameInput");if(input)input.focus();return false}
  statusEl.textContent="Đang lưu “"+saveName+"” ở Turn "+(state.turn||1)+" lên Google Drive…";
  Android.driveSave(JSON.stringify(state),saveName);
  return true;
}'''
html = replace_function(html, "saveOnline", named_js)

old_saved_callback = 'window.backroomDriveSaved=function(payload){\n  try{const r=JSON.parse(payload);statusEl.textContent="Đã lưu online Turn "+(r.turn||state.turn||1)+" vào SAVE GAME / "+(r.name||"backroom-online-save.json")+"."}catch(e){statusEl.textContent="Đã lưu save online lên Google Drive."}\n};'
new_saved_callback = 'window.backroomDriveSaved=function(payload){\n  try{const r=JSON.parse(payload);statusEl.textContent="Đã lưu online “"+(r.displayName||r.name||currentSaveName())+"” ở Turn "+(r.turn||state.turn||1)+"."}catch(e){statusEl.textContent="Đã lưu save online lên Google Drive."}\n};'
if old_saved_callback in html:
    html = html.replace(old_saved_callback, new_saved_callback, 1)
elif 'r.displayName||r.name||currentSaveName()' not in html:
    raise RuntimeError("Step 3 saved callback anchor missing")

old_bridge = '''    @JavascriptInterface public void driveSave(String stateJson) {
      io.execute(() -> {
        try {
          if (driveSaveManager == null) throw new Exception("Google Drive manager chưa sẵn sàng.");
          emit("backroomDriveSaved", driveSaveManager.saveDefault(stateJson));
        } catch (Exception e) {
          emit("backroomDriveError", e.getMessage() == null ? "Không thể lưu save lên Google Drive." : e.getMessage());
        }
      });
    }
'''
new_bridge = '''    @JavascriptInterface public void driveSave(String stateJson, String saveName) {
      io.execute(() -> {
        try {
          if (driveSaveManager == null) throw new Exception("Google Drive manager chưa sẵn sàng.");
          emit("backroomDriveSaved", driveSaveManager.saveNamed(stateJson, saveName));
        } catch (Exception e) {
          emit("backroomDriveError", e.getMessage() == null ? "Không thể lưu save lên Google Drive." : e.getMessage());
        }
      });
    }
'''
if old_bridge in java:
    java = java.replace(old_bridge, new_bridge, 1)
elif '@JavascriptInterface public void driveSave(String stateJson, String saveName)' not in java:
    raise RuntimeError("Step 3 Drive bridge anchor missing")

named_method = '''  String saveNamed(String stateJson, String requestedName) throws Exception {
    requireSignedIn();
    String displayName = normalizeSaveName(requestedName);
    String fileName = displayName + ".json";
    JSONObject state = new JSONObject(stateJson);
    JSONObject envelope = new JSONObject()
      .put("format", "backroom-save-v1")
      .put("name", displayName)
      .put("fileName", fileName)
      .put("savedAt", System.currentTimeMillis())
      .put("turn", state.optInt("turn", 1))
      .put("state", state);
    String token = accessToken();
    String existingId = findFileId(token, fileName);
    if (existingId != null && !existingId.isEmpty()) {
      throw new Exception("Đã có save tên ‘" + displayName + "’. Overwrite save cũ sẽ được bật ở bước 4.");
    }
    String fileId = createFile(token, fileName, envelope.toString());
    return new JSONObject()
      .put("ok", true)
      .put("fileId", fileId)
      .put("name", fileName)
      .put("displayName", displayName)
      .put("turn", state.optInt("turn", 1))
      .put("folderId", SAVE_FOLDER_ID)
      .toString();
  }

  private String normalizeSaveName(String requestedName) throws Exception {
    String name = requestedName == null ? "" : requestedName.trim();
    if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) name = name.substring(0, name.length() - 5).trim();
    name = name.replace((char)34, '-').replaceAll("[\\\\/:*?<>|\\p{Cntrl}]", "-").replaceAll("\\s+", " ").trim();
    while (name.endsWith(".")) name = name.substring(0, name.length() - 1).trim();
    if (name.isEmpty()) throw new Exception("Tên save không được để trống.");
    if (name.length() > 80) throw new Exception("Tên save tối đa 80 ký tự.");
    return name;
  }

'''
if "String saveNamed(String stateJson, String requestedName)" not in drive:
    anchor = "  String saveDefault(String stateJson) throws Exception {\n"
    if anchor not in drive:
        raise RuntimeError("Step 3 Drive manager insertion anchor missing")
    drive = drive.replace(anchor, named_method + anchor, 1)

for required in [
    'id="saveNameInput"',
    'function currentSaveName()',
    'Android.driveSave(JSON.stringify(state),saveName)',
    'r.displayName||r.name||currentSaveName()',
]:
    if required not in html:
        raise RuntimeError(f"Step 3 HTML marker missing: {required}")
for required in [
    '@JavascriptInterface public void driveSave(String stateJson, String saveName)',
    'driveSaveManager.saveNamed(stateJson, saveName)',
]:
    if required not in java:
        raise RuntimeError(f"Step 3 Java marker missing: {required}")
for required in [
    'String saveNamed(String stateJson, String requestedName)',
    'String displayName = normalizeSaveName(requestedName)',
    'String fileName = displayName + ".json"',
    'name.replace((char)34,',
    'Overwrite save cũ sẽ được bật ở bước 4',
]:
    if required not in drive:
        raise RuntimeError(f"Step 3 Drive marker missing: {required}")

MAIN.write_text(java, encoding="utf-8")
INDEX.write_text(html, encoding="utf-8")
DRIVE.write_text(drive, encoding="utf-8")
print("Step 3 complete: users can name online saves; duplicate names are rejected until overwrite is implemented in step 4.")
