from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/PartyTurnCombatInterleavedTest.kt"

text = TEST.read_text(encoding="utf-8")
old = '    assertEquals("Lucia", PartyTurnCombat.json(state)!!.getString("actorName"))\n'
new = '    assertEquals(LUCIA_ID, PartyTurnCombat.json(state)!!.getString("actorId"))\n'

if new not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia actor identity regression: expected one stale display-name assertion, found {count}")
    text = text.replace(old, new, 1)

TEST.write_text(text, encoding="utf-8")

if new not in TEST.read_text(encoding="utf-8"):
    raise RuntimeError("Lucia actor identity regression marker missing")

print("Interleaved combat V5 applied: actor sequencing regression keys Lucia by stable character id, not mutable display name.")
