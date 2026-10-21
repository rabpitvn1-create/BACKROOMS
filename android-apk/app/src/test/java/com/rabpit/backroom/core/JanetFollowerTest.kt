package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JanetFollowerTest {
  private fun at(node: String): GameState = GameState.initial().copy(world = mapOf(
    "levelJson" to requireNotNull(LevelRouteRegistry.node(node)).asJson().toString()
  ))

  @Test fun `Janet keeps 1988 Louisiana identity and canonical portraits`() {
    val state = GameState.initial()
    val actor = state.characters.getValue(JANET_ID)
    assertEquals("Janet the Lady", actor.name)
    assertEquals("Janet Marie Boudreaux", actor.metadata["fullName"])
    assertEquals("Janet the Lady", actor.metadata["nickname"])
    assertEquals("19", actor.metadata["age"])
    assertEquals("1988", actor.metadata["originYear"])
    assertEquals("avatars/Janet_avatar.webp", actor.avatarRef)
    assertEquals("Janet.webp", actor.metadata["spriteRef"])
    assertFalse(JANET_ID in state.party.memberIds)
    val equipment = state.equipment.getValue(JANET_ID).slots
    assertEquals(JANET_BAT_ID, equipment["weapon"])
    assertEquals(JANET_VEST_ID, equipment["outfit"])
    assertEquals(JANET_GLOVES_ID, equipment["gauntlets"])
    assertEquals(JANET_BOOTS_ID, equipment["footwear"])
    assertEquals(24, EquipmentCatalog.definition(JANET_BAT_ID)!!.weapon!!.dmg)
    assertNotNull(EquipmentCatalog.definition(JANET_VEST_ID))
    assertNotNull(EquipmentCatalog.definition(JANET_GLOVES_ID))
    assertNotNull(EquipmentCatalog.definition(JANET_BOOTS_ID))
  }

  @Test fun `Janet has exactly five normal skills and one Ultimate`() {
    val skills = CompanionSkillCatalog.forCharacter(JANET_ID)
    assertEquals(6, skills.size)
    assertEquals(5, skills.count { it.kind != "ULTIMATE" })
    assertEquals(1, skills.count { it.kind == "ULTIMATE" })
    assertEquals("Last Inning // Bayou Thunder", skills.last().name)
  }

  @Test fun `all listed parent sublevel and AREA nodes support Janet's five percent window`() {
    assertEquals(500, NpcSpawnTable.JANET_THRESHOLD)
    val ids = listOf("0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment",
      "red_rooms", "dullness", "epsilon", "ls_2", "1", "1.2", "1.5",
      "traders_vault", "base_alpha", "2", "office_space_el3a", "3", "4",
      "office_market", "5", "5.1", "6", "6.1")
    assertEquals(LevelRouteRegistry.size(), ids.size)
    for (node in ids) {
      val state = at(node)
      val first = SpawnMechanism.rollSceneSpawns(state, "MOVE") { 4000 }
      val last = SpawnMechanism.rollSceneSpawns(state, "MOVE") { 4499 }
      val outside = SpawnMechanism.rollSceneSpawns(state, "MOVE") { 4500 }
      assertTrue("Janet eligible in $node", first.npcEncounters.any { it.characterId == JANET_ID })
      assertTrue("Janet upper threshold in $node", last.npcEncounters.any { it.characterId == JANET_ID })
      assertFalse("No more than 5% in $node", outside.npcEncounters.any { it.characterId == JANET_ID })
    }
  }

  @Test fun `Entity Catherine and Janet have disjoint 35 5 5 windows`() {
    val state = at("4")
    val all = (0 until 10000).map { ticket ->
      SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
    }
    assertEquals(3500, all.count { it.entityKey != null })
    assertEquals(500, all.count { it.npcEncounters.any { npc -> npc.characterId == CATHERINE_ID } })
    assertEquals(500, all.count { it.npcEncounters.any { npc -> npc.characterId == JANET_ID } })
    assertTrue(all.none { it.entityKey != null &&
      it.npcEncounters.any { npc -> npc.characterId == JANET_ID || npc.characterId == CATHERINE_ID } })
    assertTrue(all.none { it.npcEncounters.any { npc -> npc.characterId == JANET_ID } &&
      it.npcEncounters.any { npc -> npc.characterId == CATHERINE_ID } })
  }

  @Test fun `Janet commits once only and never silently joins Party`() {
    val state = at("3")
    val result = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi tới") { 4000 }
    assertNull(result.entityKey)
    assertTrue(result.npcEncounters.any { it.characterId == JANET_ID })
    val pending = SpawnMechanism.prepareSpawns(state, result)
    assertNull(pending.characters.getValue(JANET_ID).metadata["encountered"])
    assertNull(SpawnMechanism.discardPendingNpcEncounters(pending)
      .characters.getValue(JANET_ID).metadata["encountered"])
    val confirmed = SpawnMechanism.commitPendingNpcEncounters(pending)
    assertEquals("true", confirmed.characters.getValue(JANET_ID).metadata["encountered"])
    assertTrue(JSONObject(confirmed.world.getValue("flagsJson"))
      .getJSONObject("janet").getBoolean("spawned"))
    assertFalse(JANET_ID in confirmed.party.memberIds)
    assertTrue(SpawnMechanism.rollSceneSpawns(confirmed, "MOVE") { 4000 }
      .npcEncounters.none { it.characterId == JANET_ID })
  }

  @Test fun `Janet does not appear in meta conversation or pending combat`() {
    val state = at("6")
    assertTrue(NpcSpawnTable.rollNpcs(state, ActionKind.EXECUTE, "nói chuyện với bạn", 6) { 4000 }
      .none { it.characterId == JANET_ID })
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE,
      "xem trạng thái", isMeta = true, nextInt = { 4000 }).npcEncounters.isEmpty())
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE,
      "đi tiếp", inCombatOrPending = true, nextInt = { 4000 }).npcEncounters.isEmpty())
  }

  @Test fun `Janet combat actions execute mechanical effects not just skill descriptions`() {
    var state = GameState.initial().copy(
      party = PartyState(memberIds = listOf(CAO_MINH_ID, JANET_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val first = CombatRuntime.resolve(state, "EXECUTE", "Cả Party tấn công")
    assertTrue("First melee action moves or hits", first.reply.contains("Janet"))
    // Some encounters start at range, so start from the committed next combat state.
    val second = CombatRuntime.resolve(first.state, "EXECUTE", "Cả Party tấn công")
    assertTrue(second.reply.contains("Janet") || CombatRuntime.active(second.state) == null)
    val evasive = CombatRuntime.resolve(state, "EXECUTE", "Party né tránh")
    assertTrue(evasive.reply.contains("Redline Sidestep"))
    val guarded = CombatRuntime.resolve(state, "EXECUTE", "Party phòng thủ")
    assertTrue(guarded.reply.contains("Ride or Die"))
  }
}
