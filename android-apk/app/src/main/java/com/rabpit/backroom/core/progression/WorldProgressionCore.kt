package com.rabpit.backroom.core.progression

/**
 * Core-owned world progression registry.
 *
 * Design skeleton for https://github.com/rabpitvn1-create/BACKROOMS/issues/453
 * (design phase: additive only, no behavior change to existing call sites).
 *
 * Contract:
 * - Every world position is a stable [WorldNodeId]. Display names, canon text and
 *   location strings are NEVER keys and are NEVER parsed to derive difficulty.
 * - Difficulty comes ONLY from the explicit, immutable [WorldNode.progressionRank].
 *   The canonical order of [NODES] serves traversal / UI / routing and must NEVER
 *   be used as a difficulty input (no ordinal, no `indexOf(node)`).
 * - Rank unit: [RANK_PER_FULL_LEVEL] = one full Level step = +10 percentage points
 *   over Entity base stats (linear, not compounding). See [EntityScaling].
 * - Only Core gameplay events may commit transitions via [validateTransition].
 *   AI / Gemini output is a narrative claim, never progression authority.
 * - Unknown nodes fail closed: no silent fallback to Level 0 / rank 0.
 */
const val RANK_PER_FULL_LEVEL: Long = 1_000_000L

/** Stable, forever-unchanging identifier of a world node, e.g. `"level-3"`. */
@JvmInline
value class WorldNodeId(val value: String)

enum class WorldNodeKind { LEVEL, SUB_LEVEL }

/**
 * A node in world progression.
 *
 * @param progressionRank explicit immutable balance data, assigned once when the
 * node is created. Level N is pinned to `N * RANK_PER_FULL_LEVEL` forever;
 * Sub-levels take an explicit rank strictly between their neighbours
 * (a midpoint is only a designer suggestion, never Core authority).
 * Never recomputed, never derived from list position.
 */
data class WorldNode(
  val id: WorldNodeId,
  val kind: WorldNodeKind,
  val progressionRank: Long,
  /** Level index for LEVEL nodes; parent Level index for SUB_LEVEL nodes. */
  val levelNumber: Int,
)

/** Directed traversal edge. Gameplay may only transition along declared edges. */
data class WorldEdge(val from: WorldNodeId, val to: WorldNodeId)

enum class TransitionRejection { UNKNOWN_NODE, NO_EDGE }

sealed interface TransitionResult {
  /** Authoritative commit. Callers persist [nodeId] as the new world node. */
  data class Committed(val nodeId: WorldNodeId, val progressionRank: Long) : TransitionResult

  /**
   * Fail closed: keep [authoritativeNodeId], do not mutate progression,
   * emit an audit event (audit wiring is implementation phase).
   */
  data class Rejected(val reason: TransitionRejection, val authoritativeNodeId: WorldNodeId) : TransitionResult
}

sealed interface RankLookup {
  data class Known(val progressionRank: Long) : RankLookup

  /** Fail closed: callers must NOT fall back to rank 0. */
  data object Unknown : RankLookup
}

/**
 * The single source of truth for world progression.
 * Pure, deterministic: no RNG, no clock, no AI / prompt / knowledge-DB input.
 */
object WorldProgressionCore {

