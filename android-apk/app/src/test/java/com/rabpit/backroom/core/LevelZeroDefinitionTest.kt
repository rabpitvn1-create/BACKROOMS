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
  }

  @Test fun levelZeroExploreRepliesDoNotMixEnglishZoneNamesIntoVietnameseProse() {
    val definition = loadLevelZero()
    val registry = LevelRegistry.from(listOf(definition))
    var state = GenericLevelRuntime.install(GameState.initial(), registry, "0", "level-zero-vietnamese-prose")

    val firstExplore = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá")
    state = firstExplore.state
    assertEquals("Kai đi sâu hơn vào Vòng lặp huỳnh quang.", firstExplore.reply)

    val secondExplore = GenericLevelRuntime.apply(state, registry, ActionKind.EXPLORE, "Khám phá")
    assertTrue(secondExplore.reply.startsWith("Kai đi sâu hơn vào Phòng dấu mốc dịch chuyển."))
    assertTrue(secondExplore.reply.contains("người sống sót"))
    assertFalse(secondExplore.reply.contains("Fluorescent Loop"))
    assertFalse(secondExplore.reply.contains("Relocated Marker Room"))
    assertFalse(secondExplore.reply.contains("survivor", ignoreCase = true))
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
      "đi theo hành lang bê tông nơi tiếng ù yếu"
    )
    assertFalse(premature.progressed)

    state = GenericLevelRuntime.apply(state, registry, ActionKind.SEARCH, "Tìm kiếm").state
    assertTrue("CONCRETE_DRIFT_IS_TRANSITION" in state.levelInstance!!.discoveredFacts)

    val follow = GenericLevelRuntime.apply(
      state,
      registry,
      ActionKind.EXECUTE,
      "đi theo hành lang bê tông nơi tiếng ù yếu"
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
