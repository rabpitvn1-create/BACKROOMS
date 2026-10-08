package com.rabpit.backroom.core.progression

/**
 * Explicit editorial order of Levels, numbered Sub-levels, and named sections.
 *
 * Editorial order alone is NOT an executable exit graph, character discovery,
 * save migration, Entity spawn or source of combat difficulty.
 * Only the ten explicitly reviewed featured stops are promoted to the runtime
 * itinerary by FeaturedJourneyRoutes; WorldProgressionCore.EDGES still validates
 * each transition. Unselected entries remain editorial/context-only.
 *
 * All 14 main Levels and only the eight approved numbered Sub-levels remain
 * registered. Seventeen named areas retain metadata/scene descriptions, but
 * only Red Rooms and Base Alpha have playable routes. OPEN entries are placeholders.
 */
enum class WorldJourneyStopKind { LEVEL, NUMBERED_SUB_LEVEL, NAMED_SECTION }

data class WorldJourneyGroup(
  val levelNumber: Int,
  /**
   * Ordered child itinerary keys. `level-N.X` is an existing ranked Sub-level;
   * `area:N:key` is a named, unranked section of the SAME parent Level.
   * They can be interleaved without inventing a WorldNodeId or rank.
   */
  val orderedChildKeys: List<String>,
) {
  /** Backwards-compatible filtered views for callers needing one kind only. */
  val numberedSublevelIds: List<String>
    get() = orderedChildKeys.filter { it.startsWith("level-") }

  val namedSectionKeys: List<String>
    get() = orderedChildKeys.mapNotNull {
      val prefix = "area:$levelNumber:"
      if (it.startsWith(prefix)) it.removePrefix(prefix) else null
    }
}

data class WorldJourneyStop(
  /** Unique itinerary key. Node keys use WorldNodeId.value; named areas use `area:N:key`. */
  val key: String,
  val kind: WorldJourneyStopKind,
  val parentLevel: Int,
  val title: String,
  val authority: WorldContentAuthority,
  /** Null for named areas: they are NOT ranked WorldNodes yet. */
  val worldNodeId: WorldNodeId?,
)

object WorldJourneyOrder {
  /**
   * Explicit editorial order within each parent Level.
   *
   * Named rooms without a source-backed numeric position are placed immediately
   * after their parent Level; their position is a game itinerary choice, NOT a
   * canon exit/depth statement. With unsupported 0.x Sub-levels removed, the
   * retained Level 0 itinerary places ε before Level 0.2 and Dullness after it.
   * Red Rooms remains the last Level 0 stop before Level 1.
   *
   * This list does NOT authorize gameplay exit links or named-area ranks.
   */
  val GROUPS: List<WorldJourneyGroup> = listOf(
    WorldJourneyGroup(0, listOf("area:0:epsilon", "area:0:ls-2", "area:0:manila-room", "area:0:the-torment", "level-0.2", "area:0:dullness", "area:0:red-rooms")),
    WorldJourneyGroup(1, listOf("area:1:base-alpha", "area:1:traders-vault", "level-1.2", "level-1.5")),
    WorldJourneyGroup(2, listOf("area:2:office-space-el3a")),
    WorldJourneyGroup(3, listOf()),
    WorldJourneyGroup(4, listOf("area:4:the-office-market")),
    WorldJourneyGroup(5, listOf("level-5.1")),
    WorldJourneyGroup(6, listOf("level-6.1")),
    WorldJourneyGroup(7, listOf("area:7:the-hadal-zone", "level-7.7")),
    WorldJourneyGroup(8, listOf("area:8:the-sanctum-subterraneous")),
    WorldJourneyGroup(9, listOf()),
    WorldJourneyGroup(10, listOf("level-10.1")),
    WorldJourneyGroup(11, listOf("area:11:asset-11-1", "area:11:scene-01-2", "area:11:after-hours", "area:11:the-headquarters", "area:11:radio-backrooms-studio", "level-11.3")),
    WorldJourneyGroup(12, emptyList()),
    WorldJourneyGroup(13, emptyList()),
  )

  val STOPS: List<WorldJourneyStop> = buildList {
    val nodes = WorldProgressionCore.NODES.associateBy { it.id.value }
    for (group in GROUPS) {
      val fullId = "level-${group.levelNumber}"
      val full = WorldContentCatalog.entry(WorldNodeId(fullId))
        ?: error("Missing full Level content: $fullId")
      check(nodes[fullId]?.kind == WorldNodeKind.LEVEL) { "Invalid full Level: $fullId" }
      add(WorldJourneyStop(fullId, WorldJourneyStopKind.LEVEL, group.levelNumber,
        full.title, full.authority, full.nodeId))

      val areaPrefix = "area:${group.levelNumber}:"
      for (key in group.orderedChildKeys) {
        when {
          key.startsWith("level-") -> {
            val node = nodes[key] ?: error("Missing numbered Sub-level node: $key")
            check(node.kind == WorldNodeKind.SUB_LEVEL && node.levelNumber == group.levelNumber) {
              "Wrong Sub-level parent: $key"
            }
            val content = WorldContentCatalog.entry(node.id)
              ?: error("Missing numbered Sub-level content: $key")
            add(WorldJourneyStop(key, WorldJourneyStopKind.NUMBERED_SUB_LEVEL, group.levelNumber,
              content.title, content.authority, node.id))
          }
          key.startsWith(areaPrefix) -> {
            val sectionKey = key.removePrefix(areaPrefix)
            val section = WorldContentCatalog.namedSections.singleOrNull {
              it.parentLevel == group.levelNumber && it.key == sectionKey
            } ?: error("Missing named section: ${group.levelNumber}/$sectionKey")
            add(WorldJourneyStop(key, WorldJourneyStopKind.NAMED_SECTION, group.levelNumber,
              section.title, section.authority, null))
          }
          else -> error("Journey key '$key' does not belong to Level ${group.levelNumber}")
        }
      }
    }
  }

  private val byKey = STOPS.associateBy { it.key }
  private val nextByKey = STOPS.zipWithNext().associate { (a, b) -> a.key to b }
  private val previousByKey = STOPS.zipWithNext().associate { (a, b) -> b.key to a }

  init {
    check(GROUPS.map { it.levelNumber } == (0..13).toList()) {
      "Journey groups must cover Levels 0 through 13 in order"
    }
    check(STOPS.size == byKey.size) { "Duplicate journey keys" }
    check(STOPS.mapNotNull { it.worldNodeId }.toSet() == WorldProgressionCore.NODES.map { it.id }.toSet()) {
      "Journey missing a registered Level or numbered Sub-level"
    }
    val named = STOPS.filter { it.kind == WorldJourneyStopKind.NAMED_SECTION }
      .map { it.parentLevel to it.key.substringAfterLast(':') }.toSet()
    val catalogNamed = WorldContentCatalog.namedSections.map { it.parentLevel to it.key }.toSet()
    check(named == catalogNamed) { "Journey missing or inventing a named section" }
  }

  /** Returns null for unknown or final stops. Query only: never commits a transition. */
  fun nextAfter(key: String): WorldJourneyStop? = nextByKey[key]

  /** Returns null for unknown or first stops. Query only: never commits a transition. */
  fun previousBefore(key: String): WorldJourneyStop? = previousByKey[key]

  fun stop(key: String): WorldJourneyStop? = byKey[key]

  fun groupStops(levelNumber: Int): List<WorldJourneyStop> =
    STOPS.filter { it.parentLevel == levelNumber }
}
