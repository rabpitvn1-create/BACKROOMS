package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Level 0 content and generator only. Environmental Canon remains in Level0Environments. */
object Level0WorldDefinition : WorldDefinition {
  override val nodeId = "0"
  override val version = 5
  override val objective = "Khám phá Level 0 và ghi nhận những gì có thể kiểm chứng."
  override fun currentObjective(progress: JSONObject): String {
    val marked = progress.getJSONObject("markers").length()
    val records = progress.getJSONArray("records")
    val noteKinds = (0 until records.length()).map { records.getJSONObject(it).optString("kind") }
    val notes = noteKinds.count { it == "FIELD_NOTE" }
    val comparisons = noteKinds.count { it == "COMPARISON" }
    val proofs = progress.getJSONObject("evidenceState").length()
    val verified = progress.getJSONObject("ruleFlags").length()
    val graphRooms = progress.getJSONObject("worldGraph").getJSONArray("rooms")
    val visitedRoomIds = (0 until progress.getJSONArray("visitedRooms").length())
      .map { progress.getJSONArray("visitedRooms").getString(it) }.toSet()
    val visitedEnvironments = (0 until graphRooms.length())
      .map { graphRooms.getJSONObject(it) }
      .filter { it.getString("id") in visitedRoomIds }
      .map { it.getString("environmentId") }.toSet().size
    return when {
      marked == 0 -> "Ghi dấu vị trí đầu tiên vào MAP để có điểm mốc khi quay lại."
      notes == 0 -> "Khám phá một khu nhỏ, kiểm tra hiện trường và lưu ghi chép đầu tiên vào Sổ tay."
      notes < 2 -> "Tiếp tục khảo sát một môi trường khác; ghi nhận vật chứng trước khi kết luận."
      comparisons == 0 ->
        "Đối chiếu ghi chép ở hai phòng thuộc cùng một khu chính, hoặc tìm thêm chi tiết vật lý có thể kiểm chứng."
      proofs < 4 -> "Thu thập chứng cứ phân biệt được dấu vết thật với phản chiếu, âm thanh và cảm giác."
      verified < evidenceLaws.size ->
        "Đã kiểm chứng ${verified}/${evidenceLaws.size} chuỗi suy luận. Đối chiếu ghi chép cùng khu, tìm điểm neo và chọn giả thuyết tại hiện trường."
      visitedEnvironments < environments.size ->
        "Đã khảo sát ${visitedEnvironments}/${environments.size} khu vực. Tiếp tục khám phá những khu còn thiếu."
      else -> "Đã có đủ ghi chép và suy luận. Kiểm tra lối rời Level 0 bằng hành động do Core cung cấp."
    }
  }
  override val requireAlternateRoutes = true
  override val passageConditions = setOf("wet-ceiling", "loop-reflection", "signal-distortion", "fracture-boundary")
  override val evidenceLaws = listOf(
    // Distinguish an actual structural connection from deceptive black reflections.
    WorldEvidenceLaw("wet-ceiling", WorldLawFamily.PHYSICAL_CORRELATION, listOf(
      WorldLawProbe("wet:floor", "phong_ngap", "floor",
        "Vết kéo lê dưới mặt nước chỉ chứng minh sự xáo trộn trên sàn; mép thảm ẩm tiếp giáp vách chưa xác định hướng nước."),
      WorldLawProbe("wet:mirror", "vung_den", "reflection",
        "Bóng trong vũng đen không trùng với phòng thật. Hình phản chiếu không thể dùng làm bằng chứng về hướng dòng nước."),
      WorldLawProbe("wet:drip", "goc_thoi", "ceiling",
        "Những giọt nước rơi từ trần xuống; vệt sàn và vị trí nhỏ giọt là hai quan sát khác nhau, chưa xác nhận cùng nguồn."),
      WorldLawProbe("wet:pipe", "khoang_ky_thuat", "ceiling",
        "Đường ống lộ thiên và mối ghép sát vách tạo một vị trí kiểm tra cấu trúc cụ thể, khác hình phản chiếu trong vũng.")
    ), "khoang_ky_thuat",
      "Đã loại trừ hình phản chiếu và dấu kéo lê làm chỉ dấu dòng chảy. Mối ghép cấu trúc trở thành vị trí có thể thử mở.",
      "Chưa đủ chứng cứ độc lập hoặc chưa bảo vệ được cách phân biệt nguồn nước và hình ảnh.",
      comparisonPairs = setOf("phong_ngap" to "vung_den"),
      inferenceSteps = listOf(
        WorldInferenceStep("wet-source",
          "Trong bốn kiểu dấu, cặp nào có giá trị kiểm chứng kết cấu thay vì chỉ là ảnh hoặc dấu xáo trộn?",
          listOf(
            WorldInferenceOption("reflection", "Ưu tiên phản chiếu và vệt kéo lê vì chúng cùng xuất hiện trong môi trường nước."),
            WorldInferenceOption("physical", "Điểm nhỏ giọt trên trần và mối ghép ống-vách có thể tiếp cận."),
            WorldInferenceOption("memory", "Ưu tiên vị trí nhỏ giọt và vết kéo lê; bỏ qua mối ống-vách vì hai dấu đầu đều liên quan đến nước.")
          ), "physical", "Đã phân biệt dấu nguồn trên kết cấu với dấu phản chiếu và dấu chuyển động không rõ nguyên nhân."),
        WorldInferenceStep("wet-test",
          "Muốn thử lối ghép mà không đánh đồng tương quan với nguyên nhân, phép thử nào hợp lý?",
          listOf(
            WorldInferenceOption("force", "Suy ra vị trí mối nối chỉ bằng hướng vết kéo lê mà không đối chiếu cấu trúc đường ống."),
            WorldInferenceOption("ignore", "Dùng hình phản chiếu rõ nhất của Vũng Đen làm mốc để tìm chỗ nối, không so lại vệt sàn."),
            WorldInferenceOption("seam", "Dùng ghi chép vùng ngập đối chiếu ảnh phản chiếu rồi kiểm tra đúng mối ống-vách.")
          ), "seam", "Đã chọn phép kiểm tra tại mối nối vật lý thay vì suy đoán từ ảnh trong nước.")
      )),
    // Replicated scenery and orientation are different: deliberate player-created
    // marks are required to disambiguate identical rooms.
    WorldEvidenceLaw("loop-reflection", WorldLawFamily.SPATIAL_ANCHOR, listOf(
      WorldLawProbe("loop:mark", "hanh_lang_lap", "markings",
        "Ký hiệu có sẵn lặp tại nhiều đoạn hành lang; tự nó không xác định căn phòng đã đi qua."),
      WorldLawProbe("loop:copy", "phong_ban_sao", "geometry",
        "Ghế gãy, vết nứt và dấu chân có bản sao giống nhau. Nhận diện theo một đồ vật là không đủ."),
      WorldLawProbe("loop:cross", "nga_tu_vo_tan", "geometry",
        "Những hướng khác nhau có lúc dẫn về điểm trông giống ban đầu; hướng di chuyển không đủ xác nhận địa điểm."),
      WorldLawProbe("loop:mirror", "phong_dao", "reflection",
        "Bố cục đổi phía như ảnh gương; chỉ có dấu tự tạo được đối chiếu theo vị trí mới phân biệt việc đảo với bản sao.")
    ), "hanh_lang_lap",
      "Hai điểm neo do người chơi tạo đã phân biệt ký hiệu lặp với vị trí thực để nhận diện đường nối.",
      "Dấu cũ, hình dạng giống nhau hoặc một điểm neo đơn độc chưa chứng minh được lộ trình.",
      setOf("hanh_lang_lap", "phong_dao"),
      comparisonPairs = setOf("hanh_lang_lap" to "phong_dao"),
      inferenceSteps = listOf(
        WorldInferenceStep("loop-identity",
          "Điều gì có thể phân biệt hai phòng trùng mọi đồ vật và có bố cục đảo chiều?",
          listOf(
            WorldInferenceOption("chair", "Kết hợp ghế gãy, vết nứt và dấu chân; ba dấu có sẵn cùng xuất hiện sẽ đủ định danh."),
            WorldInferenceOption("owned", "Đối chiếu dấu do chính người chơi tạo tại hai vị trí."),
            WorldInferenceOption("direction", "Kết hợp số bước ước lượng với hướng rẽ để xác định danh tính phòng khi mọi đồ vật lặp lại.")
          ), "owned", "Đã xác định dấu do người chơi tạo là nguồn đối chứng độc lập với bản sao sẵn có."),
        WorldInferenceStep("loop-route",
          "Trong ngã tư lặp và phòng đảo, kết luận nào đủ căn cứ để phân biệt phép đảo với việc quay về?",
          listOf(
            WorldInferenceOption("orientation", "So vị trí tương đối của cả hai dấu mới với bố cục đảo và ghi chép đã đối chiếu."),
            WorldInferenceOption("icon", "Dùng tỷ lệ vết nứt và vị trí ghế để hiệu chỉnh khi thấy căn phòng đảo chiều."),
            WorldInferenceOption("turncount", "Lấy chu kỳ quay lại ngã tư làm gốc và gán thứ tự cố định cho bốn nhánh.")
          ), "orientation", "Đã xác minh lộ trình bằng hai điểm neo độc lập và quan hệ trái-phải.")
      )),
    // Four auditory/visual phenomena can resemble one moving source; the
    // valid inference is *insufficient evidence*, not a new supernatural law.
    WorldEvidenceLaw("signal-distortion", WorldLawFamily.PHYSICAL_CORRELATION, listOf(
      WorldLawProbe("signal:blink", "phong_chop", "light",
        "Đồ vật dường như đổi hình trong khoảng đèn tắt; không có bằng chứng chúng thực sự di chuyển."),
      WorldLawProbe("signal:lag", "hanh_lang_cam", "sound",
        "Tiếng bước chân đôi khi đến muộn so với bước chân, nên âm thanh không phải thước đo thời điểm tuyệt đối."),
      WorldLawProbe("signal:wall", "phong_thi_tham", "sound",
        "Tiếng thì thầm ở sau vách không đủ rõ để nhận ra lời nói hoặc xác định người phát."),
      WorldLawProbe("signal:call", "phong_tieng_goi", "sound",
        "Tiếng gọi đổi vị trí khi tiến gần; cảm giác quen thuộc không chứng minh một người có thật đang di chuyển.")
    ), "hanh_lang_cam",
      "Đã phân biệt âm trễ, tín hiệu méo và giọng gọi thay đổi: chưa có bằng chứng buộc chúng cùng một nguồn.",
      "Chưa phân biệt dấu quan sát được với suy đoán về người phát hoặc thời điểm phát.",
      comparisonPairs = setOf("hanh_lang_cam" to "phong_thi_tham"),
      prerequisites = setOf("loop-reflection"),
      inferenceSteps = listOf(
        WorldInferenceStep("signal-timing",
          "Từ tiếng bước chậm và vật như đổi hình lúc đèn tắt, mệnh đề nào chắc chắn nhất?",
          listOf(
            WorldInferenceOption("clock", "Dùng chu kỳ đèn chớp làm đồng hồ chung rồi gán mỗi tiếng bước vào một pha sáng."),
            WorldInferenceOption("limits", "Quan sát có độ trễ hoặc gián đoạn; chưa suy ra vật hay người đã di chuyển."),
            WorldInferenceOption("entity", "Suy từ độ trễ tiếng bước ra hướng di chuyển của một nguồn giữa hai lần quan sát.")
          ), "limits", "Đã tách độ trễ của giác quan khỏi khẳng định có chuyển động hay nguồn phát."),
        WorldInferenceStep("signal-source",
          "Hai loại giọng sau vách và tiếng gọi di chuyển cho phép kết luận gì?",
          listOf(
            WorldInferenceOption("same", "Dấu đổi vị trí khi tiến gần phù hợp nhất với một người phát âm đang đi qua các vách."),
            WorldInferenceOption("many", "Giọng phía sau vách và tiếng gọi đổi vị trí buộc phải đến từ ít nhất hai nguồn độc lập."),
            WorldInferenceOption("unproven", "Chưa định vị được nguồn; không đủ chứng cứ để khẳng định một hay nhiều người.")
          ), "unproven", "Đã loại bỏ các giả thuyết về danh tính và số nguồn không có chứng cứ.")
      )),
    // The final deduction weighs three distinct observations without treating
    // the Fracture as an authenticated exit to another Level.
    WorldEvidenceLaw("fracture-boundary", WorldLawFamily.SPATIAL_ANCHOR, listOf(
      WorldLawProbe("fracture:crack", "tuong_nut", "surface",
        "Vết nứt và tiếng rung trầm xác nhận bề mặt có dấu biến dạng, không tự xác nhận lối đi ở phía sau."),
      WorldLawProbe("fracture:floor", "san_lech", "floor",
        "Những khối sàn lệch cao và hành lang nghiêng là hình học quan sát được; hướng nghiêng chưa chỉ đích đến."),
      WorldLawProbe("fracture:void", "khe_toi", "depth",
        "Khe tối không xác định được chiều sâu, tiếng vọng không rõ; không thể lấy độ sâu tưởng tượng làm lối ra."),
      WorldLawProbe("fracture:layers", "phong_giao_thoa", "surface",
        "Các lớp tường lộ bê tông xám cho thấy vật liệu khác nhau, nhưng không xác nhận nơi này nối sang Level mới.")
    ), "phong_giao_thoa",
      "Đã phân biệt biến dạng cấu trúc, sai lệch cao độ và giới hạn đo sâu. Phòng Giao Thoa chưa được xác nhận là lối thoát.",
      "Thiếu đối chứng hoặc đang nhầm bề mặt bất thường với bằng chứng tồn tại một cửa chuyển Level.",
      setOf("tuong_nut", "san_lech"),
      comparisonPairs = setOf("tuong_nut" to "san_lech"),
      prerequisites = setOf("wet-ceiling", "signal-distortion"),
      inferenceSteps = listOf(
        WorldInferenceStep("fracture-evidence",
          "Các dấu vết nào thực sự đo được, và điều gì vẫn chưa thể kết luận?",
          listOf(
            WorldInferenceOption("depth", "Tiếng rung từ tường và khe không vọng là hai phép đo đủ để suy ra khe sâu hơn độ lệch sàn."),
            WorldInferenceOption("layers", "Độ lệch sàn và lớp vật liệu là chứng cứ hình học; chiều sâu khe chưa xác định."),
            WorldInferenceOption("portal", "Độ lệch sàn cùng vật liệu bê tông khác loại đủ để xác định một ranh giới sang Level khác.")
          ), "layers", "Đã tách các bề mặt quan sát được khỏi giả thuyết về chiều sâu khe."),
        WorldInferenceStep("fracture-exit",
          "Sau khi đối chiếu mối ghép, tín hiệu méo và hai dấu neo, lựa chọn nào không vượt quá chứng cứ?",
          listOf(
            WorldInferenceOption("jump", "Chọn khe không có tiếng vọng làm phép thử rời Level vì đường nứt nối tiếp nền sàn lệch."),
            WorldInferenceOption("certainty", "Kết hợp mối ghép ống-vách với lớp bê tông lộ ra để xác nhận Phòng Giao Thoa là cổng."),
            WorldInferenceOption("boundary", "Xác nhận biến đổi cấu trúc; chỉ rời Level qua lối được kiểm chứng riêng.")
          ), "boundary", "Đã kết luận đúng giới hạn của bằng chứng; không coi Phòng Giao Thoa là lối thoát.")
      ))
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
