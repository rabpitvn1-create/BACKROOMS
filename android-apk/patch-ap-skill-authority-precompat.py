from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

combat = COMBAT.read_text(encoding="utf-8")

# Entity party-action budgeting moved Dead Angle / Counterphase into the Entity
# response phase as percentage auto-counters. Retire those real proc blocks here
# before the AP authority finalizer re-homes both skills as explicit paid actions.
patterns = (
    (
        r'\n      if \(missedIris && irisActive && c\.entityHp > 0 && roll\(c\.copy\(eventCounter = c\.eventCounter \+ 281\), 100\) < 15\) \{\n.*?\n      \}\n',
        "Dead Angle legacy counter",
    ),
    (
        r'\n      if \(missedSyvial && syvialActive && c\.entityHp > 0 && roll\(c\.copy\(eventCounter = c\.eventCounter \+ 293\), 100\) < 30\) \{\n.*?\n      \}\n',
        "Counterphase legacy counter",
    ),
)
first_removed_at = None
for pattern, label in patterns:
    match = re.search(pattern, combat, flags=re.S)
    if match is None:
        raise RuntimeError(f"AP skill precompat missing {label}")
    if first_removed_at is None:
        first_removed_at = match.start()
    combat = combat[:match.start()] + "\n" + combat[match.end():]

if first_removed_at is None:
    raise RuntimeError("AP skill precompat did not remove legacy counters")

# The finalizer historically used the old pre-budget Dead Angle block as its
# insertion anchor. The correct execution point for a manually selected skill is
# the player phase, immediately before the existing authoritative defeat check
# and before any Entity response. Put a zero-behaviour sentinel there so the
# finalizer can replace only the sentinel while preserving every Entity branch.
defeat_anchor = '    if (c.entityHp <= 0) {\n'
defeat_at = combat.rfind(defeat_anchor, 0, first_removed_at)
if defeat_at < 0:
    raise RuntimeError("AP skill precompat could not locate pre-response defeat checkpoint")
sentinel = '''        if (irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
          // AP_SKILL_COUNTER_SENTINEL: replaced by the final authority patch before compile.
        }
'''
combat = combat[:defeat_at] + sentinel + combat[defeat_at:]

for forbidden in ("if (missedIris && irisActive", "if (missedSyvial && syvialActive"):
    if forbidden in combat:
        raise RuntimeError("Legacy percentage counter survived precompat: " + forbidden)
if "AP_SKILL_COUNTER_SENTINEL" not in combat:
    raise RuntimeError("AP skill counter sentinel missing")

COMBAT.write_text(combat, encoding="utf-8")
print("AP skill precompat applied: legacy percentage counters retired; manual skills anchor before Entity response.")
