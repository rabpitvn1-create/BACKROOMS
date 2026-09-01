from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
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


# ---------------------------------------------------------------------------
# Validate the authored Level 0 -> Level 1 quest plan before wiring runtime.
# ---------------------------------------------------------------------------
quest = json.loads(QUESTS.read_text(encoding="utf-8"))
story = json.loads(STORY.read_text(encoding="utf-8"))
if quest.get("planId") != "QUEST_PLAN_LEVEL0_TO_LEVEL1_R01":
    raise RuntimeError("level01_quest_plan_id_invalid")
if quest.get("storyId") != story.get("storyId") or quest.get("campaignId") != story.get("campaignId"):
    raise RuntimeError("level01_quest_plan_story_identity_mismatch")
locks = quest.get("locks") or {}
for key in (
    "coreOwnsProgression",
    "geminiCannotAdvanceQuest",
    "liteRTCannotAdvanceQuest",
    "oneSignalCompletesAtMostOneObjective",
    "luciaEncounterIsNotQuestObjective",
):
    if locks.get(key) is not True:
        raise RuntimeError("level01_quest_lock_missing:" + key)

chapter = quest.get("chapter") or {}
act = chapter.get("act") or {}
quests = act.get("quests") or []
objective_rows = []
for q in quests:
    for objective in q.get("objectives") or []:
        objective_rows.append((q.get("id"), objective.get("id"), objective.get("completion") or {}))
