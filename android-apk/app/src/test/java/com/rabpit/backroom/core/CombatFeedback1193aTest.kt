package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Checks the live single-hit CombatRuntime contract. The previous 1.1.93a test
 * referenced poison procs and bridge APIs removed from this runtime generation.
 * Feedback below is always based on actual HP mutations, never guessed from log text.
 */
class CombatFeedback1193aTest {
  private fun started(seed: Int = 0): GameState {
    val state = CombatRuntime.start(GameState.initial(), "hound")
    return state.copy(metadata = state.metadata + ("combat.seed" to seed.toString()))
  }

  @Test fun feedbackDoesNotInventPoisonForRetiredProcMetadata() {
    val results = (0..200).map { seed ->
      val state = started(seed).copy(metadata = started(seed).metadata + ("combat.caoMinhProc3" to "1"))
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }
    assertTrue(results.any { it.feedback.any { event -> event.target == "entity" } })
    assertTrue(results.all { it.feedback.all { event -> event.amount > 0 && event.toJson().getString("text") == "-${event.amount} HP" } })
    assertTrue(results.all { it.feedback.none { event -> event.toJson().has("status") } })
  }

  @Test fun criticalFlagComesFromTheRuntimeRoll() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed).copy(metadata = started(seed).metadata + ("combat.daiDaoMaTonStacks" to "5"))
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }.first { it.feedback.any { event -> event.critical } }
    assertTrue(result.feedback.any { it.target == "entity" && it.critical })
    assertTrue(result.feedback.all { it.amount > 0 })
  }

  // Đại Ma Đầu runs in parallel with Đại Đạo Ma Tôn inside the single passive funnel
  // (CharacterStatCore.effective) that every combat number in CombatRuntime reads:
  // basic damage (effective.str), skill damage (effective.skl), crit chance
  // (effective.criticalChancePercent), incoming damage (effective.def) and
  // evasion/resistances (effective evasion/res fields). Base 100 is used because the
  // two rounding conventions for additive passives (one combined percent vs one
  // rounding per passive) agree exactly there: 100 + 10 (Đại Đạo) + 50 (Đại Ma Đầu) = 160.
  @Test fun daiMaDauAppliesInParallelWithDaiDaoInEffectiveStats() {
    val initial = GameState.initial()
    val cao = initial.characters.getValue(CAO_MINH_ID)
    val boosted = initial.copy(characters = initial.characters + (CAO_MINH_ID to cao.copy(
      statProfile = cao.statProfile.copy(str = 100, def = 100, skl = 100, vit = 100)
    )))
    val effective = CharacterStatCore.effective(boosted, CAO_MINH_ID)
    assertEquals(160, effective.str)
    assertEquals(160, effective.def)
    assertEquals(160, effective.skl)
    assertEquals(160, effective.vit)
    // Passives are Cao Minh-only: a companion's effective stats stay at base.
    val allyProfile = CharacterStatProfiles.forId(LUC_TRAM_ID)
    val allyEffective = CharacterStatCore.effective(initial, LUC_TRAM_ID)
    assertEquals(allyProfile.str, allyEffective.str)
    assertEquals(allyProfile.def, allyEffective.def)
    assertEquals(allyProfile.skl, allyEffective.skl)
    assertEquals(allyProfile.vit, allyEffective.vit)
  }

  @Test fun lethalFeedbackIsClippedToActualHpAndSurvivesCombatCleanup() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed).let { it.copy(metadata = it.metadata + ("combat.entityHp" to "1")) }
      CombatRuntime.resolve(state, "EXECUTE", "Tấn công")
    }.first { it.entityDestroyed }
    assertNull(CombatRuntime.active(result.state))
    assertEquals(1, result.feedback.filter { it.target == "entity" }.sumOf { it.amount })
    assertFalse(result.state.metadata.keys.any { it.contains("feedback", ignoreCase = true) })
  }

  @Test fun incomingDamageFeedbackEqualsRealHpDecrease() {
    val result = (0..200).asSequence().map { seed ->
      val state = started(seed)
      state.characters.getValue(CAO_MINH_ID).vitalState.currentHp to
        CombatRuntime.resolve(state, "EXECUTE", "Đứng yên")
    }.first { (_, resolution) -> resolution.feedback.any { it.target == "actor" } }
    val total = result.second.feedback.filter { it.target == "actor" }.sumOf { it.amount }
    val finalHp = result.second.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
    assertEquals(result.first - finalHp, total)
    assertEquals("entity", result.second.feedback.single { it.target == "actor" }.phase)
  }

  @Test fun feedbackIsTransientAndDoesNotBecomeSaveMetadata() {
    val result = (0..200).asSequence().map { seed ->
      CombatRuntime.resolve(started(seed), "EXECUTE", "Tấn công")
    }.first { it.feedback.isNotEmpty() }
    assertTrue(result.state.metadata.keys.none { it.contains("feedback", ignoreCase = true) })
    assertTrue(result.feedback.all { it.toJson().optString("actorId") == CAO_MINH_ID })
  }
}
