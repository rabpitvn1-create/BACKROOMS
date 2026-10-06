"""Apply the first Cao Minh replacement after the legacy runtime patch chain."""
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app/src"
MAIN = APP / "main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = APP / "main/assets"
EXPLORER = "CAO_MINH_OVERLAY_EXPLORER_IDLE.png"
COMBAT = "CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png"

for name in (EXPLORER, COMBAT):
    raw = (ASSETS / name).read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n" or len(raw) < 33:
        raise RuntimeError(f"Invalid Cao Minh PNG: {name}")
    width, height = struct.unpack(">II", raw[16:24])
    if width < 512 or height < 683 or raw[25] not in (4, 6):
        raise RuntimeError(f"Cao Minh overlay needs full-size alpha artwork: {name}")

# Preserve equipment/canon keys and code symbols until their own replacement step.
# Only the complete player ID token changes; asset paths such as kai_avatar.png
# and existing equipment IDs remain valid.
for path in APP.rglob("*"):
    if not path.is_file() or path.suffix not in (".java", ".kt", ".html", ".txt", ".json"):
        continue
    old = path.read_text(encoding="utf-8")
    new = re.sub(r"\bKai(?: Akechi)?\b", "Cao Minh", old)
    new = new.replace('"kai"', '"cao_minh"').replace("'kai'", "'cao_minh'")
    if new != old:
        path.write_text(new, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
overlay = (
    "function kaiOverlaySource(){return state&&state.combat&&state.combat.active===true?"
    f"'{COMBAT}':'{EXPLORER}'}}"
)
main, count = re.subn(r"function kaiOverlaySource\(\)\{[^}]*\}", lambda _: overlay, main)
if count != 1:
    raise RuntimeError(f"Expected one final player overlay selector, found {count}")
main = main.replace("this.src='kai_snapshot_overlay.png'", f"this.src='{EXPLORER}'")
MAIN.write_text(main, encoding="utf-8")

state = (APP / "main/java/com/rabpit/backroom/core/GameState.kt").read_text(encoding="utf-8")
html = (ASSETS / "index.html").read_text(encoding="utf-8")
if 'const val KAI_ID = "cao_minh"' not in state:
    raise RuntimeError("Authoritative player ID was not replaced")
if 'data-character="cao_minh"' not in html or "kai.alt='Cao Minh'" not in main:
    raise RuntimeError("Party/overlay identity was not replaced")
if "Kai Akechi" in main + html + state:
    raise RuntimeError("Legacy player display name remains")

(APP / "test/java/com/rabpit/backroom/core/CaoMinhIdentityTest.kt").write_text('''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CaoMinhIdentityTest {
  @Test fun freshPlayerOwnsPartyInventoryAndEquipmentUnderNewId() {
    val state = GameState.initial()
    assertEquals("cao_minh", KAI_ID)
    assertEquals("Cao Minh", state.characters.getValue("cao_minh").name)
    assertEquals("cao_minh", state.party.leaderId)
    assertTrue(state.party.memberIds.contains("cao_minh"))
    assertTrue(state.inventories.containsKey("cao_minh"))
    assertTrue(state.equipment.containsKey("cao_minh"))
    assertFalse(state.characters.containsKey("kai"))
  }

  @Test fun newSaveRoundTripKeepsPlayerIdentityAndOwnership() {
    val decoded = GameStateCodec.decode(GameStateCodec.encode(GameState.initial()))
    assertEquals("Cao Minh", decoded.characters.getValue("cao_minh").name)
    assertEquals("cao_minh", decoded.party.leaderId)
    assertTrue(decoded.inventories.containsKey("cao_minh"))
    assertTrue(decoded.equipment.containsKey("cao_minh"))
    assertFalse(decoded.characters.containsKey("kai"))
  }
}
''', encoding="utf-8")
print("Cao Minh identity installed: cao_minh player ID, explorer/combat alpha overlays.")
