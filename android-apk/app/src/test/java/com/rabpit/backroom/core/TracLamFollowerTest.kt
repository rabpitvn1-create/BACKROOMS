package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TracLamFollowerTest {
  private fun at(id: String): GameState = GameState.initial().copy(world = mapOf(
    "levelJson" to requireNotNull(LevelRouteRegistry.node(id)).asJson().toString()))

  @Test fun `Codex assets and baseline identity remain bounded to SRU-03 year 2099`() {
    val state = GameState.initial()
    val a = state.characters.getValue(TRAC_LAM_ID)
    assertEquals("Trác Lâm", a.name)
    assertEquals("28", a.metadata["age"])
    assertEquals("2099", a.metadata["originYear"])
    assertEquals("SRU-03", a.metadata["organization"])
    assertEquals("0.7", a.metadata["encounterLevels"])
    assertEquals("3%", a.metadata["encounterChance"])
    assertEquals("avatars/TracLam_avatar.jpg", a.avatarRef)
    assertEquals("TracLam.png", a.metadata["spriteRef"])
    assertFalse(TRAC_LAM_ID in state.party.memberIds)
    assertEquals(30, a.statProfile.baseMaxHp)
    assertEquals(30, a.vitalState.currentHp)
    assertEquals(listOf(1, 2, 2, 2, 1), listOf(
      a.statProfile.str, a.statProfile.dex, a.statProfile.agi,
      a.statProfile.vit, a.statProfile.eva))
    assertEquals(30, CharacterStatCore.effective(state, TRAC_LAM_ID).maxHp)
    assertEquals(27, EquipmentCatalog.definition(TRAC_LAM_GEMINI_ID)!!.weapon!!.dmg)
    assertEquals(TRAC_LAM_GEMINI_ID, state.equipment.getValue(TRAC_LAM_ID).slots["weapon"])
    assertEquals(TRAC_LAM_AEGIS_ID, state.equipment.getValue(TRAC_LAM_ID).slots["armor"])
    assertEquals(TRAC_LAM_SCOUT_ID, state.equipment.getValue(TRAC_LAM_ID).slots["special"])
    assertEquals(TRAC_LAM_MEDKIT_ID, state.equipment.getValue(TRAC_LAM_ID).slots["wrist"])
  }

  @Test fun `seven regular skills have three active three proc one passive plus Ultimate`() {
    val all = CompanionSkillCatalog.forCharacter(TRAC_LAM_ID)
    assertEquals(8, all.size)
    assertEquals(1, all.count { it.kind == "PASSIVE" })
    assertEquals(3, all.count { it.kind == "ACTIVE" })
    assertEquals(3, all.count { it.kind == "PROC" })
    assertEquals(1, all.count { it.kind == "ULTIMATE" })
    assertEquals("ECHO-17: Phá Tuyến", all.last().name)
    assertEquals(0, CharacterSkillUnlocks.requiredLevel(TRAC_LAM_ID, "Khóa Mục Tiêu"))
    assertEquals(10, CharacterSkillUnlocks.requiredLevel(TRAC_LAM_ID, "Hỏa Lực Chế Áp"))
    assertEquals(20, CharacterSkillUnlocks.requiredLevel(TRAC_LAM_ID, "Đánh Giá Dị Thường"))
    assertEquals(60, CharacterSkillUnlocks.requiredLevel(TRAC_LAM_ID, "ECHO-17: Phá Tuyến"))
    assertTrue(CharacterSkillUnlocks.isUnlocked(TRAC_LAM_ID, "Kỷ Luật SRU-03", 0))
    assertFalse(CharacterSkillUnlocks.isUnlocked(TRAC_LAM_ID, "ECHO-17: Phá Tuyến", 0))
  }

  @Test fun `three percent window restricted to Canon first contact Level zero point seven`() {
    assertEquals(300, NpcSpawnTable.TRAC_LAM_THRESHOLD)
    val state = at("0.7")
    val samples = (0 until 10000).map { ticket ->
      SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
    }
    assertEquals(300, samples.count { result -> result.npcEncounters.any { it.characterId == TRAC_LAM_ID } })
    for (ticket in listOf(5500, 5799)) {
      val r = SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
      assertEquals(listOf(TRAC_LAM_ID), r.npcEncounters.map { it.characterId })
      assertNull(r.entityKey)
    }
    assertFalse(SpawnMechanism.rollSceneSpawns(state, "MOVE") { 5800 }
      .npcEncounters.any { it.characterId == TRAC_LAM_ID })
    assertFalse(SpawnMechanism.rollSceneSpawns(at("0"), "MOVE") { 5500 }
      .npcEncounters.any { it.characterId == TRAC_LAM_ID })
    assertFalse(SpawnMechanism.rollSceneSpawns(at("4"), "MOVE") { 5500 }
      .npcEncounters.any { it.characterId == TRAC_LAM_ID })
  }

  @Test fun `confirmed first contact does not automatically recruit and cannot spawn twice`() {
    val state = at("0.7")
    val rolled = SpawnMechanism.rollSceneSpawns(state, "MOVE") { 5500 }
    assertEquals(listOf(TRAC_LAM_ID), rolled.npcEncounters.map { it.characterId })
    val pending = SpawnMechanism.prepareSpawns(state, rolled)
    assertNull(pending.characters.getValue(TRAC_LAM_ID).metadata["encountered"])
    val abort = SpawnMechanism.discardPendingNpcEncounters(pending)
    assertNull(abort.characters.getValue(TRAC_LAM_ID).metadata["encountered"])
    val committed = SpawnMechanism.commitPendingNpcEncounters(pending)
    assertEquals("true", committed.characters.getValue(TRAC_LAM_ID).metadata["encountered"])
    assertTrue(JSONObject(committed.world.getValue("flagsJson"))
      .getJSONObject("tracLam").getBoolean("spawned"))
    assertFalse(TRAC_LAM_ID in committed.party.memberIds)
    assertTrue(SpawnMechanism.rollSceneSpawns(committed, "MOVE") { 5500 }
      .npcEncounters.none { it.characterId == TRAC_LAM_ID })
    val restored = GameStateCodec.decode(GameStateCodec.encode(committed))
    assertEquals("true", restored.characters.getValue(TRAC_LAM_ID).metadata["encountered"])
    assertEquals(30, restored.characters.getValue(TRAC_LAM_ID).statProfile.baseMaxHp)
    assertEquals(30, CharacterStatCore.effective(restored, TRAC_LAM_ID).maxHp)
  }

  @Test fun `meta dialogue combat pending never rolls Trác Lâm`() {
    val s = at("0.7")
    assertTrue(NpcSpawnTable.rollNpcs(s, ActionKind.EXECUTE, "nói chuyện", 0) { 5500 }
      .none { it.characterId == TRAC_LAM_ID })
    assertTrue(SpawnMechanism.rollSpawns(s, ActionKind.EXECUTE,
      "xem trạng thái", isMeta = true, nextInt = { 5500 }).npcEncounters.isEmpty())
    assertTrue(SpawnMechanism.rollSpawns(s, ActionKind.EXECUTE,
      "đi tiếp", inCombatOrPending = true, nextInt = { 5500 }).npcEncounters.isEmpty())
  }

  @Test fun `combat actions are implemented and use only unlocked abilities`() {
    val initial = GameState.initial().copy(
      party = PartyState(memberIds = listOf(TRAC_LAM_ID, CAO_MINH_ID)))
    val encounter = CombatRuntime.start(initial, "diep_minh")
    val attack = CombatRuntime.resolve(encounter, "EXECUTE", "Cả Party tấn công")
    assertTrue(attack.reply.contains("Trác Lâm") ||
      CombatRuntime.active(attack.state) == null)
    val unlocked = encounter.copy(characters = encounter.characters + (TRAC_LAM_ID to
      encounter.characters.getValue(TRAC_LAM_ID).copy(progression = CharacterProgression(60, 0))))
    val observe = CombatRuntime.resolve(unlocked, "SEARCH", "quan sát")
    assertTrue(observe.reply.contains("Đánh Giá Dị Thường"))
    val evade = CombatRuntime.resolve(unlocked, "EXECUTE", "Party né tránh")
    assertTrue(evade.reply.contains("Hỏa Lực Chế Áp"))
  }
}
