from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
SAVE_REPOSITORY = ROOT / "app/src/main/java/com/rabpit/backroom/core/SaveRepository.kt"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"

html = INDEX.read_text(encoding="utf-8")


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


step1_controls = '''<div class="card"><h2>Save / Load</h2><div class="actions"><button type="button" id="saveButton" onclick="save()">Lưu</button><button type="button" id="loadButton" onclick="load()">Tải</button><button type="button" id="driveConnectButton" class="wide" onclick="connectDrive()">Kết nối Google Drive</button><button type="button" id="driveSaveButton" class="wide" onclick="saveOnline()">Lưu Online</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button><button type="button" id="deleteSaveButton" class="wide danger" onclick="clearSave()">Xóa save trên máy</button></div></div>'''
online_only_controls = '''<div class="card"><h2>Online Save</h2><div class="actions"><button type="button" id="driveConnectButton" class="wide" onclick="connectDrive()">Kết nối Google Drive</button><button type="button" id="driveSaveButton" class="wide" onclick="saveOnline()">Lưu Online</button><button type="button" id="newGameButton" class="wide" onclick="resetGame()">Bắt đầu lại từ đầu</button></div></div>'''
if step1_controls in html:
    html = html.replace(step1_controls, online_only_controls, 1)
elif online_only_controls not in html:
    raise RuntimeError("Step 2 online-only controls anchor missing")

safe_state = '''let state=(()=>{try{const raw=localStorage.getItem("backroom-apk-state");if(!raw)return JSON.parse(JSON.stringify(initial));const parsed=JSON.parse(raw);return parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed:JSON.parse(JSON.stringify(initial));}catch(e){try{localStorage.removeItem("backroom-apk-state");}catch(ignore){}return JSON.parse(JSON.stringify(initial));}})();'''
fresh_state = 'let state=JSON.parse(JSON.stringify(initial));'
if safe_state in html:
    html = html.replace(safe_state, fresh_state, 1)
elif 'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;' in html:
    html = html.replace('let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;', fresh_state, 1)
elif fresh_state not in html:
    raise RuntimeError("Step 2 state initialization anchor missing")

# Remove the old one-time prologue migration write. It must never recreate a local game save.
html = html.replace('    localStorage.setItem("backroom-apk-state",JSON.stringify(state));\n', '', 1)
html = html.replace('localStorage.setItem("backroom-apk-state",JSON.stringify(state));', '', 1)

# The old helper functions may still exist because step 1 intentionally preserved compatibility.
# Replace their behavior so all game-state persistence routes to Drive only.
html = replace_function(html, "savedState", 'function savedState(){return null}')
html = replace_function(html, "save", 'function save(){return saveOnline()}')
html = replace_function(html, "load", 'function load(){statusEl.textContent="Local save đã bị tắt hoàn toàn. Chỉ cho phép load online ở bước 5.";return false}')
html = replace_function(html, "clearSave", 'function clearSave(){statusEl.textContent="Không còn local save để xóa. Xóa save online sẽ được xử lý ở bước 7.";return false}')
html = html.replace('const SAVE_KEY="backroom-apk-state";\n', '', 1)

# Erase any stale legacy state once at startup so it cannot silently resurrect on a later build.
cleanup = 'try{localStorage.removeItem("backroom-apk-state")}catch(ignore){}\n'
if cleanup not in html:
    anchor = 'let busy=false;\n'
    if anchor not in html:
        raise RuntimeError("Step 2 startup cleanup anchor missing")
    html = html.replace(anchor, anchor + cleanup, 1)

html = html.replace(
    'statusEl.textContent="Turn "+state.turn+" đã lưu trên máy."',
    'statusEl.textContent="Turn "+state.turn+" đang đồng bộ lên Google Drive."',
)

for forbidden in [
    'id="saveButton"',
    'id="loadButton"',
    'id="deleteSaveButton"',
    'localStorage.getItem("backroom-apk-state")',
    'localStorage.setItem("backroom-apk-state"',
    'localStorage.getItem(SAVE_KEY)',
    'localStorage.setItem(SAVE_KEY',
]:
    if forbidden in html:
        raise RuntimeError(f"Local game save still present after step 2: {forbidden}")

for required in [
    '<h2>Online Save</h2>',
    'id="driveConnectButton"',
    'id="driveSaveButton"',
    'function save(){return saveOnline()}',
    fresh_state,
    cleanup.strip(),
]:
    if required not in html:
        raise RuntimeError(f"Step 2 HTML marker missing: {required}")

INDEX.write_text(html, encoding="utf-8")

repo = SAVE_REPOSITORY.read_text(encoding="utf-8")
if "class SharedPreferencesSaveRepository" in repo:
    class_start = repo.index("class SharedPreferencesSaveRepository")
    repo = repo[:class_start].rstrip() + '''\n\nclass InMemorySaveRepository : SaveRepository {
  @Volatile private var state: GameState? = null

  @Synchronized override fun save(state: GameState) {
    this.state = state
  }

  @Synchronized override fun load(): GameState = state ?: GameState.initial()

  override fun exists(): Boolean = state != null

  @Synchronized override fun clear() {
    state = null
  }
}
'''
    repo = repo.replace("import android.content.Context\n\n", "", 1)
elif "class InMemorySaveRepository" not in repo:
    raise RuntimeError("Step 2 SaveRepository anchor missing")

if "SharedPreferences" in repo or "getSharedPreferences" in repo:
    raise RuntimeError("Persistent local Game State Core repository still present")
SAVE_REPOSITORY.write_text(repo, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
old_repo = "SharedPreferencesSaveRepository(context.applicationContext)"
if old_repo in facade:
    facade = facade.replace(old_repo, "InMemorySaveRepository()", 1)
elif "InMemorySaveRepository()" not in facade:
    raise RuntimeError("Step 2 GameCoreFacade repository anchor missing")
if "SharedPreferencesSaveRepository(" in facade:
    raise RuntimeError("GameCoreFacade still selects a persistent local repository")
FACADE.write_text(facade, encoding="utf-8")

print("Step 2 complete: persistent local game-state save removed from WebView and Game State Core; online Drive save is the only save path.")
