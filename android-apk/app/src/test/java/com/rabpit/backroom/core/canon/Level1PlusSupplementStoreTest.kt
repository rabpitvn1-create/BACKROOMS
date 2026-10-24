package com.rabpit.backroom.core.canon

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** CR2 reads two local supplemental packs; this does NOT exercise route navigation. */
class Level1PlusSupplementStoreTest {
  private fun record(id: String, level: String, status: String = "INFERRED") = JSONObject()
    .put("id", id)
    .put("schema_version", 2)
    .put("universe_id", "BACKROOMS_MAIN")
    .put("entity_id", level)
    .put("domain", "LEVEL")
    .put("kind", "location-card")
    .put("authority", "ASSET_IDENTIFIED_SCENE_DRAFT")
    .put("mutability", "IMMUTABLE")
    .put("truth_status", status)
    .put("knowledge_visibility", "ACTOR_AWARE")
    .put("text", "Scene metadata only, no exits or entity spawns.")
    .put("aliases", JSONArray().put("node:test-" + id))
    .put("activation", JSONObject().put("level_scope", level)
      .put("min_evidence", status))
    .put("source", JSONObject().put("document_id", "TEST").put("anchor", id))

  private fun pack(vararg items: JSONObject) = JSONObject()
    .put("schemaVersion", 2)
    .put("recordCount", items.size)
    .put("records", JSONArray().also { array -> items.forEach { array.put(it) } })
    .toString()

  @Test fun mergesBothSupplementPacksWithoutLeakingAcrossLevels() {
    val base = pack(record("LEVEL.00", "LEVEL.00", "CONFIRMED"))
    val level0 = pack(record("LEVEL.NODE.0.ZONE_01", "LEVEL.00"))
    val later = pack(
      record("LEVEL.07", "LEVEL.07", "CONFIRMED"),
      record("LEVEL.NODE.7", "LEVEL.07"),
      record("LEVEL.11", "LEVEL.11", "CONFIRMED")
    )
    val store = JsonCanonStore.parseWithSupplements(base, listOf(level0, later))
    assertEquals(5, store.snapshot().recordCount)
    val level7 = CanonScope(levelId = "LEVEL.07", povActorId = "cao-minh",
      knownByActorIds = setOf("cao-minh"))
    val level0Scope = level7.copy(levelId = "LEVEL.00")
    val token = "node:test-LEVEL.NODE.7"
    assertEquals(setOf("LEVEL.NODE.7"), store.resolveAliases(setOf(token), level7))
    assertTrue(store.resolveAliases(setOf(token), level0Scope).isEmpty())
    assertNotNull(store.loadRecordsByIds(setOf("LEVEL.11"), store.snapshot()).singleOrNull())
  }

  @Test fun duplicateAcrossEitherSupplementFailsFast() {
    val base = pack(record("LEVEL.00", "LEVEL.00"))
    val pack0 = pack(record("LEVEL.NODE.0.ZONE_01", "LEVEL.00"))
    val pack1 = pack(record("LEVEL.NODE.0.ZONE_01", "LEVEL.01"))
    try {
      JsonCanonStore.parseWithSupplements(base, listOf(pack0, pack1))
      fail("duplicate Canon ID must fail")
    } catch (e: CanonStoreException) {
      assertTrue(e.message?.contains("Failed to parse canon database") == true)
    }
  }
}
