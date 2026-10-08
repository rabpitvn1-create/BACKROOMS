package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the WorldProgressionCore registry invariants from issue #453.
 * Any deliberate balance/registry change must update these tests explicitly.
 */
class WorldProgressionCoreTest {

  @Test fun rankPerFullLevelConstantIsPinned() {
    // The constant itself is balance data: changing it rebalances every node,
    // so the literal value is pinned, not just its uses.
    assertEquals(1_000_000L, RANK_PER_FULL_LEVEL)
  }

  @Test fun nodeIdsAreUnique() {
    val ids = WorldProgressionCore.NODES.map { it.id.value }
    assertEquals(ids.size, ids.toSet().size)
  }

  @Test fun canonicalOrderHasStrictlyIncreasingRanks() {
    val ranks = WorldProgressionCore.NODES.map { it.progressionRank }
    assertTrue(
      "ranks must strictly increase along canonical order",
      ranks.zipWithNext().all { (a, b) -> b > a },
    )
  }

  @Test fun everyEdgeReferencesExistingNodes() {
    val ids = WorldProgressionCore.NODES.map { it.id }.toSet()
    WorldProgressionCore.EDGES.forEach { edge ->
      assertTrue("edge from unknown node: ${edge.from.value}", edge.from in ids)
      assertTrue("edge to unknown node: ${edge.to.value}", edge.to in ids)
    }
  }

  @Test fun allNodeRanksWithinScalerDomain() {
    // Point 5: the registry itself enforces the scaler's domain, so no
    // committed node can carry a rank EntityScaling would reject.
    WorldProgressionCore.NODES.forEach { node ->
      assertTrue(
        "rank out of scaler domain for ${node.id.value}",
        node.progressionRank in 0L..EntityScaling.MAX_PROGRESSION_RANK,
      )
    }
  }

  @Test fun legacyLevelsAreFullyConnected() {
    // Transition semantics (deliberate): the legacy set_level rule is
    // preserved — any Level 0..6 is reachable from any other once the Core
    // exit gate passes. NOT a new adjacent-only restriction.
    val levels = (0..6).map { WorldNodeId("level-$it") }
    val edges = WorldProgressionCore.EDGES.toSet()
    for (from in levels) for (to in levels) {
      if (from == to) continue
      assertTrue("missing edge ${from.value} -> ${to.value}", WorldEdge(from, to) in edges)
    }
  }

  @Test fun subLevelsGetNoAutomaticEdges() {
    // Adding a Sub-level to canonical order must not silently rewire
    // traversal: its edges are added explicitly, never derived.
    val sub = WorldNodeId("level-1.sub-a")
    assertTrue(WorldProgressionCore.EDGES.none { it.from == sub || it.to == sub })
  }

  @Test fun selfTransitionIsIdempotent() {
    // Matches the legacy set_level no-op when target == current level.
    val result = WorldProgressionCore.validateTransition(WorldNodeId("level-2"), WorldNodeId("level-2"))
    assertTrue(result is TransitionResult.Committed)
    assertEquals(2 * RANK_PER_FULL_LEVEL, (result as TransitionResult.Committed).progressionRank)
  }

  @Test fun legacyLevelsZeroToSixArePinned() {
    for (n in 0..6) {
      val nodeId = WorldProgressionCore.nodeIdForLegacyLevelNumber(n)
      assertNotNull("level-$n must exist", nodeId)
      val rank = WorldProgressionCore.rankOf(nodeId!!)
      assertTrue("level-$n must have a known rank", rank is RankLookup.Known)
      assertEquals(n * RANK_PER_FULL_LEVEL, (rank as RankLookup.Known).progressionRank)
    }
  }

  @Test fun unknownNodeRankLookupFailsClosed() {
    val lookup = WorldProgressionCore.rankOf(WorldNodeId("level-999"))
    assertTrue("unknown node must not resolve to a rank", lookup is RankLookup.Unknown)
  }

