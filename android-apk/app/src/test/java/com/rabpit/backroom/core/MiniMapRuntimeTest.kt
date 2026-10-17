package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class MiniMapRuntimeTest {
  private fun script(level: Int = 0, index: Int = 1, nodeId: String = level.toString()) = MiniMapScenario.parse("""
    {"schemaVersion":3,"id":"investigation","levelNumber":$level,"index":$index,"nodeAnchor":"${nodeId}",
    "objective":"Đối chiếu dấu hiệu và lời khai để xác định màu khóa, hướng van.","entryRoomId":"hall","exitRoomId":"hall",
    "rooms":[{"id":"hall","name":"Hành lang","description":"Một bảng điều khiển có hai hàng lựa chọn.","adjacentRoomIds":["lab","witness","alcove"]},
    {"id":"lab","name":"Kho hồ sơ","description":"Tủ hồ sơ và một mảnh giấy.","adjacentRoomIds":["hall"]},
    {"id":"witness","name":"Phòng trực","description":"Một người gác đang chờ.","adjacentRoomIds":["hall"]},
    {"id":"alcove","name":"Hốc tường","description":"Một góc phòng để quan sát.","adjacentRoomIds":["hall"]}],
    "npcs":[{"id":"guard_npc","name":"Người gác","role":"Nhân chứng trong phòng trực",
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
  private fun state(level: Int = 0, index: Int = 1, nodeId: String = level.toString()): GameState {
    val node = LevelRouteRegistry.node(nodeId)
    var state = MiniMapRuntime.initialize(CoreClientProjection.initialize(GameState.initial()).let {
      it.copy(world = it.world + ("levelJson" to node.asJson().toString()))
    })
    val p = MiniMapRuntime.progress(state).put("parent",level).put("index",index).put("roomId","hall")
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
    MiniMapScenario.copy(it).apply { remove("label") }.toString()
  }
  private fun apply(core: GameCoreFacade, repo: MemorySave, target: String) {
    val command = choice(repo.state,target)
    core.beginSceneAction(command)
    core.commitSceneAction(JSONObject(command).getString("actionId"),"Cao Minh quan sát kết quả.","{}")
  }
  @Test fun graphClaimAndProofValidationRejectInvalidScripts() {
    assertEquals("investigation",MiniMapScenario.validate(script(),"0",1).getString("id"))
    val onlyThree=script();onlyThree.getJSONArray("rooms").remove(3)
    onlyThree.getJSONArray("rooms").getJSONObject(0).put("adjacentRoomIds",JSONArray().put("lab").put("witness"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(onlyThree,"0",1) }
    val disconnected=script();disconnected.getJSONArray("rooms").getJSONObject(1).put("adjacentRoomIds",JSONArray().put("witness"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(disconnected,"0",1) }
    val lying=script();lying.getJSONArray("interactions").getJSONObject(2).getJSONObject("effect").put("value","đỏ")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(lying,"0",1) }
    val impossible=script();impossible.getJSONArray("interactions").getJSONObject(0).put("requires",JSONObject("{\"op\":\"HAS\",\"id\":\"answer_red\"}"))
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(impossible,"0",1) }
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
    assertTrue(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).any {
      it.getString("targetId") == "next"
    })
  }

  @Test fun multipleNpcHaveStableIdentityRoomAndActualTalkActions() {
    val scene = script()
    scene.getJSONArray("npcs").put(JSONObject()
      .put("id", "technician").put("name", "Người kỹ thuật")
      .put("role", "Người trông phòng máy")
      .put("purpose", "Đối chiếu hướng van với vật chứng")
      .put("description", "Đứng bên bộ điều khiển.")
      .put("roomId", "lab"))
    scene.getJSONArray("interactions").put(JSONObject()
      .put("id", "tech_claim").put("kind", "TALK").put("npcId", "technician")
      .put("roomId", "lab").put("label", "Hỏi về hướng van")
      .put("requires", JSONObject("""{"op":"TRUE"}"""))
      .put("effect", JSONObject("""{"type":"CLAIM","factId":"valve","value":"trái","policy":"TRUTH"}""")))
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
      it.getString("targetId") == "tech_claim" && it.getString("label").contains("Người kỹ thuật")
    })
    apply(core, repo, "tech_claim")
    val badRoom = script()
    badRoom.getJSONArray("interactions").getJSONObject(2).put("roomId", "lab")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(badRoom, "0", 1) }
    val orphan = script()
    orphan.getJSONArray("npcs").getJSONObject(0).put("id", "someone_else")
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(orphan, "0", 1) }
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
  @Test fun typedSpawnGateDoesNotConsumeRngOnTalkOrCombat() {
    var calls=0;val rng:(Int)->Int={ calls++;9999 }
    val s=state()
    SpawnMechanism.rollSceneSpawns(s,"TALK",rng);assertEquals(0,calls)
    SpawnMechanism.rollSceneSpawns(s,"SEARCH",rng);assertTrue(calls>0)
    calls=0
    SpawnMechanism.rollSceneSpawns(CombatRuntime.prepareEncounter(s,"hound","e1"),"MOVE",rng);assertEquals(0,calls)
  }
  @Test fun nodeOutlineNeverChangesCanonicalAnchorInsideItsTenMaps() {
    for (id in listOf("0", "0.1", "dullness", "6", "6.1")) {
      assertEquals(id,MiniMapScenario.validateOutline(outline(id),id).getString("nodeAnchor"))
      assertEquals(id,MiniMapScenario.validate(script(LevelRouteRegistry.node(id).parent,3,id),id,3).getString("nodeAnchor"))
    }
    val swapped=outline("0").apply { getJSONArray("entries").getJSONObject(2).put("nodeAnchor","0.1") }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validateOutline(swapped,"0") }
    assertThrows(IllegalArgumentException::class.java) { MiniMapScenario.validate(script(0,1,"0.1"),"0",1) }
    assertTrue(LevelRouteRegistry.exits("6").none { LevelRouteRegistry.node(it).parent==7 })
    assertEquals(setOf("4","6"),LevelRouteRegistry.exits("3").toSet())
  }
  private fun outline(nodeId: String) = JSONObject().put("schemaVersion",3)
    .put("levelNumber",LevelRouteRegistry.node(nodeId).parent).put("nodeAnchor",nodeId).put("entries",JSONArray().apply {
      (1..10).forEach { put(JSONObject().put("index",it).put("nodeAnchor",nodeId).put("beat","Điều tra").put("objective","Đối chiếu dữ kiện").put("dependencyRefs",JSONArray())) }
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
    p.put("completed",JSONArray().apply { (1 until index).forEach { put(JSONObject().put("nodeId",nodeId).put("parent",level).put("index",it).put("objective","Đã giải")) } })
    return state.copy(world=state.world+(MiniMapRuntime.STATE to p.toString()))
  }
  @Test fun tenthMapUnlocksOnlyRegistryEdgesAndLevelSixWaitsForEnding() {
    val repo=MemorySave(solvedState(3,10));val core=facade(repo)
    val exits=MiniMapScenario.objects(MiniMapRuntime.actions(repo.state)).map { it.getString("targetId") }.filter { it.startsWith("node:") }
    assertEquals(setOf("node:4","node:6"),exits.toSet())
    apply(core,repo,"node:6")
    assertEquals(6,MiniMapRuntime.progress(repo.state).getInt("parent"));assertEquals(1,MiniMapRuntime.progress(repo.state).getInt("index"))
    val end=solvedState(6,10)
    assertTrue(MiniMapRuntime.publicView(end).getBoolean("awaitingEnding"))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(end)).any { it.optString("targetId")=="node:7" })
  }
  @Test fun unfinishedNodeExposesOnlyRoomsAndRejectsForgedCrossNodeCommandsWithoutRng() {
    for (index in 1..9) {
      val repo=MemorySave(solvedState(0,index));val rng=CountingRng();val core=facade(repo,rng)
      val actions=MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      assertFalse(actions.any { it.getString("targetId").startsWith("node:") })
      val roomCommand=JSONObject(choice(repo.state,"room:lab"))
      for (target in listOf("node:0.1","node:dullness","node:epsilon","node:ls_2")) {
        val forged=MiniMapScenario.copy(roomCommand).put("targetId",target)
        assertThrows(IllegalStateException::class.java) { core.beginSceneAction(forged.toString()) }
      }
      assertEquals(0,rng.calls)
      if (index == 2) {
        apply(core,repo,"next")
        assertEquals("0",MiniMapRuntime.node(repo.state).id)
        assertEquals("0",MiniMapRuntime.progress(repo.state).getString("nodeId"))
        assertEquals(3,MiniMapRuntime.progress(repo.state).getInt("index"))
      }
    }
  }
  @Test fun nodeExitsRequireTenthGoalAllEarlierMapsAndTheExitRoom() {
    val unfinished=state(0,10)
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(unfinished)).any { it.getString("targetId").startsWith("node:") })
    var finished=solvedState(0,10)
    val targets=MiniMapScenario.objects(MiniMapRuntime.actions(finished)).map { it.getString("targetId") }
    assertEquals(listOf("node:0.1"),targets.filter { it.startsWith("node:") })
    val p=MiniMapRuntime.progress(finished).put("roomId","lab")
    finished=finished.copy(world=finished.world+(MiniMapRuntime.STATE to p.toString()))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(finished)).any { it.getString("targetId").startsWith("node:") })
    p.put("roomId","hall").put("completed",JSONArray())
    finished=finished.copy(world=finished.world+(MiniMapRuntime.STATE to p.toString()))
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(finished)).any { it.getString("targetId").startsWith("node:") })
  }
  @Test fun everyMainSublevelAreaAndSpecialNodeHasItsOwnTenMapOutline() {
    val nodeIds = listOf(
      "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms",
      "dullness", "epsilon", "ls_2", "1", "1.2", "1.5", "traders_vault",
      "base_alpha", "2", "office_space_el3a", "3", "4", "office_market",
      "5", "5.1", "6", "6.1"
    )
    val outlineKeys = mutableSetOf<String>()
    val scriptKeys = mutableSetOf<String>()
    nodeIds.forEach { id ->
      val parent = requireNotNull(LevelRouteRegistry.node(id)).parent
      val validated = MiniMapScenario.validateOutline(outline(id), id)
      assertEquals(id, validated.getString("nodeAnchor"))
      assertEquals(10, validated.getJSONArray("entries").length())
      val map = MiniMapScenario.validate(script(parent, 1, id), id, 1)
      assertEquals(4, map.getJSONArray("rooms").length())
      val progress = MiniMapRuntime.progress(state(parent, 1, id))
      assertEquals(id, progress.getString("nodeId"))
      assertEquals(1, progress.getInt("index"))
      assertTrue(outlineKeys.add(MiniMapRuntime.outlineKey(progress)))
      assertTrue(scriptKeys.add(MiniMapRuntime.scriptKey(progress)))
    }
  }

  @Test fun levelZeroHasTheConfirmedIrreversibleSequenceIncludingThreeAreas() {
    val chain = listOf("0", "0.1", "0.2", "0.5", "0.7",
      "dullness", "epsilon", "ls_2", "manila_room", "the_torment", "red_rooms", "1")
    for (index in 0 until chain.lastIndex) {
      val from = chain[index]
      val to = chain[index + 1]
      assertEquals(listOf(to), LevelRouteRegistry.exits(from))
      assertFalse(LevelRouteRegistry.canMove(to, from))
    }
    val repo = MemorySave(solvedState(0, 10))
    val core = facade(repo)
    val staleAction = choice(repo.state, "room:lab")
    apply(core, repo, "node:0.1")
    assertEquals("0.1", MiniMapRuntime.node(repo.state).id)
    assertEquals(1, MiniMapRuntime.progress(repo.state).getInt("index"))
    assertNull(MiniMapRuntime.script(repo.state))
    assertThrows(IllegalArgumentException::class.java) { core.beginSceneAction(staleAction) }
    core.acceptSceneGeneration(MiniMapRuntime.token(repo.state), outline("0.1").toString())
    core.acceptSceneGeneration(MiniMapRuntime.token(repo.state), script(0, 1, "0.1").toString())
    assertFalse(MiniMapScenario.objects(MiniMapRuntime.actions(repo.state))
      .any { it.getString("targetId") == "node:0" })
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
