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
      .put("completed", JSONArray()).put("visitedRooms", JSONArray()).put("areaEvents", JSONArray())
  }
  fun progress(state: GameState): JSONObject = state.world[STATE]?.let(::JSONObject)
    ?: freshProgress(state.metadata["story.runId"] ?: UUID.randomUUID().toString(), node(state).id)
  fun initialize(state: GameState): GameState = if (STATE in state.world) state else state.copy(world = state.world + (STATE to progress(state).toString()))
  fun script(state: GameState, p: JSONObject = progress(state)): JSONObject? = state.world[scriptKey(p)]?.let(::JSONObject)
  fun node(state: GameState) = LevelRouteRegistry.requested(JSONObject(state.world.getValue("levelJson")))!!
  fun token(state: GameState): String = progress(state).let { "${it.getString("runId")}:${it.getString("nodeId")}:${it.getInt("index")}" }
  fun blocked(state: GameState) = CombatRuntime.active(state) != null || CombatRuntime.pending(state) != null || PENDING in state.world || ActionRuntime.activeSession(state) != null || state.turn.pending != null
  /** A checked scene control is a small, repeatable action, not a new encounter roll. */
  fun isMechanismControl(state: GameState, actionId: String): Boolean {
    val s = script(state) ?: return false
    return MiniMapScenario.objects(s.getJSONArray("interactions")).any {
      it.getString("id") == actionId && it.getJSONObject("effect").getString("type") == "STEP"
    }
  }
  private fun accepted(p: JSONObject) = MiniMapScenario.strings(p.getJSONArray("accepted")).toSet()
  private fun attempted(p: JSONObject) = MiniMapScenario.strings(p.getJSONArray("attempted")).toSet()
  fun complete(state: GameState): Boolean {
    val p = progress(state)
    if (WorldGraphRuntime.active(p)) return false
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
    val p = progress(state)
    if (WorldGraphRuntime.active(p)) return WorldGraphRuntime.actions(state, p)
    if (node(state).id != MiniMapScenario.ACTIVE_NODE_ID ||
      p.getString("nodeId") != MiniMapScenario.ACTIVE_NODE_ID ||
      p.getInt("index") !in 1..MiniMapScenario.MINI_MAP_COUNT) return JSONArray()
    val script = script(state) ?: return JSONArray()
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
    MiniMapScenario.objects(script.getJSONArray("interactions")).filter { action ->
      val effect = action.getJSONObject("effect")
      val step = effect.getString("type") == "STEP"
      action.getString("roomId") == p.getString("roomId") &&
        (step && effect.getString("mechanismId") !in accepted(p) || !step && action.getString("id") !in attempted(p)) &&
        MiniMapScenario.holds(action.getJSONObject("requires"), accepted(p))
    }.forEach { a ->
      val label = if (a.getString("kind") == "TALK") {
        val npc = MiniMapScenario.objects(script.getJSONArray("npcs")).first { it.getString("id") == a.getString("npcId") }
        "${npc.getString("name")} · ${a.getString("label")}"
      } else a.getString("label")
      add(a.getString("kind"), a.getString("id"), label)
      val effect = a.getJSONObject("effect")
      if (effect.getString("type") == "STEP")
        out.getJSONObject(out.length() - 1).put("mechanismId", effect.getString("mechanismId"))
    }
    presentCharacters(state).forEach { character ->
      dialogueTopics.forEach { (topicId, label) -> add("TALK", "character:${character.id}:$topicId", "${character.name} · $label") }
    }
    // Mini-maps 1..7 advance inside Level 0. Mini-map 8 has no next
    // command and no cross-level transition or ending mechanism yet.
    if (complete(state) && r.getString("id") == script.getString("exitRoomId") &&
      p.getInt("index") < MiniMapScenario.MINI_MAP_COUNT)
      add("MOVE", "next", "Tiến vào khu vực kế tiếp.")
    return out
  }
  /** Derived from already tried interactions, never from the hidden answer key. */
  private fun temporaryInference(state: GameState, s: JSONObject, p: JSONObject): String {
    if (complete(state)) return if (p.getInt("index") == MiniMapScenario.MINI_MAP_COUNT)
      "Đã hoàn thành mục tiêu mini-map hiện tại." else
      "Các suy luận cần thiết đã được xác nhận. Hãy tìm đến lối ra của mini-map."
    val done = attempted(p)
    val tried = MiniMapScenario.objects(s.getJSONArray("interactions")).filter { it.getString("id") in done }
    val claims = tried.filter { it.getJSONObject("effect").getString("type") == "CLAIM" }
    val evidence = tried.filter { it.getJSONObject("effect").getString("type") in setOf("CLUE", "EVIDENCE", "HINT") }
    val verifiedFactIds = evidence.filter { it.getJSONObject("effect").has("factId") }
      .map { it.getJSONObject("effect").getString("factId") }.toSet()
    val unverified = claims.filter { it.getJSONObject("effect").getString("factId") !in verifiedFactIds }
    val reasoning = when {
      unverified.isNotEmpty() ->
        "Có ${claims.size} lời khai nhưng ít nhất một nhận định vẫn thiếu vật chứng độc lập. Chưa thể coi lời kể của nhân chứng là sự thật."
      claims.isNotEmpty() && evidence.isNotEmpty() ->
        "Đã thu thập lời khai và dấu vết vật lý. Hãy so sánh chúng để loại những giả thuyết không phù hợp."
      evidence.isNotEmpty() ->
        "Đã tìm thấy ${evidence.size} dấu vết. Hãy suy ra quy luật trước khi thử thao tác."
      claims.isNotEmpty() ->
        "Lời khai chỉ là nhận định từ góc nhìn nhân chứng. Hãy tìm chứng cứ tại hiện trường."
      else -> "Chưa đủ dữ kiện để suy luận. Hãy quan sát những vật thể hoặc dấu vết có thể kiểm chứng."
    }
    val current = p.getString("roomId")
    val remaining = MiniMapScenario.objects(s.getJSONArray("interactions")).filter {
      it.getString("id") !in done && it.getString("kind") == "SEARCH" &&
        it.getJSONObject("effect").getString("type") !in setOf("ANSWER", "STEP") &&
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

  /** Area Log only records confirmed, non-sensitive milestones. Do not copy observations or solution labels. */
  private fun noteAreaEvent(p: JSONObject, type: String, sourceId: String, text: String) {
    val events = p.optJSONArray("areaEvents") ?: JSONArray()
    // Deduplicate by confirmed Core identity, not by generic public prose.
    if (MiniMapScenario.objects(events).none { it.optString("type") == type && it.optString("sourceId") == sourceId }) {
      events.put(JSONObject().put("type", type).put("sourceId", sourceId).put("text", text))
      p.put("areaEvents", events)
    }
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
    if (WorldGraphRuntime.active(p)) return WorldGraphRuntime.publicView(state, p)
    val out = JSONObject().put("miniIndex", p.getInt("index")).put("parentLevel", p.getInt("parent"))
      .put("nodeId", p.getString("nodeId")).put("nodeTitle", node(state).title())
      .put("sceneVersion", p.getInt("version")).put("availableActions", actions(state))
      .put("records", p.getJSONArray("records")).put("generationNeeded",
        s == null && p.getString("nodeId") == MiniMapScenario.ACTIVE_NODE_ID &&
          p.getInt("index") in 1..MiniMapScenario.MINI_MAP_COUNT)
      .put("awaitingEnding", false).put("nodeExitUnlocked", false)
    if (s != null) {
      // Only explored room names are revealed. Edges beyond the discovered
      // frontier stay hidden and no interaction/fact/solution enters MAP.
      val visited = MiniMapScenario.strings(p.optJSONArray("visitedRooms") ?: JSONArray()).toSet() + p.getString("roomId")
      val rooms = MiniMapScenario.objects(s.getJSONArray("rooms"))
      out.put("map", JSONObject().put("currentRoomId", p.getString("roomId"))
        .put("entryRoomId", s.getString("entryRoomId"))
        .put("rooms", JSONArray().apply {
          rooms.forEach { room ->
            val roomId = room.getString("id")
            val known = roomId in visited
            val frontier = known || rooms.any { adjacent -> adjacent.getString("id") in visited &&
              roomId in MiniMapScenario.strings(adjacent.getJSONArray("adjacentRoomIds")) }
            if (frontier) put(JSONObject().put("id", roomId)
              .put("name", if (known) room.getString("name") else "Chưa khám phá")
              .put("visited", known)
              .put("adjacentRoomIds", JSONArray(
                if (known) MiniMapScenario.strings(room.getJSONArray("adjacentRoomIds")).filter {
                  it in visited || rooms.any { adjacent -> adjacent.getString("id") in visited &&
                    it in MiniMapScenario.strings(adjacent.getJSONArray("adjacentRoomIds")) }
                } else emptyList<String>())))
          }
        }))
      // Internal sourceId exists only for deduplication and never enters WebView or AI context.
      out.put("areaLog", JSONArray().apply {
        MiniMapScenario.objects(p.optJSONArray("areaEvents") ?: JSONArray()).forEach { event ->
          put(JSONObject().put("type", event.getString("type")).put("text", event.getString("text")))
        }
      })
      out.put("objective", s.getString("objective"))
      // Re-project saved receipts from script data; never expose internal fact enum IDs.
      out.put("records", publicRecords(s, p))
      out.put("temporaryInference", temporaryInference(state, s, p))
      val r = room(s, p)
      val environment = Level0Environments.requireEnvironment(r.getString("environmentId"))
      out.put("zoneId", environment.reference.substringBefore('.'))
      if (environment.parent != null) out.put("subzoneId", environment.reference)
      out.put("environment", JSONObject().put("id", environment.id).put("name", environment.name)
        .put("zoneId", environment.parent ?: environment.id).put("canon", environment.canon))
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
    val p = progress(state)
    if (WorldGraphRuntime.active(p)) return JSONObject().put("kind", "ready").put("token", token(state))
    require(node(state).id == MiniMapScenario.ACTIVE_NODE_ID &&
      p.getString("nodeId") == MiniMapScenario.ACTIVE_NODE_ID &&
      p.getInt("index") in 1..MiniMapScenario.MINI_MAP_COUNT) { "scene_node_scope" }
    val outline = state.world[outlineKey(p)]
    val kind = if (outline == null) "outline" else if (script(state) == null) "script" else "ready"
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
    check(!WorldGraphRuntime.active(p)) { "world_generation_not_allowed" }
    check(node(state).id == MiniMapScenario.ACTIVE_NODE_ID &&
      p.getString("nodeId") == MiniMapScenario.ACTIVE_NODE_ID &&
      p.getInt("index") in 1..MiniMapScenario.MINI_MAP_COUNT) { "scene_node_scope" }
    val data = MiniMapScenario.parse(raw)
    if (state.world[outlineKey(p)] == null) {
      val outline = MiniMapScenario.validateOutline(data, p.getString("nodeId"))
      return state.copy(world = state.world + (outlineKey(p) to outline.toString()))
    }
    if (script(state) != null) return state // A frozen script is never replaced.
    val script = MiniMapScenario.validate(data, p.getString("nodeId"), p.getInt("index"))
    p.put("roomId", script.getString("entryRoomId"))
      .put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray())
      .put("visitedRooms", JSONArray().put(script.getString("entryRoomId")))
      .put("areaEvents", JSONArray().put(JSONObject().put("type", "AREA")
        .put("text", "Đã bước vào " + MiniMapScenario.objects(script.getJSONArray("rooms"))
          .first { it.getString("id") == script.getString("entryRoomId") }.getString("name") + ".")))
    return state.copy(world = state.world + mapOf(scriptKey(p) to script.toString(), STATE to p.toString()))
  }
  data class Staged(val progress: JSONObject, val nodeId: String, val event: JSONObject, val label: String, val discardKeys: Set<String>)
  fun stage(state: GameState, command: JSONObject): Staged {
    var p = progress(state)
    if (WorldGraphRuntime.active(p)) return WorldGraphRuntime.stage(state, command, p)
    val s = requireNotNull(script(state)) { "scene_not_ready" }
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
        target.startsWith("room:") -> {
          val destination = target.removePrefix("room:")
          p.put("roomId", destination)
          val visited = p.optJSONArray("visitedRooms") ?: JSONArray()
          if (destination !in MiniMapScenario.strings(visited)) {
            visited.put(destination)
            p.put("visitedRooms", visited)
            val destinationName = room(s, p).getString("name")
            val events = p.optJSONArray("areaEvents") ?: JSONArray()
            events.put(JSONObject().put("type", "AREA").put("text", "Đã khám phá " + destinationName + "."))
            // Observing a person exists is an event; their testimony is not.
            authoredNpcs(s, destination).let { people ->
              for (i in 0 until people.length())
                events.put(JSONObject().put("type", "ENCOUNTER")
                  .put("text", "Đã gặp " + people.getJSONObject(i).getString("name") + "."))
            }
            p.put("areaEvents", events)
          }
        }
        target == "next" -> {
          check(p.getInt("index") < MiniMapScenario.MINI_MAP_COUNT) { "scene_mini_limit" }
          discardKeys += scriptKey(p)
          p.getJSONArray("completed").put(p.getInt("index"))
          p.put("index", p.getInt("index") + 1).put("roomId", "")
            .put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray())
            .put("visitedRooms", JSONArray()).put("areaEvents", JSONArray())
          p.remove("mechanismPositions")
        }
        else -> error("scene_move")
      }
      require(nodeId == MiniMapScenario.ACTIVE_NODE_ID) { "scene_node_scope" }
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
      val effect = a.getJSONObject("effect")
      if (effect.getString("type") == "STEP") {
        val mechanismId = effect.getString("mechanismId")
        if (kind == "SEARCH") noteAreaEvent(p, "DEVICE", mechanismId,
          "Đã phát hiện một cơ cấu điều khiển tại " + room(s, p).getString("name") + ".")
        val mechanism = MiniMapScenario.objects(s.getJSONArray("mechanisms"))
          .first { it.getString("id") == mechanismId }
        val steps = MiniMapScenario.strings(mechanism.getJSONArray("steps"))
        val positions = p.optJSONObject("mechanismPositions") ?: JSONObject()
        val position = positions.optInt(mechanismId, 0)
        val next = if (steps[position] == target) position + 1 else 0
        if (next == steps.size) {
          positions.remove(mechanismId)
          p.getJSONArray("accepted").put(mechanismId)
          line = "Cơ cấu khóa vang lên tiếng bật chốt. Cánh cửa đã được mở khóa."
          p.getJSONArray("records").put(JSONObject().put("id", mechanismId)
            .put("kind", "MECHANISM").put("text", line))
          noteAreaEvent(p, "UNLOCK", mechanismId, "Một cơ cấu khóa đã mở.")
        } else {
          positions.put(mechanismId, next)
          line = if (next == 0) "Cơ cấu trở lại trạng thái ban đầu; chốt cửa vẫn khóa."
            else "Cần điều khiển chuyển vị trí. Chốt cửa vẫn chưa mở."
        }
        p.put("mechanismPositions", positions)
      } else {
        val (fact, correct) = MiniMapScenario.effectText(s, a)
        line = fact
        p.getJSONArray("attempted").put(target)
        if (correct) p.getJSONArray("accepted").put(target)
        p.getJSONArray("records").put(JSONObject().put("id", target)
          .put("kind", effect.getString("type")).put("text", fact))
        if (kind == "SEARCH" && correct && effect.getString("type") in setOf("CLUE", "EVIDENCE", "HINT"))
          noteAreaEvent(p, "DISCOVERY", target,
            "Đã ghi nhận dấu vết tại " + room(s, p).getString("name") + ".")
      }
    }
    p.put("version", p.getInt("version") + 1)
    // A transition carries no solved puzzle records; only the fresh scene may produce new evidence.
    val nextScript = state.world[scriptKey(p)]?.let(::JSONObject)
    val roomPublic = if (nextScript != null && p.getString("roomId").isNotEmpty()) room(nextScript, p).let {
      val environment = Level0Environments.requireEnvironment(it.getString("environmentId"))
      JSONObject().put("name", it.getString("name")).put("description", it.getString("description"))
        .put("environmentId", environment.id).put("environmentCanon", environment.canon)
    } else JSONObject().put("description", "Đang tới phân đoạn tiếp theo; chưa khám phá căn phòng mới.")
    val event = JSONObject().put("kind", kind).put("interaction", label).put("result", line).put("node", nodeId).put("room", roomPublic)
    val deviceStep = kind == "SEARCH" && MiniMapScenario.objects(s.getJSONArray("interactions"))
      .any { it.getString("id") == target &&
        it.getJSONObject("effect").getString("type") == "STEP" }
    if (deviceStep) event.put("mechanismStep", true)
    event.put("miniIndex", p.getInt("index"))
      .put("observations", if (nextScript != null) publicRecords(nextScript, p) else JSONArray())
      .put("npcs", if (nextScript != null && p.getString("roomId").isNotEmpty())
        authoredNpcs(nextScript, p.getString("roomId")) else JSONArray())
    if (authoredSpeaker != null) event.put("npc", authoredSpeaker)
    if (dialogue != null) event.put("dialogue", dialogue)
    return Staged(p, nodeId, event, label, discardKeys)
  }
}
