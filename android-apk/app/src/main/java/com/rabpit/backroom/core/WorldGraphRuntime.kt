package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Core-owned exploration foundation. Topology is game design, not an authored Level route.
 * Stored inside scene.progress so the existing pending/receipt transaction commits it atomically.
 * No provider is needed to create, restore or traverse this world. */
object WorldGraphRuntime {
  private const val GRAPH = "worldGraph"
  const val GRAPH_VERSION = 3
  fun active(p: JSONObject) = p.has(GRAPH)
  private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
  private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
  private fun rooms(g: JSONObject) = objects(g.getJSONArray("rooms"))
  private fun passages(g: JSONObject): List<JSONObject> = g.optJSONArray("passages")?.let(::objects) ?: emptyList()
  private fun passageStates(p: JSONObject) = p.getJSONObject("passageStates")
  private fun flags(p: JSONObject) = p.getJSONObject("ruleFlags")
  private val probeLabels = linkedMapOf(
    "floor" to "Kiểm tra chi tiết nền và thảm",
    "ceiling" to "Kiểm tra trần và đường kỹ thuật",
    "markings" to "Đọc dấu vết vật lý",
    "reflection" to "Kiểm tra chi tiết đối xứng"
  )
  private fun trace(p: JSONObject, roomId: String): JSONObject {
    val changes = p.getJSONObject("environmentChanges")
    return changes.optJSONObject(roomId) ?: JSONObject().also { changes.put(roomId, it) }
  }
  private fun lawFulfilled(p: JSONObject, g: JSONObject, law: WorldEvidenceLaw): Boolean {
    val evidence = p.getJSONObject("evidenceState")
    if (!law.probes.all { evidence.optBoolean(it.id) }) return false
    if (law.family == WorldLawFamily.PHYSICAL_CORRELATION) return true
    val traced = rooms(g).filter { p.getJSONObject("environmentChanges")
      .optJSONObject(it.getString("id"))?.optBoolean("trace") == true }
      .map { it.getString("environmentId") }.toSet()
    return law.physicalAnchors.all { it in traced }
  }
  private fun passable(p: JSONObject, passage: JSONObject) =
    passageStates(p).getJSONObject(passage.getString("id")).let { it.getBoolean("discovered") && it.getBoolean("unlocked") }
  private fun departure(passage: JSONObject, current: String): String? = when (current) {
    passage.getString("from") -> passage.getString("to")
    passage.getString("to") -> if (passage.optBoolean("oneWay")) null else passage.getString("from")
    else -> null
  }
  private fun knownNeighbors(g: JSONObject, p: JSONObject, current: String): List<String> =
    strings(room(g, current).getJSONArray("adjacentRoomIds")) + passages(g).mapNotNull { passage ->
      if (passable(p, passage)) departure(passage, current) else null
    }

  private fun room(g: JSONObject, id: String) = rooms(g).first { it.getString("id") == id }
  private fun environment(r: JSONObject, definition: WorldDefinition): WorldEnvironment =
    requireNotNull(definition.environments.firstOrNull { it.id == r.getString("environmentId") }) { "world_environment" }
  /** Gameplay-room label, not a new Canon identity. Stable in the saved graph and
   * shown only after discovery; snapshot and knowledge still use environmentId. */
  private fun displayName(g: JSONObject, roomId: String, definition: WorldDefinition): String {
    val selected = room(g, roomId)
    val identity = selected.getString("environmentId")
    val e = environment(selected, definition)
    val duplicates = rooms(g).filter { it.getString("environmentId") == identity }
    if (duplicates.size == 1) return e.name
    return e.name + " · vị trí " + (duplicates.indexOfFirst { it.getString("id") == roomId } + 1)
  }
  private fun definition(p: JSONObject) = WorldDefinitions.require(p.getString("nodeId"))
  private fun locationTags(out: JSONObject, e: WorldEnvironment): JSONObject = out.apply {
    e.sceneZoneId?.let { put("zoneId", it) }
    e.sceneSubzoneId?.let { put("subzoneId", it) }
  }

