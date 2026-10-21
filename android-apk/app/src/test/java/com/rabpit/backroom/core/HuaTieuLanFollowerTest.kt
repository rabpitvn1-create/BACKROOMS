package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HuaTieuLanFollowerTest {
  private fun at(node: String): GameState = GameState.initial().copy(world = mapOf(
    "levelJson" to requireNotNull(LevelRouteRegistry.node(node)).asJson().toString()
  ))

  @Test fun `fictional Cholon 1895 daughter has correct age and original art references`() {
    val state = GameState.initial()
    val actor = state.characters.getValue(HUA_TIEU_LAN_ID)
    assertEquals("Hứa Tiểu Lan", actor.name)
    assertEquals("18", actor.metadata["age"])
    assertEquals("1877", actor.metadata["birthYear"])
    assertEquals("1895", actor.metadata["originYear"])
    assertEquals("18th birthday, 1895", actor.metadata["disappearance"])
    assertEquals("Hứa Bổn Hòa (alternate-history fiction)", actor.metadata["father"])
    assertEquals("ALTERNATE_HISTORY_FICTION", actor.metadata["historyScope"])
    assertEquals("avatars/HuaTieuLan_avatar.webp", actor.avatarRef)
    assertEquals("HuaTieuLan.webp", actor.metadata["spriteRef"])
    assertFalse(HUA_TIEU_LAN_ID in state.party.memberIds)
    val slots = state.equipment.getValue(HUA_TIEU_LAN_ID).slots
    assertEquals(TIEU_LAN_RIFLE_ID, slots["weapon"])
    assertEquals(TIEU_LAN_AO_DAI_ID, slots["outfit"])
    assertEquals(TIEU_LAN_JADE_ID, slots["wrist"])
    assertEquals(TIEU_LAN_SHOES_ID, slots["footwear"])
    assertEquals(25, EquipmentCatalog.definition(TIEU_LAN_RIFLE_ID)!!.weapon!!.dmg)
    assertNotNull(EquipmentCatalog.definition(TIEU_LAN_AO_DAI_ID))
    assertNotNull(EquipmentCatalog.definition(TIEU_LAN_JADE_ID))
    assertNotNull(EquipmentCatalog.definition(TIEU_LAN_SHOES_ID))
  }

  @Test fun `exact five normal skills and one Poker Dice ultimate`() {
    val skills = CompanionSkillCatalog.forCharacter(HUA_TIEU_LAN_ID)
    assertEquals(6, skills.size)
    assertEquals(5, skills.count { it.kind != "ULTIMATE" })
    assertEquals(1, skills.count { it.kind == "ULTIMATE" })
    assertEquals("Bách Hoa Nhất Tiễn", skills.last().name)
  }

  @Test fun `exact five percent in every known parent Level sublevel and AREA`() {
    assertEquals(500, NpcSpawnTable.TIEU_LAN_THRESHOLD)
    val nodes = listOf("0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment",
      "red_rooms", "dullness", "epsilon", "ls_2", "1", "1.2", "1.5",
      "traders_vault", "base_alpha", "2", "office_space_el3a", "3", "4",
      "office_market", "5", "5.1", "6", "6.1")
    assertEquals(LevelRouteRegistry.size(), nodes.size)
    for (node in nodes) {
      val state = at(node)
      for (ticket in listOf(4500, 4999)) {
        val result = SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
        assertTrue("5% Tiểu Lan gate at $node, ticket=$ticket",
          result.npcEncounters.any { it.characterId == HUA_TIEU_LAN_ID })
        assertNull(result.entityKey)
      }
      assertTrue(SpawnMechanism.rollSceneSpawns(state, "MOVE") { 5000 }
        .npcEncounters.none { it.characterId == HUA_TIEU_LAN_ID })
    }
  }

  @Test fun `independent first-meeting rate windows preserve all existing encounters`() {
    val state = at("4")
    val outcomes = (0 until 10000).map { ticket ->
      SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
    }
    assertEquals(3500, outcomes.count { it.entityKey != null })
    val firstMeetings = listOf(CATHERINE_ID, JANET_ID, HUA_TIEU_LAN_ID, NGUYEN_QUOC_NAM_ID)
    for (id in firstMeetings) {
      assertEquals(id, 500, outcomes.count { r -> r.npcEncounters.any { it.characterId == id } })
    }
    assertTrue(outcomes.none { r -> r.entityKey != null &&
      r.npcEncounters.any { it.characterId in firstMeetings } })
    assertTrue(outcomes.none { r ->
      r.npcEncounters.count { it.characterId in firstMeetings } > 1
    })
  }

  @Test fun `first meeting must commit after successful turn and never auto joins Party`() {
    val state = at("3")
    val rolled = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi tới") { 4500 }
    assertNull(rolled.entityKey)
    assertTrue(rolled.npcEncounters.any { it.characterId == HUA_TIEU_LAN_ID })
    val pending = SpawnMechanism.prepareSpawns(state, rolled)
    assertNull(pending.characters.getValue(HUA_TIEU_LAN_ID).metadata["encountered"])
    assertNull(SpawnMechanism.discardPendingNpcEncounters(pending)
      .characters.getValue(HUA_TIEU_LAN_ID).metadata["encountered"])
    val committed = SpawnMechanism.commitPendingNpcEncounters(pending)
    assertEquals("true", committed.characters.getValue(HUA_TIEU_LAN_ID).metadata["encountered"])
    assertTrue(JSONObject(committed.world.getValue("flagsJson"))
      .getJSONObject("huaTieuLan").getBoolean("spawned"))
    assertFalse(HUA_TIEU_LAN_ID in committed.party.memberIds)
    assertTrue(SpawnMechanism.rollSceneSpawns(committed, "MOVE") { 4500 }
      .npcEncounters.none { it.characterId == HUA_TIEU_LAN_ID })
  }

  @Test fun `no encounter from dialogue-only meta or pending combat turns`() {
    val state = at("6")
    assertTrue(NpcSpawnTable.rollNpcs(state, ActionKind.EXECUTE, "nói chuyện", 6) { 4500 }
      .none { it.characterId == HUA_TIEU_LAN_ID })
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE,
      "xem trạng thái", isMeta = true, nextInt = { 4500 }).npcEncounters.isEmpty())
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE,
      "đi tiếp", inCombatOrPending = true, nextInt = { 4500 }).npcEncounters.isEmpty())
  }

  @Test fun `Tiểu Lan active combat skills actually execute`() {
    var state = GameState.initial().copy(
      party = PartyState(memberIds = listOf(HUA_TIEU_LAN_ID, CAO_MINH_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val attack = CombatRuntime.resolve(state, "EXECUTE", "Cả Party tấn công")
    assertTrue(attack.reply.contains("Tĩnh Tâm Ngắm Bắn") || attack.reply.contains("Bách Hoa Nhất Tiễn"))
    val evade = CombatRuntime.resolve(state, "EXECUTE", "Party né tránh")
    assertTrue(evade.reply.contains("Bộ Pháp Tà Lụa"))
    val observation = CombatRuntime.resolve(state, "SEARCH", "quan sát")
    assertTrue(observation.reply.contains("Ngắm Qua Hoa Cúc"))
  }
}
