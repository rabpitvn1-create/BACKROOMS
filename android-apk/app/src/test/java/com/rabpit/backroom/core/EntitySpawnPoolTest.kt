package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for Core-authoritative spawn mechanism.
 * Verifies per-level 35% Entity encounters and the existing NPC eligibility rules.
 */
class EntitySpawnPoolTest {

  private fun stateAt(id: String): GameState = GameState.initial().copy(world = mapOf(
    "levelJson" to JSONObject().put("id", id).toString()))

  @Test fun `all twenty two entities have a resident or approved roaming pool`() {
    val all = EntitySpawnPool.COMMON + EntitySpawnPool.LIFEFORM + EntitySpawnPool.RARE + EntitySpawnPool.KILLERS
    val residents = EntitySpawnPool.RESIDENTS_BY_LEVEL.values.flatten().toSet()
    assertEquals(22, all.size)
    assertEquals(all.toSet(), residents + EntitySpawnPool.ROAMING.toSet())
    assertTrue(residents.intersect(EntitySpawnPool.ROAMING.toSet()).isEmpty())
  }

  @Test fun `resident pools follow project canon instead of a global roster`() {
    assertEquals(setOf("hound", "clump", "duller", "deathmoth", "hostile_faceling", "false_puddle", "paintings"), EntitySpawnPool.poolForLevel("1").toSet())
    assertEquals(setOf("clump", "hound", "smiler", "skin-stealer", "predatory_window", "biological_pipeline"), EntitySpawnPool.poolForLevel("2").toSet())
    assertEquals(setOf("deathmoth", "wretch", "skin-stealer", "cable_mimic"), EntitySpawnPool.poolForLevel("3").toSet())
    assertEquals(setOf("the_beast_of_level_5", "predatory_window", "skin-stealer", "hound", "hotel_corpse_lure"), EntitySpawnPool.poolForLevel("5").toSet())
    assertFalse(EntitySpawnPool.poolForLevel("1").contains("the_beast_of_level_5"))
  }

  @Test fun `empty levels and exact sublevels use only the approved roaming pool`() {
    for (id in listOf("0", "4", "6", "0.1", "0.2", "0.5", "0.7", "1.2", "1.5", "5.1", "6.1", "manila_room", "office_market", "unknown")) {
      assertTrue(EntitySpawnPool.residentsForLevel(id).isEmpty())
      assertEquals(EntitySpawnPool.ROAMING, EntitySpawnPool.poolForLevel(id))
    }
  }

  @Test fun `every registered node has exactly 3500 successful tickets and only local species`() {
    val ids = listOf("0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms", "dullness", "epsilon", "ls_2",
      "1", "1.2", "1.5", "traders_vault", "base_alpha", "2", "office_space_el3a", "3", "4", "office_market", "5", "5.1", "6", "6.1")
    assertEquals(LevelRouteRegistry.size(), ids.size)
    for (id in ids) {
      var hits = 0
      val observed = mutableSetOf<String>()
      for (ticket in 0 until 10000) {
        var calls = 0
        val entity = EntitySpawnPool.rollEntity(id) { bound -> calls++; assertEquals(10000, bound); ticket }
        assertEquals("Only one encounter roll at $id", 1, calls)
        if (entity != null) { hits++; observed += entity }
      }
      assertEquals("Total chance at $id must not depend on roster size", 3500, hits)
      assertEquals(EntitySpawnPool.poolForLevel(id).toSet(), observed)
    }
  }

  @Test fun `35 percent boundary is exact for resident and roaming levels`() {
    for (id in listOf("1", "2", "3", "5", "0", "4", "6", "1.2")) {
      assertNotNull(EntitySpawnPool.rollEntity(id) { 3499 })
      assertNull(EntitySpawnPool.rollEntity(id) { 3500 })
      assertNull(EntitySpawnPool.rollEntity(id) { 9999 })
    }
  }

  @Test fun `successful species selection retains rarity weights within the level`() {
    val counts = (1..3500).map { EntitySpawnPool.pickForSuccessfulRoll("5", it) }.groupingBy { it }.eachCount()
    assertEquals(319, counts["the_beast_of_level_5"])
    assertTrue(counts.getValue("hound") in 795..796)
    assertEquals(3500, counts.values.sum())
  }

  @Test fun `runtime uses exact level id and falls back safely when location is missing`() {
    for (id in listOf("1", "2", "3", "5", "1.2", "5.1", "0", "4", "6")) {
      val state = stateAt(id)
      for (ticket in listOf(0, 3499, 3500, 9999)) {
        val result = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi về phía trước") { ticket }
        assertEquals(EntitySpawnPool.rollEntity(id) { ticket }, result.entityKey)
      }
    }
    for (raw in listOf("{}", "broken json", "{\"id\":\"missing\"}")) {
      val state = GameState.initial().copy(world = mapOf("levelJson" to raw))
      assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi") { 0 }.entityKey in EntitySpawnPool.ROAMING)
    }
  }

  // --- NPC thresholds ---

