package com.rabpit.backroom.core

/** Authored environment identity, never a route, spawn rule or mini-map index. */
object Level0Environments {
  data class Environment(val id: String, val name: String, val parent: String?, val reference: String, val canon: String)
  val all = listOf(
    Environment("yellow_rot", "Vàng Úa", null, "1", "Một hành lang vàng dài hun hút, hàng chục cánh cửa trống rỗng và bóng người đen đứng bất động dưới ngọn đèn cuối cùng."),
    Environment("deadlight", "Đèn Chết", null, "2", "Một căn phòng vàng tối om, chỉ còn một bóng đèn chớp tắt phía trên và chiếc bóng méo mó kéo dài trên bức tường trống."),
    Environment("wetfloor", "Thảm Sũng", null, "3", "Hành lang vàng ngập nước đen nông, thảm sàn nổi thành những khối hình bất thường và hàng loạt vệt kéo lê hướng về bóng tối."),
    Environment("silent_hall", "Lối Câm", null, "4", "Một hành lang vàng cực hẹp, hoàn toàn không có bóng đèn sáng, với những dấu bàn tay đen kéo dài dọc bức tường."),
    Environment("hollow_ceiling", "Trần Rỗng", null, "5", "Góc nhìn từ sàn nhà lên trần giả vỡ nát, những sợi dây điện rủ xuống và một khoảng tối sâu không thấy đáy."),
    Environment("afterimage", "Hồi Ảnh", null, "6", "Một căn phòng vàng trống trải với chiếc ghế đơn độc, phía sau là những mảng ố trên tường tạo thành hàng chục khuôn mặt mờ nhạt."),
    Environment("the_loop", "Vòng Lặp", null, "7", "Bốn hành lang vàng giống hệt nhau giao tại một điểm, mỗi hành lang có cùng một chiếc ghế gãy và cùng một dấu tay trên tường."),
    Environment("the_fracture", "Rạn Nứt", null, "8", "Một căn phòng vàng méo lệch, nền thảm bị xé rách, các vách tường nứt toác và một khe tối khổng lồ nằm sâu giữa những khối kiến trúc không thể giải thích."),
    Environment("phong_moc", "Phòng Mốc", "yellow_rot", "1.1", "Những căn phòng vàng phủ đầy nấm mốc đen. Giấy dán tường bong ra từng mảng, để lộ những vết ố giống hình người."),
    Environment("hanh_lang_dai", "Hành Lang Dài", "yellow_rot", "1.2", "Hành lang thẳng tắp với hàng trăm bóng đèn huỳnh quang. Càng đi, điểm cuối càng có vẻ xa hơn."),
    Environment("goc_khuat", "Góc Khuất", "yellow_rot", "1.3", "Các căn phòng nhỏ nối nhau bằng những góc cua hẹp. Một bóng đen đôi khi xuất hiện ở góc mắt rồi biến mất."),
    Environment("phong_trong", "Phòng Trống", "yellow_rot", "1.4", "Căn phòng rộng không có cửa sổ hay đồ vật. Giữa nền thảm xuất hiện một dấu lõm giống cơ thể người nằm bất động."),
    Environment("phong_chop", "Phòng Chớp", "deadlight", "2.1", "Đèn huỳnh quang liên tục nhấp nháy. Trong khoảnh khắc tối, hình dạng đồ vật dường như thay đổi."),
    Environment("hanh_lang_toi", "Hành Lang Tối", "deadlight", "2.2", "Những bóng đèn đã cháy hoàn toàn. Chỉ còn ánh sáng yếu ớt hắt tới từ các phòng xa."),
    Environment("tram_dien", "Trạm Điện", "deadlight", "2.3", "Một căn phòng đầy hộp điện gỉ sét và dây cáp cháy đen. Tiếng tia lửa điện vang lên dù không thấy nguồn phát."),
    Environment("vung_den", "Vũng Đen", "wetfloor", "3.1", "Nhiều vũng chất lỏng đen đọng trên thảm, phản chiếu những hình dạng không tương ứng với căn phòng."),
    Environment("tham_phong", "Thảm Phồng", "wetfloor", "3.2", "Nền thảm nổi lên thành những khối dài giống có vật nằm bên dưới. Chúng từ từ xẹp xuống khi bị giẫm lên."),
    Environment("phong_ngap", "Phòng Ngập", "wetfloor", "3.3", "Một căn phòng trũng với lớp nước lạnh ngập mắt cá chân. Dưới mặt nước đục xuất hiện những vệt kéo lê."),
    Environment("goc_thoi", "Góc Thối", "wetfloor", "3.4", "Khu vực có mùi phân hủy nồng nặc. Tường và thảm phủ chất nhầy sẫm màu, những giọt nước liên tục rơi từ trần xuống."),
    Environment("hanh_lang_cam", "Hành Lang Câm", "silent_hall", "4.1", "Một đoạn hành lang dài nơi tiếng ù của đèn biến mất hoàn toàn. Tiếng bước chân đôi khi vang lên chậm hơn chuyển động thực tế."),
    Environment("phong_thi_tham", "Phòng Thì Thầm", "silent_hall", "4.2", "Những tiếng nói rất nhỏ phát ra từ sau lớp giấy dán tường. Không thể nghe rõ lời dù áp tai sát vách."),
    Environment("nga_ba_chet", "Ngã Ba Chết", "silent_hall", "4.3", "Ba hành lang hẹp giao nhau tại một khoảng trống. Những dấu bàn tay đen xuất hiện trên tường ở độ cao bất thường."),
    Environment("tran_vo", "Trần Vỡ", "hollow_ceiling", "5.1", "Hàng loạt tấm trần giả nằm rải rác trên sàn. Những khoảng trống phía trên chìm trong bóng tối."),
    Environment("khoang_ky_thuat", "Khoang Kỹ Thuật", "hollow_ceiling", "5.2", "Một căn phòng với vô số đường ống và dây điện lộ thiên. Tiếng cào kim loại vang lên từ bên trong các ống thông gió."),
    Environment("gieng_tran", "Giếng Trần", "hollow_ceiling", "5.3", "Một khoảng trống thẳng đứng mở lên phía trên, sâu đến mức ánh sáng không thể chạm tới đáy tối của nó."),
    Environment("phong_treo", "Phòng Treo", "hollow_ceiling", "5.4", "Các mảnh trần vỡ và dây cáp buông xuống sát đầu người. Một số sợi dây đung đưa nhẹ dù không có gió."),
    Environment("phong_ky_uc", "Phòng Ký Ức", "afterimage", "6.1", "Căn phòng vàng mang những chi tiết giống nơi chốn quen thuộc trong quá khứ, nhưng mọi đồ vật đều sai lệch."),
    Environment("tuong_mat", "Tường Mặt", "afterimage", "6.2", "Những vết ẩm mốc hình thành các khuôn mặt người méo mó. Dưới ánh sáng nhấp nháy, biểu cảm của chúng dường như thay đổi."),
    Environment("phong_tieng_goi", "Phòng Tiếng Gọi", "afterimage", "6.3", "Tiếng nói quen thuộc vang lên từ phía sau các bức tường. Mỗi lần đến gần, âm thanh lại xuất hiện ở vị trí khác."),
    Environment("hanh_lang_lap", "Hành Lang Lặp", "the_loop", "7.1", "Một dãy hành lang có bố cục giống hệt nhau. Những ký hiệu đánh dấu đường đi xuất hiện tại các vị trí khó giải thích."),
    Environment("phong_ban_sao", "Phòng Bản Sao", "the_loop", "7.2", "Nhiều căn phòng chứa cùng một chiếc ghế gãy, cùng vết nứt và cùng dấu chân trên thảm."),
    Environment("nga_tu_vo_tan", "Ngã Tư Vô Tận", "the_loop", "7.3", "Bốn hành lang giao nhau tại một điểm. Đi theo những hướng khác nhau đôi khi vẫn trở về cùng một vị trí."),
    Environment("phong_dao", "Phòng Đảo", "the_loop", "7.4", "Một căn phòng có bố cục tương tự phòng vừa rời khỏi nhưng mọi chi tiết đều nằm ở phía đối diện, như hình phản chiếu trong gương."),
    Environment("tuong_nut", "Tường Nứt", "the_fracture", "8.1", "Những vết nứt sâu chạy dọc các bức tường vàng. Từ bên trong vọng ra tiếng rung trầm không rõ nguồn."),
    Environment("san_lech", "Sàn Lệch", "the_fracture", "8.2", "Nền thảm bị xé rách, các khối sàn nằm ở độ cao khác nhau. Một số đoạn hành lang nghiêng theo góc bất thường."),
    Environment("khe_toi", "Khe Tối", "the_fracture", "8.3", "Khoảng trống khổng lồ nằm giữa hai bức tường, không thể xác định chiều sâu. Âm thanh ném vào khe không tạo tiếng vọng rõ ràng."),
    Environment("phong_giao_thoa", "Phòng Giao Thoa", "the_fracture", "8.4", "Giấy dán tường vàng bong tróc, để lộ những cột bê tông xám và bề mặt kiến trúc lạ. Không gian mang dấu hiệu biến đổi nhưng chưa xác nhận là lối thoát.")
  )
  private val byId = all.associateBy { it.id }
  fun requireEnvironment(id: String): Environment = requireNotNull(byId[id]) { "scene_environment" }
  @JvmStatic fun promptCatalog(): String = all.joinToString("; ") {
    "${it.id}=${it.name}" + (it.parent?.let { parent -> " [$parent]" } ?: " [khu chính]") + ": ${it.canon}"
  }
}
