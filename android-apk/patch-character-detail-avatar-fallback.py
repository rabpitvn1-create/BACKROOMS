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

# Ordered final runtime transformation chain. Inventory V2 owns all item semantics. Capacity
# hardening verifies Core first, then aligns presentation/writer text without creating item state.
# The foundation-canon finalizer runs after gameplay/inventory finalizers and only normalizes
# canon knowledge plus retrieval routing.
PATCH_CHAIN = [
    "patch-survival-hud-chat-ux.py",
    "patch-an-nhien-follower-final.py",
    "patch-search-action-false-warning.py",
    "patch-annhien-cheat-code.py",
    "patch-an-nhien-crocs.py",
    "patch-friendly-item-display.py",
    "patch-jeff-encounter-2pct.py",
    "patch-entity-encounter-plus-8pct.py",
    "patch-immersive-fullscreen.py",
    "patch-knowledge-engine-source.py",
    "patch-knowledge-context-builder.py",
    "benchmark-knowledge-context.py",
    "patch-two-page-ui.py",
    "patch-entity-roaming-3pct.py",
    "patch-entity-overlays-local.py",
    "patch-kai-dual-overlay-final.py",
    "patch-party-entity-overlays-final.py",
    "patch-auto-turn-combat-final.py",
    "patch-auto-turn-combat-compat.py",
    "patch-kai-skills-final.py",
    "patch-kai-skills-progression-compat.py",
    "patch-combat-start-anchor-compat.py",
    "patch-newgame-canon-compat.py",
    "patch-combat-start-pacing-newgame.py",
    "patch-combat-visual-effects.py",
    "patch-combat-visual-effects-loop-guard.py",
    "patch-combat-ux-regression-final.py",
    "patch-an-nhien-rare-spawn-final.py",
    "patch-lucia-proc-skills-final.py",
    "patch-async-member-entity-final.py",
    "patch-syvial-iris-skills-final.py",
    "patch-syvial-iris-skills-cycle-2-final.py",
    "patch-syvial-iris-skills-cycle-2-test-compat.py",
    "patch-inventory-v2-final.py",
    "patch-inventory-v2-compile-fix.py",
    "patch-inventory-capacity-final.py",
    "patch-inventory-capacity-ui-final.py",
    "patch-inventory-capacity-prompt-final.py",
    "patch-inventory-capacity-test-compat.py",
    "patch-sru-backrooms-async-canon.py",
]

for patch_name in PATCH_CHAIN:
    runpy.run_path(str(ROOT / patch_name), run_name="__main__")
