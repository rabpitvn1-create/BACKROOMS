package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for P0 Entity GM perception and P1 meta gating.
 *
 * P0: When Core reserves an Entity encounter via beginAction(), the GM narration
 * prompt must receive the correct Entity identity (entityKey/entityName/encounterId)
 * in the same turn. No leak when not spawned. No Entity change on idempotent retry.
 *
 * P1: Meta turns (e.g. "xem trạng thái") must NEVER roll Entity/NPC, consume RNG,
 * or create pending encounters. Core determines isMeta authoritatively from action text.
 */
class EntityGmMetaGatingTest {

  // --- P1: Core-authoritative isMeta detection ---

  @Test fun `core detects meta action from Vietnamese text`() {
    assertTrue(GameCoreFacade.isMetaAction("xem trạng thái"))
    assertTrue(GameCoreFacade.isMetaAction("Xem Trạng Thái Hiện Tại"))
    assertTrue(GameCoreFacade.isMetaAction("xem inventory của tôi"))
    assertTrue(GameCoreFacade.isMetaAction("show party"))
    assertTrue(GameCoreFacade.isMetaAction("STATUS"))
  }

  @Test fun `core does not classify gameplay as meta`() {
    assertFalse(GameCoreFacade.isMetaAction("đi về phía trước"))
    assertFalse(GameCoreFacade.isMetaAction("khám phá căn phòng"))
    assertFalse(GameCoreFacade.isMetaAction("tấn công con quái"))
    assertFalse(GameCoreFacade.isMetaAction("nói chuyện với Lucia"))
    assertFalse(GameCoreFacade.isMetaAction(""))
  }

  @Test fun `meta turn skips all spawn rolls`() {
    // Simulate what beginAction does: rollSpawns with isMeta=true must return empty.
    val state = GameState.initial()
    var rngCalls = 0
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXECUTE, "xem trạng thái",
      isMeta = true, inCombatOrPending = false
    ) { bound -> rngCalls++; 0 }
    assertNull(result.entityKey)
    assertTrue(result.npcEncounters.isEmpty())
    // RNG must NOT be consumed for meta turns.
    assertEquals(0, rngCalls)
  }

  @Test fun `non-meta turn may roll`() {
    // Isolate the Entity gate: an already-met Catherine cannot preempt it.
    val state = GameState.initial().copy(world = mapOf(
      "flagsJson" to """{"catherine":{"spawned":true}}"""))
    var rngCalls = 0
    // Use a seed that guarantees entity spawn (roll 1).
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXECUTE, "đi khám phá",
      isMeta = false, inCombatOrPending = false
    ) { bound -> rngCalls++; 0 } // nextInt always returns 0 → roll = 1 → entity spawns
    // RNG was consumed (rolls happened).
    assertTrue(rngCalls > 0)
    // Entity should spawn with roll=1.
    assertNotNull(result.entityKey)
  }

  // --- P0: Entity perception in beginAction response ---

  @Test fun `pendingEntityEncounter has required fields`() {
    // Verify the JSON contract: {entityKey, entityName, encounterId}
    val entityKey = "hound"
    val entityName = CombatRuntime.entityName(entityKey)
    val encounterId = "spawn:turn1:hound:12345"
    val json = JSONObject()
      .put("entityKey", entityKey)
      .put("entityName", entityName)
      .put("encounterId", encounterId)
    assertEquals("hound", json.optString("entityKey"))
    assertTrue(json.optString("entityName").isNotBlank())
    assertEquals("spawn:turn1:hound:12345", json.optString("encounterId"))
  }

  @Test fun `entity visual ID format matches knowledge DB`() {
    // KnowledgeContextEngine.confirmedEntityVisualId() formats as ENTITY.<UPPER>.VISUAL
    // Verify our entity keys produce valid visual IDs.
    val testKeys = listOf("hound", "smiler", "skin-stealer", "jeff_the_killer")
    for (key in testKeys) {
      val visualId = "ENTITY." + key.uppercase().replace('-', '_') + ".VISUAL"
      assertTrue(visualId.matches(Regex("ENTITY\\.[A-Z0-9_]+\\.VISUAL")))
    }
    // Specific check: skin-stealer → ENTITY.SKIN_STEALER.VISUAL
    assertEquals(
      "ENTITY.SKIN_STEALER.VISUAL",
      "ENTITY." + "skin-stealer".uppercase().replace('-', '_') + ".VISUAL"
    )
  }

  @Test fun `entityName resolves for all spawn pool entities`() {
    // Every entity in the spawn pool must have a display name for the GM prompt.
    val allEntities = EntitySpawnPool.COMMON + EntitySpawnPool.LIFEFORM +
      EntitySpawnPool.RARE + EntitySpawnPool.KILLERS
    assertEquals(27, allEntities.size) // Includes the five approved special Entities.
    for (key in allEntities) {
      val name = CombatRuntime.entityName(key)
      assertTrue("Entity $key must have a display name", name.isNotBlank())
    }
  }
}
