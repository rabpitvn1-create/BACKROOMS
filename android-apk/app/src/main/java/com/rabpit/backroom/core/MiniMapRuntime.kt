package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** All scene data lives in the same Core save/checkpoint as combat, inventory and party. */
object MiniMapRuntime {
  const val STATE = "scene.progress"
  const val PENDING = "scene.pending"
  const val RECEIPT = "scene.receipt"
  fun scriptKey(p: JSONObject) = "scene.script.${p.getString("nodeId")}.${p.getInt("index")}"
  fun outlineKey(p: JSONObject) = "scene.outline.${p.getString("nodeId")}"
  private fun freshProgress(runId: String, nodeId: String): JSONObject {
    val node = requireNotNull(LevelRouteRegistry.node(nodeId)) { "scene_node" }
    return JSONObject().put("runId", runId).put("nodeId", nodeId).put("parent", node.parent)
      .put("index", 1).put("version", 1).put("roomId", "")
      .put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray())
      .put("completed", JSONArray())
  }
  fun progress(state: GameState): JSONObject = state.world[STATE]?.let(::JSONObject)
    ?: freshProgress(state.metadata["story.runId"] ?: UUID.randomUUID().toString(), node(state).id)
  fun initialize(state: GameState): GameState = if (STATE in state.world) state else state.copy(world = state.world + (STATE to progress(state).toString()))
  fun script(state: GameState, p: JSONObject = progress(state)): JSONObject? = state.world[scriptKey(p)]?.let(::JSONObject)
  fun node(state: GameState) = LevelRouteRegistry.requested(JSONObject(state.world.getValue("levelJson")))!!
  fun token(state: GameState): String = progress(state).let { "${it.getString("runId")}:${it.getString("nodeId")}:${it.getInt("index")}" }
  fun blocked(state: GameState) = CombatRuntime.active(state) != null || CombatRuntime.pending(state) != null || PENDING in state.world || ActionRuntime.activeSession(state) != null || state.turn.pending != null
  private fun accepted(p: JSONObject) = MiniMapScenario.strings(p.getJSONArray("accepted")).toSet()
  private fun attempted(p: JSONObject) = MiniMapScenario.strings(p.getJSONArray("attempted")).toSet()
  fun complete(state: GameState): Boolean {
    val p = progress(state)
    if (p.optBoolean("endingComplete", false)) return true
    return script(state)?.let { MiniMapScenario.holds(it.getJSONObject("goal"), accepted(p)) } ?: false
  }
  private fun room(s: JSONObject, p: JSONObject) = MiniMapScenario.objects(s.getJSONArray("rooms")).first { it.getString("id") == p.getString("roomId") }
  private fun authoredNpcs(s: JSONObject, roomId: String): JSONArray = JSONArray().apply {
    MiniMapScenario.objects(s.getJSONArray("npcs")).filter { it.getString("roomId") == roomId }.forEach { npc ->
      put(JSONObject().put("id", npc.getString("id")).put("name", npc.getString("name"))
        .put("originPeriod", npc.getString("originPeriod"))
        .put("role", npc.getString("role")).put("description", npc.getString("description")))
    }
  }
  private val dialogueTopics = linkedMapOf(
    "introduce" to "Giới thiệu và hỏi tên",
    "situation" to "Hỏi về tình hình hiện tại",
    "observations" to "Trao đổi về manh mối đã biết"
  )
  /** Presence is owned by Character/Party Core, independently of generated puzzle NPCs. */
  private fun presentCharacters(state: GameState): List<CharacterState> = state.characters.values.filter {
    it.id != CAO_MINH_ID && it.presence == CharacterPresence.ACTIVE &&
      (it.id in state.party.memberIds || it.metadata["encountered"] == "true")
  }
  fun actions(state: GameState): JSONArray {
    val p = progress(state); val script = script(state) ?: return JSONArray()
    val out = JSONArray(); if (blocked(state)) return out
    fun add(kind: String, target: String, label: String) {
      out.put(JSONObject().put("kind", kind).put("targetId", target).put("label", label)
        .put("sceneVersion", p.getInt("version")).put("roomId", p.getString("roomId"))
        .put("actionId", "${p.getString("runId")}:${p.getString("nodeId")}:${p.getInt("version")}:$kind:$target"))
    }
    val current = node(state)
    require(current.id == p.getString("nodeId") && script.getString("nodeAnchor") == current.id) { "scene_node_mismatch" }
    val r = room(script, p)
    val rooms = MiniMapScenario.objects(script.getJSONArray("rooms"))
    MiniMapScenario.strings(r.getJSONArray("adjacentRoomIds")).forEach { id -> add("MOVE", "room:$id", rooms.first { it.getString("id") == id }.getString("name")) }
    MiniMapScenario.objects(script.getJSONArray("interactions")).filter {
      it.getString("roomId") == p.getString("roomId") && it.getString("id") !in attempted(p) && MiniMapScenario.holds(it.getJSONObject("requires"), accepted(p))
    }.forEach { a ->
      val label = if (a.getString("kind") == "TALK") {
        val npc = MiniMapScenario.objects(script.getJSONArray("npcs")).first { it.getString("id") == a.getString("npcId") }
        "${npc.getString("name")} · ${a.getString("label")}"
      } else a.getString("label")
      add(a.getString("kind"), a.getString("id"), label)
    }
    presentCharacters(state).forEach { character ->
      dialogueTopics.forEach { (topicId, label) -> add("TALK", "character:${character.id}:$topicId", "${character.name} · $label") }
    }
    // The route is one-way: never offer a previous mini-map or Canon node.
    // Cross-node transitions require all ten maps and the real exit room.
    if (complete(state) && r.getString("id") == script.getString("exitRoomId")) {
      if (p.getInt("index") < 10) add("MOVE", "next", "Tiến vào khu vực kế tiếp.")
      else if ((1..9).all { index -> p.getJSONArray("completed").let { entries ->
          (0 until entries.length()).any { entries.getInt(it) == index }
        } }) LevelRouteRegistry.exits(current.id).forEach { destination ->
          add("MOVE", "node:$destination", "Rời ${current.name} → ${LevelRouteRegistry.node(destination).name}")
        }
    }
    return out
  }
  /** Derived from already tried interactions, never from the hidden answer key. */
  private fun temporaryInference(state: GameState, s: JSONObject, p: JSONObject): String {
    if (complete(state)) return "Các suy luận cần thiết đã được xác nhận. Hãy tìm đến lối ra của mini-map."
    val done = attempted(p)
    val tried = MiniMapScenario.objects(s.getJSONArray("interactions")).filter { it.getString("id") in done }
    val claims = tried.filter { it.getJSONObject("effect").getString("type") == "CLAIM" }
    val evidence = tried.filter { it.getJSONObject("effect").getString("type") in setOf("CLUE", "EVIDENCE") }
    val verifiedFactIds = evidence.map { it.getJSONObject("effect").getString("factId") }.toSet()
    val unverified = claims.filter { it.getJSONObject("effect").getString("factId") !in verifiedFactIds }
    val reasoning = when {
      unverified.isNotEmpty() ->
        "Có ${claims.size} lời khai nhưng ít nhất một nhận định vẫn thiếu vật chứng độc lập. Chưa thể coi lời kể của nhân chứng là sự thật."
      claims.isNotEmpty() && evidence.isNotEmpty() ->
        "Đã thu thập lời khai và dấu vết vật lý. Hãy so sánh chúng để loại những giả thuyết không phù hợp."
      evidence.isNotEmpty() ->
        "Đã tìm thấy ${evidence.size} dấu vết. Cần đối chiếu thêm trước khi kết luận."
      claims.isNotEmpty() ->
        "Lời khai chỉ là nhận định từ góc nhìn nhân chứng. Hãy tìm chứng cứ tại hiện trường."
      else -> "Chưa đủ dữ kiện để suy luận. Hãy quan sát những vật thể hoặc dấu vết có thể kiểm chứng."
    }
    val current = p.getString("roomId")
    val remaining = MiniMapScenario.objects(s.getJSONArray("interactions")).filter {
      it.getString("id") !in done && it.getString("kind") == "SEARCH" &&
        it.getJSONObject("effect").getString("type") != "ANSWER" &&
        MiniMapScenario.holds(it.getJSONObject("requires"), accepted(p))
    }
    val here = remaining.firstOrNull { it.getString("roomId") == current }
    val next = when {
      here != null -> " Gợi ý bước tiếp: ${here.getString("label")}."
      else -> {
        val currentRoom = room(s, p)
        val adjacent = MiniMapScenario.strings(currentRoom.getJSONArray("adjacentRoomIds"))
        val nearby = remaining.firstOrNull { it.getString("roomId") in adjacent }
        if (nearby != null) {
          val name = MiniMapScenario.objects(s.getJSONArray("rooms"))
            .first { it.getString("id") == nearby.getString("roomId") }.getString("name")
          " Gợi ý bước tiếp: kiểm tra thêm phòng $name."
        } else if (MiniMapScenario.objects(actions(state)).any {
            it.getString("kind") == "SEARCH" && it.getString("targetId") in
              MiniMapScenario.objects(s.getJSONArray("interactions")).filter { item ->
                item.getJSONObject("effect").getString("type") == "ANSWER"
              }.map { item -> item.getString("id") }
          }) " Gợi ý bước tiếp: đã có lựa chọn đối chiếu khả dụng trong phòng này."
        else " Gợi ý bước tiếp: khám phá các phòng nối với phòng hiện tại để tìm thêm dấu vết."
      }
    }
    return reasoning + next
  }

  private fun publicRecords(s: JSONObject, p: JSONObject): JSONArray {
    val interactions = MiniMapScenario.objects(s.getJSONArray("interactions")).associateBy { it.getString("id") }
    return JSONArray().apply {
      MiniMapScenario.objects(p.getJSONArray("records")).forEach { record ->
        val action = interactions[record.getString("id")]
        val displayed = MiniMapScenario.copy(record)
        if (action != null) displayed.put("text", MiniMapScenario.effectText(s, action).first)
        put(displayed)
      }
    }
  }

  fun publicView(state: GameState): JSONObject {
    val p = progress(state); val s = script(state)
    val out = JSONObject().put("miniIndex", p.getInt("index")).put("parentLevel", p.getInt("parent"))
      .put("nodeId", p.getString("nodeId")).put("nodeTitle", node(state).title())
      .put("sceneVersion", p.getInt("version")).put("availableActions", actions(state))
      .put("records", p.getJSONArray("records")).put("generationNeeded", s == null && !p.optBoolean("endingComplete", false))
      .put("awaitingEnding", p.getString("nodeId") == "6.1" && p.getInt("index") == 10 && complete(state))
      .put("nodeExitUnlocked", p.getInt("index") == 10 && complete(state) &&
        LevelRouteRegistry.exits(p.getString("nodeId")).isNotEmpty())
    if (s != null) {
      out.put("objective", s.getString("objective"))
      // Re-project saved receipts from script data; never expose internal fact enum IDs.
      out.put("records", publicRecords(s, p))
      out.put("temporaryInference", temporaryInference(state, s, p))
      val r = room(s, p)
      out.put("room", JSONObject().put("id", r.getString("id")).put("name", r.getString("name")).put("description", r.getString("description")))
      // Only residents of the current room are public; NPC motivation stays in the script.
      out.put("npcs", authoredNpcs(s, p.getString("roomId")))
      out.put("completed", complete(state))
    }
    state.world[PENDING]?.let { raw -> out.put("pendingActionId", JSONObject(raw).getString("actionId")) }
    return out
  }
  fun generationRequest(state: GameState): JSONObject {
    check(!blocked(state)) { "scene_busy" }
    val p = progress(state); val outline = state.world[outlineKey(p)]
    val kind = if (p.optBoolean("endingComplete", false)) "ready"
      else if (outline == null) "outline" else if (script(state) == null) "script" else "ready"
    return JSONObject().put("kind", kind).put("token", token(state)).put("levelNumber", p.getInt("parent"))
      .put("index", p.getInt("index")).put("nodeAnchor", p.getString("nodeId"))
      .apply {
        if (outline != null && kind == "script") {
          // Only the immutable plan and completed indices cross mini-map boundaries.
          // Never send previous puzzle evidence, NPC statements, answers or records.
          put("outline", JSONObject(outline))
          put("completed", p.getJSONArray("completed"))
        }
      }
  }
  fun acceptGeneration(state: GameState, expected: String, raw: String): GameState {
    check(expected == token(state) && !blocked(state)) { "generation_stale" }
    val p = progress(state)
    check(!p.optBoolean("endingComplete", false)) { "scene_ending_finalized" }
    val data = MiniMapScenario.parse(raw)
    if (state.world[outlineKey(p)] == null) {
      val outline = MiniMapScenario.validateOutline(data, p.getString("nodeId"))
      return state.copy(world = state.world + (outlineKey(p) to outline.toString()))
    }
    if (script(state) != null) return state // A frozen script is never replaced.
    val script = MiniMapScenario.validate(data, p.getString("nodeId"), p.getInt("index"))
    p.put("roomId", script.getString("entryRoomId")).put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray())
    return state.copy(world = state.world + mapOf(scriptKey(p) to script.toString(), STATE to p.toString()))
  }
  data class Staged(val progress: JSONObject, val nodeId: String, val event: JSONObject, val label: String, val discardKeys: Set<String>)
  fun stage(state: GameState, command: JSONObject): Staged {
    var p = progress(state); val s = requireNotNull(script(state)) { "scene_not_ready" }
    require(command.keys().asSequence().toSet() == setOf("actionId", "sceneVersion", "roomId", "kind", "targetId")) { "command_fields" }
    require(command.get("sceneVersion") is Int && command.getInt("sceneVersion") == p.getInt("version") && command.getString("roomId") == p.getString("roomId")) { "scene_stale" }
    val choice = MiniMapScenario.objects(actions(state)).firstOrNull { a ->
      listOf("actionId", "kind", "targetId", "roomId").all { a.getString(it) == command.getString(it) }
    } ?: error("scene_choice_unavailable")
    val kind = command.getString("kind"); val target = command.getString("targetId")
    var nodeId = node(state).id
    val discardKeys = mutableSetOf<String>()
    val label = choice.getString("label")
    var line = label
    var dialogue: JSONObject? = null
    var authoredSpeaker: JSONObject? = null
    if (kind == "MOVE") {
      when {
        target.startsWith("room:") -> p.put("roomId", target.removePrefix("room:"))
        target == "next" -> {
          discardKeys += scriptKey(p)
          p.getJSONArray("completed").put(p.getInt("index"))
          p.put("index", p.getInt("index") + 1).put("roomId", "")
            .put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray())
        }
        target.startsWith("node:") -> {
          nodeId = target.removePrefix("node:")
          // All 10 maps must be finished before this one-way move can be offered.
          // Discard the final puzzle and outline atomically on narration commit.
          discardKeys += scriptKey(p)
          discardKeys += outlineKey(p)
          p = freshProgress(p.getString("runId"), nodeId)
        }
        else -> error("scene_move")
      }
      require(nodeId == node(state).id || LevelRouteRegistry.canMove(node(state).id, nodeId)) { "scene_route" }
    } else if (kind == "TALK" && target.startsWith("character:")) {
      val parts = target.split(':')
      val character = presentCharacters(state).first { it.id == parts[1] }
      dialogue = JSONObject().put("characterId", character.id).put("name", character.name)
        .put("topicId", parts[2]).put("request", dialogueTopics.getValue(parts[2]))
      line = "Người chơi chủ động giao tiếp với ${character.name}: ${dialogueTopics.getValue(parts[2])}. " +
        "Chỉ đối thoại theo Canon và thông tin đã biết; không cấp vật phẩm, gia nhập Party hoặc tiết lộ lời giải mới."
    } else {
      val a = MiniMapScenario.objects(s.getJSONArray("interactions")).first { it.getString("id") == target }
      if (kind == "TALK") {
        val npc = MiniMapScenario.objects(s.getJSONArray("npcs")).first { it.getString("id") == a.getString("npcId") }
        authoredSpeaker = JSONObject().put("id", npc.getString("id")).put("name", npc.getString("name"))
          .put("originPeriod", npc.getString("originPeriod"))
          .put("role", npc.getString("role")).put("description", npc.getString("description"))
      }
      val (fact, correct) = MiniMapScenario.effectText(s, a)
      line = fact
      p.getJSONArray("attempted").put(target)
      if (correct) p.getJSONArray("accepted").put(target)
      p.getJSONArray("records").put(JSONObject().put("id", target).put("kind", a.getJSONObject("effect").getString("type")).put("text", fact))
      if (p.getString("nodeId") == "6.1" && p.getInt("index") == 10 &&
          MiniMapScenario.holds(s.getJSONObject("goal"), accepted(p))) {
        // Final chapter has no later transition. Drop its puzzle on commit.
        discardKeys += scriptKey(p)
        discardKeys += outlineKey(p)
        p.put("endingComplete", true).put("accepted", JSONArray())
          .put("attempted", JSONArray()).put("records", JSONArray())
      }
    }
    p.put("version", p.getInt("version") + 1)
    // A transition carries no solved puzzle records; only the fresh scene may produce new evidence.
    val nextScript = state.world[scriptKey(p)]?.let(::JSONObject)
    val roomPublic = if (nextScript != null && p.getString("roomId").isNotEmpty()) room(nextScript, p).let {
      JSONObject().put("name", it.getString("name")).put("description", it.getString("description"))
    } else JSONObject().put("description", "Đang tới phân đoạn tiếp theo; chưa khám phá căn phòng mới.")
    val event = JSONObject().put("kind", kind).put("interaction", label).put("result", line).put("node", nodeId).put("room", roomPublic)
      .put("miniIndex", p.getInt("index"))
      .put("observations", if (nextScript != null) publicRecords(nextScript, p) else JSONArray())
      .put("npcs", if (nextScript != null && p.getString("roomId").isNotEmpty())
        authoredNpcs(nextScript, p.getString("roomId")) else JSONArray())
    if (authoredSpeaker != null) event.put("npc", authoredSpeaker)
    if (dialogue != null) event.put("dialogue", dialogue)
    return Staged(p, nodeId, event, label, discardKeys)
  }
}
