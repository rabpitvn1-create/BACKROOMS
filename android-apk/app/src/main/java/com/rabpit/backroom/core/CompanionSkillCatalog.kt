package com.rabpit.backroom.core

data class CharacterSkillDefinition(
  val name: String,
  val kind: String,
  val trigger: String,
  val effect: String,
  val note: String? = null
)

object CompanionSkillCatalog {
  private fun s(name: String, kind: String, trigger: String, effect: String, note: String? = null) =
    CharacterSkillDefinition(name, kind, trigger, effect, note)

  private val iris = listOf(
    s("ARGUS Terrain Read", "PASSIVE", "Khi giao tranh bắt đầu hoặc dữ liệu được làm mới", "Trong 3 lượt tiếp theo, Iris liên tục đọc địa hình, góc bắn và những sơ hở trong cách mục tiêu di chuyển.", "Khả năng này không cho phép Iris nhìn xuyên tường hay tự biết bản thể thật của mục tiêu."),
    s("Thousandfold Cognition", "PASSIVE", "Khi Iris trở thành mục tiêu của một đòn tấn công", "Iris tăng tốc xử lý thông tin lên tối đa 1:1.000 để đọc quỹ đạo và chọn phản ứng phù hợp.", "Chỉ tốc độ nhận thức được gia tốc; cơ thể và súng của Iris không nhanh hơn 1.000 lần."),
    s("Twosome Time", "AUTO", "30% ở mỗi lượt hợp lệ", "Iris bắn hai phát từ hai góc chéo nhau. Kỹ năng gây 155% sát thương vũ khí, tăng lên 170% nếu mục tiêu đang được ARGUS phân tích."),
    s("Rain Storm", "AUTO", "20% ở mỗi lượt hợp lệ", "Iris đổi góc tấn công trên không rồi bắn liên tiếp 6 phát, gây tổng cộng 145% sát thương vũ khí."),
    s("Honeycomb Fire", "AUTO", "20% ở mỗi lượt hợp lệ", "Iris dồn 8 phát vào cùng một vùng mục tiêu, gây 185% sát thương vũ khí. Mục tiêu bị Phá Giáp 20% trong 2 lượt."),
    s("Charged Shot", "AUTO", "25% ở mỗi lượt hợp lệ", "Iris nạp lực cho một phát bắn xuyên phá, gây 175% sát thương vũ khí và bỏ qua 35% Giáp của mục tiêu."),
    s("Dead Angle", "COUNTER", "15% sau khi Thực thể phản công hụt", "Iris lập tức dùng Ivory & Ebony bắn trả từ góc chết, gây 120% sát thương vũ khí mà không tiêu tốn lượt chính."),
    s("ARGUS // Thousandfold Execution", "ULTIMATE", "Tự động sau mỗi 4 lượt chiến đấu", "Iris khóa toàn bộ dữ liệu đã phân tích rồi bắn 12 phát luân phiên, gây 300% sát thương vũ khí. Mục tiêu bị Lộ hoàn toàn trong 2 lượt, mất 25% Né tránh và 20% Giáp.", "Kỹ năng chỉ khai thác dữ liệu Iris đã có; nó không tự phát hiện mục tiêu hay bản thể chưa từng được nhận diện."),
  )

