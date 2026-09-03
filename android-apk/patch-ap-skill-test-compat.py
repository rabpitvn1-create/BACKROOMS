from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

text = TEST.read_text(encoding="utf-8")
needle = "luciaTooYoungToDieTriggerChancePercent"
lines = text.splitlines(keepends=True)
refs = [i for i, line in enumerate(lines) if needle in line]
if len(refs) != 6:
    raise RuntimeError(f"Expected 6 stale Too Young To Die percentage assertions, found {len(refs)}")

start = refs[0]
while start >= 0 and not lines[start].lstrip().startswith("@Test fun "):
    start -= 1
if start < 0:
    raise RuntimeError("Could not locate stale Too Young To Die test start")

end = refs[-1] + 1
while end < len(lines) and not lines[end].lstrip().startswith("@Test fun "):
    end += 1

if any(needle in line for line in lines[:start] + lines[end:]):
    raise RuntimeError("Too Young To Die percentage helper is referenced outside the retired test")

replacement = [
    "  // Percentage-trigger regression retired with AP skill authority.\n",
    "  // Manual activation/cost/effect coverage lives in PartyTurnCombatApSkillAuthorityTest.\n",
]
lines[start:end] = replacement
text = "".join(lines)

if needle in text:
    raise RuntimeError("Retired percentage-trigger helper still referenced by CombatRuntimeTest")

TEST.write_text(text, encoding="utf-8")
print("AP skill test compatibility applied: retired Lucia percentage-proc regression removed; AP regressions remain authoritative.")
