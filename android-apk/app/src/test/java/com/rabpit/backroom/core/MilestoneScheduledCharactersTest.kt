package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MilestoneScheduledCharactersTest {
  private fun encounter(id: String, turn: Int, kind: String, characterId: String) = JSONObject()
    .put("id", id).put("turn", turn).put("kind", kind).put("characterId", characterId)
    .put("action", "Nhân vật bước ra").put("event", "Nhân vật gặp Cao Minh").put("outcome", "Đã gặp, chưa gia nhập")

  private fun raw(events: List<JSONObject>, checkpoint: String = "CP-L00-01"): String {
    val quota = TestCheckpointQuota.events(JSONArray().put(JSONObject()
      .put("id", "boss").put("turn", 12).put("kind", "FINAL_ENTITY").put("entityKey", "hound")
      .put("action", "Boss xuất hiện").put("event", "Boss chặn lối").put("outcome", "Chờ Battle Start")
      .put("hpMultiplier", 9).put("expMultiplier", 9)))
    val values = (0 until quota.length()).map { quota.getJSONObject(it) } + events
    return JSONObject().put("schemaVersion", StoryMilestonePlan.SCHEMA_VERSION).put("checkpointId", checkpoint).put("storyArc", TestCheckpointQuota.storyArc())
      .put("events", JSONArray(values.sortedBy { it.getInt("turn") }))
      .put("completion", JSONObject().put("type", "NEXT_CHECKPOINT").put("outcome", "Qua CP")).toString()
  }

  private fun initial(events: List<JSONObject>): GameState = StoryMilestonePlan.installCurrent(
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial())), raw(events))

  private fun reach(state: GameState, turn: Int): GameState {
    var result = state
    while (CoreClientProjection.turnIndex(result) < turn)
      result = CoreClientProjection.advance(TestMilestoneTurn.dispatch(result))
    return result
  }

  @Test fun luciaAppearsOnTurnThreeRegardlessOfFailedRandomWithoutJoiningParty() {
    var state = initial(listOf(encounter("lucia3", 3, "FOLLOWER", LUCIA_ID)))
    state = state.copy(world = state.world + ("flagsJson" to JSONObject(state.world.getValue("flagsJson"))
      .put("lastRolls", JSONObject().put("turn", 3).put("luciaEncounter", JSONObject().put("success", false)))
      .toString()))
    assertFalse(state.characters.getValue(LUCIA_ID).metadata.containsKey("encountered"))
    state = reach(state, 3)
    val due = JSONArray(StoryMilestoneRuntime.dueJson(state)).getJSONObject(0)
    assertEquals("lucia", due.getString("characterId"))
    assertEquals(LuciaCanon.NAME, due.getString("characterName"))
    val committed = TestMilestoneTurn.dispatch(state)
    assertEquals("true", committed.characters.getValue(LUCIA_ID).metadata["encountered"])
    assertEquals("3", committed.characters.getValue(LUCIA_ID).metadata["encounterTurn"])
    assertEquals(listOf(CAO_MINH_ID), committed.party.memberIds)
    assertEquals(committed, TestMilestoneTurn.dispatch(committed))
    val restored = GameStateCodec.decode(GameStateCodec.encode(CoreClientProjection.advance(committed)))
    StoryMilestoneRuntime.validateStoredState(restored)
    assertEquals(committed.characters[LUCIA_ID], restored.characters[LUCIA_ID])
    assertTrue(StoryMilestoneRuntime.due(restored).isEmpty())
  }

  @Test fun allRegisteredFollowersUseOneDispatcherAndSameTurnOrderIsPreserved() {
    val ids = listOf(AN_NHIEN_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID)
    val before = reach(initial(ids.mapIndexed { index, id -> encounter("meet$index", 3, "FOLLOWER", id) }), 3)
    assertEquals(ids, StoryMilestoneRuntime.due(before).map { it.characterId })
    val after = TestMilestoneTurn.dispatch(before)
    ids.forEach { assertEquals("3", after.characters.getValue(it).metadata["encounterTurn"]) }
    assertEquals(listOf(CAO_MINH_ID), after.party.memberIds)
    val records = JSONObject(after.world.getValue("story.milestone.eventRecords"))
    assertEquals(ids.size, records.length())
    assertEquals(ids.size, ids.map { after.characters.getValue(it).metadata["encounterId"] }.toSet().size)
    val checkpoint = GameCheckpointCodec.decode(GameCheckpointCodec.encode(CoreClientProjection.advance(after), JSONObject()))
    assertEquals(ids.size, checkpoint.presentation.getJSONArray("encounteredCharacters").length())
  }

  @Test fun lucTramUsesTheSameScheduledDispatcherAtHerRegistryLevel() {
    val position = StoryCheckpointCore.Position(4, 1, 40)
    val base = CoreClientProjection.initialize(GameState.initial())
    val state = base.copy(world = base.world + mapOf(
      "story.checkpoint" to JSONObject().put("level", 4).put("index", 1).put("total", 40).toString(),
      "levelJson" to LevelRouteRegistry.node("4")!!.asJson().toString()))
    val accepted = StoryMilestonePlan.installCurrent(state, raw(listOf(encounter("lucTram3", 3, "FOLLOWER", LUC_TRAM_ID)), position.id))
    val committed = TestMilestoneTurn.dispatch(reach(accepted, 3))
    assertEquals("3", committed.characters.getValue(LUC_TRAM_ID).metadata["encounterTurn"])
    assertEquals(listOf(CAO_MINH_ID), committed.party.memberIds)
  }

  @Test fun npcAndFollowerEventsTogetherAreBothRecordedWithoutPartyMutation() {
    val before = reach(initial(listOf(encounter("npc", 3, "NPC", IRIS_ID), encounter("follower", 3, "FOLLOWER", LUCIA_ID))), 3)
    val after = TestMilestoneTurn.dispatch(before)
    assertEquals("NPC", after.characters.getValue(IRIS_ID).metadata["encounterKind"])
    assertEquals("FOLLOWER", after.characters.getValue(LUCIA_ID).metadata["encounterKind"])
    assertEquals(2, JSONObject(after.world.getValue("story.milestone.eventRecords")).length())
    assertEquals(before.party, after.party)
  }

  @Test fun noScheduledCharacterEventCannotManufactureACharacterEncounter() {
    val state = TestMilestoneTurn.dispatch(initial(emptyList()))
    assertTrue(state.characters.values.none { it.metadata["encountered"] == "true" })
    assertEquals(listOf(CAO_MINH_ID), state.party.memberIds)
  }

  @Test fun unknownIdsMissingCombatFlagAndImpossiblePlansAreRejectedBeforeInstallation() {
    val state = StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))
    assertThrows(Exception::class.java) { StoryMilestonePlan.installCurrent(state, raw(listOf(encounter("bad", 3, "FOLLOWER", "invented")))) }
    val dead = state.copy(characters = state.characters + (LUCIA_ID to state.characters.getValue(LUCIA_ID).copy(presence = CharacterPresence.DEAD)))
    assertThrows(Exception::class.java) { StoryMilestonePlan.installCurrent(dead, raw(listOf(encounter("dead", 3, "FOLLOWER", LUCIA_ID)))) }
    val missing = JSONObject(raw(emptyList()))
    missing.getJSONArray("events").getJSONObject(0).put("combatRequired", false)
    assertThrows(Exception::class.java) { StoryMilestonePlan.installCurrent(state, missing.toString()) }
  }

  @Test fun checkpointHistoryKeepsScheduledCharacterSourcesAfterBossDefeat() {
    val state = TestCheckpointQuota.reachBoss(initial(listOf(encounter("lucia3", 3, "FOLLOWER", LUCIA_ID))))
    val waiting = CoreClientProjection.advance(TestMilestoneTurn.dispatch(state))
    val id = CombatRuntime.pending(waiting)!!.encounterId
    val active = CombatRuntime.confirmEncounter(waiting, id)
    val prepared = StoryMilestonePlan.prepareNextDuringCombat(active, raw(emptyList(), "CP-L00-02"))
    val ended = CombatRuntime.resolve(prepared.copy(metadata = prepared.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát")
    val next = StoryMilestoneRuntime.afterCombat(ended.state, id, true, false)
    StoryMilestoneRuntime.validateStoredState(GameStateCodec.decode(GameStateCodec.encode(next)))
    assertEquals("true", next.characters.getValue(LUCIA_ID).metadata["encountered"])
    assertTrue(StoryMilestoneRuntime.continuityContext(next).contains("lucia"))
  }
}
