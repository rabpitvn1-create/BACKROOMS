package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LevelInstanceTest {
  @Test fun levelOnePrototypeHasLockedSolvableEvidenceGraph() {
    val level = LevelOnePrototype.create("seed-a")
    val validation = BlueprintValidator.validate(level)

    assertTrue(validation.errors.joinToString(), validation.valid)
    assertTrue(level.escapeBlueprint.locked)
    assertEquals("parking_a", level.currentZoneId)
    assertEquals(3, level.escapeBlueprint.requiredFacts.size)
  }

  @Test fun levelInstanceJsonRoundTripPreservesHiddenBlueprintAndEvidence() {
    val original = LevelOnePrototype.create("seed-roundtrip")
    val decoded = LevelInstanceJson.decode(LevelInstanceJson.encode(original))

    assertEquals(original, decoded)
    assertEquals(original.escapeBlueprint, decoded.escapeBlueprint)
    assertEquals(original.evidence, decoded.evidence)
  }

  @Test fun repeatedSearchCannotCreateProgressWithoutWorldChange() {
    var state = LevelOneRuntime.install(GameState.initial())
    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state

    val first = LevelOneRuntime.apply(state, ActionKind.SEARCH, "Tìm kiếm")
    val second = LevelOneRuntime.apply(first.state, ActionKind.SEARCH, "Tìm kiếm")

    assertTrue(first.progressed)
    assertEquals(setOf("e-door-scratch"), first.evidenceIds)
    assertFalse(second.progressed)
    assertTrue(second.evidenceIds.isEmpty())
    assertEquals(first.state.levelInstance?.discoveredFacts, second.state.levelInstance?.discoveredFacts)
    assertEquals(first.state.levelInstance?.revision, second.state.levelInstance?.revision)
  }

  @Test fun repeatedExplorationCanRevealARealEnvironmentalPattern() {
    var state = LevelOneRuntime.install(GameState.initial())
    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state
    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state
    val third = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá")

    assertEquals("parking_loop", third.state.levelInstance?.currentZoneId)
    assertTrue("e-door-repeat" in third.evidenceIds)
    assertTrue(third.state.levelInstance?.evidence?.get("e-door-repeat")?.discovered == true)
  }

  @Test fun wrongExecuteDoesNotRerollOrMutateEscapeProgress() {
    var state = LevelOneRuntime.install(GameState.initial())
    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state

    val first = LevelOneRuntime.apply(state, ActionKind.EXECUTE, "Đập cửa số 14")
    val second = LevelOneRuntime.apply(first.state, ActionKind.EXECUTE, "Đập cửa số 14 lần nữa")

    assertFalse(first.progressed)
    assertFalse(second.progressed)
    assertEquals(emptyList<String>(), second.state.levelInstance?.completedActions)
    assertEquals(state.levelInstance, second.state.levelInstance)
  }

  @Test fun playerCanSolveLevelOneOnlyByExecutingTheLockedSequenceInValidLocations() {
    var state = LevelOneRuntime.install(GameState.initial())

    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state // parking loop
    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state // maintenance

    val cutPower = LevelOneRuntime.apply(state, ActionKind.EXECUTE, "Ngắt nguồn điện chính ở cầu dao")
    assertTrue(cutPower.progressed)
    assertEquals("off", cutPower.state.levelInstance?.environment?.get("power"))
    state = cutPower.state

    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state // return door 14
    val returnDoor = LevelOneRuntime.apply(state, ActionKind.EXECUTE, "Quay trở lại cửa số 14")
    assertTrue(returnDoor.progressed)
    state = returnDoor.state

    state = LevelOneRuntime.apply(state, ActionKind.EXPLORE, "Khám phá").state // blackout hall
    val againstHum = LevelOneRuntime.apply(state, ActionKind.EXECUTE, "Đi ngược hướng tiếng máy")
    assertTrue(againstHum.progressed)
    assertEquals("service_elevator", againstHum.state.levelInstance?.currentZoneId)
    state = againstHum.state

    val escape = LevelOneRuntime.apply(state, ActionKind.EXECUTE, "Mở và đi vào thang máy dịch vụ")
    assertTrue(escape.progressed)
    assertTrue(escape.escaped)
    assertTrue(escape.state.levelInstance?.completed == true)
    assertEquals(
      listOf("cut_power", "return_door_14", "follow_against_hum", "enter_service_elevator"),
      escape.state.levelInstance?.completedActions
    )
  }
}
