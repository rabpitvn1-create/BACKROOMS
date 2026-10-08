package com.rabpit.backroom.core.progression

/**
 * Source-grounded content inventory. This is NOT progression authority, an exit
 * resolver, an Entity spawn table, or a grant of items/knowledge to any character.
 *
 * PROJECT_WORLD means the local BACKROOMS_WORLD.md has priority over wiki prose.
 * EXTERNAL_REFERENCE means a candidate from https://backrooms-wiki.wikidot.com/normal-levels-i.
 * OPEN means unresolved/rewrite-only: never extrapolate details.
 *
 * A registered node remains unreachable until Core and the game exit resolver both
 * declare a validated route. This inventory creates no routes and no gameplay RNG.
 */
enum class WorldContentAuthority { PROJECT_CANON, EXTERNAL_REFERENCE, OPEN }
enum class WorldContentSource { PROJECT_WORLD, WIKIDOT }

data class WorldContentEntry(
  val nodeId: WorldNodeId,
  val title: String,
  val authority: WorldContentAuthority,
  val source: WorldContentSource,
  val environmentBaseline: String?,
)

data class WorldNamedSection(
  /** Parent full-Level number. These are NOT independently ranked WorldNodes. */
  val parentLevel: Int,
  val key: String,
  val title: String,
  val authority: WorldContentAuthority,
)

object WorldContentCatalog {
  const val WIKI_INDEX = "https://backrooms-wiki.wikidot.com/normal-levels-i"
  const val PROJECT_WORLD_PATH = "knowledge/novel_asset/BACKROOMS_WORLD.md"

