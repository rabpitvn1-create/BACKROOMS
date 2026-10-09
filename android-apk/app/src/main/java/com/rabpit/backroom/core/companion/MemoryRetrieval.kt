package com.rabpit.backroom.core.companion

/**
 * M2b deterministic actor-private memory retrieval (issue #504).
 *
 * Pipeline (all deterministic, no ML, no provider):
 * 1. Filter slot + actor FIRST — another actor's topics, private promises, and
 *    writer secrets can never enter the packet.
 * 2. Resolve to latest per correction chain (supersedes links).
 * 3. Rank: explicit episode refs > scene/actors match > unresolved goals/promises
 *    topics > pivotal salience > recently relevant (higher revision first).
 *    Stable tie-break by memoryId ascending.
 * 4. Budget: take top entries until maxChars; the budget limits the PACKET only —
 *    permanent history is never pruned.
 *
 * Provenance and correction links are preserved on every returned entry.
 * Ordinary indexes only (memory_by_actor); no FTS dependency.
 *
 * Pure Kotlin: no Android, no SQLite, no I/O.
 */
internal object MemoryRetrieval {
  data class MemoryView(
    val memoryId: String,
    val slotId: String,
    val ownerActorId: String,
    val observationId: String,
    val createdTurnId: String,
    val committedRevision: Long,
    val summary: String,
    val topic: String,
    val salience: EpisodicMemory.Salience,
    val supersedesMemoryId: String?,
    /** Scene/actor context for ranking; from the linked observation. */
    val sceneId: String,
    val involvedActorIds: Set<String>
  )

  data class Query(
    val slotId: String,
    val actorId: String,
    /** Explicitly requested memory ids — highest rank. */
    val episodeRefs: Set<String> = emptySet(),
    val sceneId: String? = null,
    val actorIds: Set<String> = emptySet(),
    /** Topics of unresolved goals/promises. */
    val pendingTopics: Set<String> = emptySet(),
    val maxChars: Int = 4000
  )

  data class Packet(val entries: List<MemoryView>, val truncated: Boolean)

  fun retrieve(all: List<MemoryView>, query: Query): Packet {
    require(query.maxChars >= 0) { "memory_budget_invalid" }
    // 1. Slot + actor filter first.
    val owned = all.filter { it.slotId == query.slotId && it.ownerActorId == query.actorId }
    // 2. Latest per correction chain.
    val superseded = owned.mapNotNull { it.supersedesMemoryId }.toSet()
    val latest = owned.filter { it.memoryId !in superseded }
    // 3. Deterministic rank.
    val ranked = latest.sortedWith(compareBy(
      { if (it.memoryId in query.episodeRefs) 0 else 1 },
      { rankSceneActors(it, query) },
      { if (it.topic in query.pendingTopics) 0 else 1 },
      { if (it.salience == EpisodicMemory.Salience.PIVOTAL) 0 else 1 },
      { -it.committedRevision },
      { it.memoryId }
    ))
    // 4. Budget the packet; history untouched.
    val entries = ArrayList<MemoryView>()
    var used = 0L
    var truncated = false
    for (m in ranked) {
      val cost = m.summary.length + 64
      if (used + cost > query.maxChars.toLong()) { truncated = true; continue }
      entries.add(m)
      used += cost
    }
    return Packet(entries, truncated)
  }

  private fun rankSceneActors(m: MemoryView, q: Query): Int {
    var score = 2
    if (q.sceneId != null && m.sceneId == q.sceneId) score--
    if (q.actorIds.isNotEmpty() && m.involvedActorIds.intersect(q.actorIds).isNotEmpty()) score--
    return score
  }
}