  /**
   * Canonical progression order. Order is for traversal / UI; difficulty comes
   * from ranks, never from position in this list.
   *
   * Invariants (locked by tests):
   * - ids unique, ranks strictly increasing along this order,
   * - every edge references an existing node,
   * - legacy Levels 0-6 pinned: rank == N * RANK_PER_FULL_LEVEL, forever.
   */
  val NODES: List<WorldNode> = listOf(
    // Only eight approved Sub-level ranks remain; literal balance pins never derive from ordering.
    WorldNode(WorldNodeId("level-0"), WorldNodeKind.LEVEL, 0L, 0),
    WorldNode(WorldNodeId("level-0.2"), WorldNodeKind.SUB_LEVEL, 200_000L, 0),
    WorldNode(WorldNodeId("level-1"), WorldNodeKind.LEVEL, 1_000_000L, 1),
    WorldNode(WorldNodeId("level-1.2"), WorldNodeKind.SUB_LEVEL, 1_200_000L, 1),
    WorldNode(WorldNodeId("level-1.5"), WorldNodeKind.SUB_LEVEL, 1_500_000L, 1),
    WorldNode(WorldNodeId("level-2"), WorldNodeKind.LEVEL, 2_000_000L, 2),
    WorldNode(WorldNodeId("level-3"), WorldNodeKind.LEVEL, 3_000_000L, 3),
    WorldNode(WorldNodeId("level-4"), WorldNodeKind.LEVEL, 4_000_000L, 4),
    WorldNode(WorldNodeId("level-5"), WorldNodeKind.LEVEL, 5_000_000L, 5),
    WorldNode(WorldNodeId("level-5.1"), WorldNodeKind.SUB_LEVEL, 5_100_000L, 5),
    WorldNode(WorldNodeId("level-6"), WorldNodeKind.LEVEL, 6_000_000L, 6),
    WorldNode(WorldNodeId("level-6.1"), WorldNodeKind.SUB_LEVEL, 6_100_000L, 6),
    WorldNode(WorldNodeId("level-7"), WorldNodeKind.LEVEL, 7_000_000L, 7),
    WorldNode(WorldNodeId("level-7.7"), WorldNodeKind.SUB_LEVEL, 7_700_000L, 7),
    WorldNode(WorldNodeId("level-8"), WorldNodeKind.LEVEL, 8_000_000L, 8),
    WorldNode(WorldNodeId("level-9"), WorldNodeKind.LEVEL, 9_000_000L, 9),
    WorldNode(WorldNodeId("level-10"), WorldNodeKind.LEVEL, 10_000_000L, 10),
    WorldNode(WorldNodeId("level-10.1"), WorldNodeKind.SUB_LEVEL, 10_100_000L, 10),
    WorldNode(WorldNodeId("level-11"), WorldNodeKind.LEVEL, 11_000_000L, 11),
    WorldNode(WorldNodeId("level-11.3"), WorldNodeKind.SUB_LEVEL, 11_300_000L, 11),
    WorldNode(WorldNodeId("level-12"), WorldNodeKind.LEVEL, 12_000_000L, 12),
    WorldNode(WorldNodeId("level-13"), WorldNodeKind.LEVEL, 13_000_000L, 13),
  )