  @Test fun `NPC thresholds match baseline`() {
    assertEquals(5000, NpcSpawnTable.LUCIA_THRESHOLD)
    assertEquals(25, NpcSpawnTable.LUC_TRAM_THRESHOLD)
    assertEquals(25, NpcSpawnTable.IRIS_THRESHOLD)
    assertEquals(25, NpcSpawnTable.SYVIAL_THRESHOLD)
    assertEquals(25, NpcSpawnTable.AN_NHIEN_THRESHOLD)
    // Ambient crowd NPC replaced the old Survivor 2% branch (2026-10-15)
    assertEquals(3000, NpcSpawnTable.AMBIENT_THRESHOLD)
  }

  @Test fun `direct npc table rejects non execute kinds without rng`() {
    var rngCalls = 0
    val encounters = NpcSpawnTable.rollNpcs(
      GameState.initial(), ActionKind.SEARCH, "đi tìm người sống sót", 4
    ) { _ -> rngCalls++; 0 }
    assertTrue(encounters.isEmpty())
    assertEquals(0, rngCalls)
  }


  @Test fun `unicode action boundaries distinguish psychic reconnaissance from movement`() {
    assertFalse(PhysicalActionDetector.isPhysical("Thả thần thức đi điều tra"))
    assertFalse(PhysicalActionDetector.isPhysical("Dùng thần thức đi quét hành lang"))
    assertTrue(PhysicalActionDetector.isPhysical("Thả thần thức đi điều tra rồi bước tới"))
    assertFalse(PhysicalActionDetector.isPhysical("Xem điểm kinh nghiệm"))
    assertTrue(PhysicalActionDetector.isPhysical("Cao Minh đi qua hành lang"))
    assertTrue(PhysicalActionDetector.isPhysical("Mở cửa"))
    assertTrue(PhysicalActionDetector.isSceneFacing("Thả thần thức đi điều tra"))
    assertFalse(PhysicalActionDetector.isSceneFacing("Nói chuyện với Lucia"))
    assertFalse(PhysicalActionDetector.isSceneFacing("Hỏi Lucia về việc khảo sát hành lang"))
    assertFalse(PhysicalActionDetector.isSceneFacing("Điểm kinh nghiệm"))
  }

  // --- Archived legacy ambient-NPC mechanic ---

  @Test fun `legacy crowd NPC never rolls from any freeform action`() {
    val state = GameState.initial().copy(world = mapOf(
      "flagsJson" to """{"entityEncountersAllowed":false,"lucia":{"spawned":true}}"""
    ))
    var calls = 0
    for (action in listOf("đi khám phá", "quan sát xung quanh", "tìm đường", "hỏi tiếp")) {
      val result = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, action) { _ ->
        calls++; 0
      }
      assertNull(result.entityKey)
      assertTrue(result.npcEncounters.isEmpty())
      assertFalse(result.ambientNpcRolled)
    }
    assertEquals("Archived ambient feature must never consume RNG", 0, calls)
  }

  @Test fun `typed explorer movement search and dialogue never roll ambient NPC`() {
    val state = GameState.initial().copy(world = mapOf(
      "flagsJson" to """{"entityEncountersAllowed":false,"lucia":{"spawned":true}}"""
    ))
    var calls = 0
    for (kind in listOf("MOVE", "SEARCH", "TALK")) {
      val result = SpawnMechanism.rollSceneSpawns(state, kind) { _ -> calls++; 0 }
      assertNull(result.entityKey)
      assertTrue(result.npcEncounters.isEmpty())
      assertFalse(result.ambientNpcRolled)
    }
    assertEquals(0, calls)
  }

  @Test fun `stale legacy ambient marker is cleared without generating an encounter`() {
    val state = GameState.initial().copy(metadata =
      GameState.initial().metadata + (SpawnMechanism.AMBIENT_NPC_METADATA_KEY to "true"))
    assertFalse(SpawnMechanism.ambientNpcRolled(state))
    val after = SpawnMechanism.applySpawns(state, SpawnResult(null, emptyList(), ambientNpcRolled = true))
    assertFalse(after.metadata.containsKey(SpawnMechanism.AMBIENT_NPC_METADATA_KEY))
    assertFalse(SpawnMechanism.ambientNpcRolled(after))
    assertEquals(after, SpawnMechanism.clearAmbientNpcMarker(after))
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
    val result1 = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE) { bound -> rng1.nextInt(bound) }
    val result2 = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE) { bound -> rng2.nextInt(bound) }
    assertEquals(result1.entityKey, result2.entityKey)
    assertEquals(result1.npcEncounters.size, result2.npcEncounters.size)
  }

  // --- Eligibility gates (baseline @ 202737c2) ---

  @Test fun `meta turn skips all spawns`() {
    val state = GameState.initial()
    // Even with a seed that would normally spawn, meta=true → nothing
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXECUTE, "đi khám phá",
      isMeta = true, inCombatOrPending = false
    ) { _ -> 0 }  // roll=1 → would spawn a roaming Entity
    assertNull("Meta turn must not spawn entity", result.entityKey)
    assertTrue("Meta turn must not spawn NPCs", result.npcEncounters.isEmpty())
  }

  @Test fun `combat pending skips all spawns`() {
    val state = GameState.initial()
    val result = SpawnMechanism.rollSpawns(
      state, ActionKind.EXECUTE, "đi khám phá",
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
      state, ActionKind.EXECUTE, "đi khám phá",
      isMeta = false, inCombatOrPending = false
    ) { _ -> 0 }  // roll=1 → would spawn a roaming Entity
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
