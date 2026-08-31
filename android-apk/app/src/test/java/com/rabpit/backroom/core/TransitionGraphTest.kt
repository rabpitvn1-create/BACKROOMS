package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class TransitionGraphTest {
  @Test fun opaqueIdsAndMultipleOutgoingTargetsArePreservedExactly() {
    val catalog = load(
      entry("742.13", 1000, transitions = listOf("999.alpha", "Red Rooms")),
      entry("999.alpha", 2000),
      entry("Red Rooms", 3000),
      entry("1.618033988749894...", 4000)
    )

    assertEquals(listOf("999.alpha", "Red Rooms"), catalog.allowedTransitionsFrom("742.13").map { it.targetId })
    assertTrue(catalog.canTransition("742.13", "999.alpha"))
    assertFalse(catalog.canTransition("742.13", "1.618033988749894..."))
  }

  @Test fun selfLoopDanglingDuplicateAndBackwardEdgesFailClosed() {
    assertRejected("transition_self_loop") { load(entry("a", 1000, listOf("a"))) }
    assertRejected("dangling_transition") { load(entry("a", 1000, listOf("missing"))) }
    assertRejected("duplicate_transition") { load(entry("a", 1000, listOf("b", "b")), entry("b", 2000)) }
    assertRejected("transition_not_forward") { load(entry("a", 2000, listOf("b")), entry("b", 1000)) }
  }

  @Test fun parentRelationshipDoesNotCreateTransition() {
    val parent = entry("main", 1000)
    val child = LevelCatalogEntry(
      id = "child", parentId = "main", name = "child", kind = LevelKind.SUBLEVEL,
      campaignId = "test", campaignOrder = 2000
    )
    val catalog = LevelCatalog.from(listOf(parent, child))
    assertFalse(catalog.canTransition("main", "child"))
  }

  @Test fun bundledZeroThroughSixCatalogLoadsAndDeclaresZeroForwardRoute() {
    val file = java.io.File("src/main/assets/level_catalog/backrooms-0-6.json")
    val catalog = LevelCatalogLoader.load(listOf(LevelCatalogDocument(file.path, file.readText())))
    assertTrue(catalog.ids().containsAll(listOf("0", "1", "2", "3", "4", "5", "6")))
    assertTrue(catalog.allowedTransitionsFrom("0").isNotEmpty())
    assertTrue(catalog.canTransition("0", "epsilon"))
  }

  private fun entry(id: String, order: Long, transitions: List<String> = emptyList()) = LevelCatalogEntry(
    id = id,
    name = id,
    kind = LevelKind.SPECIAL,
    campaignId = "test",
    campaignOrder = order,
    outgoingTransitions = transitions.map(::LevelTransition)
  )

  private fun load(vararg entries: LevelCatalogEntry) = LevelCatalog.from(entries.toList())

  private fun assertRejected(fragment: String, block: () -> Unit) {
    try {
      block()
      fail("Expected $fragment")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains(fragment))
    }
  }
}