  fun initialize(state: GameState, seed: Long = java.security.SecureRandom().nextLong(),
    definition: WorldDefinition = WorldDefinitions.require(MiniMapRuntime.node(state).id)): GameState {
    check(MiniMapRuntime.STATE !in state.world) { "world_already_initialized" }
    check(MiniMapRuntime.node(state).id == definition.nodeId) { "world_node_scope" }
    val g = definition.generate(seed)
    validate(g, definition)
    require(g.getString("seed") == seed.toString()) { "world_seed_mismatch" }
    val start = g.getString("entryRoomId")
    val p = JSONObject().put("runId", UUID.randomUUID().toString()).put("nodeId", definition.nodeId)
      .put("parent", MiniMapRuntime.node(state).parent).put("index", 1).put("version", 1).put("roomId", start)
      .put(GRAPH, g).put("visitedRooms", JSONArray().put(start))
      .put("records", JSONArray()).put("markers", JSONObject())
      .put("ruleFlags", JSONObject()).put("environmentChanges", JSONObject())
      .put("evidenceState", JSONObject())
      .put("passageStates", JSONObject().apply {
        passages(g).forEach { passage ->
          put(passage.getString("id"), JSONObject()
            .put("discovered", !passage.optBoolean("hidden"))
            .put("unlocked", passage.optString("requires").isEmpty()))
        }
      })
      .put("areaEvents", JSONArray().put(JSONObject().put("type", "AREA")
        .put("text", "Đã bước vào " + environment(room(g, start), definition).name + ".")))
    return state.copy(world = state.world + (MiniMapRuntime.STATE to p.toString()))
  }

  /** Shared checks for the currently supported bidirectional graph. Additional route
   * redundancy and content/layout constraints belong to the selected definition. */
  fun validate(g: JSONObject, definition: WorldDefinition = definition(g)) {
    require(g.getInt("version") == GRAPH_VERSION) { "world_version" }
    require(g.getString("nodeId") == definition.nodeId && g.getInt("definitionVersion") == definition.version) { "world_definition_mismatch" }
    g.getString("seed").toLong()
    require(definition.environments.isNotEmpty() && definition.environments.map { it.id }.distinct().size == definition.environments.size) { "world_environment_catalog" }
    val rs = rooms(g); val ids = rs.map { it.getString("id") }.toSet()
    require(rs.isNotEmpty() && rs.size == ids.size) { "world_rooms" }
    val graph = rs.associate { r ->
      val e = environment(r, definition)
      require(r.getString("id").matches(Regex("[a-zA-Z0-9:_-]{1,96}")) && r.getString("zoneId") == e.zoneId) { "world_identity" }
      r.getString("id") to strings(r.getJSONArray("adjacentRoomIds"))
    }
    graph.forEach { (id, edges) ->
      require(edges.distinct().size == edges.size && id !in edges) { "world_edges" }
      edges.forEach { to -> require(to in ids && id in graph.getValue(to)) { "world_bidirectional" } }
    }
    val ps = passages(g)
    require(ps.map { it.getString("id") }.distinct().size == ps.size) { "world_passage_id" }
    ps.forEach { edge ->
      require(edge.getString("id").matches(Regex("[a-zA-Z0-9:_-]{1,96}")) &&
        edge.getString("from") in ids && edge.getString("to") in ids &&
        edge.getString("from") != edge.getString("to")) { "world_passage_target" }
      require(!edge.has("requires") || edge.getString("requires") in definition.passageConditions) { "world_passage_condition" }
      require(!edge.has("oneWay") || edge.get("oneWay") is Boolean) { "world_passage_direction" }
      require(!edge.has("hidden") || edge.get("hidden") is Boolean) { "world_passage_visibility" }
    }
    val laws = definition.evidenceLaws
    val catalog = definition.environments.map { it.id }.toSet()
    require(laws.map { it.id }.distinct().size == laws.size &&
      laws.all { law -> law.id in definition.passageConditions &&
        law.probes.size >= 2 && law.probes.map { it.id }.distinct().size == law.probes.size &&
        law.probes.all { it.method in probeLabels && it.environmentId in catalog } &&
        law.verifyEnvironmentId in catalog && law.physicalAnchors.all { it in catalog } }) { "world_law_definition" }
    val frozen = g.optJSONArray("lawInstances") ?: JSONArray()
    require(frozen.toString() == JSONArray().apply { laws.forEach { put(it.freeze()) } }.toString()) { "world_law_parameters" }
    // Open corridors alone must remain viable with all optional gates closed. This
    // proves no collectible, NPC, fight or secret route is mandatory for backtracking.
    val entry = g.getString("entryRoomId")
    require(entry in ids) { "world_entry" }
    fun reachesAll(blockA: String?, blockB: String?): Boolean {
      val seen = mutableSetOf(entry); val queue = java.util.ArrayDeque<String>(); queue.add(entry)
      while (queue.isNotEmpty()) {
        val from = queue.removeFirst()
        graph.getValue(from).forEach { to ->
          if (!((from == blockA && to == blockB) || (from == blockB && to == blockA)) && seen.add(to)) queue.add(to)
        }
      }
      return seen == ids
    }
    require(reachesAll(null, null)) { "world_unreachable" }
    if (definition.requireAlternateRoutes) graph.forEach { (from, edges) -> edges.forEach { to ->
      require(reachesAll(from, to)) { "world_single_passage_softlock" }
    } }
    definition.validateLayout(g)
  }

