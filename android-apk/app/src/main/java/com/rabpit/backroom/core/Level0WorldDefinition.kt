package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Level 0 content and generator only. Environmental Canon remains in Level0Environments. */
object Level0WorldDefinition : WorldDefinition {
  override val nodeId = "0"
  override val version = 1
  override val objective = "Khám phá Level 0 và ghi nhận những gì có thể kiểm chứng."
  override val requireAlternateRoutes = true
  override val environments = Level0Environments.all.map {
    WorldEnvironment(it.id, it.name, it.parent ?: it.id, it.canon,
      it.reference.substringBefore('.'), if (it.parent != null) it.reference else null)
  }

  /** Ring backbone + local cycles + cross-area chords: connected even after any single edge
   * is removed. Seed only changes layout, never Canon membership or environmental identity. */
  override fun generate(seed: Long): JSONObject {
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
    val g = JSONObject().put("version", WorldGraphRuntime.GRAPH_VERSION).put("seed", seed.toString()).put("nodeId", nodeId).put("definitionVersion", version)
      .put("entryRoomId", roomIds.getValue("yellow_rot")).put("rooms", JSONArray().apply {
        catalog.forEach { e -> put(JSONObject().put("id", roomIds.getValue(e.id))
          .put("environmentId", e.id).put("zoneId", e.parent ?: e.id)
          .put("adjacentRoomIds", JSONArray(adjacency.getValue(e.id).map { roomIds.getValue(it) }))) }
      })
    return g
  }

  override fun validateLayout(graph: JSONObject) {
    val rooms = (0 until graph.getJSONArray("rooms").length()).map { graph.getJSONArray("rooms").getJSONObject(it) }
    require(rooms.size == environments.size &&
      rooms.map { it.getString("environmentId") }.toSet() == environments.map { it.id }.toSet()) { "world_environments" }
    require(graph.getString("entryRoomId") == "l0:r000" &&
      rooms.all { it.getString("id").matches(Regex("l0:r[0-9]{3}")) }) { "world_identity" }
  }
}
