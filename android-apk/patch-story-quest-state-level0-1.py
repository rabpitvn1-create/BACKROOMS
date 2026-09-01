from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
GAME_STATE = CORE / "GameState.kt"
CODEC = CORE / "GameStateCodec.kt"
FACADE = CORE / "GameCoreFacade.kt"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"
QUESTS = ROOT / "app/src/main/assets/campaign_story/level0-to-level1-quests.json"
MAIN_STORY_PATCH = ROOT / "patch-main-story-level0-1.py"
COMPANION_PATCH = ROOT / "patch-story-owned-companion-continuity.py"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


quest = json.loads(QUESTS.read_text(encoding="utf-8"))
story = json.loads(STORY.read_text(encoding="utf-8"))
if quest.get("planId") != "QUEST_PLAN_LEVEL0_TO_LEVEL1_R01":
    raise RuntimeError("level01_quest_plan_id_invalid")
if quest.get("storyId") != story.get("storyId") or quest.get("campaignId") != story.get("campaignId"):
    raise RuntimeError("level01_quest_plan_story_identity_mismatch")
for key in (
    "coreOwnsProgression", "geminiCannotAdvanceQuest", "liteRTCannotAdvanceQuest",
    "oneSignalCompletesAtMostOneObjective", "luciaEncounterIsNotQuestObjective",
):
    if (quest.get("locks") or {}).get(key) is not True:
        raise RuntimeError("level01_quest_lock_missing:" + key)

objective_rows = []
chapter = quest.get("chapter") or {}
for q in ((chapter.get("act") or {}).get("quests") or []):
    for objective in q.get("objectives") or []:
        objective_rows.append((q.get("id"), objective.get("id"), objective.get("completion") or {}))
expected = [
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_01_VERIFY_LAYOUT_ANOMALY", "EVIDENCE_ANY"),
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_02_IDENTIFY_TRANSITION", "EVIDENCE_ANY"),
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_03_CONFIRM_HUM_DIRECTION", "EVIDENCE_ANY"),
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_04_LEAVE_LEVEL_ZERO_CORE", "LEVEL_ESCAPED"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_05_REACH_BIOHAZARD_REGION", "ENTER_AREA"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_06_REACH_DEEP_REGION", "ENTER_AREA"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_07_REACH_RED_ROOMS", "ENTER_AREA"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_08_REACH_LEVEL_ONE", "ENTER_AREA"),
]
actual = [(qid, oid, completion.get("type")) for qid, oid, completion in objective_rows]
if actual != expected:
    raise RuntimeError("level01_quest_objective_order_invalid")
if any("lucia" in json.dumps(row, ensure_ascii=False).lower() for row in objective_rows):
    raise RuntimeError("lucia_must_not_be_quest_objective")

story_text = STORY.read_text(encoding="utf-8")
for obsolete in ("Hứa Thuý Lan", "2267", "Black Blood"):
    if obsolete in story_text:
        raise RuntimeError("obsolete_story_asset_canon_survived:" + obsolete)
main_story_patch_text = MAIN_STORY_PATCH.read_text(encoding="utf-8")
if "MISSION_YEAR = 2267" in main_story_patch_text or "PRIVATE_TARGET =" in main_story_patch_text:
    raise RuntimeError("obsolete_main_story_generator_canon_survived")
companion_patch_text = COMPANION_PATCH.read_text(encoding="utf-8")
if 'story.pop("kaiPrivateObjective"' in companion_patch_text or 'story["officialMission"].update' in companion_patch_text:
    raise RuntimeError("companion_patch_must_not_rewrite_campaign_canon")

# Persist typed StoryState. No migration branch is added for this New Game feature.
game_state = GAME_STATE.read_text(encoding="utf-8")
if "val story: StoryState" not in game_state:
    game_state = replace_once(
        game_state,
        "  val saveVersion: Int = CURRENT_SAVE_VERSION,\n",
        "  val story: StoryState = StoryState.initial(),\n  val saveVersion: Int = CURRENT_SAVE_VERSION,\n",
        "GameState StoryState field",
    )
GAME_STATE.write_text(game_state, encoding="utf-8")

codec = CODEC.read_text(encoding="utf-8")
if 'put("story", StoryStateJson.encode(state.story))' not in codec:
    codec = replace_once(
        codec,
        '    put("world", stringMap(state.world))\n    put("metadata", stringMap(state.metadata))\n',
        '    put("world", stringMap(state.world))\n    put("story", StoryStateJson.encode(state.story))\n    put("metadata", stringMap(state.metadata))\n',
        "encode StoryState",
    )
if 'story = root.optJSONObject("story")?.let(StoryStateJson::decode)' not in codec:
    decode_start = codec.find("  private fun decodeCurrent(root: JSONObject): GameState {")
    decode_end = codec.find("\n  private fun character(", decode_start)
    if decode_start < 0 or decode_end < 0:
        raise RuntimeError("decodeCurrent block missing")
    block = codec[decode_start:decode_end]
    block = replace_once(
        block,
        "      saveVersion = CURRENT_SAVE_VERSION,\n",
        '      story = root.optJSONObject("story")?.let(StoryStateJson::decode) ?: StoryState.initial(),\n      saveVersion = CURRENT_SAVE_VERSION,\n',
        "decode StoryState",
    )
    codec = codec[:decode_start] + block + codec[decode_end:]