  fun validateSavedProgress(p: JSONObject, definition: WorldDefinition = definition(p)) {
    val g = p.getJSONObject(GRAPH)
    validate(g, definition)
    val ids = rooms(g).map { it.getString("id") }.toSet()
    val visited = strings(p.getJSONArray("visitedRooms")).toSet()
    require(p.getString("nodeId") == definition.nodeId && p.getInt("version") > 0 &&
      p.getString("roomId") in visited && visited.all { it in ids }) { "world_saved_position" }
    require(p.getJSONObject("markers").keys().asSequence().all { it in visited }) { "world_saved_markers" }
    require(objects(p.getJSONArray("records")).all { it.getString("id") in visited }) { "world_saved_observations" }
    val states = passageStates(p)
    require(states.keys().asSequence().toSet() == passages(g).map { it.getString("id") }.toSet()) { "world_saved_passages" }
    val facts = flags(p)
    passages(g).forEach { passage ->
      val status = states.getJSONObject(passage.getString("id"))
      require(status.get("discovered") is Boolean && status.get("unlocked") is Boolean) { "world_passage_state" }
      require(!status.getBoolean("unlocked") || (status.getBoolean("discovered") &&
        (passage.optString("requires").isEmpty() || facts.optBoolean(passage.getString("requires"))))) { "world_passage_unlock" }
    }
    require(facts.keys().asSequence().all { it in definition.passageConditions && facts.get(it) is Boolean }) { "world_saved_facts" }
    val changes = p.getJSONObject("environmentChanges")
    require(changes.keys().asSequence().all { id -> id in ids && id in visited &&
      changes.getJSONObject(id).keys().asSequence().all { key ->
        if (key == "trace") changes.getJSONObject(id).get(key) is Boolean
        else key == "failedTests" && changes.getJSONObject(id).getInt(key) in 0..10000
      } }) { "world_saved_changes" }
    val proof = p.getJSONObject("evidenceState")
    val probes = definition.evidenceLaws.flatMap { it.probes }
    require(proof.keys().asSequence().all { id ->
      val source = probes.firstOrNull { it.id == id }
      source != null && proof.get(id) is Boolean && proof.getBoolean(id) &&
        rooms(g).any { it.getString("id") in visited && it.getString("environmentId") == source.environmentId } &&
        objects(p.getJSONArray("records")).any { it.optString("kind") == "EVIDENCE" && it.optString("text") == source.clue }
    }) { "world_saved_evidence" }
    require(definition.evidenceLaws.all { !facts.optBoolean(it.id) || lawFulfilled(p, g, it) }) { "world_law_unverified" }
  }

  fun characterPresent(state: GameState, actor: CharacterState, roomId: String = MiniMapRuntime.progress(state).getString("roomId")): Boolean =
    actor.presence == CharacterPresence.ACTIVE && (actor.id in state.party.memberIds ||
      actor.metadata["encountered"] == "true" && actor.metadata["world.nodeId"] == MiniMapRuntime.progress(state).getString("nodeId") && actor.metadata["world.roomId"] == roomId)

