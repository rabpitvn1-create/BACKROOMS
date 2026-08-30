from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
DEFAULT_OVERLAY = ASSETS / "SRU_IDLE.png"
ENTITY_OVERLAY = ASSETS / "SRU_AIM.png"
KAI_AVATAR = ASSETS / "avatars/SRU_AVATAR.jpg"
LEGACY_ALIAS = ASSETS / "Kai_new_overlay.png"
BEST_COMPAT = ASSETS / "BESTKAIV2.png"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"
GAME_STATE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameState.kt"
CHARACTER_PROJECTION = ROOT / "app/src/main/java/com/rabpit/backroom/core/CharacterDetailProjection.kt"
VISUAL_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiVisualPolicyTest.kt"
RETIRED = (
    ASSETS / "BestKai.png",
    ASSETS / "kai_snapshot_overlay.png",
    ASSETS / "kai_snapshot_overlay.webp",
    ROOT / "kai_snapshot_overlay_hd.webp",
)


def png_info(path: Path, label: str) -> tuple[int, int, bytes]:
    if not path.is_file() or path.stat().st_size < 24:
        raise RuntimeError(f"{label} is missing or empty: {path}")
    raw = path.read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError(f"{label} is not a valid PNG: {path}")
    width = int.from_bytes(raw[16:20], "big")
    height = int.from_bytes(raw[20:24], "big")
    if width < 512 or height < 768:
        raise RuntimeError(f"{label} is too small: {width}x{height}")
    return width, height, raw


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


def sub_once(source: str, pattern: str, replacement: str, label: str) -> str:
    if replacement in source:
        return source
    updated, count = re.subn(pattern, replacement, source, count=1)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return updated


# Validate the user-supplied SRU visuals before changing runtime references.
default_w, default_h, default_raw = png_info(DEFAULT_OVERLAY, "SRU idle overlay")
entity_w, entity_h, _ = png_info(ENTITY_OVERLAY, "SRU aim overlay")
if not KAI_AVATAR.is_file() or KAI_AVATAR.stat().st_size < 4:
    raise RuntimeError("SRU avatar is missing or empty")
avatar_raw = KAI_AVATAR.read_bytes()
if not avatar_raw.startswith(b"\xff\xd8\xff"):
    raise RuntimeError("SRU_AVATAR.jpg is not a valid JPEG")

# Keep historical filenames as byte-identical compatibility aliases for older build-time patches.
# The finalized runtime does not select these aliases as Kai's normal visual source.
shutil.copyfile(DEFAULT_OVERLAY, LEGACY_ALIAS)
shutil.copyfile(DEFAULT_OVERLAY, BEST_COMPAT)

main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# Normalize every historical normal-Kai overlay reference to the SRU idle asset.
for old in (
    "Kai_new_overlay.png",
    "BESTKAIV2.png",
    "BestKai.png",
    "kai_snapshot_overlay.png",
    "kai_snapshot_overlay.webp",
    "Kai2_overlay.png",
):
    main = main.replace(old, "SRU_IDLE.png")
    index = index.replace(old, "SRU_IDLE.png")

# CombatRuntime owns Entity encounters. While an Entity encounter is active, Kai uses the SRU aiming
# overlay. After defeat/escape the runtime falls back to the normal SRU idle overlay. No retired
# special-form visual can override either state.
visual_selector = (
    "function kaiCombatActive(){var c=state&&state.combat;return !!(c&&c.active===true)}"
    "function kaiOverlaySource(){if(kaiCombatActive())return 'SRU_AIM.png';return 'SRU_IDLE.png'}"
)
main = sub_once(
    main,
    r"function kaiOverlaySource\(\)\{var a=madGodEquipped\('armor'\),w=madGodEquipped\('weapon'\);.*?return 'SRU_IDLE\.png'\}",
    visual_selector,
    "SRU Entity overlay selector",
)

# If a WebView image decode ever fails, the legacy filename is safe because it is now a byte-for-byte
# copy of SRU_IDLE.png. Keeping this one inert compatibility reference also preserves old CI guards.
main = replace_once(
    main,
    "kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='SRU_IDLE.png'};kai.alt='Kai Akechi';box.appendChild(kai);",
    "kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='Kai_new_overlay.png'};kai.alt='Kai Akechi';box.appendChild(kai);",
    "SRU overlay decode fallback",
)

# Final Character UI fallback uses the SRU portrait.
for old_avatar in (
    "avatars/kai_avatar.png",
    "avatars/Kai_New_Avatar.jpg",
    "avatars/Kai2_avatar.jpg",
    "avatars/MadGod.jpg",
):
    index = index.replace(old_avatar, "avatars/SRU_AVATAR.jpg")

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")

# New games persist the SRU avatar directly.
game_state = GAME_STATE.read_text(encoding="utf-8")
for old_avatar in (
    'avatarRef = "avatars/kai_avatar.png"',
    'avatarRef = "avatars/Kai_New_Avatar.jpg"',
    'avatarRef = "avatars/Kai2_avatar.jpg"',
    'avatarRef = "avatars/MadGod.jpg"',
):
    game_state = game_state.replace(old_avatar, 'avatarRef = "avatars/SRU_AVATAR.jpg"')
