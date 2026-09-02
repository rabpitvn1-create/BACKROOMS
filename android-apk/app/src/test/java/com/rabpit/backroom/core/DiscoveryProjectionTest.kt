package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class DiscoveryProjectionTest {
  private fun definition(): LevelDefinition {
    val evidence = mapOf(
      "surface" to EvidenceState(
        id = "surface",
        supports = setOf("hidden.escape.fact"),
        sources = setOf(EvidenceSource.SURVIVOR, EvidenceSource.ENVIRONMENT),
        discovered = true,
        discoverConditions = setOf("fact:hidden.escape.fact")
      ),
      "future" to EvidenceState(
        id = "future",
        supports = setOf("future.secret"),
        sources = setOf(EvidenceSource.SEARCH),
        discovered = false
      )
    )
    return LevelDefinition(
      id = "test",
      name = "Test",
      initialZoneId = "z",
      zones = mapOf("z" to ZoneState("z", "Zone", tags = setOf("escape"))),
      escapeBlueprint = EscapeBlueprintState("secret.solution", setOf("hidden.escape.fact"), listOf("act")),
      evidence = evidence,
      npcKnowledge = mapOf("survivor_17" to setOf("hidden.escape.fact", "future.secret")),
      actions = mapOf("act" to LevelActionRule("act", listOf(setOf("act")), effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))),
      replies = mapOf(
        "evidence:surface" to "Người sống sót nhớ rằng tiếng đèn đổi nhịp ở đoạn hành lang này.",
        "evidence:future" to "Một dấu hiệu chưa được phát hiện."
      )
    )
  }

  @Test fun projectionExposesOnlyDiscoveredPresentationAndInferenceBoundary() {
    val d = definition()
    val level = LevelInstanceState(
      runSeed = "r", levelId = d.id, generationId = "g", currentZoneId = "z",
      zones = d.zones, escapeBlueprint = d.escapeBlueprint,
      evidence = d.evidence, npcKnowledge = d.npcKnowledge, actions = d.actions, replies = d.replies
    )
    val state = GameState.initial().copy(levelInstance = level)
    val json = DiscoveryProjection.build(state, d, setOf("surface", "future"), "hỏi survivor 17")
    val raw = json.toString()

    assertTrue(raw.contains("Người sống sót nhớ rằng tiếng đèn đổi nhịp"))
    assertTrue(raw.contains("OBSERVED_DETAIL_ONLY"))
    assertTrue(raw.contains("allowedNpcStatements"))
    assertFalse(raw.contains("hidden.escape.fact"))
    assertFalse(raw.contains("future.secret"))
    assertFalse(raw.contains("secret.solution"))
    assertFalse(raw.contains("discoverConditions"))
    assertFalse(raw.contains("supports"))
    assertFalse(raw.contains("Một dấu hiệu chưa được phát hiện"))
  }

  @Test fun npcKnowledgeIsNotProjectedForUnmentionedActor() {
    val d = definition()
    val level = LevelInstanceState(
      runSeed = "r", levelId = d.id, generationId = "g", currentZoneId = "z",
      zones = d.zones, escapeBlueprint = d.escapeBlueprint,
      evidence = d.evidence, npcKnowledge = d.npcKnowledge, actions = d.actions, replies = d.replies
    )
    val state = GameState.initial().copy(levelInstance = level)
    val json = DiscoveryProjection.build(state, d, setOf("surface"), "quan sát bức tường")
    assertEquals(0, json.getJSONArray("allowedNpcStatements").length())
  }
}
