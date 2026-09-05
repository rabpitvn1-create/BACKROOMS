from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-lucia-proc-skills-final.py"
code = PATCH.read_text(encoding="utf-8")

old_exit_anchor = """    '    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search), \" discovery clue\");\\n',
"""
new_exit_anchor = """    '    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search), anNhienFollowing ? \" discovery clue +2% An Nhiên\" : \" discovery clue\");\\n',
"""
old_exit_replacement = """    '    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search) && !(level == 0 && !luciaEncountered(state)), \" discovery clue\");\\n',
"""
new_exit_replacement = """    '    JSONObject exitProbe = thresholdRoll(\"exitProbe\", 10000, exitThreshold, exitIntent && (physical || search) && !(level == 0 && !luciaEncountered(state)), anNhienFollowing ? \" discovery clue +2% An Nhiên\" : \" discovery clue\");\\n',
"""

old_loot_anchor = """    '    rolls.put(\"loot\", thresholdRoll(\"loot\", 10000, lootThresholds[level], search, \"\"));\\n',
"""
new_loot_anchor = """    '    rolls.put(\"loot\", thresholdRoll(\"loot\", 10000, Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0)), search, anNhienFollowing ? \" +10% An Nhiên\" : \"\"));\\n',
"""
old_loot_replacement = """    '''    int lootThreshold = lootThresholds[level];
    if (partyHas(state, \"Lucia\") || partyHas(state, \"Hứa Thuý Mai\")) lootThreshold = Math.min(10000, lootThreshold + 500);
    rolls.put(\"loot\", thresholdRoll(\"loot\", 10000, lootThreshold, search, partyHas(state, \"Lucia\") || partyHas(state, \"Hứa Thuý Mai\") ? \" +5pp Lucia battlefield scout\" : \"\"));
''',
"""
new_loot_replacement = """    '''    int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0));
    boolean luciaScoutFollowing = partyHas(state, \"Lucia\") || partyHas(state, \"Hứa Thuý Mai\");
    if (luciaScoutFollowing) lootThreshold = Math.min(10000, lootThreshold + 500);
    String lootSuffix = anNhienFollowing ? \" +10% An Nhiên\" : \"\";
    if (luciaScoutFollowing) lootSuffix += \" +5pp Lucia battlefield scout\";
    rolls.put(\"loot\", thresholdRoll(\"loot\", 10000, lootThreshold, search, lootSuffix));
''',
"""

for old, new, label in [
    (old_exit_anchor, new_exit_anchor, "final exit anchor"),
    (old_exit_replacement, new_exit_replacement, "final exit replacement"),
    (old_loot_anchor, new_loot_anchor, "final loot anchor"),
    (old_loot_replacement, new_loot_replacement, "final loot replacement"),
]:
    if code.count(old) != 1:
        raise RuntimeError(f"Lucia compat {label}: expected exactly one source match, found {code.count(old)}")
    code = code.replace(old, new, 1)

# Make the engine-level regression deterministic without depending on incidental RNG
# calls from entity retaliation. Zero keeps every Lucia proc successful while Evasion
# prevents the target from dying before Lucia reaches her second combat turn.
old_rng = r'''    val random = SequenceRandom(listOf(
      0.99, 0.99,
      0.00, 0.00, 0.99, 0.99,
      0.99, 0.99,
      0.99, 0.99,
      0.00, 0.00, 0.99, 0.99, 0.99
    ))
'''
new_rng = r'''    val random = SequenceRandom(emptyList(), fallback = 0.0)
'''
if code.count(old_rng) != 1:
    raise RuntimeError(f"Lucia compat RNG regression anchor: expected exactly one source match, found {code.count(old_rng)}")
code = code.replace(old_rng, new_rng, 1)

# The older An Nhiên finalizer already owns the Level-0 transition guard. Extend it
# after Lucia first-contact logic exists so a pre-discovered exit cannot skip Lucia.
insert_before_write = '''MAIN.write_text(main, encoding="utf-8")\n'''
transition_patch = r"""main = replace_once(
    main,
    '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    if (currentLevel(before) == 0 && !anNhienEncountered(before)) return false;
''',
    '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    if (currentLevel(before) == 0 && (!anNhienEncountered(before) || !luciaEncountered(before))) return false;
''',
    "Level-0 transition lock before Lucia",
)

"""
if code.count(insert_before_write) != 1:
    raise RuntimeError(f"Lucia compat transition insertion: expected exactly one write anchor, found {code.count(insert_before_write)}")
code = code.replace(insert_before_write, transition_patch + insert_before_write, 1)

exec(compile(code, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