  // Base environments are reference context only; Entity presence/loot/exits require Core validation.
  val levels: List<WorldContentEntry> = listOf(
    WorldContentEntry(WorldNodeId("level-0"), "Threshold / The Lobby", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Yellow, damp, fluorescent non-Euclidean office labyrinth."),
    WorldContentEntry(WorldNodeId("level-1"), "Habitable Zone", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Concrete parking and industrial service halls; blackouts remain possible."),
    WorldContentEntry(WorldNodeId("level-2"), "Abandoned Utility Halls", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Pipe-lined hot and humid maintenance passages with narrow routes."),
    WorldContentEntry(WorldNodeId("level-3"), "Electrical Station", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Industrial electrical machinery, transformers, cables and changing power conditions."),
    WorldContentEntry(WorldNodeId("level-4"), "Abandoned Office", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Office tower with cubicles and windows under a rainy grey sky."),
    WorldContentEntry(WorldNodeId("level-5"), "Terror Hotel", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Antique hotel with wings, ballrooms, bedrooms and boiler rooms."),
    WorldContentEntry(WorldNodeId("level-6"), "Lights Out", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Project hard lock: permanently dark tundra, not the classic dark maze."),
    WorldContentEntry(WorldNodeId("level-7"), "Thalassophobia", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Near-boundless cold ocean and decaying metal shelter; depths are uncertain."),
    WorldContentEntry(WorldNodeId("level-8"), "Cave Systems", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Large cave network with deep drops, tight tunnels and standing water."),
    WorldContentEntry(WorldNodeId("level-9"), "The Suburbs", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Endlessly repeating nocturnal suburban streets and houses."),
    WorldContentEntry(WorldNodeId("level-10"), "Bumper Crop", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, "Extensive farmland, fields, barns and overcast daylight."),
    WorldContentEntry(WorldNodeId("level-11"), "The City That Never Sleeps", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, "Expansive city blocks, transit, commercial and residential structures."),
    WorldContentEntry(WorldNodeId("level-12"), "Matrix", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, "White furnished room and broader white void; recordings can become censored."),
    WorldContentEntry(WorldNodeId("level-13"), "The Boiling Frogs", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, "Large apartment complex with repetitive residences and insidious complacency."),
  )

  // 36 numeric sublevels sourced from project canon and the external Wikidot index.
  // Level 4, 12 and 13 currently have no numbered sublevels in the selected sources.
  val sublevels: List<WorldContentEntry> = listOf(
    WorldContentEntry(WorldNodeId("level-0.01"), "The Exit?", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.1"), "Deep Emptiness", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.11"), "Water Damage", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.2"), "Remodeled Mess", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-0.22"), "Fully Remodeled", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.23"), "Half Finished", WorldContentAuthority.OPEN, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-0.3"), "The Icy Rooms", WorldContentAuthority.OPEN, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-0.41"), "Disease", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.5"), "Aquaclaustrophobic Infirmary", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.66"), "The Lobby Went COLD", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.7"), "Claustrophobia", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.8"), "Inundation", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-0.99"), "Deeper Regions", WorldContentAuthority.PROJECT_CANON, WorldContentSource.PROJECT_WORLD, null),
    WorldContentEntry(WorldNodeId("level-1.1"), "Corrupted Corridor", WorldContentAuthority.OPEN, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-1.2"), "Concrete Garden", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-1.3"), "Malignance", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-1.5"), "Inverted", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-2.1"), "Locked", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-3.5"), "Electropolis", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-5.1"), "Terror Hotel Casino", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-5.2"), "Scenic Views", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-5.3"), "Promethei Bibliotheca", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-6.1"), "The Snackrooms", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-6.2"), "The Neon Maze", WorldContentAuthority.OPEN, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-6.3"), "Vantablack", WorldContentAuthority.OPEN, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-6.31"), "Pierce the Veil", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-7.6"), "Evacuation", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-7.7"), "The Forsaken Debris", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-7.8"), "Impaled Ocean", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-8.1"), "The Dead Caverns", WorldContentAuthority.OPEN, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-9.2"), "Black Market", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-9.3"), "The Overcast Manifold", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-9.5"), "Rochester Blues", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-10.1"), "Corpse Lake", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-10.2"), "Hay Bale Heaven", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
    WorldContentEntry(WorldNodeId("level-11.3"), "The Red Light District", WorldContentAuthority.EXTERNAL_REFERENCE, WorldContentSource.WIKIDOT, null),
  )

  // Special/named sections are catalogued but deliberately receive NO synthetic
  // WorldNodeId, progressionRank or automatic route.
  val namedSections: List<WorldNamedSection> = listOf(
    WorldNamedSection(0, "epsilon", "Level ε — Incessant Hum-Buzz", WorldContentAuthority.PROJECT_CANON),
    WorldNamedSection(0, "dullness", "Dullness", WorldContentAuthority.PROJECT_CANON),
    WorldNamedSection(0, "red-rooms", "Red Rooms", WorldContentAuthority.PROJECT_CANON),
    WorldNamedSection(0, "ls-2", "LS-2", WorldContentAuthority.OPEN),
    WorldNamedSection(0, "manila-room", "Manila Room", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(0, "the-torment", "The Torment", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(1, "base-alpha", "Base Alpha", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(1, "traders-vault", "Traders Vault", WorldContentAuthority.OPEN),
    WorldNamedSection(2, "office-space-el3a", "Office Space EL3A", WorldContentAuthority.OPEN),
    WorldNamedSection(4, "the-office-market", "The Office Market", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(7, "the-hadal-zone", "The Hadal Zone", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(8, "the-sanctum-subterraneous", "The Sanctum Subterraneous", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(11, "asset-11-1", "Asset 11.1 — Private Enterprise", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(11, "scene-01-2", "Scene-01.2 — The Refuge", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(11, "after-hours", "AFTER HOURS", WorldContentAuthority.EXTERNAL_REFERENCE),
    WorldNamedSection(11, "the-headquarters", "The Headquarters", WorldContentAuthority.OPEN),
    WorldNamedSection(11, "radio-backrooms-studio", "Radio Backrooms' Studio", WorldContentAuthority.OPEN),
  )

  private val byId = (levels + sublevels).associateBy { it.nodeId }

  /** Metadata lookup never changes the authoritative world state. */
  fun entry(nodeId: WorldNodeId): WorldContentEntry? = byId[nodeId]
}
