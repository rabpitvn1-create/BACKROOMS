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

for old, new, label in replacements:
    if new in text:
        continue
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"{label} actor identity regression: expected one stale display-name assertion, found {count}"
        )
    text = text.replace(old, new, 1)

TEST.write_text(text, encoding="utf-8")

final = TEST.read_text(encoding="utf-8")
for _, marker, label in replacements:
    if marker not in final:
        raise RuntimeError(f"{label} actor identity regression marker missing")

print(
    "Interleaved combat V5 applied: actor sequencing regressions key Kai/Iris/Lucia by stable character ids, not mutable display names."
)
