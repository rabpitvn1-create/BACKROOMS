package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Only the arrival handoff from Red Rooms, NOT a full Level 1 implementation.
 * Existing project Canon: concrete parking decks, columns, ramps, rare damaged
 * vehicles, intermittent light and local fog. No authored Level 1 exit here.
 */
object Level1ArrivalWorldDefinition : WorldDefinition {
  override val nodeId = "1"
  override val version = 1
  override val objective = "Đã tới Level 1. Quan sát những kết cấu có thể xác minh trong vùng bãi đỗ xe."
  override val requireAlternateRoutes = false
  override val environments = listOf(
    WorldEnvironment("l1_arrival", "Sảnh Đỗ Xe", "l1_arrival",
      "Một khoảng bãi đỗ xe bê tông rộng dưới trần thấp, với những hàng cột vuông và dốc dẫn lên vùng tối; đèn có thể chập chờn."),
    WorldEnvironment("l1_ramp", "Đầu Dốc Bê Tông", "l1_ramp",
      "Một đoạn dốc bê tông nối hai cao độ trong khu gara; không thể suy ra lối thoát chỉ từ hướng dốc và ánh sáng phía xa."),
    WorldEnvironment("l1_maintenance", "Lối Bảo Trì", "l1_maintenance",
      "Dãy cửa kỹ thuật nằm bên hông bãi đỗ xe; một số đèn có thể còn nguồn nhưng không có gì bảo đảm khu này an toàn.")
  )
  override fun generate(seed: Long): JSONObject =
    JSONObject().put("version", WorldGraphRuntime.GRAPH_VERSION).put("seed", seed.toString())
      .put("nodeId", nodeId).put("definitionVersion", version)
      .put("entryRoomId", "l1:arrival").put("passages", JSONArray())
      .put("lawInstances", JSONArray())
      .put("rooms", JSONArray().apply {
        val topology = listOf(
          Triple("l1:arrival", "l1_arrival", listOf("l1:ramp", "l1:maintenance")),
          Triple("l1:ramp", "l1_ramp", listOf("l1:arrival")),
          Triple("l1:maintenance", "l1_maintenance", listOf("l1:arrival"))
        )
        for ((id, environment, neighbors) in topology)
          put(JSONObject().put("id", id).put("environmentId", environment)
            .put("zoneId", environment)
            .put("adjacentRoomIds", JSONArray(neighbors)))
      })
  override fun validateLayout(graph: JSONObject) {
    val canonical = generate(graph.getString("seed").toLong())
    require(graph.getJSONArray("rooms").toString() == canonical.getJSONArray("rooms").toString() &&
      graph.getJSONArray("passages").toString() == canonical.getJSONArray("passages").toString() &&
      graph.getString("entryRoomId") == "l1:arrival") { "level1_arrival_topology" }
  }
}
