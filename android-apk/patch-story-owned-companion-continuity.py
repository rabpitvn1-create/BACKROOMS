from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
COMPANIONS = ROOT / "app/src/main/assets/campaign_story/companion-continuity.json"
SPECIAL = CORE / "SpecialFollowersCanon.kt"
LUCIA = CORE / "LuciaCanon.kt"
GAME_CORE = CORE / "GameCoreFacade.kt"
ENGINES = CORE / "Engines.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def rewrite_character_function(text: str, function_name: str, next_marker: str, presence: str, fixed_level: int) -> str:
    start = text.find(f"  fun {function_name}(existing: CharacterState? = null): CharacterState {{")
    end = text.find(next_marker, start)
    if start < 0 or end < 0:
        raise RuntimeError(f"{function_name}: function block missing")
    block = text[start:end]
    if f"fixedEncounterLevel\" to \"{fixed_level}" not in block:
        block = replace_once(
            block,
            "    return base.copy(\n",
            f"    return base.copy(\n      presence = existing?.presence ?: CharacterPresence.{presence},\n",
            f"{function_name} initial presence",
        )
        block = replace_once(
            block,
            '        "encounterChance" to ENCOUNTER_CHANCE,\n',
            '        "encounterChance" to "0%",\n'
            '        "randomSpawn" to "false",\n'
            '        "storyOwned" to "true",\n'
            f'        "fixedEncounterLevel" to "{fixed_level}",\n',
            f"{function_name} fixed story metadata",
        )
        block = block.replace('        "encounterLevels" to ENCOUNTER_LEVELS,\n', '        "encounterLevels" to "STORY_ONLY",\n', 1)
    return text[:start] + block + text[end:]


# Companion continuity is a deterministic story contract, never an encounter probability.
continuity = json.loads(COMPANIONS.read_text(encoding="utf-8"))
expected = {
    "lucia": (0, "FIXED_ENCOUNTER"),
    "syvial": (37, "FIXED_REUNION"),
    "iris": (94, "FIXED_REUNION"),
}
if continuity.get("missionYear") != 2299 or continuity.get("unit") != "SRU" or continuity.get("missionSubject") != "Async":
    raise RuntimeError("story_companion_mission_canon_invalid")
entry = continuity.get("entry") or {}
if entry.get("participants") != ["kai", "iris", "syvial"]:
    raise RuntimeError("story_companion_entry_participants_invalid")
if entry.get("sameGate") is not True or entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLevelsDifferent") is not True:
    raise RuntimeError("story_companion_entry_separation_invalid")
for character_id, (level, event_type) in expected.items():
    raw = (continuity.get("companions") or {}).get(character_id) or {}
    if raw.get("level") != level or raw.get("eventType") != event_type:
        raise RuntimeError("story_companion_gate_invalid:" + character_id)
    if raw.get("randomSpawn") is not False or raw.get("storyOwned") is not True:
        raise RuntimeError("story_companion_random_spawn_must_be_disabled:" + character_id)
if (continuity.get("companions") or {}).get("lucia", {}).get("requiresQuest") is not False:
    raise RuntimeError("lucia_fixed_encounter_must_not_require_quest")

# The main-story patch now owns campaign canon directly. This patch only verifies it.
story = json.loads(STORY.read_text(encoding="utf-8"))
if (story.get("entryEvent") or {}).get("year") != 2299:
    raise RuntimeError("story_companion_campaign_year_mismatch")
if (story.get("officialMission") or {}).get("unit") != "SRU":
    raise RuntimeError("story_companion_campaign_unit_mismatch")
if story.get("companionContinuityRef") != "campaign_story/companion-continuity.json":
    raise RuntimeError("story_companion_ref_missing")
story_text = json.dumps(story, ensure_ascii=False)
for obsolete in ("Hứa Thuý Lan", "2267", "Black Blood"):
    if obsolete in story_text:
        raise RuntimeError("obsolete_campaign_canon_reintroduced:" + obsolete)

# Existing generated character definitions keep their equipment/stats, but encounter authority is story-owned.
special = SPECIAL.read_text(encoding="utf-8")
special = special.replace('  const val ENCOUNTER_CHANCE = "0.25%"', '  const val ENCOUNTER_CHANCE = "0%"', 1)
special = special.replace('  const val ENCOUNTER_LEVELS = "0-6"', '  const val ENCOUNTER_LEVELS = "STORY_ONLY"', 1)
special = rewrite_character_function(special, "irisCharacter", "\n  fun syvialCharacter", "SEPARATED", 94)
special = rewrite_character_function(special, "syvialCharacter", "\n  fun ensure", "SEPARATED", 37)
SPECIAL.write_text(special, encoding="utf-8")

lucia = LUCIA.read_text(encoding="utf-8")
lucia = lucia.replace('  const val ENCOUNTER_CHANCE = "50%"', '  const val ENCOUNTER_CHANCE = "0%"', 1)
if '"fixedEncounterLevel" to "0"' not in lucia:
    lucia = replace_once(
        lucia,
        "    return base.copy(\n",
        "    return base.copy(\n      presence = existing?.presence ?: CharacterPresence.MISSING,\n",
        "Lucia initial story presence",
    )
    lucia = replace_once(
        lucia,
        '        "encounterChance" to ENCOUNTER_CHANCE,\n',
        '        "encounterChance" to "0%",\n'
        '        "randomSpawn" to "false",\n'
        '        "storyOwned" to "true",\n'
        '        "fixedEncounterLevel" to "0",\n'
        '        "requiresQuest" to "false",\n',
        "Lucia fixed encounter metadata",
    )
    lucia = lucia.replace('        "encounterAction" to "EXPLORE",\n', '        "encounterAction" to "STORY",\n', 1)
