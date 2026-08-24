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

# An Nhiên's +10 percentage-point follower bonus is already installed earlier in the
# final patch stack. Compose Lucia's +5 points with that existing threshold instead of
# replacing it, so both followers retain their independent canon bonuses.
loot_start = source.find("loot_old = ")
loot_end = source.find('main = replace_once(main, loot_old, loot_new, "Lucia +5 percentage-point loot bonus")', loot_start)
if loot_start < 0 or loot_end < 0:
    raise RuntimeError("Lucia combat scout finalizer could not locate loot composition block")
loot_block = r'''loot_old = '    rolls.put("loot", thresholdRoll("loot", 10000, Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0)), search, anNhienFollowing ? " +10% An Nhiên" : ""));\n'
loot_new = '''    int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;
    int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0) + luciaScoutBonus);
    String lootSuffix = (anNhienFollowing ? " +10% An Nhiên" : "") +
      (luciaScoutBonus > 0 ? " + Lucia Trinh sát chiến trường 5%" : "");
    rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold, search, lootSuffix));
'''
'''
source = source[:loot_start] + loot_block + source[loot_end:]

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
print("Lucia combat/scout finalizer executed with composed An Nhiên + Lucia loot bonuses.")
