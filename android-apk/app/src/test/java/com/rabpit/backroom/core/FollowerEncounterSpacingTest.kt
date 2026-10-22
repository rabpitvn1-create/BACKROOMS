package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Regression: no clustered Follower first-meetings, 5% windows stay separate. */
class FollowerEncounterSpacingTest {
  private fun at(levelId: String): GameState {
    val fresh = GameState.initial()
    return fresh.copy(world = fresh.world + mapOf(
      "levelJson" to requireNotNull(LevelRouteRegistry.node(levelId)).asJson().toString()))
  }

  private fun atTurn(state: GameState, turn: Int): GameState =
    state.copy(metadata = state.metadata + ("core.viewTurn" to turn.toString()))

  @Test fun `four independent five percent windows never overlap and preserve exact 500 tickets`() {
    val state = at("4")
    val roster = listOf(CATHERINE_ID, JANET_ID, HUA_TIEU_LAN_ID, NGUYEN_QUOC_NAM_ID)
    val counts = roster.associateWith { 0 }.toMutableMap()
    for (ticket in 0 until 10000) {
      val result = SpawnMechanism.rollSceneSpawns(state, "MOVE") { ticket }
      val found = result.npcEncounters.filter { it.characterId in roster }
      assertTrue("at most one Follower per roll ticket $ticket", found.size <= 1)
      found.forEach { counts[it.characterId] = counts.getValue(it.characterId) + 1 }
    }
    assertEquals(500, counts.getValue(CATHERINE_ID))
    assertEquals(500, counts.getValue(JANET_ID))
    assertEquals(500, counts.getValue(HUA_TIEU_LAN_ID))
    assertEquals(500, counts.getValue(NGUYEN_QUOC_NAM_ID))
    assertTrue(SpawnMechanism.rollSceneSpawns(state, "MOVE") { 4500 }.npcEncounters
      .none { it.characterId == NGUYEN_QUOC_NAM_ID })
    assertTrue(SpawnMechanism.rollSceneSpawns(state, "MOVE") { 5000 }.npcEncounters
      .any { it.characterId == NGUYEN_QUOC_NAM_ID })
  }

  @Test fun `disabling Entity spawns does not cause independent Follower ticket collisions`() {
    // Level 2 has no independently rolled Lục Trầm, so RNG-call count
    // isolates the four Follower windows without changing their behavior.
    val state = at("2")
    val flags = JSONObject().put("entityEncountersAllowed", false)
    val disabled = state.copy(world = state.world + ("flagsJson" to flags.toString()))
    var calls = 0
    val result = SpawnMechanism.rollSpawns(disabled, ActionKind.EXECUTE, "đi khám phá") {
      calls++
      if (calls == 1) 3500 else 4500
    }
    assertNull(result.entityKey)
    assertEquals(1, calls)
    assertEquals(listOf(CATHERINE_ID), result.npcEncounters.map { it.characterId })
  }

  @Test fun `Level zero Lucia and a five percent Follower cannot spawn together`() {
    val state = at("0")
    val collision = SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, "đi khám phá") { 3500 }
    assertEquals(listOf(CATHERINE_ID), collision.npcEncounters.map { it.characterId })
    assertEquals(1, collision.npcEncounters.size)
  }

  @Test fun `all random Followers respect ten finished turn spacing across levels`() {
    val state = atTurn(at("4"), 100)
    val first = SpawnMechanism.applySpawns(state, SpawnResult(null,
      listOf(NpcSpawnTable.NpcEncounter(CATHERINE_ID, "catherine"))))
    assertEquals("100", first.metadata[SpawnMechanism.LAST_FOLLOWER_MEETING_TURN])
    assertTrue(SpawnMechanism.followerCooldownActive(first))
    val cast = listOf(JANET_ID, HUA_TIEU_LAN_ID, NGUYEN_QUOC_NAM_ID, LUC_TRAM_ID)
    val tickets = listOf(4000, 4500, 5000, 0)
    for (turn in 101..110) {
      val cooling = atTurn(first, turn)
      for (ticket in tickets) {
        val result = SpawnMechanism.rollSpawns(cooling, ActionKind.EXECUTE, "đi khám phá") { ticket }
        assertTrue("No NPC during cooldown turn $turn ($ticket)", result.npcEncounters.isEmpty())
      }
    }
    val resumed = atTurn(first, 111)
    assertFalse(SpawnMechanism.followerCooldownActive(resumed))
    assertEquals(listOf(JANET_ID),
      SpawnMechanism.rollSpawns(resumed, ActionKind.EXECUTE, "đi khám phá") { 4000 }
        .npcEncounters.map { it.characterId })
    assertTrue(SpawnMechanism.rollSpawns(atTurn(first, 110), ActionKind.EXECUTE,
      "đi khám phá") { 0 }.npcEncounters.isEmpty())
    val levelZero = atTurn(first.copy(world = first.world + ("levelJson" to
      requireNotNull(LevelRouteRegistry.node("0")).asJson().toString())), 110)
    assertTrue(SpawnMechanism.rollSpawns(levelZero, ActionKind.EXECUTE,
      "đi khám phá") { 0 }.npcEncounters.isEmpty())
    val serialized = GameStateCodec.decode(GameStateCodec.encode(first))
    assertEquals("100", serialized.metadata[SpawnMechanism.LAST_FOLLOWER_MEETING_TURN])
    assertTrue(SpawnMechanism.followerCooldownActive(atTurn(serialized, 108)))
  }

  @Test fun `rejected and discarded encounter does not begin cooldown`() {
    val fresh = atTurn(at("4"), 20)
    val proposal = SpawnResult(null, listOf(NpcSpawnTable.NpcEncounter(JANET_ID, "janet")))
    val pending = SpawnMechanism.prepareSpawns(fresh, proposal)
    assertNull(pending.metadata[SpawnMechanism.LAST_FOLLOWER_MEETING_TURN])
    assertEquals(1, SpawnMechanism.pendingNpcEncounters(pending).size)
    val discarded = SpawnMechanism.discardPendingNpcEncounters(pending)
    assertNull(discarded.metadata[SpawnMechanism.LAST_FOLLOWER_MEETING_TURN])
    assertTrue(SpawnMechanism.rollSpawns(discarded, ActionKind.EXECUTE,
      "đi khám phá") { 4000 }.npcEncounters.any { it.characterId == JANET_ID })
    val committed = SpawnMechanism.commitPendingNpcEncounters(pending)
    assertEquals("20", committed.metadata[SpawnMechanism.LAST_FOLLOWER_MEETING_TURN])
    assertEquals("true", committed.characters.getValue(JANET_ID).metadata["encountered"])
    assertTrue(SpawnMechanism.rollSpawns(atTurn(committed, 25), ActionKind.EXECUTE,
      "đi khám phá") { 5000 }.npcEncounters.isEmpty())
  }

  @Test fun `direct and pending callers cannot commit two first meetings in one action`() {
    val fresh = at("4")
    val duplicate = listOf(
      NpcSpawnTable.NpcEncounter(HUA_TIEU_LAN_ID, "huaTieuLan"),
      NpcSpawnTable.NpcEncounter(NGUYEN_QUOC_NAM_ID, "nguyenQuocNam"))
    val pending = SpawnMechanism.prepareNpcEncounters(fresh, duplicate)
    assertEquals(1, SpawnMechanism.pendingNpcEncounters(pending).size)
    val applied = SpawnMechanism.applySpawns(fresh, SpawnResult(null, duplicate))
    assertEquals("true", applied.characters.getValue(HUA_TIEU_LAN_ID).metadata["encountered"])
    assertNull(applied.characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
  }
}