  private val syvial = listOf(
    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi Syvial còn khả năng chiến đấu", "Lucifer Core không cạn vì Ma lực, Năng lượng hay Quá nhiệt nội tại. Syvial hồi 2% Máu tối đa mỗi lượt; khi Devil Trigger đang hoạt động, mức hồi tăng lên 4% Máu tối đa.", "Lucifer Core không thể kéo Syvial trở lại từ 0 Máu."),
    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định tấn công", "Syvial đọc chuyển động chuẩn bị của đối thủ để chọn thời điểm phản đòn, đồng thời tạo điều kiện cho Counterphase."),
    s("Rift Sever", "AUTO", "30% ở mỗi lượt hợp lệ", "Syvial dùng Spatial Shift làm lệch hướng phòng thủ rồi chém bằng GodKiller, gây 175% sát thương vũ khí và bỏ qua 20% Giáp."),
    s("Crimson Guillotine", "AUTO", "20% ở mỗi lượt hợp lệ", "Syvial tung một nhát chém nặng gây 190% sát thương vũ khí. Vết thương tiếp tục Chảy máu trong 3 lượt, mỗi lượt mất 4% Máu tối đa."),
    s("Lucifer Breaker", "AUTO", "20% ở mỗi lượt hợp lệ", "Syvial áp sát bằng một chuỗi đòn cận chiến rồi kết thúc bằng GodKiller, gây 155% sát thương vũ khí và Choáng để cắt phản ứng hiện tại của Thực thể."),
    s("Counterphase", "COUNTER", "30% sau khi Thực thể phản công hụt", "Syvial dùng Spatial Shift lướt vào góc chết rồi phản chém, gây 125% sát thương vũ khí mà không tiêu tốn lượt chính."),
    s("GodKiller Recall", "PASSIVE", "Khi GodKiller bị tước khỏi Syvial", "Đầu lượt kế tiếp, Syvial gọi GodKiller trở lại tay. Hiệu ứng không xảy ra nếu luật riêng của trùm đang khóa khả năng triệu hồi."),
    s("Devil Trigger", "STATE", "Khi Máu còn 50% trở xuống hoặc khi đối đầu Diệp Minh", "Syvial gây thêm 25% sát thương, nhận thêm 20% Né tránh và giảm 20% sát thương phải chịu. Trong trạng thái này, Lucifer Core hồi 4% Máu tối đa mỗi lượt.", "Devil Trigger không có hồi chiêu nội tại và không bị giới hạn thời gian theo nguyên tác."),
    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger đang hoạt động", "Syvial liên tục đổi vị trí bằng Spatial Shift rồi tấn công bằng GodKiller, gây 210% sát thương vũ khí. Mục tiêu bị Mất phương hướng và giảm 25% Độ chính xác trong 2 lượt."),
    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Sau mỗi 3 lượt chiến đấu khi Devil Trigger đang hoạt động", "Syvial dừng hoàn toàn thời gian ngoại giới và tung đúng 24 nhát chém. Mỗi nhát gây 10 Máu, tổng cộng 240 Máu, đồng thời bỏ qua Né tránh.", "Đây không phải đòn kết liễu tuyệt đối; các luật phòng thủ riêng của mục tiêu vẫn được áp dụng nếu có."),
  )

  private val anNhien = listOf(
    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên đang đi cùng đội", "Bản năng cảnh giác của An Nhiên giúp đội giảm 25% xác suất gặp nguy hiểm trong các hành động vật lý hợp lệ."),
    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi đội thực hiện TÌM KIẾM", "An Nhiên giúp nhận ra thứ đáng lấy, cộng 10 điểm phần trăm vào tỷ lệ rơi vật phẩm chung của lần TÌM KIẾM đó.", "Kỹ năng chỉ tăng tỷ lệ hiện có, không tạo thêm một lần kiểm tra rơi vật phẩm."),
    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống trở nên nguy hiểm", "An Nhiên chủ động tìm vị trí an toàn và tránh chen vào tuyến giao tranh, nhờ đó giữ đúng vai trò hỗ trợ sinh tồn thay vì trở thành nhân vật chiến đấu."),
    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% ở mỗi lượt chiến đấu khi An Nhiên đang trong đội", "An Nhiên quăng một vật vô hại để làm Thực thể mất tập trung. Phản ứng hiện tại của Thực thể bị giảm 25 điểm phần trăm Độ chính xác.", "Đòn đánh lạc hướng không gây sát thương và không được tính là sử dụng vũ khí."),
    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi đội TÌM KIẾM một lối thoát", "Nếu An Nhiên nhận ra dấu hiệu hữu ích, lần kiểm tra lối thoát của chính hành động đó được cộng 20 điểm phần trăm."),
    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi đội kiểm tra nước hoặc chất lỏng khả nghi", "Nếu An Nhiên phát hiện dấu hiệu bất thường, lần kiểm tra nguy hiểm của hành động đó bị chặn trước khi đội tiếp xúc trực tiếp.", "An Nhiên chỉ nhận ra dấu hiệu nguy cơ; cô không tự biết toàn bộ bản chất của chất lỏng."),
    s("Thôi Để Tôi Làm", "UTILITY", "Khi đội xử lý một thao tác sinh tồn phù hợp", "An Nhiên trực tiếp phụ trách những việc thực dụng mà cô làm tốt, giúp Quản trò thể hiện lợi thế sinh tồn của cô trong kết quả hành động.", "Kỹ năng không áp dụng cho hack, phép thuật hay công nghệ vượt ngoài khả năng của An Nhiên."),
    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Sau mỗi 5 lượt chiến đấu khi An Nhiên đang trong đội", "An Nhiên tận dụng địa hình để mở đường rút: đội nhận +30 Tiến độ thoát, còn phản ứng hiện tại của Thực thể bị giảm 20 điểm phần trăm Độ chính xác.", "Kỹ năng chỉ hỗ trợ thoát thân và không gây sát thương."),
  )

