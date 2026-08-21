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
