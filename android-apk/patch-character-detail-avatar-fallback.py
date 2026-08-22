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

# Jeff the Killer uses an independent 2% roaming encounter roll on eligible physical turns.
runpy.run_path(str(ROOT / "patch-jeff-encounter-2pct.py"), run_name="__main__")

# Raise the normal Entity encounter chance by +8 percentage points on every Level 0-6.
runpy.run_path(str(ROOT / "patch-entity-encounter-plus-8pct.py"), run_name="__main__")

# Android immersive fullscreen: hide status/navigation bars with transient swipe reveal.
runpy.run_path(str(ROOT / "patch-immersive-fullscreen.py"), run_name="__main__")

# Harden the committed local knowledge engine before Java integration.
runpy.run_path(str(ROOT / "patch-knowledge-engine-source.py"), run_name="__main__")

# Final runtime authority: replace legacy canon blobs with the indexed, budgeted local knowledge packet.
runpy.run_path(str(ROOT / "patch-knowledge-context-builder.py"), run_name="__main__")

# Deterministic OLD-vs-NEW context contract benchmark. Failure blocks the build.
runpy.run_path(str(ROOT / "benchmark-knowledge-context.py"), run_name="__main__")

# Startup hardening rewrites onCreate to the final WebView configuration. Run this before
# the Entity Drive patch so the loader binds to the final settings block instead of an
# intermediate onCreate shape produced earlier in the patch chain.
runpy.run_path(str(ROOT / "patch-startup-survival.py"), run_name="__main__")

# Drive-backed Entity artwork is the last Android transformation: resolve exact canon Entity IDs
# through the remote manifest, then overlay the matching image on the left side of Snapshot while
# Kai remains on the right. Running last also makes the WebView cache anchor deterministic.
runpy.run_path(str(ROOT / "patch-entity-drive-overlay.py"), run_name="__main__")
