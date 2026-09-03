from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/PartyTurnCombatInterleavedTest.kt"

text = TEST.read_text(encoding="utf-8")
replacements = (
    (
        '    assertEquals("Iris", PartyTurnCombat.json(state)!!.getString("actorName"))\n',
        '    assertEquals(IRIS_ID, PartyTurnCombat.json(state)!!.getString("actorId"))\n',
        "Iris",
    ),
    (
        '    assertEquals("Lucia", PartyTurnCombat.json(state)!!.getString("actorName"))\n',
        '    assertEquals(LUCIA_ID, PartyTurnCombat.json(state)!!.getString("actorId"))\n',
        "Lucia",
    ),
    (
        '    assertEquals("Kai", PartyTurnCombat.json(state)!!.getString("actorName"))\n',
        '    assertEquals(KAI_ID, PartyTurnCombat.json(state)!!.getString("actorId"))\n',
        "Kai",
    ),
)

changed = 0
for old, new, label in replacements:
    count = text.count(old)
    if count > 0:
        text = text.replace(old, new)
        changed += count
    if new not in text:
        raise RuntimeError(f"{label} actor identity regression marker missing after normalization")

if changed == 0:
    raise RuntimeError("Actor identity regression finalizer made no changes")

TEST.write_text(text, encoding="utf-8")

print(
    f"Interleaved combat V5 applied: normalized {changed} actor-order assertions to stable Kai/Iris/Lucia ids."
)
