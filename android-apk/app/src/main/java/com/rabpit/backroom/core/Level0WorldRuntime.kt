package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import java.util.UUID

/** Core-owned exploration foundation. Topology is game design, not an authored Level route.
 * Stored inside scene.progress so the existing pending/receipt transaction commits it atomically.
 * No provider is needed to create, restore or traverse this world. */
object Level0WorldRuntime {
  private const val GRAPH = "worldGraph"
  private const val GRAPH_VERSION = 1
  fun active(p: JSONObject) = p.has(GRAPH)
  private fun strings(a: JSONArray) = MiniMapScenario.strings(a)
  private fun rooms(g: JSONObject) = MiniMapScenario.objects(g.getJSONArray("rooms"))
  private fun room(g: JSONObject, id: String) = rooms(g).first { it.getString("id") == id }
  private fun environment(r: JSONObject) = Level0Environments.requireEnvironment(r.getString("environmentId"))

  fun initialize(state: GameState, seed: Long = java.security.SecureRandom().nextLong()): GameState {
    check(MiniMapRuntime.STATE !in state.world) { "world_already_initialized" }
    check(MiniMapRuntime.node(state).id == "0") { "world_node_scope" }
    val g = generate(seed)
    val start = g.getString("entryRoomId")
    val p = JSONObject().put("runId", UUID.randomUUID().toString()).put("nodeId", "0")
      .put("parent", 0).put("index", 1).put("version", 1).put("roomId", start)
      .put(GRAPH, g).put("visitedRooms", JSONArray().put(start))
      .put("records", JSONArray()).put("markers", JSONObject())
      .put("areaEvents", JSONArray().put(JSONObject().put("type", "AREA")
        .put("text", "Đã bước vào " + environment(room(g, start)).name + ".")))
    return state.copy(world = state.world + (MiniMapRuntime.STATE to p.toString()))
  }

  /** Ring backbone + local cycles + cross-area chords: connected even after any single edge
   * is removed. Seed only changes layout, never Canon membership or environmental identity. */
  fun generate(seed: Long): JSONObject {
    val rng = Random(seed)
    val catalog = Level0Environments.all
    val roomIds = catalog.mapIndexed { i, e -> e.id to "l0:r" + i.toString().padStart(3, '0') }.toMap()
    val adjacency = catalog.associate { it.id to linkedSetOf<String>() }
    fun link(a: String, b: String) { adjacency.getValue(a).add(b); adjacency.getValue(b).add(a) }
    val hubs = catalog.filter { it.parent == null }.map { it.id }.toMutableList()
    java.util.Collections.shuffle(hubs, rng)
    hubs.indices.forEach { link(hubs[it], hubs[(it + 1) % hubs.size]) }
    hubs.forEachIndexed { i, hub ->
      val children = catalog.filter { it.parent == hub }.map { it.id }.toMutableList()
      java.util.Collections.shuffle(children, rng)
      val cycle = listOf(hub) + children
      cycle.indices.forEach { link(cycle[it], cycle[(it + 1) % cycle.size]) }
      // A second inter-area approach does not require solving a puzzle or meeting a follower.
      link(children.last(), hubs[(i + 2) % hubs.size])
    }
    val g = JSONObject().put("version", GRAPH_VERSION).put("seed", seed.toString())
      .put("entryRoomId", roomIds.getValue("yellow_rot")).put("rooms", JSONArray().apply {
        catalog.forEach { e -> put(JSONObject().put("id", roomIds.getValue(e.id))
          .put("environmentId", e.id).put("zoneId", e.parent ?: e.id)
          .put("adjacentRoomIds", JSONArray(adjacency.getValue(e.id).map { roomIds.getValue(it) }))) }
      })
    validate(g)
    return g
  }

