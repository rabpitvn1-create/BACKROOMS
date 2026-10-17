package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatRuntimeTest {

  /** Match Lucia's encounter/action-bound, finalized Poker Dice contract. */
  private fun withUltimateHand(
    state: GameState, hand: String, finalized: Boolean = true,
    action: String = PokerDiceCore.DIRECT_COMBAT_ACTION
  ): GameState {
    val encounterId = CombatRuntime.active(state)!!.encounterId
    return state.copy(metadata = state.metadata + mapOf(
      "poker.dice.encounter" to encounterId,
      "poker.dice.action" to action,
      "poker.dice.values" to when (hand) {
        "SSF" -> "1,2,3,4,5"
        "FSF" -> "6,6,6,6,6"
        else -> "1,1,2,2,3"
      },
      "poker.dice.held" to "0,0,0,0,0",
      "poker.dice.finalized" to finalized.toString(),
      "poker.dice.hand" to hand
    ))
  }

  @Test fun entityTriggerStartsOneAuthoritativeEncounterWithHealth() {
    val started = CombatRuntime.start(GameState.initial(), "hound")
    val combat = CombatRuntime.active(started)
    assertNotNull(combat)
    assertEquals("hound", combat!!.entityKey)
    assertEquals(220, combat.entityMaxHp)
    assertEquals(220, combat.entityHp)
    val initial = GameState.initial()
    val expectedMaxHp = CharacterStatEngine.effective(initial, CAO_MINH_ID).maxHp
    val expectedHp = initial.characters.getValue(CAO_MINH_ID).vitalState.currentHp
    assertEquals(60, expectedMaxHp)
    assertEquals(50, expectedHp)
    assertEquals(expectedMaxHp, combat.playerMaxHp)
    assertEquals(expectedHp, combat.playerHp)

    val duplicate = CombatRuntime.start(started, "smiler")
    assertEquals("hound", CombatRuntime.active(duplicate)!!.entityKey)
  }


  @Test fun successiveEncountersInSameTurnCannotReuseRewardIdentity() {
    val first = CombatRuntime.start(GameState.initial(), "hound")
    val firstId = CombatRuntime.active(first)!!.encounterId
    val second = CombatRuntime.start(CombatRuntime.clear(first), "hound")
    val secondId = CombatRuntime.active(second)!!.encounterId
    assertFalse(firstId == secondId)
    assertEquals("2", second.metadata["loot.encounterSerial"])
  }

  @Test fun repeatedAuthoritativeAttacksEventuallyDestroyAndClearEntity() {
    var state = CombatRuntime.start(GameState.initial(), "hound")
    var destroyed = false
    repeat(24) {
      if (destroyed) return@repeat
      val result = CombatRuntime.resolve(state, "EXECUTE", "bắn Hound bằng Magnum")
      assertTrue(result.handled)
      state = result.state
      destroyed = result.entityDestroyed
    }
    assertTrue("Entity must be destroyable by authoritative combat resolution", destroyed)
    assertNull(CombatRuntime.active(state))
  }

  @Test fun combatMovementIsNotAnotherEncounter() {
    val started = CombatRuntime.start(GameState.initial(), "skin-stealer")
    val before = CombatRuntime.active(started)!!
    val result = CombatRuntime.resolve(started, "EXECUTE", "lùi lại tìm vật che chắn")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    if (after != null) {
      assertEquals("skin-stealer", after.entityKey)
      assertTrue(after.escapeProgress >= before.escapeProgress)
      assertTrue(after.range.ordinal >= before.range.ordinal)
    }
  }

  @Test fun escapeResolutionClearsEncounterWithoutDestroyingRequirement() {
    val started = CombatRuntime.start(GameState.initial(), "smiler")
    val state = started.copy(metadata = started.metadata + ("combat.escapeProgress" to "95"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "chạy thoát khỏi encounter")
    assertTrue(result.handled)
    assertTrue(result.escaped)
    assertFalse(result.entityDestroyed)
    assertNull(CombatRuntime.active(result.state))
  }

  @Test fun readActionRevealsTelegraphAndBuildsOpeningWhenEncounterSurvives() {
    val state = CombatRuntime.start(GameState.initial(), "clump")
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát kỹ chuyển động của nó")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    assertNotNull(after)
    assertTrue(after!!.opening >= 1)
    assertTrue(after.momentum >= 0)
    assertFalse(after.telegraph.isBlank())
  }
  @Test fun everyDefeatedEntityAwardsExactlyOneOfflineItem() {
    val initial = GameState.initial()
    val before = initial.inventories.getValue(CAO_MINH_ID).items.values.sumOf { it.quantity }
    val awarded = OfflineEntityLoot.award(initial, "encounter:one", 91L)
    assertNotNull(awarded.itemId)
    assertFalse(awarded.queued)
    assertEquals(before + 1, awarded.state.inventories.getValue(CAO_MINH_ID).items.values.sumOf { it.quantity })
    val duplicate = OfflineEntityLoot.award(awarded.state, "encounter:one", 42L)
    assertEquals(awarded.state, duplicate.state)
  }

  @Test fun entityDropSurvivesFullInventoryUntilSlotOpens() {
    val initial = GameState.initial()
    val fullItems = (1..InventoryPolicy.CAO_MINH.maxTypes).associate { n ->
      val itemId = "filler:$n"
      itemId to ItemStack(itemId, "Filler $n", 1)
    }
    val full = initial.copy(inventories = initial.inventories +
      (CAO_MINH_ID to InventoryState(CAO_MINH_ID, fullItems)))
    val award = OfflineEntityLoot.award(full, "encounter:full", 51L)
    assertTrue(award.queued)
    val freed = award.state.copy(inventories = award.state.inventories +
      (CAO_MINH_ID to InventoryState(CAO_MINH_ID, fullItems - "filler:1")))
    val collected = OfflineEntityLoot.collectPending(freed)
    assertTrue(collected.metadata["loot.pendingEntityDrops"].isNullOrBlank())
    assertTrue(collected.inventories.getValue(CAO_MINH_ID).items.containsKey(award.itemId!!))
  }


  @Test fun survivingEntityRegeneratesOneHpPerCombatTurnUpToMax() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")
    val full = CombatRuntime.active(state)!!
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to (full.entityMaxHp - 5).toString()))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát chuyển động")
    val after = CombatRuntime.active(result.state)!!
    assertTrue(result.reply.contains("hồi +1 HP"))
    assertTrue(after.entityHp <= after.entityMaxHp)
  }

  @Test fun everyNonDiepMinhEntityDoublesItsPreviousScaledHp() {
    val expected = mapOf(
      "hound" to 110, "clump" to 135, "duller" to 120, "deathmoth" to 95,
      "hostile_faceling" to 105, "false_puddle" to 125, "paintings" to 100,
      "smiler" to 115, "skin-stealer" to 130, "predatory_window" to 145,
      "biological_pipeline" to 150, "wretch" to 115, "cable_mimic" to 130,
      "the_beast_of_level_5" to 175, "hotel_corpse_lure" to 140,
      "jeff_the_killer" to 150, "jane_the_killer" to 150, "slenderman" to 190,
      "blackroot_sentinel" to 134, "sinew_strider" to 134, "hollow_grasper" to 134
    )
    expected.forEach { (key, hp) ->
      val entity = CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!
      assertEquals("Full current HP of $key must double", hp * 2, entity.entityMaxHp)
      assertEquals(entity.entityMaxHp, entity.entityHp)
    }
  }

  @Test fun caoMinhUltimateRequiresFinalizedSsfOrFsfRatherThanTurnCounter() {
    val action = PokerDiceCore.DIRECT_COMBAT_ACTION
    val started = CombatRuntime.start(GameState.initial(), "diep_minh")
    val state = started.copy(metadata = started.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.entityHp" to "2000",
      "combat.entityMaxHp" to "2999"
    ))
    val noHand = CombatRuntime.resolve(state, "EXECUTE", action)
    val ordinary = CombatRuntime.resolve(withUltimateHand(state, "TWO PAIR"), "EXECUTE", action)
    val unfinished = CombatRuntime.resolve(withUltimateHand(state, "SSF", finalized = false), "EXECUTE", action)
    val mismatched = CombatRuntime.resolve(withUltimateHand(state, "SSF", action = "khác"), "EXECUTE", action)
    val evade = CombatRuntime.resolve(withUltimateHand(state, "SSF"), "EXECUTE", "Cả Party cùng né tránh")
    for (result in listOf(noHand, ordinary, unfinished, mismatched, evade)) {
      assertFalse(result.reply, result.reply.contains("Huyết Ma Nhị Thập Tứ Trảm"))
    }

    val slashDamage = CharacterStatEngine.skillBaseDamage(state, CAO_MINH_ID) * 115 / 100
    for (hand in listOf("SSF", "FSF")) {
      val result = CombatRuntime.resolve(withUltimateHand(state, hand), "EXECUTE", action)
      assertTrue(result.handled)
      assertTrue(result.reply, result.reply.contains("Huyết Ma Nhị Thập Tứ Trảm"))
      assertTrue(result.reply.contains("24/24 trảm trúng liên tiếp"))
      assertTrue(result.reply.contains("Accuracy 200%"))
      assertTrue(result.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
      assertTrue(result.reply.contains("mỗi trảm -$slashDamage HP"))
      assertTrue(result.reply.contains("tổng -${24 * slashDamage} HP"))
    }
  }

  @Test fun irisSyvialAndLucTramUltimatesRequireFinalizedSsfOrFsf() {
    val action = PokerDiceCore.DIRECT_COMBAT_ACTION
    val abilities = mapOf(
      IRIS_ID to "ARGUS // Thousandfold Execution",
      SYVIAL_ID to "GodKiller Override // Twenty-Four Severance",
      LUC_TRAM_ID to "Thiên Kiếm Định Giới"
    )
    for ((actorId, name) in abilities) {
      var initial = SpecialFollowersCanon.ensure(GameState.initial())
        .copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, actorId)))
      if (actorId == LUC_TRAM_ID) {
        val equipped = initial.equipment.getValue(LUC_TRAM_ID)
        initial = initial.copy(equipment = initial.equipment +
          (LUC_TRAM_ID to equipped.copy(slots = equipped.slots +
            ("weapon" to LUC_TRAM_TICH_QUANG_ID))))
      }
      val started = CombatRuntime.start(initial, "diep_minh")
      // 12th turn is a former automatic trigger for Iris/Syvial. Lục Trầm
      // additionally has a confirmed sword and opening 2.
      val state = started.copy(metadata = started.metadata + mapOf(
        "combat.eventCounter" to "11", "combat.opening" to "2"
      ))
      for (without in listOf(state, withUltimateHand(state, "TWO PAIR"),
          withUltimateHand(state, "SSF", finalized = false))) {
        val result = CombatRuntime.resolve(without, "EXECUTE", action)
        assertFalse("$actorId triggered $name without finalized SSF/FSF: ${result.reply}",
          result.reply.contains(name))
      }
      for (hand in listOf("SSF", "FSF")) {
        val result = CombatRuntime.resolve(withUltimateHand(state, hand), "EXECUTE", action)
        assertTrue("$actorId did not trigger $name with $hand: ${result.reply}",
          result.reply.contains(name))
      }
    }
  }

  @Test fun guiltyCrownOverrideAppliesTwentyFourStrScaledHitsBeforeNormalRegen() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.entityHp" to "2000",
      "combat.entityMaxHp" to "2999",
      "combat.eventCounter" to "2"
    ))

    val third = CombatRuntime.resolve(withUltimateHand(state, "SSF"), "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)
    assertTrue(third.handled)
    val slashDamage = CharacterStatEngine.skillBaseDamage(state, CAO_MINH_ID) * 115 / 100
    assertTrue(third.reply.contains("tổng -${24 * slashDamage} HP"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
  }

  @Test fun diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val started = CombatRuntime.active(state)!!
    assertEquals(2999, started.entityMaxHp)
    assertEquals(2999, started.entityHp)

    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "2900"))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát Diệp Minh")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(2930, after.entityHp)
    assertTrue(result.reply.contains("hồi +30 HP"))
  }

  @Test fun diepMinhDevilsAndGoldHitsEveryActivePartyMemberForFivePercentMaxHpOnTurnFive() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = "iris",
      name = "Iris",
      statProfile = CharacterStatProfiles.forId("iris"),
      vitalState = CharacterStatProfiles.initialVitals("iris")
    )
    var state = initial.copy(
      characters = initial.characters + ("iris" to iris),
      party = PartyState(memberIds = listOf(CAO_MINH_ID, "iris"))
    )
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "4"))

    val caoMinhBefore = state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
    val irisBefore = state.characters.getValue("iris").vitalState.currentHp
    val caoMinhMax = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
    val irisMax = CharacterStatEngine.effective(state, "iris").maxHp
    val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")

    assertTrue(result.reply.contains("Devils And Gold"))
    val caoMinhDamage = maxOf(1, (caoMinhMax * 5 + 99) / 100)
    val daiDaoHeal = maxOf(1, (caoMinhMax * CaoMinhCombatPassive.HEAL_PERCENT + 50) / 100)
    assertEquals(minOf(caoMinhMax, caoMinhBefore - caoMinhDamage + daiDaoHeal), result.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(irisBefore - maxOf(1, (irisMax * 5 + 99) / 100), result.state.characters.getValue("iris").vitalState.currentHp)
  }

  @Test fun caoMinhAutomaticSwordSkillsExposeAllFourIndependentProcContracts() {
    val seen = mutableSetOf<String>()
    for (counter in 0..240) {
      if (seen.size == 4) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (result.reply.contains("Huyết Ma Tứ Liên tự động kích hoạt")) seen += "requiem"
      if (result.reply.contains("Ma Tâm Trấn Hồn tự động kích hoạt")) seen += "lullaby"
      if (result.reply.contains("Huyết Ảnh Ma Độn tự động kích hoạt")) seen += "salvation"
      if (result.reply.contains("Thiên Ma Bộ tự động kích hoạt")) seen += "quick_step"
    }
    assertEquals(setOf("requiem", "lullaby", "salvation", "quick_step"), seen)
  }

  @Test fun lastRequiemBleedingPersistsAndTicksFivePercentMaxHp() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.entityHp" to "2000",
        "combat.caoMinhBleedTurns" to "3"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi mục tiêu")
      if (result.reply.contains("Huyết Ma Tứ Liên tự động kích hoạt")) continue
      val after = CombatRuntime.active(result.state) ?: continue
      assertTrue(result.reply.contains("Bleeding từ Huyết Ma Tứ Liên gây -150 HP"))
      assertEquals("2", result.state.metadata["combat.caoMinhBleedTurns"])
      assertTrue(after.entityHp <= 1880)
      verified = true
    }
    assertTrue("Expected a deterministic turn without Last Requiem refresh", verified)
  }

  @Test fun silentLullabyStunSuppressesCurrentEnemyResponse() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (!result.reply.contains("Ma Tâm Trấn Hồn tự động kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("bị Stun và mất lượt phản ứng hiện tại"))
      assertFalse(result.reply, result.reply.contains("Diệp Minh phản công:"))
      assertFalse(result.reply, result.reply.contains("Devils And Gold kích hoạt"))
      verified = true
    }
    assertTrue("Expected an ATTACK turn where Ma Tâm Trấn Hồn activates", verified)
  }

  @Test fun quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("Thiên Ma Bộ tự động kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("+50% Evasion trong 3 turn"))
      assertEquals("2", result.state.metadata["combat.caoMinhQuickStepTurns"])
      verified = true
    }
    assertTrue("Expected a Party EVADE turn where Thiên Ma Bộ activates", verified)
  }

  @Test fun guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(withUltimateHand(state, "SSF"), "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)
    assertTrue(result.reply.contains("Huyết Ma Nhị Thập Tứ Trảm"))
    assertFalse(result.reply.contains("Huyết Ma Tứ Liên tự động kích hoạt"))
    assertFalse(result.reply.contains("Ma Tâm Trấn Hồn tự động kích hoạt"))
    assertFalse(result.reply.contains("Huyết Ảnh Ma Độn tự động kích hoạt"))
    assertFalse(result.reply.contains("Thiên Ma Bộ tự động kích hoạt"))
  }

  @Test fun luciaGetsAnIndependentCombatResolutionWhenBothAttack() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "0"))

    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả 2 cùng tấn công")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(
      result.reply.contains("bắn hỗ trợ bằng M4A1") ||
        result.reply.contains("cũng khai hỏa nhưng phát bắn không trúng mục tiêu")
    )
  }

  @Test fun luciaAttackIntentIsPartyWide() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val result = CombatRuntime.resolve(state, "EXECUTE", "Cao Minh tấn công")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(
      result.reply.contains("bắn hỗ trợ bằng M4A1") ||
        result.reply.contains("cũng khai hỏa nhưng phát bắn không trúng mục tiêu")
    )
  }


  @Test fun lifeformTrioUsesOnePointThreeHoundBaseBeforeSharedDurability() {
    for (key in listOf("blackroot_sentinel", "sinew_strider", "hollow_grasper")) {
      val active = CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!
      assertEquals(268, active.entityMaxHp)
      assertEquals(268, active.entityHp)
    }
  }

  @Test fun lifeformTrioExposesThreeAttackStatusSkillsPerEntity() {
    val expected = mapOf(
      "blackroot_sentinel" to setOf("Thorned Hemorrhage", "Blight Sap", "Crimson Mycotoxin"),
      "sinew_strider" to setOf("Tendon Ripper", "Septic Thread", "Venomous Flay"),
      "hollow_grasper" to setOf("Hollow Laceration", "Carrion Toxin", "Necrotic Clutch")
    )
    expected.forEach { (key, wanted) ->
      val seen = mutableSetOf<String>()
      for (counter in 0..700) {
        var state = CombatRuntime.start(GameState.initial(), key)
        state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
        val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
        wanted.forEach { skill ->
          if (result.reply.contains(skill)) {
            seen += skill
            val expectedPercent = when (skill) {
              "Thorned Hemorrhage", "Tendon Ripper", "Hollow Laceration" -> 115
              "Blight Sap", "Septic Thread", "Carrion Toxin" -> 125
              else -> 140
            }
            assertTrue(result.reply.contains(expectedPercent.toString() + "% Basic Attack"))
          }
        }
        if (seen == wanted) break
      }
      assertEquals("All three skills should proc for " + key, wanted, seen)
    }
  }

  @Test fun lifeformBleedAndPoisonAreTheOnlyPersistentStatusDamage() {
    var state = CombatRuntime.start(GameState.initial(), "blackroot_sentinel")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.lifeformBleedTurns" to "1",
      "combat.lifeformPoisonTurns" to "1"
    ))
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "EVADE", "né đòn")
    val bleed = result.feedback.single { it.status == CombatRuntime.FeedbackStatus.BLEED && it.target == "actor" }
    val poison = result.feedback.single { it.status == CombatRuntime.FeedbackStatus.POISON && it.target == "actor" }
    assertEquals(maxOf(1, (before.playerMaxHp * 3 + 99) / 100), bleed.amount)
    assertEquals(maxOf(1, (before.playerMaxHp * 2 + 99) / 100), poison.amount)
  }
}
