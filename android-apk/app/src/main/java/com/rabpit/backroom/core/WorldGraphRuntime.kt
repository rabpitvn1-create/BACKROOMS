package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Core-owned exploration foundation. Topology is game design, not an authored Level route.
 * Stored inside scene.progress so the existing pending/receipt transaction commits it atomically.
 * No provider is needed to create, restore or traverse this world. */
object WorldGraphRuntime {
  private const val GRAPH = "worldGraph"
  const val GRAPH_VERSION = 2
  fun active(p: JSONObject) = p.has(GRAPH)
  private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
  private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
  private fun rooms(g: JSONObject) = objects(g.getJSONArray("rooms"))
  private fun room(g: JSONObject, id: String) = rooms(g).first { it.getString("id") == id }
  private fun environment(r: JSONObject, definition: WorldDefinition): WorldEnvironment =
    requireNotNull(definition.environments.firstOrNull { it.id == r.getString("environmentId") }) { "world_environment" }
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
      add("MOVE", "room:$to", if (to in visited) environment(room(g, to), definition).name else "Lối chưa khám phá ${index + 1}")
    }
    add("SEARCH", "observe", "Quan sát môi trường hiện tại")
    if (!p.getJSONObject("markers").has(current)) add("SEARCH", "mark", "Ghi dấu mốc vị trí vào bản đồ")
    presentCharacters(state).forEach { add("TALK", "character:${it.id}", "${it.name} · Trò chuyện") }
    return out
  }

  fun publicView(state: GameState, p: JSONObject, definition: WorldDefinition = definition(p)): JSONObject {
    val g = p.getJSONObject(GRAPH); val current = p.getString("roomId")
    val rs = rooms(g); val e = environment(room(g, current), definition)
    val visited = strings(p.getJSONArray("visitedRooms")).toSet()
    val frontier = visited + rs.filter { it.getString("id") in visited }
      .flatMap { strings(it.getJSONArray("adjacentRoomIds")) }
    return JSONObject().put("runtime", "world-graph-v2").put("miniIndex", 1)
      .put("nodeId", p.getString("nodeId")).put("parentLevel", p.getInt("parent")).put("nodeTitle", MiniMapRuntime.node(state).title())
      .put("sceneVersion", p.getInt("version")).put("generationNeeded", false)
      .put("awaitingEnding", false).put("nodeExitUnlocked", false).put("completed", false)
      .put("availableActions", actions(state, p, definition)).put("records", p.getJSONArray("records"))
      .put("areaLog", p.getJSONArray("areaEvents")).put("temporaryInference", "")
      .put("objective", definition.objective)
      .apply { locationTags(this, e) }
      .put("environment", JSONObject().put("id", e.id).put("name", e.name).put("zoneId", e.zoneId).put("canon", e.canon))
      .put("room", JSONObject().put("id", current).put("name", e.name).put("description", e.canon))
      .put("npcs", JSONArray())
      .put("map", JSONObject().put("currentRoomId", current).put("entryRoomId", g.getString("entryRoomId"))
        .put("rooms", JSONArray().apply {
          rs.filter { it.getString("id") in frontier }.forEach { r ->
            val id = r.getString("id"); val known = id in visited
            put(JSONObject().put("id", id).put("visited", known)
              .put("name", if (known) environment(r, definition).name else "Chưa khám phá")
              .put("marked", p.getJSONObject("markers").has(id))
              .put("adjacentRoomIds", JSONArray(if (known) strings(r.getJSONArray("adjacentRoomIds")) else emptyList<String>())))
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
        val to = target.removePrefix("room:"); p.put("roomId", to)
        val name = environment(room(g, to), definition).name
        if (to !in strings(p.getJSONArray("visitedRooms"))) {
          p.getJSONArray("visitedRooms").put(to)
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "AREA").put("text", "Đã khám phá $name."))
        }
        result = "Đã tới $name."
      }
      "SEARCH" -> {
        val id = p.getString("roomId")
        if (target == "mark") {
          result = "Đã ghi vị trí ${environment(room(g, id), definition).name} làm dấu mốc trên bản đồ cá nhân."
          p.getJSONObject("markers").put(id, true)
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "MARK").put("text", result))
        } else {
          result = environment(room(g, id), definition).canon
          if (objects(p.getJSONArray("records")).none { it.getString("id") == id })
            p.getJSONArray("records").put(JSONObject().put("id", id).put("kind", "OBSERVATION").put("text", result))
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
