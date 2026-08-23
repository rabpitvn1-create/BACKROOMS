from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
DRIVE = ROOT / "app/src/main/java/com/rabpit/backroom/DriveOnlineSaveManager.java"

html = INDEX.read_text(encoding="utf-8")
drive = DRIVE.read_text(encoding="utf-8")

old_duplicate_block = '''    String existingId = findFileId(token, fileName);
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
      .toString();'''
new_overwrite_block = '''    String existingId = findFileId(token, fileName);
    boolean overwritten = existingId != null && !existingId.isEmpty();
    String fileId;
    if (overwritten) {
      updateFile(token, existingId, envelope.toString());
      fileId = existingId;
    } else {
      fileId = createFile(token, fileName, envelope.toString());
    }
    return new JSONObject()
      .put("ok", true)
      .put("fileId", fileId)
      .put("name", fileName)
      .put("displayName", displayName)
      .put("overwritten", overwritten)
      .put("turn", state.optInt("turn", 1))
      .put("folderId", SAVE_FOLDER_ID)
      .toString();'''
if old_duplicate_block in drive:
    drive = drive.replace(old_duplicate_block, new_overwrite_block, 1)
elif new_overwrite_block not in drive:
    raise RuntimeError("Step 4 saveNamed overwrite anchor missing")

old_callback = 'window.backroomDriveSaved=function(payload){\n  try{const r=JSON.parse(payload);statusEl.textContent="Đã lưu online “"+(r.displayName||r.name||currentSaveName())+"” ở Turn "+(r.turn||state.turn||1)+"."}catch(e){statusEl.textContent="Đã lưu save online lên Google Drive."}\n};'
new_callback = 'window.backroomDriveSaved=function(payload){\n  try{const r=JSON.parse(payload);const n=(r.displayName||r.name||currentSaveName());statusEl.textContent=(r.overwritten?"Đã overwrite save “":"Đã tạo save “")+n+"” ở Turn "+(r.turn||state.turn||1)+" trên Google Drive."}catch(e){statusEl.textContent="Đã lưu save online lên Google Drive."}\n};'
if old_callback in html:
    html = html.replace(old_callback, new_callback, 1)
elif 'r.overwritten?"Đã overwrite save “":"Đã tạo save “"' not in html:
    raise RuntimeError("Step 4 overwrite status callback anchor missing")

for forbidden in [
    'Overwrite save cũ sẽ được bật ở bước 4',
    'throw new Exception("Đã có save tên',
]:
    if forbidden in drive:
        raise RuntimeError(f"Step 4 duplicate-save rejection still present: {forbidden}")

for required in [
    'boolean overwritten = existingId != null && !existingId.isEmpty();',
    'updateFile(token, existingId, envelope.toString());',
    '.put("overwritten", overwritten)',
]:
    if required not in drive:
        raise RuntimeError(f"Step 4 Drive marker missing: {required}")

for required in [
    'r.overwritten?"Đã overwrite save “":"Đã tạo save “"',
    'trên Google Drive.',
]:
    if required not in html:
        raise RuntimeError(f"Step 4 HTML marker missing: {required}")

INDEX.write_text(html, encoding="utf-8")
DRIVE.write_text(drive, encoding="utf-8")
print("Step 4 complete: saving with an existing name overwrites that exact Drive file; new names create new files.")

# Final gameplay input layer: keep the three primary actions downstream of every prior UI/save rewrite.
runpy.run_path(str(ROOT / "patch-three-action-runtime-ui.py"), run_name="__main__")

# MadGod must run after the final ActionRuntime/UI rewrite so its cheat bypass, permanent equipment
# locks and Snapshot overlay routing bind to the actual release sources rather than an intermediate file.
runpy.run_path(str(ROOT / "patch-madgod-equipment.py"), run_name="__main__")
