package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/**
 * Project-authored exploration for the three mandatory AREA nodes.
 * Dullness and Epsilon remain grounded in BACKROOMS_WORLD.md.
 * LS-2 was OPEN in that document; its design below is NEW PROJECT CONTENT
 * authorized on 2026-10-21, not an externally established Backrooms fact.
 */
object Level0AreaEnvironments {
  private fun zone(tag: String, number: Int, title: String, description: String,
                   children: List<Pair<String, String>>): List<WorldEnvironment> {
    require(children.size == 4)
    val id = "area_${tag}_z$number"
    return listOf(WorldEnvironment(id, title, id, description)) +
      children.mapIndexed { index, (name, detail) ->
        WorldEnvironment("${id}_s${index + 1}", name, id, detail)
      }
  }

  val catalogs: Map<String, List<WorldEnvironment>> = linkedMapOf(
    "dullness" to listOf(
      zone("DU", 1, "Dãy Tỷ Lệ Trôi", "Các phòng vàng thay đổi kích thước đột ngột; thảm và góc tường tiếp tục giống nhau dù chiều dài quan sát sai khác.", listOf(
        "Ngách Trần Quá Cao" to "Cửa hẹp mở vào khoảng trần bất thường; các nếp giấy tường nối liền nhưng tỷ lệ giãn mạnh.",
        "Phòng Cửa Ngắn" to "Cùng loại bản lề xuất hiện ở hai cửa có chiều cao chênh lệch; không có dấu người điều chỉnh.",
        "Mép Tường Co" to "Đường chân tường đổi độ dày khi đi quanh góc; bóng đèn vẫn treo cùng một kiểu.",
        "Bậc Sàn Rộng" to "Bậc thảm trải ra như một sân nhỏ; vết nối thảm không xác nhận kích thước ban đầu."
      )),
      zone("DU", 2, "Giao Lộ Nhập Khối", "Những bề mặt cùng chất liệu lấn vào nhau tại góc cua, khiến ranh giới giữa hai phòng khó xác nhận.", listOf(
        "Hai Cửa Chung Khung" to "Hai lỗ cửa gần nhau dùng chung đoạn khung tường; đường mép không khép thành hình chữ nhật.",
        "Góc Vách Dính" to "Hai dải giấy tường cùng họa tiết nằm chồng lên một rãnh hẹp như hai mặt phẳng nhập lại.",
        "Hành Lang Phân Thân" to "Vách tách thành hai nhánh không song song; ánh đèn phản trên thảm vẫn gần như đồng nhất.",
        "Nút Thắt Hình Học" to "Ba đoạn tường gặp nhau tại chỗ không đủ khoảng cho ba phòng thông thường."
      )),
      zone("DU", 3, "Vòng Lặp Méo", "Các lối giống nhau dẫn tới những không gian không còn giữ tỉ lệ cũ, buộc người đi phân biệt hình dáng với vị trí.", listOf(
        "Ngã Rẽ Đồng Mẫu" to "Các góc cua cùng hoa văn nhưng chiều dài đoạn đi không đồng nhất.",
        "Phòng Mẫu Phóng To" to "Một căn phòng mang cùng bố cục nơi đã thấy nhưng kích thước đồ án trên tường lớn hơn.",
        "Khung Đèn Nhân Bản" to "Hai đèn cùng mã kiểu dáng lắp ở trần khác cao độ, không đủ để nhận diện phòng.",
        "Dãy Cửa Sai Tầm" to "Tay nắm cửa có độ cao tương đối không giống những đoạn trước."
      )),
      zone("DU", 4, "Đầu Mút Mất Thước", "Các mốc độ dài mất tính ổn định; chỉ có những so sánh vật lý tại chỗ còn giữ giá trị.", listOf(
        "Vách Chia Bất Chợt" to "Một bức vách xuất hiện giữa khoảng được tưởng là hành lang liên tục.",
        "Điểm Khâu Thảm" to "Mẫu vải thảm nối với mép khác hướng, cho thấy thay đổi kết cấu chứ không xác nhận dịch chuyển.",
        "Hộp Đèn Lệch" to "Hộp đèn dài bị ép vào khoảng trần ngắn, chiếu bóng sai tỷ lệ.",
        "Ngõ Hẹp Khó Ước" to "Một ngõ có chiều ngang thay đổi dọc tuyến; không thể đo bằng số bước chân."
      ))
    ).flatten(),
    "epsilon" to listOf(
      zone("EP", 1, "Sảnh Cao Vàng", "Tường vàng và thảm beige mở rộng thành một sảnh cao vượt tầm nhìn; tiếng đèn huỳnh quang vọng lâu.", listOf(
        "Vòm Đèn Xa" to "Dãy bóng huỳnh quang rất cao; khoảng cách nhìn thấy không tương ứng tiếng ù gần.",
        "Thảm Giữa Sảnh" to "Nền beige kéo dài liên tục đến chân tường nhỏ như một đường kẻ.",
        "Chân Cột Dài" to "Cột vuông giống Level 0 nhưng tỷ lệ và bóng đổ khác xa chiều rộng nền.",
        "Góc Sảnh Lùi" to "Các góc tường lùi sâu dưới ánh sáng vàng nhợt; không có mốc xác nhận độ sâu."
      )),
      zone("EP", 2, "Hệ Cầu Treo", "Lối đi nhiều tầng nối bằng cầu và sàn nổi; hình chiếu các cầu có thể đánh lừa thứ tự cao độ.", listOf(
        "Nhịp Cầu Trống" to "Cầu đi bộ treo qua khoảng rỗng không thấy mép dưới.",
        "Sàn Tầng Trung" to "Một sàn bê tông thấp hơn cầu nhưng cao hơn chân cột, lan can đứt giữa chừng.",
        "Giao Cầu Bất Đối" to "Hai cầu cắt nhau trên hình chiếu nhưng không gặp nhau ở cùng độ cao.",
        "Bệ Lan Can Mòn" to "Dấu cọ trên lan can nằm ở hai tầng khác nhau; không chứng minh có một đường tắt."
      )),
      zone("EP", 3, "Mạng Đường Hầm", "Các đường hầm đi dưới kết cấu lớn, trần thấp và đèn kéo thành những nhịp dài phi lý.", listOf(
        "Hầm Đèn Trùng" to "Bóng đèn huỳnh quang lặp đều, làm mỗi khúc hầm giống nhau.",
        "Khúc Hầm Dốc" to "Nền thảm nghiêng nhẹ, mặt tường gần như không đổi họa tiết.",
        "Giếng Thông Tầng" to "Lỗ thông đứng mở vào một cao độ chưa xác định; tiếng ù vọng qua lỗ.",
        "Ngõ Đổi Độ Dài" to "Đoạn hầm nhìn thẳng có vẻ ngắn nhưng mất nhiều bước hơn ước tính."
      )),
      zone("EP", 4, "Thang Đa Cao Độ", "Cầu thang, platform và đường hầm xếp chồng nhưng không tạo một sơ đồ tầng thông thường.", listOf(
        "Vế Thang Cụt" to "Một vế thang dừng trước vách phẳng, tay vịn nối tiếp sang hướng khác.",
        "Chiếu Nghỉ Treo" to "Chiếu nghỉ có ba lối nhìn thấy nhưng chỉ hai lối cùng cao độ.",
        "Hành Lang Trên Đầu" to "Lối sáng cao hơn đèn trần nơi đứng, không chứng minh có cầu nối tới đó.",
        "Vị Trí Đèn Ngược" to "Đèn ở chân thang và đầu thang cùng góc quan sát nhưng bóng khác hướng."
      ))
    ).flatten(),
    "ls_2" to listOf(
      // LS-2: new project Canon, authorized user extension. No time travel, Entity lore or guaranteed safe route.
      zone("LS", 1, "Thư Viện Mép Trống", "Một vùng phòng lưu trữ không có sách, chỉ có khung kệ, ô số thiếu thứ tự và lớp bụi nằm thành những hình chữ nhật gián đoạn.", listOf(
        "Kệ Số Bỏ Trống" to "Ba khung kệ ghi số bằng nét khắc nhưng một vị trí hoàn toàn trắng, không đủ căn cứ để điền giá trị thiếu.",
        "Tủ Nhãn Đảo" to "Hai nhãn kim loại có mũi chỉ trái-phải đổi phía; chữ trên mặt đã mòn.",
        "Lối Giấy Than" to "Mẩu giấy than mục mắc ở khe kệ, không còn thông tin đọc được.",
        "Phòng Ô Đứt Quãng" to "Dãy ô chữ nhật kết thúc giữa vách thay vì nối hết chiều rộng như dự kiến."
      )),
      zone("LS", 2, "Xưởng Đồng Hồ Rỗng", "Các bộ khung bánh răng treo bên trong phòng không kim chỉ; tiếng tích tắc vọng từ cấu kiện chứ không chứng minh thời gian bị đổi.", listOf(
        "Bàn Bánh Răng" to "Ba bánh răng mất răng nằm rời, chưa rõ chúng từng ăn khớp theo thứ tự nào.",
        "Trục Không Kim" to "Một trục nhô khỏi tường không gắn kim, bóng trục thay đổi theo đèn.",
        "Khoang Nhịp Gõ" to "Âm gõ ba nhịp vang qua ống rỗng, quãng ngắt không ổn định.",
        "Phòng Vành Răng" to "Một vòng răng bằng thép treo lệch tâm, rãnh khóa không đối xứng."
      )),
      zone("LS", 3, "Hành Lang Sai Số", "Mặt sàn ghép bằng các tấm hình học lặp, mỗi nhánh giữ một vài dấu đúng trong khi các dấu khác thay đổi.", listOf(
        "Sàn Tấm Chẵn" to "Tấm nền xen kẽ hai kiểu vân; mắt thường dễ gộp những nhịp khác nhau.",
        "Góc Đếm Lệch" to "Các vạch đếm khắc trên vách không cùng chiều bắt đầu, chỉ số lớn nhất chưa biết.",
        "Lối Phản Chiếu Chéo" to "Góc chiếu của bóng sàn đảo phía so với mối ghép, không chứng minh sàn đã quay.",
        "Bệ Bốn Hướng" to "Bốn lối có dấu chỉ giống nhau nhưng đường nối mép thảm không đồng hướng."
      )),
      zone("LS", 4, "Nút Chốt Không Tên", "Bốn hành lang đưa về một khung kim loại chưa lắp cửa; ba lớp vết cọ gợi những thao tác cũ nhưng không khẳng định lối thoát.", listOf(
        "Khung Chốt Đôi" to "Hai khe giữ chốt nằm ở cao độ khác nhau, mòn không đều.",
        "Nền Mốc Chồng" to "Vết đo cũ nằm chồng lên mép sàn, không biết lần ghi nào có trước.",
        "Buồng Đối Chiếu" to "Ba rãnh quan sát trên tường cùng ghi nhận một cạnh khung ở ba góc khác nhau.",
        "Ngõ Số Không" to "Một lối hẹp không bảng tên, nền gạch hở ba đường ghép nối tới khung kim loại."
      ))
    ).flatten()
  )
  fun forNode(node: String): List<WorldEnvironment> = catalogs[node] ?: error("area_node_unknown")
}