  @Test fun unknownTransitionTargetIsRejectedAndKeepsAuthoritativeNode() {
    val current = WorldNodeId("level-2")
    val result = WorldProgressionCore.validateTransition(current, WorldNodeId("nope"))
    assertTrue(result is TransitionResult.Rejected)
    val rejected = result as TransitionResult.Rejected
    assertEquals(TransitionRejection.UNKNOWN_NODE, rejected.reason)
    assertEquals(current, rejected.authoritativeNodeId)
  }

  @Test fun distantTransitionIsAllowedPreservingLegacyRule() {
    // Legacy set_level allowed any target 0..6 after the exit gate passed;
    // the explicit graph preserves exactly that (no new adjacent-only rule).
    val result = WorldProgressionCore.validateTransition(WorldNodeId("level-0"), WorldNodeId("level-5"))
    assertTrue(result is TransitionResult.Committed)
    assertEquals(5 * RANK_PER_FULL_LEVEL, (result as TransitionResult.Committed).progressionRank)
  }

  @Test fun validTransitionCommitsWithRank() {
    val result = WorldProgressionCore.validateTransition(WorldNodeId("level-1"), WorldNodeId("level-2"))
    assertTrue(result is TransitionResult.Committed)
    val committed = result as TransitionResult.Committed
    assertEquals(WorldNodeId("level-2"), committed.nodeId)
    assertEquals(2 * RANK_PER_FULL_LEVEL, committed.progressionRank)
  }

  @Test fun goldenRegistrySnapshotUsesLiteralRanks() {
    // Pinned ranks as LITERALS — deliberately not computed from
    // RANK_PER_FULL_LEVEL, so changing the constant cannot silently
    // rebalance every level while keeping this test green.
    val expected = listOf(
      "level-0" to 0L,
      "level-1" to 1_000_000L,
      "level-2" to 2_000_000L,
      "level-3" to 3_000_000L,
      "level-4" to 4_000_000L,
      "level-5" to 5_000_000L,
      "level-6" to 6_000_000L,
      "level-7" to 7_000_000L,
      "level-8" to 8_000_000L,
      "level-9" to 9_000_000L,
      "level-10" to 10_000_000L,
      "level-11" to 11_000_000L,
      "level-12" to 12_000_000L,
      "level-13" to 13_000_000L,
    )
    val actual = WorldProgressionCore.NODES.filter { it.kind == WorldNodeKind.LEVEL }
      .map { it.id.value to it.progressionRank }
    assertEquals(expected, actual)
  }

  @Test fun everyMainLevelHasItsApprovedSuccessorUntilThirteen() {
    for (n in 6..12) {
      val from = WorldNodeId("level-$n")
      val to = WorldNodeId("level-${n + 1}")
      assertTrue(WorldEdge(from, to) in WorldProgressionCore.EDGES)
      val result = WorldProgressionCore.validateTransition(from, to)
      assertTrue(result is TransitionResult.Committed)
      assertEquals((n + 1).toLong() * RANK_PER_FULL_LEVEL,
        (result as TransitionResult.Committed).progressionRank)
      if (n >= 7) {
        assertTrue(WorldProgressionCore.validateTransition(to, from) is TransitionResult.Rejected)
      }
    }
    assertTrue(WorldProgressionCore.EDGES.none { it.from == WorldNodeId("level-13") })
    assertTrue(WorldProgressionCore.validateTransition(
      WorldNodeId("level-7"), WorldNodeId("level-10")) is TransitionResult.Rejected)
  }

  @Test fun mainLevelSaveMigrationSupportsZeroThroughThirteenOnly() {
    for (n in 0..13) assertEquals(WorldNodeId("level-$n"),
      WorldProgressionCore.nodeIdForLegacyLevelNumber(n))
    assertNull(WorldProgressionCore.nodeIdForLegacyLevelNumber(-1))
    assertNull(WorldProgressionCore.nodeIdForLegacyLevelNumber(14))
  }

