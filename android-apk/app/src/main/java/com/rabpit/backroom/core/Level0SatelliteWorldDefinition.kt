package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/**
 * Author-directed expansions grounded in the existing exact-node Level 0 satellite
 * environment notes. They are NEW project scenes, not pre-existing wiki facts.
 * No route, Entity, reward, supply, or guaranteed exit is defined here.
 */
object Level0SatelliteEnvironments {
  private fun group(node: String, tag: String, zone: Int, name: String, description: String,
                    subs: List<Pair<String, String>>): List<WorldEnvironment> {
    require(subs.size in 4..5)
    val id = "sat_${tag}_z${zone}"
    return listOf(WorldEnvironment(id, name, id, description)) +
      subs.mapIndexed { index, sub ->
        WorldEnvironment("${id}_s${index + 1}", sub.first, id, sub.second)
      }
  }
  val catalogs: Map<String, List<WorldEnvironment>> = linkedMapOf(
    "0.1" to listOf(
      group("0.1", "01", 1, "Sảnh Khung Kính", "Sảnh kim loại rộng với những mặt kính lớn rạn nứt; các dầm chịu lực lộ ra sau lớp ốp bong và ánh sáng lạnh đọng trên nền phủ bụi.", listOf(
        "Lối Kính Mẻ" to "Hành lang kẹp giữa hai dãy kính sứt mép; một số ô phản chiếu lệch vị trí của các dầm thép phía sau.",
        "Đài Quan Sát Nứt" to "Bệ nhìn cao có lan can gãy và nền kim loại rung nhẹ mỗi khi dòng điện chạy qua kết cấu.",
        "Tiền Sảnh Vắng" to "Khoảng đón khách trống với khung chỉ dẫn đã mất chữ; các vệt bụi dưới chân tường không nối tiếp nhau.",
        "Khe Giữa Hai Vách" to "Lối hẹp hình thành giữa kính vỡ và khung thép lệch; phía sâu chỉ thấy ánh sáng từ các dây dẫn trần."
      )),
      group("0.1", "01", 2, "Ga Điện Tử Bỏ Hoang", "Một dãy bảng điện, màn hình tối và tủ điều khiển nằm trên các bệ dài. Thiết bị cũ còn phát tiếng rít ngắt quãng dù công dụng không rõ.", listOf(
        "Phòng Màn Hình Tắt" to "Các màn hình nứt xếp thành hàng, mặt kính còn phản sáng nhưng không hiển thị dữ liệu nào có thể đọc.",
        "Kho Dây Chằng Chịt" to "Bó dây dày luồn qua tủ mở, một vài lõi cáp trơ đồng có nguy cơ còn điện.",
        "Trạm Nút Bấm" to "Bàn điều khiển thiếu nhãn, các nút kẹt trong ổ; tiếng lách cách đôi lúc vọng từ hộc kín.",
        "Hành Lang Đèn Xanh" to "Một dải đèn báo xanh yếu kéo dài sát sàn rồi đứt giữa các tấm che gỉ."
      )),
      group("0.1", "01", 3, "Cầu Nối Chênh Vênh", "Các cầu đi bộ bằng lưới thép nối những tầng khung sắt và kính khác cao độ; bên dưới là khoảng tối khó đo chiều sâu.", listOf(
        "Nhịp Cầu Rạn" to "Một đoạn cầu bị xoắn dọc trục; bulông bong ra để lại những lỗ đen quanh chân lan can.",
        "Thang Gãy Bậc" to "Cầu thang thép có nhiều bậc vênh, mép sắc và khoảng trống giữa các chiếu nghỉ.",
        "Bệ Chuyển Tầng" to "Sàn trung gian hình lục giác mở ra nhiều nhánh cầu, biển hướng dẫn đã bong sạch.",
        "Vực Khung Thép" to "Giữa hai giàn chịu lực là giếng không gian thẳng đứng; tiếng vật rơi bị nuốt trong tiếng ù điện."
      )),
      group("0.1", "01", 4, "Lõi Công Trình Hư Hại", "Phần kết cấu trong cùng tối hơn, đầy vỏ máy tháo dở, cột thép nứt mối hàn và các mảng kính rơi chất đống.", listOf(
        "Buồng Vỏ Máy" to "Một buồng hình hộp bị mở tung, bề mặt máy phủ bụi và dấu cọ kim loại dài.",
        "Ngách Ống Thông" to "Hốc kỹ thuật phía sau tường thép chứa lưới hút bụi và tiếng gió luồn từ nơi không thấy.",
        "Sàn Tia Lửa" to "Mặt sàn sẫm cháy cạnh các bó cáp lộ; tia sáng chớp ngắn khiến việc quan sát khó ổn định.",
        "Khoang Kết Cấu" to "Dầm chéo ép lối đi thành đường zíc zắc, kính vụn lạo xạo trên mỗi chỗ đặt chân."
      ))
    ).flatten(),
    "0.2" to listOf(
      group("0.2", "02", 1, "Dãy Phòng Vừa Sửa", "Những phòng tường sơn trắng có mùi vật liệu cũ và thảm đỏ khô, sạch khác thường; các đường ghép tường bắt đầu nứt tại góc.", listOf(
        "Phòng Sơn Trắng" to "Một phòng hình chữ nhật với tường trắng đồng màu; dưới chân tường hiện vệt nứt nhỏ kéo dài qua lớp sơn.",
        "Hành Lang Thảm Đỏ" to "Dải thảm đỏ phẳng chạy dưới đèn đều sáng; mép thảm có chỗ bật lên để lộ nền lót rỗng.",
        "Cửa Không Tay Nắm" to "Các cánh cửa trắng liền vách không có ổ khóa hay tay nắm; khe cửa mang mùi bụi vữa.",
        "Phòng Trần Mới" to "Tấm trần màu kem có vết vá sáng hơn xung quanh và tiếng gõ nhỏ mỗi khi nền rung."
      )),
      group("0.2", "02", 2, "Kết Cấu Đang Bong", "Các bề mặt trông mới bị tách từng lớp: thạch cao rạn, phào tường xô lệch và trần có nguy cơ rơi.", listOf(
        "Góc Tường Tách" to "Một đường nứt chạy từ mặt sàn lên cao, làm hai lớp tường lộ ra màu vật liệu khác nhau.",
        "Trần Sụp Dở" to "Một mảng trần xệ xuống trên lớp bụi trắng, dây treo và gạch vỡ chắn bớt lối.",
        "Phòng Thảm Lún" to "Thảm đỏ trũng sâu giữa phòng; phần nền bên dưới không còn tạo cảm giác chắc chắn.",
        "Ngách Vữa Rơi" to "Mảng vữa vụn tích thành dải bên cửa; tiếng rơi lạch cạch phát ra từ khoảng khuất sau tấm ốp."
      )),
      group("0.2", "02", 3, "Mê Cung Hành Lang Vá", "Các hành lang được sửa ở những mức khác nhau và đổi chiều bất ngờ, khiến phần cũ và mới nối nhau không hợp lý.", listOf(
        "Đường Phào Ngắt" to "Các đường phào trắng kết thúc giữa chừng rồi xuất hiện ở cao độ khác trên vách đối diện.",
        "Hành Lang Hai Lớp" to "Tại một góc cua, lớp sơn mới bị khoét, lộ mặt giấy vàng cũ nằm sâu phía trong.",
        "Phòng Nối Sai Góc" to "Hai cửa đối diện nhau nhưng bước qua lại dẫn tới không gian lệch trục, không thể đối chiếu bằng mắt.",
        "Đoạn Vách Rỗng" to "Dãy tường gõ lên phát tiếng rỗng, vài chỗ lộ xương thép đã cong và vữa vụn bám dưới chân."
      )),
      group("0.2", "02", 4, "Vùng Sập Gãy", "Khu kết thúc bằng sàn nứt, trần vỡ và những khoảng trống nơi vật liệu xây dựng rơi xuống; không bề mặt nào mặc nhiên an toàn.", listOf(
        "Hố Sàn Nứt" to "Một khe nứt mở ngang thảm đỏ, mép bê tông vụn và thiếu điểm tựa chắc chắn.",
        "Bãi Tấm Trần" to "Những mảnh trần trắng rơi chồng lên nhau, mặt dưới bám lớp bụi tối và sợi cáp đứt.",
        "Cột Bong Lớp" to "Một cột vuông mất lớp ốp, phần lõi lộ vết cắt và các mảnh sơn vẫn tiếp tục rơi.",
        "Hành Lang Gãy Nhịp" to "Nền hành lang sụt từng đoạn khiến đường đi bị chia bởi các bậc bất thường."
      ))
    ).flatten(),
    "0.5" to listOf(
      group("0.5", "05", 1, "Lối Ngập Nước Lạnh", "Hành lang bệnh xá hẹp bị nước lạnh và đục ngập gần đầu gối. Giấy tường kem tróc ở đường nước, đèn xanh lủng lẳng bên trên.", listOf(
        "Dãy Đèn Treo" to "Đèn chữ nhật đung đưa trên dây sờn, ánh xanh soi những quầng nước đục và dây dẫn đáng ngại.",
        "Đường Nước Đen" to "Hành lang thấp có mặt nước tối che kín nền; mỗi gợn sóng phản lên tường thành vệt dài.",
        "Hốc Tường Mục" to "Giấy tường kem bong thành mảng, để lộ nền ẩm sẫm với dấu nước dừng ở nhiều độ cao.",
        "Ngã Rẽ Chìm" to "Hai lối hẹp gặp nhau tại vùng nước sâu hơn; đường mép sàn hoàn toàn khuất."
      )),
      group("0.5", "05", 2, "Khu Khám Chữa Bỏ Hoang", "Cửa phòng khám và những vách ngăn mục hiện lên sau hành lang ẩm. Không khí nặng mùi thuốc cũ lẫn nước ô nhiễm.", listOf(
        "Phòng Giường Rỉ" to "Khung giường kim loại trơ trọi đứng giữa vũng nước, bánh xe chìm một phần trong lớp cặn.",
        "Quầy Hồ Sơ Ướt" to "Bàn tiếp nhận hỏng phủ giấy nhão không đọc được; ngăn kéo méo ép vào vách ẩm.",
        "Phòng Rèm Mốc" to "Những tấm rèm khám bệnh bám mốc xám lơ lửng trên mặt nước, tạo nhiều góc khuất.",
        "Buồng Bồn Rửa" to "Bồn men rạn và vòi gỉ nằm trong khoang chật; từng giọt nhỏ xuống chậu nước không rõ nguồn."
      )),
      group("0.5", "05", 3, "Cánh Hành Lang Cách Ly", "Những đoạn hành lang có cửa nặng, ô kính mờ và biển cảnh báo cũ. Nguy cơ bệnh tật chỉ gắn với môi trường bẩn, không mặc định tác nhân siêu nhiên.", listOf(
        "Cửa Kính Đục" to "Một cửa dày có ô nhìn mờ cặn, phía sau là ánh xanh yếu và bóng của các khung giường.",
        "Phòng Áp Tường" to "Phòng nhỏ nhiều lớp vách, tấm cách âm ẩm sũng và mùi nấm mốc rõ hơn ngoài hành lang.",
        "Lối Cửa Kép" to "Hai cánh cửa nối bằng khoang ngắn ngập nước; ổ bản lề gỉ kêu mỗi khi có dao động.",
        "Góc Biển Cảnh Báo" to "Biển kim loại cong vênh còn vài vệt màu, nhưng chữ đã mất bởi nước và ăn mòn."
      )),
      group("0.5", "05", 4, "Khu Kỹ Thuật Bệnh Xá", "Ống cấp thoát nước, máng cáp và buồng máy ẩm đan xen. Những đoạn điện hở cạnh nước đọng là hiểm họa có thể kiểm chứng.", listOf(
        "Trạm Bơm Câm" to "Vỏ bơm ngập nửa thân, các đồng hồ áp lực mờ và van tay bị phủ cáu.",
        "Hành Lang Cáp Ướt" to "Dây điện chảy dọc tường gần mặt nước, chỗ bọc bị nứt và có tiếng nổ lép bép xa.",
        "Giếng Ống Rò" to "Nước chảy qua khe ống đứng, tiếng vọng làm khó phân biệt rò rỉ ở tầng nào.",
        "Phòng Máy Khô Nửa" to "Một bục máy nhô khỏi nước cạnh sàn ngập; mặt thép phủ gỉ và bụi ẩm."
      ))
    ).flatten(),
    "0.7" to listOf(
      group("0.7", "07", 1, "Phố Kiến Trúc Cũ", "Những lối đi giống phố trong nhà, mặt đứng cổ điển và cửa gỗ xếp dọc dưới ánh sáng vô định. Đây là dấu ấn kiến trúc, không chứng minh quay về quá khứ.", listOf(
        "Hiên Nhà Vắng" to "Hàng hiên gỗ sẫm có cột chạm khắc cũ; dưới chân là bụi mịn không dấu chân rõ.",
        "Ngõ Đèn Đồng" to "Dãy đèn kiểu cổ soi vòm tường bong tróc, ánh sáng biến mất khi qua một góc cua.",
        "Sảnh Đồng Hồ Chết" to "Chiếc đồng hồ mặt tròn đứng trên tường nứt, kim đứng yên giữa các hoa văn phủ bụi.",
        "Hành Lang Cửa Chớp" to "Cửa chớp gỗ đóng kín hai bên lối hẹp; các thanh ngang phát tiếng rung rất nhỏ."
      )),
      group("0.7", "07", 2, "Dải Công Trình Chuyển Tiếp", "Cột đá cũ dần nhường chỗ cho kính và thép, song từng mảng vật liệu giao nhau đột ngột như các mô hình dựng thiếu chỗ nối.", listOf(
        "Cầu Thang Hai Kiểu" to "Nửa dưới là bậc đá mòn, nửa trên là thép sơn bạc; độ cao bậc đổi bất ngờ.",
        "Lối Vòm Trắng" to "Vòm xây kiểu cũ nối sang phòng trần phẳng và ống đèn hiện đại không ăn khớp.",
        "Phòng Trưng Bày Trống" to "Bệ đá và giá kính cùng nằm trong phòng bỏ trống, nền gạch chuyển màu giữa chừng.",
        "Dãy Cửa Lệch Đời" to "Những cánh cửa có kiểu tay nắm khác nhau đứng sát nhau mà không có biển giải thích."
      )),
      group("0.7", "07", 3, "Khu Mặt Kính Hiện Đại", "Tường kính lớn, đèn tuyến tính và mặt sàn đánh bóng gợi kiến trúc đương đại, nhưng không có bằng chứng đây là một thời đại có thật.", listOf(
        "Hành Lang Phản Sáng" to "Hai dải kính soi nhau tạo chiều sâu khó tin; khung cửa phía xa biến mất theo góc nhìn.",
        "Phòng Vách Trong" to "Các tấm kính mờ phân chia phòng mở, phản chiếu những lối chưa chắc thông nhau.",
        "Bệ Thang Cuốn Đứng" to "Một thang cuốn ngừng hoạt động kết thúc trước bức tường trống; bậc thép phủ bụi.",
        "Sảnh Mái Phẳng" to "Trần thấp lắp đèn dài và mảng kính rạn; âm thanh bước chân vọng trên nền cứng."
      )),
      group("0.7", "07", 4, "Giao Điểm Ký Ức Kiến Trúc", "Các dạng kiến trúc khác kỷ nguyên chồng vào một mặt bằng, làm cảm giác thời gian rối loạn nhưng không xác nhận dịch chuyển thời gian.", listOf(
        "Cột Ghép Niên Đại" to "Một cột có chân đá chạm và thân bê tông nhẵn, đường tiếp giáp gồ ghề.",
        "Ngã Tư Ba Chất Liệu" to "Ba hành lang mở từ sàn gỗ, gạch và bê tông khác nhau gặp nhau tại một điểm.",
        "Phòng Bóng Nắng Giả" to "Vệt sáng giống nắng chiều nằm trên tường dù không tìm thấy cửa sổ hay nguồn sáng ngoài.",
        "Lối Cầu Thang Sai Mốc" to "Các chiếu nghỉ mang kiểu lan can thay đổi liên tiếp, nhưng không có đồng hồ hay dấu hiệu xác nhận năm tháng."
      ))
    ).flatten(),
    "manila_room" to listOf(
      group("manila_room", "MN", 1, "Phòng Bàn Trung Tâm", "Một căn phòng beige gần như ổn định với ván sàn gỗ và chiếc bàn ở giữa. Ghi chép người sống sót có thể hiện diện, nhưng không xác nhận ai còn ở đây.", listOf(
        "Mép Bàn Ghi Chép" to "Góc bàn có những vết xước và mẩu ghi chú cũ; nội dung nào không đọc được phải để là chưa rõ.",
        "Khoảng Sàn Trống" to "Ván gỗ cũ trải rộng quanh bàn, vài mối ghép kêu nhỏ khi có trọng lượng đặt lên.",
        "Ghế Lệch Chân" to "Chiếc ghế trống nghiêng cạnh bàn, bóng đổ nằm cố định dù ánh sáng phòng không rõ nguồn.",
        "Góc Tường Beige" to "Một góc tường nhợt gần như không có đồ vật; đường chân tường đều hơn phần còn lại."
      )),
      group("manila_room", "MN", 2, "Dãy Vách Yên Tĩnh", "Những góc phòng và đoạn tường ngắn giữ âm thanh nhỏ đến mức tiếng áo chạm tay cũng rõ. Sự yên ắng không đồng nghĩa bảo đảm an toàn.", listOf(
        "Hốc Không Tiếng" to "Một hốc tường hình vuông nuốt tiếng vọng, còn đường viền gỗ vẫn nguyên vẹn.",
        "Đường Len Chân Tường" to "Tấm len gỗ chạy liền mạch qua một góc cua, các đinh nhỏ phủ bụi đều nhau.",
        "Khoảng Trắng Cạnh Cửa" to "Mảng tường cạnh khung cửa hoàn toàn sạch giấy ghi chú, đối lập chiếc bàn có vết mực.",
        "Góc Ánh Sáng Dịu" to "Ánh vàng nhẹ trải trên nền mà không có bóng đèn nhìn thấy từ vị trí đứng."
      )),
      group("manila_room", "MN", 3, "Vùng Giấy Tờ Bỏ Lại", "Các dấu mực, giấy mục và sổ rời gợi những người từng dừng chân. Không suy ra mật mã, vật phẩm hay chỉ dẫn thoát hiểm chỉ từ cách bày chúng.", listOf(
        "Ngăn Kéo Cũ" to "Ngăn bàn gỗ kẹt cứng, mặt trước mòn ở điểm thường nắm kéo; bụi nằm nguyên dọc đường ghép.",
        "Tờ Giấy Mờ" to "Giấy cũ cong mép trên mặt gỗ, nét viết nhòe không đủ để xác nhận một lộ trình.",
        "Vệt Mực Trên Ván" to "Một vệt mực dài bám theo thớ gỗ cạnh bàn, không có dấu hiệu mới khô.",
        "Giá Tài Liệu Rỗng" to "Một giá nhỏ áp tường chỉ còn nẹp gỗ và bụi phủ thành hình chữ nhật."
      )),
      group("manila_room", "MN", 4, "Rìa Không Gian Be", "Những đoạn vách ít thay đổi nối thành vùng ngoại vi rất khó phân biệt. Chỉ quan sát vật lý mới xác nhận được điểm đã ghé qua.", listOf(
        "Lối Tường Song Song" to "Hai vách be đặt gần nhau dẫn tới một khoảng hẹp tối hơn trung tâm phòng.",
        "Mặt Sàn Đổi Thớ" to "Ván gỗ đổi hướng thớ tại một đường ghép, làm âm thanh bước chân thay đổi.",
        "Cạnh Cửa Ít Sáng" to "Một khung cửa không biển tên nằm sát góc tối, phía trong chưa có dữ kiện chắc chắn.",
        "Góc Bụi Phẳng" to "Lớp bụi nằm đều trên chân tường và mặt sàn, không tự nói lên lần cuối có người đi qua."
      ))
    ).flatten(),
    "the_torment" to listOf(
      group("the_torment", "TM", 1, "Vành Mộ Ngoài", "Một vành bia mộ xám đặt theo vòng tròn trên đất lạnh. Sương phủ thấp, cây chết vươn cành dưới mặt trăng đứng yên.", listOf(
        "Lối Bia Nghiêng" to "Các bia đá lệch góc tạo hành lang cong; tên khắc không còn đủ nét để đọc.",
        "Đất Giữa Hai Mộ" to "Dải đất cứng kẹp giữa hai nấm mộ, không có cây cỏ và dấu đào mới xác nhận.",
        "Bờ Sương Thấp" to "Sương bò sát mắt cá chân và che mép nền đất, khiến ước lượng khoảng cách dễ sai.",
        "Gốc Cây Trơ" to "Thân cây không lá tách thành nhiều nhánh tối, bóng kéo dài dù mặt trăng không đổi vị trí."
      )),
      group("the_torment", "TM", 2, "Vành Mộ Đồng Tâm", "Các vòng mộ kế tiếp lặp hình học tròn nhưng khoảng cách không đều, làm việc xác định tâm chỉ bằng mắt trở nên khó khăn.", listOf(
        "Cung Đá Gãy" to "Một cung bia vỡ tạo thành đường cong cụt, mảnh đá chìm nửa trong đất xám.",
        "Lối Giữa Hai Vành" to "Khoảng đi hẹp giữa hai vòng mộ, mọi hướng nhìn đều gặp những hình dáng bia giống nhau.",
        "Hàng Bia Không Chữ" to "Một đoạn vòng mộ chỉ còn mặt bia trơn mài mòn, không thấy dấu mới khắc.",
        "Điểm Giao Vòng" to "Hai dải nền tròn cắt nhau trong sương mù; âm thanh bước chân đổi đột ngột trên đá."
      )),
      group("the_torment", "TM", 3, "Rừng Cây Chết", "Những cây khẳng khiu chen giữa các ngôi mộ. Tán lá biến mất từ lâu, cành khô quệt vào nhau trong khoảng lặng.", listOf(
        "Lối Cành Đan" to "Cành khô giao nhau phía trên đầu, một vài đoạn gãy nằm trên nền đất trắng xám.",
        "Bãi Rễ Lộ" to "Rễ cây trồi quanh chân bia, đất mỏng bám trên mặt gỗ xám mục và len vào khe đá nứt.",
        "Khoảng Trống Không Gió" to "Một khoảng rừng cây yên tuyệt đối, không có tiếng lá để giúp xác định phương hướng.",
        "Bóng Thân Chồng" to "Nhiều bóng cây chồng lên cùng vị trí trong ánh trăng cố định, che khuất mép những nấm mộ."
      )),
      group("the_torment", "TM", 4, "Quảng Trường Tượng Vỡ", "Trung tâm nghĩa địa có bức tượng choàng mũ trùm bị hư hại. Nguồn gốc tượng, người được tưởng niệm và mọi hiện tượng chưa được xác định.", listOf(
        "Bệ Tượng Nứt" to "Bệ đá thấp vỡ một góc, bụi mịn đọng trong các đường khắc đã mờ và dưới vạt tượng rơi.",
        "Vạt Áo Đá" to "Mảnh tượng mô phỏng tà áo choàng nằm dưới chân bệ, rìa gãy sắc phủ một lớp bụi xám dày.",
        "Mặt Trăng Sau Tượng" to "Ánh trăng bất động tạo quầng bạc phía sau dáng tượng, khiến gương mặt nằm trong bóng.",
        "Bậc Đá Câm" to "Các bậc đá dẫn tới bệ tượng hấp thu tiếng vọng; quanh chân là những mẩu đá vụn."
      ))
    ).flatten(),
    "red_rooms" to listOf(
      group("red_rooms", "RR", 1, "Dãy Tường Đỏ Thẫm", "Giấy dán tường đỏ tối bọc những phòng giống Level 0 bị nhuộm màu. Đèn đỏ yếu làm các góc tường khó phân biệt.", listOf(
        "Hành Lang Đỏ Hẹp" to "Một dải hành lang tối màu với giấy tường nhăn và bóng đỏ loang trên trần.",
        "Phòng Vách Nhăn" to "Các nếp giấy đỏ dồn thành mảng lớn, tạo những đường cong như vết gấp sâu.",
        "Ngã Ba Đèn Đỏ" to "Ba lối giao nhau dưới đèn đỏ lờ mờ, ánh sáng không lan tới cuối các nhánh.",
        "Góc Bóng Mật" to "Một góc phòng nơi ánh đỏ đặc lại, làm mép chân tường chìm trong nền tối."
      )),
      group("red_rooms", "RR", 2, "Thảm Dính Mục", "Thảm sẫm có những đoạn dính và ráp bất thường, mùi mốc trộn với bụi cũ. Các vệt kéo không chứng minh có sinh vật ở gần.", listOf(
        "Lối Thảm Bết" to "Dải thảm có bề mặt bết giữ dấu chà kéo dài, bước chân phát âm khô khó chịu.",
        "Phòng Sợi Mốc" to "Những sợi mốc mảnh bám thành vệt dọc mép thảm và ván chân tường, đọng nhiều ở góc tối.",
        "Mép Thảm Bong" to "Thảm đỏ bật mép, lộ nền sẫm đầy bụi và các mối ghép không thẳng dưới lớp sợi rách.",
        "Vùng Sàn Nhám" to "Mặt sàn đổi từ dính sang ráp trong vài bước, những mảnh vụn nhỏ mắc vào giày."
      )),
      group("red_rooms", "RR", 3, "Vùng Nấm Tường", "Mốc xám đen ăn lan trên nền đỏ, khiến một số mảng tường như có chiều sâu. Không diễn giải vết ố thành thực thể thật.", listOf(
        "Mảng Mốc Hình Vòm" to "Một vết mốc cong trên tường đỏ giống cửa vòm, nhưng bề mặt vẫn kín.",
        "Phòng Mùi Chua" to "Không khí mang mùi ẩm chua mạnh, các mảng mốc tụ ở đường nối trần.",
        "Tường Chấm Đen" to "Những đốm tối rất nhỏ lặp lại thành cụm dọc giấy tường sẫm, kéo tới sát đường viền trần.",
        "Góc Ẩm Xám" to "Một đoạn góc tường mất sắc đỏ vì lớp mốc nhạt phủ lên, nền thảm vẫn tối."
      )),
      group("red_rooms", "RR", 4, "Hành Lang Mất Sóng", "Tín hiệu liên lạc suy yếu giữa các hành lang đỏ đậm. Sự ngắt quãng không tự chứng minh có nguồn gây nhiễu hay ai đang gọi.", listOf(
        "Buồng Tiếng Rè" to "Một phòng hẹp nơi âm thanh máy liên lạc biến thành tiếng rè không đều.",
        "Đoạn Đèn Mỏi" to "Đèn đỏ yếu đến mức các góc sàn chỉ hiện thành mảng đen, không phân biệt vật nhỏ.",
        "Cửa Không Biển" to "Một cánh cửa đỏ gần trùng với vách, mép dưới phủ bụi sẫm; bản lề chìm vào lớp giấy tường nhăn.",
        "Ngõ Mất Vọng" to "Hành lang ngắn khiến tiếng gọi gần như không dội lại, dù vách cứng bao quanh."
      ))
    ).flatten()
  )
  fun forNode(node: String): List<WorldEnvironment> = catalogs[node] ?: error("satellite_node_unknown")
}

