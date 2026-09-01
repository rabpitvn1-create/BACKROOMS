package com.rabpit.backroom.core

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