CODEC.write_text(codec, encoding="utf-8")

# Final facade at this point already owns LevelCatalog, BackroomsDirector and WorldDirector.
facade = FACADE.read_text(encoding="utf-8")
if "private val storyQuestPlan: StoryQuestPlan" not in facade:
    facade = replace_once(
        facade,
        '''  private val levelRegistry: LevelRegistry,
  private val levelCatalog: LevelCatalog,
  private val backroomsDirector: BackroomsDirector,
  private val worldDirector: WorldDirector
) : AutoCloseable {''',
        '''  private val levelRegistry: LevelRegistry,
  private val levelCatalog: LevelCatalog,
  private val backroomsDirector: BackroomsDirector,
  private val worldDirector: WorldDirector,
  private val storyQuestPlan: StoryQuestPlan
) : AutoCloseable {''',
        "story quest facade constructor",
    )
    facade = replace_once(
        facade,
        '''  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()
''',
        '''  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()
  private val storyQuestEngine = StoryQuestEngine(storyQuestPlan)
''',
        "story quest engine property",
    )
    facade = replace_once(
        facade,
        '''        AndroidLevelRegistry.load(appContext),
        AndroidLevelCatalog.load(appContext),
        BackroomsDirector.liteRT(appContext),
        WorldDirector.liteRT(appContext)
''',
        '''        AndroidLevelRegistry.load(appContext),
        AndroidLevelCatalog.load(appContext),
        BackroomsDirector.liteRT(appContext),
        WorldDirector.liteRT(appContext),
        AndroidStoryQuestPlan.load(appContext)
''',
        "story quest plan app factory",
    )

registered_old = '''    repository.save(result.state)
    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."
    appendLog(output, action, reply)
    logger.log(PipelineLogEvent(
      "REGISTERED_LEVEL_COMMIT",
      turnId = result.state.metadata["lastAction.turnId"],
'''
registered_new = '''    val storyState = storyQuestEngine.applySignal(
      result.state,
      StorySignal(
        areaId = levelId,
        evidenceIds = result.evidenceIds,
        escapedLevelId = levelId.takeIf { result.escaped }
      )
    )
    repository.save(storyState)
    val output = syncLegacy(legacy, storyState, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."
    appendLog(output, action, reply)
    logger.log(PipelineLogEvent(
      "REGISTERED_LEVEL_COMMIT",
      turnId = storyState.metadata["lastAction.turnId"],
'''
if "escapedLevelId = levelId.takeIf { result.escaped }" not in facade:
    facade = replace_once(facade, registered_old, registered_new, "registered Level story signal")

validated_old = '''    repository.save(committed.state)
    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
'''
validated_new = '''    val storyState = storyQuestEngine.applySignal(
      committed.state,
      StorySignal(areaId = storyAreaId(committed.state))
    )
    repository.save(storyState)
    val synchronized = syncLegacy(candidate, storyState, incrementTurn = false)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
'''
if "StorySignal(areaId = storyAreaId(committed.state))" not in facade:
    facade = replace_once(facade, validated_old, validated_new, "validated area story signal")

if "private fun storyAreaId(state: GameState): String?" not in facade:
    facade = replace_once(
        facade,
        '''  private fun stableItemId(name: String): String = name.lowercase()
''',
        '''  private fun storyAreaId(state: GameState): String? {
    val flags = state.world["flagsJson"] ?: return null
    return runCatching {
      JSONObject(flags).optJSONObject("exploration")?.optString("areaId")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
  }

  private fun stableItemId(name: String): String = name.lowercase()
''',
        "story area helper",
    )

if 'output.put("storyQuest", StoryStateJson.visible(storyQuestPlan, state.story))' not in facade:
    facade = replace_once(
        facade,
        '''    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("gameTime", JSONObject().apply {
''',
        '''    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("storyQuest", StoryStateJson.visible(storyQuestPlan, state.story))
    output.put("gameTime", JSONObject().apply {
''',
        "visible story quest projection",
    )

for marker in (
    "private val storyQuestPlan: StoryQuestPlan",
    "StoryQuestEngine(storyQuestPlan)",
    "AndroidStoryQuestPlan.load(appContext)",
    "escapedLevelId = levelId.takeIf { result.escaped }",
    "StorySignal(areaId = storyAreaId(committed.state))",
    'output.put("storyQuest", StoryStateJson.visible(storyQuestPlan, state.story))',
):
    if marker not in facade:
        raise RuntimeError("story quest facade integration missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")

if "val story: StoryState = StoryState.initial()" not in GAME_STATE.read_text(encoding="utf-8"):
    raise RuntimeError("StoryState not persisted in GameState")
if 'put("story", StoryStateJson.encode(state.story))' not in CODEC.read_text(encoding="utf-8"):
    raise RuntimeError("StoryState codec encoding missing")

print("Core StoryState/QuestState integrated for Level 0 -> Level 1: evidence-gated investigation, route milestones, one-signal-one-objective, Core-only progression and visible current objective projection.")
