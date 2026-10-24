package com.rabpit.backroom.core

import com.rabpit.backroom.core.canon.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LevelTravelEngineTest {
  private fun assets(name: String): String {
    val dirs = listOf("src/main/assets/knowledge", "app/src/main/assets/knowledge", "android-apk/app/src/main/assets/knowledge")
    return dirs.map { File(it, name) }.first { it.isFile }.readText()
  }
  private fun policyJson() = JSONObject(assets("level_travel_policy.json"))
  private fun policy() = LevelTravelPolicy(policyJson())
  private class RandomSequence(vararg values: Double) : () -> Double {
    private val sequence = values.toList()
    var calls = 0
    override fun invoke(): Double = sequence[calls++]
  }
  private fun streak(state: GameState) = LevelTravelEngine.read(state).getInt("exitStreak")
  private fun id(state: GameState) = LevelTravelEngine.read(state).getString("nodeId")
  private fun attempt(engine: LevelTravelEngine, state: GameState, turn: Int) = engine.attempt(state, "run", turn, "Thử thoát Level")
  private fun atNode(state: GameState, node: String): GameState {
    val t = LevelTravelEngine.read(state).put("nodeId", node)
    return state.copy(world = state.world + (LevelTravelEngine.KEY to t.toString()))
  }
  private fun expectFailure(message: String, block: () -> Unit) {
    try { block(); fail("expected $message") } catch (e: IllegalStateException) { assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(message)) }
  }

  @Test fun newGameDrawsOnceAndSecondStartDoesNotReroll() {
    val rng = RandomSequence(0.95)
    val engine = LevelTravelEngine(policy(), rng)
    val initial = engine.start(GameState.initial(), "run")
    assertEquals("LEVEL.NODE.10", id(initial)); assertEquals(1, rng.calls)
    expectFailure("game_already_started") { engine.start(initial) }
    assertEquals(1, rng.calls)
    assertTrue(initial.world.getValue("levelBackground").contains("cloudinary"))
  }

  @Test fun successSuccessFailureResetsAndThreeNewSuccessesExitExactlyOnce() {
    val rng = RandomSequence(0.0, 0.0, 0.49, 0.50, 0.1, 0.2, 0.3, 0.0)
    val engine = LevelTravelEngine(policy(), rng)
    var s = engine.start(GameState.initial(), "run")
    listOf(1, 2, 0, 1, 2).forEachIndexed { index, expected ->
      s = attempt(engine, s, index + 1).state
      assertEquals(expected, streak(s)); assertEquals("LEVEL.NODE.0", id(s))
    }
    val exited = attempt(engine, s, 6)
    assertTrue(exited.receipt.getBoolean("completed")); assertEquals(3, exited.receipt.getInt("streak"))
    assertEquals("LEVEL.NODE.1", id(exited.state)); assertEquals(0, streak(exited.state))
    val duplicate = attempt(engine, exited.state, 6)
    assertEquals(exited.state, duplicate.state); assertEquals(exited.receipt.toString(), duplicate.receipt.toString())
    assertEquals(8, rng.calls)
    val events = JSONObject(exited.state.world.getValue("flagsJson")).getJSONObject("storyContinuity").getJSONArray("levelTravelEvents")
    assertEquals(2, events.length())
  }

  @Test fun ordinaryActionsAreNotAttemptsAndDoNotDrawOrReset() {
    val actions = listOf("nói chuyện", "quan sát", "nghỉ ngơi", "đi về phía trước", "chiến đấu", "dùng vật phẩm",
      "tìm lối thoát", "quan sát cửa trắng", "nói về việc thử thoát Level", "không thử thoát Level", "nếu có cửa thì thử thoát Level")
    val rng = RandomSequence(0.0, 0.2)
    val engine = LevelTravelEngine(policy(), rng)
    val state = attempt(engine, engine.start(GameState.initial(), "run"), 1).state
    actions.forEach { action ->
      assertFalse(action, LevelTravelEngine.isExitAction(action))
      expectFailure("exit_intent_required") { engine.attempt(state, "run", 2, action) }
    }
    assertEquals(1, streak(state)); assertEquals(2, rng.calls)
  }

  @Test fun probabilityBoundaryIsExactlyHalfWithoutFollowerBonuses() {
    val rng = RandomSequence(0.0, 0.499999, 0.5)
    val engine = LevelTravelEngine(policy(), rng)
    val started = engine.start(GameState.initial(), "run")
    val success = attempt(engine, started, 1)
    val failed = attempt(engine, success.state, 2)
    assertTrue(success.receipt.getBoolean("success")); assertFalse(failed.receipt.getBoolean("success"))
    assertEquals(0, streak(failed.state)); assertEquals(50, failed.receipt.getInt("chancePercent"))
  }

  @Test fun visualOnlyRoomNodesAndHardLockConflictsDoNotDrawOrChangeState() {
    val rng = RandomSequence(0.0)
    val engine = LevelTravelEngine(policy(), rng)
    val unknown = atNode(engine.start(GameState.initial(), "run"), "LEVEL.NODE.0.DULLNESS.ZONE_01")
    val encoded = GameStateCodec.encode(unknown)
    expectFailure("exit_unconfigured") { attempt(engine, unknown, 1) }
    assertEquals(encoded, GameStateCodec.encode(unknown)); assertEquals(1, rng.calls)
    val config = policyJson()
    val nodes = config.getJSONArray("nodes")
    for (i in 0 until nodes.length()) if (nodes.getJSONObject(i).getString("id") == "LEVEL.NODE.0.5") {
      nodes.getJSONObject(i).put("destinations", JSONArray().put("LEVEL.NODE.9"))
    }
    expectFailure("canon_exit_conflict") { LevelTravelPolicy(config).destinations("LEVEL.NODE.0.5") }
  }

  @Test fun sublevelDoesNotReturnToParentAndKeepsHardDestination() {
    val rng = RandomSequence(0.0, 0.0, 0.0, 0.0, 0.0)
    val engine = LevelTravelEngine(policy(), rng)
    var s = atNode(engine.start(GameState.initial(), "run"), "LEVEL.NODE.0.5")
    repeat(3) { s = attempt(engine, s, it + 1).state }
    assertEquals("LEVEL.NODE.0.7", id(s)); assertEquals(0, streak(s))
  }

  @Test fun destinationPoolExcludesSelfAndEntryLocks() {
    val config = policyJson()
    val nodes = config.getJSONArray("nodes")
    for (i in 0 until nodes.length()) if (nodes.getJSONObject(i).getString("id") == "LEVEL.NODE.6") {
      nodes.getJSONObject(i).put("destinations", JSONArray().put("LEVEL.NODE.6").put("LEVEL.NODE.0.5").put("LEVEL.NODE.9"))
    }
    assertEquals(listOf("LEVEL.NODE.9"), LevelTravelPolicy(config).destinations("LEVEL.NODE.6"))
  }

  @Test fun saveLoadKeepsLocationStreakAndReceiptWithoutRng() {
    val rng = RandomSequence(0.0, 0.0, 0.0)
    val engine = LevelTravelEngine(policy(), rng)
    var s = engine.start(GameState.initial(), "run")
    repeat(2) { s = attempt(engine, s, it + 1).state }
    val decoded = GameStateCodec.decode(GameStateCodec.encode(s))
    assertEquals(s.world, decoded.world); assertEquals(2, streak(decoded))
    assertEquals(decoded, attempt(engine, decoded, 2).state); assertEquals(3, rng.calls)
  }

  @Test fun differentInputAndStaleRunCannotRerollSameAction() {
    val rng = RandomSequence(0.0, 0.2)
    val engine = LevelTravelEngine(policy(), rng)
    val s = attempt(engine, engine.start(GameState.initial(), "run"), 1).state
    expectFailure("exit_action_conflict") { engine.attempt(s, "run", 1, "exit level") }
    expectFailure("stale_game_run") { engine.attempt(s, "old", 2, "exit level") }
    expectFailure("stale_exit_turn") { engine.attempt(s, "run", 9, "exit level") }
    assertEquals(2, rng.calls)
  }

  @Test fun invalidDestinationRandomDoesNotCommitHalfATransition() {
    val rng = RandomSequence(0.0, 0.0, 0.0, 0.0, 1.0)
    val engine = LevelTravelEngine(policy(), rng)
    var s = engine.start(GameState.initial(), "run")
    repeat(2) { s = attempt(engine, s, it + 1).state }
    val raw = GameStateCodec.encode(s)
    expectFailure("invalid_random_source") { attempt(engine, s, 3) }
    assertEquals(raw, GameStateCodec.encode(s)); assertEquals(2, streak(s))
  }

  private class MemoryRepository : SaveRepository {
    var raw: String? = null
    var failSave = false
    var saves = 0
    override fun exists() = raw != null
    override fun clear() { raw = null }
    override fun load() = raw?.let(GameStateCodec::decode) ?: GameState.initial()
    override fun save(state: GameState) {
      check(!failSave) { "storage_failed" }
      raw = GameStateCodec.encode(state); saves++
    }
  }
  private fun facade(repo: MemoryRepository, engine: LevelTravelEngine): GameCoreFacade {
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java, LevelTravelEngine::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(repo, NoOpGamePipelineLogger, engine)
  }
  private fun initialUi() = """{"turn":1,"party":[],"inventory":[],"flags":{}}"""

  @Test fun facadeNormalTurnsNeverRollAndGmCannotChangeLevelOrStreak() {
    val rng = RandomSequence(0.0, 0.2)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val initial = JSONObject(f.initializeLevelGame(initialUi(), true))
    val after = JSONObject(f.processLevelAction(initial.toString(), "Thử thoát Level")).getJSONObject("state")
    val normal = JSONObject(f.processLevelAction(after.toString(), "nói chuyện"))
    assertFalse(normal.getBoolean("handled"))
    val forged = JSONObject(after.toString()).put("turn", 3).put("level", JSONObject().put("number", 9))
      .put("location", "Level 9").put("levelTravel", JSONObject().put("exitStreak", 3))
      .put("flags", JSONObject().put("confirmedExit", true).put("transitionReady", true))
    val result = JSONObject(f.finishLevelTurn(after.toString(), forged.toString(), "nói chuyện"))
    assertEquals("LEVEL.NODE.0", result.getJSONObject("level").getString("id"))
    assertEquals(after.getString("location"), result.getString("location"))
    assertEquals(1, result.getJSONObject("levelTravel").getInt("exitStreak")); assertEquals(2, rng.calls)
  }

  @Test fun processDeathBeforeUiSaveRecoversCoreReceiptWithoutReroll() {
    val rng = RandomSequence(0.0, 0.2)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val initial = f.initializeLevelGame(initialUi(), true)
    val first = JSONObject(f.processLevelAction(initial, "Thử thoát Level")).getJSONObject("state")
    val restarted = facade(repo, LevelTravelEngine(policy()) { error("must not draw") })
    val recovered = JSONObject(restarted.initializeLevelGame(initial, false))
    assertEquals(2, recovered.getInt("turn")); assertEquals(1, recovered.getJSONObject("levelTravel").getInt("exitStreak"))
    val retry = JSONObject(restarted.processLevelAction(initial, "Thử thoát Level")).getJSONObject("state")
    assertEquals(first.getJSONObject("exitResult").toString(), retry.getJSONObject("exitResult").toString())
  }

  @Test fun failedStorageDoesNotPublishPartiallyChangedState() {
    val rng = RandomSequence(0.0, 0.2)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val initial = f.initializeLevelGame(initialUi(), true)
    val raw = repo.raw
    repo.failSave = true
    expectFailure("storage_failed") { f.processLevelAction(initial, "Thử thoát Level") }
    assertEquals(raw, repo.raw); assertEquals(0, streak(repo.load()))
  }

  @Test fun actualCanonOnlyLoadsCurrentNodeAndCurrentLevel() {
    val store = JsonCanonStore.parseWithSupplements(assets("knowledge_db.json"), listOf(assets("level_0_canon_cards.json"), assets("level_1plus_canon_cards.json")))
    val engine = LevelTravelEngine(policy()) { 0.65 }
    val state = engine.start(GameState.initial(), "run")
    val ui = LevelTravelEngine.project(state, JSONObject(initialUi()))
    val scene = LegacyJsonSceneResolver().resolve(ui.toString(), "quan sát Level 0.5", """{"turn":1}""", false)
    assertEquals("LEVEL.07", scene.levelId); assertEquals("LEVEL.NODE.7", scene.locationNodeId)
    val selection = CanonResolver(store).resolve(scene, store.snapshot())
    assertTrue(selection.missingRequiredIds.toString(), selection.missingRequiredIds.isEmpty())
    val selected = selection.required + selection.optional
    assertTrue(selected.any { it.id == "LEVEL.07" }); assertTrue(selected.any { it.id == "LEVEL.NODE.7" })
    assertFalse(selected.any { it.id.startsWith("LEVEL.NODE.") && it.id != "LEVEL.NODE.7" })
  }

  @Test fun hundredOrdinaryTurnsPreserveLevelAndStreakWithoutRng() {
    val rng = RandomSequence(0.0, 0.2)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    var ui = JSONObject(f.initializeLevelGame(initialUi(), true))
    ui = JSONObject(f.processLevelAction(ui.toString(), "Thử thoát Level")).getJSONObject("state")
    val location = ui.getString("location")
    repeat(100) {
      val before = ui.toString()
      f.processLevelAction(before, "quan sát")
      ui = JSONObject(f.finishLevelTurn(before, JSONObject(before).put("turn", ui.getInt("turn") + 1).toString(), "quan sát"))
    }
    assertEquals(1, ui.getJSONObject("levelTravel").getInt("exitStreak"))
    assertEquals(location, ui.getString("location")); assertEquals(2, rng.calls)
  }

  @Test fun concurrentDuplicateCallbacksShareOnePersistentOutcome() {
    val rng = RandomSequence(0.0, 0.2)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val ui = f.initializeLevelGame(initialUi(), true)
    val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
    try {
      val results = pool.invokeAll((1..8).map { java.util.concurrent.Callable { f.processLevelAction(ui, "Thử thoát Level") } })
      val receipts = results.map { JSONObject(it.get()).getJSONObject("state").getJSONObject("exitResult").toString() }
      assertEquals(1, receipts.distinct().size); assertEquals(2, rng.calls)
      assertEquals(1, streak(repo.load()))
    } finally { pool.shutdownNow() }
  }

  @Test fun delayedOldCallbackCannotRewindLaterUiTurn() {
    val rng = RandomSequence(0.0, 0.2, 0.3)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val initial = f.initializeLevelGame(initialUi(), true)
    val one = JSONObject(f.processLevelAction(initial, "Thử thoát Level")).getJSONObject("state")
    f.processLevelAction(one.toString(), "Thử thoát Level")
    val late = JSONObject(f.processLevelAction(initial, "Thử thoát Level")).getJSONObject("state")
    assertEquals(3, late.getInt("turn")); assertEquals(2, late.getJSONObject("levelTravel").getInt("exitStreak"))
    assertEquals(3, rng.calls)
  }

  @Test fun shippedSpecialAreasCanBeEnteredAndExitedOnlyAtThreeSuccesses() {
    val p = policy()
    listOf("LEVEL.NODE.0.RED_ROOMS", "LEVEL.NODE.0.DULLNESS", "LEVEL.NODE.0.EPSILON").forEach { special ->
      val entering = p.destinations("LEVEL.NODE.0")
      val leaving = p.destinations(special)
      assertTrue("$special must be reachable", special in entering)
      val rng = RandomSequence(0.0, 0.0, 0.0, 0.0, (entering.indexOf(special) + 0.1) / entering.size,
        0.0, 0.0, 0.0, (leaving.indexOf("LEVEL.NODE.9") + 0.1) / leaving.size)
      val engine = LevelTravelEngine(p, rng)
      var state = engine.start(GameState.initial(), "run")
      repeat(2) { state = attempt(engine, state, it + 1).state; assertEquals("LEVEL.NODE.0", id(state)) }
      state = attempt(engine, state, 3).state
      assertEquals(special, id(state)); assertEquals(0, streak(state))
      assertEquals(special, JSONObject(state.world.getValue("levelJson")).getString("id"))
      assertEquals(p.node(special).getString("background"), state.world["levelBackground"])
      repeat(2) { state = attempt(engine, state, it + 4).state; assertEquals(special, id(state)) }
      val escaped = attempt(engine, state, 6)
      assertEquals("LEVEL.NODE.9", id(escaped.state)); assertEquals(0, streak(escaped.state))
      assertEquals(escaped.state, attempt(engine, escaped.state, 6).state)
      assertEquals(9, rng.calls)
    }
  }

  @Test fun allStandaloneSublevelsAndAreasHaveInboundAndIndependentExitPolicies() {
    val p = policy()
    val rootNodes = p.nodes.values.filter { it.getString("kind") in setOf("SUBLEVEL", "AREA") }
    assertEquals(29, rootNodes.size)
    rootNodes.forEach { node ->
      val target = node.getString("id")
      assertTrue("$target must be enabled", node.getBoolean("independentExit"))
      val from = if (target == "LEVEL.NODE.0.5") "LEVEL.NODE.0.2" else "LEVEL.NODE.0"
      assertTrue("$target must be reachable from $from", target in p.destinations(from))
      assertTrue(p.destinations(target).isNotEmpty())
      assertFalse(target in p.destinations(target))
    }
    assertEquals(listOf("LEVEL.NODE.0.7"), p.destinations("LEVEL.NODE.0.5"))
    assertFalse("LEVEL.NODE.0.5" in p.destinations("LEVEL.NODE.0"))
    assertEquals((0..10).map { "LEVEL.NODE.$it" }, p.startingIds)
  }

  @Test fun failureInSpecialAreaResetsComboAndSaveLoadKeepsCurrentNode() {
    val p = policy()
    val special = "LEVEL.NODE.0.DULLNESS"
    val entering = p.destinations("LEVEL.NODE.0")
    val rng = RandomSequence(0.0, 0.0, 0.0, 0.0, (entering.indexOf(special) + 0.1) / entering.size,
      0.0, 0.0, 0.5)
    val engine = LevelTravelEngine(p, rng)
    var state = engine.start(GameState.initial(), "run")
    repeat(5) { state = attempt(engine, state, it + 1).state }
    assertEquals(special, id(state)); assertEquals(2, streak(state))
    state = GameStateCodec.decode(GameStateCodec.encode(state))
    val unchanged = LevelTravelEngine.protectCandidate(state, JSONObject().put("location", "another Level"), "quan sát")
    assertEquals(special, unchanged.getJSONObject("levelTravel").getString("nodeId"))
    assertEquals(2, unchanged.getJSONObject("levelTravel").getInt("exitStreak"))
    state = attempt(engine, state, 6).state
    assertEquals(special, id(state)); assertEquals(0, streak(state))
    assertEquals(8, rng.calls)
  }

  @Test fun allEnabledScenesCompileCurrentCanonWithinBudget() {
    val store = JsonCanonStore.parseWithSupplements(assets("knowledge_db.json"), listOf(assets("level_0_canon_cards.json"), assets("level_1plus_canon_cards.json")))
    val p = policy()
    p.nodes.values.filter { it.getBoolean("independentExit") }.forEach { node ->
      val id = node.getString("id")
      val ui = JSONObject(initialUi())
        .put("level", JSONObject().put("id", id).put("canonScope", node.getString("scope")))
        .put("levelTravel", JSONObject().put("nodeId", id))
        .put("location", node.getString("entryLocation"))
      val scene = LegacyJsonSceneResolver().resolve(ui.toString(), "quan sát", """{"turn":1}""", false)
      val selection = CanonResolver(store).resolve(scene, store.snapshot())
      val result = ContextCompiler().compile(scene, selection, ProviderProfile(), ContextBudget(10000, 1000, 500, 10000, 6000))
      assertTrue("$id: $result", result is ContextPacketResult.Ready)
      val records = selection.required + selection.optional
      assertTrue("$id card missing", records.any { it.id == id })
      assertFalse(records.any { it.id.startsWith("LEVEL.NODE.") && it.id != id })
      val source = store.loadRecordsByIds(setOf(id), store.snapshot()).single()
      assertEquals(source.truthStatus, records.single { it.id == id }.truthStatus)
    }
  }


  @Test fun ordinaryMovementCanChangeLocalPositionWithoutChangingLevelOrStreak() {
    val rng = RandomSequence(0.0)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val before = f.initializeLevelGame(initialUi(), true)
    val proposal = JSONObject(before).put("turn", 2).put("location", "Phòng kế bên hành lang hiện tại")
      .put("level", JSONObject().put("number", 9))
    val result = JSONObject(f.finishLevelTurn(before, proposal.toString(), "đi sang phòng kế bên"))
    assertEquals("Phòng kế bên hành lang hiện tại", result.getString("location"))
    assertEquals("LEVEL.NODE.0", result.getJSONObject("level").getString("id"))
    assertEquals(0, result.getJSONObject("levelTravel").getInt("exitStreak")); assertEquals(1, rng.calls)
  }

  @Test fun sublevelPacketUsesExactNodeWithoutParentEnvironment() {
    val store = JsonCanonStore.parseWithSupplements(assets("knowledge_db.json"), listOf(assets("level_0_canon_cards.json"), assets("level_1plus_canon_cards.json")))
    listOf("0.1", "0.5", "0.7").forEach { key ->
      val node = "LEVEL.NODE.$key"
      val ui = JSONObject(initialUi()).put("level", JSONObject().put("canonScope", "LEVEL.00"))
        .put("levelTravel", JSONObject().put("nodeId", node))
      val scene = LegacyJsonSceneResolver().resolve(ui.toString(), "quan sát", """{"turn":1}""", false)
      val selection = CanonResolver(store).resolve(scene, store.snapshot())
      val result = ContextCompiler().compile(scene, selection, ProviderProfile(), ContextBudget(10000, 1000, 500, 10000, 6000))
      assertTrue("$node: $result", result is ContextPacketResult.Ready)
      val records = selection.required + selection.optional
      assertTrue(records.any { it.id == node }); assertFalse(records.any { it.id == "LEVEL.00" })
      assertFalse(records.any { it.id.startsWith("LEVEL.NODE.") && it.id != node })
    }
  }


  @Test fun processDeathBeforeFirstBrowserAutosaveResumesSameNewGameWithoutRng() {
    val rng = RandomSequence(0.8)
    val repo = MemoryRepository(); val f = facade(repo, LevelTravelEngine(policy(), rng))
    val initial = JSONObject(f.resumeLevelGame(initialUi(), false))
    val restarted = facade(repo, LevelTravelEngine(policy()) { error("must not draw at boot") })
    val recovered = JSONObject(restarted.resumeLevelGame(initialUi(), false))
    assertEquals(initial.getJSONObject("levelTravel").toString(), recovered.getJSONObject("levelTravel").toString())
    assertEquals(initial.getString("location"), recovered.getString("location")); assertEquals(1, rng.calls)
  }

}
