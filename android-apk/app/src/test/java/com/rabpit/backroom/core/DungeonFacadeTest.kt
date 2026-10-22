package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DungeonFacadeTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    var checkpoint: String? = null
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { checkpoint = GameCheckpointCodec.encode(state, presentation) }
    override fun loadCheckpoint() = checkpoint?.let(GameCheckpointCodec::decode)
  }
  private var now = 1_790_000_000_000L
  private fun core(repo: MemorySave): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger).also { facade ->
      GameCoreFacade::class.java.getDeclaredField("dungeonNow").apply { isAccessible = true }
        .set(facade, { now })
    }
  }
  private fun repo(): MemorySave {
    val initial = CoreClientProjection.initialize(GameState.initial())
    return MemorySave(WorldGraphRuntime.initialize(initial.copy(
      party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID))), 41L))
  }

  @Test fun restartSettlesOfflineMinutesAndCheckpointRestoreDoesNotAddRewardsTwice() {
    val repo = repo();var core = core(repo)
    core.dungeonAction(0, IRIS_ID, -1, false)
    val original = repo.state
    val saved = JSONObject(core.saveManualCheckpoint(core.currentClientState("{}")))
    assertTrue(saved.getBoolean("ok"))
    now += 120000
    core = core(repo)
    core.currentClientState("{}")
    val accrued = repo.state.characters.getValue(IRIS_ID)
    assertEquals(CharacterProgression(0, 8), accrued.progression)
    assertEquals(original.turn, repo.state.turn)
    repeat(2) {
      assertTrue(JSONObject(core.loadManualCheckpoint()).getBoolean("ok"))
      assertEquals(accrued, repo.state.characters.getValue(IRIS_ID))
      assertEquals(2, repo.state.dungeon.runs.single().creditedMinutes)
    }
  }

  @Test fun aPreviouslyRecruitedFollowerCanReturnAndRejoinWithoutInventingAMeeting() {
    val repo = repo();val core = core(repo)
    val view = JSONObject(core.dungeonAction(0, IRIS_ID, -1, false))
    val id = view.getJSONArray("slots").getJSONObject(0).getLong("runId")
    now += 60000
    core.dungeonAction(0, "", id, true)
    val client = core.currentClientState("{}")
    assertTrue(MiniMapScenario.objects(JSONObject(client).getJSONArray("encounteredCharacters"))
      .any { it.getString("id") == IRIS_ID && it.getBoolean("present") })
    assertTrue(JSONObject(core.recruitFollower(client, IRIS_ID)).getBoolean("handled"))
    assertTrue(IRIS_ID in repo.state.party.memberIds)
    assertNotEquals("true", repo.state.characters.getValue(IRIS_ID).metadata["encountered"])
  }

  @Test fun clientCannotForgeDungeonAndCombatBlocksAssignmentButNotReadOnlyStatus() {
    val repo = repo();val core = core(repo)
    assertFalse(JSONObject(core.currentClientState("{\"dungeon\":{\"exp\":999999}}" )).has("dungeon"))
    assertTrue(repo.state.dungeon.runs.isEmpty())
    repo.state = CombatRuntime.start(repo.state, "hound")
    val before = repo.state
    assertThrows(IllegalArgumentException::class.java) { core.dungeonAction(0, IRIS_ID, -1, false) }
    assertEquals(before, repo.state)
    assertEquals(5, JSONObject(core.dungeonView()).getJSONArray("slots").length())
    assertEquals(before, repo.state)
  }
}
