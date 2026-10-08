package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class WorldContentCatalogTest {

  @Test fun metadataCoversEveryRegisteredLevelAndNumericSublevelExactlyOnce() {
    val content = WorldContentCatalog.levels + WorldContentCatalog.sublevels
    val ids = content.map { it.nodeId }
    assertEquals(ids.size, ids.toSet().size)
    assertEquals(14, WorldContentCatalog.levels.size)
    assertEquals(36, WorldContentCatalog.sublevels.size)
    assertEquals(WorldProgressionCore.NODES.map { it.id }.toSet(), ids.toSet())
    assertTrue(content.all { it.title.isNotBlank() })
    content.forEach { assertEquals(it, WorldContentCatalog.entry(it.nodeId)) }
    assertNull(WorldContentCatalog.entry(WorldNodeId("level-999")))
  }

  @Test fun projectCanonWinsOverExternalTitlesAndHardLocks() {
    assertEquals("Claustrophobia", WorldContentCatalog.entry(WorldNodeId("level-0.7"))?.title)
    assertEquals("Deep Emptiness", WorldContentCatalog.entry(WorldNodeId("level-0.1"))?.title)
    val six = WorldContentCatalog.entry(WorldNodeId("level-6"))
    assertEquals(WorldContentAuthority.PROJECT_CANON, six?.authority)
    assertTrue(six?.environmentBaseline?.contains("tundra") == true)
    for (n in 11..13) {
      assertEquals(WorldContentAuthority.EXTERNAL_REFERENCE,
        WorldContentCatalog.entry(WorldNodeId("level-$n"))?.authority)
    }
  }

  @Test fun namedSectionsAreMetadataOnlyAndNeverInventRankOrExit() {
    assertEquals(17, WorldContentCatalog.namedSections.size)
    val identities = WorldContentCatalog.namedSections.map { it.parentLevel to it.key }
    assertEquals(identities.size, identities.toSet().size)
    WorldContentCatalog.namedSections.forEach { section ->
      assertTrue(section.parentLevel in 0..13)
      assertTrue(section.key.isNotBlank())
      assertTrue(section.title.isNotBlank())
      assertNull(WorldContentCatalog.entry(WorldNodeId("level-${section.parentLevel}.${section.key}")))
    }
    // Only explicitly approved main-Level routes exist. Catalog-only Sub-levels/areas
    // must never acquire edges as a side effect of adding scene text.
    val main = (0..13).map { WorldNodeId("level-$it") }.toSet()
    assertEquals(49, WorldProgressionCore.EDGES.size)
    assertTrue(WorldProgressionCore.EDGES.all { it.from in main && it.to in main })
  }

  @Test fun openAndReferenceOnlyEntriesAreNotUpgradedToCanon() {
    assertEquals(WorldContentAuthority.OPEN,
      WorldContentCatalog.entry(WorldNodeId("level-0.23"))?.authority)
    assertEquals(WorldContentAuthority.OPEN,
      WorldContentCatalog.entry(WorldNodeId("level-0.3"))?.authority)
    assertEquals(WorldContentAuthority.EXTERNAL_REFERENCE,
      WorldContentCatalog.entry(WorldNodeId("level-10.2"))?.authority)
    assertEquals(WorldContentAuthority.OPEN,
      WorldContentCatalog.namedSections.first { it.key == "ls-2" }.authority)
  }
}
