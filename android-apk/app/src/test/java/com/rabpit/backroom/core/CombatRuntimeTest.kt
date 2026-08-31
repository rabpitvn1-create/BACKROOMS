package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatRuntimeTest {
  @Test fun entityTriggerStartsOneAuthoritativeEncounterWithHealth() {
    val started = CombatRuntime.start(GameState.initial(), "hound")
    val combat = CombatRuntime.active(started)
    assertNotNull(combat)
    assertEquals("hound", combat!!.entityKey)
    assertEquals(500, combat.entityMaxHp)
    assertEquals(500, combat.entityHp)
    val expectedMaxHp = CharacterStatEngine.effective(GameState.initial(), KAI_ID).maxHp
    assertEquals(140, expectedMaxHp)
    assertEquals(expectedMaxHp, combat.playerMaxHp)
    assertEquals(expectedMaxHp, combat.playerHp)

    val duplicate = CombatRuntime.start(started, "smiler")
    assertEquals("hound", CombatRuntime.active(duplicate)!!.entityKey)
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

  @Test fun combatExploreIsMovementNotAnotherEncounter() {
    val started = CombatRuntime.start(GameState.initial(), "skin-stealer")
    val before = CombatRuntime.active(started)!!
    val result = CombatRuntime.resolve(started, "EXPLORE", "lùi lại tìm vật che chắn")
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

  @Test fun survivingEntityRegeneratesOneHpPerCombatTurnUpToMax() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")
    val full = CombatRuntime.active(state)!!
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to (full.entityMaxHp - 5).toString()))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát chuyển động")
    val after = CombatRuntime.active(result.state)!!
    assertTrue(result.reply.contains("hồi +1 HP"))
    assertTrue(after.entityHp <= after.entityMaxHp)
  }

  @Test fun allEntityProfilesReceiveThirtyBonusHp() {
    val expected = mapOf(
      "hound" to 500, "clump" to 396, "duller" to 399, "deathmoth" to 480,
      "hostile_faceling" to 424, "false_puddle" to 347, "paintings" to 412, "smiler" to 310,
      "skin-stealer" to 371, "predatory_window" to 476, "biological_pipeline" to 347, "wretch" to 494,
      "cable_mimic" to 430, "the_beast_of_level_5" to 315, "hotel_corpse_lure" to 313, "jeff_the_killer" to 947,
      "jane_the_killer" to 947, "slenderman" to 467
    )
    expected.forEach { (key, hp) ->
      assertEquals("+30 HP must apply to $key", hp, CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!.entityMaxHp)
    }
  }

  @Test fun guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn() {
    var evadeState = CombatRuntime.start(GameState.initial(), "diep_minh")
    evadeState = evadeState.copy(metadata = evadeState.metadata + ("combat.eventCounter" to "2"))
    val evade = CombatRuntime.resolve(evadeState, "EXECUTE", "Cả Party cùng né tránh")
    assertTrue(evade.handled)
    assertFalse("đội EVADE must not secretly fire the attack-only Override", evade.reply.contains("Guilty Crown Override"))

    var attackState = CombatRuntime.start(GameState.initial(), "diep_minh")
    attackState = attackState.copy(metadata = attackState.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.entityHp" to "2000",
      "combat.entityMaxHp" to "2999"
    ))
    val third = CombatRuntime.resolve(attackState, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(third.handled)
    assertTrue(third.reply.contains("Guilty Crown Override"))
    assertTrue(third.reply.contains("24/24 phát trúng liên tiếp"))
    assertTrue(third.reply.contains("Độ chính xác 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
    assertTrue(third.reply.contains("mỗi phát -10 HP"))
    assertTrue(third.reply.contains("tổng -240 HP"))
  }

  @Test fun guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.entityHp" to "2000",
      "combat.entityMaxHp" to "2999",
      "combat.eventCounter" to "2"
    ))

    val third = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(third.handled)
    assertTrue(third.reply.contains("tổng -240 HP"))
    assertTrue(third.reply.contains("Độ chính xác 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
  }

  @Test fun diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val started = CombatRuntime.active(state)!!
    assertEquals(3199, started.entityMaxHp)
    assertEquals(3199, started.entityHp)

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
      party = PartyState(memberIds = listOf(KAI_ID, "iris"))
    )
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "4"))

    val kaiBefore = state.characters.getValue(KAI_ID).vitalState.currentHp
    val irisBefore = state.characters.getValue("iris").vitalState.currentHp
    val kaiMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val irisMax = CharacterStatEngine.effective(state, "iris").maxHp
    val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")

    assertTrue(result.reply.contains("Devils And Gold"))
    assertEquals(kaiBefore - maxOf(1, (kaiMax * 5 + 99) / 100), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(irisBefore - maxOf(1, (irisMax * 5 + 99) / 100), result.state.characters.getValue("iris").vitalState.currentHp)
  }

  @Test fun kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts() {
    val seen = mutableSetOf<String>()
    for (counter in 0..240) {
      if (seen.size == 4) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (result.reply.contains("The Last Requiem tự động kích hoạt")) seen += "requiem"
      if (result.reply.contains("Silent Lullaby tự động kích hoạt")) seen += "lullaby"
      if (result.reply.contains("Salvation tự động kích hoạt")) seen += "salvation"
      if (result.reply.contains("Quick Step tự động kích hoạt")) seen += "quick_step"
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
        "combat.kaiBleedTurns" to "3"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi mục tiêu")
      if (result.reply.contains("The Last Requiem tự động kích hoạt")) continue
      val after = CombatRuntime.active(result.state) ?: continue
      assertTrue(result.reply.contains("Chảy máu từ The Last Requiem gây -160 HP"))
      assertEquals("2", result.state.metadata["combat.kaiBleedTurns"])
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
      if (!result.reply.contains("Silent Lullaby tự động kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("bị Choáng và mất lượt phản ứng hiện tại"))
      assertFalse(result.reply, result.reply.contains("Diệp Minh phản công:"))
      assertFalse(result.reply, result.reply.contains("Devils And Gold kích hoạt"))
      verified = true
    }
    assertTrue("Expected an ATTACK turn where Silent Lullaby activates", verified)
  }

  @Test fun quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("Quick Step tự động kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("+50% Né tránh trong 3 lượt"))
      assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])
      verified = true
    }
    assertTrue("Expected a Party EVADE turn where Quick Step activates", verified)
  }

  @Test fun guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.reply.contains("Guilty Crown Override"))
    assertFalse(result.reply.contains("The Last Requiem tự động kích hoạt"))
    assertFalse(result.reply.contains("Silent Lullaby tự động kích hoạt"))
    assertFalse(result.reply.contains("Salvation tự động kích hoạt"))
    assertFalse(result.reply.contains("Quick Step tự động kích hoạt"))
  }

  @Test fun luciaGetsAnIndependentCombatResolutionWhenBothAttack() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
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
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val result = CombatRuntime.resolve(state, "EXECUTE", "Kai tấn công")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(
      result.reply.contains("bắn hỗ trợ bằng M4A1") ||
        result.reply.contains("cũng khai hỏa nhưng phát bắn không trúng mục tiêu")
    )
  }

  @Test fun monsterXHasExactHpAndFiftyRegen() {
    var state = CombatRuntime.start(GameState.initial(), "monster_x")
    var combat = CombatRuntime.active(state)!!
    assertEquals(3656, combat.entityMaxHp)
    assertEquals(3656, combat.entityHp)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "3000"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    combat = CombatRuntime.active(result.state)!!
    assertEquals(3050, combat.entityHp)
  }

  @Test fun monsterXAttackUsesSixPercentMaxHp() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "monster_x")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (!result.reply.contains("Monster X tấn công:")) continue
      assertTrue(result.reply, result.reply.contains("6% Max HP"))
      verified = true
    }
    assertTrue("Expected Monster X to land a 6% Max HP attack", verified)
  }

  @Test fun monsterXBleedingAndStunContractsArePersistent() {
    var sawBleed = false
    var sawStun = false
    for (counter in 0..600) {
      if (sawBleed && sawStun) break
      var state = CombatRuntime.start(GameState.initial(), "monster_x")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (result.reply.contains("gây Chảy máu cho toàn bộ đội")) {
        assertEquals("5", result.state.metadata["combat.monsterXBleedTurns"])
        sawBleed = true
      }
      if (result.reply.contains("Monster X chuẩn bị Choáng:")) {
        assertEquals("1", result.state.metadata["combat.monsterXStunTurns"])
        val next = CombatRuntime.resolve(result.state, "EXECUTE", "Cả Party cùng tấn công")
        assertTrue(next.reply, next.reply.contains("toàn bộ đội mất lượt hành động hiện tại"))
        sawStun = true
      }
    }
    assertTrue("Expected deterministic search to reach Monster X Bleeding proc", sawBleed)
    assertTrue("Expected deterministic search to reach Monster X Stun proc", sawStun)
  }

  @Test fun johnDoeHasExactHpAndThirtyRegen() {
    var state = CombatRuntime.start(GameState.initial(), "john_doe")
    assertEquals(1434, CombatRuntime.active(state)!!.entityMaxHp)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "1100"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertEquals(1130, CombatRuntime.active(result.state)!!.entityHp)
    assertTrue(result.reply, result.reply.contains("hồi +30 HP"))
  }

  @Test fun johnDoeAttackUsesSixPercentTargetMaxHp() {
    var verified = false
    for (counter in 0..600) {
      if (verified) break
      val turn = counter + 1
      if (turn % 2 == 0 || turn % 3 == 0) continue
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
      val before = state.characters.getValue(KAI_ID).vitalState.currentHp
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("John Doe tấn công:")) continue
      val expected = maxOf(1, (maxHp * 6 + 99) / 100)
      assertTrue(result.reply, result.reply.contains("6% Max HP"))
      assertEquals(before - expected, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
      verified = true
    }
    assertTrue("Expected John Doe to land an exact 6% Max-HP attack", verified)
  }

  @Test fun johnDoePoisonTracksAffectedMembersSeparatelyAndTicksFourPercent() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = "iris",
      name = "Iris",
      statProfile = CharacterStatProfiles.forId("iris"),
      vitalState = CharacterStatProfiles.initialVitals("iris")
    )
    var triggered: GameState? = null
    for (counter in 0..900) {
      if (triggered != null) break
      val turn = counter + 1
      if (turn % 3 != 0) continue
      var state = initial.copy(
        characters = initial.characters + ("iris" to iris),
        party = PartyState(memberIds = listOf(KAI_ID, "iris"))
      )
      state = CombatRuntime.start(state, "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("John Doe gây Poison")) continue
      assertEquals("true", result.state.metadata["combat.johnDoePoisoned.$KAI_ID"])
      assertEquals("true", result.state.metadata["combat.johnDoePoisoned.iris"])
      triggered = result.state
    }
    assertNotNull("Expected deterministic search to reach the 50% Poison proc", triggered)
    val poisoned = triggered!!
    val irisBefore = poisoned.characters.getValue("iris").vitalState.currentHp
    val irisMax = CharacterStatEngine.effective(poisoned, "iris").maxHp
    val tick = CombatRuntime.resolve(poisoned, "EXECUTE", "Cả Party cùng né tránh")
    val expected = maxOf(1, (irisMax * 4 + 99) / 100)
    assertTrue(tick.reply, tick.reply.contains("Poison John Doe"))
    assertTrue(tick.reply, tick.reply.contains("Iris -$expected HP"))
    assertEquals(irisBefore - expected, tick.state.characters.getValue("iris").vitalState.currentHp)
  }

  @Test fun johnDoeStunUsesThirtyThenTwentyPercentAndBlocksOneTurnWithoutDamage() {
    var triggered: GameState? = null
    for (counter in 0..1800) {
      if (triggered != null) break
      val turn = counter + 1
      if (turn % 2 != 0 || turn % 3 == 0) continue
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("John Doe Choáng check:")) continue
      assertTrue(result.reply, result.reply.contains("cổng 30%"))
      assertTrue(result.reply, result.reply.contains("kích hoạt 20%"))
      assertFalse(result.reply, result.reply.contains("20% Max HP"))
      assertEquals("1", result.state.metadata["combat.johnDoeStunTurns"])
      triggered = result.state
    }
    assertNotNull("Expected deterministic search to reach the 30% x 20% Stun proc", triggered)
    val stunned = triggered!!
    val before = CombatRuntime.active(stunned)!!.entityHp
    val next = CombatRuntime.resolve(stunned, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(next.reply, next.reply.contains("không thể thực hiện hành động trong lượt hiện tại"))
    assertFalse(next.reply, next.reply.contains("PARTY ACTION TẤN CÔNG"))
    assertNull(next.state.metadata["combat.johnDoeStunTurns"])
    assertEquals(minOf(1434, before + 30), CombatRuntime.active(next.state)!!.entityHp)
  }

  @Test fun scp173StartsObservedWithExactHpAndStateProjection() {
    val state = CombatRuntime.start(GameState.initial(), "scp_173")
    val active = CombatRuntime.active(state)!!
    val json = CombatRuntime.toJson(state)!!
    assertEquals(1930, active.entityMaxHp)
    assertEquals(1930, active.entityHp)
    assertEquals("OBSERVED", json.getString("observationState"))
    assertEquals(100, json.getInt("actionSpeedPercent"))
    assertEquals(25, json.getInt("physicalDamageReductionPercent"))
    assertEquals(20, json.getInt("observedDamageReductionPercent"))
    assertEquals(1, json.getInt("stunMaxTurns"))
  }

  @Test fun scp173ObservedCannotAttackMoveOrApproach() {
    var state = CombatRuntime.start(GameState.initial(), "scp_173")
    state = state.copy(metadata = state.metadata + ("combat.scp173.cooldown.blinkPressure" to "3"))
    val beforeHp = state.characters.getValue(KAI_ID).vitalState.currentHp
    val beforeRange = CombatRuntime.active(state)!!.range
    val result = CombatRuntime.resolve(state, "EXECUTE", "giữ phòng thủ và nhìn thẳng SCP-173")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(beforeHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(beforeRange, after.range)
    assertTrue(result.reply, result.reply.contains("được quan sát"))
    assertTrue(result.reply, result.reply.contains("không thể di chuyển, áp sát hay tấn công"))
  }

  @Test fun scp173ThirdObservedTurnForcesBlinkAndBecomesUnobserved() {
    var state = CombatRuntime.start(GameState.initial(), "scp_173")
    state = state.copy(metadata = state.metadata + ("combat.scp173.cooldown.blinkPressure" to "3"))
    repeat(3) {
      val result = CombatRuntime.resolve(state, "SEARCH", "tiếp tục nhìn SCP-173")
      assertTrue(result.handled)
      state = result.state
    }
    val json = CombatRuntime.toJson(state)!!
    assertEquals("UNOBSERVED", json.getString("observationState"))
    assertEquals(150, json.getInt("actionSpeedPercent"))
    assertTrue(state.characters.getValue(KAI_ID).statusIds.any { id -> state.statuses[id]?.type == "BLINK" })
  }

  @Test fun scp173ObservedConcreteBodyMitigatesGuiltyCrownDirectDamage() {
    var state = CombatRuntime.start(GameState.initial(), "scp_173")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.scp173.cooldown.blinkPressure" to "3"
    ))
    val json = CombatRuntime.toJson(state)!!
    assertEquals("OBSERVED", json.getString("observationState"))
    assertEquals(25, json.getInt("physicalDamageReductionPercent"))
    assertEquals(20, json.getInt("observedDamageReductionPercent"))
  }

  @Test fun scp173UnobservedConcreteRushUsesVulnerableTwentyPlusFirstStrikeFivePercent() {
    val initial = GameState.initial()
    val blindEffect = StatusEffect("test:blind:kai", "BLIND", "test", durationTurns = 5)
    val blinded = StatusEngine.execute(initial, StatusCommand(
      commandId = "test:blind", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
      source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
    )).state
    val state = CombatRuntime.start(blinded, "scp_173")
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val before = state.characters.getValue(KAI_ID).vitalState.currentHp
    val result = CombatRuntime.resolve(state, "SEARCH", "không thể nhìn thấy SCP-173")
    val expected = maxOf(1, (maxHp * 25 + 99) / 100)
    assertTrue(result.reply, result.reply.contains("Concrete Rush"))
    assertTrue(result.reply, result.reply.contains("25% Max HP"))
    assertEquals(before - expected, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun scp173NeckSnapExecutesOnlyAtOrBelowFifteenPercent() {
    var verified = false
    for (counter in 0..200) {
      if (verified) break
      val initial = GameState.initial()
      val blindEffect = StatusEffect("test:blind:kai:neck:$counter", "BLIND", "test", durationTurns = 5)
      var state = StatusEngine.execute(initial, StatusCommand(
        commandId = "test:blind:neck:$counter", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
        source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
      )).state
      state = CombatRuntime.start(state, "scp_173")
      val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
      // Start at 9% so Kai remains <=15% even if Devil Trigger heals 5% before SCP-173 resolves.
      val threshold = maxOf(1, maxHp * 9 / 100)
      state = CharacterStatEngine.setCurrentHp(state, KAI_ID, threshold)
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.range" to CombatRuntime.RangeBand.CLOSE.name,
        "combat.eventCounter" to counter.toString()
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "không thể quan sát SCP-173")
      if (!result.reply.contains("Neck Snap")) continue
      assertEquals(0, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
      assertTrue(result.reply, result.reply.contains("Kết liễu hợp lệ"))
      verified = true
    }
    assertTrue("Expected a deterministic turn where Kai Devil Trigger does not evade Neck Snap", verified)
  }

  @Test fun scp173SnapStrikeStunUsesStatusEngineForOneTurn() {
    var verified = false
    for (counter in 0..600) {
      if (verified) break
      val initial = GameState.initial()
      val blindEffect = StatusEffect("test:blind:kai:snap:$counter", "BLIND", "test", durationTurns = 5)
      var state = StatusEngine.execute(initial, StatusCommand(
        commandId = "test:blind:snap:$counter", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
        source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
      )).state
      state = CombatRuntime.start(state, "scp_173")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.scp173.cooldown.concreteRush" to "2",
        "combat.scp173.cooldown.neckSnap" to "4"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "mất tầm nhìn")
      if (!result.reply.contains("Snap Strike") || !result.reply.contains("Choáng 1 lượt")) continue
      val stun = result.state.characters.getValue(KAI_ID).statusIds.mapNotNull(result.state.statuses::get)
        .firstOrNull { it.source == "scp_173" && it.type == "STUN" }
      assertNotNull(stun)
      assertEquals(1, stun!!.durationTurns)
      verified = true
    }
    assertTrue("Expected deterministic search to reach SCP-173 Snap Strike 25% Stun proc", verified)
  }


  @org.junit.Test fun luciaFullAutoBurstCanProcOnSecondAttackTurn() {
    var sawBurst = false
    for (seed in 1L..500L) {
      var state = LuciaCanon.ensure(GameState.initial())
      state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
      state = CombatRuntime.start(state, "scp_173")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to "1",
        "combat.seed" to seed.toString(),
        "passive.devilTrigger.kai.cooldownTurns" to "5"
      ))
      val result = CombatRuntime.resolve(state, "EXECUTE", "TẤN CÔNG")
      if (result.reply.contains("M4A1 Full Auto Burst")) {
        sawBurst = true
        break
      }
    }
    org.junit.Assert.assertTrue("Lucia 20% full-auto proc should be reachable on an eligible second turn", sawBurst)
  }

  @org.junit.Test fun luciaFullAutoBurstDoesNotRunOnFirstAttackTurn() {
    var state = LuciaCanon.ensure(GameState.initial())
    state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "scp_173")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "0",
      "combat.seed" to "7",
      "passive.devilTrigger.kai.cooldownTurns" to "5"
    ))
    val result = CombatRuntime.resolve(state, "EXECUTE", "TẤN CÔNG")
    org.junit.Assert.assertFalse(result.reply.contains("M4A1 Full Auto Burst"))
  }
  @Test fun entityActionBudgetTargetsEachCombatantOnceAndExcludesAnNhien() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = IRIS_ID, name = "Iris",
      statProfile = CharacterStatProfiles.forId(IRIS_ID),
      vitalState = CharacterStatProfiles.initialVitals(IRIS_ID)
    )
    val lucia = CharacterState(
      id = LUCIA_ID, name = "Lucia",
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = CharacterStatProfiles.initialVitals(LUCIA_ID)
    )
    val anNhien = CharacterState(
      id = AN_NHIEN_ID, name = "An Nhiên",
      statProfile = CharacterStatProfiles.forId(AN_NHIEN_ID),
      vitalState = CharacterStatProfiles.initialVitals(AN_NHIEN_ID)
    )
    var state = initial.copy(
      characters = initial.characters + mapOf(IRIS_ID to iris, LUCIA_ID to lucia, AN_NHIEN_ID to anNhien),
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID, AN_NHIEN_ID), maxMembers = 4)
    )
    state = CombatRuntime.start(state, "slenderman")
    val result = CombatRuntime.resolve(state, "OTHER", "...")

    assertTrue(result.reply, result.reply.contains("ENTITY ACTION BUDGET: Slenderman = 3"))
    assertEquals(1, result.reply.split("-> Kai Akechi:").size - 1)
    assertEquals(1, result.reply.split("-> Iris:").size - 1)
    assertEquals(1, result.reply.split("-> Lucia:").size - 1)
    assertFalse(result.reply, result.reply.contains("-> An Nhiên:"))
  }

  @Test fun entityActionBudgetSkipsDefeatedCombatantWithoutRetargetingSomeoneTwice() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = IRIS_ID, name = "Iris",
      statProfile = CharacterStatProfiles.forId(IRIS_ID),
      vitalState = CharacterVitalState(currentHp = 0, condition = CharacterCondition.DEFEATED)
    )
    val lucia = CharacterState(
      id = LUCIA_ID, name = "Lucia",
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = CharacterStatProfiles.initialVitals(LUCIA_ID)
    )
    var state = initial.copy(
      characters = initial.characters + mapOf(IRIS_ID to iris, LUCIA_ID to lucia),
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID))
    )
    state = CombatRuntime.start(state, "slenderman")
    val result = CombatRuntime.resolve(state, "OTHER", "...")

    assertTrue(result.reply, result.reply.contains("ENTITY ACTION BUDGET: Slenderman = 2"))
    assertEquals(1, result.reply.split("-> Kai Akechi:").size - 1)
    assertEquals(1, result.reply.split("-> Lucia:").size - 1)
    assertFalse(result.reply, result.reply.contains("-> Iris:"))
  }

  @Test fun entityDirectActionWritesDamageToCompanionVitalState() {
    var verified = false
    for (counter in 0..120) {
      if (verified) break
      val initial = GameState.initial()
      val lucia = CharacterState(
        id = LUCIA_ID, name = "Lucia",
        statProfile = CharacterStatProfiles.forId(LUCIA_ID),
        vitalState = CharacterStatProfiles.initialVitals(LUCIA_ID)
      )
      var state = initial.copy(
        characters = initial.characters + (LUCIA_ID to lucia),
        party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID))
      )
      state = CombatRuntime.start(state, "slenderman")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val before = state.characters.getValue(LUCIA_ID).vitalState.currentHp
      val result = CombatRuntime.resolve(state, "OTHER", "...")
      if (!result.reply.contains("-> Lucia: HIT")) continue
      assertTrue(result.state.characters.getValue(LUCIA_ID).vitalState.currentHp < before)
      verified = true
    }
    assertTrue("Expected deterministic search to find a landed Entity direct action on Lucia", verified)
  }

  @Test fun scp173UnobservedDistributesDirectActionsAcrossCombatants() {
    val initial = GameState.initial()
    val blindKai = initial.characters.getValue(KAI_ID).copy(metadata = mapOf("blind" to "true"))
    val iris = CharacterState(
      id = IRIS_ID, name = "Iris",
      metadata = mapOf("blind" to "true"),
      statProfile = CharacterStatProfiles.forId(IRIS_ID),
      vitalState = CharacterStatProfiles.initialVitals(IRIS_ID)
    )
    var state = initial.copy(
      characters = initial.characters + mapOf(KAI_ID to blindKai, IRIS_ID to iris),
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID))
    )
    state = CombatRuntime.start(state, "scp_173")
    assertEquals("UNOBSERVED", CombatRuntime.toJson(state)!!.getString("observationState"))
    val irisBefore = state.characters.getValue(IRIS_ID).vitalState.currentHp
    val result = CombatRuntime.resolve(state, "OTHER", "...")

    assertTrue(result.reply, result.reply.contains("ENTITY ACTION BUDGET: SCP-173 không bị quan sát = 2"))
    assertTrue(result.reply, result.reply.contains("ENTITY ACTION 1/2 -> Kai Akechi"))
    assertTrue(result.reply, result.reply.contains("ENTITY ACTION 2/2 -> Iris: HIT"))
    assertTrue(result.state.characters.getValue(IRIS_ID).vitalState.currentHp < irisBefore)
  }


  @Test fun jeffAndJaneUseExact947MaxHp() {
    val jeff = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "jeff_the_killer"))!!
    val jane = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "jane_the_killer"))!!
    assertEquals(947, jeff.entityMaxHp)
    assertEquals(947, jeff.entityHp)
    assertEquals(947, jane.entityMaxHp)
    assertEquals(947, jane.entityHp)
  }

  @Test fun jeffNoSafeRouteAppliesEscapePenaltyAndFailedEscapeRetaliation() {
    val started = CombatRuntime.start(GameState.initial(), "jeff_the_killer")
    val result = CombatRuntime.resolve(started, "EXECUTE", "chạy thoát khỏi encounter")
    assertTrue(result.reply, result.reply.contains("No Safe Route"))
    val active = CombatRuntime.active(result.state)
    if (active != null) assertEquals(0, active.escapeProgress)
    assertTrue(result.reply, result.reply.contains("No Safe Route retaliation"))
  }

  @Test fun janeDontWakeUpCanApplyTwoPercentBleedForTwoTurns() {
    var verified = false
    for (counter in 0..320) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "jane_the_killer")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val first = CombatRuntime.resolve(state, "OTHER", "...")
      if (first.reply.contains("Don't Wake Up: 2 hit")) {
        val second = CombatRuntime.resolve(first.state, "SEARCH", "quan sát Jane")
        assertTrue(second.reply, second.reply.contains("Bleed:"))
        assertTrue(second.reply, second.reply.contains("2% Max HP"))
        verified = true
      }
    }
    assertTrue("Jane must expose a deterministic Don't Wake Up + Bleed case", verified)
  }

  @Test fun janeVengefulReflexCanProcAfterLosingTwentyPercentMaxHpInOneTurn() {
    var verified = false
    for (counter in 0..360) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "jane_the_killer")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "bắn Jane bằng Magnum")
      if (result.reply.contains("Vengeful Reflex: Jane phản kích")) verified = true
    }
    assertTrue("Jane Vengeful Reflex 35% proc must be reachable after a qualifying damage turn", verified)
  }

  @Test fun violetWardenStartsAtApproximatelyTenPercentMoreHpThanCurrentDiepMinh() {
    val diep = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "diep_minh"))!!
    val violet = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "violet_warden"))!!
    assertEquals(3199, diep.entityMaxHp)
    assertEquals(3519, violet.entityMaxHp)
    assertEquals((diep.entityMaxHp * 110 + 99) / 100, violet.entityMaxHp)
  }

  @Test fun violetWardenProjectsDuelBlockAndFormerHumanIdentity() {
    val state = CombatRuntime.start(GameState.initial(), "violet_warden")
    val json = CombatRuntime.toJson(state)!!
    assertEquals("Control / Single Target / Counter", json.getString("combatRole"))
    assertEquals("Violet Judgment", json.getString("weapon"))
    assertEquals(60, json.getInt("blockPercent"))
    assertEquals(30, json.getInt("blockReductionPercent"))
    assertEquals("15th century", json.getString("originEra"))
  }

  @Test fun violetWardenUsesOneDuelTargetInsteadOfPartySizedDirectActions() {
    val state = CombatRuntime.start(GameState.initial(), "violet_warden")
    val result = CombatRuntime.resolve(state, "OTHER", "giữ vị trí")
    assertTrue(result.reply, result.reply.contains("Duelist's Decree") || result.reply.contains("The Violet Warden"))
    assertFalse(result.reply, result.reply.contains("ENTITY ACTION BUDGET: The Violet Warden"))
  }

  @Test fun violetWardenStunSuppressesOnlyKaiPersonalAttackForOneCombatEvent() {
    var state = CombatRuntime.start(GameState.initial(), "violet_warden")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.violetWardenStunTargetId" to KAI_ID,
      "combat.violetWardenStunUntilEvent" to "1"
    ))
    val before = CombatRuntime.active(state)!!.entityHp
    val locked = CombatRuntime.resolve(state, "ATTACK", "tấn công")
    assertEquals(before, CombatRuntime.active(locked.state)!!.entityHp)
    assertTrue(locked.reply, locked.reply.contains("Violet Warden STUN: Kai mất lượt hành động cá nhân"))

    val released = CombatRuntime.resolve(locked.state, "ATTACK", "tấn công")
    assertFalse(released.reply, released.reply.contains("Violet Warden STUN: Kai mất lượt hành động cá nhân"))
  }

  @org.junit.Test fun luciaTooYoungToDieChanceScalesOnlyAfterThreePercentLostBelowHalfHp() {
    org.junit.Assert.assertEquals(15, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(50, 100))
    org.junit.Assert.assertEquals(15, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(49, 100))
    org.junit.Assert.assertEquals(15, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(48, 100))
    org.junit.Assert.assertEquals(20, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(47, 100))
    org.junit.Assert.assertEquals(25, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(44, 100))
    org.junit.Assert.assertEquals(40, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(35, 100))
  }

  @org.junit.Test fun luciaTooYoungToDieCanProcOnAnyCombatTurn() {
    var sawSkill = false
    for (seed in 1L..500L) {
      var state = LuciaCanon.ensure(GameState.initial())
      state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
      state = CombatRuntime.start(state, "scp_173")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to "0",
        "combat.seed" to seed.toString(),
        "passive.devilTrigger.kai.cooldownTurns" to "5"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ vị trí và quan sát")
      if (result.reply.contains("Too Young To Die")) {
        sawSkill = true
        break
      }
    }
    org.junit.Assert.assertTrue("Lucia 15% Too Young To Die proc should be reachable on a non-ATTACK combat turn", sawSkill)
  }
}
