package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameCoreManualCheckpointTest {
  private class MemorySave(var state: GameState = CoreClientProjection.initialize(GameState.initial())) : SaveRepository {
    var slot: String? = null
    override fun save(state: GameState) { this.state = state }
    override fun load(): GameState = state
    override fun exists(): Boolean = true
    override fun clear() { state = GameState.initial(); slot = null }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
      slot = GameCheckpointCodec.encode(state, presentation)
    }
    override fun loadCheckpoint(): GameCheckpoint? = slot?.let(GameCheckpointCodec::decode)
  }

  private fun facade(repository: SaveRepository): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repository, NoOpGamePipelineLogger)
  }

  @Test fun manualLoadReallyRewindsCoreInventoryHpAndTurnAlongWithLog() {
    val repo = MemorySave()
    val core = CharacterStatEngine.setCurrentHp(InventoryEngine.grantDrop(repo.state, BANDAGE_ID)!!, KAI_ID, 25)
    repo.state = CoreClientProjection.advance(core)
    val facade = facade(repo)
    val view = JSONObject().put("turn", 2)
      .put("log", JSONArray().put(JSONObject().put("role", "gm").put("text", "Điểm lưu")))
      .put("inventory", JSONArray().put(JSONObject().put("id", "hacked")))
    assertTrue(JSONObject(facade.saveManualCheckpoint(view.toString())).getBoolean("ok"))
    val later = CharacterStatEngine.setCurrentHp(repo.state, KAI_ID, 8)
    repo.state = CoreClientProjection.advance(later.copy(inventories =
      later.inventories + (KAI_ID to InventoryState(KAI_ID))))
    assertEquals(3, CoreClientProjection.turnIndex(repo.state))
    assertFalse(repo.state.inventories.getValue(KAI_ID).items.containsKey(BANDAGE_ID))

    val loaded = JSONObject(facade.loadManualCheckpoint())
    assertTrue(loaded.toString(), loaded.getBoolean("ok"))
    assertEquals(2, CoreClientProjection.turnIndex(repo.state))
    assertEquals(25, repo.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(1, repo.state.inventories.getValue(KAI_ID).items.getValue(BANDAGE_ID).quantity)
    val restoredView = loaded.getJSONObject("state")
    assertEquals(2, restoredView.getInt("turn"))
    assertEquals("Điểm lưu", restoredView.getJSONArray("log").getJSONObject(0).getString("text"))
    assertEquals(BANDAGE_ID, restoredView.getJSONArray("inventory").getJSONObject(0).getString("id"))
  }

  @Test fun staleOrInProgressSaveIsRejectedAndDoesNotOverwriteSlot() {
    val repo = MemorySave()
    val facade = facade(repo)
    val good = JSONObject().put("turn", 1).put("log", JSONArray())
    assertTrue(JSONObject(facade.saveManualCheckpoint(good.toString())).getBoolean("ok"))
    val saved = repo.slot
    val stale = JSONObject(facade.saveManualCheckpoint(JSONObject().put("turn", 4).toString()))
    assertFalse(stale.getBoolean("ok"))
    assertEquals("stale_client_state", stale.getString("error"))
    assertEquals(saved, repo.slot)

    repo.state = TurnCoordinator.createPending(repo.state, "TURN_1", "Đang xử lý").state
    assertEquals("turn_in_progress", JSONObject(facade.saveManualCheckpoint(good.toString())).getString("error"))
    assertEquals("turn_in_progress", JSONObject(facade.loadManualCheckpoint()).getString("error"))
    assertEquals(saved, repo.slot)
  }

  @Test fun noOrBrokenCheckpointCannotOverwriteLiveGame() {
    val repo = MemorySave()
    val facade = facade(repo)
    val before = repo.state
    val missing = JSONObject(facade.loadManualCheckpoint())
    assertFalse(missing.getBoolean("ok"))
    assertEquals("checkpoint_missing", missing.getString("error"))
    assertEquals(before, repo.state)
    repo.slot = "{\"saveVersion\":0}"
    assertThrows(Exception::class.java) { facade.loadManualCheckpoint() }
    assertEquals(before, repo.state)
  }

  @Test fun clearRemovesCurrentRunAndManualCheckpointTogether() {
    val repo = MemorySave()
    val facade = facade(repo)
    assertTrue(JSONObject(facade.saveManualCheckpoint("{\"turn\":1}")).getBoolean("ok"))
    facade.clear()
    assertNull(repo.slot)
  }
  @Test fun checkpointRestoresActiveMultiWaveEncounterAndFinalizedPokerDice() {
    val repo = MemorySave()
    val initial = CombatRuntime.startWaves(repo.state, listOf("hound", "clump", "hound"))
    val firstDefeat = CombatRuntime.resolve(
      initial.copy(metadata = initial.metadata + ("combat.entityHp" to "0")), "SEARCH", "quan sát"
    )
    assertFalse(firstDefeat.entityDestroyed)
    val active = CombatRuntime.active(firstDefeat.state)!!
    val dice = PokerDiceCore.finish(
      PokerDiceCore.prepare(firstDefeat.state, PokerDiceCore.DIRECT_COMBAT_ACTION, active.encounterId)
    )
    repo.state = dice
    val facade = facade(repo)
    val turn = CoreClientProjection.turnIndex(repo.state)
    val response = JSONObject(facade.saveManualCheckpoint(JSONObject().put("turn", turn).toString()))
    assertTrue(response.toString(), response.getBoolean("ok"))

    val expectedHp = PartyCombatHealth.currentHp(repo.state)
    val storedIndex = repo.state.metadata["combat.waveIndex"]
    val originalEncounter = active.encounterId
    repo.state = PokerDiceCore.clearDice(CombatRuntime.clear(repo.state))
    assertNull(CombatRuntime.active(repo.state))
    assertFalse(PokerDiceCore.isFinalizedFor(repo.state, PokerDiceCore.DIRECT_COMBAT_ACTION, originalEncounter))

    val restored = JSONObject(facade.loadManualCheckpoint())
    assertTrue(restored.toString(), restored.getBoolean("ok"))
    val combat = CombatRuntime.active(repo.state)!!
    assertEquals(originalEncounter, combat.encounterId)
    assertEquals("clump", combat.entityKey)
    assertEquals(storedIndex, repo.state.metadata["combat.waveIndex"])
    assertEquals(expectedHp, PartyCombatHealth.currentHp(repo.state))
    assertTrue(PokerDiceCore.isFinalizedFor(repo.state, PokerDiceCore.DIRECT_COMBAT_ACTION, combat.encounterId))
    val projected = restored.getJSONObject("state").getJSONObject("combat")
    assertEquals(2, projected.getInt("waveNumber"))
    assertEquals(3, projected.getInt("waveCount"))
    assertEquals(2, projected.getInt("remainingEntityCount"))
    assertTrue(projected.getJSONObject("diceState").getBoolean("finalized"))
  }

  @Test fun duplicateCombatSubmissionCannotAdvanceTimeOrAwardLootTwice() {
    val repo = MemorySave()
    val started = CombatRuntime.startWaves(repo.state, listOf("hound", "clump"))
    val marked = started.copy(metadata = started.metadata + ("combat.entityHp" to "0"))
    val encounterId = CombatRuntime.active(marked)!!.encounterId
    repo.state = PokerDiceCore.finish(PokerDiceCore.prepare(marked, "quan sát", encounterId))
    val facade = facade(repo)
    val request = JSONObject().put("turn", CoreClientProjection.turnIndex(repo.state)).toString()

    val first = JSONObject(facade.processCombat(request, "SEARCH", "quan sát"))
    assertTrue(first.toString(), first.getBoolean("handled"))
    assertEquals("clump", CombatRuntime.active(repo.state)!!.entityKey)
    val afterFirst = GameStateCodec.encode(repo.state)
    val lootCount = repo.state.metadata.keys.count { it.startsWith("loot.awarded.") }
    assertEquals(1, lootCount)

    val second = JSONObject(facade.processCombat(request, "SEARCH", "quan sát"))
    assertTrue(second.toString(), second.getBoolean("handled"))
    assertEquals("combat_dice_required", second.optString("reason"))
    assertEquals(afterFirst, GameStateCodec.encode(repo.state))
    assertEquals(lootCount, repo.state.metadata.keys.count { it.startsWith("loot.awarded.") })
  }

}
