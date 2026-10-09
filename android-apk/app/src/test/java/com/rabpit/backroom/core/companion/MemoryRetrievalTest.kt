package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.MemoryRetrieval.MemoryView
import com.rabpit.backroom.core.companion.MemoryRetrieval.Query
import org.junit.Assert.*
import org.junit.Test

/** M2b retrieval fixtures (issue #504): isolation, order, budget, determinism. */
class MemoryRetrievalTest {
  private fun view(id: String, owner: String = "cao_minh", slot: String = "s",
                   rev: Long = 1, topic: String = "t",
                   salience: EpisodicMemory.Salience = EpisodicMemory.Salience.ORDINARY,
                   scene: String = "node-7", actors: Set<String> = setOf("cao_minh"),
                   supersedes: String? = null) =
    MemoryView(id, slot, owner, "obs-$id", "turn-$rev", rev, "summary $id",
      topic, salience, supersedes, scene, actors)

  @Test fun slotActorFilterFirst() {
    val all = listOf(view("m1"), view("m2", owner = "luc_tram"), view("m3", slot = "other"))
    val p = MemoryRetrieval.retrieve(all, Query("s", "cao_minh"))
    assertEquals(listOf("m1"), p.entries.map { it.memoryId })
  }

  @Test fun correctionChainResolvesToLatest() {
    val all = listOf(view("m1"), view("m2", supersedes = "m1"))
    val p = MemoryRetrieval.retrieve(all, Query("s", "cao_minh"))
    assertEquals(listOf("m2"), p.entries.map { it.memoryId })
  }

  @Test fun episodeRefsRankFirst() {
    val all = listOf(view("m1", rev = 5), view("m2", rev = 1))
    val p = MemoryRetrieval.retrieve(all, Query("s", "cao_minh", episodeRefs = setOf("m2")))
    assertEquals("m2", p.entries.first().memoryId)
  }

  @Test fun sceneAndActorMatchOutranksRecency() {
    val all = listOf(
      view("m1", rev = 9, scene = "node-9"),
      view("m2", rev = 1, scene = "node-7", actors = setOf("cao_minh", "luc_tram")))
    val p = MemoryRetrieval.retrieve(all,
      Query("s", "cao_minh", sceneId = "node-7", actorIds = setOf("luc_tram")))
    assertEquals("m2", p.entries.first().memoryId)
  }

  @Test fun pivotalOutranksOrdinary() {
    val all = listOf(
      view("m1", rev = 9, salience = EpisodicMemory.Salience.ORDINARY),
      view("m2", rev = 1, salience = EpisodicMemory.Salience.PIVOTAL))
    val p = MemoryRetrieval.retrieve(all, Query("s", "cao_minh"))
    assertEquals("m2", p.entries.first().memoryId)
  }

  @Test fun pendingTopicsOutrankPivotal() {
    val all = listOf(
      view("m1", topic = "goal.escape", salience = EpisodicMemory.Salience.ORDINARY),
      view("m2", topic = "other", salience = EpisodicMemory.Salience.PIVOTAL))
    val p = MemoryRetrieval.retrieve(all,
      Query("s", "cao_minh", pendingTopics = setOf("goal.escape")))
    assertEquals("m1", p.entries.first().memoryId)
  }

  @Test fun tieBreakIsStableByMemoryId() {
    val all = listOf(view("mb", rev = 1), view("ma", rev = 1))
    val p1 = MemoryRetrieval.retrieve(all, Query("s", "cao_minh"))
    val p2 = MemoryRetrieval.retrieve(all.shuffled(), Query("s", "cao_minh"))
    assertEquals(p1.entries.map { it.memoryId }, p2.entries.map { it.memoryId })
    assertEquals("ma", p1.entries.first().memoryId)
  }

  @Test fun budgetLimitsPacketNotHistory() {
    val all = (1..10).map { view("m$it") }
    val p = MemoryRetrieval.retrieve(all, Query("s", "cao_minh", maxChars = 200))
    assertTrue(p.truncated)
    assertTrue(p.entries.size < 10)
    // history untouched: re-query with bigger budget returns all
    val full = MemoryRetrieval.retrieve(all, Query("s", "cao_minh", maxChars = 100000))
    assertEquals(10, full.entries.size)
    assertFalse(full.truncated)
  }

  @Test fun provenancePreserved() {
    val all = listOf(view("m1"))
    val p = MemoryRetrieval.retrieve(all, Query("s", "cao_minh"))
    val e = p.entries.single()
    assertEquals("obs-m1", e.observationId)
    assertEquals("turn-1", e.createdTurnId)
  }
}
