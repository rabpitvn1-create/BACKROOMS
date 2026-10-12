package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryMilestoneOutcomeTest {
  private fun effects(hpLoss: Int = 12, characterId: String = LUCIA_ID): JSONArray =
    JSONArray().put(JSONObject()
      .put("type", "INJURY")
      .put("characterId", characterId)
      .put("label", "vết cắn trên vai")
      .put("hpLoss", hpLoss))

  private fun plan(effects: JSONArray): String = JSONObject()
    .put("schemaVersion", 1).put("checkpointId", "CP-L00-01")
    .put("events", JSONArray()
      .put(JSONObject().put("id", "injury").put("turn", 1)
        .put("kind", "STORY").put("event", "Hound lao tới Lucia")
        .put("outcome", "Lucia bị thương, vết cắn trên vai")
        .put("effects", effects))
      .put(JSONObject().put("id", "boss").put("turn", 3)
        .put("kind", "FINAL_ENTITY").put("event", "Hound lộ diện")
        .put("outcome", "Combat bắt đầu").put("entityKey", "hound")
        .put("hpMultiplier", 9).put("expMultiplier", 9)))
    .toString()

  private fun base(withLucia: Boolean): GameState {
    var state = StoryCheckpointCore.seedLevelZero(
      CoreClientProjection.initialize(GameState.initial()))
    if (withLucia) {
      state = LuciaCanon.ensure(state)
      state = state.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID)))
    }
    return state
  }

  @Test fun plannedInjuryChangesHpAndPermanentStatusAtomically() {
    val base = base(true)
    val planned = StoryMilestonePlan.installCurrent(base, plan(effects()))
    val oldHp = planned.characters.getValue(LUCIA_ID).vitalState.currentHp
    val committed = StoryMilestoneRuntime.acceptTurn(planned, "[\"injury\"]")
    val lucia = committed.characters.getValue(LUCIA_ID)
    assertEquals((oldHp - 12).coerceAtLeast(1), lucia.vitalState.currentHp)
    assertEquals(listOf("vết cắn trên vai"), lucia.injuries)
    assertEquals(1, lucia.statusIds.size)
    val statusId = lucia.statusIds.single()
    val status = committed.statuses.getValue(statusId)
    assertEquals("INJURY", status.type)
    assertTrue(status.persistent)
    assertEquals("vết cắn trên vai", status.metadata["description"])
    assertEquals("MILESTONE:CP-L00-01:injury", status.source)
    val reloaded = GameStateCodec.decode(GameStateCodec.encode(committed))
    assertEquals(lucia, reloaded.characters.getValue(LUCIA_ID))
    assertEquals(status, reloaded.statuses.getValue(statusId))
    val replay = StoryMilestoneRuntime.acceptTurn(reloaded, "[]")
    assertEquals(reloaded, replay)
    assertEquals((oldHp - 12).coerceAtLeast(1), replay.characters.getValue(LUCIA_ID).vitalState.currentHp)
  }

  @Test fun cannotInjurePersonOutsidePartyEvenIfCharacterRecordExists() {
    val presentInSaveButNotParty = StoryMilestonePlan.installCurrent(
      LuciaCanon.ensure(base(false)), plan(effects()))
    val before = GameStateCodec.encode(presentInSaveButNotParty)
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.acceptTurn(presentInSaveButNotParty, "[\"injury\"]")
    }
    assertEquals(before, GameStateCodec.encode(presentInSaveButNotParty))
    assertEquals(listOf("injury"), StoryMilestoneRuntime.due(presentInSaveButNotParty).map { it.id })
    assertTrue(presentInSaveButNotParty.characters.getValue(LUCIA_ID).injuries.isEmpty())
  }

  @Test fun schemaRejectsUnknownEffectsAndUntrustedStatChanges() {
    val position = StoryCheckpointCore.Position(0, 1, 20)
    fun reject(raw: JSONArray) {
      assertThrows(Exception::class.java) { StoryMilestonePlan.parse(plan(raw), position) }
    }
    reject(JSONArray().put(JSONObject().put("type", "AWARD_EXP")
      .put("characterId", LUCIA_ID).put("label", "free XP").put("hpLoss", 0)))
    reject(effects(-1))
    reject(effects(201))
    reject(effects(characterId = ""))
    reject(JSONArray().put(JSONObject().put("type", "INJURY")
      .put("characterId", LUCIA_ID).put("label", "injury")
      .put("hpLoss", 12).put("inventory", "free loot")))
  }

  @Test fun injuryCannotImplicitlyKillTargetOrAwardXp() {
    var initial = base(true)
    initial = CharacterStatEngine.setCurrentHp(initial, LUCIA_ID, 3)
    val state = StoryMilestonePlan.installCurrent(initial, plan(effects(100)))
    val settled = StoryMilestoneRuntime.acceptTurn(state, "[\"injury\"]")
    assertEquals(1, settled.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertTrue(settled.characters.getValue(LUCIA_ID).statusIds.isNotEmpty())
    assertTrue(settled.metadata.keys.none { it.startsWith("exp.awarded.") })
  }

  @Test fun emptyInjuryEffectsLeavesCombatMechanicsUntouched() {
    val state = StoryMilestonePlan.installCurrent(base(true), plan(JSONArray()))
    val before = state.characters.getValue(LUCIA_ID).vitalState.currentHp
    val after = StoryMilestoneRuntime.acceptTurn(state, "[\"injury\"]")
    assertEquals(before, after.characters.getValue(LUCIA_ID).vitalState.currentHp)
    assertTrue(after.characters.getValue(LUCIA_ID).injuries.isEmpty())
  }
}
