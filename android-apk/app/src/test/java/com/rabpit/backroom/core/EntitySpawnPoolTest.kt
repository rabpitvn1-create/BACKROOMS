package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for Core-authoritative spawn mechanism.
 * Verifies exact rates from pre-Milestone baseline (b7929e00~1).
 */
class EntitySpawnPoolTest {

  // --- Entity pool weights ---

  @Test fun `pool weights total 4500bp (45 percent)`() {
    val total = EntitySpawnPool.COMMON.size * EntitySpawnPool.COMMON_BP +
      EntitySpawnPool.LIFEFORM.size * EntitySpawnPool.LIFEFORM_BP +
      EntitySpawnPool.RARE.size * EntitySpawnPool.RARE_BP +
      EntitySpawnPool.KILLERS.size * EntitySpawnPool.KILLER_BP
    assertEquals(4500, total)
  }

  @Test fun `pool has 22 entities`() {
    assertEquals(14, EntitySpawnPool.COMMON.size)
    assertEquals(3, EntitySpawnPool.LIFEFORM.size)
    assertEquals(3, EntitySpawnPool.RARE.size)
    assertEquals(2, EntitySpawnPool.KILLERS.size)
  }

  // --- pickForSuccessfulRoll boundary tests ---

  @Test fun `roll 1 selects first common`() {
    assertEquals("hound", EntitySpawnPool.pickForSuccessfulRoll(1))
  }

  @Test fun `roll 250 selects first common (boundary)`() {
    // ticket = 249, 249/250 = 0 -> COMMON[0]
    assertEquals("hound", EntitySpawnPool.pickForSuccessfulRoll(250))
  }

  @Test fun `roll 251 selects second common`() {
    // ticket = 250, 250/250 = 1 -> COMMON[1]
    assertEquals("clump", EntitySpawnPool.pickForSuccessfulRoll(251))
  }

  @Test fun `roll 3500 selects last common`() {
    // ticket = 3499, 3499/250 = 13 -> COMMON[13]
    assertEquals("hotel_corpse_lure", EntitySpawnPool.pickForSuccessfulRoll(3500))
  }

  @Test fun `roll 3501 selects first lifeform`() {
    // ticket = 3500 -> -=3500 = 0, 0/200 = 0 -> LIFEFORM[0]
    assertEquals("blackroot_sentinel", EntitySpawnPool.pickForSuccessfulRoll(3501))
  }

  @Test fun `roll 4100 selects last lifeform`() {
    // ticket = 4099 -> -=3500 = 599, 599/200 = 2 -> LIFEFORM[2]
    assertEquals("hollow_grasper", EntitySpawnPool.pickForSuccessfulRoll(4100))
  }

  @Test fun `roll 4101 selects first rare`() {
    // ticket = 4100 -> -=3500 = 600 -> -=600 = 0, 0/100 = 0 -> RARE[0]
    assertEquals("the_beast_of_level_5", EntitySpawnPool.pickForSuccessfulRoll(4101))
  }

  @Test fun `roll 4400 selects last rare`() {
    // ticket = 4399 -> -=3500 = 899 -> -=600 = 299, 299/100 = 2 -> RARE[2]
    assertEquals("diep_minh", EntitySpawnPool.pickForSuccessfulRoll(4400))
  }

  @Test fun `roll 4401 selects first killer`() {
    // ticket = 4400 -> -=3500 = 900 -> -=600 = 300 -> -=300 = 0, 0/50 = 0 -> KILLERS[0]
    assertEquals("jeff_the_killer", EntitySpawnPool.pickForSuccessfulRoll(4401))
  }

  @Test fun `roll 4500 selects last killer`() {
    // ticket = 4499 -> -=3500 = 999 -> -=600 = 399 -> -=300 = 99, 99/50 = 1 -> KILLERS[1]
    assertEquals("jane_the_killer", EntitySpawnPool.pickForSuccessfulRoll(4500))
  }

  // --- rollEntity with fixed seed ---

  @Test fun `rollEntity returns null for rolls above 4500`() {
    // nextInt returns 9999 -> roll = 10000 > 4500 -> null
    val result = EntitySpawnPool.rollEntity { 9999 }
    assertNull(result)
  }

  @Test fun `rollEntity returns entity for rolls 1 to 4500`() {
    // nextInt returns 0 -> roll = 1 <= 4500 -> entity
    val result = EntitySpawnPool.rollEntity { 0 }
    assertNotNull(result)
    assertEquals("hound", result)
  }

  @Test fun `rollEntity distribution matches 45 percent over fixed seed`() {
    val rng = Random(42)
    var hits = 0
    val total = 10000
    repeat(total) {
      if (EntitySpawnPool.rollEntity { bound -> rng.nextInt(bound) } != null) hits++
    }
    // 45% of 10000 = 4500, allow ±3% tolerance for random variance
    assertTrue("Expected ~4500 hits, got $hits", hits in 4200..4800)
  }

