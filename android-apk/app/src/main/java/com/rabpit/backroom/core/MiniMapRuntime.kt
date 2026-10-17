package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** All scene data lives in the same Core save/checkpoint as combat, inventory and party. */
object MiniMapRuntime {
  const val STATE = "scene.progress"
  const val PENDING = "scene.pending"
  const val RECEIPT = "scene.receipt"
  fun scriptKey(p: JSONObject) = "scene.script.${p.getInt("parent")}.${p.getInt("index")}"
  fun outlineKey(p: JSONObject) = "scene.outline.${p.getInt("parent")}"
  fun progress(state: GameState): JSONObject = state.world[STATE]?.let(::JSONObject) ?: JSONObject()
    .put("runId", state.metadata["story.runId"] ?: UUID.randomUUID().toString())
    .put("parent", LevelRouteRegistry.requested(JSONObject(state.world.getValue("levelJson")))!!.parent)
    .put("index", 1).put("version", 1).put("roomId", "")
    .put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray()).put("completed", JSONArray()).put("history", JSONArray())
  fun initialize(state: GameState): GameState = if (STATE in state.world) state else state.copy(world = state.world + (STATE to progress(state).toString()))
  fun script(state: GameState, p: JSONObject = progress(state)): JSONObject? = state.world[scriptKey(p)]?.let(::JSONObject)
  fun node(state: GameState) = LevelRouteRegistry.requested(JSONObject(state.world.getValue("levelJson")))!!
  fun token(state: GameState): String = progress(state).let { "${it.getString("runId")}:${it.getInt("parent")}:${it.getInt("index")}" }
  fun blocked(state: GameState) = CombatRuntime.active(state) != null || CombatRuntime.pending(state) != null || PENDING in state.world || ActionRuntime.activeSession(state) != null || state.turn.pending != null
  private fun accepted(p: JSONObject) = MiniMapScenario.strings(p.getJSONArray("accepted")).toSet()
  private fun attempted(p: JSONObject) = MiniMapScenario.strings(p.getJSONArray("attempted")).toSet()
  fun complete(state: GameState): Boolean = script(state)?.let { MiniMapScenario.holds(it.getJSONObject("goal"), accepted(progress(state))) } ?: false
  private fun room(s: JSONObject, p: JSONObject) = MiniMapScenario.objects(s.getJSONArray("rooms")).first { it.getString("id") == p.getString("roomId") }
  fun actions(state: GameState): JSONArray {
    val p = progress(state); val script = script(state) ?: return JSONArray()
    val out = JSONArray(); if (blocked(state)) return out
    fun add(kind: String, target: String, label: String) {
      out.put(JSONObject().put("kind", kind).put("targetId", target).put("label", label)
        .put("sceneVersion", p.getInt("version")).put("roomId", p.getString("roomId"))
        .put("actionId", "${p.getString("runId")}:${p.getInt("version")}:$kind:$target"))
    }
    val current = node(state); val anchor = script.getString("nodeAnchor")
    if (current.id != anchor) {
      require(LevelRouteRegistry.canMove(current.id, anchor)) { "scene_detour_return" }
      add("MOVE", "node:$anchor", "Quay lại ${LevelRouteRegistry.node(anchor).name}"); return out
    }
    val r = room(script, p)
    val rooms = MiniMapScenario.objects(script.getJSONArray("rooms"))
    MiniMapScenario.strings(r.getJSONArray("adjacentRoomIds")).forEach { id -> add("MOVE", "room:$id", rooms.first { it.getString("id") == id }.getString("name")) }
    MiniMapScenario.objects(script.getJSONArray("interactions")).filter {
      it.getString("roomId") == p.getString("roomId") && it.getString("id") !in attempted(p) && MiniMapScenario.holds(it.getJSONObject("requires"), accepted(p))
    }.forEach { a -> add(a.getString("kind"), a.getString("id"), a.getString("label")) }
    // Optional areas can only return to this scene's anchor, never skip main nodes.
    LevelRouteRegistry.exits(current.id).filter { id ->
      LevelRouteRegistry.node(id).parent == current.parent && LevelRouteRegistry.canMove(id, anchor)
    }.forEach { add("MOVE", "node:$it", "Khám phá ${LevelRouteRegistry.node(it).name}") }
    if (complete(state) && r.getString("id") == script.getString("exitRoomId")) {
      if (p.getInt("index") < 10) add("MOVE", "next", "Sang mini-map ${p.getInt("index") + 1}/10")
      else LevelRouteRegistry.exits(current.id).filter { LevelRouteRegistry.node(it).parent != current.parent }
        .forEach { add("MOVE", "node:$it", "Rời Level → ${LevelRouteRegistry.node(it).name}") }
    }
    return out
  }
  fun publicView(state: GameState): JSONObject {
    val p = progress(state); val s = script(state)
    val out = JSONObject().put("miniIndex", p.getInt("index")).put("parentLevel", p.getInt("parent"))
      .put("sceneVersion", p.getInt("version")).put("availableActions", actions(state))
      .put("records", p.getJSONArray("records")).put("previousObservations", previousObservations(p)).put("generationNeeded", s == null)
      .put("awaitingEnding", p.getInt("parent") == 6 && p.getInt("index") == 10 && complete(state))
      .put("levelExitUnlocked", p.getInt("index") == 10 && complete(state))
    if (s != null) {
      out.put("objective", s.getString("objective"))
      val r = room(s, p)
      out.put("room", JSONObject().put("id", r.getString("id")).put("name", r.getString("name")).put("description", r.getString("description")))
      if (node(state).id != s.getString("nodeAnchor")) out.put("room", JSONObject().put("name", node(state).name).put("description", "Khu vực tùy chọn. Quay lại để tiếp tục mini-map hiện tại."))
      out.put("completed", complete(state))
    }
    state.world[PENDING]?.let { raw -> out.put("pendingActionId", JSONObject(raw).getString("actionId")) }
    return out
  }
  private fun previousObservations(p: JSONObject): JSONArray {
    val history = p.getJSONArray("history")
    return if (history.length() == 0) JSONArray() else history.getJSONObject(history.length() - 1).getJSONArray("records")
  }
  private fun archive(p: JSONObject, s: JSONObject) {
    p.getJSONArray("history").put(JSONObject().put("parent", p.getInt("parent")).put("index", p.getInt("index"))
      .put("objective", s.getString("objective")).put("records", JSONArray(p.getJSONArray("records").toString())))
  }
  fun generationRequest(state: GameState): JSONObject {
    check(!blocked(state)) { "scene_busy" }
    val p = progress(state); val outline = state.world[outlineKey(p)]
    val kind = if (outline == null) "outline" else if (script(state) == null) "script" else "ready"
    return JSONObject().put("kind", kind).put("token", token(state)).put("levelNumber", p.getInt("parent"))
      .put("index", p.getInt("index")).put("nodeAnchor", MiniMapScenario.anchor(p.getInt("parent"), p.getInt("index")))
      .apply {
        if (outline != null && kind == "script") {
          put("outline", JSONObject(outline)); put("completed", p.getJSONArray("completed"))
          put("previousOutcome", p.getJSONArray("records"))
          val dependencies = MiniMapScenario.objects(JSONObject(outline).getJSONArray("entries"))[p.getInt("index") - 1].getJSONArray("dependencyRefs")
          val wanted = (0 until dependencies.length()).map { dependencies.getInt(it) }.toSet()
          put("dependencyOutcomes", JSONArray(MiniMapScenario.objects(p.getJSONArray("history")).filter {
            it.getInt("parent") == p.getInt("parent") && it.getInt("index") in wanted
          }.map { outcome -> JSONObject().put("index", outcome.getInt("index")).put("objective", outcome.getString("objective"))
            .put("observations", JSONArray(MiniMapScenario.objects(outcome.getJSONArray("records")).takeLast(6))) }))
        }
      }
  }
  fun acceptGeneration(state: GameState, expected: String, raw: String): GameState {
    check(expected == token(state) && !blocked(state)) { "generation_stale" }
    val p = progress(state); val data = MiniMapScenario.parse(raw)
    if (state.world[outlineKey(p)] == null) {
      val outline = MiniMapScenario.validateOutline(data, p.getInt("parent"))
      return state.copy(world = state.world + (outlineKey(p) to outline.toString()))
    }
    if (script(state) != null) return state // A frozen script is never replaced.
    val script = MiniMapScenario.validate(data, p.getInt("parent"), p.getInt("index"))
    p.put("roomId", script.getString("entryRoomId")).put("accepted", JSONArray()).put("attempted", JSONArray()).put("records", JSONArray())
    return state.copy(world = state.world + mapOf(scriptKey(p) to script.toString(), STATE to p.toString()))
  }
  data class Staged(val progress: JSONObject, val nodeId: String, val event: JSONObject, val label: String, val spawnEligible: Boolean)
  fun stage(state: GameState, command: JSONObject): Staged {
    val p = progress(state); val s = requireNotNull(script(state)) { "scene_not_ready" }
    require(command.keys().asSequence().toSet() == setOf("actionId", "sceneVersion", "roomId", "kind", "targetId")) { "command_fields" }
    require(command.get("sceneVersion") is Int && command.getInt("sceneVersion") == p.getInt("version") && command.getString("roomId") == p.getString("roomId")) { "scene_stale" }
    val choice = MiniMapScenario.objects(actions(state)).firstOrNull { a ->
      listOf("actionId", "kind", "targetId", "roomId").all { a.getString(it) == command.getString(it) }
    } ?: error("scene_choice_unavailable")
    val kind = command.getString("kind"); val target = command.getString("targetId")
    var nodeId = node(state).id
    val label = choice.getString("label")
    var line = label
    if (kind == "MOVE") {
      when {
        target.startsWith("room:") -> p.put("roomId", target.removePrefix("room:"))
        target == "next" -> {
          archive(p, s)
          p.getJSONArray("completed").put(JSONObject().put("parent", p.getInt("parent")).put("index", p.getInt("index")).put("objective", s.getString("objective")))
          p.put("index", p.getInt("index") + 1).put("roomId", "")
          nodeId = MiniMapScenario.anchor(p.getInt("parent"), p.getInt("index"))
        }
        target.startsWith("node:") -> {
          nodeId = target.removePrefix("node:")
          if (LevelRouteRegistry.node(nodeId).parent != p.getInt("parent")) {
            require((1..9).all { n -> MiniMapScenario.objects(p.getJSONArray("completed")).any { it.getInt("parent") == p.getInt("parent") && it.getInt("index") == n } }) { "scene_progress_incomplete" }
            archive(p, s)
            p.getJSONArray("completed").put(JSONObject().put("parent", p.getInt("parent")).put("index", 10).put("objective", s.getString("objective")))
            p.put("parent", LevelRouteRegistry.node(nodeId).parent).put("index", 1).put("roomId", "")
          }
        }
        else -> error("scene_move")
      }
      require(nodeId == node(state).id || LevelRouteRegistry.canMove(node(state).id, nodeId)) { "scene_route" }
    } else {
      val a = MiniMapScenario.objects(s.getJSONArray("interactions")).first { it.getString("id") == target }
      val (fact, correct) = MiniMapScenario.effectText(s, a)
      line = fact
      p.getJSONArray("attempted").put(target)
      if (correct) p.getJSONArray("accepted").put(target)
      p.getJSONArray("records").put(JSONObject().put("id", target).put("kind", a.getJSONObject("effect").getString("type")).put("text", fact))
    }
    p.put("version", p.getInt("version") + 1)
    // On transitions the previous observations are only for the next generator; they
    // cannot satisfy a new puzzle. acceptGeneration resets them before activating it.
    val nextScript = state.world[scriptKey(p)]?.let(::JSONObject)
    val roomPublic = if (nextScript != null && p.getString("roomId").isNotEmpty()) room(nextScript, p).let {
      JSONObject().put("name", it.getString("name")).put("description", it.getString("description"))
    } else JSONObject().put("description", "Đang tới phân đoạn tiếp theo; chưa khám phá căn phòng mới.")
    val event = JSONObject().put("kind", kind).put("interaction", label).put("result", line).put("node", nodeId).put("room", roomPublic)
      .put("miniIndex", p.getInt("index")).put("observations", p.getJSONArray("records"))
    return Staged(p, nodeId, event, label, kind == "MOVE" || kind == "SEARCH")
  }
}
