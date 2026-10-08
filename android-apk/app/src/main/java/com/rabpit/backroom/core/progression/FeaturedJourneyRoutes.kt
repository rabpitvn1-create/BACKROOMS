package com.rabpit.backroom.core.progression

/**
 * The ten author-approved featured stops inserted in their parent Level groups.
 * This is a separately approved gameplay itinerary, not a projection of the 67
 * editorial stops. Unselected Sub-levels/areas remain inaccessible.
 *
 * Named areas have no synthetic WorldNodeId/rank: their worldNodeId is the most
 * recently visited ranked stop, and their distinct location is the stop key.
 */
data class FeaturedJourneyRoute(
  val sourceStopKey: String,
  val targetStopKey: String,
  val sourceNodeId: String,
  val targetNodeId: String,
  val targetLevelNumber: Int,
  val targetTitle: String,
  val targetProgressionRank: Long,
  val targetIsNamedArea: Boolean,
)

object FeaturedJourneyRoutes {
  private val itinerary = listOf(
    "level-0", "level-0.2", "area:0:red-rooms",
    "level-1", "area:1:base-alpha", "level-1.2", "level-1.5",
    "level-2", "level-3", "level-4",
    "level-5", "level-5.1", "level-6", "level-6.1",
    "level-7", "level-7.7", "level-8", "level-9",
    "level-10", "level-10.1", "level-11", "level-11.3",
    "level-12", "level-13",
  )

  @JvmStatic fun allStopKeys(): List<String> = itinerary.toList()
  @JvmStatic fun firstStop(): String = itinerary.first()
  @JvmStatic fun contains(stopKey: String): Boolean = stopKey in itinerary

  @JvmStatic fun nodeIdAt(stopKey: String): String? {
    val index = itinerary.indexOf(stopKey)
    if (index < 0) return null
    for (i in index downTo 0) {
      val nodeId = WorldJourneyOrder.stop(itinerary[i])?.worldNodeId
      if (nodeId != null) return nodeId.value
    }
    return null
  }

  @JvmStatic fun stopLevelNumber(stopKey: String): Int? =
    if (contains(stopKey)) WorldJourneyOrder.stop(stopKey)?.parentLevel else null

  @JvmStatic fun titleFor(stopKey: String): String? =
    if (contains(stopKey)) WorldJourneyOrder.stop(stopKey)?.title else null

  /** Returns null for unknown keys, impossible/unapproved edges and Level 13. */
  @JvmStatic fun next(stopKey: String): FeaturedJourneyRoute? {
    val index = itinerary.indexOf(stopKey)
    if (index < 0 || index == itinerary.lastIndex) return null
    val sourceStop = WorldJourneyOrder.stop(stopKey) ?: return null
    val targetKey = itinerary[index + 1]
    val target = WorldJourneyOrder.stop(targetKey) ?: return null
    val sourceId = nodeIdAt(stopKey) ?: return null
    val targetId = target.worldNodeId?.value ?: sourceId
    val result = WorldProgressionCore.validateTransition(
      WorldNodeId(sourceId), WorldNodeId(targetId),
    ) as? TransitionResult.Committed ?: return null
    // An unranked named area may ONLY retain the predecessor's existing node.
    if (target.kind == WorldJourneyStopKind.NAMED_SECTION && targetId != sourceId) return null
    if (sourceStop.kind == WorldJourneyStopKind.NAMED_SECTION && sourceStop.parentLevel != target.parentLevel &&
      target.kind == WorldJourneyStopKind.NAMED_SECTION) return null
    return FeaturedJourneyRoute(
      stopKey, targetKey, sourceId, targetId,
      target.parentLevel, target.title, result.progressionRank,
      target.kind == WorldJourneyStopKind.NAMED_SECTION,
    )
  }
}