  private fun presentCharacters(state: GameState) = state.characters.values.filter {
    it.id != CAO_MINH_ID && characterPresent(state, it)
  }
  fun actions(state: GameState, p: JSONObject, definition: WorldDefinition = definition(p)): JSONArray {
    require(MiniMapRuntime.node(state).id == definition.nodeId && p.getString("nodeId") == definition.nodeId) { "world_node_scope" }
    val out = JSONArray()
    if (MiniMapRuntime.blocked(state)) return out
    val g = p.getJSONObject(GRAPH); val current = p.getString("roomId")
    val visited = strings(p.getJSONArray("visitedRooms")).toSet()
    fun add(kind: String, target: String, label: String) {
      out.put(JSONObject().put("kind", kind).put("targetId", target).put("label", label)
        .put("sceneVersion", p.getInt("version")).put("roomId", current)
        .put("actionId", "${p.getString("runId")}:world:${p.getInt("version")}:$kind:$target"))
    }
    strings(room(g, current).getJSONArray("adjacentRoomIds")).forEachIndexed { index, to ->
      add("MOVE", "room:$to", if (to in visited) displayName(g, to, definition) else "Lối chưa khám phá ${index + 1}")
    }
    passages(g).forEach { passage ->
      val dest = departure(passage, current) ?: return@forEach
      val status = passageStates(p).getJSONObject(passage.getString("id"))
      if (status.getBoolean("discovered") && status.getBoolean("unlocked"))
        add("MOVE", "passage:" + passage.getString("id"),
          if (dest in visited) displayName(g, dest, definition) else "Lối nối đã mở")
      else if (status.getBoolean("discovered") && !status.getBoolean("unlocked") &&
        (passage.optString("requires").isEmpty() || flags(p).optBoolean(passage.getString("requires"))))
        add("INTERACT", "passage:" + passage.getString("id"), "Thử mở lối nối đã phát hiện")
    }
    add("SEARCH", "observe", "Quan sát môi trường hiện tại")
    add("SEARCH", "inspect", "Khảo sát cấu trúc hiện tại")
    probeLabels.forEach { (id, label) -> add("SEARCH", "probe:" + id, label) }
    add("INTERACT", "trace", "Tạo dấu kiểm chứng trên bề mặt")
    add("INTERACT", "verify", "Thử kiểm chứng nhận định tại chỗ")
    if (!p.getJSONObject("markers").has(current)) add("SEARCH", "mark", "Ghi dấu mốc vị trí vào bản đồ")
    presentCharacters(state).forEach { add("TALK", "character:${it.id}", "${it.name} · Trò chuyện") }
    return out
  }

  fun publicView(state: GameState, p: JSONObject, definition: WorldDefinition = definition(p)): JSONObject {
    val g = p.getJSONObject(GRAPH); val current = p.getString("roomId")
    val rs = rooms(g); val e = environment(room(g, current), definition)
    val visited = strings(p.getJSONArray("visitedRooms")).toSet()
    val frontier = visited + rs.filter { it.getString("id") in visited }
      .flatMap { knownNeighbors(g, p, it.getString("id")) }
    // Inaccessible seams become visible only after inspection. Never include
    // private passage IDs, endpoints or requirement keys in any public packet.
    val obstructions = passages(g).filter { passage ->
      departure(passage, current) != null &&
        passageStates(p).getJSONObject(passage.getString("id")).let {
          it.getBoolean("discovered") && !it.getBoolean("unlocked")
        }
    }.map { passage ->
      if (passage.optString("requires").isEmpty() || flags(p).optBoolean(passage.getString("requires")))
        "Lối nối đã được nhận biết nhưng chưa mở; có thể thử thao tác tại đây."
      else "Lối nối đã được nhận biết nhưng chưa đủ chứng cứ kiểm chứng để mở."
    }
    return JSONObject().put("runtime", "world-graph-v3").put("miniIndex", 1)
      .put("nodeId", p.getString("nodeId")).put("parentLevel", p.getInt("parent")).put("nodeTitle", MiniMapRuntime.node(state).title())
      .put("sceneVersion", p.getInt("version")).put("generationNeeded", false)
      .put("awaitingEnding", false).put("nodeExitUnlocked", false).put("completed", false)
      .put("availableActions", actions(state, p, definition)).put("records", p.getJSONArray("records"))
      .put("areaLog", p.getJSONArray("areaEvents")).put("temporaryInference", "")
      .put("objective", definition.objective)
      .apply { locationTags(this, e) }
      .put("environment", JSONObject().put("id", e.id).put("name", e.name).put("zoneId", e.zoneId).put("canon", e.canon))
      .put("room", JSONObject().put("id", current).put("name", displayName(g, current, definition)).put("description", e.canon)
        .put("physicalTrace", p.getJSONObject("environmentChanges").optJSONObject(current)?.optBoolean("trace") == true))
      .put("npcs", JSONArray())
      .put("map", JSONObject().put("currentRoomId", current).put("entryRoomId", g.getString("entryRoomId"))
        .put("knownObstructions", JSONArray(obstructions))
        .put("rooms", JSONArray().apply {
          rs.filter { it.getString("id") in frontier }.forEach { r ->
            val id = r.getString("id"); val known = id in visited
            put(JSONObject().put("id", id).put("visited", known)
              .put("name", if (known) displayName(g, id, definition) else "Chưa khám phá")
              .put("marked", p.getJSONObject("markers").has(id))
              .put("traced", if (known) p.getJSONObject("environmentChanges").optJSONObject(id)?.optBoolean("trace") == true else false)
              .put("adjacentRoomIds", JSONArray(if (known) knownNeighbors(g, p, id) else emptyList<String>())))
          }
        }))
      .apply { state.world[MiniMapRuntime.PENDING]?.let { put("pendingActionId", JSONObject(it).getString("actionId")) } }
  }

