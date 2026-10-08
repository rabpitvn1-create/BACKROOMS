package com.rabpit.backroom.core.progression

/**
 * Deliberately approved one-way traversal of the fourteen main Backrooms Levels.
 *
 * This is NOT the editorial 67-stop itinerary. Its Sub-levels and named sections
 * are not made playable by name recognition or sorted catalog membership.
 * Every transition must be explicitly listed here AND accepted by the Core graph.
 */
data class MainLevelExitRoute(
  val sourceNodeId: String,
  val targetNodeId: String,
  val targetLevelNumber: Int,
  val targetTitle: String,
  val targetProgressionRank: Long,
)

object MainLevelExitRoutes {
  private val forward = mapOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6,
    6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13,
  )

  @JvmStatic fun titleFor(levelNumber: Int): String? {
    val nodeId = WorldProgressionCore.nodeIdForLegacyLevelNumber(levelNumber) ?: return null
    return WorldContentCatalog.entry(nodeId)?.title
  }

  @JvmStatic fun next(levelNumber: Int): MainLevelExitRoute? {
    val target = forward[levelNumber] ?: return null
    val sourceId = WorldNodeId("level-$levelNumber")
    val targetId = WorldNodeId("level-$target")
    val approved = WorldProgressionCore.validateTransition(sourceId, targetId)
      as? TransitionResult.Committed ?: return null
    val sourceNode = WorldProgressionCore.NODES.firstOrNull { it.id == sourceId }
    val targetNode = WorldProgressionCore.NODES.firstOrNull { it.id == targetId }
    if (sourceNode?.kind != WorldNodeKind.LEVEL || targetNode?.kind != WorldNodeKind.LEVEL) return null
    val content = WorldContentCatalog.entry(targetId) ?: return null
    return MainLevelExitRoute(
      sourceId.value, approved.nodeId.value, target, content.title, approved.progressionRank,
    )
  }
}
