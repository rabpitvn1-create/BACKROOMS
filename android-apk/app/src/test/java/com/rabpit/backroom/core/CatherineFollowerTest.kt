package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatherineFollowerTest {
  private fun freshAt(node: String): GameState = GameState.initial().copy(world = mapOf(
    "levelJson" to requireNotNull(LevelRouteRegistry.node(node)).asJson().toString()
  ))

  @Test fun `new and current-run normalization register Catherine without forcing Party`() {
    val fresh = GameState.initial()
    val actor = fresh.characters.getValue(CATHERINE_ID)
    assertEquals(CatherineCanon.NAME, actor.name)
    assertEquals("avatars/Catherine_avatar.webp", actor.avatarRef)
    assertEquals("Catherine.webp", actor.metadata["spriteRef"])
    assertEquals("follower", actor.metadata["npcType"])
    assertFalse(CATHERINE_ID in fresh.party.memberIds)
    assertEquals(CATHERINE_VESPER_ID, fresh.equipment.getValue(CATHERINE_ID).slots["weapon"])
    assertNotNull(EquipmentCatalog.definition(CATHERINE_COAT_ID))
    assertNotNull(EquipmentCatalog.definition(CATHERINE_GLOVES_ID))
    assertNotNull(EquipmentCatalog.definition(CATHERINE_BOOTS_ID))
    val withoutNewCharacter = fresh.copy(characters = fresh.characters - CATHERINE_ID,
      equipment = fresh.equipment - CATHERINE_ID, inventories = fresh.inventories - CATHERINE_ID)
    val normalized = CharacterEquipmentSystem.normalize(withoutNewCharacter)
    assertEquals(CatherineCanon.NAME, normalized.characters.getValue(CATHERINE_ID).name)
    assertEquals(CATHERINE_VESPER_ID, normalized.equipment.getValue(CATHERINE_ID).slots["weapon"])
  }

  @Test fun `five normal skills and one ultimate appear in party profile`() {
    val skills = CompanionSkillCatalog.forCharacter(CATHERINE_ID)
    assertEquals(6, skills.size)
    assertEquals(5, skills.count { it.kind != "ULTIMATE" })
    assertEquals(1, skills.count { it.kind == "ULTIMATE" })
    assertTrue(skills.any { it.name == "Dạ Khúc Mười Ba Phát" })
    }

  @Test fun `exact five percent at all registered parent and sub levels`() {
    val nodes = listOf("0", "0.1", "1", "2", "3", "4", "5", "6", "dullness", "epsilon", "ls_2")
    assertEquals(500, NpcSpawnTable.CATHERINE_THRESHOLD)
    // Verify 35% Entity plus 5% Catherine exactly, with zero collisions.
    val sample = freshAt("4")
    val outcomes = (0 until 10000).map { ticket ->
      SpawnMechanism.rollSceneSpawns(sample, "MOVE") { ticket }
    }
    assertEquals(3500, outcomes.count { it.entityKey != null })
    assertEquals(500, outcomes.count { r -> r.npcEncounters.any { it.characterId == CATHERINE_ID } })
    assertTrue(outcomes.none { r -> r.entityKey != null &&
      r.npcEncounters.any { it.characterId == CATHERINE_ID } })
    for (node in nodes) {
      val state = freshAt(node)
      val level = requireNotNull(LevelRouteRegistry.node(node)).parent
      val hits = (0 until 10000).count { ticket ->
        NpcSpawnTable.rollNpcsWithPhysical(state, ActionKind.EXECUTE, "", level,
          nextInt = { ticket }, physicalOverride = true
        ).any { it.characterId == CATHERINE_ID }
      }
      assertEquals("Rate at $node", 500, hits)
    }
  }

  @Test fun `Catherine preserves both encounter probabilities and never auto recruits`() {
    val state = freshAt("0")
    val entity = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi khám phá") { 0 }
    assertNotNull("Original Entity window must stay intact", entity.entityKey)
    assertTrue(entity.npcEncounters.none { it.characterId == CATHERINE_ID })
    val rolled = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi khám phá") { 3500 }
    assertNull("Catherine's disjoint five-percent window excludes Entity", rolled.entityKey)
    assertTrue(rolled.npcEncounters.any { it.characterId == CATHERINE_ID })
    val met = SpawnMechanism.applySpawns(state, rolled)
    assertEquals("true", met.characters.getValue(CATHERINE_ID).metadata["encountered"])
    assertTrue(JSONObject(met.world.getValue("flagsJson")).getJSONObject("catherine").getBoolean("spawned"))
    assertFalse(CATHERINE_ID in met.party.memberIds)
    assertTrue(NpcSpawnTable.rollNpcs(met, ActionKind.EXECUTE, "đi tiếp", 0) { 0 }
      .none { it.characterId == CATHERINE_ID })
  }

  @Test fun `Catherine cannot spawn from meta or dialogue and is stable after failed encounter`() {
    val state = freshAt("6")
    assertTrue(NpcSpawnTable.rollNpcs(state, ActionKind.EXECUTE, "nói chuyện với người lạ", 6) { 0 }
      .none { it.characterId == CATHERINE_ID })
    assertTrue(SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "xem trạng thái",
      isMeta = true, nextInt = { 0 }).npcEncounters.isEmpty())
    assertTrue(NpcSpawnTable.rollNpcs(state, ActionKind.SEARCH, "đi", 6) { 0 }.isEmpty())
    val rolled = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi") { 3500 }
    val pending = SpawnMechanism.prepareSpawns(state, rolled)
    assertNull(pending.characters.getValue(CATHERINE_ID).metadata["encountered"])
    assertEquals("true", SpawnMechanism.commitPendingNpcEncounters(pending)
      .characters.getValue(CATHERINE_ID).metadata["encountered"])
    assertNull(SpawnMechanism.discardPendingNpcEncounters(pending)
      .characters.getValue(CATHERINE_ID).metadata["encountered"])
  }

  @Test fun `Catherine combat is not a presentation-only skill card`() {
    var state = GameState.initial().copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, CATHERINE_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val seen = (0..160).any { n ->
      val candidate = state.copy(metadata = state.metadata + ("combat.eventCounter" to n.toString()))
      val result = CombatRuntime.resolve(candidate, "EXECUTE", "Cả Party tấn công")
      result.reply.contains("Đồng Tử Hổ Phách") || result.reply.contains("Khúc Đạn Hồi Âm") ||
        result.reply.contains("Hoa Hồng Xuyên Giáp")
    }
    assertTrue("Core combat must execute Catherine's shooting skills", seen)
  }
}