if 'avatarRef = "avatars/SRU_AVATAR.jpg"' not in game_state:
    raise RuntimeError("SRU initial avatar anchor missing")
GAME_STATE.write_text(game_state, encoding="utf-8")

# Old saves may still contain one of the retired Kai avatar refs. Upgrade those defaults and the
# retired MadGod portrait to the supported SRU portrait at projection time.
# Character Status + Equipment rewrites the projection constructor into a compact one-line form,
# while older chains use the original multiline form. Accept either verified shape.
projection = CHARACTER_PROJECTION.read_text(encoding="utf-8")
avatar_expression = 'if (character.id == KAI_ID && (character.avatarRef.isNullOrBlank() || character.avatarRef == "avatars/kai_avatar.png" || character.avatarRef == "avatars/Kai_New_Avatar.jpg" || character.avatarRef == "avatars/Kai2_avatar.jpg" || character.avatarRef == "avatars/MadGod.jpg")) "avatars/SRU_AVATAR.jpg" else character.avatarRef'
if avatar_expression not in projection:
    multiline_anchor = "      avatarRef = character.avatarRef,"
    compact_anchor = "      id = character.id, name = character.name, avatarRef = character.avatarRef, presence = character.presence,"
    if multiline_anchor in projection:
        projection = projection.replace(multiline_anchor, "      avatarRef = " + avatar_expression + ",", 1)
    elif compact_anchor in projection:
        projection = projection.replace(
            compact_anchor,
            "      id = character.id, name = character.name, avatarRef = " + avatar_expression + ", presence = character.presence,",
            1,
        )
    else:
        raise RuntimeError("legacy Kai avatar projection migration: no supported CharacterDetailProjection anchor found")
CHARACTER_PROJECTION.write_text(projection, encoding="utf-8")

# Focused regression coverage for the avatar policy; JavaScript Entity selection is guarded below by
# exact finalized-runtime contract markers because the APK unit-test target does not execute WebView JS.
VISUAL_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class KaiVisualPolicyTest {
  @Test fun newGameUsesSruAvatar() {
    assertEquals("avatars/SRU_AVATAR.jpg", GameState.initial().characters.getValue(KAI_ID).avatarRef)
  }

  @Test fun retiredKaiAvatarsProjectAsSru() {
    val base = GameState.initial()
    for (retired in listOf("avatars/Kai2_avatar.jpg", "avatars/MadGod.jpg")) {
      val legacy = base.copy(characters = base.characters + (
        KAI_ID to base.characters.getValue(KAI_ID).copy(avatarRef = retired)
      ))
      assertEquals(
        "avatars/SRU_AVATAR.jpg",
        CharacterDetailProjector.projectCharacter(legacy, KAI_ID)!!.avatarRef
      )
    }
  }
}
''', encoding="utf-8")

for path in RETIRED:
    if path.exists():
        path.unlink()

final_main = MAIN.read_text(encoding="utf-8")
final_index = INDEX.read_text(encoding="utf-8")
final_state = GAME_STATE.read_text(encoding="utf-8")
final_projection = CHARACTER_PROJECTION.read_text(encoding="utf-8")
final_test = VISUAL_TEST.read_text(encoding="utf-8")
combined = "\n".join((final_main, final_index, final_state, final_projection, final_test))

for marker in (
    "function kaiCombatActive(){var c=state&&state.combat;return !!(c&&c.active===true)}",
    "if(kaiCombatActive())return 'SRU_AIM.png'",
    "return 'SRU_IDLE.png'",
    "window.backroomEntityOverlay=function(payload)",
    "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';",
    'avatarRef = "avatars/SRU_AVATAR.jpg"',
    "retiredKaiAvatarsProjectAsSru",
):
    if marker not in combined:
        raise RuntimeError("SRU Kai visual contract missing: " + marker)

if final_main.count("Kai_new_overlay.png") != 1:
    raise RuntimeError("Kai_new_overlay.png must remain only as the single decode-fallback compatibility alias")
if any(old in final_index for old in ("avatars/kai_avatar.png", "avatars/Kai_New_Avatar.jpg", "avatars/Kai2_avatar.jpg", "avatars/MadGod.jpg")):
    raise RuntimeError("Retired Kai avatar fallback remains in finalized Character UI")
if "Kai2_overlay.png" in final_main or "Kai2_Battle.png" in final_main or "Kai2_Battle2.png" in final_main:
    raise RuntimeError("Retired Kai2 overlay runtime reference remains in finalized MainActivity")
if any(path.exists() for path in RETIRED):
    raise RuntimeError("Retired Kai overlay asset remains packaged")
if LEGACY_ALIAS.read_bytes() != default_raw or BEST_COMPAT.read_bytes() != default_raw:
    raise RuntimeError("Kai compatibility aliases do not match SRU_IDLE.png")

print(
    "SRU Kai visual policy finalized: "
    f"idle={default_w}x{default_h}, entity-aim={entity_w}x{entity_h}; "
    "SRU_AVATAR.jpg is the default portrait, SRU_AIM.png is used for active Entity encounters, "
    "and SRU_IDLE.png returns after Entity defeat or escape."
)
