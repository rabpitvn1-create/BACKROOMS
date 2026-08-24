from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-lucia-combat-scout.py"

source = PATCH.read_text(encoding="utf-8")

# The generated Kotlin source correctly contains escaped quotes around Lục. The original
# textual contract check compared against runtime text instead of escaped Kotlin source.
source, count = re.subn(
    r"^\s*'Lucia \\\"Lục\\\" bắn hỗ trợ bằng M4A1',\n",
    "",
    source,
    count=1,
    flags=re.MULTILINE,
)
if count != 1:
    raise RuntimeError("Lucia combat scout finalizer could not locate the redundant escaped-log marker")

# An Nhiên has already rewritten the generic loot line by this stage. Replace the fragile
# exact-line block in the Lucia patch with a final-runtime splice that composes both bonuses.
loot_start = source.find("loot_old = ")
loot_replace = 'main = replace_once(main, loot_old, loot_new, "Lucia +5 percentage-point loot bonus")'
loot_replace_pos = source.find(loot_replace, loot_start)
if loot_start < 0 or loot_replace_pos < 0:
    raise RuntimeError("Lucia combat scout finalizer could not locate loot composition block")
loot_end = loot_replace_pos + len(loot_replace)
loot_block = r"""loot_anchor_start = main.find('    rolls.put("loot", thresholdRoll("loot", 10000,')
if loot_anchor_start < 0:
    raise RuntimeError("Lucia loot bonus: generic loot roll not found")
loot_anchor_end = main.find("\n", loot_anchor_start)
if loot_anchor_end < 0:
    raise RuntimeError("Lucia loot bonus: generic loot roll line is truncated")
loot_new = '''    int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;
    int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0) + luciaScoutBonus);
    String lootSuffix = (anNhienFollowing ? " +10% An Nhiên" : "") +
      (luciaScoutBonus > 0 ? " + Lucia Trinh sát chiến trường 5%" : "");
    rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold, search, lootSuffix));
'''
main = main[:loot_anchor_start] + loot_new + main[loot_anchor_end + 1:]
"""
source = source[:loot_start] + loot_block + source[loot_end:]

# The final composed threshold necessarily includes An Nhiên's existing +1000 term, so the
# original Lucia-only exact threshold marker is obsolete. Keep the stronger runtime markers.
source, marker_count = re.subn(
    r"^\s*'int lootThreshold = Math\.min\(10000, lootThresholds\[level\] \+ luciaScoutBonus\);',\n",
    "",
    source,
    count=1,
    flags=re.MULTILINE,
)
if marker_count != 1:
    raise RuntimeError("Lucia combat scout finalizer could not remove obsolete loot threshold marker")

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
print("Lucia combat/scout finalizer executed with composed An Nhiên + Lucia loot bonuses.")
