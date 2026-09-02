package com.rabpit.backroom.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LevelZeroDefinitionTest {
  @Test fun packagedLevelZeroDefinitionIsCanonBoundedAndValid() {
    val definition = loadLevelZero()
    val validation = LevelDefinitionValidator.validate(definition)

    assertTrue(validation.errors.joinToString("\n"), validation.valid)
    assertEquals("0", definition.id)
    assertEquals("The Lobby", definition.name)
    assertTrue(definition.canonProfile.environmentTags.contains("yellow_room_network"))
    assertTrue(definition.canonProfile.allowedPhenomena.containsAll(setOf(
      "fluorescent_pressure", "memory_rooms", "layout_resistance"
    )))
    assertTrue("backrooms_confirmed_conscious" in definition.canonProfile.forbiddenClaims)
    assertTrue("level_confirmed_intentionally_opposes_player" in definition.canonProfile.forbiddenClaims)
    assertEquals("cannot_be_forced_only_followed_when_present", definition.canonProfile.metadata["transitionRule"])
    assertTrue(definition.generationConstraints.proceduralTopology)
    assertTrue(definition.generationConstraints.proceduralEvidencePlacement)
    assertTrue(definition.generationConstraints.proceduralEscapeBlueprint)

    val finish = definition.actions.getValue("continue_until_geometry_changes")
    assertFalse(finish.matchGroups.flatten().any { it.contains("level 1", ignoreCase = true) })
    assertFalse(finish.semanticDescriptions.any { it.contains("Level 1", ignoreCase = true) })
    assertFalse(finish.reply.orEmpty().contains("Level 1", ignoreCase = true))
    assertFalse("level1_transition" in definition.zones.getValue("concrete_drift").tags)
  }

  @Test fun levelZeroExploreRepliesDescribePlacesNaturallyInsteadOfShowingZoneLabels() {
    val definition = loadLevelZero()
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "0", "level-zero-vietnamese-prose")

    val firstExplore = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá")
    state = firstExplore.state
    assertEquals("Kai đi sâu hơn vào khu hành lang dưới ánh đèn huỳnh quang.", firstExplore.reply)

    val secondExplore = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá")
    assertTrue(secondExplore.reply.startsWith("Kai đi sâu hơn vào căn phòng có một tấm trần lệch."))
    assertTrue(secondExplore.reply.contains("người sống sót"))
    assertFalse(secondExplore.reply.contains("Fluorescent Loop"))
    assertFalse(secondExplore.reply.contains("Relocated Marker Room"))
    assertFalse(secondExplore.reply.contains("Vòng lặp huỳnh quang"))
    assertFalse(secondExplore.reply.contains("Phòng dấu mốc dịch chuyển"))
    assertFalse(secondExplore.reply.contains("survivor", ignoreCase = true))
  }

  @Test fun levelZeroPresentationLeadsToEpsilonWithoutParkingArchitecture() {
    val definition = loadLevelZero()
    assertEquals("epsilon", definition.metadata["campaignExitTarget"])
    val visible = definition.zones.values.map { it.name } + definition.replies.values +
      definition.actions.values.mapNotNull { it.reply }
    visible.forEach { assertFalse(it, LevelNarrativePolicy.contradictsArea("0", it)) }
    assertFalse(definition.replies.getValue("evidence:e-marker-repeat").contains("không đáng tin"))
    assertFalse(definition.replies.getValue("evidence:e-hum-survivor").contains("đừng quay"))
  }

  @Test fun continuedFixtureSaveRefreshesPresentationWithoutResettingProgress() {
    val definition = loadLevelZero()
    val registry = LevelRegistry.from(listOf(definition))
    val installed = GenericLevelRuntime.install(GameState.initial(), registry, "0", "old-save")
    val initial = installed.levelInstance!!
    val saved = initial.copy(
      currentZoneId = "concrete_drift",
      zones = initial.zones + ("concrete_drift" to initial.zones.getValue("concrete_drift").copy(name = "bãi đỗ xe bê tông")),
      replies = initial.replies + ("evidence:e-hum-survivor" to "đừng quay theo dấu cũ"),
      evidence = initial.evidence.mapValues { (_, item) -> item.copy(discovered = true, discoveredAtRevision = 3) },
      discoveredFacts = initial.escapeBlueprint.requiredFacts,
      completedActions = listOf("follow_transition_signs"),
      revision = 7
    )
    val original = installed.copy(levelInstance = saved)
    val restored = GameStateCodec.decode(GameStateCodec.encode(original))
    val refreshedState = GenericLevelRuntime.install(restored, registry, "0", "ignored")
    val refreshed = refreshedState.levelInstance!!
    assertEquals(saved.runSeed, refreshed.runSeed)
    assertEquals(saved.generationId, refreshed.generationId)
    assertEquals(saved.currentZoneId, refreshed.currentZoneId)
    assertEquals(saved.evidence, refreshed.evidence)
    assertEquals(saved.discoveredFacts, refreshed.discoveredFacts)
    assertEquals(saved.completedActions, refreshed.completedActions)
    assertEquals(saved.revision, refreshed.revision)
    assertEquals(original.inventories, refreshedState.inventories)
    assertFalse(refreshedState.world.getValue("location").contains("bê tông"))
    assertEquals(definition.replies, refreshed.replies)
    assertEquals(refreshedState, GenericLevelRuntime.install(refreshedState, registry, "0", "ignored"))
    val finish = GenericLevelRuntime.apply(refreshedState, registry, ActionKind.EXECUTE, "tiếp tục cho tới khi kiến trúc đổi hẳn")
    assertTrue(finish.escaped)
  }

  @Test fun deterministicFallbackRequiresClueCollectionThenExecuteToLeaveLevelZero() {
    val definition = loadLevelZero()
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "0", "level-zero-fixture-test")

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    val firstSearch = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm")
    state = firstSearch.state
    assertTrue(firstSearch.progressed)
    assertFalse("MARKERS_ARE_UNRELIABLE" in state.levelInstance!!.discoveredFacts)

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    assertFalse("HUM_FADES_ALONG_TRANSITION" in state.levelInstance!!.discoveredFacts)

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    assertTrue("MARKERS_ARE_UNRELIABLE" in state.levelInstance!!.discoveredFacts)

    state = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá").state
    assertTrue("HUM_FADES_ALONG_TRANSITION" in state.levelInstance!!.discoveredFacts)
    assertFalse("CONCRETE_DRIFT_IS_TRANSITION" in state.levelInstance!!.discoveredFacts)

    val premature = GenericLevelRuntime.apply(
      state,
      registry,
      ActionKind.EXECUTE,
      "đi theo hành lang có đèn rung và tiếng ù chồng lên nhau"
    )
    assertFalse(premature.progressed)

    state = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm").state
    assertTrue("CONCRETE_DRIFT_IS_TRANSITION" in state.levelInstance!!.discoveredFacts)

    val follow = GenericLevelRuntime.apply(
      state,
      registry,
      ActionKind.EXECUTE,
      "đi theo hành lang có đèn rung và tiếng ù chồng lên nhau"
    )
    assertTrue(follow.progressed)
    assertFalse(follow.escaped)
    assertEquals("concrete_drift", follow.state.levelInstance!!.currentZoneId)

    val finish = GenericLevelRuntime.apply(
      follow.state,
      registry,
      ActionKind.EXECUTE,
      "tiếp tục cho tới khi kiến trúc đổi hẳn"
    )
    assertTrue(finish.progressed)
    assertTrue(finish.escaped)
    assertEquals("0", finish.state.levelInstance!!.levelId)
    assertFalse(finish.reply.contains("Level 1", ignoreCase = true))
  }

  private fun loadLevelZero(): LevelDefinition {
    val candidates = listOf(
      File("src/main/assets/levels/0.json"),
      File("app/src/main/assets/levels/0.json"),
      File("android-apk/app/src/main/assets/levels/0.json")
    )
    val file = candidates.firstOrNull(File::isFile)
      ?: error("Cannot locate packaged levels/0.json from ${File(".").absolutePath}")
    return LevelDefinitionJson.decode(file.readText(Charsets.UTF_8))
  }
}
