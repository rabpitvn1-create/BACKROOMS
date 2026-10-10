package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.ObservationReader.Row
import org.junit.Assert.*
import org.junit.Test

/** M1b.4 retrieval fixtures (issue #501): cross-actor, forged, missing/extra, alias replay. */
class ObservationReaderTest {
  private fun row(id: String, owner: String = "cao_minh", rev: Long = 9) =
    Row(id, owner, "event-9-0", "turn-9", rev, "SEEN", "d$id")

  @Test fun ownerFilterDropsCrossActorRows() {
    val rows = listOf(row("o1", "cao_minh"), row("o2", "luc_tram"), row("o3", "cao_minh"))
    val filtered = ObservationReader.filterOwner(rows, "cao_minh")
    assertEquals(listOf("o1", "o3"), filtered.map { it.observationId })
  }

  @Test fun completeSetVerifiesAndSortsStable() {
    val rows = listOf(row("o2", rev = 9), row("o1", rev = 9))
    val out = ObservationReader.verifyComplete("s", "turn-9", 9, listOf("o1", "o2"), rows)
    assertEquals(listOf("o1", "o2"), out.map { it.observationId })
  }

  @Test fun missingObservationRejected() {
    try {
      ObservationReader.verifyComplete("s", "turn-9", 9, listOf("o1", "o2"), listOf(row("o1")))
      fail("expected load_missing_observations")
    } catch (e: IllegalArgumentException) {
      assertEquals("load_missing_observations", e.message)
    }
  }

  @Test fun extraObservationRejected() {
    try {
      ObservationReader.verifyComplete("s", "turn-9", 9, listOf("o1"), listOf(row("o1"), row("oX")))
      fail("expected load_extra_observations")
    } catch (e: IllegalArgumentException) {
      assertEquals("load_extra_observations", e.message)
    }
  }

  @Test fun futureRevisionRejected() {
    try {
      ObservationReader.verifyComplete("s", "turn-9", 9, listOf("o1"), listOf(row("o1", rev = 10)))
      fail("expected load_future_revision")
    } catch (e: IllegalArgumentException) {
      assertEquals("load_future_revision", e.message)
    }
  }

  @Test fun duplicateManifestRejected() {
    try {
      ObservationReader.verifyComplete("s", "turn-9", 9, listOf("o1", "o1"), listOf(row("o1")))
      fail("expected load_manifest_duplicate")
    } catch (e: IllegalArgumentException) {
      assertEquals("load_manifest_duplicate", e.message)
    }
  }

  @Test fun emptyManifestAndRowsVerifies() {
    assertTrue(ObservationReader.verifyComplete("s", "turn-9", 9, emptyList(), emptyList()).isEmpty())
  }

  @Test fun queryIsBoundedAndSlotOwnerFiltered() {
    // SQL-level guarantees asserted by the sqlite3 harness; here: shape check.
    assertTrue(ObservationReader.QUERY_OBSERVATIONS.contains("WHERE slot_id = ? AND actor_id = ?"))
    assertTrue(ObservationReader.QUERY_OBSERVATIONS.contains("LIMIT ?"))
    assertTrue(ObservationReader.QUERY_OBSERVATIONS.contains("ORDER BY"))
    assertFalse(ObservationReader.QUERY_OBSERVATIONS.contains("record"))
  }
}
