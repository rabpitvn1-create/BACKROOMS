package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Level 0 content and generator only. Environmental Canon remains in Level0Environments. */
object Level0WorldDefinition : WorldDefinition {
  override val nodeId = "0"
  override val version = 4
  override val objective = "Khám phá Level 0 và ghi nhận những gì có thể kiểm chứng."
  override fun currentObjective(progress: JSONObject): String {
    val marked = progress.getJSONObject("markers").length()
    val records = progress.getJSONArray("records")
    val noteKinds = (0 until records.length()).map { records.getJSONObject(it).optString("kind") }
    val notes = noteKinds.count { it == "FIELD_NOTE" }
    val comparisons = noteKinds.count { it == "COMPARISON" }
    val proofs = progress.getJSONObject("evidenceState").length()
    val verified = progress.getJSONObject("ruleFlags").length()
    val visited = progress.getJSONArray("visitedRooms").length()
    return when {
      marked == 0 -> "Ghi dấu vị trí đầu tiên vào MAP để có điểm mốc khi quay lại."
      notes == 0 -> "Khám phá một khu nhỏ, kiểm tra hiện trường và lưu ghi chép đầu tiên vào Sổ tay."
      notes < 2 -> "Tiếp tục khảo sát một môi trường khác; ghi nhận vật chứng trước khi kết luận."
      comparisons == 0 ->
        "Đối chiếu ghi chép ở hai phòng thuộc cùng một khu chính, hoặc tìm thêm chi tiết vật lý có thể kiểm chứng."
      proofs < 2 -> "Quan sát kỹ hiện tượng vật lý tại các khu đã gặp; thử cách kiểm tra khác khi quay lại."
      verified == 0 -> "Đối chiếu chứng cứ độc lập và thử kiểm chứng tại hiện trường; thất bại không xóa dấu vết."
      visited < 45 -> "Tiếp tục khám phá những lối chưa biết và quay lại các địa điểm cần kiểm chứng."
      else -> "Tổng hợp ghi chép, kiểm chứng các giả thuyết còn mở. Lối thoát Level 0 chưa được xác nhận."
    }
  }
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
  /** Player investigations explicitly anchored to the approved 29 subzone descriptions.
   * The observation is copied from the same catalog used by the knowledge packet.
   * These are optional field notes, not hidden puzzle solutions or Canon amendments. */
  private val fieldFocus = mapOf(
    "phong_moc" to "Kiểm tra giấy tường phủ mốc",
    "hanh_lang_dai" to "Đối chiếu tầm nhìn cuối hành lang",
    "goc_khuat" to "Theo dõi góc khuất",
    "phong_trong" to "Khảo sát vết lõm trên thảm",
    "phong_chop" to "Ghi nhận biến đổi theo nhịp đèn",
    "hanh_lang_toi" to "Khảo sát ánh sáng từ phòng xa",
    "tram_dien" to "Kiểm tra hộp điện và cáp cháy",
    "vung_den" to "Đối chiếu phản chiếu trong vũng",
    "tham_phong" to "Quan sát nền thảm phồng",
    "phong_ngap" to "Theo dấu vệt kéo lê dưới nước",
    "goc_thoi" to "Xác định nguồn giọt nước",
    "hanh_lang_cam" to "So sánh nhịp bước và tiếng vọng",
    "phong_thi_tham" to "Lắng nghe tiếng nói sau vách",
    "nga_ba_chet" to "Kiểm tra độ cao dấu bàn tay",
    "tran_vo" to "Khảo sát những tấm trần rơi",
    "khoang_ky_thuat" to "Khảo sát đường ống và âm thanh",
    "gieng_tran" to "Kiểm tra độ sâu nhìn thấy trên trần",
    "phong_treo" to "Quan sát chuyển động dây cáp",
    "phong_ky_uc" to "Đối chiếu chi tiết quen thuộc",
    "tuong_mat" to "Quan sát vết ố dưới ánh đèn",
    "phong_tieng_goi" to "Theo dõi vị trí tiếng gọi",
    "hanh_lang_lap" to "So sánh ký hiệu dọc hành lang",
    "phong_ban_sao" to "Đối chiếu các vật thể tương tự",
    "nga_tu_vo_tan" to "Đánh dấu hướng tại ngã tư",
    "phong_dao" to "Kiểm tra bố cục phản chiếu",
    "tuong_nut" to "Lắng nghe tiếng rung từ vết nứt",
    "san_lech" to "Khảo sát chênh lệch mặt sàn",
    "khe_toi" to "Kiểm tra giới hạn tầm nhìn",
    "phong_giao_thoa" to "Đối chiếu hai lớp bề mặt tường"
  )
  override val fieldStudies = Level0Environments.all.filter { it.parent != null }.map {
    WorldFieldStudy(it.id, fieldFocus.getValue(it.id), it.canon)
  }

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
    // A structurally valid graph is not necessarily the world generated from
    // its stored seed. Reject room/edge/passage rewrites instead of accepting
    // a reconfigured game after save/load.
    val canonical = generate(graph.getString("seed").toLong())
    require(graph.getJSONArray("rooms").toString() == canonical.getJSONArray("rooms").toString() &&
      graph.getJSONArray("passages").toString() == canonical.getJSONArray("passages").toString() &&
      graph.getString("entryRoomId") == canonical.getString("entryRoomId")) { "world_seeded_topology" }
  }
}
