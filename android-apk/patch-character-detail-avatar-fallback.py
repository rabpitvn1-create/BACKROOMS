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

# This is the final UI transformation in both test and release patch chains.
runpy.run_path(str(ROOT / "patch-survival-hud-chat-ux.py"), run_name="__main__")

# Apply An Nhien only after all existing gameplay/UI hardening. The wrapper adapts the integration
# to the final conditional-audit prompt that exists at this point in the build chain.
runpy.run_path(str(ROOT / "patch-an-nhien-follower-final.py"), run_name="__main__")

# Keep planning/search prose on the normal GM path. LiteRT classifications are advisory and must
# not convert a harmless search into an authoritative pickup rejection.
runpy.run_path(str(ROOT / "patch-search-action-false-warning.py"), run_name="__main__")

# Developer slash command: /annhien1234 instantly adds An Nhien to Party without AI, dice,
# turn advancement or silently evicting an existing member.
runpy.run_path(str(ROOT / "patch-annhien-cheat-code.py"), run_name="__main__")

# Latest An Nhien equipment canon: replace the old Baby Tree slippers with pink Crocs.
runpy.run_path(str(ROOT / "patch-an-nhien-crocs.py"), run_name="__main__")

# Human-friendly character item labels: hide internal namespaces/IDs and render readable uppercase names.
runpy.run_path(str(ROOT / "patch-friendly-item-display.py"), run_name="__main__")

# Legacy Entity patches run first because later code depends on their established anchors.
# The final roaming patch below replaces their probabilities with the current 18 x 3% rule.
runpy.run_path(str(ROOT / "patch-jeff-encounter-2pct.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-entity-encounter-plus-8pct.py"), run_name="__main__")

# Android immersive fullscreen: hide status/navigation bars with transient swipe reveal.
runpy.run_path(str(ROOT / "patch-immersive-fullscreen.py"), run_name="__main__")

# Harden the committed local knowledge engine before Java integration.
runpy.run_path(str(ROOT / "patch-knowledge-engine-source.py"), run_name="__main__")

# Final runtime authority: replace legacy canon blobs with the indexed, budgeted local knowledge packet.
runpy.run_path(str(ROOT / "patch-knowledge-context-builder.py"), run_name="__main__")

# Deterministic OLD-vs-NEW context contract benchmark. Failure blocks the build.
runpy.run_path(str(ROOT / "benchmark-knowledge-context.py"), run_name="__main__")

# Final presentation split: GAME ends at THỰC HIỆN; all following status/panels live on page 2.
runpy.run_path(str(ROOT / "patch-two-page-ui.py"), run_name="__main__")

# Current Entity rule: all 18 entries roam every playable Level and each rolls independently
# at exactly 3% on physical gameplay turns. This intentionally supersedes the legacy Level pool
# and Jeff-specific probability above.
runpy.run_path(str(ROOT / "patch-entity-roaming-3pct.py"), run_name="__main__")

# Snapshot overlays always use the exact original PNG bytes committed from the Drive source folder.
# The patch verifies SHA-256, byte size and dimensions before wiring them into the UI.
runpy.run_path(str(ROOT / "patch-entity-overlays-local.py"), run_name="__main__")

# Kai uses a relaxed overlay normally and the aiming overlay whenever the current Entity roll
# contains one or more successful encounters. The patch also validates the new Kai avatar asset.
runpy.run_path(str(ROOT / "patch-kai-dual-overlay-final.py"), run_name="__main__")