  private val kai = listOf(
    s("The Last Requiem", "AUTO", "38% ở mỗi lượt hợp lệ", "Kai ghìm nhịp giật của SRU-SG và bắn 4 viên đạn quỷ lực vào vùng vai để phá nhịp vận động của mục tiêu. Kỹ năng gây 170% sát thương vũ khí và Chảy máu trong 3 lượt, mỗi lượt mất 5% Máu tối đa."),
    s("Silent Lullaby", "AUTO", "27% ở mỗi lượt hợp lệ", "Kai giữ chặt SRU-SG và dồn 4 viên đạn quỷ lực vào cùng một vùng trọng yếu trên ngực. Kỹ năng gây 130% sát thương vũ khí và Choáng mục tiêu trong 1 lượt."),
    s("Salvation", "AUTO", "26% ở mỗi lượt hợp lệ", "Kai bứt qua góc chết, áp sát rồi ghì SRU-SG bằng hai tay để bắn 2 viên đạn quỷ lực ở cự ly gần, gây 147% sát thương vũ khí."),
    s("Quick Step", "AUTO", "35% ở mỗi lượt hợp lệ", "Kai liên tục đổi góc bằng những pha bứt tốc ngắn nhưng vẫn giữ SRU-SG ở tư thế sẵn bắn. Trong 3 lượt, Kai nhận thêm 50 điểm phần trăm Né tránh trước các phản công thông thường."),
    s("Guilty Crown Override", "ULTIMATE", "Tự động sau mỗi 3 lượt chiến đấu", "Kai tung đúng 24 phát liên tiếp. Mỗi phát gây 10 Máu, tổng cộng 240 Máu; Độ chính xác của kỹ năng là 200% và đòn bắn bỏ qua Né tránh."),
    s("Devil Blessing", "PASSIVE", "Khi Kai đang đang hoạt động trong đội và giao tranh đang diễn ra", "Các đồng đội đang hoạt động trong đội nhận thêm 5% Tấn Công, Phòng Thủ, Né tránh và Máu tối đa. Kai không nhận hiệu ứng từ chính kỹ năng này.", "Hiệu ứng chỉ áp dụng khi Kai và đồng đội mục tiêu vẫn còn khả năng chiến đấu.")
  )

  private val lucia = listOf(
    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia đang ở trong đội", "Khả năng quan sát chiến trường của Lucia cộng 5 điểm phần trăm vào tỷ lệ rơi vật phẩm chung hiện có của đội."),
    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cho Kai và Lucia cùng tấn công", "Lucia thực hiện phần tấn công bằng M4A1 như một hành động riêng trong lượt của đội. Loạt bắn của cô vẫn phải vượt qua kiểm tra Né tránh của Thực thể."),
    s("M4A1 Full Auto Burst", "AUTO", "20% sau mỗi 2 lượt chiến đấu hợp lệ khi đội chọn TẤN CÔNG", "Lucia xả đúng 30 viên từ M4A1. Mỗi viên gây 30 + sát thương cơ bản trước Giáp; toàn bộ loạt bắn chỉ thực hiện một lần kiểm tra Né tránh của Thực thể."),
    s("Too Young To Die", "AUTO", "15% ở mỗi lượt chiến đấu; khi Máu dưới 50%, cứ mất thêm 3 điểm phần trăm Máu thì tỷ lệ kích hoạt tăng thêm 5 điểm phần trăm", "Lucia xả hết băng 60 viên. Mỗi viên gây sát thương cơ bản +5% trước Giáp và trước các hiệu ứng tăng cường ngoài kỹ năng; toàn bộ loạt bắn chỉ thực hiện một lần kiểm tra Né tránh của Thực thể.", "Ví dụ: ở 49% Máu tỷ lệ là 15%, ở 47% là 20%, ở 44% là 25%. Tỷ lệ kích hoạt tối đa là 100%."),
  )

  fun forCharacter(characterId: String): List<CharacterSkillDefinition> = when (characterId) {
    KAI_ID -> kai
    IRIS_ID -> iris
    SYVIAL_ID -> syvial
    AN_NHIEN_ID -> anNhien
    LUCIA_ID -> lucia
    else -> emptyList()
  }
}

// Legacy CI compatibility marker only, not player-facing text: 20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG
