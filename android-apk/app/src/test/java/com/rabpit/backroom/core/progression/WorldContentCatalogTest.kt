package com.rabpit.backroom.core.progression

import org.junit.Assert.*
import org.junit.Test

class WorldContentCatalogTest {

  @Test fun metadataCoversEveryRegisteredLevelAndNumericSublevelExactlyOnce() {
    val content = WorldContentCatalog.levels + WorldContentCatalog.sublevels
    val ids = content.map { it.nodeId }
    assertEquals(ids.size, ids.toSet().size)
    assertEquals(14, WorldContentCatalog.levels.size)
    assertEquals(24, WorldContentCatalog.sublevels.size)
    assertEquals(WorldProgressionCore.NODES.map { it.id }.toSet(), ids.toSet())
    assertTrue(content.all { it.title.isNotBlank() })
    content.forEach { assertEquals(it, WorldContentCatalog.entry(it.nodeId)) }
    assertNull(WorldContentCatalog.entry(WorldNodeId("level-999")))
  }

  @Test fun projectCanonWinsOverExternalTitlesAndHardLocks() {
    assertNull(WorldContentCatalog.entry(WorldNodeId("level-0.7")))
    assertNull(WorldContentCatalog.entry(WorldNodeId("level-0.1")))
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
    assertEquals(64, WorldProgressionCore.EDGES.size)
    val selected = setOf("level-0.2", "level-1.2", "level-1.5", "level-5.1",
      "level-6.1", "level-7.7", "level-10.1", "level-11.3")
    assertTrue(WorldProgressionCore.EDGES.all { edge ->
      edge.from in main || edge.from.value in selected
    })
    assertTrue(WorldProgressionCore.EDGES.all { edge ->
      edge.to in main || edge.to.value in selected
    })
  }

  @Test fun openAndReferenceOnlyEntriesAreNotUpgradedToCanon() {
    assertNull(WorldContentCatalog.entry(WorldNodeId("level-0.23")))
    assertNull(WorldContentCatalog.entry(WorldNodeId("level-0.3")))
    assertEquals(WorldContentAuthority.EXTERNAL_REFERENCE,
      WorldContentCatalog.entry(WorldNodeId("level-10.2"))?.authority)
    assertEquals(WorldContentAuthority.OPEN,
      WorldContentCatalog.namedSections.first { it.key == "ls-2" }.authority)
  }
}
