from hashlib import sha256
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
BALANCE_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiMonsterBalanceTest.kt"

ENTITY_HP_BONUS = 30
SKIP_KEYS = {
    "jeff_the_killer",
    "jane_the_killer",
    "diep_minh",
    "monster_x",
    "john_doe",
    "scp_173",
    "violet_warden",
}

PROFILE_RE = re.compile(
    r'Profile\("(?P<key>[^"]+)", "(?P<name>[^"]+)", (?P<hp>\d+), (?P<rest>[^)]*)\)'
)


def encounter_hp(key: str) -> int:
    digest = int(sha256(key.encode("utf-8")).hexdigest(), 16)
    return 300 + (digest % 201)


combat = COMBAT.read_text(encoding="utf-8")
changed = {}


def rewrite_profile(match: re.Match[str]) -> str:
    key = match.group("key")
    name = match.group("name")
    hp = int(match.group("hp"))
    rest = match.group("rest")
    if key in SKIP_KEYS:
        return match.group(0)
    current = hp + ENTITY_HP_BONUS
    if current >= 300:
        return match.group(0)
    target = encounter_hp(key)
    profile_hp = target - ENTITY_HP_BONUS
    changed[key] = target
    return f'Profile("{key}", "{name}", {profile_hp}, {rest})'


new_combat, count = PROFILE_RE.subn(rewrite_profile, combat)
if not changed:
    raise RuntimeError("No Entity profiles below 300 HP were found to raise")
if count < 1:
    raise RuntimeError("Entity profile rewrite did not match any Profile(...) entries")
COMBAT.write_text(new_combat, encoding="utf-8")

# This patch is the last HP authority in the generator chain, so it must also finalize
# expectations created by earlier balance/durability patches instead of leaving stale tests behind.
test = TEST.read_text(encoding="utf-8")
hound_hp = changed.get("hound", encounter_hp("hound"))
test = test.replace(
    "    assertEquals(110, combat.entityMaxHp)\n    assertEquals(110, combat.entityHp)\n",
    f"    assertEquals({hound_hp}, combat.entityMaxHp)\n    assertEquals({hound_hp}, combat.entityHp)\n",
)
test = test.replace(
    '    assertEquals(110, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)\n',
    f'    assertEquals({hound_hp}, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)\n',
)

old_map = re.search(
    r'    val expected = mapOf\(\n(?:      .*\n)+    \)',
    test,
)
if not old_map:
    raise RuntimeError("Entity durability expected HP map not found")

roaming = [
    "hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle",
    "paintings", "smiler", "skin-stealer", "predatory_window", "biological_pipeline",
    "wretch", "cable_mimic", "the_beast_of_level_5", "hotel_corpse_lure",
    "jeff_the_killer", "jane_the_killer", "slenderman",
]
pairs = []
for key in roaming:
    if key in {"jeff_the_killer", "jane_the_killer"}:
        pairs.append(f'"{key}" to 947')
    else:
        pairs.append(f'"{key}" to {changed.get(key, encounter_hp(key))}')
lines = ["    val expected = mapOf("]
for i in range(0, len(pairs), 4):
    chunk = ", ".join(pairs[i:i + 4])
    suffix = "," if i + 4 < len(pairs) else ""
    lines.append(f"      {chunk}{suffix}")
lines.append("    )")
test = test[:old_map.start()] + "\n".join(lines) + test[old_map.end():]

test = test.replace('"slenderman" to 190', f'"slenderman" to {changed.get("slenderman", encounter_hp("slenderman"))}')
TEST.write_text(test, encoding="utf-8")

if BALANCE_TEST.is_file():
    balance_test = BALANCE_TEST.read_text(encoding="utf-8")
    old_hound_expectation = '    assertEquals(110, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)\n'
    new_hound_expectation = f'    assertEquals({hound_hp}, CombatRuntime.active(CombatRuntime.start(GameState.initial(), "hound"))!!.entityMaxHp)\n'
    if old_hound_expectation not in balance_test and new_hound_expectation not in balance_test:
        raise RuntimeError("Kai monster balance Hound expectation anchor missing")
    balance_test = balance_test.replace(old_hound_expectation, new_hound_expectation, 1)
    BALANCE_TEST.write_text(balance_test, encoding="utf-8")

print("Raised Entity HP floor:")
for key, hp in changed.items():
    print(f"  {key}: {hp}")