  @Test fun allSourcedNumericSublevelsHavePinnedRanks() {
    val expected = listOf(
      "level-0.01" to 10_000L,
      "level-0.1" to 100_000L,
      "level-0.11" to 110_000L,
      "level-0.2" to 200_000L,
      "level-0.22" to 220_000L,
      "level-0.23" to 230_000L,
      "level-0.3" to 300_000L,
      "level-0.41" to 410_000L,
      "level-0.5" to 500_000L,
      "level-0.66" to 660_000L,
      "level-0.7" to 700_000L,
      "level-0.8" to 800_000L,
      "level-0.99" to 990_000L,
      "level-1.1" to 1_100_000L,
      "level-1.2" to 1_200_000L,
      "level-1.3" to 1_300_000L,
      "level-1.5" to 1_500_000L,
      "level-2.1" to 2_100_000L,
      "level-3.5" to 3_500_000L,
      "level-5.1" to 5_100_000L,
      "level-5.2" to 5_200_000L,
      "level-5.3" to 5_300_000L,
      "level-6.1" to 6_100_000L,
      "level-6.2" to 6_200_000L,
      "level-6.3" to 6_300_000L,
      "level-6.31" to 6_310_000L,
      "level-7.6" to 7_600_000L,
      "level-7.7" to 7_700_000L,
      "level-7.8" to 7_800_000L,
      "level-8.1" to 8_100_000L,
      "level-9.2" to 9_200_000L,
      "level-9.3" to 9_300_000L,
      "level-9.5" to 9_500_000L,
      "level-10.1" to 10_100_000L,
      "level-10.2" to 10_200_000L,
      "level-11.3" to 11_300_000L,
    )
    val actual = WorldProgressionCore.NODES.filter { it.kind == WorldNodeKind.SUB_LEVEL }
      .map { it.id.value to it.progressionRank }
    assertEquals(expected, actual)
  }

  @Test fun allSublevelsKeepCorrectParentAndHaveNoAutomaticRoutes() {
    val nodes = WorldProgressionCore.NODES
    val edges = WorldProgressionCore.EDGES
    assertEquals(14, nodes.count { it.kind == WorldNodeKind.LEVEL })
    assertEquals(36, nodes.count { it.kind == WorldNodeKind.SUB_LEVEL })
    nodes.filter { it.kind == WorldNodeKind.SUB_LEVEL }.forEach { node ->
      val parentRank = node.levelNumber.toLong() * RANK_PER_FULL_LEVEL
      assertTrue("sublevel must be above parent: ${node.id.value}", node.progressionRank > parentRank)
      assertTrue("sublevel must be below next level: ${node.id.value}",
        node.progressionRank < parentRank + RANK_PER_FULL_LEVEL)
      assertTrue("sublevel cannot inherit routes: ${node.id.value}",
        edges.none { it.from == node.id || it.to == node.id })
    }
  }

  @Test fun addingSubLevelDoesNotChangeExistingRanks() {
    // Core property: a new node carries its own explicit rank; existing nodes
    // are untouched, so inserting a Sub-level can never silently rebalance.
    val before = WorldProgressionCore.NODES.associate { it.id.value to it.progressionRank }
    val extended = WorldProgressionCore.NODES + WorldNode(
      WorldNodeId("level-1.sub-a"), WorldNodeKind.SUB_LEVEL, 1_500_000L, 1,
    )
    val after = extended.associate { it.id.value to it.progressionRank }
    before.forEach { (id, rank) -> assertEquals("rank changed for $id", rank, after[id]) }
    assertEquals(1_500_000L, after["level-1.sub-a"])
  }
  @Test fun legacyEdgeSetIsPinnedIndependentlyOfFutureRegistryNodes() {
    val expected = (0..6).flatMap { from -> (0..6).filter { it != from }.map { to ->
      WorldEdge(WorldNodeId("level-$from"), WorldNodeId("level-$to"))
    } }.toSet()
    val newForward = (6..12).map { n ->
      WorldEdge(WorldNodeId("level-$n"), WorldNodeId("level-${n + 1}"))
    }.toSet()
    assertEquals(expected + newForward, WorldProgressionCore.EDGES.toSet())
    assertEquals(49, WorldProgressionCore.EDGES.size)
  }

}
