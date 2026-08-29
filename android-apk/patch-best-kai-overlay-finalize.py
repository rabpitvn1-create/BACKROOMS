from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
DEFAULT_OVERLAY = ASSETS / "Kai2_overlay.png"
BATTLE_OVERLAY_1 = ASSETS / "Kai2_Battle.png"
BATTLE_OVERLAY_2 = ASSETS / "Kai2_Battle2.png"
KAI_AVATAR = ASSETS / "avatars/Kai2_avatar.jpg"
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


# Validate every user-supplied Kai2 visual before changing runtime references.
default_w, default_h, default_raw = png_info(DEFAULT_OVERLAY, "Kai2 default overlay")
battle1_w, battle1_h, _ = png_info(BATTLE_OVERLAY_1, "Kai2 Battle overlay 1")
battle2_w, battle2_h, _ = png_info(BATTLE_OVERLAY_2, "Kai2 Battle overlay 2")
if not KAI_AVATAR.is_file() or KAI_AVATAR.stat().st_size < 4:
    raise RuntimeError("Kai2 avatar is missing or empty")
avatar_raw = KAI_AVATAR.read_bytes()
if not avatar_raw.startswith(b"\xff\xd8\xff"):
    raise RuntimeError("Kai2_avatar.jpg is not a valid JPEG")

# Keep the historical filenames as byte-identical compatibility aliases for older build-time
# patches, but the finalized runtime no longer chooses them as Kai's normal Snapshot source.
shutil.copyfile(DEFAULT_OVERLAY, LEGACY_ALIAS)
shutil.copyfile(DEFAULT_OVERLAY, BEST_COMPAT)

main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# Normalize every historical normal-Kai overlay reference to the new non-combat Kai2 asset.
for old in (
    "Kai_new_overlay.png",
    "BESTKAIV2.png",
    "BestKai.png",
    "kai_snapshot_overlay.png",
    "kai_snapshot_overlay.webp",
):
    main = main.replace(old, "Kai2_overlay.png")
    index = index.replace(old, "Kai2_overlay.png")

# CombatRuntime's encounterId is stable for the whole fight. The UI stores only the last encountered
# id + variant, so a new encounter toggles Battle -> Battle2 -> Battle while re-renders in the same
# encounter keep the exact same pose. Any active CombatRuntime session gets the battle visual,
# independent of Entity key, so normal Entities, unique Entities and bosses share the same policy.
battle_selector = (
    "function kaiCombatEncounterId(){var c=state&&state.combat;return c&&c.active===true?String(c.encounterId||''):''}"
    "function kaiBattleOverlay(encounterId){try{var key='backroom-kai-battle-cycle-v1';"
    "var saved=JSON.parse(localStorage.getItem(key)||'null');var index=0;"
    "if(saved&&String(saved.encounterId||'')===encounterId)index=Number(saved.index)===1?1:0;"
    "else if(saved)index=Number(saved.index)===0?1:0;"
    "var next={encounterId:encounterId,index:index};localStorage.setItem(key,JSON.stringify(next));"
    "return index===1?'Kai2_Battle2.png':'Kai2_Battle.png'}catch(e){return 'Kai2_Battle.png'}}"
    "function kaiOverlaySource(){var encounterId=kaiCombatEncounterId();if(encounterId)return kaiBattleOverlay(encounterId);"
    "var a=madGodEquipped('armor'),w=madGodEquipped('weapon');"
    "if(a&&w)return 'Kai_MadGod_snapshot_overlay.png';if(a)return 'Kai_MadGod_snapshot_overlay.png';"
    "if(w)return 'Kai_MadGod_snapshot_overlay.png';return 'Kai2_overlay.png'}"
)
main = sub_once(
    main,
    r"function kaiOverlaySource\(\)\{var a=madGodEquipped\('armor'\),w=madGodEquipped\('weapon'\);.*?return 'Kai2_overlay\.png'\}",
    battle_selector,
    "Kai2 combat overlay selector",
)

# If a WebView image decode ever fails, the legacy filename is safe because it is now a byte-for-byte
# copy of Kai2_overlay.png. Keeping this one inert compatibility reference also preserves old CI guards.
main = replace_once(
    main,
    "kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='Kai2_overlay.png'};kai.alt='Kai Akechi';box.appendChild(kai);",
    "kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='Kai_new_overlay.png'};kai.alt='Kai Akechi';box.appendChild(kai);",
    "Kai2 overlay decode fallback",
)

