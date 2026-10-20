package com.rabpit.backroom.core

/**
 * Node-specific reasoning challenges. Their observations refer only to the
 * selected environment's authored Canon. A plausible hypothesis is not a fact
 * until the player has collected clues, compared two rooms, and tested anchors.
 */
object Level0RouteRiddles {
  private data class Choice(val question: String, val possibilities: List<String>, val answer: Int,
                            val conclusion: String)
  private data class Thread(val focus: String, val claim: Choice, val test: Choice)

  private fun choice(question: String, a: String, b: String, c: String,
                     correct: Int, conclusion: String): Choice =
    Choice(question, listOf(a, b, c), correct, conclusion)

  private val scenarios: Map<String, List<Thread>> = linkedMapOf(
    "0.1" to listOf(
      Thread("Sai lệch kính và cấu kiện",
        choice("Hai ô kính cho hình phản xạ khác, nhưng dầm thép vẫn có rãnh nối. Dấu nào đáng tin để xác định cạnh công trình?",
          "Theo hình phản xạ của ô kính ít xước nhất.", "Theo rãnh dầm vật lý ở cùng cao độ.", "Theo đốm ánh sáng đứng yên.", 1,
          "Chỉ rãnh dầm có thể làm mốc vật lý độc lập với mặt kính."),
        choice("Phép thử nào loại được giả thuyết phản chiếu là đường nối?",
          "So lại hai mép khung và vết nứt từ vị trí khác.", "Đi về phía ảnh phản chiếu gần nhất.", "Dựa vào màu đèn xanh mạnh hơn.", 0,
          "Đã đối chiếu khung thật với hình phản xạ và không coi ảnh gương là cửa.")),
      Thread("Tín hiệu điện hay lối đi",
        choice("Tiếng rít thiết bị và đèn báo thay đổi có đủ kết luận một cửa dẫn đường đang hoạt động?",
          "Đủ vì cả hai cùng dùng điện.", "Đủ nếu đèn xanh hơn.", "Không; dấu điện và lối đi vật lý cần kiểm tra độc lập.", 2,
          "Đã tách khả năng có điện khỏi khả năng tồn tại lối đi."),
        choice("Khi kết cấu bị ngắt, phép kiểm tra nào đáng tin nhất?",
          "Chọn nhánh có tiếng rít cao.", "Đối chiếu vị trí dây, vết cắt và mép khung ở hai phía.", "Tin vết sáng kéo dài là hành lang liền.", 1,
          "Đã xác nhận tính liên tục của cấu kiện bằng các mối nối quan sát được."))),
    "0.2" to listOf(
      Thread("Bề mặt vừa sơn hay kết cấu cũ",
        choice("Sơn trắng mới và thảm đỏ sạch chứng minh điều gì về độ ổn định?",
          "Chỉ chứng minh bề mặt có vẻ mới, chưa chứng minh nền chịu lực.", "Sàn chắc hơn mọi khu cũ.", "Trần mới đồng nghĩa không thể sập.", 0,
          "Đã tách lớp hoàn thiện khỏi kết cấu chịu tải."),
        choice("Cách nào kiểm tra một khe cửa không tay nắm mà không đoán cơ chế bên trong?",
          "Đẩy mạnh vì lớp sơn mới.", "Dựa vào màu vữa ở một bên.", "Đối chiếu đường nối cửa, nền lót và lớp tường lộ ra.", 2,
          "Đã so ba bề mặt thực, không suy ra có cơ cấu khóa ẩn.")),
      Thread("Hai lớp sửa và nguy cơ sập",
        choice("Lớp sơn và trần có độ mới khác nhau. Kết luận nào cần được ưu tiên?",
          "Mọi đường phào đều là lối đi.", "Độ mới không thay thế kiểm tra vết nứt và chỗ thiếu điểm tựa.", "Cửa sơn trắng luôn thông nhau.", 1,
          "Đã phân biệt ảo giác đồng nhất với đứt gãy vật liệu."),
        choice("Gặp nền nghiêng cạnh mảng trần sụp, lựa chọn suy luận nào hợp lệ?",
          "Dựa vào các vết nứt liên tục trước khi coi đó là đường chịu lực.", "Đi theo đường thảm đỏ đẹp nhất.", "Coi khoảng trống là đường tắt bất kỳ.", 0,
          "Đã xác nhận giới hạn đường đi theo bằng chứng sàn-trần."))),
    "0.5" to listOf(
      Thread("Mực nước hay độ sâu",
        choice("Nước đục và dấu nước cao trên vách có cho biết độ sâu hiện tại ở mọi phòng?",
          "Có, vì đều là bệnh xá.", "Không; dấu nước có thể phản ánh thời điểm khác.", "Có nếu ánh đèn xanh còn sáng.", 1,
          "Đã phân biệt mực hiện tại với dấu vết mức nước cũ."),
        choice("Bước kiểm tra nào tránh nhầm phản quang với ranh giới sàn?",
          "Đi theo gợn sáng trên tường.", "Ước lượng bằng màu nước.", "So mép nền thực, vách và vật đứng yên tại cùng phòng.", 2,
          "Đã đối chiếu mốc sàn thay vì dựa vào hình ảnh trong nước.")),
      Thread("Nước bẩn và dây điện",
        choice("Dây cáp nứt gần nước chứng minh điều gì?",
          "Có nguy cơ điện, chưa khẳng định dây đang mang điện.", "Toàn bộ nước có điện.", "Mọi đèn tắt đều vô hại.", 0,
          "Đã đánh giá nguy cơ mà không gán trạng thái điện chưa đo."),
        choice("Những dấu nào có ích khi khảo sát từ vị trí an toàn?",
          "Chọn chỗ nước đen nhất.", "Dựa vào âm nổ và khoảng cách dây hở, không chạm nước để thử.", "Đo điện bằng tay vào vỏ cáp.", 1,
          "Đã chọn quan sát không tiếp xúc thay cho hành động nguy hiểm."))),
    "0.7" to listOf(
      Thread("Phong cách kiến trúc hay năm tháng",
        choice("Một dãy cửa theo nhiều phong cách xây dựng khác nhau chứng minh điều gì?",
          "Đã đi qua nhiều thế kỷ.", "Đồng hồ đứng yên chỉ năm hiện tại.", "Chỉ xác nhận nhiều kiểu vật liệu và hình dáng cạnh nhau.", 2,
          "Đã tách kiểu dáng khỏi khẳng định du hành thời gian."),
        choice("Đối chiếu nào có thể bác bỏ việc xếp phòng theo lịch sử giả định?",
          "Đường ghép hai chất liệu và cao độ bậc tại cùng một vị trí.", "Tên gọi thời đại đoán từ lan can.", "Màu bóng đèn cổ.", 0,
          "Đã kiểm tra kết cấu thay vì suy diễn niên đại.")),
      Thread("Kính, bậc thang và chiều nối",
        choice("Hai mặt kính phản nhiều lối có đủ chứng minh các lối thông nhau?",
          "Có nếu ánh sáng trùng màu.", "Không; phản xạ không xác nhận hình học đi được.", "Có nếu cửa chớp cùng mẫu.", 1,
          "Đã phân biệt lối nhìn thấy với tuyến có thể đi."),
        choice("Khi chiếu nghỉ đổi loại lan can, điều gì cần kiểm tra?",
          "Tin ký ức về thiết kế quen.", "Suy năm xây từ chất liệu.", "Theo đường sàn và chỗ nối bậc thực trước khi chọn nhánh.", 2,
          "Đã lựa chọn tuyến bằng tính liên tục vật lý."))),
    "dullness" to listOf(
      Thread("Tỷ lệ phòng có còn bất biến?",
        choice("Cửa và trần giữ hình dáng nhưng độ lớn đổi mạnh. Cái nào không đủ để nhận diện vị trí?",
          "Một tỷ lệ đo bằng mắt ở góc khác.", "Một dấu tự tạo gắn vào bề mặt.", "Đường ghép vách đối chiếu tại chỗ.", 0,
          "Đã phân biệt hình dạng giống nhau khỏi định danh vị trí."),
        choice("Thử nghiệm nào giúp nhận ra cùng một góc dù kích thước quan sát biến đổi?",
          "Đếm số bóng đèn không cần đánh dấu.", "So dấu tự tạo với đường khâu thảm thật qua hai lần quan sát.", "Tin cảm giác hành lang dài ngắn.", 1,
          "Đã dùng hai nguồn neo thay cho ước lượng tỷ lệ.")),
      Thread("Chồng mặt phẳng và đường nối",
        choice("Các vách trùng họa tiết giao nhau. Kết luận nào ít giả định nhất?",
          "Đó chắc chắn là cổng sang Level khác.", "Mọi lối song song sẽ gặp nhau.", "Chỉ nhận biết mép vật liệu giao nhau, chưa biết đường thông.", 2,
          "Đã tách giao hình học khỏi tính thông hành."),
        choice("Làm sao phân biệt hai lối trông như nhập làm một?",
          "So dấu vách tự tạo, mép thảm và hướng đường nối.", "Đi thẳng theo hoa văn vàng.", "Tin vị trí bóng sáng lớn.", 0,
          "Đã phân biệt bề mặt chồng và đường đi thực."))),
    "epsilon" to listOf(
      Thread("Hình chiếu cầu hay cùng cao độ",
        choice("Hai cầu cắt nhau khi nhìn từ dưới có nghĩa gì?",
          "Chắc chắn có giao điểm đi được.", "Có thể khác cao độ, phải kiểm tra bệ nối.", "Có thang máy vô hình.", 1,
          "Đã tách hình chiếu 2D khỏi kết nối ba chiều."),
        choice("Bằng chứng nào ưu tiên khi xác định thứ tự các tầng?",
          "Tiếng ù gần nhất.", "Kích thước bóng đèn.", "Cao độ chân cầu, bệ sàn và chiếu nghỉ quan sát được.", 2,
          "Đã đối chiếu các mốc cao độ thật.")),
      Thread("Đường hầm và hồi âm",
        choice("Hành lang trông ngắn nhưng mất nhiều bước hơn dự kiến; điều gì chưa được chứng minh?",
          "Chưa thể kết luận hầm tự dài ra chỉ từ bước chân.", "Chiều dài đã thay đổi chắc chắn.", "Có người đổi lối đi giữa chừng.", 0,
          "Đã giữ riêng sai số bước và biến dạng hình học."),
        choice("Muốn kiểm tra lối nối cầu-hầm, phải ưu tiên gì?",
          "Âm thanh ù lớn.", "Mốc giao thực ở bậc thang và lối hầm, không chỉ hình nhìn từ xa.", "Màu thảm giống nhau.", 1,
          "Đã đối chiếu lối thông theo điểm nối quan sát được."))),
    "ls_2" to listOf(
      Thread("Dãy nhãn thiếu dữ kiện",
        choice("Ba ô kệ có ký hiệu nhưng một nhãn mất. Điều nào là cách xử lý hợp lệ?",
          "Điền số kế tiếp theo thói quen.", "Cho rằng ô trống luôn là số không.", "Không khôi phục nhãn khi quy luật chưa được xác nhận độc lập.", 2,
          "Đã chống việc tự điền quy luật từ dãy dữ kiện thiếu."),
        choice("Những gì có thể phân biệt thứ tự thật với ảnh nhãn đảo?",
          "So dấu khung vật lý, mũi tên và vị trí ô khuyết từ hai phía.", "Chọn nhãn còn đọc được nhất.", "Tin mảnh giấy than còn cất mật mã.", 0,
          "Đã so nhãn với điểm neo thay vì đoán chuỗi số.")),
      Thread("Nhịp đếm và chốt không tên",
        choice("Ba tiếng gõ và các vạch đếm lệch chiều cho phép suy gì về số lần mở chốt?",
          "Chính xác ba lần.", "Chưa thể đồng nhất tiếng gõ với lần thao tác.", "Bốn lần vì có bốn lối.", 1,
          "Đã phân biệt tín hiệu đếm được với sự kiện chưa chứng minh."),
        choice("Để kiểm tra khung chốt kép mà không tạo mật mã tưởng tượng, nên làm gì?",
          "Gán mã từ số thanh răng nhìn thấy.", "Xoay thử mọi bánh răng.", "So khe chốt, vết mòn và điểm tựa trước khi kết luận một vị trí phù hợp.", 2,
          "Đã kiểm tra bằng dấu mòn và cấu kiện thực, không thêm mã số bí mật."))),
    "manila_room" to listOf(
      Thread("Ghi chép hay lời khẳng định",
        choice("Giấy mờ và mẩu ghi chú trên bàn chứng minh gì về tuyến đi?",
          "Chỉ chứng minh từng có giấy và vết ghi, không xác nhận nội dung đã mất.", "Có hướng dẫn thoát khỏi Level.", "Người ghi chắc chắn còn trong phòng.", 0,
          "Đã phân biệt vật chứng giấy với lời khai chưa đọc được."),
        choice("Phép so nào đáng tin hơn suy ra sơ đồ từ nét mực?",
          "Chọn chữ giống hướng nhất.", "Theo chiều đổ của ghế.", "Đối chiếu thớ sàn, cạnh bàn và dấu mực cùng chỗ.", 2,
          "Đã so ba dấu vật lý và giữ kết luận trong giới hạn quan sát.")),
      Thread("Yên tĩnh không phải an toàn",
        choice("Tiếng vọng yếu và ánh dịu có đủ cho kết luận nơi đây luôn an toàn?",
          "Có, vì không nghe thấy nguy hiểm.", "Không; đó chỉ là trạng thái quan sát tại chỗ.", "Có nếu bàn không bị dịch.", 1,
          "Đã tách cảm giác ổn định khỏi đảm bảo an toàn."),
        choice("Nhận diện một hành lang be trùng nhau cần căn cứ nào?",
          "So đường len tường, hướng thớ gỗ và dấu tự tạo.", "Tin mức độ yên tĩnh.", "Chọn góc sáng nhất.", 0,
          "Đã xác định vị trí bằng các dấu bền vững thay cho ánh sáng."))),
    "the_torment" to listOf(
      Thread("Vòng mộ có cùng tâm?",
        choice("Hai vòng bia trong sương trông đồng tâm. Suy luận nào là quá mức?",
          "Khoảng cách các bia không đều.", "Các cung có thể lệch tâm.", "Tất cả vòng có chung tâm vì hình dạng gần tròn.", 2,
          "Đã phân biệt cung nhìn được với tâm chưa xác định."),
        choice("Cần làm gì để kiểm tra tâm thay vì đi theo bóng trăng?",
          "Đo tương quan nhiều cung bia và dấu neo tại hai vị trí.", "Tin vầng trăng cố định chỉ Bắc.", "Chọn hàng bia nghiêng nhất.", 0,
          "Đã đối chiếu các cung và điểm neo, không dùng trăng làm la bàn.")),
      Thread("Ánh trăng và tượng vỡ",
        choice("Bóng tượng bất động dưới mặt trăng đứng yên có cho biết hướng ra nghĩa địa?",
          "Có, cứ ngược bóng mà đi.", "Không; nguồn sáng đứng yên không xác nhận lối ra.", "Có nếu bia đá cùng hướng.", 1,
          "Đã phân biệt bóng và phương vị lối đi."),
        choice("Chỗ nào đáng dùng làm mốc cấu trúc khi mọi vòng mộ lặp nhau?",
          "Hình mặt trăng sau tượng.", "Cảm giác độ lạnh trong sương.", "Vết vỡ bệ tượng cùng vị trí của bậc đá và dấu neo.", 2,
          "Đã xác định điểm mốc vật lý quanh tượng, không giả định nguồn gốc tượng."))),
    "red_rooms" to listOf(
      Thread("Ánh đỏ và bề mặt vật liệu",
        choice("Một vùng đỏ sẫm hơn trông sâu hơn. Có thể kết luận gì?",
          "Độ sáng thấp làm mất chi tiết, chưa chứng minh tường lõm.", "Đó là một khe cửa.", "Luôn có sinh vật dưới thảm.", 0,
          "Đã phân biệt màu và độ sâu hình học."),
        choice("Đối chứng nào giúp tìm mép tường thật?",
          "Đi theo vùng đỏ tối nhất.", "So đường chân tường, gấp giấy và vết ghép thảm.", "Dựa vào vệt mốc trông như hình người.", 1,
          "Đã dựa vào nối vật liệu thay vì ảo giác màu.")),
      Thread("Mốc hình vòm và mất tín hiệu",
        choice("Nấm trên tường tạo hình cửa và radio rè; kết luận nào có căn cứ?",
          "Radio gây ra cửa.", "Vết mốc là dấu mở cửa.", "Hai hiện tượng chưa xác nhận có liên hệ nhân quả.", 2,
          "Đã tách dấu mốc và nhiễu liên lạc."),
        choice("Phải kiểm tra gì trước khi coi một vách đỏ là lối đi?",
          "Mé gỗ, bản lề và đường ghép thực thay vì hình mốc trên giấy.", "Dò tần số rè lớn nhất.", "Tin âm thanh vọng ít.", 0,
          "Đã đối chiếu kết cấu cửa trước khi tin hình dạng ngụy trang.")))
  )

