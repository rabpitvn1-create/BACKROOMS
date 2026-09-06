from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Snapshot battle visual polish {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Kai's pose belongs to the combat session, not the current turn. Once combat playback owns
# the Snapshot, every FOCUS keeps the battle overlay. finishVisuals/resetVisuals remain the
# only places that restore the normal overlay.
html = replace_once(
    html,
    "      const desired=actor==='kai'?'file:///android_asset/kai_snapshot_overlay_combat.png':'file:///android_asset/kai_snapshot_overlay.png';",
    "      const desired='file:///android_asset/kai_snapshot_overlay_combat.png';",
    "persistent Kai battle overlay",
)

# Nudge Kai slightly farther right in encounter staging while preserving the visible-alpha
# clipping guard. The idle/exploration pose is nudged separately in the final CSS below.
html = replace_once(
    html,
    "    const kaiContactX=kaiIdleContactX(rr);",
    "    const kaiContactX=Math.min(rr.width*.995,kaiIdleContactX(rr)+14);",
    "Kai encounter right nudge",
)
html = replace_once(
    html,
    "      const maxLeft=rr.width*.99-rect.width*visibleMaxX;",
    "      const maxLeft=rr.width*.995-rect.width*visibleMaxX;",
    "visible right-edge allowance",
)

# Pixel-art sprites should sit on a pixel-art contact shadow. Keep the existing authoritative
# alpha contact point, but enlarge the footprint modestly and render a tiny nearest-neighbour
# 32x8 RGBA sprite instead of the old blurred radial-gradient ellipse.
html = replace_once(
    html,
    "      const width=clamp(contactWidth*1.22,24,role==='entity'?118:94);",
    "      const width=clamp(contactWidth*1.38,28,role==='entity'?132:106);",
    "pixel shadow width",
)
html = replace_once(
    html,
    "      const height=clamp(width*.18,6,15);",
    "      const height=clamp(width*.20,7,17);",
    "pixel shadow height",
)

PIXEL_SHADOW_PNG = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAICAYAAACYhf2vAAAAPklEQVR42mNgIA5okInJAsgGhKLhJiIxuj6iHIVuKbqh60nEuByF1SH4LF9PISbKEQPugAGPgkGRCAckGwIA6rhSCW38rOcAAAAASUVORK5CYII="
style = f'''<!-- SNAPSHOT_BATTLE_VISUAL_POLISH_V5 -->
<style id="snapshot-battle-visual-polish-v5-style">
/* Remove the old 8px horizontal Snapshot gutter. This adds ~16px of useful width on the
   current phone layout, effectively the requested ~19px expansion without overflowing the game panel. */
#gameplayPage .snapshot{{margin:6px 0!important;width:auto!important;max-width:none!important}}

/* Exploration/finished combat: keep Kai on the right, just a little closer to the edge. */
#gameplayPage .snapshot:not(.entity-encounter-present) .snapshot-character{{right:-14px!important;left:auto!important}}

/* Low-resolution sprite shadow. No blur, no vector-like radial gradient. */
.snapshot .snapshot-contact-shadow-v3{{
  border-radius:0!important;
  background:url("data:image/png;base64,{PIXEL_SHADOW_PNG}") center/100% 100% no-repeat!important;
  image-rendering:pixelated!important;
  filter:none!important;
  transform:translate(-50%,-50%)!important;
  transform-origin:center!important;
}}
</style>'''
if "<!-- SNAPSHOT_BATTLE_VISUAL_POLISH_V5 -->" not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Snapshot battle visual polish expected exactly one </body>")
    html = html.replace("</body>", style + "\n</body>", 1)

# Fail closed if any old per-turn Kai pose switch remains after this finalizer.
for forbidden in [
    "const desired=actor==='kai'?'file:///android_asset/kai_snapshot_overlay_combat.png':'file:///android_asset/kai_snapshot_overlay.png'",
    "contactWidth*1.22",
    "border-radius:50%;background:radial-gradient(ellipse at center",
]:
    if forbidden in html:
        # The legacy shadow declaration is allowed to remain earlier in the stylesheet because the
        # final V5 rule overrides it. Only reject it if our V5 pixel rule itself is missing.
        if forbidden.startswith("border-radius") and "SNAPSHOT_BATTLE_VISUAL_POLISH_V5" in html:
            continue
        raise RuntimeError("Snapshot battle visual polish legacy contract survived: " + forbidden)

for required in [
    "const desired='file:///android_asset/kai_snapshot_overlay_combat.png'",
    "kaiIdleContactX(rr)+14",
    "contactWidth*1.38",
    "role==='entity'?132:106",
    "image-rendering:pixelated",
    "data:image/png;base64,",
    "#gameplayPage .snapshot{margin:6px 0!important",
    ".snapshot:not(.entity-encounter-present) .snapshot-character{right:-14px!important",
    "const kai=root.querySelector('.snapshot-character');if(kai)kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay.png');",
    "SNAPSHOT_BATTLE_VISUAL_POLISH_V5",
]:
    if required not in html:
        raise RuntimeError("Snapshot battle visual polish missing: " + required)

INDEX.write_text(html, encoding="utf-8")
print("Snapshot battle visual polish applied: persistent Kai battle overlay, wider edge-to-edge frame, right nudge and larger pixel contact shadows.")
