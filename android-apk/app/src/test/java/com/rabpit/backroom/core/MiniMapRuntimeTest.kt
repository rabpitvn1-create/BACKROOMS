package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class MiniMapRuntimeTest {
  private fun script(level: Int = 0, index: Int = 1, nodeId: String = level.toString()) = MiniMapScenario.parse("""
    {"schemaVersion":5,"id":"investigation","levelNumber":$level,"index":$index,"nodeAnchor":"${nodeId}",
    "objective":"Đối chiếu dấu hiệu và lời khai để xác định màu khóa, hướng van.","entryRoomId":"hall","exitRoomId":"hall",
    "rooms":[{"id":"hall","name":"Hành lang","description":"Một bảng điều khiển có hai hàng lựa chọn.","environmentId":"phong_moc","adjacentRoomIds":["lab","witness","alcove"]},
    {"id":"lab","name":"Kho hồ sơ","description":"Tủ hồ sơ và một mảnh giấy.","environmentId":"phong_moc","adjacentRoomIds":["hall"]},
    {"id":"witness","name":"Phòng trực","description":"Một người gác đang chờ.","environmentId":"phong_moc","adjacentRoomIds":["hall"]},
    {"id":"alcove","name":"Hốc tường","description":"Một góc phòng để quan sát.","environmentId":"phong_moc","adjacentRoomIds":["hall"]}],
    "npcs":[{"id":"guard_npc","name":"Trần Văn Duy","originPeriod":"thập niên 1940","role":"Nhân chứng trong phòng trực",
    "purpose":"Cung cấp lời khai để người chơi kiểm chứng bằng hồ sơ",
    "description":"Một người đang đứng cạnh bàn trực.","roomId":"witness"}],
    "facts":[{"id":"color","label":"Màu khóa","value":"đỏ","values":["đỏ","xanh"]},{"id":"valve","label":"Hướng van","value":"trái","values":["trái","phải"]}],
    "interactions":[
    {"id":"paper","kind":"SEARCH","roomId":"lab","label":"Đọc hồ sơ","requires":{"op":"TRUE"},"effect":{"type":"CLUE","factId":"color"}},
    {"id":"mark","kind":"SEARCH","roomId":"hall","label":"Đối chiếu dấu van","requires":{"op":"HAS","id":"paper"},"effect":{"type":"EVIDENCE","factId":"valve"}},
    {"id":"guard","kind":"TALK","roomId":"witness","npcId":"guard_npc","label":"Hỏi về màu khóa","requires":{"op":"TRUE"},"effect":{"type":"CLAIM","factId":"color","value":"xanh","policy":"LIE"}},
    {"id":"answer_red","kind":"SEARCH","roomId":"hall","label":"Chọn màu đỏ","requires":{"op":"AND","args":[{"op":"HAS","id":"mark"},{"op":"HAS","id":"guard"}]},"effect":{"type":"ANSWER","factId":"color","value":"đỏ"}},
    {"id":"answer_blue","kind":"SEARCH","roomId":"hall","label":"Chọn màu xanh","requires":{"op":"AND","args":[{"op":"HAS","id":"mark"},{"op":"HAS","id":"guard"}]},"effect":{"type":"ANSWER","factId":"color","value":"xanh"}},
    {"id":"answer_left","kind":"SEARCH","roomId":"hall","label":"Chọn van trái","requires":{"op":"HAS","id":"answer_red"},"effect":{"type":"ANSWER","factId":"valve","value":"trái"}},
    {"id":"answer_right","kind":"SEARCH","roomId":"hall","label":"Chọn van phải","requires":{"op":"HAS","id":"answer_red"},"effect":{"type":"ANSWER","factId":"valve","value":"phải"}}],
    "goal":{"op":"AND","args":[{"op":"HAS","id":"answer_red"},{"op":"HAS","id":"answer_left"}]}}
  """.trimIndent())
  @Test fun exactEnvironmentIdsAreValidatedWithoutInferringFromRoomNames() {
    assertEquals(37, Level0Environments.all.size)
    assertEquals(8, Level0Environments.all.count { it.parent == null })
    for (environment in Level0Environments.all) {
      val scene = leverPuzzle()
      scene.getJSONArray("rooms").getJSONObject(0).put("environmentId", environment.id).put("name", environment.name)
      MiniMapScenario.validate(scene, "0", 1)
    }
    for (invalid in listOf("unknown", "../phong_moc", "Phòng Mốc", "0.1")) {
      val scene = leverPuzzle()
      scene.getJSONArray("rooms").getJSONObject(0).put("environmentId", invalid)
      try { MiniMapScenario.validate(scene, "0", 1); fail("Rejected environment required") }
      catch (expected: IllegalArgumentException) { assertEquals("scene_environment", expected.message) }
    }
    val missing = leverPuzzle()
    missing.getJSONArray("rooms").getJSONObject(0).remove("environmentId")
    try { MiniMapScenario.validate(missing, "0", 1); fail("Missing identity must fail") }
    catch (expected: IllegalArgumentException) { assertEquals("scenario_fields", expected.message) }
  }
  private fun leverPuzzle(): JSONObject {
    val scene = script()
    // This device-only task should not retain the unrelated color/valve puzzle
    // from the shared test fixture: it would be rejected as dead story logic.
    val clue = MiniMapScenario.copy(MiniMapScenario.objects(scene.getJSONArray("interactions"))
      .first { it.getString("id") == "paper" })
      .put("label", "Đọc sơ đồ cần gạt")
      .put("observation", "Trên giấy có vệt đỏ thành chuỗi 1–1–2–2–3; nét vẽ mô tả các lần gạt lên rồi xuống.")
      .put("effect", JSONObject().put("type", "HINT"))
    scene.put("npcs", JSONArray()).put("facts", JSONArray())
      .put("interactions", JSONArray().put(clue))
    val controls = listOf("lever_1_up", "lever_1_down", "lever_2_up",
      "lever_2_down", "lever_3_up", "lever_3_down")
    val labels = listOf("Gạt cần số 1 lên", "Gạt cần số 1 xuống", "Gạt cần số 2 lên",
      "Gạt cần số 2 xuống", "Gạt cần số 3 lên", "Gạt cần số 3 xuống")
    controls.forEachIndexed { i, control ->
      scene.getJSONArray("interactions").put(JSONObject()
        .put("id", control).put("kind", "SEARCH").put("roomId", "hall")
        .put("label", labels[i]).put("requires", JSONObject("""{"op":"TRUE"}"""))
        .put("effect", JSONObject().put("type", "STEP").put("mechanismId", "door_lock")))
    }
    scene.put("mechanisms", JSONArray().put(JSONObject().put("id", "door_lock")
      .put("clueId", "paper").put("steps", JSONArray()
        .put("lever_1_up").put("lever_1_down").put("lever_2_up")
        .put("lever_2_down").put("lever_3_up"))))
      .put("goal", JSONObject().put("op", "HAS").put("id", "door_lock"))
    return scene
  }

  private fun state(level: Int = 0, index: Int = 1, nodeId: String = level.toString()): GameState {
    val node = LevelRouteRegistry.node(nodeId)
    var state = MiniMapRuntime.initialize(CoreClientProjection.initialize(GameState.initial()).let {
      it.copy(world = it.world + ("levelJson" to node.asJson().toString()))
    })
    val p = MiniMapRuntime.progress(state).put("parent",level).put("index",index).put("roomId","hall")
      .put("visitedRooms", JSONArray().put("hall")).put("areaEvents", JSONArray())
    state = state.copy(world = state.world + (MiniMapRuntime.STATE to p.toString()))
    return state.copy(world = state.world + (MiniMapRuntime.scriptKey(p) to MiniMapScenario.validate(script(level,index,nodeId),nodeId,index).toString()))
  }
  private class MemorySave(var state: GameState): SaveRepository {
    var checkpoint: String? = null
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() { error("not used") }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { checkpoint = GameCheckpointCodec.encode(state,presentation) }
    override fun loadCheckpoint() = checkpoint?.let(GameCheckpointCodec::decode)
  }
  private class CountingRng(val value: Int = 9999): SecureRandom() {
    var calls = 0
    override fun nextInt(bound: Int): Int { calls++; return value.coerceAtMost(bound - 1) }
  }
  private fun facade(repo: MemorySave, rng: SecureRandom = CountingRng()): GameCoreFacade {
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java,GamePipelineLogger::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(repo,NoOpGamePipelineLogger).also {
      GameCoreFacade::class.java.getDeclaredField("spawnRng").apply { isAccessible=true }.set(it,rng)
    }
  }
  private fun choice(state: GameState, target: String): String = MiniMapScenario.objects(MiniMapRuntime.actions(state)).first { it.getString("targetId") == target }.let {
    MiniMapScenario.copy(it).apply {
    remove("label")
    // Display-only mechanism grouping metadata is not part of the Core command.
    remove("mechanismId")
  }.toString()
  }
  private fun apply(core: GameCoreFacade, repo: MemorySave, target: String) {
    val command = choice(repo.state,target)
    core.beginSceneAction(command)
    core.commitSceneAction(JSONObject(command).getString("actionId"),"Cao Minh quan sát kết quả.","{}")
  }
  @Test fun mapBranchesRevealOnlyDiscoveredRoomsAndAreaLogExcludesPuzzleAnswers() {
    val repo = MemorySave(state())
    val core = facade(repo)
    val initial = MiniMapRuntime.publicView(repo.state)
    val planner = JSONObject(core.scenePlayerActionContext())
    assertEquals(setOf("currentRoomId", "nodeId", "miniIndex", "rooms", "availableActions"),
      planner.keys().asSequence().toSet())
    assertEquals(initial.getJSONObject("map").getJSONArray("rooms").toString(),
      planner.getJSONArray("rooms").toString())
    assertFalse(planner.toString().contains("\"interactions\""))
    assertFalse(planner.toString().contains("\"steps\""))
    assertFalse(planner.toString().contains("\"facts\""))
    assertFalse(planner.toString().contains("\"requires\""))
    val offered = MiniMapScenario.objects(planner.getJSONArray("availableActions"))
    assertTrue(offered.all { it.keys().asSequence().toSet() == setOf("kind", "targetId", "label") })
    assertEquals(MiniMapScenario.objects(initial.getJSONArray("availableActions"))
      .filter { it.getString("kind") in setOf("MOVE", "SEARCH") }.size, offered.size)
    assertFalse(offered.any { it.getString("targetId") == "paper" || it.getString("targetId") == "mark" })
    assertTrue(offered.any { it.getString("kind") == "MOVE" &&
      it.getString("targetId") == "room:lab" &&
      it.getString("label") == "Lối sang khu vực chưa khám phá" })
    // Environment-specific snapshots are visual-only. The planning contract
    // remains unchanged and does not contain the hidden frontier visual key.
    assertFalse(planner.has("snapshots"))
    assertFalse(planner.toString().contains("phong_moc"))
    val initialMap = initial.getJSONObject("map")
    assertEquals("phong_moc", initialMap.getJSONObject("snapshots").getString("lab"))
    assertEquals("phong_moc", initialMap.getJSONObject("snapshots").getString("hall"))
    assertEquals("hall", initialMap.getString("currentRoomId"))
    assertEquals("hall", initialMap.getString("entryRoomId"))
    val initialRooms = MiniMapScenario.objects(initialMap.getJSONArray("rooms"))
    assertEquals("Hành lang", initialRooms.first { it.getString("id") == "hall" }.getString("name"))
    assertEquals("Chưa khám phá", initialRooms.first { it.getString("id") == "lab" }.getString("name"))
    assertFalse(initial.toString().contains("MẬT MÃ"))
    apply(core, repo, "room:lab")
    val atLab = JSONObject(core.scenePlayerActionContext())
    assertTrue(MiniMapScenario.objects(atLab.getJSONArray("availableActions"))
      .any { it.getString("kind") == "SEARCH" && it.getString("targetId") == "paper" })
    assertFalse("Locked interactions stay private",
      MiniMapScenario.objects(atLab.getJSONArray("availableActions"))
        .any { it.getString("targetId") == "mark" })
    val explored = MiniMapRuntime.publicView(repo.state)
    val map = explored.getJSONObject("map")
    assertEquals("lab", map.getString("currentRoomId"))
    assertEquals("Kho hồ sơ", MiniMapScenario.objects(map.getJSONArray("rooms"))
      .first { it.getString("id") == "lab" }.getString("name"))
    val events = MiniMapScenario.objects(explored.getJSONArray("areaLog"))
    assertEquals(1, events.size)
    assertTrue(events[0].getString("text").contains("Kho hồ sơ"))
    apply(core, repo, "paper")
    val log = MiniMapRuntime.publicView(repo.state).getJSONArray("areaLog").toString()
    assertFalse(log.contains("Màu khóa"))
    assertFalse(log.contains("đỏ"))
    assertFalse(log.contains("paper"))
    assertFalse(log.contains("manh mối"))
    assertTrue(log.contains("Đã ghi nhận dấu vết tại Kho hồ sơ."))
    // Retracing a room does not create another discovery event.
    apply(core, repo, "room:hall")
    apply(core, repo, "room:lab")
    assertEquals(2, MiniMapRuntime.publicView(repo.state).getJSONArray("areaLog").length())
  }

  @Test fun distinctDiscoveriesInOneRoomHaveSeparateSafeAreaLogEntries() {
    val initial = state()
    val progress = MiniMapRuntime.progress(initial)
    val revised = MiniMapScenario.copy(requireNotNull(MiniMapRuntime.script(initial)))
    MiniMapScenario.objects(revised.getJSONArray("interactions"))
      .first { it.getString("id") == "mark" }.put("roomId", "lab")
    val validated = MiniMapScenario.validate(revised, "0", 1)
    val repo = MemorySave(initial.copy(world = initial.world +
      (MiniMapRuntime.scriptKey(progress) to validated.toString())))
    val core = facade(repo)
    apply(core, repo, "room:lab")
    apply(core, repo, "paper")
    apply(core, repo, "mark")
    val privateLog = MiniMapScenario.objects(MiniMapRuntime.progress(repo.state).getJSONArray("areaEvents"))
    assertEquals(setOf("paper", "mark"), privateLog.filter { it.getString("type") == "DISCOVERY" }
      .map { it.getString("sourceId") }.toSet())
    val visibleLog = MiniMapScenario.objects(MiniMapRuntime.publicView(repo.state).getJSONArray("areaLog"))
    assertEquals(2, visibleLog.count { it.getString("type") == "DISCOVERY" })
    assertTrue(visibleLog.all { it.keys().asSequence().toSet() == setOf("type", "text") })
    assertFalse(visibleLog.toString().contains("paper"))
    assertFalse(visibleLog.toString().contains("mark"))
    assertFalse(visibleLog.toString().contains("sourceId"))
  }

  @Test fun generatedObjectivesCannotGiveCorrectAnswersOrDeviceOrder() {
    val answer = script().put("objective", "Chọn màu đỏ rồi chọn van trái để mở đường.")
    val answerError = assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(answer, "0", 1)
    }
    assertEquals("scene_objective_spoiler", answerError.message)
    val order = leverPuzzle().put("objective", "Gạt các cần theo đúng thứ tự 1–1–2–2–3 để mở khóa.")
    val orderError = assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(order, "0", 1)
    }
    assertEquals("scene_objective_spoiler", orderError.message)
    assertEquals("investigation", MiniMapScenario.validate(script(), "0", 1).getString("id"))
  }

  @Test fun sequencePuzzleRequiresDeductionAndCorrectControlOrderWithPersistentRetries() {
    val script = leverPuzzle()
    assertEquals("investigation", MiniMapScenario.validate(script, "0", 1).getString("id"))
    val base = state()
    val progress = MiniMapRuntime.progress(base)
    val visualFlags = JSONObject(base.world["flagsJson"] ?: "{}")
      .put("visualAreaKey", "fixed_panel")
      .put("exploration", JSONObject().put(SnapshotSceneProgress.STEP_KEY, 7))
    val scripted = base.copy(world = base.world + mapOf(
      MiniMapRuntime.scriptKey(progress) to script.toString(),
      "flagsJson" to visualFlags.toString()))
    val repo = MemorySave(scripted)
    val rng = CountingRng()
    val core = facade(repo, rng)
    // Six controls remain selectable, including the one decoy. Solving is not a
    // matter of tapping each button once or answering immediately.
    assertEquals(6, MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).count {
      it.getString("targetId").startsWith("lever_")
    })
    apply(core, repo, "lever_2_up")
    assertFalse(MiniMapRuntime.complete(repo.state))
    assertEquals(0, MiniMapRuntime.progress(repo.state).getJSONObject("mechanismPositions").getInt("door_lock"))
    apply(core, repo, "lever_1_up")
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any {
      it.getString("targetId") == "lever_1_up"
    })
    // Wrong second input resets progress rather than unlocking or softlocking.
    apply(core, repo, "lever_3_up")
    assertFalse(MiniMapRuntime.complete(repo.state))
    assertEquals(0, MiniMapRuntime.progress(repo.state).getJSONObject("mechanismPositions").getInt("door_lock"))
    val discoveredDeviceEvents = MiniMapScenario.objects(MiniMapRuntime.publicView(repo.state).getJSONArray("areaLog"))
    assertEquals(1, discoveredDeviceEvents.count { it.getString("type") == "DEVICE" })
    assertFalse(discoveredDeviceEvents.toString().contains("lever_"))
    assertFalse(discoveredDeviceEvents.toString().contains("1–1–2–2–3"))
    apply(core, repo, "lever_1_up")
    apply(core, repo, "lever_1_down")
    assertEquals(2, MiniMapRuntime.progress(repo.state).getJSONObject("mechanismPositions").getInt("door_lock"))
    val afterReload = GameStateCodec.decode(GameStateCodec.encode(repo.state))
    assertEquals(2, MiniMapRuntime.progress(afterReload).getJSONObject("mechanismPositions").getInt("door_lock"))
    apply(core, repo, "lever_2_up")
    apply(core, repo, "lever_2_down")
    assertFalse(MiniMapRuntime.complete(repo.state))
    apply(core, repo, "lever_3_up")
    assertTrue(MiniMapRuntime.complete(repo.state))
    assertEquals("Repeating controls must not duplicate device discovery", 1,
      MiniMapScenario.objects(MiniMapRuntime.publicView(repo.state).getJSONArray("areaLog"))
        .count { it.getString("type") == "DEVICE" })
    assertEquals("Mechanical inputs must not randomly spawn enemies", 0, rng.calls)
    val postDevice = JSONObject(repo.state.world.getValue("flagsJson"))
    assertEquals(7, postDevice.getJSONObject("exploration").getInt(SnapshotSceneProgress.STEP_KEY))
    assertEquals("fixed_panel", postDevice.getString("visualAreaKey"))
    assertEquals("hall", MiniMapRuntime.progress(repo.state).getString("roomId"))
    assertEquals(1, MiniMapScenario.objects(MiniMapRuntime.progress(repo.state).getJSONArray("records"))
      .count { it.getString("kind") == "MECHANISM" })
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      .any { it.getString("targetId") == "next" })
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      .any { it.getString("targetId").startsWith("lever_") })
    assertFalse(MiniMapRuntime.publicView(repo.state).toString().contains("mechanismPositions"))
    assertFalse(MiniMapRuntime.publicView(repo.state).toString().contains("lever_1_down"))
  }

  @Test fun factFreeMechanicalPuzzleCanUseObservationalHint() {
    val scene = leverPuzzle()
    val originalActions = MiniMapScenario.objects(scene.getJSONArray("interactions"))
    val clue = MiniMapScenario.copy(originalActions.first { it.getString("id") == "paper" })
      .put("label", "Đọc sơ đồ cần gạt")
      .put("observation", "Trên giấy có vệt đỏ thành chuỗi 1–1–2–2–3; nét vẽ mô tả gạt lên rồi xuống.")
      .put("effect", JSONObject().put("type", "HINT"))
    scene.put("npcs", JSONArray()).put("facts", JSONArray())
      .put("interactions", JSONArray().put(clue).apply {
        originalActions.filter { it.getJSONObject("effect").getString("type") == "STEP" }
          .forEach { put(it) }
      })
    assertEquals("investigation", MiniMapScenario.validate(scene, "0", 1).getString("id"))
    val base = state()
    val p = MiniMapRuntime.progress(base)
    val repo = MemorySave(base.copy(world = base.world +
      (MiniMapRuntime.scriptKey(p) to scene.toString())))
    val core = facade(repo)
    apply(core, repo, "room:lab")
    apply(core, repo, "paper")
    val projection = MiniMapRuntime.publicView(repo.state)
    assertTrue(projection.getJSONArray("records").getJSONObject(0).getString("text").contains("1–1–2–2–3"))
    assertTrue(projection.getString("temporaryInference").contains("dấu vết"))
    assertFalse(projection.toString().contains("mechanismPositions"))
  }

  @Test fun invalidDeviceScriptsCannotCreateUnsolvableSequence() {
    val missing = leverPuzzle().put("mechanisms", JSONArray().put(JSONObject()
      .put("id", "door_lock").put("clueId", "unknown_evidence")
      .put("steps", JSONArray().put("lever_1_up").put("lever_1_down")
        .put("lever_2_up").put("lever_2_down").put("lever_3_up"))))
    assertEquals("mechanism_missing_clue", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(missing, "0", 1)
    }.message)
    val short = leverPuzzle()
    short.getJSONArray("mechanisms").getJSONObject(0)
      .put("steps", JSONArray().put("lever_1_up").put("lever_3_up"))
    assertEquals("mechanism_sequence", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(short, "0", 1)
    }.message)
    val hidden = leverPuzzle()
    hidden.getJSONArray("interactions").getJSONObject(1)
      .put("requires", JSONObject("""{"op":"HAS","id":"paper"}"""))
    assertEquals("mechanism_control", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(hidden, "0", 1)
    }.message)
  }

  @Test fun sceneNamesMustUseVietnameseExceptCanonProperNames() {
    val hybrid = script()
    hybrid.getJSONArray("rooms").getJSONObject(0).put("name", "Khu Trần Giả Vòng Down")
    assertEquals("scene_language_mixed", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(hybrid, "0", 1)
    }.message)

    val hybridDescription = script()
    hybridDescription.getJSONArray("rooms").getJSONObject(0)
      .put("description", "Phía sau lớp giấy dán tường là một hidden room nhỏ.")
    assertEquals("scene_language_mixed", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(hybridDescription, "0", 1)
    }.message)

    val namedCanon = script()
    namedCanon.getJSONArray("rooms").getJSONObject(0)
      .put("name", "Góc tường gần Hollow Grasper")
    assertEquals("investigation", MiniMapScenario.validate(namedCanon, "0", 1).getString("id"))
    val canonicalDestination = script()
    canonicalDestination.getJSONArray("rooms").getJSONObject(0).put("name", "Lối rẽ gần Manila Room")
    assertEquals("investigation", MiniMapScenario.validate(canonicalDestination, "0", 1).getString("id"))
    val canonicalEntity = script()
    canonicalEntity.getJSONArray("rooms").getJSONObject(0)
      .put("description", "Một dấu hiệu của Predatory Window hiện rõ trên mặt giấy cũ.")
    assertEquals("investigation", MiniMapScenario.validate(canonicalEntity, "0", 1).getString("id"))
  }

  @Test fun roomNamesArePlacesNotActionCommandsOrTitleCaseWordSalad() {
    for (bad in listOf("Hành Lang Góc Khuyết", "Phòng Dây Điện Hở", "Nghe Âm Huỳnh Quang")) {
      val scene = script()
      scene.getJSONArray("rooms").getJSONObject(0).put("name", bad)
      assertEquals(bad, "scene_room_name", assertThrows(IllegalArgumentException::class.java) {
        MiniMapScenario.validate(scene, "0", 1)
      }.message)
    }
    for (natural in listOf("Hành lang có góc khuất", "Phòng có dây điện hở",
      "Hành lang đèn huỳnh quang kêu rè", "Góc tường gần Hollow Grasper",
      "Lối rẽ gần Manila Room")) {
      val scene = script()
      scene.getJSONArray("rooms").getJSONObject(0).put("name", natural)
      assertEquals(natural, "investigation", MiniMapScenario.validate(scene, "0", 1).getString("id"))
    }
    // The check applies to room names only, not player actions.
    val scene = script()
    scene.getJSONArray("interactions").getJSONObject(0).put("label", "Nghe tiếng đèn kêu rè")
    assertEquals("investigation", MiniMapScenario.validate(scene, "0", 1).getString("id"))
  }

  @Test fun currentLevelZeroRejectsScenesThatPreviewLevelOne() {
    val concreteExit = script()
    concreteExit.getJSONArray("rooms").getJSONObject(0).put("name", "Lối ra vùng bê tông")
    // A concrete wall or improvised mechanism is allowed inside Level 0.
    assertEquals("investigation", MiniMapScenario.validate(concreteExit, "0", 1).getString("id"))
    val futureObjective = script().put("objective", "Đến Level 1 trước khi kết thúc nhiệm vụ")
    assertEquals("scene_canon_boundary", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(futureObjective, "0", 1)
    }.message)
    val futureProse = script()
    futureProse.getJSONArray("interactions").getJSONObject(0).put("observation",
      "Một khoảng hành lang công nghiệp xuất hiện ở đầu đường, ngay sát tấm thảm mục.")
    assertEquals("investigation", MiniMapScenario.validate(futureProse, "0", 1).getString("id"))
  }

  @Test fun observationsAndInferenceHideInternalEnumsAndOnlySuggestAvailableSteps() {
    val scene = script()
    scene.getJSONArray("interactions").getJSONObject(0).put("observation",
      "Mảnh hồ sơ ghi rõ màu khóa cùng dấu sáp niêm phong còn nguyên vẹn trên mép giấy.")
    scene.getJSONArray("interactions").getJSONObject(2).put("observation",
      "Tôi thấy chiếc khóa màu xanh khi đang đứng trực ca đêm, nhưng đèn hành lang chớp liên tục.")
    MiniMapScenario.validate(scene, "0", 1)
    val base = state()
    val p = MiniMapRuntime.progress(base)
    val withCast = base.copy(world = base.world + (MiniMapRuntime.scriptKey(p) to scene.toString()))
    val repo = MemorySave(withCast)
    val core = facade(repo)
    assertTrue(MiniMapRuntime.publicView(repo.state).getString("temporaryInference").contains("Chưa đủ dữ kiện"))
    apply(core, repo, "room:witness")
    apply(core, repo, "guard")
    val claimView = MiniMapRuntime.publicView(repo.state)
    val claim = claimView.getJSONArray("records").getJSONObject(0).getString("text")
    assertTrue(claim.contains("Trần Văn Duy"))
    assertTrue(claim.contains("chiếc khóa màu xanh"))
    assertFalse(claim.contains("= xanh"))
    assertTrue(claimView.getString("temporaryInference").contains("vật chứng độc lập"))
    apply(core, repo, "room:hall")
    apply(core, repo, "room:lab")
    apply(core, repo, "paper")
    val evidence = MiniMapRuntime.publicView(repo.state)
    val confirmedClue = evidence.getJSONArray("records").getJSONObject(1)
    assertTrue(confirmedClue.getString("text").contains("Mảnh hồ sơ"))
    assertEquals("CLUE", confirmedClue.getString("kind"))
    assertTrue(confirmedClue.getBoolean("confirmed"))
    assertEquals("Đọc hồ sơ", confirmedClue.getString("label"))
    // CLAIM is witness testimony, not an authoritative named physical clue.
    assertFalse(evidence.getJSONArray("records").getJSONObject(0).has("label"))
    assertTrue(evidence.getString("temporaryInference").contains("so sánh"))
    assertFalse(evidence.toString().contains("facts"))
    // Saved technical receipts are re-rendered from script without mutating game state.
    val stale = MiniMapRuntime.progress(repo.state)
    stale.getJSONArray("records").getJSONObject(0).put("text", "lech_the_ky")
    val staleSave = repo.state.copy(world = repo.state.world + (MiniMapRuntime.STATE to stale.toString()))
    assertFalse(MiniMapRuntime.publicView(staleSave).toString().contains("lech_the_ky"))
    assertEquals("lech_the_ky", MiniMapRuntime.progress(staleSave)
      .getJSONArray("records").getJSONObject(0).getString("text"))
  }

  @Test fun newAuthoredObservationsMustBeConcreteAndDistinct() {
    val scene = script()
    scene.getJSONArray("interactions").getJSONObject(0).put("observation", "le_ch_the_ky")
    assertEquals("scene_observation_quality", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(scene, "0", 1)
    }.message)
    val duplicate = script()
    val line = "Một dấu vết hình mũi tên bị che bên dưới lớp giấy dán tường ẩm mốc."
    duplicate.getJSONArray("interactions").getJSONObject(0).put("observation", line)
    duplicate.getJSONArray("interactions").getJSONObject(1).put("observation", line)
    assertEquals("scene_duplicate_observation", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(duplicate, "0", 1)
    }.message)
  }

  @Test fun graphClaimAndProofValidationRejectInvalidScripts() {
    assertEquals("investigation",MiniMapScenario.validate(script(),"0",1).getString("id"))
    val onlyThree=script();onlyThree.getJSONArray("rooms").remove(3)
    onlyThree.getJSONArray("rooms").getJSONObject(0).put("adjacentRoomIds",JSONArray().put("lab").put("witness"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(onlyThree,"0",1) }
    val disconnected=script();disconnected.getJSONArray("rooms").getJSONObject(1).put("adjacentRoomIds",JSONArray().put("witness"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(disconnected,"0",1) }
    // Regression: Gemini uses private snake_case codes for answer matching.
    // They must be accepted, not accidentally treated as display prose.
    val machineCodes=script()
    machineCodes.getJSONArray("facts").getJSONObject(0)
      .put("value","mau_khoa_do").put("values",JSONArray().put("mau_khoa_do").put("xanh"))
    machineCodes.getJSONArray("interactions").getJSONObject(3).getJSONObject("effect").put("value","mau_khoa_do")
    machineCodes.getJSONArray("interactions").getJSONObject(3).put("label","Chọn khóa có dấu đỏ")
    assertEquals("investigation",MiniMapScenario.validate(machineCodes,"0",1).getString("id"))
    val unsafeControl=script()
    val controlValue = "mau" + 1.toChar() + "do"
    // Keep the correct answer printable. Only an alternate choice carries a
    // control byte, so this specifically exercises the fact-domain check.
    unsafeControl.getJSONArray("facts").getJSONObject(0)
      .put("values",JSONArray().put("đỏ").put("xanh").put(controlValue))
    assertEquals("fact_human_value", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(unsafeControl,"0",1)
    }.message)
    val lying=script();lying.getJSONArray("interactions").getJSONObject(2).getJSONObject("effect").put("value","đỏ")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(lying,"0",1) }
    val impossible=script();impossible.getJSONArray("interactions").getJSONObject(0).put("requires",JSONObject("{\"op\":\"HAS\",\"id\":\"answer_red\"}"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(impossible,"0",1) }
    val guessable=script()
    guessable.getJSONArray("interactions").getJSONObject(3).put("requires",JSONObject("""{"op":"TRUE"}"""))
    assertEquals("answer_without_observed_proof", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(guessable,"0",1)
    }.message)
    val injected=script().put("eval","grantItem()")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(injected,"0",1) }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.parse("[".repeat(13)+"]".repeat(13)) }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.parse(" ".repeat(48001)) }
  }
  private fun noNpcScript(): JSONObject {
    val scene = script().put("npcs", JSONArray())
    val interactions = scene.getJSONArray("interactions")
    for (i in interactions.length() - 1 downTo 0) {
      if (interactions.getJSONObject(i).getString("id") == "guard") interactions.remove(i)
    }
    MiniMapScenario.objects(interactions).filter {
      it.getString("id") in setOf("answer_red", "answer_blue")
    }.forEach { it.put("requires", JSONObject("""{"op":"HAS","id":"mark"}""")) }
    return scene
  }

  @Test fun puzzlesWithoutNpcOrClaimsAreValidAndPlayable() {
    val scene = MiniMapScenario.validate(noNpcScript(), "0", 1)
    assertEquals(0, scene.getJSONArray("npcs").length())
    assertTrue(MiniMapScenario.objects(scene.getJSONArray("interactions")).all { it.getString("kind") == "SEARCH" })
    val base = state()
    val repo = MemorySave(base.copy(world = base.world +
      (MiniMapRuntime.scriptKey(MiniMapRuntime.progress(base)) to scene.toString())))
    val core = facade(repo)
    apply(core, repo, "room:lab"); apply(core, repo, "paper")
    apply(core, repo, "room:hall"); apply(core, repo, "mark")
    apply(core, repo, "answer_red"); apply(core, repo, "answer_left")
    assertTrue(MiniMapRuntime.complete(repo.state))
    assertEquals(0, MiniMapRuntime.publicView(repo.state).getJSONArray("npcs").length())
    val next = MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      .first { it.getString("targetId") == "next" }
    assertEquals("MOVE", next.getString("kind"))
    assertEquals("Tiến vào khu vực kế tiếp.", next.getString("label"))
    // The transition is still gated by the real exit room, not by client styling.
    val outside = MiniMapRuntime.progress(repo.state).put("roomId", "lab")
    val outsideState = repo.state.copy(world = repo.state.world + (MiniMapRuntime.STATE to outside.toString()))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(outsideState))
      .any { it.getString("targetId") == "next" })
  }

  @Test fun multipleNpcHaveStableIdentityRoomAndActualTalkActions() {
    val scene = script()
    scene.getJSONArray("npcs").put(JSONObject()
      .put("id", "technician").put("name", "Lê Hoàng Phúc").put("originPeriod", "thập niên 1990")
      .put("role", "Người trông phòng máy")
      .put("purpose", "Đối chiếu hướng van với vật chứng")
      .put("description", "Đứng bên bộ điều khiển.")
      .put("roomId", "lab"))
    scene.getJSONArray("interactions").put(JSONObject()
      .put("id", "tech_claim").put("kind", "TALK").put("npcId", "technician")
      .put("roomId", "lab").put("label", "Hỏi về hướng van")
      .put("requires", JSONObject("""{"op":"TRUE"}"""))
      .put("effect", JSONObject("""{"type":"CLAIM","factId":"valve","value":"trái","policy":"TRUTH"}""")))
    // Useful optional dialogue is valid; a character need not be an unlock key.
    val validated = MiniMapScenario.validate(scene, "0", 1)
    val base = state()
    val repo = MemorySave(base.copy(world = base.world +
      (MiniMapRuntime.scriptKey(MiniMapRuntime.progress(base)) to validated.toString())))
    val core = facade(repo)
    val witnessView = MiniMapRuntime.publicView(repo.state)
    assertEquals(0, witnessView.getJSONArray("npcs").length())
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any { it.getString("targetId") == "guard" })
    apply(core, repo, "room:witness")
    assertEquals("guard_npc", MiniMapRuntime.publicView(repo.state).getJSONArray("npcs").getJSONObject(0).getString("id"))
    apply(core, repo, "guard")
    assertTrue(MiniMapRuntime.progress(repo.state).getJSONArray("records").length() > 0)
    apply(core, repo, "room:hall"); apply(core, repo, "room:lab")
    val labView = MiniMapRuntime.publicView(repo.state)
    assertEquals("technician", labView.getJSONArray("npcs").getJSONObject(0).getString("id"))
    assertFalse(labView.toString().contains("Đối chiếu hướng van với vật chứng"))
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any {
      it.getString("targetId") == "tech_claim" && it.getString("label").contains("Lê Hoàng Phúc")
    })
    apply(core, repo, "tech_claim")
    val badRoom = script()
    badRoom.getJSONArray("interactions").getJSONObject(2).put("roomId", "lab")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(badRoom, "0", 1) }
    val orphan = script()
    orphan.getJSONArray("npcs").getJSONObject(0).put("id", "someone_else")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(orphan, "0", 1) }
  }

  @Test fun twelveNpcIsTotalScriptCapacityNotSimultaneousRoomPopulation() {
    val scene = script()
    // Twelve stable characters spread among rooms; each has real TALK content.
    val rooms = listOf("lab", "alcove", "witness")
    val names = listOf("Étienne Moreau", "Amelia Hart", "Nguyễn An", "Haruki Tanaka",
      "Carlos Rivera", "Fatima Zahra", "Sophie Laurent", "Sanae Mori",
      "Joseph Okeke", "Elena Popescu", "Amina Diallo")
    val periods = listOf("cuối thế kỷ XIX", "thập niên 1920", "thập niên 1970", "thập niên 1990", "đầu thế kỷ XXI")
    for (i in 1..11) {
      val id = "witness_$i"
      val roomId = rooms[(i - 1) % rooms.size]
      scene.getJSONArray("npcs").put(JSONObject()
        .put("id", id).put("name", names[i - 1]).put("originPeriod", periods[(i - 1) % periods.size])
        .put("role", "Người nắm thông tin").put("purpose", "Đưa lời khai có thể đối chiếu")
        .put("description", "Một nhân chứng đang quan sát dấu vết.")
        .put("roomId", roomId))
      scene.getJSONArray("interactions").put(JSONObject()
        .put("id", "talk_$i").put("kind", "TALK").put("npcId", id)
        .put("roomId", roomId).put("label", "Hỏi về ký hiệu $i")
        .put("requires", JSONObject("""{"op":"TRUE"}"""))
        .put("effect", JSONObject("""{"type":"CLAIM","factId":"color","value":"đỏ","policy":"TRUTH"}""")))
    }
    val valid = MiniMapScenario.validate(scene, "0", 1)
    assertEquals(12, valid.getJSONArray("npcs").length())
    val base = state()
    val scriptKey = MiniMapRuntime.scriptKey(MiniMapRuntime.progress(base))
    val withCast = base.copy(world = base.world + (scriptKey to valid.toString()))
    val shown = mutableSetOf<String>()
    for (roomId in listOf("hall", "lab", "witness", "alcove")) {
      val p = MiniMapRuntime.progress(withCast).put("roomId", roomId)
      val inRoom = withCast.copy(world = withCast.world + (MiniMapRuntime.STATE to p.toString()))
      val visible = MiniMapScenario.objects(MiniMapRuntime.publicView(inRoom).getJSONArray("npcs"))
      assertTrue("Room $roomId must not show all NPCs", visible.size < 12)
      assertTrue(visible.all { it.getString("id") !in shown })
      shown += visible.map { it.getString("id") }
    }
    assertEquals(12, shown.size)
    // A thirteenth NPC still violates the global mini-map cap, independent of room.
    scene.getJSONArray("npcs").put(JSONObject()
      .put("id", "extra_witness").put("name", "Mariam Saleh").put("originPeriod", "thập niên 1980")
      .put("role", "Người quan sát").put("purpose", "Đối chiếu dấu vết")
      .put("description", "Đứng cạnh máy ghi âm.").put("roomId", "lab"))
    val rejected = assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(scene, "0", 1)
    }
    assertEquals("scenario_count", rejected.message)
  }

  @Test fun npcRequiresProperUniqueNamesAndPeriodBackstory() {
    val unnamed = script()
    unnamed.getJSONArray("npcs").getJSONObject(0).put("name", "Người gác 1")
    assertEquals("npc_name_required", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(unnamed, "0", 1)
    }.message)
    val missingPeriod = script()
    missingPeriod.getJSONArray("npcs").getJSONObject(0).remove("originPeriod")
    assertEquals("scenario_fields", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(missingPeriod, "0", 1)
    }.message)

    val scene = script()
    scene.getJSONArray("npcs").put(JSONObject()
      .put("id", "visitor").put("name", "Clara Voss").put("originPeriod", "cuối thế kỷ XIX")
      .put("role", "Người đưa tin").put("purpose", "Nêu một lời khai về đường đi")
      .put("description", "Người phụ nữ mặc áo khoác dài.").put("roomId", "lab"))
    scene.getJSONArray("interactions").put(JSONObject()
      .put("id", "visitor_claim").put("kind", "TALK").put("npcId", "visitor")
      .put("roomId", "lab").put("label", "Hỏi về màu khóa")
      .put("requires", JSONObject("""{"op":"TRUE"}"""))
      .put("effect", JSONObject("""{"type":"CLAIM","factId":"color","value":"đỏ","policy":"TRUTH"}""")))
    assertEquals(2, MiniMapScenario.validate(scene, "0", 1).getJSONArray("npcs").length())
    val duplicateName = MiniMapScenario.copy(scene)
    duplicateName.getJSONArray("npcs").getJSONObject(1).put("name", "  TRẦN VĂN DUY  ")
    assertEquals("npc_duplicate_name", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(duplicateName, "0", 1)
    }.message)
    val samePeriod = MiniMapScenario.copy(scene)
    samePeriod.getJSONArray("npcs").getJSONObject(1).put("originPeriod", "thập niên 1940")
    assertEquals("npc_period_diversity", assertThrows(IllegalArgumentException::class.java) {
      MiniMapScenario.validate(samePeriod, "0", 1)
    }.message)

    val base = state()
    val p = MiniMapRuntime.progress(base).put("roomId", "lab")
    val inRoom = base.copy(world = base.world + (MiniMapRuntime.STATE to p.toString()) +
      (MiniMapRuntime.scriptKey(p) to MiniMapScenario.validate(scene, "0", 1).toString()))
    val visible = MiniMapRuntime.publicView(inRoom).getJSONArray("npcs")
    assertEquals(1, visible.length())
    assertEquals("Clara Voss", visible.getJSONObject(0).getString("name"))
    assertEquals("cuối thế kỷ XIX", visible.getJSONObject(0).getString("originPeriod"))
    assertFalse(visible.toString().contains("Trần Văn Duy"))
  }

  @Test fun snapshotIdentityFollowsCommittedRoomAndRejectsClientSpoofing() {
    val initial = state()
    val progress = MiniMapRuntime.progress(initial)
    val scene = JSONObject(initial.world.getValue(MiniMapRuntime.scriptKey(progress)))
    scene.getJSONArray("rooms").getJSONObject(1).put("environmentId", "phong_chop")
    val repo = MemorySave(initial.copy(world = initial.world + (MiniMapRuntime.scriptKey(progress) to scene.toString())))
    val core = facade(repo)
    val before = MiniMapRuntime.publicView(repo.state)
    assertEquals("phong_moc", before.getJSONObject("environment").getString("id"))
    assertEquals("1", before.getString("zoneId"))
    assertEquals("1.1", before.getString("subzoneId"))
    val command = choice(repo.state, "room:lab")
    core.beginSceneAction(command)
    assertEquals("phong_moc", MiniMapRuntime.publicView(repo.state).getJSONObject("environment").getString("id"))
    assertEquals("phong_chop", JSONObject(core.sceneNarrationContext()).getJSONObject("room").getString("environmentId"))
    core.commitSceneAction(JSONObject(command).getString("actionId"), "Cao Minh bước vào phòng.", "{}")
    val after = MiniMapRuntime.publicView(repo.state)
    assertEquals("phong_chop", after.getJSONObject("environment").getString("id"))
    assertEquals("2.1", after.getString("subzoneId"))
    val spoof = JSONObject().put("scene", JSONObject().put("environment", JSONObject().put("id", "khe_toi")))
    assertEquals("phong_chop", CoreClientProjection.project(spoof, repo.state).getJSONObject("scene").getJSONObject("environment").getString("id"))
    val restored = GameStateCodec.decode(GameStateCodec.encode(repo.state))
    assertEquals("phong_chop", MiniMapRuntime.publicView(restored).getJSONObject("environment").getString("id"))
  }

  @Test fun publicViewNeverContainsAnswerKeyOrTruthPolicy() {
    val view=MiniMapRuntime.publicView(state()).toString()
    assertFalse(view.contains("facts"));assertFalse(view.contains("LIE"));assertFalse(view.contains("factId"));assertFalse(view.contains("answer_red"))
    val tampered=JSONObject().put("scene",script()).put("inventory",JSONArray().put("fake"))
    val projected=CoreClientProjection.project(tampered,state()).getJSONObject("scene")
    assertFalse(projected.has("facts"));assertFalse(projected.has("goal"))
  }
  @Test fun pendingSurvivesRestartAndRetryWithoutRerollThenCommitsOnce() {
    val repo=MemorySave(state()); val rng=CountingRng(); val core=facade(repo,rng)
    val command=choice(repo.state,"room:lab")
    core.beginSceneAction(command); val calls=rng.calls;assertTrue(calls>0)
    assertEquals("hall",MiniMapRuntime.progress(repo.state).getString("roomId"));assertEquals(1,CoreClientProjection.turnIndex(repo.state))
    val retry=facade(repo,rng);retry.beginSceneAction(command);assertEquals(calls,rng.calls)
    assertFalse(retry.abortAction("provider_timeout"));assertTrue(repo.state.world.containsKey(MiniMapRuntime.PENDING))
    assertThrows(IllegalArgumentException::class.java) { retry.commitSceneAction(JSONObject(command).getString("actionId"),"", "{}") }
    retry.commitSceneAction(JSONObject(command).getString("actionId"),"Cao Minh bước vào kho hồ sơ.","{}")
    assertEquals("lab",MiniMapRuntime.progress(repo.state).getString("roomId"));assertEquals(2,CoreClientProjection.turnIndex(repo.state));assertEquals(1L,repo.state.time.elapsedSubjectiveMinutes)
    val saved=GameStateCodec.encode(repo.state)
    retry.beginSceneAction(command);retry.commitSceneAction(JSONObject(command).getString("actionId"),"Bản kể lặp.","{}")
    assertEquals(calls,rng.calls);assertEquals(saved,GameStateCodec.encode(repo.state))
    assertThrows(IllegalArgumentException::class.java) { retry.beginSceneAction(choice(state(),"room:witness")) }
  }
  @Test fun popupTalkInvalidAndRepeatedSearchHaveCorrectRollGates() {
    val repo=MemorySave(state());val rng=CountingRng();val core=facade(repo,rng)
    repeat(5) { MiniMapRuntime.publicView(repo.state) };assertEquals(0,rng.calls)
    val invalid=JSONObject(choice(repo.state,"room:lab")).put("targetId","room:secret")
    assertThrows(IllegalStateException::class.java) { core.beginSceneAction(invalid.toString()) };assertEquals(0,rng.calls)
    apply(core,repo,"room:witness");val before=rng.calls
    apply(core,repo,"guard");assertEquals(before,rng.calls)
    apply(core,repo,"room:hall");apply(core,repo,"room:lab");apply(core,repo,"paper")
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any { it.getString("targetId")=="paper" })
  }
  @Test fun multiStepPuzzleAllowsBacktrackingWrongHypothesisAndOneTimeEvidence() {
    val repo=MemorySave(state());val core=facade(repo)
    apply(core,repo,"room:witness");apply(core,repo,"guard");apply(core,repo,"room:hall")
    apply(core,repo,"room:lab");apply(core,repo,"paper");apply(core,repo,"room:hall");apply(core,repo,"mark")
    apply(core,repo,"answer_blue");assertFalse(MiniMapRuntime.complete(repo.state))
    apply(core,repo,"answer_red");apply(core,repo,"answer_left");assertTrue(MiniMapRuntime.complete(repo.state))
    val records=MiniMapRuntime.progress(repo.state).getJSONArray("records")
    assertEquals(1,MiniMapScenario.objects(records).count { it.getString("id")=="mark" })
    assertTrue(repo.state.inventories.getValue(CAO_MINH_ID).items.isEmpty())
    assertTrue(JSONObject(core.saveManualCheckpoint(core.currentClientState("{}"))).getBoolean("ok"))
    val frozen=repo.state.world[MiniMapRuntime.scriptKey(MiniMapRuntime.progress(repo.state))]
    apply(core,repo,"next");assertEquals(2,MiniMapRuntime.progress(repo.state).getInt("index"))
    assertTrue(JSONObject(core.loadManualCheckpoint()).getBoolean("ok"))
    assertEquals(1,MiniMapRuntime.progress(repo.state).getInt("index"));assertEquals(frozen,repo.state.world[MiniMapRuntime.scriptKey(MiniMapRuntime.progress(repo.state))])
  }
  @Test fun finishingMiniMapAtomicallyDropsPuzzleScriptEvidenceAndPresentationHistory() {
    val base = solvedState(0,1)
    val p = MiniMapRuntime.progress(base).put("records", JSONArray().put(
      JSONObject().put("id","secret").put("kind","CLUE").put("text","Mật mã cũ không được giữ")))
    val repo = MemorySave(base.copy(world=base.world+(MiniMapRuntime.STATE to p.toString())))
    val core = facade(repo)
    core.acceptSceneGeneration(MiniMapRuntime.token(repo.state),outline("0").toString())
    val oldScriptKey = MiniMapRuntime.scriptKey(MiniMapRuntime.progress(repo.state))
    val command = choice(repo.state,"next")
    core.beginSceneAction(command)
    assertTrue(repo.state.world.containsKey(oldScriptKey))
    assertEquals(1,MiniMapRuntime.progress(repo.state).getInt("index"))
    core.commitSceneAction(JSONObject(command).getString("actionId"),"Rời bản đồ cũ.","{}")
    assertFalse(repo.state.world.containsKey(oldScriptKey))
    val after = MiniMapRuntime.progress(repo.state)
    assertEquals(2,after.getInt("index"))
    assertEquals(1,after.getJSONArray("completed").getInt(0))
    assertEquals(0,after.getJSONArray("accepted").length())
    assertEquals(0,after.getJSONArray("attempted").length())
    assertEquals(0,after.getJSONArray("records").length())
    assertFalse(after.has("history"))
    assertFalse(after.has("arrival"))
    val request = JSONObject(core.sceneGenerationRequest())
    assertEquals("script",request.getString("kind"))
    assertFalse(request.has("previousOutcome"))
    assertFalse(request.has("dependencyOutcomes"))
    assertFalse(request.toString().contains("Mật mã cũ"))
    val client = JSONObject().put("log",JSONArray()
      .put(JSONObject().put("role","gm").put("text","Mật mã cũ không được giữ").put("sceneMap","0:1"))
      .put(JSONObject().put("role","gm").put("text","Câu chuyện Canon giữ nguyên")))
    val projected=JSONObject(core.currentClientState(client.toString()))
    val displayed=projected.getJSONArray("log").toString()
    assertFalse(displayed.contains("Mật mã cũ"))
    assertTrue(displayed.contains("Câu chuyện Canon giữ nguyên"))
    assertTrue(JSONObject(core.saveManualCheckpoint(projected.toString())).getBoolean("ok"))
    val reloaded=facade(repo).loadManualCheckpoint()
    assertTrue(JSONObject(reloaded).getBoolean("ok"))
    assertFalse(repo.state.world.containsKey(oldScriptKey))
    assertEquals(0,MiniMapRuntime.progress(repo.state).getJSONArray("records").length())
  }

  @Test fun completingEighthMiniMapKeepsScriptAndOutlineWithoutEndingGame() {
    val base=solvedState(0,8)
    val p=MiniMapRuntime.progress(base)
    val accepted=MiniMapScenario.strings(p.getJSONArray("accepted")).filter { it != "answer_left" }
    p.put("accepted",JSONArray(accepted))
    val repo=MemorySave(base.copy(world=base.world+(MiniMapRuntime.STATE to p.toString())))
    val core=facade(repo)
    core.acceptSceneGeneration(MiniMapRuntime.token(repo.state),outline("0").toString())
    val scriptKey=MiniMapRuntime.scriptKey(p);val outlineKey=MiniMapRuntime.outlineKey(p)
    assertFalse(MiniMapRuntime.complete(repo.state))
    apply(core,repo,"answer_left")
    assertTrue(MiniMapRuntime.complete(repo.state))
    assertEquals("0",MiniMapRuntime.progress(repo.state).getString("nodeId"))
    assertEquals(8,MiniMapRuntime.progress(repo.state).getInt("index"))
    assertTrue(repo.state.world.containsKey(scriptKey))
    assertTrue(repo.state.world.containsKey(outlineKey))
    assertFalse(MiniMapRuntime.publicView(repo.state).getBoolean("awaitingEnding"))
    assertFalse(MiniMapRuntime.publicView(repo.state).getBoolean("nodeExitUnlocked"))
    assertFalse(MiniMapRuntime.publicView(repo.state).getBoolean("generationNeeded"))
    assertEquals("ready",JSONObject(core.sceneGenerationRequest()).getString("kind"))
    val actions=MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
    assertFalse(actions.any { it.getString("targetId")=="next" || it.getString("targetId").startsWith("node:") })
    assertTrue(JSONObject(core.saveManualCheckpoint(core.currentClientState("{}"))).getBoolean("ok"))
    assertTrue(JSONObject(core.loadManualCheckpoint()).getBoolean("ok"))
    assertEquals(8,MiniMapRuntime.progress(repo.state).getInt("index"))
    assertTrue(repo.state.world.containsKey(scriptKey))
  }
  @Test fun typedSpawnGateDoesNotConsumeRngOnTalkOrCombat() {
    var calls=0;val rng:(Int)->Int={ calls++;9999 }
    val s=state()
    SpawnMechanism.rollSceneSpawns(s,"TALK",rng);assertEquals(0,calls)
    SpawnMechanism.rollSceneSpawns(s,"SEARCH",rng);assertTrue(calls>0)
    calls=0
    SpawnMechanism.rollSceneSpawns(CombatRuntime.prepareEncounter(s,"hound","e1"),"MOVE",rng);assertEquals(0,calls)
  }
  @Test fun levelZeroOutlineAndScriptsUseExactlyEightSegments() {
    val valid=MiniMapScenario.validateOutline(outline("0"),"0")
    assertEquals(8,valid.getJSONArray("entries").length())
    assertEquals("0",MiniMapScenario.validate(script(0,8), "0", 8).getString("nodeAnchor"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(script(0,9), "0", 9) }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validateOutline(
      outline("0").put("entries",JSONArray().apply {
        (1..9).forEach { put(JSONObject().put("index",it).put("nodeAnchor","0")
          .put("beat","Điều tra").put("objective","Đối chiếu dữ kiện").put("dependencyRefs",JSONArray())) }
      }),"0") }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validateOutline(outline("0.1"),"0.1") }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(script(0,1,"0.1"),"0.1",1) }
    val swapped=outline("0").apply { getJSONArray("entries").getJSONObject(2).put("nodeAnchor","0.1") }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validateOutline(swapped,"0") }
    // Canon metadata remains available without enabling that node's Explorer.
    assertNotNull(LevelRouteRegistry.node("0.1"))
    assertEquals(listOf("0.1"),LevelRouteRegistry.exits("0"))
  }
  private fun outline(nodeId: String) = JSONObject().put("schemaVersion",5)
    .put("levelNumber",LevelRouteRegistry.node(nodeId).parent).put("nodeAnchor",nodeId).put("entries",JSONArray().apply {
      (1..8).forEach { put(JSONObject().put("index",it).put("nodeAnchor",nodeId)
        .put("beat","Điều tra").put("objective","Đối chiếu dữ kiện").put("dependencyRefs",JSONArray())) }
    })
  @Test fun acceptedScriptIsFrozenAndBadGenerationCannotModifySave() {
    var initial=state()
    val p=MiniMapRuntime.progress(initial)
    initial=initial.copy(world=initial.world - MiniMapRuntime.scriptKey(p))
    val repo=MemorySave(initial);val core=facade(repo)
    val token=MiniMapRuntime.token(repo.state)
    assertThrows(IllegalArgumentException::class.java) { core.acceptSceneGeneration(token,script().toString()) }
    assertNull(MiniMapRuntime.script(repo.state))
    core.acceptSceneGeneration(token,outline("0").toString())
    core.acceptSceneGeneration(token,script().toString())
    val frozen=repo.state.world[MiniMapRuntime.scriptKey(p)]
    core.acceptSceneGeneration(token,script().apply { getJSONArray("facts").getJSONObject(0).put("value","xanh") }.toString())
    assertEquals(frozen,repo.state.world[MiniMapRuntime.scriptKey(p)])
    val request=JSONObject(core.sceneGenerationRequest());assertEquals("ready",request.getString("kind"))
    assertFalse(request.has("facts"));assertFalse(request.has("rooms"))
  }
  @Test fun entityNarrationUsesStagedRoomAndConfirmationWaitsForCommit() {
    val repo=MemorySave(state());val rng=CountingRng(0);val core=facade(repo,rng)
    val command=choice(repo.state,"room:lab")
    core.beginSceneAction(command)
    val encounter=CombatRuntime.pending(repo.state)!!
    val event=JSONObject(core.sceneNarrationContext())
    assertEquals("Kho hồ sơ",event.getJSONObject("room").getString("name"))
    assertEquals(encounter.entityKey,event.getJSONObject("encounter").getString("entityKey"))
    assertEquals("hall",MiniMapRuntime.progress(repo.state).getString("roomId"))
    assertFalse(JSONObject(core.currentClientState("{}")).has("pendingEncounter"))
    assertFalse(JSONObject(core.confirmEncounter(core.currentClientState("{}"),encounter.encounterId)).getBoolean("handled"))
    val before=rng.calls;core.beginSceneAction(command);assertEquals(before,rng.calls)
    val view=JSONObject(core.commitSceneAction(JSONObject(command).getString("actionId"),"Cao Minh chạm trán Entity ở kho hồ sơ.","{}"))
    assertEquals("lab",MiniMapRuntime.progress(repo.state).getString("roomId"))
    assertTrue(view.has("pendingEncounter"));assertTrue(MiniMapRuntime.actions(repo.state).length()==0)
    assertEquals("player",view.getJSONArray("log").getJSONObject(0).getString("role"))
    assertEquals("gm",view.getJSONArray("log").getJSONObject(1).getString("role"))
    assertTrue(JSONObject(core.confirmEncounter(view.toString(),encounter.encounterId)).getBoolean("handled"))
    assertNotNull(CombatRuntime.active(repo.state));assertEquals(before,rng.calls)
  }
  private fun solvedState(level: Int, index: Int, nodeId: String = level.toString()): GameState {
    val state=state(level,index,nodeId);val p=MiniMapRuntime.progress(state);val s=MiniMapRuntime.script(state)!!
    p.put("accepted",JSONArray(MiniMapScenario.objects(s.getJSONArray("interactions")).filter { MiniMapScenario.effectText(s,it).second }.map { it.getString("id") }))
    p.put("completed",JSONArray().apply { (1 until index).forEach { put(it) } })
    return state.copy(world=state.world+(MiniMapRuntime.STATE to p.toString()))
  }
  @Test fun eighthMapOffersNoNextOrNodeTransitionAndDoesNotEndGame() {
    val repo=MemorySave(solvedState(0,8))
    val core=facade(repo)
    // Match an actual generated scene: its outline is stored alongside the script.
    core.acceptSceneGeneration(MiniMapRuntime.token(repo.state),outline("0").toString())
    val actions=MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
    assertTrue(MiniMapRuntime.complete(repo.state))
    assertFalse(actions.any { it.getString("targetId") == "next" || it.getString("targetId").startsWith("node:") })
    assertFalse(MiniMapRuntime.publicView(repo.state).getBoolean("awaitingEnding"))
    assertFalse(MiniMapRuntime.publicView(repo.state).getBoolean("nodeExitUnlocked"))
    assertEquals("ready",JSONObject(core.sceneGenerationRequest()).getString("kind"))
    val before=GameStateCodec.encode(repo.state)
    val forged=JSONObject(choice(repo.state,"room:lab")).put("targetId","node:0.1")
    assertThrows(IllegalStateException::class.java) { core.beginSceneAction(forged.toString()) }
    assertEquals(before,GameStateCodec.encode(repo.state))
  }
  @Test fun levelZeroIsTheOnlyNodeAllowedForNewScenarioGeneration() {
    val repo=MemorySave(state())
    val core=facade(repo)
    val old=GameStateCodec.encode(repo.state)
    assertThrows(IllegalArgumentException::class.java) {
      core.acceptSceneGeneration(MiniMapRuntime.token(repo.state),outline("0.1").toString())
    }
    assertEquals(old,GameStateCodec.encode(repo.state))
    val otherProgress=MiniMapRuntime.progress(repo.state)
      .put("nodeId","0.1").put("parent",0).put("roomId","")
    val wrongNode=repo.state.copy(world=repo.state.world+mapOf(
      MiniMapRuntime.STATE to otherProgress.toString(),
      "levelJson" to LevelRouteRegistry.node("0.1").asJson().toString()))
    assertThrows(IllegalArgumentException::class.java) { MiniMapRuntime.generationRequest(wrongNode) }
    assertFalse(MiniMapRuntime.publicView(wrongNode).getBoolean("generationNeeded"))
    assertEquals(0,MiniMapRuntime.actions(wrongNode).length())
  }
  @Test fun firstSevenMiniMapsRejectCrossNodeCommandsAndAdvanceOnlyLocally() {
    for (index in 1..7) {
      val repo=MemorySave(solvedState(0,index));val rng=CountingRng();val core=facade(repo,rng)
      val actions=MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      assertFalse(actions.any { it.getString("targetId").startsWith("node:") })
      val roomCommand=JSONObject(choice(repo.state,"room:lab"))
      for (target in listOf("node:0.1","node:dullness","node:epsilon","node:ls_2")) {
        val forged=MiniMapScenario.copy(roomCommand).put("targetId",target)
        assertThrows(IllegalStateException::class.java) { core.beginSceneAction(forged.toString()) }
      }
      assertEquals(0,rng.calls)
      if (index == 7) {
        apply(core,repo,"next")
        assertEquals("0",MiniMapRuntime.node(repo.state).id)
        assertEquals("0",MiniMapRuntime.progress(repo.state).getString("nodeId"))
        assertEquals(8,MiniMapRuntime.progress(repo.state).getInt("index"))
      }
    }
  }
  @Test fun eighthGoalDoesNotExposeTransitionEvenAtExitRoom() {
    val unfinished=state(0,8)
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(unfinished))
      .any { it.getString("targetId")=="next" || it.getString("targetId").startsWith("node:") })
    var finished=solvedState(0,8)
    assertEquals("hall",MiniMapRuntime.progress(finished).getString("roomId"))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(finished))
      .any { it.getString("targetId")=="next" || it.getString("targetId").startsWith("node:") })
    val p=MiniMapRuntime.progress(finished).put("roomId","lab")
    finished=finished.copy(world=finished.world+(MiniMapRuntime.STATE to p.toString()))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(finished))
      .any { it.getString("targetId")=="next" || it.getString("targetId").startsWith("node:") })
  }
  @Test fun eightLevelZeroKeysAreDistinctAndNoOtherNodeCanGenerate() {
    val validated=MiniMapScenario.validateOutline(outline("0"),"0")
    assertEquals(8,validated.getJSONArray("entries").length())
    val keys=mutableSetOf<String>()
    for (index in 1..8) {
      val p=MiniMapRuntime.progress(state(0,index))
      assertEquals("0",p.getString("nodeId"))
      assertEquals(index,p.getInt("index"))
      assertTrue(keys.add(MiniMapRuntime.scriptKey(p)))
      assertEquals("investigation",MiniMapScenario.validate(script(0,index),"0",index).getString("id"))
    }
    assertEquals(8,keys.size)
    for (node in listOf("0.1","dullness","1","6.1"))
      assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validateOutline(outline(node),node) }
  }
  @Test fun canonicalLevelRoutesRemainMetadataNotExplorerControls() {
    assertEquals(25,LevelRouteRegistry.size())
    assertEquals(listOf("0.1"),LevelRouteRegistry.exits("0"))
    val repo=MemorySave(solvedState(0,8));val core=facade(repo)
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      .none { it.getString("targetId").startsWith("node:") })
    val staleAction=JSONObject(choice(repo.state,"room:lab")).put("targetId","node:0.1")
    assertThrows(IllegalStateException::class.java) { core.beginSceneAction(staleAction.toString()) }
    assertEquals("0",MiniMapRuntime.node(repo.state).id)
  }
  @Test fun luciaSpawnImmediatelyEnablesTypedDialogueWithoutAnyNewRollOrPuzzleReward() {
    val repo=MemorySave(state());var calls=0
    val rng=object: SecureRandom() { override fun nextInt(bound: Int): Int {
      calls++;return if(calls == 2) 0 else bound - 1
    } }
    val core=facade(repo,rng)
    apply(core,repo,"room:lab")
    assertEquals("true",repo.state.characters.getValue(LUCIA_ID).metadata["encountered"])
    val talk="character:lucia:introduce"
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any { it.getString("targetId")==talk && it.getString("kind")=="TALK" })
    val beforeCalls=calls;val accepted=MiniMapRuntime.progress(repo.state).getJSONArray("accepted").toString()
    val inventory=repo.state.inventories;val party=repo.state.party
    val command=choice(repo.state,talk)
    core.beginSceneAction(command)
    val packet=JSONObject(core.sceneNarrationContext())
    assertEquals("lucia",packet.getJSONObject("dialogue").getString("characterId"))
    assertEquals("introduce",packet.getJSONObject("dialogue").getString("topicId"))
    assertEquals(beforeCalls,calls)
    // Failed narration/restart retries the same conversation without rolling.
    facade(repo,rng).beginSceneAction(command);assertEquals(beforeCalls,calls)
    core.commitSceneAction(JSONObject(command).getString("actionId"),"Lucia đáp lại lời giới thiệu.","{}")
    assertEquals(beforeCalls,calls)
    assertEquals(accepted,MiniMapRuntime.progress(repo.state).getJSONArray("accepted").toString())
    assertEquals(inventory,repo.state.inventories);assertEquals(party,repo.state.party)
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any { it.getString("targetId")==talk })
    // Missing/dead/separated actors do not remain selectable merely because of a past encounter.
    for (presence in listOf(CharacterPresence.SEPARATED,CharacterPresence.MISSING,CharacterPresence.DEAD)) {
      val absent=repo.state.copy(characters=repo.state.characters+(LUCIA_ID to repo.state.characters.getValue(LUCIA_ID).copy(presence=presence)))
      assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(absent)).any { it.getString("targetId").startsWith("character:lucia:") })
    }
  }
  @Test fun partyDialogueDoesNotDependOnHavingScriptNpcTopicsOrOldEncounterFlag() {
    val base=state()
    val actor=base.characters.getValue(IRIS_ID).copy(presence=CharacterPresence.ACTIVE)
    val partyState=base.copy(characters=base.characters+(IRIS_ID to actor),party=base.party.copy(memberIds=listOf(CAO_MINH_ID,IRIS_ID)))
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(partyState)).any { it.getString("targetId")=="character:iris:situation" })
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(base)).any { it.getString("targetId").startsWith("character:iris:") })
  }

}