  /** Acceptance gate, not a random retry until a graph happens to work. No gates in v1;
   * every room must remain reachable with any one passage removed. */
  fun validate(g: JSONObject) {
    require(g.getInt("version") == GRAPH_VERSION) { "world_version" }
    g.getString("seed").toLong()
    val rs = rooms(g); val ids = rs.map { it.getString("id") }.toSet()
    require(rs.size == ids.size && rs.size == Level0Environments.all.size) { "world_rooms" }
    require(rs.map { environment(it).id }.toSet() == Level0Environments.all.map { it.id }.toSet()) { "world_environments" }
    val graph = rs.associate { r ->
      val e = environment(r)
      require(r.getString("id").matches(Regex("l0:r[0-9]{3}")) && r.getString("zoneId") == (e.parent ?: e.id)) { "world_identity" }
      r.getString("id") to strings(r.getJSONArray("adjacentRoomIds"))
    }
    graph.forEach { (id, edges) ->
      require(edges.size >= 2 && edges.distinct().size == edges.size && id !in edges) { "world_edges" }
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
    graph.forEach { (from, edges) -> edges.forEach { to ->
      require(reachesAll(from, to)) { "world_single_passage_softlock" }
    } }
  }

  fun validateSavedProgress(p: JSONObject) {
    val g = p.getJSONObject(GRAPH)
    validate(g)
    val ids = rooms(g).map { it.getString("id") }.toSet()
    val visited = strings(p.getJSONArray("visitedRooms")).toSet()
    require(p.getString("nodeId") == "0" && p.getInt("version") > 0 &&
      p.getString("roomId") in visited && visited.all { it in ids }) { "world_saved_position" }
    require(p.getJSONObject("markers").keys().asSequence().all { it in visited }) { "world_saved_markers" }
    require(MiniMapScenario.objects(p.getJSONArray("records")).all { it.getString("id") in visited }) { "world_saved_observations" }
  }

  fun characterPresent(state: GameState, actor: CharacterState, roomId: String = MiniMapRuntime.progress(state).getString("roomId")): Boolean =
    actor.presence == CharacterPresence.ACTIVE && (actor.id in state.party.memberIds ||
      actor.metadata["encountered"] == "true" && actor.metadata["level0.roomId"] == roomId)

  private fun presentCharacters(state: GameState) = state.characters.values.filter {
    it.id != CAO_MINH_ID && characterPresent(state, it)
  }
  fun actions(state: GameState, p: JSONObject): JSONArray {
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
      add("MOVE", "room:$to", if (to in visited) environment(room(g, to)).name else "Lối chưa khám phá ${index + 1}")
    }
    add("SEARCH", "observe", "Quan sát môi trường hiện tại")
    if (!p.getJSONObject("markers").has(current)) add("SEARCH", "mark", "Ghi dấu mốc vị trí vào bản đồ")
    presentCharacters(state).forEach { add("TALK", "character:${it.id}", "${it.name} · Trò chuyện") }
    return out
  }

