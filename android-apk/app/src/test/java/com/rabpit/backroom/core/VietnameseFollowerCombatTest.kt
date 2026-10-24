package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class VietnameseFollowerCombatTest {
  private val attack = PokerDiceCore.DIRECT_COMBAT_ACTION
  private val profile = CombatRuntime.Profile("slenderman", "Slenderman", 2000, 23, 10, 10)

  private fun party(vararg ids: String): GameState {
    val initial = GameState.initial()
    val chars = initial.characters.toMutableMap()
    ids.forEach { id -> chars[id] = chars.getValue(id).copy(presence = CharacterPresence.ACTIVE) }
    val state = initial.copy(characters = chars, party = initial.party.copy(memberIds = listOf(CAO_MINH_ID) + ids))
    return CombatRuntime.start(state, "slenderman")
  }

  private fun snapshot(state: GameState) = CombatRuntime.active(state)!!.copy(entityHp = 2000, entityMaxHp = 2000)

  private fun hitNoProc(salt: String, bound: Int): Int = when {
    salt.endsWith(":accuracy") -> 0
    else -> bound - 1
  }

  @Test fun ordinaryShotsRequireAccuracyAndSeparateEntityEvasionThenProcOnlyOnce() {
    for ((id, normal, proc) in listOf(Triple(NGUYEN_QUOC_NAM_ID, 9, 13), Triple(HUA_TIEU_LAN_ID, 15, 30))) {
      val state = party(id)
      val c = snapshot(state)
      val missed = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", attack) { salt, bound -> if (salt.endsWith(":evasion")) 0 else hitNoProc(salt, bound) }
      assertEquals("Entity evasion is separate from accuracy", 2000, missed.combat.entityHp)
      val noProc = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", attack, ::hitNoProc)
      assertEquals(normal, 2000 - noProc.combat.entityHp)
      val withProc = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", attack) { salt, bound -> if (salt.endsWith(":proc")) 24 else hitNoProc(salt, bound) }
      assertEquals(normal + proc, 2000 - withProc.combat.entityHp)
      assertEquals(2, withProc.feedback.size)
      assertTrue(withProc.feedback.all { it.actorId == id && !it.critical })
      val boundary = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", attack) { salt, bound -> if (salt.endsWith(":proc")) 25 else hitNoProc(salt, bound) }
      assertEquals(normal, 2000 - boundary.combat.entityHp)
    }
  }

  @Test fun finalizedSsfAndFsfUseExactUltimateDamageWithoutAccuracyCriticalOrExtraProc() {
    for (values in listOf("1,2,3,4,5", "6,6,6,6,6")) {
      var state = party(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID)
      val c = snapshot(state)
      state = PokerDiceCore.prepare(state, attack, c.encounterId)
      state = PokerDiceCore.finish(state.copy(metadata = state.metadata + ("poker.dice.values" to values)))
      val result = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", attack) { _, _ -> fail("Ultimate must not use hit, evasion, critical or proc rolls"); 0 }
      assertEquals(2, result.feedback.size)
      assertEquals(44, result.feedback.single { it.actorId == NGUYEN_QUOC_NAM_ID }.amount)
      assertEquals(70, result.feedback.single { it.actorId == HUA_TIEU_LAN_ID }.amount)
      assertTrue(result.feedback.none { it.critical })
      val wrongAction = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", "Cả Party cùng tấn công một hướng khác", ::hitNoProc)
      assertFalse(wrongAction.log.any { it.contains("Lời Hứa Trở Về") || it.contains("Bách Hoa Nhất Tiễn") })
      val unfinalized = state.copy(metadata = state.metadata + ("poker.dice.finalized" to "false"))
      val pending = VietnameseFollowerCombat.beforeEnemyResponse(unfinalized, c, profile, CombatRuntime.Intent.ATTACK,
        "EXECUTE", attack, ::hitNoProc)
      assertFalse(pending.log.any { it.contains("Lời Hứa Trở Về") })
    }
  }

  @Test fun absentDeadDefeatedAndUnarmedFollowersCannotShoot() {
    val state = party(NGUYEN_QUOC_NAM_ID)
    for (presence in listOf(CharacterPresence.SEPARATED, CharacterPresence.MISSING, CharacterPresence.DEAD)) {
      val actor = state.characters.getValue(NGUYEN_QUOC_NAM_ID).copy(presence = presence)
      val inactive = state.copy(characters = state.characters + (actor.id to actor))
      assertEquals(2000, VietnameseFollowerCombat.beforeEnemyResponse(inactive, snapshot(state), profile,
        CombatRuntime.Intent.ATTACK, "EXECUTE", attack, ::hitNoProc).combat.entityHp)
    }
    val actor = state.characters.getValue(NGUYEN_QUOC_NAM_ID)
    val defeated = state.copy(characters = state.characters + (actor.id to actor.copy(vitalState = actor.vitalState.copy(currentHp = 0))))
    val noParty = state.copy(party = state.party.copy(memberIds = listOf(CAO_MINH_ID)))
    val unarmed = state.copy(equipment = state.equipment + (actor.id to EquipmentState(actor.id)))
    val missingItem = state.copy(inventories = state.inventories + (actor.id to InventoryState(actor.id)))
    for (input in listOf(defeated, noParty, unarmed, missingItem)) {
      assertEquals(2000, VietnameseFollowerCombat.beforeEnemyResponse(input, snapshot(state), profile,
        CombatRuntime.Intent.ATTACK, "EXECUTE", attack, ::hitNoProc).combat.entityHp)
    }
    val soloAction = VietnameseFollowerCombat.beforeEnemyResponse(state, snapshot(state), profile,
      CombatRuntime.Intent.ATTACK, "EXECUTE", "Cao Minh bắn", ::hitNoProc)
    assertEquals(2000, soloAction.combat.entityHp)
    assertFalse(VietnameseFollowerCombat.isPartyAction("EXECUTE", "Cả Party đừng tấn công"))
    assertFalse(VietnameseFollowerCombat.isPartyAction("EXECUTE", "Nói với Party về chiến thuật tấn công"))
  }

  @Test fun firstAidSelectsLowestLivingHpRatioAndIsBoundToEncounterAcrossSaveLoad() {
    var state = party(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID)
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, 25)
    state = CharacterStatEngine.setCurrentHp(state, HUA_TIEU_LAN_ID, 10)
    val c = snapshot(state)
    val first = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.GUARD,
      "PARTY_GUARD", "Cả Party phòng thủ")
    val lanMax = CharacterStatEngine.effective(state, HUA_TIEU_LAN_ID).maxHp
    assertEquals(10 + lanMax * 12 / 100, first.state.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp)
    assertEquals(25, first.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(first.state))
    val again = VietnameseFollowerCombat.beforeEnemyResponse(loaded, c, profile, CombatRuntime.Intent.GUARD,
      "PARTY_GUARD", "Cả Party phòng thủ")
    assertEquals(first.state.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp,
      again.state.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp)
    val newEncounter = snapshot(CombatRuntime.start(CombatRuntime.clear(loaded), "slenderman"))
    val next = VietnameseFollowerCombat.beforeEnemyResponse(loaded, newEncounter, profile, CombatRuntime.Intent.GUARD,
      "PARTY_GUARD", "Cả Party phòng thủ")
    assertTrue(next.state.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp >
      loaded.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp)

    val deadLan = CharacterStatEngine.setCurrentHp(state, HUA_TIEU_LAN_ID, 0)
    val rescue = VietnameseFollowerCombat.beforeEnemyResponse(deadLan, snapshot(deadLan), profile, CombatRuntime.Intent.GUARD,
      "PARTY_GUARD", "Cả Party phòng thủ")
    assertEquals(0, rescue.state.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp)
    assertTrue(rescue.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp > 25)
  }

  @Test fun guardHealAndFollowerMetadataSurviveRealCombatEncodeAndClearOnFinish() {
    var state = party(NGUYEN_QUOC_NAM_ID)
    state = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, 15)
    val result = CombatRuntime.resolve(state, "PARTY_GUARD", "Cả Party phòng thủ")
    assertTrue(result.reply.contains("Sơ Cứu Tại Chỗ"))
    assertEquals(result.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp,
      CombatRuntime.active(result.state)!!.playerHp)
    assertNotNull(result.state.metadata["combat.namFirstAidUsed"])
    assertFalse(CombatRuntime.clear(result.state).metadata.containsKey("combat.namFirstAidUsed"))
  }

  @Test fun readAndEvadeEffectsRequireActivePartyAndRespectOpeningLimit() {
    val state = party(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID)
    val c = snapshot(state).copy(opening = 1)
    val read = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.READ,
      "PARTY_READ", "Cả Party quan sát")
    assertEquals(3, read.combat.opening)
    val saturated = VietnameseFollowerCombat.beforeEnemyResponse(state, c.copy(opening = 3), profile,
      CombatRuntime.Intent.READ, "PARTY_READ", "Cả Party quan sát")
    assertEquals(3, saturated.combat.opening)
    val evade = VietnameseFollowerCombat.beforeEnemyResponse(state, c, profile, CombatRuntime.Intent.EVADE,
      "PARTY_EVADE", "Cả Party né tránh")
    assertEquals(24, evade.enemyAccuracyPenalty)
  }

  @Test fun tieuLanCounterHasTwentyPercentBoundaryAndCanFinishRealEncounter() {
    val state = party(HUA_TIEU_LAN_ID)
    val c = snapshot(state)
    val counter = VietnameseFollowerCombat.afterEnemyMiss(state, c, profile) { _, _ -> 19 }
    assertEquals(18, 2000 - counter.combat.entityHp)
    val missed = VietnameseFollowerCombat.afterEnemyMiss(state, c, profile) { _, _ -> 20 }
    assertEquals(2000, missed.combat.entityHp)
    val noWeapon = state.copy(equipment = state.equipment + (HUA_TIEU_LAN_ID to EquipmentState(HUA_TIEU_LAN_ID)))
    assertEquals(2000, VietnameseFollowerCombat.afterEnemyMiss(noWeapon, c, profile) { _, _ -> 0 }.combat.entityHp)
    val result = (0..300).asSequence().map { seed ->
      val low = state.copy(metadata = state.metadata + mapOf("combat.seed" to seed.toString(), "combat.entityHp" to "1"))
      CombatRuntime.resolve(low, "EXECUTE", "Đứng yên")
    }.first { it.entityDestroyed }
    assertNull(CombatRuntime.active(result.state))
    assertEquals(1, result.feedback.single { it.actorId == HUA_TIEU_LAN_ID }.amount)
  }

  @Test fun skillCatalogExposesAllFiveSkillsAndOneUltimatePerFollower() {
    for (id in listOf(NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID)) {
      assertEquals(6, CompanionSkillCatalog.forCharacter(id).size)
      assertEquals(1, CompanionSkillCatalog.forCharacter(id).count { it.kind == "ULTIMATE" })
    }
  }
}
