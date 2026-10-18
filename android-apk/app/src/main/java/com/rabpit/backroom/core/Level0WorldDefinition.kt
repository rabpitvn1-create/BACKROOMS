package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Level 0 content and generator only. Environmental Canon remains in Level0Environments. */
object Level0WorldDefinition : WorldDefinition {
  override val nodeId = "0"
  override val version = 3
  override val objective = "Khám phá Level 0 và ghi nhận những gì có thể kiểm chứng."
  override val requireAlternateRoutes = true
  override val passageConditions = setOf("wet-ceiling", "loop-reflection")
  override val evidenceLaws = listOf(
    WorldEvidenceLaw("wet-ceiling", WorldLawFamily.PHYSICAL_CORRELATION, listOf(
      WorldLawProbe("wet:floor", "phong_ngap", "floor",
        "Ở mép thảm ngập, vệt ẩm leo lên đường ghép tường chứ không tiếp tục theo vết kéo lê trên sàn."),
      WorldLawProbe("wet:ceiling", "khoang_ky_thuat", "ceiling",
        "Đầu một đường ống kỹ thuật ướt bất thường; hơi ẩm đọng quanh mối ghép với vách.")
    ), "khoang_ky_thuat",
      "Đối chiếu vệt nước với đầu ống đã xác nhận một mối nối vật lý có thể thử mở.",
      "Thử đối chiếu đường ống chưa đủ căn cứ; mối nối không dịch chuyển."),
    WorldEvidenceLaw("loop-reflection", WorldLawFamily.SPATIAL_ANCHOR, listOf(
      WorldLawProbe("loop:mark", "hanh_lang_lap", "markings",
        "Hai vết xước cũ giống nhau nhưng hướng kéo lệch khác nhau; dấu có sẵn không xác định được lối đi."),
      WorldLawProbe("loop:mirror", "phong_dao", "reflection",
        "Bố cục đảo chiều nhưng một đường nứt vẫn giữ cùng phía với vết xước đã quan sát.")
    ), "hanh_lang_lap",
      "Hai dấu do người chơi tạo giúp phân biệt vị trí với bản sao; nhận diện được đường nối mới.",
      "Dấu có sẵn chưa chứng minh được vị trí; thử nghiệm không tạo ra đường nối.",
      setOf("hanh_lang_lap", "phong_dao"))
  )
  override val environments = Level0Environments.all.map {
    WorldEnvironment(it.id, it.name, it.parent ?: it.id, it.canon,
      it.reference.substringBefore('.'), if (it.parent != null) it.reference else null)
  }

  /** Every Canon identity has a room, with a second physical room at each zone hub.
   * Seed scrambles room identifiers independently of Canon order. The optional hidden
   * connectors are bonus approaches, never needed to traverse all 45 rooms. */
  override fun generate(seed: Long): JSONObject {
    val rng = Random(seed)
    val catalog = Level0Environments.all
    val hubs = catalog.filter { it.parent == null }.map { it.id }.toMutableList()
    val keys = catalog.map { it.id } + hubs.map { "annex:" + it }
    val indices = keys.indices.toMutableList()
    java.util.Collections.shuffle(indices, rng)
    val roomIds = keys.mapIndexed { i, key -> key to ("l0:r" + indices[i].toString().padStart(3, '0')) }.toMap()
    val adjacency = keys.associateWith { linkedSetOf<String>() }
    fun link(a: String, b: String) {
      adjacency.getValue(a).add(b)
      adjacency.getValue(b).add(a)
    }
    java.util.Collections.shuffle(hubs, rng)
    hubs.indices.forEach { link(hubs[it], hubs[(it + 1) % hubs.size]) }
    hubs.forEachIndexed { i, hub ->
      val children = catalog.filter { it.parent == hub }.map { it.id }.toMutableList()
      java.util.Collections.shuffle(children, rng)
      val cycle = listOf(hub) + children
      cycle.indices.forEach { link(cycle[it], cycle[(it + 1) % cycle.size]) }
      link(children.last(), hubs[(i + 2) % hubs.size])
      val annex = "annex:" + hub
      link(annex, hub)
      link(annex, children.first())
      link(annex, children.last())
    }
    val passages = JSONArray()
      .put(JSONObject().put("id", "p:00")
        .put("from", roomIds.getValue("phong_ngap")).put("to", roomIds.getValue("khoang_ky_thuat"))
        .put("oneWay", false).put("hidden", true).put("requires", "wet-ceiling"))
      .put(JSONObject().put("id", "p:01")
        .put("from", roomIds.getValue("hanh_lang_lap")).put("to", roomIds.getValue("phong_ky_uc"))
        .put("oneWay", true).put("hidden", true).put("requires", "loop-reflection"))
    return JSONObject().put("version", WorldGraphRuntime.GRAPH_VERSION).put("seed", seed.toString())
      .put("nodeId", nodeId).put("definitionVersion", version)
      .put("entryRoomId", roomIds.getValue("yellow_rot")).put("passages", passages)
      .put("lawInstances", JSONArray().apply { evidenceLaws.forEach { put(it.freeze()) } })
      .put("rooms", JSONArray().apply {
        keys.forEach { key ->
          val identity = key.removePrefix("annex:")
          val e = catalog.first { it.id == identity }
          put(JSONObject().put("id", roomIds.getValue(key))
            .put("environmentId", identity).put("zoneId", e.parent ?: e.id)
            .put("adjacentRoomIds", JSONArray(adjacency.getValue(key).map { roomIds.getValue(it) })))
        }
      })
  }

  override fun validateLayout(graph: JSONObject) {
    val rooms = (0 until graph.getJSONArray("rooms").length()).map { graph.getJSONArray("rooms").getJSONObject(it) }
    val counts = rooms.groupingBy { it.getString("environmentId") }.eachCount()
    require(rooms.size == 45 && counts.keys == environments.map { it.id }.toSet() &&
      environments.all { counts[it.id] == if (it.id == it.zoneId) 2 else 1 }) { "world_environments" }
    require(rooms.any { it.getString("id") == graph.getString("entryRoomId") &&
      it.getString("environmentId") == "yellow_rot" } &&
      rooms.all { it.getString("id").matches(Regex("l0:r[0-9]{3}")) }) { "world_identity" }
    require(graph.getJSONArray("passages").length() == 2) { "world_passage_layout" }
  }
}