  fun publicView(state: GameState, p: JSONObject): JSONObject {
    val g = p.getJSONObject(GRAPH); val current = p.getString("roomId")
    val rs = rooms(g); val e = environment(room(g, current))
    val visited = strings(p.getJSONArray("visitedRooms")).toSet()
    val frontier = visited + rs.filter { it.getString("id") in visited }
      .flatMap { strings(it.getJSONArray("adjacentRoomIds")) }
    return JSONObject().put("runtime", "level0-world-v1").put("miniIndex", 1)
      .put("nodeId", "0").put("parentLevel", 0).put("nodeTitle", MiniMapRuntime.node(state).title())
      .put("sceneVersion", p.getInt("version")).put("generationNeeded", false)
      .put("awaitingEnding", false).put("nodeExitUnlocked", false).put("completed", false)
      .put("availableActions", actions(state, p)).put("records", p.getJSONArray("records"))
      .put("areaLog", p.getJSONArray("areaEvents")).put("temporaryInference", "")
      .put("objective", "Khám phá Level 0 và ghi nhận những gì có thể kiểm chứng.")
      .put("zoneId", e.reference.substringBefore('.'))
      .apply { if (e.parent != null) put("subzoneId", e.reference) }
      .put("environment", JSONObject().put("id", e.id).put("name", e.name).put("zoneId", e.parent ?: e.id).put("canon", e.canon))
      .put("room", JSONObject().put("id", current).put("name", e.name).put("description", e.canon))
      .put("npcs", JSONArray())
      .put("map", JSONObject().put("currentRoomId", current).put("entryRoomId", g.getString("entryRoomId"))
        .put("rooms", JSONArray().apply {
          rs.filter { it.getString("id") in frontier }.forEach { r ->
            val id = r.getString("id"); val known = id in visited
            put(JSONObject().put("id", id).put("visited", known)
              .put("name", if (known) environment(r).name else "Chưa khám phá")
              .put("marked", p.getJSONObject("markers").has(id))
              .put("adjacentRoomIds", JSONArray(if (known) strings(r.getJSONArray("adjacentRoomIds")) else emptyList<String>())))
          }
        }))
      .apply { state.world[MiniMapRuntime.PENDING]?.let { put("pendingActionId", JSONObject(it).getString("actionId")) } }
  }

  fun stage(state: GameState, command: JSONObject, p: JSONObject): MiniMapRuntime.Staged {
    require(command.keys().asSequence().toSet() == setOf("actionId", "sceneVersion", "roomId", "kind", "targetId")) { "command_fields" }
    require(command.get("sceneVersion") is Int && command.getInt("sceneVersion") == p.getInt("version") &&
      command.getString("roomId") == p.getString("roomId")) { "scene_stale" }
    val choice = MiniMapScenario.objects(actions(state, p)).firstOrNull { a ->
      listOf("actionId", "kind", "targetId", "roomId").all { a.getString(it) == command.getString(it) }
    } ?: error("scene_choice_unavailable")
    val g = p.getJSONObject(GRAPH); val kind = command.getString("kind"); val target = command.getString("targetId")
    var result: String
    var dialogue: JSONObject? = null
    when (kind) {
      "MOVE" -> {
        val to = target.removePrefix("room:"); p.put("roomId", to)
        val name = environment(room(g, to)).name
        if (to !in strings(p.getJSONArray("visitedRooms"))) {
          p.getJSONArray("visitedRooms").put(to)
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "AREA").put("text", "Đã khám phá $name."))
        }
        result = "Đã tới $name."
      }
      "SEARCH" -> {
        val id = p.getString("roomId")
        if (target == "mark") {
          result = "Đã ghi vị trí ${environment(room(g, id)).name} làm dấu mốc trên bản đồ cá nhân."
          p.getJSONObject("markers").put(id, true)
          p.getJSONArray("areaEvents").put(JSONObject().put("type", "MARK").put("text", result))
        } else {
          result = environment(room(g, id)).canon
          if (MiniMapScenario.objects(p.getJSONArray("records")).none { it.getString("id") == id })
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
    val e = environment(room(g, p.getString("roomId")))
    val event = JSONObject().put("kind", kind).put("interaction", choice.getString("label")).put("result", result)
      .put("node", "0").put("miniIndex", 1).put("zoneId", e.reference.substringBefore('.'))
      .apply { if (e.parent != null) put("subzoneId", e.reference) }
      .put("room", JSONObject().put("name", e.name).put("description", e.canon)
        .put("environmentId", e.id).put("environmentCanon", e.canon))
      .put("observations", p.getJSONArray("records")).put("npcs", JSONArray())
      .put("presentCharacterIds", JSONArray(state.characters.values.filter { characterPresent(state, it, p.getString("roomId")) }.map { it.id }))
    dialogue?.let { event.put("dialogue", it) }
    return MiniMapRuntime.Staged(p, "0", event, choice.getString("label"), emptySet())
  }
}