LUCIA.write_text(lucia, encoding="utf-8")

# Party commit may activate a story-owned SEPARATED/MISSING character only at its fixed Level.
core = GAME_CORE.read_text(encoding="utf-8")
if "val candidateLevel = candidate.optJSONObject" not in core:
    core = replace_once(
        core,
        '    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()\n',
        '    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()\n'
        '    val candidateLevel = candidate.optJSONObject("level")?.optInt("number", -1) ?: -1\n',
        "story companion candidate level",
    )
    core = replace_once(
        core,
        '      val known = pending.state.characters[id]\n      commands += PartyCommand(\n',
        '      val known = pending.state.characters[id]\n'
        '      val storyJoin = StoryCompanionContinuity.canMaterialize(id, candidateLevel, id in currentFollowers)\n'
        '      commands += PartyCommand(\n',
        "story companion materialization gate",
    )
    core = replace_once(
        core,
        '        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE\n',
        '        targetPresent = member.optBoolean("present", false) && (known?.presence == CharacterPresence.ACTIVE || storyJoin)\n',
        "story companion target presence",
    )
GAME_CORE.write_text(core, encoding="utf-8")

engines = ENGINES.read_text(encoding="utf-8")
if "character.copy(presence = CharacterPresence.ACTIVE)" not in engines:
    engines = replace_once(
        engines,
        '      changed(state.copy(party = state.party.copy(memberIds = state.party.memberIds + command.targetId)), "party_member_added")\n',
        '      val character = state.characters.getValue(command.targetId)\n'
        '      changed(\n'
        '        state.copy(\n'
        '          party = state.party.copy(memberIds = state.party.memberIds + command.targetId),\n'
        '          characters = state.characters + (command.targetId to character.copy(presence = CharacterPresence.ACTIVE))\n'
        '        ),\n'
        '        "party_member_added"\n'
        '      )\n',
        "activate companion on committed party add",
    )
ENGINES.write_text(engines, encoding="utf-8")

# Android runtime removes all companion probability rolls. Future reunion Levels remain backend-only.
main = MAIN.read_text(encoding="utf-8")
if "import com.rabpit.backroom.core.StoryCompanionContinuity;" not in main:
    main = replace_once(
        main,
        "import com.rabpit.backroom.core.GameCoreFacade;\n",
        "import com.rabpit.backroom.core.GameCoreFacade;\nimport com.rabpit.backroom.core.StoryCompanionContinuity;\n",
        "import story companion continuity",
    )

old_rolls = '''    rolls.put("irisReunion", thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris"), " follower encounter"));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));
    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia"), " Level 0 Lucia follower encounter"));
'''
new_rolls = '''    boolean irisStoryGate = StoryCompanionContinuity.canMaterialize("iris", level, partyHas(state, "iris") || flagSpawned(state, "iris"));
    boolean syvialStoryGate = StoryCompanionContinuity.canMaterialize("syvial", level, partyHas(state, "syvial") || flagSpawned(state, "syvial"));
    boolean luciaStoryGate = StoryCompanionContinuity.canMaterialize("lucia", level, partyHas(state, "lucia") || flagSpawned(state, "lucia"));
    rolls.put("irisReunion", new JSONObject().put("label", "irisReunion").put("storyOwned", true).put("eligible", irisStoryGate).put("success", irisStoryGate).put("roll", JSONObject.NULL));
    rolls.put("syvialReunion", new JSONObject().put("label", "syvialReunion").put("storyOwned", true).put("eligible", syvialStoryGate).put("success", syvialStoryGate).put("roll", JSONObject.NULL));
    rolls.put("luciaEncounter", new JSONObject().put("label", "luciaEncounter").put("storyOwned", true).put("requiresQuest", false).put("eligible", luciaStoryGate).put("success", luciaStoryGate).put("roll", JSONObject.NULL));
'''
if 'put("storyOwned", true).put("requiresQuest", false)' not in main:
    main = replace_once(main, old_rolls, new_rolls, "replace random companion rolls with story gates")

for forbidden in (
    'thresholdRoll("irisReunion"',
    'thresholdRoll("syvialReunion"',
    'thresholdRoll("luciaEncounter"',
    "Hứa Thuý Lan",
    "năm 2267",
    'unit", "Black Blood"',
):
    if forbidden in main:
        raise RuntimeError("story_companion_forbidden_runtime_marker:" + forbidden)
for marker in (
    'StoryCompanionContinuity.canMaterialize("iris", level',
    'StoryCompanionContinuity.canMaterialize("syvial", level',
    'StoryCompanionContinuity.canMaterialize("lucia", level',
    'MAIN STORY HARD LOCK: năm 2299',
):
    if marker not in main:
        raise RuntimeError("story_companion_runtime_gate_missing:" + marker)
MAIN.write_text(main, encoding="utf-8")

print("Applied story-owned companion continuity without rewriting campaign canon: Lucia fixed at Level 0, Syvial reunion at 37, Iris reunion at 94, no companion spawn rolls.")