  /**
   * Canonical traversal graph. EXPLICIT — never derived from NODES order, so
   * adding a Sub-level to canonical order cannot silently rewire traversal.
   *
   * TRANSITION SEMANTICS (deliberate, locked): the legacy `set_level` rule is
   * preserved exactly — any Level 0..6 is reachable from any other Level once
   * the Core exit gate passes. This is NOT a new adjacent-only restriction;
   * tightening it (e.g. adjacent-only) is a separate gameplay decision.
   * The exit gate itself (`canTransition`: confirmedExit or levelExit roll)
   * stays a gameplay precondition evaluated by the caller BEFORE calling
   * [validateTransition], which enforces graph authority only.
   *
   * Approved 5-streak forward journey also connects the main Level 6 -> 7 -> ... -> 13.
   * All 42 legacy Level 0-6 edges remain untouched. Numbered Sub-levels and named
   * areas never get implicit edges from their catalog or editorial ordering.
   */
  val EDGES: List<WorldEdge> = buildList {
    // Pin legacy connectivity. New full Levels require deliberate edge declarations too.
    val levels = (0..6).map { WorldNodeId("level-$it") }
    for (from in levels) for (to in levels) {
      if (from != to) add(WorldEdge(from, to))
    }
    // Explicitly approved forward main-Level journey for the 5-streak exit rule.
    add(WorldEdge(WorldNodeId("level-6"), WorldNodeId("level-7")))
    add(WorldEdge(WorldNodeId("level-7"), WorldNodeId("level-8")))
    add(WorldEdge(WorldNodeId("level-8"), WorldNodeId("level-9")))
    add(WorldEdge(WorldNodeId("level-9"), WorldNodeId("level-10")))
    add(WorldEdge(WorldNodeId("level-10"), WorldNodeId("level-11")))
    add(WorldEdge(WorldNodeId("level-11"), WorldNodeId("level-12")))
    add(WorldEdge(WorldNodeId("level-12"), WorldNodeId("level-13")))
    // Fifteen explicit author-approved ranked edges for ten featured stops.
    // Named areas use a separately pinned journey key, never an invented rank.
    add(WorldEdge(WorldNodeId("level-0"), WorldNodeId("level-0.2")))
    add(WorldEdge(WorldNodeId("level-0.2"), WorldNodeId("level-1")))
    add(WorldEdge(WorldNodeId("level-1"), WorldNodeId("level-1.2")))
    add(WorldEdge(WorldNodeId("level-1.2"), WorldNodeId("level-1.5")))
    add(WorldEdge(WorldNodeId("level-1.5"), WorldNodeId("level-2")))
    add(WorldEdge(WorldNodeId("level-5"), WorldNodeId("level-5.1")))
    add(WorldEdge(WorldNodeId("level-5.1"), WorldNodeId("level-6")))
    add(WorldEdge(WorldNodeId("level-6"), WorldNodeId("level-6.1")))
    add(WorldEdge(WorldNodeId("level-6.1"), WorldNodeId("level-7")))
    add(WorldEdge(WorldNodeId("level-7"), WorldNodeId("level-7.7")))
    add(WorldEdge(WorldNodeId("level-7.7"), WorldNodeId("level-8")))
    add(WorldEdge(WorldNodeId("level-10"), WorldNodeId("level-10.1")))
    add(WorldEdge(WorldNodeId("level-10.1"), WorldNodeId("level-11")))
    add(WorldEdge(WorldNodeId("level-11"), WorldNodeId("level-11.3")))
    add(WorldEdge(WorldNodeId("level-11.3"), WorldNodeId("level-12")))
  }

  init {
    // Invariant "rank explicit & valid": no committed node may carry a rank
    // the scaler rejects. Fail fast at registry load, not at combat time.
    require(NODES.all { it.progressionRank in 0L..EntityScaling.MAX_PROGRESSION_RANK }) {
      "WorldNode rank outside EntityScaling supported domain [0, ${EntityScaling.MAX_PROGRESSION_RANK}]"
    }
  }

  private val byId: Map<WorldNodeId, WorldNode> = NODES.associateBy { it.id }
  private val edgeSet: Set<WorldEdge> = EDGES.toSet()

  /** Pure rank lookup. Unknown ids fail closed ([RankLookup.Unknown]). */
  fun rankOf(nodeId: WorldNodeId): RankLookup =
    byId[nodeId]?.let { RankLookup.Known(it.progressionRank) } ?: RankLookup.Unknown

  /**
   * Authoritative transition validation. Called ONLY by Core gameplay events,
   * never directly from AI / Gemini output.
   */
  fun validateTransition(from: WorldNodeId, to: WorldNodeId): TransitionResult {
    val target = byId[to]
      ?: return TransitionResult.Rejected(TransitionRejection.UNKNOWN_NODE, from)
    if (from == to) return TransitionResult.Committed(to, target.progressionRank)
    if (byId[from] == null) {
      return TransitionResult.Rejected(TransitionRejection.UNKNOWN_NODE, from)
    }
    if (WorldEdge(from, to) !in edgeSet) {
      return TransitionResult.Rejected(TransitionRejection.NO_EDGE, from)
    }
    return TransitionResult.Committed(to, target.progressionRank)
  }

  /**
   * Save migration helper: integer main Level 0–13 -> registered stable node id.
   * Returns null for unknown numbers (fail closed; caller raises migration error).
   */
  fun nodeIdForLegacyLevelNumber(levelNumber: Int): WorldNodeId? =
    if (levelNumber in 0..13) byId.keys.firstOrNull { it.value == "level-$levelNumber" } else null
}
