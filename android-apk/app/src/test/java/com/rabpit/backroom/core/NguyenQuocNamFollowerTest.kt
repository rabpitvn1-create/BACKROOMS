package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NguyenQuocNamFollowerTest {
  private fun at(node: String): GameState = GameState.initial().copy(world = mapOf(
    "levelJson" to requireNotNull(LevelRouteRegistry.node(node)).asJson().toString()
  ))

  @Test fun `Nguyen Quoc Nam identity family equipment and portraits are canonical`() {
    val state = GameState.initial()
    val actor = state.characters.getValue(NGUYEN_QUOC_NAM_ID)
    assertEquals("Nguyễn Quốc Nam", actor.name)
    assertEquals("36", actor.metadata["age"])
    assertEquals("2001", actor.metadata["originYear"])
    assertEquals("Nguyễn Hồng Lan", actor.metadata["wife"])
    assertEquals("Nguyễn Thùy Dương", actor.metadata["daughter"])
    assertEquals("6", actor.metadata["daughterAgeAtDisappearance"])
    assertEquals("OUTSIDE_BACKROOMS", actor.metadata["familyWorld"])
    assertEquals("avatars/NguyenQuocNam_avatar.webp", actor.avatarRef)
    assertEquals("NguyenQuocNam.webp", actor.metadata["spriteRef"])
    assertFalse(NGUYEN_QUOC_NAM_ID in state.party.memberIds)
    assertFalse(state.characters.containsKey("nguyen_hong_lan"))
    assertFalse(state.characters.containsKey("nguyen_thuy_duong"))
    val slots = state.equipment.getValue(NGUYEN_QUOC_NAM_ID).slots
    assertEquals(NAM_PISTOL_ID, slots["weapon"])
    assertEquals(NAM_SHIRT_ID, slots["outfit"])
    assertEquals(NAM_WATCH_ID, slots["wrist"])
    assertEquals(NAM_SHOES_ID, slots["footwear"])
    assertEquals(19, EquipmentCatalog.definition(NAM_PISTOL_ID)!!.weapon!!.dmg)
  }

  @Test fun `five normal skills and one ultimate are registered`() {
    val skills = CompanionSkillCatalog.forCharacter(NGUYEN_QUOC_NAM_ID)
    assertEquals(6, skills.size)
    assertEquals(5, skills.count { it.kind != "ULTIMATE" })
    assertEquals(1, skills.count { it.kind == "ULTIMATE" })
    assertEquals("Lời Hứa Trở Về", skills.last().name)
  }

  @Test fun `every existing level sublevel and AREA permits five percent first meeting`() {
    assertEquals(500, NpcSpawnTable.NAM_THRESHOLD)
    val nodes = listOf("0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment",
      "red_rooms", "dullness", "epsilon", "ls_2", "1", "1.2", "1.5",
      "traders_vault", "base_alpha", "2", "office_space_el3a", "3", "4",
      "office_market", "5", "5.1", "6", "6.1")
    assertEquals(LevelRouteRegistry.size(), nodes.size)
    for (node in nodes) {
      val state = at(node)
      for (ticket in listOf(5000, 5499)) {
        val result = SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
        assertTrue("Nam should spawn in $node at $ticket",
          result.npcEncounters.any { it.characterId == NGUYEN_QUOC_NAM_ID })
        assertNull("No Entity conflict in $node", result.entityKey)
      }
      val outside = SpawnMechanism.rollSceneSpawns(state, "MOVE") { 5500 }
      assertFalse(outside.npcEncounters.any { it.characterId == NGUYEN_QUOC_NAM_ID })
    }
  }

  @Test fun `Entity Catherine Janet and Nam remain disjoint exact probability windows`() {
    val state = at("4")
    val results = (0 until 10000).map { ticket ->
      SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
    }
    assertEquals(3500, results.count { it.entityKey != null })
    for (id in listOf(CATHERINE_ID, JANET_ID, NGUYEN_QUOC_NAM_ID)) {
      assertEquals("5 percent first encounter for $id",
        500, results.count { r -> r.npcEncounters.any { it.characterId == id } })
    }
    assertTrue(results.none { r -> r.entityKey != null &&
      r.npcEncounters.any { it.characterId in setOf(CATHERINE_ID, JANET_ID, NGUYEN_QUOC_NAM_ID) } })
    assertTrue(results.none { r ->
      r.npcEncounters.count { it.characterId in setOf(CATHERINE_ID, JANET_ID, NGUYEN_QUOC_NAM_ID) } > 1
    })
  }

  @Test fun `encounter pending does not consume Nam first meeting or recruit him`() {
    val state = at("3")
    val roll = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi tới") { 5000 }
    assertTrue(roll.npcEncounters.any { it.characterId == NGUYEN_QUOC_NAM_ID })
    val pending = SpawnMechanism.prepareSpawns(state, roll)
    assertNull(pending.characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    assertNull(SpawnMechanism.discardPendingNpcEncounters(pending)
      .characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    val confirmed = SpawnMechanism.commitPendingNpcEncounters(pending)
    assertEquals("true", confirmed.characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    assertTrue(JSONObject(confirmed.world.getValue("flagsJson"))
      .getJSONObject("nguyenQuocNam").getBoolean("spawned"))
    assertFalse(NGUYEN_QUOC_NAM_ID in confirmed.party.memberIds)
    assertTrue(SpawnMechanism.rollSceneSpawns(confirmed, "MOVE") { 5000 }
      .npcEncounters.none { it.characterId == NGUYEN_QUOC_NAM_ID })
  }

  @Test fun `meta combat pending and dialogue do not spawn Nam`() {
    val state = at("6")
    assertTrue(NpcSpawnTable.rollNpcs(state, ActionKind.EXECUTE, "nói chuyện", 6) { 5000 }
      .none { it.characterId == NGUYEN_QUOC_NAM_ID })
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE,
      "xem trạng thái", isMeta = true, nextInt = { 5000 }).npcEncounters.isEmpty())
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE,
      "đi tiếp", inCombatOrPending = true, nextInt = { 5000 }).npcEncounters.isEmpty())
  }

  @Test fun `Nam executes defensive skills and first aid only once per encounter`() {
    var state = GameState.initial().copy(
      party = PartyState(memberIds = listOf(CAO_MINH_ID, NGUYEN_QUOC_NAM_ID)))
    val maxHp = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, maxHp - 35)
    state = CombatRuntime.start(state, "diep_minh").withUnlockedSkills(NGUYEN_QUOC_NAM_ID)
    val guard = CombatRuntime.resolve(state, "EXECUTE", "Party phòng thủ")
    assertTrue(guard.reply.contains("Sơ Cứu Tại Chỗ"))
    assertNotNull(guard.state.metadata["combat.nguyenQuocNamFirstAidEncounter"])
    if (CombatRuntime.active(guard.state) != null) {
      val twice = CombatRuntime.resolve(guard.state, "EXECUTE", "Party phòng thủ")
      assertFalse(twice.reply.contains("Sơ Cứu Tại Chỗ"))
    }
    assertTrue(CombatRuntime.resolve(state, "EXECUTE", "Party né tránh").reply.contains("Giữ Khoảng Cách"))
    assertTrue(CombatRuntime.resolve(state, "SEARCH", "quan sát").reply.contains("Đánh Giá Nguy Cơ"))
    assertTrue(CombatRuntime.resolve(state, "EXECUTE", "Party tấn công").reply.contains("Quan Sát Lâm Sàng"))
  }
}
