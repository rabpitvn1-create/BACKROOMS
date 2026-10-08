"""Install one authoritative, level-bound d10000 Entity selection AFTER all old roll patches.

The original 1% roaming, 1% Diệp Minh and 2% 1.1.93a compatibility rolls
are deliberately retained as intermediate source anchors for earlier release
checks. Their outcomes are replaced before makeGameplayRolls returns.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
java = MAIN.read_text(encoding="utf-8")

MARKER = "LEVEL_BOUND_ENTITY_SPAWN_V1"
if MARKER in java:
    raise RuntimeError("Level-bound Entity spawn patch applied twice")

signature = (
    "  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, "
    "String action, boolean meta) throws Exception {\n"
)
method_start = java.find(signature)
method_end = java.find("\n  private boolean rollSuccess(JSONObject rolls, String key) {", method_start)
if method_start < 0 or method_end < 0:
    raise RuntimeError("Final typed makeGameplayRolls/rollSuccess boundaries missing")
method = java[method_start:method_end]
end_anchor = "    return rolls;\n"
if method.count(end_anchor) != 1:
    raise RuntimeError("Expected one final makeGameplayRolls return")
if "entityEncounterAction && entityAllowed" not in method:
    raise RuntimeError("SEARCH, EXPLORE, EXECUTE entity gate missing")
method = method.replace(
    end_anchor,
    "    applyLevelBoundEntitySpawns(rolls, level, entityEncounterAction && entityAllowed);\n"
    + end_anchor,
    1,
)

helper = r'''
  // LEVEL_BOUND_ENTITY_SPAWN_V1: ONE shared d10000 outcome, no priority distortion.
  // The encounter key is selected only here; earlier compatibility rolls cannot
  // spawn a second Entity or override the selected normal/rare Entity.
  private void applyLevelBoundEntitySpawns(JSONObject rolls, int level, boolean eligible)
      throws Exception {
    final int total = com.rabpit.backroom.core.EntityEncounterPolicy.totalChanceBasisPoints(level);
    final int draw = eligible
        ? GAME_RNG.nextInt(com.rabpit.backroom.core.EntityEncounterPolicy.DIE) + 1 : 0;
    final Object rollValue = eligible ? Integer.valueOf(draw) : JSONObject.NULL;
    final com.rabpit.backroom.core.EntityEncounterPolicy.Window selected = eligible
        ? com.rabpit.backroom.core.EntityEncounterPolicy.pick(level, draw) : null;
    final String selectedKey = selected == null ? "" : selected.entityKey;

    JSONObject encounter = rolls.optJSONObject("entityEncounter");
    if (encounter == null) throw new IllegalStateException("entityEncounter roll missing");
    encounter.put("label", "entityEncounter")
        .put("dice", "d10000")
        .put("max", 10000)
        .put("threshold", total)
        .put("eligible", eligible)
        .put("roll", rollValue)
        .put("chancePercent", total / 100.0)
        .put("chance", String.format(java.util.Locale.ROOT, "%.4f%%", total / 100.0))
        .put("success", selected != null)
        .put("selectedBy", selected == null ? JSONObject.NULL : selected.rollLabel);

    // Keep legacy named special rolls readable, but resolve all of them from
    // THE SAME draw. Jeff/Jane/Slenderman now have independent 4% windows.
    for (com.rabpit.backroom.core.EntityEncounterPolicy.Window rare :
        com.rabpit.backroom.core.EntityEncounterPolicy.rareWindows()) {
      JSONObject special = rolls.optJSONObject(rare.rollLabel);
      if (special == null) special = new JSONObject();
      final double chance = rare.chanceBasisPoints() / 100.0;
      special.put("label", rare.rollLabel)
          .put("dice", "d10000")
          .put("max", 10000)
          .put("threshold", rare.chanceBasisPoints())
          .put("rangeStart", rare.start)
          .put("rangeEnd", rare.end)
          .put("eligible", eligible)
          .put("roll", rollValue)
          .put("chancePercent", chance)
          .put("chance", String.format(java.util.Locale.ROOT, "%.4f%%", chance))
          .put("success", selected != null && rare.entityKey.equals(selectedKey));
      rolls.put(rare.rollLabel, special);
    }

    // Combat93, snapshot and the boss priority bridge already consume these
    // two authoritative values; do not introduce a second encounter channel.
    if (selected == null) rolls.remove("roamingEntityKey");
    else rolls.put("roamingEntityKey", selected.entityKey);
    rolls.put("entitySpawnPolicy", new JSONObject()
        .put("revision", "LEVEL_BOUND_ENTITY_SPAWN_V1")
        .put("level", level)
        .put("normalPerEntityPercent", 8.0)
        .put("selectedEntityKey", selected == null ? JSONObject.NULL : selectedKey)
        .put("selectedRare", selected != null && selected.rare)
        .put("totalChancePercent", total / 100.0));
  }
'''
java = java[:method_start] + method + helper + java[method_end:]

# Tell the AI Game Master that its older prose about all-level roaming,
# exclusive EXPLORE gating and 1%/2% rare rates has been superseded.
writer_start = java.find("  private String writerPrompt(JSONObject before, String action, "
                         "JSONObject rolls, JSONArray auditFeedback) throws Exception {")
writer_end = java.find("\n  private JSONArray localKnowledgeIssues(", writer_start)
if writer_start < 0 or writer_end < 0:
    raise RuntimeError("Final writerPrompt boundaries missing")
writer = java[writer_start:writer_end]
return_starts = list(re.finditer(r"(?m)^    return ", writer))
if len(return_starts) != 1:
    raise RuntimeError("Writer prompt needs one stable return anchor")
directive = (
    '    return "ENTITY SPAWN POLICY V1 (NEWEST RUNTIME RULE, OVERRIDES OLD '
    'ENTITY ROAMING/RARE RATES): Only rolls.entityEncounter.success=true may '
    'start a NEW hostile Entity encounter. Always use exactly rolls.roamingEntityKey; '
    'never invent, reroll or substitute a different Entity. Fifteen NORMAL Entities '
    'are Level-bound at 8% each, only at their assigned Level; Levels with no '
    'normal Entity still roll RARE Entities. Nine RARE Entities can occur on ANY '
    'Level: Diệp Minh 3%; Lục Trầm Hắc Hoá, Bacterial Stalker, Bacterial Strider, '
    'Bacterial Weaver, Research ASYNC Member, Slenderman, Jane the Killer and '
    'Jeff the Killer 4% EACH. SEARCH, EXECUTE, EXPLORE are eligible. Each action '
    'starts at most ONE encounter. Ignore all contrary older GM text. " +\n      '
)
writer = writer.replace("    return ", directive, 1)
java = java[:writer_start] + writer + java[writer_end:]

for contract in (
    "LEVEL_BOUND_ENTITY_SPAWN_V1",
    "applyLevelBoundEntitySpawns(rolls, level, entityEncounterAction && entityAllowed);",
    "EntityEncounterPolicy.pick(level, draw)",
    'rolls.remove("roamingEntityKey")',
    'rolls.put("roamingEntityKey", selected.entityKey)',
    '"diepMinhEncounter"',
    "DIỆP MINH BOSS HARD LOCK:",
    "COMBAT_93_POST_COMMIT_START",
):
    if contract not in java:
        raise RuntimeError("Required generated runtime contract missing: " + contract)

MAIN.write_text(java, encoding="utf-8")
print("Installed level-bound 8%/Entity spawn with 9 global rares (3% Diệp, 4% others), one authoritative roll.")