  fun forNode(nodeId: String, environments: List<WorldEnvironment>): List<WorldEvidenceLaw> {
    val threads = scenarios[nodeId] ?: error("world_riddle_node_unknown")
    require(threads.size == 2)
    val hubs = environments.filter { it.id == it.zoneId }
    require(hubs.size == 4)
    val prefix = nodeId.replace(Regex("[^a-z0-9]"), "-")
    fun law(index: Int, thread: Thread): WorldEvidenceLaw {
      val hub1 = hubs[index * 2].id
      val hub2 = hubs[index * 2 + 1].id
      val first = environments.filter { it.zoneId == hub1 && it.id != hub1 }
      val second = environments.filter { it.zoneId == hub2 && it.id != hub2 }
      require(first.size == 4 && second.size == 4)
      val sources = listOf(first[0], first[1], second[0], second[1])
      val methods = listOf("surface", "geometry", "markings", "reflection")
      val label = "$prefix-${index + 1}"
      val probes = sources.mapIndexed { i, environment ->
        WorldLawProbe("$label:p${i + 1}", environment.id, methods[i],
          "Quan sát được tại ${environment.name}: ${environment.canon} " +
            "Đây là dữ liệu hiện trường, không phải lời giải đã được chứng minh.")
      }
      fun toStep(n: Int, c: Choice) = WorldInferenceStep("$label-step$n", c.question,
        c.possibilities.mapIndexed { index, text ->
          WorldInferenceOption("option-${index + 1}", text)
        }, "option-${c.answer + 1}", c.conclusion)
      return WorldEvidenceLaw(label,
        if (index == 0) WorldLawFamily.PHYSICAL_CORRELATION else WorldLawFamily.SPATIAL_ANCHOR,
        probes, second[1].id,
        "Đã hoàn tất chuỗi kiểm chứng: ${thread.focus}. Các kết luận chỉ áp dụng cho hiện trường đã khảo sát.",
        "Chưa có đủ đối chứng hoặc chưa hoàn thành các bước lập luận cho ${thread.focus}.",
        physicalAnchors = if (index == 0) setOf(second[0].id) else setOf(second[0].id, second[1].id),
        comparisonPairs = setOf(first[0].id to first[1].id),
        prerequisites = if (index == 0) emptySet() else setOf("$prefix-1"),
        inferenceSteps = listOf(toStep(1, thread.claim), toStep(2, thread.test)))
    }
    return threads.mapIndexed(::law)
  }
}
