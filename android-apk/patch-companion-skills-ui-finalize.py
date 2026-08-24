from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-companion-skills-ui.py"

source = PATCH.read_text(encoding="utf-8")
old = "skills_anchor = '    put(\"statuses\", JSONArray().apply {\\n'"
new = "skills_anchor = '    put(\"equipment\", JSONObject(c.equipment))\\n'"
if old not in source:
    raise RuntimeError("Companion skill finalizer could not locate Character Detail projection anchor")
source = source.replace(old, new, 1)
source = source.replace(
    'CompanionSkillCatalog.forCharacter(character.id)',
    'CompanionSkillCatalog.forCharacter(c.id)',
    1,
)

# Avoid relying on a newer stdlib helper when a plain metadata comparison is enough.
source = source.replace(
    'state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.toBooleanStrictOrNull() ?: false',
    'state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.equals("true", ignoreCase = true) == true',
    1,
)

# Use a unique countdown marker for idempotence. Earlier Analyze writes should not suppress
# the end-of-turn countdown insertion.
source = source.replace(
    "if 'resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY' not in combat.split(countdown_anchor)[0][-1800:]:",
    "if 'irisAnalyzedTurns = max(0, irisAnalyzedTurns - 1)' not in combat:",
    1,
)

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
print("Companion skill finalizer executed against the final Character Detail/runtime projection.")