  fun stage(state: GameState, command: JSONObject, p: JSONObject, definition: WorldDefinition = definition(p)): MiniMapRuntime.Staged {
    require(command.keys().asSequence().toSet() == setOf("actionId", "sceneVersion", "roomId", "kind", "targetId")) { "command_fields" }
    require(command.get("sceneVersion") is Int && command.getInt("sceneVersion") == p.getInt("version") &&
      command.getString("roomId") == p.getString("roomId")) { "scene_stale" }
    val choice = objects(actions(state, p, definition)).firstOrNull { a ->
      listOf("actionId", "kind", "targetId", "roomId").all { a.getString(it) == command.getString(it) }
    } ?: error("scene_choice_unavailable")
    val g = p.getJSONObject(GRAPH); val kind = command.getString("kind"); val target = command.getString("targetId")
    var result: String
    var dialogue: JSONObject? = null
    when (kind) {
      "MOVE" -> {
        val from = p.getString("roomId")
        val to = if (target.startsWith("passage:")) {
          val passage = passages(g).first { "passage:" + it.getString("id") == target }
          require(passable(p, passage)) { "world_passage_closed" }
          requireNotNull(departure(passage, from)) { "world_passage_direction" }
        } else target.removePrefix("room:")
        p.put("roomId", to)
        val name = environment(room(g, to), definition).name
        if (to !in strings(p.getJSONArray("visitedRooms"))) {
          p.getJSONArray("visitedRooms").put(to)
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "AREA").put("text", "Đã khám phá $name."))
        }
        result = "Đã tới $name."
      }
      "SEARCH" -> {
        val id = p.getString("roomId")
        if (target == "inspect") {
          val found = passages(g).filter { departure(it, id) != null }.filter { passage ->
            val s = passageStates(p).getJSONObject(passage.getString("id"))
            if (s.getBoolean("discovered")) false else { s.put("discovered", true); true }
          }
          result = if (found.isEmpty()) "Đã khảo sát cấu trúc, không xác nhận được lối nối mới."
            else "Phát hiện khe ghép cấu trúc. Chưa rõ có thể vượt qua hay không."
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "INSPECT").put("text", result))
        } else if (target.startsWith("probe:")) {
          val method = target.removePrefix("probe:")
          val source = definition.evidenceLaws.flatMap { it.probes }.firstOrNull {
            it.environmentId == environment(room(g, id), definition).id && it.method == method
          }
          if (source == null) result = "Không tìm thấy dấu vết có thể xác nhận thêm bằng cách kiểm tra này."
          else {
            result = source.clue
            if (!p.getJSONObject("evidenceState").optBoolean(source.id)) {
              p.getJSONObject("evidenceState").put(source.id, true)
              p.getJSONArray("records").put(JSONObject().put("id", id).put("kind", "EVIDENCE").put("text", result))
              p.getJSONArray("areaEvents").put(JSONObject().put("type", "EVIDENCE").put("text", result))
            }
          }
        } else if (target == "mark") {
          result = "Đã ghi vị trí ${environment(room(g, id), definition).name} làm dấu mốc trên bản đồ cá nhân."
          p.getJSONObject("markers").put(id, true)
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "MARK").put("text", result))
        } else {
          result = environment(room(g, id), definition).canon
          if (objects(p.getJSONArray("records")).none { it.getString("id") == id })
            p.getJSONArray("records").put(JSONObject().put("id", id).put("kind", "OBSERVATION").put("text", result))
        }
      }
      "INTERACT" -> {
        if (target == "trace") {
          val id = p.getString("roomId")
          val changed = trace(p, id)
          if (changed.optBoolean("trace")) {
            result = "Vết khắc đã có sẵn; không cần tạo thêm dấu trùng lặp."
          } else {
            changed.put("trace", true)
            result = "Đã tạo vết khắc nhỏ để kiểm tra tính bền vững của dấu trên bề mặt."
            p.getJSONArray("areaEvents").put(JSONObject().put("type", "TRACE").put("text", result))
          }
        } else if (target == "verify") {
          val id = p.getString("roomId")
          val at = environment(room(g, id), definition).id
          val law = definition.evidenceLaws.firstOrNull {
            it.verifyEnvironmentId == at && !flags(p).optBoolean(it.id)
          }
          if (law == null) result = "Tại đây chưa có kết cấu hoặc dữ liệu mới để kiểm chứng."
          else if (lawFulfilled(p, g, law)) {
            flags(p).put(law.id, true)
            result = law.successText
            p.getJSONArray("records").put(JSONObject().put("id", id).put("kind", "VERIFICATION").put("text", result))
            p.getJSONArray("areaEvents").put(JSONObject().put("type", "VERIFIED").put("text", result))
          } else {
            val changed = trace(p, id)
            changed.put("failedTests", (changed.optInt("failedTests", 0) + 1).coerceAtMost(10000))
            result = law.failedText
            p.getJSONArray("areaEvents").put(JSONObject().put("type", "TEST_FAILED").put("text", result))
          }
        } else {
        val passage = passages(g).first { "passage:" + it.getString("id") == target }
        require(departure(passage, p.getString("roomId")) != null) { "world_passage_direction" }
        val status = passageStates(p).getJSONObject(passage.getString("id"))
        require(status.getBoolean("discovered") && !status.getBoolean("unlocked") &&
          (passage.optString("requires").isEmpty() || flags(p).optBoolean(passage.getString("requires")))) { "world_passage_closed" }
        status.put("unlocked", true)
        result = "Lối nối cấu trúc đã mở và có thể quay lại kiểm tra."
        p.getJSONArray("areaEvents").put(JSONObject().put("type", "ROUTE").put("text", result))
        }
      }
      "TALK" -> {
        val character = presentCharacters(state).first { "character:${it.id}" == target }
        result = "Người chơi trò chuyện với ${character.name}; không tiết lộ kiến thức chưa được xác nhận."
        dialogue = JSONObject().put("characterId", character.id).put("name", character.name).put("topicId", "situation")
      }
      else -> error("world_action")
    }
    p.put("version", p.getInt("version") + 1)
    val e = environment(room(g, p.getString("roomId")), definition)
    val event = JSONObject().put("kind", kind).put("interaction", choice.getString("label")).put("result", result)
      .put("node", p.getString("nodeId")).put("miniIndex", 1)
      .apply { locationTags(this, e) }
      .put("room", JSONObject().put("name", e.name).put("description", e.canon)
        .put("environmentId", e.id).put("environmentCanon", e.canon))
      .put("observations", p.getJSONArray("records")).put("npcs", JSONArray())
      .put("presentCharacterIds", JSONArray(state.characters.values.filter { characterPresent(state, it, p.getString("roomId")) }.map { it.id }))
    dialogue?.let { event.put("dialogue", it) }
    return MiniMapRuntime.Staged(p, p.getString("nodeId"), event, choice.getString("label"), emptySet())
  }
}
