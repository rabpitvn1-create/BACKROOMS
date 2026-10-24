package com.rabpit.backroom.core.canon

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Level0SupplementStoreTest {
  private fun record(id: String, visibility: String, alias: String) = JSONObject()
    .put("id", id)
    .put("schema_version", 2)
    .put("universe_id", "BACKROOMS_MAIN")
    .put("entity_id", "LEVEL.00")
    .put("domain", "LEVEL")
    .put("kind", "location-card")
    .put("authority", "WORLD_CANON")
    .put("mutability", "IMMUTABLE")
    .put("truth_status", "INFERRED")
    .put("knowledge_visibility", visibility)
    .put("text", "Level 0 environment; routes and entities not authorized.")
    .put("aliases", JSONArray().put(alias))
    .put("activation", JSONObject()
      .put("level_scope", "LEVEL.00")
      .put("min_evidence", "INFERRED"))
    .put("source", JSONObject()
      .put("document_id", "TEST_LEVEL0")
      .put("anchor", id))

  private fun pack(vararg items: JSONObject) = JSONObject()
    .put("schemaVersion", 2)
    .put("records", JSONArray().also { array -> items.forEach { array.put(it) } })
    .put("recordCount", items.size)
    .toString()

  @Test fun supplementalNodesUseExactLevelScopeAndRemainSeparateFromLegacySeed() {
    val seed = pack(record("LEVEL.00", "WORLD", "the lobby"))
    val nodes = pack(record("LEVEL.NODE.0.ZONE_01", "ACTOR_AWARE", "level0:0/zone_01"))
    val store = JsonCanonStore.parseWithSupplement(seed, nodes)
    assertEquals(2, store.snapshot().recordCount)

    val found = store.loadRecordsByIds(
      setOf("LEVEL.00", "LEVEL.NODE.0.ZONE_01"), store.snapshot()
    ).map { it.id }.toSet()
    assertEquals(setOf("LEVEL.00", "LEVEL.NODE.0.ZONE_01"), found)

    val l0 = CanonScope(levelId = "LEVEL.00", povActorId = "cao-minh",
      knownByActorIds = setOf("cao-minh"))
    val l1 = l0.copy(levelId = "LEVEL.01")
    assertEquals(setOf("LEVEL.NODE.0.ZONE_01"),
      store.resolveAliases(setOf("level0:0/zone_01"), l0))
    assertTrue(store.resolveAliases(setOf("level0:0/zone_01"), l1).isEmpty())
  }

  @Test fun existingMetaAndWorldVisibilityAreReadableByCr2() {
    val store = JsonCanonStore.parse(pack(
      record("LEVEL.00", "WORLD", "the lobby"),
      record("GAME.TEST", "META", "test gm rule")
    ))
    assertEquals(2, store.snapshot().recordCount)
  }

  @Test fun duplicateIdsAcrossPacksFailInsteadOfOverwritingCanon() {
    val duplicate = record("LEVEL.00", "WORLD", "the lobby")
    try {
      JsonCanonStore.parseWithSupplement(pack(duplicate), pack(duplicate))
      fail("duplicate canon id must fail")
    } catch (e: CanonStoreException) {
      assertTrue(e.message?.contains("Failed to parse canon database") == true)
    }
  }
}
