package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryCheckpointCoreTest {
  @Test fun newGameStartsAtFirstOfTwentyLevelZeroCheckpoints() {
    val initial = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    val first = StoryCheckpointCore.current(initial)!!
    assertEquals("CP-L00-01", first.id)
    assertEquals(0, first.level)
    assertEquals(1, first.index)
    assertEquals(20, first.total)
    assertFalse(first.isLast)
    assertEquals("CP-L00-02", first.nextAddress()!!.id)
  }

  @Test fun ordinaryTurnsAndSaveLoadCannotAdvanceOrResetCheckpoint() {
    val initial = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    val advanced = CoreClientProjection.advance(CoreClientProjection.advance(initial))
    val restored = GameStateCodec.decode(GameStateCodec.encode(advanced))
    val again = CoreClientProjection.initialize(restored)
    assertEquals(3, CoreClientProjection.turnIndex(again))
    assertEquals("CP-L00-01", StoryCheckpointCore.current(again)!!.id)
    assertEquals(initial.world["story.checkpoint"], again.world["story.checkpoint"])
  }

  @Test fun sublevelsDoNotCreateAnotherNarrativeCheckpoint() {
    val initial = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    val movedNode = initial.copy(world = initial.world +
      ("levelJson" to JSONObject().put("id", "0.1").put("number", 0).toString()))
    assertEquals("CP-L00-01", StoryCheckpointCore.current(
      StoryCheckpointCore.seedLevelZero(movedNode))!!.id)
  }

  @Test fun higherLevelsHaveNoInventedCheckpointCount() {
    val levelOne = GameState.initial().copy(world = mapOf(
      "levelJson" to JSONObject().put("id", "1").put("number", 1).toString()))
    assertNull(StoryCheckpointCore.current(StoryCheckpointCore.seedLevelZero(levelOne)))
    val planned = StoryCheckpointCore.addressForLevel(1, 1, 12)
    assertEquals("CP-L01-01", planned.id)
    assertEquals(12, planned.total)
  }

  @Test fun lastCheckpointHasNoImplicitNextCheckpoint() {
    val last = StoryCheckpointCore.Position(0, 20, 20)
    assertEquals("CP-L00-20", last.id)
    assertTrue(last.isLast)
    assertNull(last.nextAddress())
  }

  @Test fun invalidPositionsAreRejectedInsteadOfBeingClampedOrReset() {
    assertThrows(IllegalArgumentException::class.java) { StoryCheckpointCore.Position(0, 0, 20) }
    assertThrows(IllegalArgumentException::class.java) { StoryCheckpointCore.Position(0, 21, 20) }
    assertThrows(IllegalArgumentException::class.java) { StoryCheckpointCore.Position(0, 1, 19) }
    val bad = GameState.initial().copy(world = mapOf(
      "story.checkpoint" to "{\"level\":0,\"index\":22,\"total\":20}"))
    assertThrows(IllegalArgumentException::class.java) { StoryCheckpointCore.seedLevelZero(bad) }
  }
}
