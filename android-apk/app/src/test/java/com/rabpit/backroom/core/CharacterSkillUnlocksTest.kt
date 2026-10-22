package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterSkillUnlocksTest {
  private val ids = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUC_TRAM_ID,
    LUCIA_ID, CATHERINE_ID, JANET_ID, NGUYEN_QUOC_NAM_ID, HUA_TIEU_LAN_ID)
  private fun atLevel(state: GameState, id: String, level: Int): GameState {
    val actor = state.characters.getValue(id)
    return state.copy(characters = state.characters + (id to actor.copy(
      presence = CharacterPresence.ACTIVE, progression = actor.progression.copy(level = level, exp = 0))))
  }
  private fun ready(id: String): GameState {
    val state = GameState.initial()
    val party = state.copy(party = PartyState(memberIds = listOf(id, CAO_MINH_ID).distinct()))
    return CombatRuntime.start(atLevel(party, id, 0), "diep_minh")
  }
  private fun hand(state: GameState): GameState = state.copy(metadata = state.metadata + mapOf(
    "poker.dice.encounter" to CombatRuntime.active(state)!!.encounterId,
    "poker.dice.action" to PokerDiceCore.DIRECT_COMBAT_ACTION,
    "poker.dice.values" to "1,2,3,4,5", "poker.dice.held" to "0,0,0,0,0",
    "poker.dice.finalized" to "true", "poker.dice.hand" to "SSF"))

  @Test fun everyKitStartsWithAllPassivesAndExactlyOneOtherSkillThenOnePerTenLevels() {
    for (id in ids) {
      val all = CompanionSkillCatalog.forCharacter(id)
      val ordered = all.filter { it.kind != "PASSIVE" }.sortedBy { it.kind == "ULTIMATE" }
      for (level in 0..100) {
        assertEquals("$id at $level", minOf(ordered.size, 1 + level / 10),
          ordered.count { CharacterSkillUnlocks.isUnlocked(id, it.name, level) })
        all.filter { it.kind == "PASSIVE" }.forEach { assertTrue(CharacterSkillUnlocks.isUnlocked(id, it.name, level)) }
      }
      ordered.forEachIndexed { index, skill -> assertEquals(index * 10, CharacterSkillUnlocks.requiredLevel(id, skill.name)) }
      assertEquals("ULTIMATE", ordered.last().kind)
    }
    assertFalse(CharacterSkillUnlocks.isUnlocked(CAO_MINH_ID, "invented", 165))
    assertFalse(CharacterSkillUnlocks.isUnlocked("unknown", "Huyết Ma Tứ Liên", 165))
  }

  @Test fun lowLevelCombatNeverRunsLockedAttackCounterDefenseStateOrUltimatePaths() {
    val actions = listOf(PokerDiceCore.DIRECT_COMBAT_ACTION, "Cả Party né tránh", "Cả Party phòng thủ", "quan sát kỹ chuyển động", "Cả Party bỏ chạy")
    for (id in ids) {
      val base = ready(id)
      val locked = CompanionSkillCatalog.forCharacter(id).filterNot { CharacterSkillUnlocks.isUnlocked(id, it.name, 0) }
      for (counter in 0..60) for (action in actions) {
        val state = hand(base.copy(metadata = base.metadata + ("combat.eventCounter" to counter.toString())))
        val result = CombatRuntime.resolve(state, "EXECUTE", action)
        for (skill in locked) {
          assertFalse("$id locked ${skill.name}: ${result.reply}", result.reply.contains(skill.name))
          assertTrue(result.feedback.none { it.skill == skill.name })
        }
      }
    }
  }

  @Test fun finalizedDiceCannotBypassUltimateLevelAndEachActorUnlocksAtOwnThreshold() {
    for (id in ids.filter { it != AN_NHIEN_ID }) {
      val ultimate = CompanionSkillCatalog.forCharacter(id).single { it.kind == "ULTIMATE" }
      val required = CharacterSkillUnlocks.requiredLevel(id, ultimate.name)!!
      var base = ready(id).copy(metadata = ready(id).metadata + mapOf("combat.range" to "CLOSE", "combat.opening" to "2"))
      if (id == LUC_TRAM_ID) {
        val equipment = base.equipment.getValue(id)
        base = base.copy(equipment = base.equipment + (id to equipment.copy(slots = equipment.slots + ("weapon" to LUC_TRAM_TICH_QUANG_ID))))
      }
      val below = CombatRuntime.resolve(hand(atLevel(base, id, required - 1)), "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)
      assertFalse("$id", below.reply.contains(ultimate.name))
      val reached = atLevel(base, id, required)
      val loaded = GameStateCodec.decode(GameStateCodec.encode(reached))
      val result = CombatRuntime.resolve(hand(loaded), "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)
      assertTrue("$id level $required: ${result.reply}", result.reply.contains(ultimate.name))
      if (id != CAO_MINH_ID) assertEquals(0, reached.characters.getValue(CAO_MINH_ID).progression.level)
    }
  }

  @Test fun utilityAtTenAndAnNhienUltimateAtFortyRespectBoundary() {
    val base = ready(NGUYEN_QUOC_NAM_ID)
    for (level in listOf(9, 10)) {
      val injured = CharacterStatEngine.setCurrentHp(atLevel(base, NGUYEN_QUOC_NAM_ID, level), CAO_MINH_ID, 10)
      val result = CombatRuntime.resolve(injured, "EXECUTE", "Cả Party phòng thủ")
      assertEquals(level == 10, result.reply.contains("Sơ Cứu Tại Chỗ"))
    }
    val an = ready(AN_NHIEN_ID).let { it.copy(metadata = it.metadata + ("combat.eventCounter" to "4")) }
    for (level in listOf(39, 40)) {
      val result = CombatRuntime.resolve(atLevel(an, AN_NHIEN_ID, level), "EXECUTE", "Cả Party bỏ chạy")
      assertEquals(level == 40, result.reply.contains("Kế Hoạch Không Có Trong Kế Hoạch"))
    }
  }

  @Test fun projectionExposesOwnLevelLocksAndRefreshesAfterExpAndSaveLoad() {
    val initial = GameState.initial()
    val before = atLevel(initial, CATHERINE_ID, 9)
    val exp = CharacterProgressionCore.expRequired(9)
    val after = CharacterProgressionCore.grantExp(before, CATHERINE_ID, exp).state
    val loaded = GameStateCodec.decode(GameStateCodec.encode(after))
    for ((state, locked) in listOf(before to true, loaded to false)) {
      val projection = CharacterDetailJson.encodeCharacter(CharacterDetailProjector.projectCharacter(state, CATHERINE_ID)!!)
      val skills = projection.getJSONArray("skills")
      val second = (0 until skills.length()).map { skills.getJSONObject(it) }.single { it.getString("name") == "Hoa Hồng Xuyên Giáp" }
      assertEquals(10, second.getInt("unlockLevel"));assertEquals(locked, second.getBoolean("locked"))
    }
    assertEquals(0, loaded.characters.getValue(CAO_MINH_ID).progression.level)
  }

  @Test fun toxicPoisonPersistsAtTenButLockedCorrosiveOrUnattributedPoisonDoesNot() {
    val base = atLevel(ready(LUCIA_ID), LUCIA_ID, 10)
    val poison = base.copy(metadata = base.metadata + mapOf("combat.luciaPoisonTurns" to "2", "combat.luciaPoisonPercent" to "3"))
    val toxic = poison.copy(metadata = poison.metadata + ("combat.luciaPoisonSource" to "Toxic Burst"))
    assertEquals("2", CharacterSkillUnlocks.enforceCombatState(toxic).metadata["combat.luciaPoisonTurns"])
    val corrosive = poison.copy(metadata = poison.metadata + ("combat.luciaPoisonSource" to "Corrosive Burst"))
    assertFalse(CharacterSkillUnlocks.enforceCombatState(corrosive).metadata.containsKey("combat.luciaPoisonTurns"))
    assertFalse(CharacterSkillUnlocks.enforceCombatState(poison).metadata.containsKey("combat.luciaPoisonTurns"))
    assertEquals("2", CharacterSkillUnlocks.enforceCombatState(atLevel(poison, LUCIA_ID, 50)).metadata["combat.luciaPoisonTurns"])
  }

  @Test fun loadedLockedStateCannotContinueBuffsOrDots() {
    val state = ready(SYVIAL_ID).copy(metadata = ready(SYVIAL_ID).metadata + mapOf(
      "combat.syvialDevilTrigger" to "true", "combat.syvialDisorientTurns" to "2",
      "combat.syvialBleedTurns" to "3", "combat.caoMinhQuickStepTurns" to "3",
      "combat.caoMinhProc1" to "2", "combat.luciaPoisonTurns" to "2"))
    val result = CombatRuntime.resolve(GameStateCodec.decode(GameStateCodec.encode(state)), "EXECUTE", "Cả Party phòng thủ")
    assertFalse(result.reply.contains("Devil Trigger"));assertFalse(result.reply.contains("Crimson Guillotine"))
    assertFalse(result.state.metadata.containsKey("combat.syvialDevilTrigger"))
    assertFalse(result.state.metadata.containsKey("combat.caoMinhProc1"))
  }
}
