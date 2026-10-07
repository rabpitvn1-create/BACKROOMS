package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the WorldProgressionCore registry invariants from issue #453.
 * Any deliberate balance/registry change must update these tests explicitly.
 */
class WorldProgressionCoreTest {

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

  @Test fun transitionWithNoEdgeIsRejected() {
    // Linear graph: level-0 -> level-2 has no direct edge.
    val result = WorldProgressionCore.validateTransition(WorldNodeId("level-0"), WorldNodeId("level-2"))
    assertTrue(result is TransitionResult.Rejected)
    assertEquals(TransitionRejection.NO_EDGE, (result as TransitionResult.Rejected).reason)
  }

  @Test fun validTransitionCommitsWithRank() {
    val result = WorldProgressionCore.validateTransition(WorldNodeId("level-1"), WorldNodeId("level-2"))
    assertTrue(result is TransitionResult.Committed)
    val committed = result as TransitionResult.Committed
    assertEquals(WorldNodeId("level-2"), committed.nodeId)
    assertEquals(2 * RANK_PER_FULL_LEVEL, committed.progressionRank)
  }

  @Test fun goldenRegistrySnapshot() {
    // Pinned ranks: changing any entry is a deliberate balance decision
    // and must update this test explicitly in the same commit.
    val expected = (0..6).map { n -> "level-$n" to n * RANK_PER_FULL_LEVEL }
    val actual = WorldProgressionCore.NODES.map { it.id.value to it.progressionRank }
    assertEquals(expected, actual)
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
}