/** The three AREA stops share the stable, explorable seeded topology, not their puzzles. */
class Level0AreaWorldDefinition(override val nodeId: String) : WorldDefinition {
  override val version = 1
  override val objective = "Khảo sát từng vùng của ${LevelRouteRegistry.node(nodeId).name} và chứng minh các kết luận trước khi rời AREA."
  override val requireAlternateRoutes = true
  override val environments = Level0AreaEnvironments.forNode(nodeId)
  override val fieldStudies = environments.filter { it.id != it.zoneId }.map {
    WorldFieldStudy(it.id, "Khảo sát " + it.name, it.canon)
  }
  override val evidenceLaws = Level0RouteRiddles.forNode(nodeId, environments)
  override val passageConditions = evidenceLaws.map { it.id }.toSet()
  override fun generate(seed: Long): JSONObject {
    val rng = Random(seed)
    val zones = environments.filter { it.id == it.zoneId }.map { it.id }.toMutableList()
    val keys = environments.map { it.id }
    val positions = keys.indices.toMutableList()
    java.util.Collections.shuffle(positions, rng)
    val ids = keys.mapIndexed { index, key -> key to ("area:r" + positions[index].toString().padStart(3, '0')) }.toMap()
    val edges = keys.associateWith { linkedSetOf<String>() }
    fun link(a: String, b: String) { edges.getValue(a).add(b); edges.getValue(b).add(a) }
    java.util.Collections.shuffle(zones, rng)
    zones.indices.forEach { link(zones[it], zones[(it + 1) % zones.size]) }
    zones.forEach { hub ->
      val subs = environments.filter { it.zoneId == hub && it.id != hub }.map { it.id }.toMutableList()
      java.util.Collections.shuffle(subs, rng)
      val cycle = listOf(hub) + subs
      cycle.indices.forEach { link(cycle[it], cycle[(it + 1) % cycle.size]) }
    }
    return JSONObject().put("version", WorldGraphRuntime.GRAPH_VERSION)
      .put("seed", seed.toString()).put("nodeId", nodeId)
      .put("definitionVersion", version).put("entryRoomId", ids.getValue(zones.first()))
      .put("passages", JSONArray())
      .put("lawInstances", JSONArray().apply { evidenceLaws.forEach { put(it.freeze()) } })
      .put("rooms", JSONArray().apply {
        keys.forEach { key ->
          val env = environments.first { it.id == key }
          put(JSONObject().put("id", ids.getValue(key)).put("environmentId", key)
            .put("zoneId", env.zoneId)
            .put("adjacentRoomIds", JSONArray(edges.getValue(key).map { ids.getValue(it) })))
        }
      })
  }
  override fun validateLayout(graph: JSONObject) {
    val rooms = graph.getJSONArray("rooms")
    val ids = (0 until rooms.length()).map { rooms.getJSONObject(it).getString("environmentId") }
    require(ids.size == 20 && ids.toSet() == environments.map { it.id }.toSet()) { "area_catalog" }
    val expected = generate(graph.getString("seed").toLong())
    require(rooms.toString() == expected.getJSONArray("rooms").toString() &&
      graph.getJSONArray("passages").toString() == expected.getJSONArray("passages").toString() &&
      graph.getString("entryRoomId") == expected.getString("entryRoomId")) { "area_seed_topology" }
  }
}
