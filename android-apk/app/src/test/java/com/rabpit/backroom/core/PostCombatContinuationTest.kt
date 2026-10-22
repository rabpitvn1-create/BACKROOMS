package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class PostCombatContinuationTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) = Unit
    override fun loadCheckpoint(): GameCheckpoint? = null
  }
  private class Rng(private val encounter: Boolean) : SecureRandom() {
    var calls = 0
    override fun nextInt(bound: Int): Int {
      calls++
      return if (encounter && calls == 1) 0 else bound - 1
    }
  }
  private fun fixture(encounter: Boolean = false, waves: Boolean = false): Triple<MemorySave, GameCoreFacade, Rng> {
    var state = WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), 41L)
    state = CombatRuntime.startWaves(state, if (waves) listOf("hound", "clump") else listOf("hound"))
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "0"))
    val id = CombatRuntime.active(state)!!.encounterId
    state = PokerDiceCore.finish(PokerDiceCore.prepare(state, "quan sát", id))
    val repo = MemorySave(state)
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(repo, NoOpGamePipelineLogger)
    val rng = Rng(encounter)
    GameCoreFacade::class.java.getDeclaredField("spawnRng").apply { isAccessible = true }.set(core, rng)
    return Triple(repo, core, rng)
  }

  @Test fun finalVictoryReservesOneAtRestTurnAndRetryNeverRerollsOrRepeatsRewards() {
    val (repo, core, rng) = fixture()
    val initial = repo.state
    val result = JSONObject(core.processCombat("{}", "SEARCH", "quan sát"))
    assertEquals("combat_entity_destroyed", result.getString("reason"))
    assertTrue(result.getJSONObject("state").getBoolean("postCombatContinuation"))
    val pending = JSONObject(repo.state.world.getValue(MiniMapRuntime.PENDING))
    assertTrue(pending.getBoolean("automaticContinuation"))
    assertTrue(JSONObject(core.sceneNarrationContext()).getBoolean("automaticContinuation"))
    val saved = repo.state
    val rolls = rng.calls
    assertTrue(rolls > 0)
    val command = core.retrySceneAction()
    core.beginSceneAction(command)
    assertEquals(saved, repo.state)
    assertEquals(rolls, rng.calls)
    val view = JSONObject(core.commitSceneAction(JSONObject(command).getString("actionId"),
      "Mọi thứ im ắng.\n1. Quan sát\n2. Chờ\n3. Lắng nghe\n4. Kiểm tra", result.getJSONObject("state").toString()))
    assertFalse(view.has("postCombatContinuation"))
    assertEquals(initial.time.elapsedSubjectiveMinutes + 2, repo.state.time.elapsedSubjectiveMinutes)
    assertEquals(MiniMapRuntime.progress(initial).getString("roomId"), MiniMapRuntime.progress(repo.state).getString("roomId"))
    assertEquals(MiniMapRuntime.progress(initial).getInt("version") + 1, MiniMapRuntime.progress(repo.state).getInt("version"))
    assertEquals(1, repo.state.metadata.keys.count { it.startsWith("exp.awarded.") })
    assertEquals(1, repo.state.metadata.keys.count { it.startsWith("loot.awarded.") })
    val log = MiniMapScenario.objects(view.getJSONArray("log"))
    assertFalse(log.any { it.optString("role") == "player" && it.optString("text").contains("free", true) })
    assertEquals(1, log.count { it.optString("text").startsWith("Mọi thứ im ắng") })
    val projected = JSONObject(core.currentClientState(view.toString()))
    assertEquals(view.getJSONArray("log").toString(), projected.getJSONArray("log").toString())
    val committed = repo.state
    core.commitSceneAction(JSONObject(command).getString("actionId"), "Mọi thứ im ắng.", view.toString())
    assertEquals(committed, repo.state)
    assertEquals(rolls, rng.calls)
    assertFalse(JSONObject(core.processCombat(view.toString(), "SEARCH", "quan sát")).getBoolean("handled"))
    assertEquals(committed, repo.state)
  }

  @Test fun aNewEntityIsReservedOnceAndCanStartAfterNarration() {
    val (repo, core, rng) = fixture(encounter = true)
    val result = JSONObject(core.processCombat("{}", "SEARCH", "quan sát"))
    val encounter = CombatRuntime.pending(repo.state)!!
    assertNull(CombatRuntime.active(repo.state))
    val rolls = rng.calls
    val command = JSONObject(core.retrySceneAction())
    val view = core.commitSceneAction(command.getString("actionId"), "Một thực thể xuất hiện.", result.getJSONObject("state").toString())
    val confirmed = JSONObject(core.confirmEncounter(view, encounter.encounterId))
    assertTrue(confirmed.getBoolean("handled"))
    assertEquals(encounter.encounterId, CombatRuntime.active(repo.state)!!.encounterId)
    assertEquals(rolls, rng.calls)
    assertEquals(1, MiniMapScenario.objects(confirmed.getJSONObject("state").getJSONArray("log"))
      .count { it.optString("text") == "Một thực thể xuất hiện." })
  }

  @Test fun anIntermediateWaveContinuesCombatWithoutAnExplorationTurn() {
    val (repo, core, rng) = fixture(waves = true)
    val result = JSONObject(core.processCombat("{}", "SEARCH", "quan sát"))
    assertEquals("combat_resolved", result.getString("reason"))
    assertFalse(result.getJSONObject("state").has("postCombatContinuation"))
    assertEquals("clump", CombatRuntime.active(repo.state)!!.entityKey)
    assertFalse(MiniMapRuntime.PENDING in repo.state.world)
    assertEquals(0, rng.calls)
  }
}