  // --- NPC thresholds ---

  @Test fun `NPC thresholds match baseline`() {
    assertEquals(5000, NpcSpawnTable.LUCIA_THRESHOLD)
    assertEquals(25, NpcSpawnTable.LUC_TRAM_THRESHOLD)
    assertEquals(25, NpcSpawnTable.IRIS_THRESHOLD)
    assertEquals(25, NpcSpawnTable.SYVIAL_THRESHOLD)
    assertEquals(25, NpcSpawnTable.AN_NHIEN_THRESHOLD)
    // Ambient crowd NPC replaced the old Survivor 2% branch (2026-10-15)
    assertEquals(1000, NpcSpawnTable.AMBIENT_THRESHOLD)
  }

  // --- Ambient NPC 10% roll (ephemeral, never a Canon encounter) ---

  @Test fun `ambient roll fires on eligible turn with no entity or canon NPC`() {
    val state = GameState.initial()
    // Force: entity roll fails (roll=10000), Lucia roll fails, ambient succeeds (roll=1)
    // Call order on Level 0 EXPLORE: entity(1), Lucia(2), ambient(3).
    // LucTram/Iris/Syvial/AnNhien rolls are skipped (level/eligibility/legacy).
    var calls = 0
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = false, inCombatOrPending = false
    ) { _ ->
      calls++
      if (calls <= 2) 9999 else 0
    }
    assertNull("No entity should spawn", result.entityKey)
    assertTrue("No canon NPC should spawn", result.npcEncounters.isEmpty())
    assertTrue("Ambient should roll on eligible turn", result.ambientNpcRolled)
  }

  @Test fun `ambient roll skipped when entity spawns this turn (entity priority)`() {
    val state = GameState.initial()
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = false, inCombatOrPending = false
    ) { _ -> 0 }  // roll=1 → entity spawns (hound)
    assertNotNull("Entity should spawn", result.entityKey)
    assertFalse("Ambient must yield to entity", result.ambientNpcRolled)
  }

  @Test fun `ambient roll skipped when canon NPC spawns (canon priority)`() {
    val state = GameState.initial()
    // Force Lucia success (roll=1) on Level 0 EXPLORE; entity fails
    var calls = 0
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = false, inCombatOrPending = false
    ) { _ ->
      calls++
      // entity(1): fail, Lucia(2): success, rest fail, ambient: would succeed but must not roll
      when (calls) {
        1 -> 9999  // entity fails
        2 -> 0     // Lucia succeeds
        else -> 9999
      }
    }
    assertTrue("Lucia should spawn", result.npcEncounters.any { it.characterId == LUCIA_ID })
    assertFalse("Ambient must yield to canon NPC", result.ambientNpcRolled)
  }

  @Test fun `ambient roll skipped on meta and combat turns`() {
    val state = GameState.initial()
    val meta = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = true, inCombatOrPending = false
    ) { _ -> 0 }
    assertFalse("Meta turn must not roll ambient", meta.ambientNpcRolled)

    val combat = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = false, inCombatOrPending = true
    ) { _ -> 0 }
    assertFalse("Combat turn must not roll ambient", combat.ambientNpcRolled)
  }

  @Test fun `ambient marker is ephemeral metadata not a canon encounter`() {
    val state = GameState.initial()
    val result = SpawnResult(entityKey = null, npcEncounters = emptyList(), ambientNpcRolled = true)
    val updated = SpawnMechanism.applySpawns(state, result)

    // Ephemeral marker set in metadata
    assertTrue(SpawnMechanism.ambientNpcRolled(updated))
    assertEquals("true", updated.metadata[SpawnMechanism.AMBIENT_NPC_METADATA_KEY])

    // NOT a Canon encounter: no flags, no CharacterState created
    val flags = JSONObject(updated.world["flagsJson"] ?: "{}")
    assertFalse("No survivor flag should exist", flags.has("survivor"))
    assertNull("No survivor character should be created", updated.characters["survivor"])
    assertTrue("No NPC encounters applied", updated.characters.keys.none { it == "survivor" })
  }

  @Test fun `ambient marker can be cleared after consumption`() {
    val state = GameState.initial()
    val marked = SpawnMechanism.applySpawns(
      state, SpawnResult(null, emptyList(), ambientNpcRolled = true))
    assertTrue(SpawnMechanism.ambientNpcRolled(marked))
    val cleared = SpawnMechanism.clearAmbientNpcMarker(marked)
    assertFalse(SpawnMechanism.ambientNpcRolled(cleared))
    assertFalse(cleared.metadata.containsKey(SpawnMechanism.AMBIENT_NPC_METADATA_KEY))
    // Clearing absent marker is a safe no-op
    assertEquals(cleared, SpawnMechanism.clearAmbientNpcMarker(cleared))
  }

  @Test fun `ambient roll distribution is roughly 10 percent`() {
    val state = GameState.initial()
    val rng = Random(42)
    var hits = 0
    val total = 5000
    repeat(total) {
      // EXPLORE on high level with no canon NPCs eligible: entity may fire,
      // ambient only counts when nothing else did
      val result = SpawnMechanism.rollSpawns(
        state, ActionKind.SEARCH, "tìm kiếm xung quanh",
        isMeta = false, inCombatOrPending = false
      ) { bound -> rng.nextInt(bound) }
      if (result.ambientNpcRolled) hits++
    }
    // Ambient fires only when entity (45%) and NPCs miss; rough sanity bound:
    // P(ambient) = P(no entity) * P(no NPC) * 0.10 ≈ 0.55 * ~1.0 * 0.10 ≈ 5.5%
    // Allow wide tolerance: 2%..10% of all turns
    assertTrue("Expected ambient hits in 2%..10% range, got $hits/$total",
      hits in (total * 0.02).toInt()..(total * 0.10).toInt())
  }

  // --- SpawnMechanism integration ---

  @Test fun `applySpawns sets entityEncounterKey flag`() {
    val state = GameState.initial()
    val result = SpawnResult(entityKey = "smiler", npcEncounters = emptyList())
    val updated = SpawnMechanism.applySpawns(state, result)
    val flags = JSONObject(updated.world["flagsJson"] ?: "{}")
    assertEquals("smiler", flags.optString("entityEncounterKey"))
  }

  @Test fun `applySpawns sets NPC encountered metadata for recruitFollower`() {
    val state = GameState.initial()
    // An Nhien exists in initial state
    val result = SpawnResult(
      entityKey = null,
      npcEncounters = listOf(NpcSpawnTable.NpcEncounter(AN_NHIEN_ID, "anNhien"))
    )
    val updated = SpawnMechanism.applySpawns(state, result)
    val character = updated.characters[AN_NHIEN_ID]
    assertNotNull(character)
    assertEquals("true", character!!.metadata["encountered"])
    assertNotNull(character.metadata["encounterId"])
    // Flag also set for once-only protection
    val flags = JSONObject(updated.world["flagsJson"] ?: "{}")
    assertTrue(flags.optJSONObject("anNhien")?.optBoolean("encountered") == true)
  }

  @Test fun `applySpawns creates Lucia character if not in state`() {
    // Fresh runs may already contain Lucia's canon record; explicitly remove it
    // to exercise the missing-character fallback without changing production Canon.
    val state = GameState.initial().let { it.copy(characters = it.characters - LUCIA_ID) }
    // The spawn mechanism reconstructs only the genuinely missing character.
    assertNull(state.characters[LUCIA_ID])
    val result = SpawnResult(
      entityKey = null,
      npcEncounters = listOf(NpcSpawnTable.NpcEncounter(LUCIA_ID, "lucia"))
    )
    val updated = SpawnMechanism.applySpawns(state, result)
    val lucia = updated.characters[LUCIA_ID]
    assertNotNull("Lucia should be created from Canon", lucia)
    assertEquals("true", lucia!!.metadata["encountered"])
  }

  @Test fun `separated Iris and Syvial can reunite without clearing once-only spawn history`() {
    for ((id, key) in listOf(IRIS_ID to "iris", SYVIAL_ID to "syvial")) {
      val met = SpawnMechanism.applySpawns(GameState.initial(),
        SpawnResult(null, listOf(NpcSpawnTable.NpcEncounter(id, key))))
      val flags = JSONObject(met.world["flagsJson"] ?: "{}")
      val existing = flags.getJSONObject(key)
      assertTrue(existing.getBoolean("spawned"))
      assertTrue(existing.getBoolean("present"))
      val separated = met.copy(
        characters = met.characters + (id to met.characters.getValue(id)
          .copy(presence = CharacterPresence.SEPARATED)),
        world = met.world + ("flagsJson" to flags.put(key,
          existing.put("present", false).put("continuity", "SEPARATED")).toString())
      )
      val reunion = NpcSpawnTable.rollNpcs(separated, ActionKind.EXECUTE,
        "đi về phía trước", 2) { 0 }
      assertTrue("Expected $id reunion", reunion.any { it.characterId == id })
      val missing = separated.copy(characters = separated.characters +
        (id to separated.characters.getValue(id).copy(presence = CharacterPresence.MISSING)))
      assertTrue("Missing $id may also reunite", NpcSpawnTable.rollNpcs(missing,
        ActionKind.EXECUTE, "đi về phía trước", 2) { 0 }
        .any { it.characterId == id })
      val deceased = separated.copy(characters = separated.characters +
        (id to separated.characters.getValue(id).copy(presence = CharacterPresence.DEAD)))
      assertFalse("Dead $id cannot reappear via reunion", NpcSpawnTable.rollNpcs(deceased,
        ActionKind.EXECUTE, "đi về phía trước", 2) { 0 }
        .any { it.characterId == id })
      val returning = SpawnMechanism.applySpawns(separated,
        SpawnResult(null, listOf(NpcSpawnTable.NpcEncounter(id, key))))
      assertEquals(CharacterPresence.ACTIVE, returning.characters.getValue(id).presence)
      assertTrue(JSONObject(returning.world.getValue("flagsJson"))
        .getJSONObject(key).getBoolean("present"))
      val blocked = NpcSpawnTable.rollNpcs(returning, ActionKind.EXECUTE,
        "đi về phía trước", 2) { 0 }
      assertFalse("No duplicate meeting while $id is present",
        blocked.any { it.characterId == id })
    }
  }

  @Test fun `rollSpawns with fixed seed is deterministic`() {
    val state = GameState.initial()
    val rng1 = Random(12345)
    val rng2 = Random(12345)
    val result1 = SpawnMechanism.rollSpawns(state, ActionKind.EXPLORE) { bound -> rng1.nextInt(bound) }
    val result2 = SpawnMechanism.rollSpawns(state, ActionKind.EXPLORE) { bound -> rng2.nextInt(bound) }
    assertEquals(result1.entityKey, result2.entityKey)
    assertEquals(result1.npcEncounters.size, result2.npcEncounters.size)
  }

  // --- Eligibility gates (baseline @ 202737c2) ---

  @Test fun `meta turn skips all spawns`() {
    val state = GameState.initial()
    // Even with a seed that would normally spawn, meta=true → nothing
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = true, inCombatOrPending = false
    ) { _ -> 0 }  // roll=1 → would spawn hound
    assertNull("Meta turn must not spawn entity", result.entityKey)
    assertTrue("Meta turn must not spawn NPCs", result.npcEncounters.isEmpty())
  }

  @Test fun `combat pending skips all spawns`() {
    val state = GameState.initial()
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = false, inCombatOrPending = true
    ) { _ -> 0 }
    assertNull("Combat/pending must not spawn entity", result.entityKey)
    assertTrue("Combat/pending must not spawn NPCs", result.npcEncounters.isEmpty())
  }

  @Test fun `entityEncountersAllowed=false blocks entity roll`() {
    var state = GameState.initial()
    // Set the flag to false
    val flags = JSONObject(state.world["flagsJson"] ?: "{}")
    flags.put("entityEncountersAllowed", false)
    state = state.copy(world = state.world + ("flagsJson" to flags.toString()))
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXPLORE, "đi khám phá",
      isMeta = false, inCombatOrPending = false
    ) { _ -> 0 }  // roll=1 → would spawn hound
    assertNull("entityEncountersAllowed=false must block entity", result.entityKey)
  }

  @Test fun `dialogue-only EXECUTE is not physical`() {
    // These contain no physical verbs → not physical
    // Note: baseline uses substring matching, so avoid "đi", "tìm", etc.
    assertFalse(PhysicalActionDetector.isPhysical("nói chuyện với Lucia"))
    assertFalse(PhysicalActionDetector.isPhysical("hỏi thăm sức khỏe"))
    assertFalse(PhysicalActionDetector.isPhysical("chào hỏi"))
  }

  @Test fun `movement EXECUTE is physical`() {
    assertTrue(PhysicalActionDetector.isPhysical("đi về phía trước"))
    assertTrue(PhysicalActionDetector.isPhysical("mở cánh cửa"))
    assertTrue(PhysicalActionDetector.isPhysical("chạy nhanh qua hành lang"))
    assertTrue(PhysicalActionDetector.isPhysical("BƯỚC tới"))  // case-insensitive
  }

  @Test fun `dialogue EXECUTE does not trigger NPC rolls`() {
    val state = GameState.initial()
    // Dialogue text with EXECUTE kind: not physical → no NPC encounters
    // (Survivor is not physical-gated, so we check the physical-gated ones)
    val rng = Random(42)
    var physicalNpcFound = false
    repeat(20) {
      val result = SpawnMechanism.rollSpawns(
        state, ActionKind.EXECUTE, "nói chuyện với người lạ",
        isMeta = false, inCombatOrPending = false
      ) { bound -> rng.nextInt(bound) }
      val physicalIds = setOf(LUC_TRAM_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID)
      if (result.npcEncounters.any { it.characterId in physicalIds }) {
        physicalNpcFound = true
      }
    }
    assertFalse("Dialogue-only action must not trigger physical NPC encounters", physicalNpcFound)
  }
}
