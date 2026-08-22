from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
java = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


if "import android.content.Intent;" not in java:
    java = replace_once(java, "import android.app.Activity;\n", "import android.app.Activity;\nimport android.content.Intent;\n", "Drive Intent import")

if "private DriveOnlineSaveManager driveSaveManager;" not in java:
    java = replace_once(
        java,
        "  private GameCoreFacade gameCore;\n",
        "  private GameCoreFacade gameCore;\n  private DriveOnlineSaveManager driveSaveManager;\n",
        "Drive manager field",
    )

if "driveSaveManager = new DriveOnlineSaveManager(this);" not in java:
    java = replace_once(
        java,
        "    super.onCreate(savedInstanceState);\n",
        "    super.onCreate(savedInstanceState);\n    driveSaveManager = new DriveOnlineSaveManager(this);\n",
        "Drive manager initialization",
    )

activity_result = r'''  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != DriveOnlineSaveManager.SIGN_IN_REQUEST || driveSaveManager == null) return;
    driveSaveManager.handleSignInResult(data, new DriveOnlineSaveManager.Callback() {
      @Override public void ok(String json) { emit("backroomDriveConnected", json); }
      @Override public void error(String message) { emit("backroomDriveError", message); }
    });
  }

'''
if "DriveOnlineSaveManager.SIGN_IN_REQUEST" not in java:
    java = replace_once(java, "  @Override protected void onDestroy() {\n", activity_result + "  @Override protected void onDestroy() {\n", "Drive sign-in result")

bridge_methods = r'''    @JavascriptInterface public void driveConnect() {
      runOnUiThread(() -> {
        if (driveSaveManager == null) {
          emit("backroomDriveError", "Google Drive manager chưa sẵn sàng.");
          return;
        }
        driveSaveManager.startSignIn();
      });
    }

    @JavascriptInterface public boolean driveSignedIn() {
      return driveSaveManager != null && driveSaveManager.isSignedIn();
    }

    @JavascriptInterface public void driveSave(String stateJson) {
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
if "@JavascriptInterface public void driveSave(String stateJson)" not in java:
    java = replace_once(java, "    @JavascriptInterface public void requestSnapshot(String stateJson) {\n", bridge_methods + "    @JavascriptInterface public void requestSnapshot(String stateJson) {\n", "Drive JS bridge")

old_buttons = '''<div class="card"><h2>Save / Load</h2><div class="actions"><button type="button" id="saveButton" onclick="save()">Lưu</button><button type="button" id="loadButton" onclick="load()">Tải</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button><button type="button" id="deleteSaveButton" class="wide danger" onclick="clearSave()">Xóa save trên máy</button></div></div>'''
new_buttons = '''<div class="card"><h2>Save / Load</h2><div class="actions"><button type="button" id="saveButton" onclick="save()">Lưu</button><button type="button" id="loadButton" onclick="load()">Tải</button><button type="button" id="driveConnectButton" class="wide" onclick="connectDrive()">Kết nối Google Drive</button><button type="button" id="driveSaveButton" class="wide" onclick="saveOnline()">Lưu Online</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button><button type="button" id="deleteSaveButton" class="wide danger" onclick="clearSave()">Xóa save trên máy</button></div></div>'''
if 'id="driveSaveButton"' not in html:
    html = replace_once(html, old_buttons, new_buttons, "Drive save controls")

online_js = r'''
function connectDrive(){
  if(!window.Android||typeof Android.driveConnect!=="function"){statusEl.textContent="Bản APK này chưa có Google Drive bridge.";return false}
  statusEl.textContent="Đang mở đăng nhập Google Drive…";
  Android.driveConnect();
  return true;
}
function saveOnline(){
  if(!window.Android||typeof Android.driveSave!=="function"){statusEl.textContent="Bản APK này chưa có Google Drive bridge.";return false}
  if(typeof Android.driveSignedIn==="function"&&!Android.driveSignedIn()){statusEl.textContent="Hãy kết nối Google Drive trước khi lưu online.";return false}
  statusEl.textContent="Đang lưu Turn "+(state.turn||1)+" lên Google Drive…";
  Android.driveSave(JSON.stringify(state));
  return true;
}
window.backroomDriveConnected=function(payload){
  try{const r=JSON.parse(payload);statusEl.textContent="Đã kết nối Google Drive"+(r.email?" — "+r.email:"")+"."}catch(e){statusEl.textContent="Đã kết nối Google Drive."}
};
window.backroomDriveSaved=function(payload){
  try{const r=JSON.parse(payload);statusEl.textContent="Đã lưu online Turn "+(r.turn||state.turn||1)+" vào SAVE GAME / "+(r.name||"backroom-online-save.json")+"."}catch(e){statusEl.textContent="Đã lưu save online lên Google Drive."}
};
window.backroomDriveError=function(message){statusEl.textContent="Google Drive: "+String(message||"Không thể thực hiện thao tác.")};
'''
if "function saveOnline()" not in html:
    marker = 'function clearSave(){\n'
    pos = html.find(marker)
    if pos < 0:
        raise RuntimeError("Drive online JS insertion point missing")
    html = html[:pos] + online_js + html[pos:]

required_java = [
    'DriveOnlineSaveManager driveSaveManager',
    'DriveOnlineSaveManager.SIGN_IN_REQUEST',
    '@JavascriptInterface public void driveConnect()',
    '@JavascriptInterface public boolean driveSignedIn()',
    '@JavascriptInterface public void driveSave(String stateJson)',
]
for marker in required_java:
    if marker not in java:
        raise RuntimeError(f"Drive online save Java marker missing: {marker}")

required_html = [
    'id="driveConnectButton"',
    'id="driveSaveButton"',
    'function connectDrive()',
    'function saveOnline()',
    'window.backroomDriveSaved=',
]
for marker in required_html:
    if marker not in html:
        raise RuntimeError(f"Drive online save HTML marker missing: {marker}")

MAIN.write_text(java, encoding="utf-8")
INDEX.write_text(html, encoding="utf-8")
print("Step 1 online save enabled: Google Sign-In + Drive API writes fixed backroom-online-save.json into SAVE GAME folder.")
