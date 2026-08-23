from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old = """    detailAvatar.src=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png');
    detailAvatar.alt=member.name||member.id||'Nhân vật';"""
new = """    const detailAvatarSrc=member.avatar||member.avatarRef||(member.id==='kai'?'avatars/kai_avatar.png':'');
    detailAvatar.hidden=!detailAvatarSrc;
    if(detailAvatarSrc)detailAvatar.src=detailAvatarSrc;else detailAvatar.removeAttribute('src');
    detailAvatar.alt=member.name||member.id||'Nhân vật';"""

if new not in html:
    if old not in html:
        raise RuntimeError("Character detail avatar fallback anchor not found")
    html = html.replace(old, new, 1)

if "member.id==='kai'?'avatars/kai_avatar.png':'avatars/kai_avatar.png'" in html:
    raise RuntimeError("Non-Kai character still falls back to Kai avatar")

INDEX.write_text(html, encoding="utf-8")
print("Character detail avatar fallback hardened: non-Kai members without avatars use no portrait.")

runpy.run_path(str(ROOT / "patch-survival-hud-chat-ux.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-an-nhien-follower-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-search-action-false-warning.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-annhien-cheat-code.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-an-nhien-crocs.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-friendly-item-display.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-jeff-encounter-2pct.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-entity-encounter-plus-8pct.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-immersive-fullscreen.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-knowledge-engine-source.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-knowledge-context-builder.py"), run_name="__main__")
runpy.run_path(str(ROOT / "benchmark-knowledge-context.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-startup-survival.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-local-entity-overlay.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-jane-killer.py"), run_name="__main__")

# Typed gameplay UI must exist before MadGod, because MadGod patches the final ActionRuntime bridge.
runpy.run_path(str(ROOT / "patch-three-action-runtime-ui.py"), run_name="__main__")

# MadGod is a release feature, not dead source code. Apply it after the three-action bridge so /madgod
# intercepts beginAction/processRule correctly and the set UI/snapshot transformations target final markup.
runpy.run_path(str(ROOT / "patch-madgod-equipment.py"), run_name="__main__")

# MadGod is a forced full-set override. This hotfix removes the synthetic-slot mismatch and makes
# one Equip replace Kai's current weapon + armor atomically while preserving unrelated slots.
runpy.run_path(str(ROOT / "patch-madgod-overwrite-hotfix.py"), run_name="__main__")

# Entity overlay hotfix runs last so both MadGod and three-action transformations cannot displace it.
runpy.run_path(str(ROOT / "patch-entity-overlay-runtime-hotfix.py"), run_name="__main__")

final_html = INDEX.read_text(encoding="utf-8")
final_java = (ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8")
final_facade = (ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").read_text(encoding="utf-8")
final_madgod = (ROOT / "app/src/main/java/com/rabpit/backroom/core/MadGodCanon.kt").read_text(encoding="utf-8")
final_engines = (ROOT / "app/src/main/java/com/rabpit/backroom/core/Engines.kt").read_text(encoding="utf-8")
final_tests = (ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt").read_text(encoding="utf-8")

for marker in (
    'id="searchActionButton"', 'id="submit"', 'id="exploreActionButton"',
    'submitMacroAction("SEARCH","Tìm kiếm")', 'submitMacroAction("EXPLORE","Khám phá")',
    'STEP2_THREE_ACTIONS', 'madGodSetEquipped()', "return ['MadGod Set','Omnivault Ring']",
):
    if marker not in final_html:
        raise RuntimeError(f"1.1.57 final UI contract missing: {marker}")
if '<button id="submit">THỰC HIỆN</button>' in final_html:
    raise RuntimeError("1.1.57 still contains legacy single Execute button")

for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    '.beginAction(stateJson, actionKind, action)', 'SEARCH HARD LOCK:', 'EXPLORE HARD LOCK:',
    'file:///android_asset/entity/', 'window.backroomEntityOverlay=function(payload)',
    'private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)',
    'forceEntityEncounterFlag(candidateState, rolls);', 'Kai_MadGod_snapshot_overlay.png',
):
    if marker not in final_java:
        raise RuntimeError(f"1.1.57 final Android runtime contract missing: {marker}")

for marker in (
    'MadGodCanon.cheat(action)', 'applyMadGodCheat(legacy,state)',
    'fun beginAction(legacyStateJson: String, kindRaw: String, action: String)',
    'private fun commitActionRuntime(', 'ActionRuntime.markSearchCoverage(',
    'output.put("equipment",MadGodCanon.legacy(state))',
):
    if marker not in final_facade:
        raise RuntimeError(f"1.1.57 final core contract missing: {marker}")

for marker in ('const val MADGOD_SET_ID = "madgod:set"', 'const val CHEAT_CODE = "/madgod"', 'const val SET_NAME = "MadGod Set"'):
    if marker not in final_madgod:
        raise RuntimeError(f"1.1.57 MadGod canon missing: {marker}")

for marker in (
    'val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)',
    'if (command.actorId != KAI_ID) return invalid(state,"madgod_equipment_slot_mismatch")',
):
    if marker not in final_engines:
        raise RuntimeError(f"1.1.57 MadGod overwrite engine missing: {marker}")

for marker in (
    'equipOverwritesKaisExistingWeaponAndArmorEvenWithoutSyntheticSetSlot',
    'typedTrangBiMadGodSetResolvesAndOverwritesExistingGear',
):
    if marker not in final_tests:
        raise RuntimeError(f"1.1.57 MadGod overwrite regression test missing: {marker}")

print("Final 1.1.57 contract verified: MadGod overwrites Kai weapon+armor, local Entity overlay, /madgod, and three-action UI.")