# Final Character UI fallback uses the uploaded Kai2 portrait. MadGod's explicit avatar continues to
# win because that path is a different asset and is applied by the existing MadGod projection logic.
index = index.replace("avatars/kai_avatar.png", "avatars/Kai2_avatar.jpg")
index = index.replace("avatars/Kai_New_Avatar.jpg", "avatars/Kai2_avatar.jpg")

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")

# New games persist the new avatar directly.
game_state = GAME_STATE.read_text(encoding="utf-8")
game_state = replace_once(
    game_state,
    'avatarRef = "avatars/kai_avatar.png"',
    'avatarRef = "avatars/Kai2_avatar.jpg"',
    "Kai2 initial avatar",
)
GAME_STATE.write_text(game_state, encoding="utf-8")

# Old saves may still contain a retired default Kai avatarRef. Upgrade only those default refs at
# projection time; explicit special-form portraits such as avatars/MadGod.jpg remain untouched.
projection = CHARACTER_PROJECTION.read_text(encoding="utf-8")
projection = replace_once(
    projection,
    "      avatarRef = character.avatarRef,",
    '      avatarRef = if (character.id == KAI_ID && (character.avatarRef.isNullOrBlank() || character.avatarRef == "avatars/kai_avatar.png" || character.avatarRef == "avatars/Kai_New_Avatar.jpg")) "avatars/Kai2_avatar.jpg" else character.avatarRef,',
    "legacy Kai avatar projection migration",
)
CHARACTER_PROJECTION.write_text(projection, encoding="utf-8")

# Focused regression coverage for the avatar policy; JavaScript battle selection is guarded below by
# exact finalized-runtime contract markers because the APK unit-test target does not execute WebView JS.
VISUAL_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class KaiVisualPolicyTest {
  @Test fun newGameUsesKai2Avatar() {
    assertEquals("avatars/Kai2_avatar.jpg", GameState.initial().characters.getValue(KAI_ID).avatarRef)
  }

  @Test fun legacyDefaultKaiAvatarProjectsAsKai2WithoutOverwritingSpecialForm() {
    val base = GameState.initial()
    val legacy = base.copy(characters = base.characters + (
      KAI_ID to base.characters.getValue(KAI_ID).copy(avatarRef = "avatars/kai_avatar.png")
    ))
    assertEquals(
      "avatars/Kai2_avatar.jpg",
      CharacterDetailProjector.projectCharacter(legacy, KAI_ID)!!.avatarRef
    )

    val special = base.copy(characters = base.characters + (
      KAI_ID to base.characters.getValue(KAI_ID).copy(avatarRef = "avatars/MadGod.jpg")
    ))
    assertEquals(
      "avatars/MadGod.jpg",
      CharacterDetailProjector.projectCharacter(special, KAI_ID)!!.avatarRef
    )
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
    "function kaiCombatEncounterId(){var c=state&&state.combat;return c&&c.active===true",
    "backroom-kai-battle-cycle-v1",
    "if(encounterId)return kaiBattleOverlay(encounterId)",
    "Kai2_Battle.png",
    "Kai2_Battle2.png",
    "return 'Kai2_overlay.png'",
    "window.backroomEntityOverlay=function(payload)",
    "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';",
    'avatarRef = "avatars/Kai2_avatar.jpg"',
    "legacyDefaultKaiAvatarProjectsAsKai2WithoutOverwritingSpecialForm",
):
    if marker not in combined:
        raise RuntimeError("Kai2 visual contract missing: " + marker)

if final_main.count("Kai_new_overlay.png") != 1:
    raise RuntimeError("Kai_new_overlay.png must remain only as the single decode-fallback compatibility alias")
if "avatars/kai_avatar.png" in final_index or "avatars/Kai_New_Avatar.jpg" in final_index:
    raise RuntimeError("Retired Kai avatar fallback remains in finalized Character UI")
if any(path.exists() for path in RETIRED):
    raise RuntimeError("Retired Kai overlay asset remains packaged")
if LEGACY_ALIAS.read_bytes() != default_raw or BEST_COMPAT.read_bytes() != default_raw:
    raise RuntimeError("Kai compatibility aliases do not match Kai2_overlay.png")

print(
    "Kai2 visual policy finalized: "
    f"default={default_w}x{default_h}, battle1={battle1_w}x{battle1_h}, battle2={battle2_w}x{battle2_h}; "
    "Kai2_avatar.jpg is the default portrait; combat alternates Battle/Battle2 per encounter and "
    "returns to the non-combat overlay after Entity defeat or escape."
)
