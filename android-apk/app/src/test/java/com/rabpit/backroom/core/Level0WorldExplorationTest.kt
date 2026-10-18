package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class Level0WorldExplorationTest {
  private fun world(seed: Long = 41L) = WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), seed)
  private fun p(s: GameState) = MiniMapRuntime.progress(s)
  private fun command(s: GameState, target: String): String = MiniMapScenario.objects(MiniMapRuntime.actions(s))
    .first { it.getString("targetId") == target }.let { JSONObject(it.toString()).apply { remove("label") }.toString() }
  private fun move(s: GameState, target: String): GameState {
    val staged = MiniMapRuntime.stage(s, JSONObject(command(s, target)))
    return s.copy(world = s.world + (MiniMapRuntime.STATE to staged.progress.toString()))
  }
  private class MemorySave(var state: GameState, var present: Boolean = true): SaveRepository {
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)); present = true }
    override fun load() = state
    override fun exists() = present
    override fun clear() { present = false }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) = Unit
    override fun loadCheckpoint(): GameCheckpoint? = null
  }
  private class CountingRng: SecureRandom() {
    var calls = 0
    override fun nextInt(bound: Int): Int { calls++; return bound - 1 }
  }
  private fun facade(repo: MemorySave, rng: CountingRng): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo, NoOpGamePipelineLogger).also {
      GameCoreFacade::class.java.getDeclaredField("spawnRng").apply { isAccessible = true }.set(it, rng)
    }
  }

  @Test fun seededGraphHasAllEnvironmentsAndSurvivesEverySinglePassageFailure() {
    val layouts = mutableSetOf<String>()
    for (seed in 0L until 128L) {
      val graph = Level0WorldDefinition.generate(seed)
      assertEquals(graph.toString(), Level0WorldDefinition.generate(seed).toString())
      WorldGraphRuntime.validate(graph) // BFS plus independent removal of every edge.
      assertEquals(37, graph.getJSONArray("rooms").length())
      layouts.add(graph.getJSONArray("rooms").toString())
    }
    assertTrue(layouts.size > 100)
    val bad = Level0WorldDefinition.generate(0L)
    bad.getJSONArray("rooms").getJSONObject(0).getJSONArray("adjacentRoomIds").put("missing")
    assertThrows(IllegalArgumentException::class.java) { WorldGraphRuntime.validate(bad) }
  }

  @Test fun backtrackingPreservesObservationsMarkersAndExactSnapshotIdentity() {
    var state = world()
    val start = p(state).getString("roomId")
    state = move(move(state, "observe"), "mark")
    val evidence = p(state).getJSONArray("records").toString()
    val target = MiniMapRuntime.actions(state).getJSONObject(0).getString("targetId")
    state = move(state, target)
    val destination = p(state).getString("roomId")
    state = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(destination, p(state).getString("roomId"))
    val graph = p(state).getJSONObject("worldGraph")
    val e = MiniMapScenario.objects(graph.getJSONArray("rooms")).first { it.getString("id") == destination }.getString("environmentId")
    assertEquals(e, MiniMapRuntime.publicView(state).getJSONObject("environment").getString("id"))
    state = move(state, "room:$start")
    assertEquals(evidence, p(state).getJSONArray("records").toString())
    assertTrue(p(state).getJSONObject("markers").getBoolean(start))
    assertEquals("yellow_rot", MiniMapRuntime.publicView(state).getJSONObject("environment").getString("id"))
  }

  @Test fun mapAndPlannerExposeOnlyKnownFrontierWithoutSeedOrUnvisitedIdentities() {
    val state = world(); val view = MiniMapRuntime.publicView(state)
    val map = view.getJSONObject("map"); val known = p(state).getString("roomId")
    assertTrue(map.getJSONArray("rooms").length() < 37)
    MiniMapScenario.objects(map.getJSONArray("rooms")).filter { it.getString("id") != known }.forEach {
      assertEquals("Chưa khám phá", it.getString("name"))
      assertEquals(0, it.getJSONArray("adjacentRoomIds").length())
      assertFalse(it.has("environmentId"))
    }
    val repo = MemorySave(state); val core = facade(repo, CountingRng())
    val planner = core.scenePlayerActionContext()
    assertFalse(planner.contains("worldGraph")); assertFalse(planner.contains("seed"))
    assertFalse(planner.contains("phong_giao_thoa")); assertEquals("", view.getString("temporaryInference"))
    assertFalse(MiniMapScenario.objects(view.getJSONArray("availableActions")).any { it.getString("targetId") == "next" })
  }

  @Test fun pendingLoadRetryAndCommitReserveRngOnceAndNeverTrustNarrationForState() {
    val repo = MemorySave(world()); val rng = CountingRng(); var core = facade(repo, rng)
    val start = p(repo.state).getString("roomId")
    val cmd = command(repo.state, MiniMapRuntime.actions(repo.state).getJSONObject(0).getString("targetId"))
    val actionId = JSONObject(cmd).getString("actionId")
    core.beginSceneAction(cmd); val calls = rng.calls
    assertTrue(calls > 0); assertEquals(start, p(repo.state).getString("roomId"))
    val reserved = GameStateCodec.encode(repo.state)
    repo.state = GameStateCodec.decode(reserved); core = facade(repo, rng)
    core.beginSceneAction(cmd); assertEquals(calls, rng.calls)
    assertEquals(JSONObject(cmd).toString(), JSONObject(core.retrySceneAction()).toString())
    core.commitSceneAction(actionId, "Đã tới căn phòng.", "{}")
    val committed = GameStateCodec.encode(repo.state)
    core.commitSceneAction(actionId, "GM muốn mở Level 1 và trao vật phẩm.", "{}")
    assertEquals(committed, GameStateCodec.encode(repo.state)); assertEquals(calls, rng.calls)
    assertEquals("0", MiniMapRuntime.node(repo.state).id)
    assertNotEquals(start, p(repo.state).getString("roomId"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapRuntime.stage(repo.state, JSONObject(cmd)) }
  }

  @Test fun observationsAndPersonalMapMarkersDoNotRollPhysicalEncounters() {
    val repo = MemorySave(world()); val rng = CountingRng(); val core = facade(repo, rng)
    for (target in listOf("observe", "mark", "observe")) {
      val cmd = command(repo.state, target); core.beginSceneAction(cmd)
      core.commitSceneAction(JSONObject(cmd).getString("actionId"), "Cao Minh ghi nhận hiện trường.", "{}")
    }
    assertEquals(0, rng.calls)
    assertEquals(1, p(repo.state).getJSONArray("records").length())
    assertEquals(1, p(repo.state).getJSONObject("markers").length())
    assertNull(CombatRuntime.pending(repo.state))
  }

  @Test fun forgedTargetsAndExtraFieldsAreRejectedBeforeAnyStateChange() {
    val state = world(); val valid = command(state, "observe")
    for (bad in listOf(JSONObject(valid).put("targetId", "room:secret"), JSONObject(valid).put("loot", "sword"))) {
      val before = GameStateCodec.encode(state)
      assertThrows(Exception::class.java) { MiniMapRuntime.stage(state, bad) }
      assertEquals(before, GameStateCodec.encode(state))
    }
  }

  @Test fun productionNewRunUsesWorldWithoutProviderAndRejectsScenarioReplacement() {
    val repo = MemorySave(GameState.initial(), false); val core = facade(repo, CountingRng())
    val view = JSONObject(core.currentClientState("{}"))
    assertEquals("world-graph-v2", view.getJSONObject("scene").getString("runtime"))
    assertFalse(view.getJSONObject("scene").getBoolean("generationNeeded"))
    val request = JSONObject(core.sceneGenerationRequest())
    assertEquals("ready", request.getString("kind"))
    assertThrows(IllegalStateException::class.java) { core.acceptSceneGeneration(request.getString("token"), "{}") }
  }

  @Test fun independentGraphWalkerCanVisitEveryRoomAndReturnToEntry() {
    var state = world(7)
    val graph = p(state).getJSONObject("worldGraph")
    val adjacency = MiniMapScenario.objects(graph.getJSONArray("rooms")).associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    fun go(destination: String) {
      val start = p(state).getString("roomId")
      val paths = mutableMapOf(start to emptyList<String>())
      val queue = java.util.ArrayDeque<String>(); queue.add(start)
      while (destination !in paths && queue.isNotEmpty()) {
        val from = queue.removeFirst()
        adjacency.getValue(from).forEach { to -> if (to !in paths) {
          paths[to] = paths.getValue(from) + to; queue.add(to)
        } }
      }
      for (step in paths.getValue(destination)) state = move(state, "room:$step")
    }
    adjacency.keys.forEach { go(it) }
    go(graph.getString("entryRoomId"))
    assertEquals(37, p(state).getJSONArray("visitedRooms").length())
    assertEquals(graph.getString("entryRoomId"), p(state).getString("roomId"))
    assertFalse(MiniMapRuntime.complete(state)) // Exhaustive discovery cannot invent an exit.
  }

  @Test fun randomExplorerCannotInventExitOrEraseEvidence() {
    var state = move(move(world(11), "observe"), "mark")
    val rng = java.util.Random(99)
    repeat(256) {
      val moves = MiniMapScenario.objects(MiniMapRuntime.actions(state)).filter { it.getString("kind") == "MOVE" }
      state = move(state, moves[rng.nextInt(moves.size)].getString("targetId"))
      assertFalse(MiniMapRuntime.complete(state))
    }
    assertEquals(1, p(state).getJSONArray("records").length())
    assertEquals(1, p(state).getJSONObject("markers").length())
    val checkpoint = GameCheckpointCodec.decode(GameCheckpointCodec.encode(state, JSONObject()))
    assertEquals(p(state).toString(), p(checkpoint.core).toString())
  }

  @Test fun encounterReservationAndNonPartyNpcPresenceStayLocalWithoutDeletingWorld() {
    var state = move(world(), "observe")
    val progress = p(state).toString()
    val pending = CombatRuntime.prepareEncounter(state, "hound", "world-test-encounter")
    assertEquals(progress, p(pending).toString())
    assertEquals(0, MiniMapRuntime.actions(pending).length())
    assertEquals(progress, p(GameStateCodec.decode(GameStateCodec.encode(pending))).toString())
    val current = p(state).getString("roomId")
    val character = state.characters.values.first { it.id != CAO_MINH_ID }
    state = state.copy(characters = state.characters + (character.id to character.copy(
      presence = CharacterPresence.ACTIVE, metadata = character.metadata + mapOf("encountered" to "true", "world.roomId" to current, "world.nodeId" to "0"))))
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(state)).any { it.getString("kind") == "TALK" })
    state = move(state, MiniMapRuntime.actions(state).getJSONObject(0).getString("targetId"))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(state)).any { it.getString("kind") == "TALK" })
    val projected = CoreClientProjection.project(JSONObject(), state).getJSONArray("encounteredCharacters")
    assertFalse(MiniMapScenario.objects(projected).first { it.getString("id") == character.id }.getBoolean("present"))
    val repo = MemorySave(state); val core = facade(repo, CountingRng())
    assertFalse(JSONObject(core.recruitFollower(core.currentClientState("{}"), character.id)).getBoolean("handled"))
    assertEquals(progress.let { JSONObject(it).getJSONArray("records").toString() }, p(state).getJSONArray("records").toString())
  }

  @Test fun corruptedWorldOrPendingGraphCannotLoadAsAnAcceptedWorld() {
    val root = JSONObject(GameStateCodec.encode(world()))
    val worldJson = root.getJSONObject("world")
    val progress = JSONObject(worldJson.getString(MiniMapRuntime.STATE))
    progress.put("roomId", "missing")
    worldJson.put(MiniMapRuntime.STATE, progress.toString())
    assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(root) }
    val repo = MemorySave(world()); val core = facade(repo, CountingRng())
    core.beginSceneAction(command(repo.state, "observe"))
    val raw = JSONObject(GameStateCodec.encode(repo.state))
    val savedWorld = raw.getJSONObject("world")
    val pending = JSONObject(savedWorld.getString(MiniMapRuntime.PENDING))
    pending.getJSONObject("progress").getJSONObject("worldGraph").put("seed", "999")
    savedWorld.put(MiniMapRuntime.PENDING, pending.toString())
    assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(raw) }
  }
}