expected_objectives = [
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_01_VERIFY_LAYOUT_ANOMALY", "EVIDENCE_ANY"),
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_02_IDENTIFY_TRANSITION", "EVIDENCE_ANY"),
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_03_CONFIRM_HUM_DIRECTION", "EVIDENCE_ANY"),
    ("QUEST_01_READ_LEVEL_ZERO", "OBJ_04_LEAVE_LEVEL_ZERO_CORE", "LEVEL_ESCAPED"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_05_REACH_BIOHAZARD_REGION", "ENTER_AREA"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_06_REACH_DEEP_REGION", "ENTER_AREA"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_07_REACH_RED_ROOMS", "ENTER_AREA"),
    ("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", "OBJ_08_REACH_LEVEL_ONE", "ENTER_AREA"),
]
actual_objectives = [(q, objective, completion.get("type")) for q, objective, completion in objective_rows]
if actual_objectives != expected_objectives:
    raise RuntimeError("level01_quest_objective_order_invalid")
if any("lucia" in json.dumps(row, ensure_ascii=False).lower() for row in objective_rows):
    raise RuntimeError("lucia_must_not_be_quest_objective")

story_text = STORY.read_text(encoding="utf-8")
main_story_patch_text = MAIN_STORY_PATCH.read_text(encoding="utf-8")
companion_patch_text = COMPANION_PATCH.read_text(encoding="utf-8")
for obsolete in ("Hứa Thuý Lan", "MISSION_YEAR = 2267", "PRIVATE_TARGET =", "đội Black Blood"):
    if obsolete in story_text or obsolete in main_story_patch_text or obsolete in companion_patch_text:
        raise RuntimeError("obsolete_story_canon_source_survived:" + obsolete)


# ---------------------------------------------------------------------------
# Persist typed StoryState inside authoritative GameState. No migration branch is
# added for this feature; fresh GameState uses StoryState.initial().
# ---------------------------------------------------------------------------
game_state = GAME_STATE.read_text(encoding="utf-8")
if "val story: StoryState" not in game_state:
    game_state, count = re.subn(
        r'(\n  val saveVersion: Int = CURRENT_SAVE_VERSION,)',
        '\n  val story: StoryState = StoryState.initial(),\\1',
        game_state,
        count=1,
    )
    if count != 1:
        raise RuntimeError("game_state_story_field_anchor_missing")
GAME_STATE.write_text(game_state, encoding="utf-8")

codec = CODEC.read_text(encoding="utf-8")
if 'put("story", StoryStateJson.encode(state.story))' not in codec:
    codec = replace_once(
        codec,
        '    put("world", stringMap(state.world))\n    put("metadata", stringMap(state.metadata))\n',
        '    put("world", stringMap(state.world))\n    put("story", StoryStateJson.encode(state.story))\n    put("metadata", stringMap(state.metadata))\n',
        "encode StoryState",
    )

    start = codec.find("  private fun decodeCurrent(root: JSONObject): GameState {")
    end = codec.find("\n  private fun character(", start)
    if start < 0 or end < 0:
        raise RuntimeError("decodeCurrent block missing")
    block = codec[start:end]
    if "story =" not in block:
        block, count = re.subn(
            r'(\n      saveVersion = CURRENT_SAVE_VERSION,)',
            '\n      story = root.optJSONObject("story")?.let(StoryStateJson::decode) ?: StoryState.initial(),\\1',
            block,
            count=1,
        )
        if count != 1:
            raise RuntimeError("decodeCurrent StoryState anchor missing")
        codec = codec[:start] + block + codec[end:]
CODEC.write_text(codec, encoding="utf-8")


# ---------------------------------------------------------------------------
# GameCoreFacade owns the plan and is the only component that advances quest
# state. Gemini/LiteRT can never submit StoryState commands.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
if "private val storyQuestPlan: StoryQuestPlan" not in facade:
    facade = replace_once(
        facade,
        '''  private val levelRegistry: LevelRegistry,
  private val backroomsDirector: BackroomsDirector
) : AutoCloseable {''',
        '''  private val levelRegistry: LevelRegistry,
  private val backroomsDirector: BackroomsDirector,
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
        BackroomsDirector.liteRT(appContext)
''',
        '''        AndroidLevelRegistry.load(appContext),
        BackroomsDirector.liteRT(appContext),
        AndroidStoryQuestPlan.load(appContext)
''',
        "story quest plan app factory",
    )

# Registered Level 0 evidence/escape becomes deterministic quest signals.
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
    facade = replace_once(facade, registered_old, registered_new, "registered Level StoryState signal")

# Validated legacy transitions carry authoritative areaId inside committed flagsJson.
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
    facade = replace_once(facade, validated_old, validated_new, "validated area StoryState signal")

if "private fun storyAreaId(state: GameState): String?" not in facade:
    helper_anchor = '''  private fun stableItemId(name: String): String = name.lowercase()
'''
    helper = '''  private fun storyAreaId(state: GameState): String? {
    val flags = state.world["flagsJson"] ?: return null
    return runCatching {
      JSONObject(flags).optJSONObject("exploration")?.optString("areaId")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
  }

'''
    facade = replace_once(facade, helper_anchor, helper + helper_anchor, "story area helper")

# Every Core -> legacy projection overwrites any model-provided quest field.
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
        "visible quest projection",
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


# ---------------------------------------------------------------------------
# Focused regressions: evidence objectives, no skip, route milestones, completion,
# and persisted StoryState round-trip.
# ---------------------------------------------------------------------------
(TESTS / "StoryQuestStateTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryQuestStateTest {
  private val plan = StoryQuestPlan.parse(
    """{
      "planId":"QUEST_PLAN_LEVEL0_TO_LEVEL1_R01",
      "storyId":"MAIN_LEVEL0_TO_LEVEL1_R01",
      "campaignId":"BACKROOMS_FANDOM_LEVELS_0_6_R01",
      "chapter":{"id":"CHAPTER_01_ASYNC_INVESTIGATION","title":"Điều tra Async","act":{"id":"ACT_01_SEPARATED_IN_BACKROOMS","title":"Bị phân tán trong Backrooms","quests":[
        {"id":"QUEST_01_READ_LEVEL_ZERO","title":"Đọc Level 0","objectives":[
          {"id":"OBJ_01_VERIFY_LAYOUT_ANOMALY","title":"A","completion":{"type":"EVIDENCE_ANY","ids":["e-marker-search","e-marker-repeat"]}},
          {"id":"OBJ_02_IDENTIFY_TRANSITION","title":"B","completion":{"type":"EVIDENCE_ANY","ids":["e-transition-search","e-transition-anomaly"]}},
          {"id":"OBJ_03_CONFIRM_HUM_DIRECTION","title":"C","completion":{"type":"EVIDENCE_ANY","ids":["e-hum-survivor","e-hum-anomaly"]}},
          {"id":"OBJ_04_LEAVE_LEVEL_ZERO_CORE","title":"D","completion":{"type":"LEVEL_ESCAPED","levelId":"0"}}
        ]},
        {"id":"QUEST_02_CROSS_LEVEL_ZERO_REGIONS","title":"Đi xuyên Level 0","objectives":[
          {"id":"OBJ_05_REACH_BIOHAZARD_REGION","title":"E","completion":{"type":"ENTER_AREA","areaId":"0.41"}},
          {"id":"OBJ_06_REACH_DEEP_REGION","title":"F","completion":{"type":"ENTER_AREA","areaId":"0.99"}},
          {"id":"OBJ_07_REACH_RED_ROOMS","title":"G","completion":{"type":"ENTER_AREA","areaId":"Red Rooms"}},
          {"id":"OBJ_08_REACH_LEVEL_ONE","title":"H","completion":{"type":"ENTER_AREA","areaId":"1"}}
        ]}
      ]}}
    }"""
  )
  private val engine = StoryQuestEngine(plan)

  @Test fun evidenceAndEscapeAdvanceOnlyTheCurrentObjective() {
    var state = GameState.initial()
    assertEquals("OBJ_01_VERIFY_LAYOUT_ANOMALY", state.story.objectiveId)

    state = engine.applySignal(state, StorySignal(areaId = "0", evidenceIds = setOf("e-marker-search", "e-transition-search")))
    assertEquals("OBJ_02_IDENTIFY_TRANSITION", state.story.objectiveId)
    assertEquals(setOf("OBJ_01_VERIFY_LAYOUT_ANOMALY"), state.story.completedObjectiveIds)

    state = engine.applySignal(state, StorySignal(areaId = "0", evidenceIds = setOf("e-transition-search")))
    assertEquals("OBJ_03_CONFIRM_HUM_DIRECTION", state.story.objectiveId)
    state = engine.applySignal(state, StorySignal(areaId = "0", evidenceIds = setOf("e-hum-anomaly")))
    assertEquals("OBJ_04_LEAVE_LEVEL_ZERO_CORE", state.story.objectiveId)
    state = engine.applySignal(state, StorySignal(areaId = "0", escapedLevelId = "0"))
    assertEquals("QUEST_02_CROSS_LEVEL_ZERO_REGIONS", state.story.questId)
    assertEquals("OBJ_05_REACH_BIOHAZARD_REGION", state.story.objectiveId)
    assertTrue("QUEST_01_READ_LEVEL_ZERO" in state.story.completedQuestIds)
  }

  @Test fun futureAreaCannotSkipCurrentMilestone() {
    var state = GameState.initial().copy(story = StoryState.initial().copy(
      questId = "QUEST_02_CROSS_LEVEL_ZERO_REGIONS",
      objectiveId = "OBJ_05_REACH_BIOHAZARD_REGION",
      completedQuestIds = setOf("QUEST_01_READ_LEVEL_ZERO")
    ))
    state = engine.applySignal(state, StorySignal(areaId = "0.99"))
    assertEquals("OBJ_05_REACH_BIOHAZARD_REGION", state.story.objectiveId)
    assertFalse("OBJ_06_REACH_DEEP_REGION" in state.story.completedObjectiveIds)
  }

  @Test fun enteringMilestonesCompletesArcAtLevelOne() {
    var state = GameState.initial().copy(story = StoryState.initial().copy(
      questId = "QUEST_02_CROSS_LEVEL_ZERO_REGIONS",
      objectiveId = "OBJ_05_REACH_BIOHAZARD_REGION",
      completedQuestIds = setOf("QUEST_01_READ_LEVEL_ZERO")
    ))
    state = engine.applySignal(state, StorySignal(areaId = "0.41"))
    assertEquals("OBJ_06_REACH_DEEP_REGION", state.story.objectiveId)
    state = engine.applySignal(state, StorySignal(areaId = "0.99"))
    assertEquals("OBJ_07_REACH_RED_ROOMS", state.story.objectiveId)
    state = engine.applySignal(state, StorySignal(areaId = "Red Rooms"))
    assertEquals("OBJ_08_REACH_LEVEL_ONE", state.story.objectiveId)
    state = engine.applySignal(state, StorySignal(areaId = "1"))
    assertEquals(StoryProgressStatus.COMPLETED, state.story.status)
    assertEquals(null, state.story.objectiveId)
    assertTrue("QUEST_02_CROSS_LEVEL_ZERO_REGIONS" in state.story.completedQuestIds)
    assertTrue("CHAPTER_01_ASYNC_INVESTIGATION" in state.story.completedChapterIds)
  }

  @Test fun storyStateRoundTripsThroughGameStateCodec() {
    val state = GameState.initial().copy(story = StoryState.initial().copy(
      objectiveId = "OBJ_02_IDENTIFY_TRANSITION",
      completedObjectiveIds = setOf("OBJ_01_VERIFY_LAYOUT_ANOMALY"),
      lastObservedAreaId = "0",
      revision = 1
    ))
    val restored = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.story, restored.story)
  }
}
''', encoding="utf-8")

# Final source/runtime audit.
for path in (GAME_STATE, CODEC, FACADE):
    if not path.read_text(encoding="utf-8").strip():
        raise RuntimeError("story quest integration produced empty file: " + path.name)
if "val story: StoryState = StoryState.initial()" not in GAME_STATE.read_text(encoding="utf-8"):
    raise RuntimeError("StoryState not persisted in GameState")
if 'put("story", StoryStateJson.encode(state.story))' not in CODEC.read_text(encoding="utf-8"):
    raise RuntimeError("StoryState codec encoding missing")

print("Core StoryState/QuestState integrated for Level 0 -> Level 1: evidence-gated Level 0 investigation, route milestones, one-signal-one-objective, Core-only progression, persisted current objective projection.")
