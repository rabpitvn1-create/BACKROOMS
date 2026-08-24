from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

text = COMBAT.read_text(encoding="utf-8")
old = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < hitChance) {
'''
new = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < hitChance && !luciaEntityEvaded) {
'''
if new not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia Entity evasion compatibility: expected 1 anchor, found {count}")
    text = text.replace(old, new, 1)

for marker in (
    'val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)',
    'val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT',
    'if (luciaRoll < hitChance && !luciaEntityEvaded)',
):
    if marker not in text:
        raise RuntimeError("Lucia Entity evasion contract missing: " + marker)

COMBAT.write_text(text, encoding="utf-8")
print("Lucia joint attack aligned with the existing 25% Entity evasion gate.")
