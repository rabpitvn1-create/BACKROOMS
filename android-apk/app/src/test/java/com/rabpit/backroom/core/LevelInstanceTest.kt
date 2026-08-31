package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LevelInstanceTest {
  @Test fun genericDefinitionHasLockedSolvableEvidenceGraph() {
    val definition = levelOneDefinition()
    val validation = LevelDefinitionValidator.validate(definition)
    val level = GenericLevelGenerator.generate(definition, "seed-a")

    assertTrue(validation.errors.joinToString(), validation.valid)
    assertTrue(level.escapeBlueprint.locked)
    assertEquals("parking_a", level.currentZoneId)
    assertEquals(3, level.escapeBlueprint.requiredFacts.size)
  }

  @Test fun levelDefinitionJsonRoundTripPreservesExactStringIds() {
    val original = levelOneDefinition().copy(id = "1.10", parentId = "1")
    val decoded = LevelDefinitionJson.decode(LevelDefinitionJson.encode(original).toString())

    assertEquals(original, decoded)
    assertEquals("1.10", decoded.id)
    assertEquals("1", decoded.parentId)
  }

  @Test fun registryAcceptsArbitraryFutureLevelWithoutRuntimeCodeChanges() {
    val arbitrary = levelOneDefinition().copy(id = "742.13", parentId = "742", name = "Future Sublevel")
    val registry = LevelRegistry.from(listOf(arbitrary))
    val state = GenericLevelRuntime.install(GameState.initial(), registry, "742.13", "future-seed")

    assertTrue(registry.contains("742.13"))
    assertEquals(setOf("742"), registry.unresolvedParents())
    assertEquals("742.13", state.levelInstance?.levelId)
    assertEquals("742.13:future-seed", state.levelInstance?.generationId)
    assertTrue(state.world["location"].orEmpty().startsWith("Level 742.13 /"))
  }

  @Test fun registryLoaderAutoRegistersEveryDefinitionDocument() {
    val main = levelOneDefinition().copy(id = "347", name = "Level 347")
    val sub = levelOneDefinition().copy(id = "347.2", parentId = "347", name = "Level 347.2")
    val registry = LevelRegistryLoader.load(
      listOf(
        LevelDefinitionDocument("levels/347/level.json", LevelDefinitionJson.encode(main).toString()),
        LevelDefinitionDocument("levels/347/sublevels/347.2.json", LevelDefinitionJson.encode(sub).toString())
      )
    )

    assertEquals(listOf("347", "347.2"), registry.ids())
    assertEquals(listOf("347.2"), registry.childrenOf("347").map { it.id })
    assertTrue(registry.unresolvedParents().isEmpty())
  }

  @Test fun levelInstanceJsonRoundTripPreservesHiddenBlueprintAndEvidence() {
    val original = GenericLevelGenerator.generate(levelOneDefinition(), "seed-roundtrip")
    val decoded = LevelInstanceJson.decode(LevelInstanceJson.encode(original))

    assertEquals(original, decoded)
    assertEquals(original.escapeBlueprint, decoded.escapeBlueprint)
    assertEquals(original.evidence, decoded.evidence)
  }

  @Test fun repeatedSearchCannotCreateProgressWithoutWorldChange() {
    val registry = registry()
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "1", "search-seed")
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state

    val first = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm")
    val second = GenericLevelRuntime.apply(first.state, registry, ActionKind.SEARCH, "Tìm kiếm")

    assertTrue(first.progressed)
    assertEquals(setOf("e-door-scratch"), first.evidenceIds)
    assertFalse(second.progressed)
    assertTrue(second.evidenceIds.isEmpty())
    assertEquals(first.state.levelInstance?.discoveredFacts, second.state.levelInstance?.discoveredFacts)
    assertEquals(first.state.levelInstance?.revision, second.state.levelInstance?.revision)
  }

  @Test fun repeatedExplorationCanRevealARealEnvironmentalPattern() {
    val registry = registry()
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "1", "explore-seed")
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    val third = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá")

    assertEquals("parking_loop", third.state.levelInstance?.currentZoneId)
    assertTrue("e-door-repeat" in third.evidenceIds)
    assertTrue(third.state.levelInstance?.evidence?.get("e-door-repeat")?.discovered == true)
  }

  @Test fun wrongExecuteDoesNotRerollOrMutateEscapeProgress() {
    val registry = registry()
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "1", "execute-seed")
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state

    val first = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "Đập cửa số 14")
    val second = GenericLevelRuntime.apply(first.state, registry, ActionKind.EXECUTE, "Đập cửa số 14 lần nữa")

    assertFalse(first.progressed)
    assertFalse(second.progressed)
    assertEquals(emptyList<String>(), second.state.levelInstance?.completedActions)
    assertEquals(state.levelInstance, second.state.levelInstance)
  }

  @Test fun playerCanSolveFixtureThroughGenericRuntimeOnly() {
    val registry = registry()
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "1", "solve-seed")

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state

    val cutPower = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "Ngắt nguồn điện chính ở cầu dao")
    assertTrue(cutPower.progressed)
    assertEquals("off", cutPower.state.levelInstance?.environment?.get("power"))
    state = cutPower.state

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    val returnDoor = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "Quay trở lại cửa số 14")
    assertTrue(returnDoor.progressed)
    state = returnDoor.state

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    val againstHum = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "Đi ngược hướng tiếng máy")
    assertTrue(againstHum.progressed)
    assertEquals("service_elevator", againstHum.state.levelInstance?.currentZoneId)
    state = againstHum.state

    val escape = GenericLevelRuntime.apply(state, registry, ActionKind.EXECUTE, "Mở và đi vào thang máy dịch vụ")
    assertTrue(escape.progressed)
    assertTrue(escape.escaped)
    assertTrue(escape.state.levelInstance?.completed == true)
    assertEquals(
      listOf("cut_power", "return_door_14", "follow_against_hum", "enter_service_elevator"),
      escape.state.levelInstance?.completedActions
    )
  }

  private fun registry(): LevelRegistry = LevelRegistry.from(listOf(levelOneDefinition()))

  private fun levelOneDefinition(): LevelDefinition {
    val factPowerOff = "POWER_OFF_REQUIRED"
    val factDoorLoop = "DOOR_14_LOOP"
    val factReverseHum = "REVERSE_HUM_ROUTE"
    val zones = linkedMapOf(
      "parking_a" to ZoneState("parking_a", "Parking A", setOf("parking_loop"), setOf("entry"), mapOf("material" to "concrete")),
      "parking_loop" to ZoneState("parking_loop", "Parking Loop 14", setOf("maintenance", "blackout_hall"), setOf("loop"), mapOf("door" to "14")),
      "maintenance" to ZoneState("maintenance", "Maintenance Hall", setOf("parking_loop", "blackout_hall"), setOf("utility"), mapOf("breaker" to "main")),
      "blackout_hall" to ZoneState("blackout_hall", "Blackout Hall", setOf("service_elevator"), setOf("dark"), mapOf("machineHum" to "east")),
      "service_elevator" to ZoneState("service_elevator", "Service Elevator", emptySet(), setOf("escape"), mapOf("doorState" to "sealed"))
    )
    val evidence = listOf(
      EvidenceState("e-door-repeat", setOf(factDoorLoop), setOf(EvidenceSource.ENVIRONMENT), "parking_loop", setOf("visit:parking_loop:2")),
      EvidenceState("e-door-scratch", setOf(factDoorLoop), setOf(EvidenceSource.SEARCH), "parking_loop"),
      EvidenceState("e-power-panel", setOf(factPowerOff), setOf(EvidenceSource.SEARCH), "maintenance"),
      EvidenceState("e-power-survivor", setOf(factPowerOff), setOf(EvidenceSource.SURVIVOR), "maintenance", setOf("visit:maintenance:1")),
      EvidenceState("e-hum-anomaly", setOf(factReverseHum), setOf(EvidenceSource.ANOMALY), "blackout_hall", setOf("env:power=off")),
      EvidenceState("e-hum-survivor", setOf(factReverseHum), setOf(EvidenceSource.SURVIVOR), "blackout_hall", setOf("visit:blackout_hall:1"))
    ).associateBy { it.id }
    val actions = listOf(
      LevelActionRule(
        "cut_power",
        listOf(setOf("tắt", "ngắt", "cắt"), setOf("điện", "nguồn", "cầu dao")),
        setOf("zone:maintenance"),
        listOf(LevelEffect(LevelEffectType.SET_ENVIRONMENT, key = "power", value = "off")),
        "Nguồn điện chính tắt."
      ),
      LevelActionRule(
        "return_door_14",
        listOf(setOf("14"), setOf("quay", "trở", "trở lại")),
        setOf("zone:parking_loop", "env:power=off"),
        reply = "Kai quay lại cửa 14."
      ),
      LevelActionRule(
        "follow_against_hum",
        listOf(setOf("ngược"), setOf("tiếng", "máy", "âm")),
        setOf("zone:blackout_hall", "env:power=off"),
        listOf(LevelEffect(LevelEffectType.MOVE_TO_ZONE, zoneId = "service_elevator")),
        "Kai đi ngược hướng tiếng máy."
      ),
      LevelActionRule(
        "enter_service_elevator",
        listOf(setOf("thang máy", "elevator"), setOf("vào", "mở", "đi")),
        setOf("zone:service_elevator"),
        listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)),
        "Lối chuyển Level mở."
      )
    ).associateBy { it.id }

    return LevelDefinition(
      id = "1",
      name = "Parking Zone",
      initialZoneId = "parking_a",
      zones = zones,
      landmarks = mapOf("door14" to "A scratched service door marked 14"),
      environment = mapOf("power" to "on"),
      escapeBlueprint = EscapeBlueprintState(
        solutionId = "level1-service-elevator",
        requiredFacts = setOf(factPowerOff, factDoorLoop, factReverseHum),
        requiredActions = listOf("cut_power", "return_door_14", "follow_against_hum", "enter_service_elevator"),
        locked = true
      ),
      evidence = evidence,
      npcKnowledge = mapOf(
        "survivor-maintenance" to setOf("e-power-survivor"),
        "survivor-blackout" to setOf("e-hum-survivor")
      ),
      exploreRoute = listOf("parking_loop", "maintenance", "parking_loop", "blackout_hall"),
      actions = actions,
      replies = mapOf(
        "evidence:e-door-repeat" to "Cửa số 14 xuất hiện lại.",
        "evidence:e-door-scratch" to "Vết xước xác nhận đây là cùng một cửa."
      )
    )
  }
}
