package com.rabpit.backroom.core.progression

/**
 * Explicit editorial order of Levels, numbered Sub-levels, and named sections.
 *
 * Order is a future itinerary/UI rule, NOT an executable exit graph, character
 * discovery, a save migration, an Entity spawn, or a source of combat difficulty.
 * `WorldProgressionCore.EDGES` and the active exit resolver remain authoritative
 * until an independently reviewed gameplay migration activates approved routes.
 *
 * The Project author specifically requires named Red Rooms to appear at the end
 * of Level 0, immediately before Level 1. OPEN entries remain placeholders.
 */
enum class WorldJourneyStopKind { LEVEL, NUMBERED_SUB_LEVEL, NAMED_SECTION }

data class WorldJourneyGroup(
  val levelNumber: Int,
  /** Exact stable WorldNode IDs in editorial sequence, never inferred from rank. */
  val numberedSublevelIds: List<String>,
  /** Keys from WorldContentCatalog.namedSections, in editorial sequence. */
  val namedSectionKeys: List<String>,
)

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
   * Explicit, stable group ordering. Numbered Sub-levels come before special
   * named sections. Do NOT automatically insert source material into this list.
   * A new node/section must be placed and tested deliberately.
   */
  val GROUPS: List<WorldJourneyGroup> = listOf(
    WorldJourneyGroup(0, listOf("level-0.01", "level-0.1", "level-0.11", "level-0.2", "level-0.22", "level-0.23", "level-0.3", "level-0.41", "level-0.5", "level-0.66", "level-0.7", "level-0.8", "level-0.99"), listOf("epsilon", "dullness", "ls-2", "manila-room", "the-torment", "red-rooms")),
    WorldJourneyGroup(1, listOf("level-1.1", "level-1.2", "level-1.3", "level-1.5"), listOf("base-alpha", "traders-vault")),
    WorldJourneyGroup(2, listOf("level-2.1"), listOf("office-space-el3a")),
    WorldJourneyGroup(3, listOf("level-3.5"), emptyList()),
    WorldJourneyGroup(4, emptyList(), listOf("the-office-market")),
    WorldJourneyGroup(5, listOf("level-5.1", "level-5.2", "level-5.3"), emptyList()),
    WorldJourneyGroup(6, listOf("level-6.1", "level-6.2", "level-6.3", "level-6.31"), emptyList()),
    WorldJourneyGroup(7, listOf("level-7.6", "level-7.7", "level-7.8"), listOf("the-hadal-zone")),
    WorldJourneyGroup(8, listOf("level-8.1"), listOf("the-sanctum-subterraneous")),
    WorldJourneyGroup(9, listOf("level-9.2", "level-9.3", "level-9.5"), emptyList()),
    WorldJourneyGroup(10, listOf("level-10.1", "level-10.2"), emptyList()),
    WorldJourneyGroup(11, listOf("level-11.3"), listOf("asset-11-1", "scene-01-2", "after-hours", "the-headquarters", "radio-backrooms-studio")),
    WorldJourneyGroup(12, emptyList(), emptyList()),
    WorldJourneyGroup(13, emptyList(), emptyList()),
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

      for (id in group.numberedSublevelIds) {
        val node = nodes[id] ?: error("Missing numbered Sub-level node: $id")
        check(node.kind == WorldNodeKind.SUB_LEVEL && node.levelNumber == group.levelNumber) {
          "Wrong Sub-level parent: $id"
        }
        val content = WorldContentCatalog.entry(node.id)
          ?: error("Missing numbered Sub-level content: $id")
        add(WorldJourneyStop(id, WorldJourneyStopKind.NUMBERED_SUB_LEVEL, group.levelNumber,
          content.title, content.authority, node.id))
      }

      for (key in group.namedSectionKeys) {
        val section = WorldContentCatalog.namedSections.singleOrNull {
          it.parentLevel == group.levelNumber && it.key == key
        } ?: error("Missing named section: ${group.levelNumber}/$key")
        add(WorldJourneyStop("area:${group.levelNumber}:$key",
          WorldJourneyStopKind.NAMED_SECTION, group.levelNumber,
          section.title, section.authority, null))
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
