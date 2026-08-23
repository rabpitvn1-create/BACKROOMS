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

# Jeff the Killer: independent 8% roaming encounter roll on eligible physical turns.
runpy.run_path(str(ROOT / "patch-jeff-encounter-2pct.py"), run_name="__main__")

# Raise the normal Entity encounter chance by +8 percentage points on every Level 0-6.
runpy.run_path(str(ROOT / "patch-entity-encounter-plus-8pct.py"), run_name="__main__")

runpy.run_path(str(ROOT / "patch-immersive-fullscreen.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-knowledge-engine-source.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-knowledge-context-builder.py"), run_name="__main__")
runpy.run_path(str(ROOT / "benchmark-knowledge-context.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-startup-survival.py"), run_name="__main__")

# Entity artwork is packaged locally in APK assets/entity. No Google Drive or remote manifest.
runpy.run_path(str(ROOT / "patch-local-entity-overlay.py"), run_name="__main__")

# Jane must run after the final Entity overlay and Context Builder rewrites so her independent
# roll, validated state root, persistent respawn contract and ENT-R02 visual state survive all earlier transformations.
runpy.run_path(str(ROOT / "patch-jane-killer.py"), run_name="__main__")

# Final gameplay/UI layer. It must run last, because earlier compatibility patches can rewrite the
# WebView and core bridge back to their legacy single-action form.
runpy.run_path(str(ROOT / "patch-three-action-runtime-ui.py"), run_name="__main__")

# Hard release contract. A new version number is useless if the APK still contains the old UI.
final_html = INDEX.read_text(encoding="utf-8")
final_java = (ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8")
final_facade = (ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").read_text(encoding="utf-8")
for marker in (
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    'class="primary-action execute-action"',
    'submitMacroAction("SEARCH","Tìm kiếm")',
    'submitMacroAction("EXPLORE","Khám phá")',
    'Android.submitAction(JSON.stringify(state),"EXECUTE",a)',
    'STEP2_THREE_ACTIONS',
):
    if marker not in final_html:
        raise RuntimeError(f"1.1.55 final UI contract missing: {marker}")
if '<button id="submit">THỰC HIỆN</button>' in final_html:
    raise RuntimeError("1.1.55 still contains legacy single Execute button")
for marker in (
    '@JavascriptInterface public void submitAction(String stateJson, String actionKind, String action)',
    '.beginAction(stateJson, actionKind, action)',
    'SEARCH HARD LOCK:',
    'EXPLORE HARD LOCK:',
):
    if marker not in final_java:
        raise RuntimeError(f"1.1.55 final action bridge missing: {marker}")
for marker in (
    'fun beginAction(legacyStateJson: String, kindRaw: String, action: String)',
    'private fun commitActionRuntime(',
    'ActionRuntime.markSearchCoverage(',
    'ActionRuntime.complete(finalState, active.sessionId)',
):
    if marker not in final_facade:
        raise RuntimeError(f"1.1.55 final ActionRuntime integration missing: {marker}")
print("Final 1.1.55 three-action release contract verified.")