/**
 * One deterministic 4-zone x 4-subzone graph per exact Canon node.
 * The zone ring and local cycles prevent a single corridor from trapping the run.
 * Never modifies LevelRouteRegistry's mandatory one-way cross-level progression.
 */
class Level0SatelliteWorldDefinition(override val nodeId: String) : WorldDefinition {
  override val version = 1
  override val objective = "Khảo sát những khu vực của " +
    (LevelRouteRegistry.node(nodeId)?.name ?: nodeId) + " và ghi nhận bằng chứng có thể kiểm chứng."
  override val requireAlternateRoutes = true
  override val environments = Level0SatelliteEnvironments.forNode(nodeId)
  override val fieldStudies = environments.filter { it.id != it.zoneId }.map {
    WorldFieldStudy(it.id, "Khảo sát " + it.name, it.canon)
  }
  override fun generate(seed: Long): JSONObject {
    val rng = Random(seed)
    val hubs = environments.filter { it.id == it.zoneId }.map { it.id }.toMutableList()
    val keys = environments.map { it.id }
    val positions = keys.indices.toMutableList()
    java.util.Collections.shuffle(positions, rng)
    val ids = keys.mapIndexed { index, key -> key to ("sat:r" + positions[index].toString().padStart(3, '0')) }.toMap()
    val edges = keys.associateWith { linkedSetOf<String>() }
    fun link(a: String, b: String) {
      edges.getValue(a).add(b); edges.getValue(b).add(a)
    }
    java.util.Collections.shuffle(hubs, rng)
    hubs.indices.forEach { link(hubs[it], hubs[(it + 1) % hubs.size]) }
    hubs.forEach { hub ->
      val children = environments.filter { it.zoneId == hub && it.id != hub }.map { it.id }.toMutableList()
      java.util.Collections.shuffle(children, rng)
      val cycle = listOf(hub) + children
      cycle.indices.forEach { link(cycle[it], cycle[(it + 1) % cycle.size]) }
    }
    return JSONObject().put("version", WorldGraphRuntime.GRAPH_VERSION)
      .put("seed", seed.toString()).put("nodeId", nodeId)
      .put("definitionVersion", version).put("entryRoomId", ids.getValue(hubs.first()))
      .put("passages", JSONArray()).put("lawInstances", JSONArray())
      .put("rooms", JSONArray().apply {
        keys.forEach { key ->
          val e = environments.first { it.id == key }
          put(JSONObject().put("id", ids.getValue(key)).put("environmentId", key)
            .put("zoneId", e.zoneId)
            .put("adjacentRoomIds", JSONArray(edges.getValue(key).map { ids.getValue(it) })))
        }
      })
  }
  override fun validateLayout(graph: JSONObject) {
    val rooms = graph.getJSONArray("rooms")
    val catalog = environments.map { it.id }.toSet()
    val ids = (0 until rooms.length()).map { rooms.getJSONObject(it).getString("environmentId") }
    require(rooms.length() == 20 && ids.toSet() == catalog && ids.distinct().size == ids.size) { "satellite_catalog" }
    val canonical = generate(graph.getString("seed").toLong())
    require(graph.getJSONArray("rooms").toString() == canonical.getJSONArray("rooms").toString() &&
      graph.getJSONArray("passages").toString() == canonical.getJSONArray("passages").toString() &&
      graph.getString("entryRoomId") == canonical.getString("entryRoomId")) { "satellite_seed_topology" }
  }
}
