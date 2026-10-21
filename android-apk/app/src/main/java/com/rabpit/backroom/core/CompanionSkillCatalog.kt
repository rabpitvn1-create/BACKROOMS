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
    s("ARGUS Terrain Read", "PASSIVE", "Bắt đầu combat / tự làm mới", "Analyzed 3 turn: Iris khai thác góc bắn và điểm hở mục tiêu.", "Không nhìn xuyên tường, không tự biết bản thể thật."),
    s("Thousandfold Cognition", "PASSIVE", "Khi Iris bị nhắm", "Tăng tốc xử lý thông tin tối đa 1:1.000 để đọc quỹ đạo và phản ứng.", "Không làm cơ thể hoặc súng nhanh hơn 1.000 lần."),
    s("Twosome Time", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "2 phát chéo góc, 155% Weapon DMG; 170% nếu mục tiêu đang Analyzed."),
    s("Rain Storm", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "6 phát khi đổi góc trên không, tổng 145% Weapon DMG."),
    s("Honeycomb Fire", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "8 phát tập trung, 185% Weapon DMG; Armor Break 20% trong 2 turn."),
    s("Charged Shot", "AUTO", "25% mỗi lượt TẤN CÔNG hợp lệ", "175% Weapon DMG, bỏ qua 35% Armor."),
    s("Dead Angle", "COUNTER", "15% sau khi Entity hụt phản công", "Ivory & Ebony phản kích tức thời, 120% Weapon DMG; không chiếm lượt chính."),
    s("ARGUS // Thousandfold Execution", "ULTIMATE", "Poker Dice SSF hoặc FSF khi Iris ACTIVE và Party TẤN CÔNG", "12 phát luân phiên, 300% Weapon DMG; Fully Exposed 2 turn làm giảm 25% Evasion và 20% Armor.", "Không tự phát hiện mục tiêu/bản thể không có dữ liệu.")
  )

  private val syvial = listOf(
    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Miễn cơ chế cạn Mana/Energy/Overheat nội tại; hồi 2% Max HP mỗi turn, 4% khi Devil Trigger.", "Không hồi từ 0 HP."),
    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định", "Đọc chuyển động và chuẩn bị phản đòn; hỗ trợ Counterphase."),
    s("Rift Sever", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "Spatial Shift lệch trục phòng thủ rồi chém, 175% Weapon DMG, bỏ qua 20% Armor."),
    s("Crimson Guillotine", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "190% Weapon DMG; Bleeding 3 turn, mỗi turn 4% Max HP."),
    s("Lucifer Breaker", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Chuỗi cận chiến + GodKiller 155% Weapon DMG; Stun phản ứng hiện tại của Entity."),
    s("Counterphase", "COUNTER", "30% sau khi Entity hụt phản công", "Spatial Shift vào góc chết và phản chém 125% Weapon DMG; không chiếm lượt chính."),
    s("GodKiller Recall", "PASSIVE", "Khi bị Disarm hợp lệ", "Gọi GodKiller trở lại ở đầu lượt kế tiếp nếu không có luật boss khóa triệu hồi."),
    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% outgoing DMG, +20% Evasion, -20% incoming DMG theo vai trò cá nhân; hồi phục Lucifer Core tăng lên 4% Max HP/turn.", "Không cooldown nội tại, không giới hạn thời gian canon."),
    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger và Party chọn TẤN CÔNG", "Chuỗi Spatial Shift + GodKiller, 210% Weapon DMG; Disoriented -25% Accuracy trong 2 turn."),
    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Poker Dice SSF hoặc FSF khi Party TẤN CÔNG và Devil Trigger", "Dừng thời gian ngoại giới, đúng 24 nhát chém x 10 HP = 240 HP; bỏ qua Evasion.", "Không phải instant-kill tuyệt đối.")
  )

  private val anNhien = listOf(
    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên theo Party", "Giảm 25% xác suất hazard trên action vật lý hợp lệ."),
    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi SEARCH", "+10 điểm phần trăm vào generic loot roll hiện có.", "Không tạo loot roll thứ hai."),
    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống xấu", "Ưu tiên vị trí an toàn; không biến An Nhiên thành combatant."),
    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% khi Party chọn TẤN CÔNG và An Nhiên ACTIVE", "Ném vật vô hại để đánh lạc hướng, Entity -25 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage, không dùng vũ khí."),
    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi SEARCH một Exit", "Nếu proc, +20 điểm phần trăm cho Exit probe của action đó."),
    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi kiểm tra nước/chất lỏng khả nghi", "Nếu proc, chặn hazard roll của action kiểm tra đó.", "Chỉ là kiểm tra nguy cơ, không tự biết toàn bộ bản chất vật thể."),
    s("Thôi Để Tôi Làm", "UTILITY", "Khi xử lý thao tác sinh tồn", "Đại diện lợi thế thực dụng trong narration/Game Master; không áp cho hack, phép thuật hoặc công nghệ ngoài khả năng."),
    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Mỗi 5 combat turn hợp lệ khi Party chọn BỎ CHẠY và An Nhiên ACTIVE", "Tận dụng địa hình: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage.")
  )

  private val caoMinh = listOf(
    s("Đại Đạo Ma Tôn", "PASSIVE", "Luôn hoạt động", "+10% Base STR/DEX/AGI/VIT/EVA; cộng từ nội tại, không dùng tài nguyên nâng điểm. STR tăng Attack và Skill DMG; DEX tăng Critical Resistance; AGI tăng Critical Chance; VIT tăng Max HP và DEF; EVA tăng Evasion và kháng né. Sau mỗi lượt Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%.", "Passive tách khỏi Base Stat và reset combat stack khi combat kết thúc."),
    s("Huyết Ma Tứ Liên", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm, 170% Weapon DMG; Chảy máu 3 turn x 5% Max HP."),
    s("Ma Tâm Trấn Hồn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm cùng điểm, 130% Weapon DMG; Choáng phản ứng hiện tại."),
    s("Huyết Ảnh Ma Độn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Dịch chuyển theo Huyết Ma Kiếm, đúng 2 trảm, 147% Weapon DMG."),
    s("Thiên Ma Bộ", "AUTO", "30% khi TẤN CÔNG hoặc NÉ TRÁNH", "+50 điểm phần trăm Evasion trong 3 turn; không chặn AoE bắt buộc."),
    s("Huyết Sát Kiếm Ấn", "AUTO", "50% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+25% DMG; Chảy máu 2 turn x 3% Max HP."),
    s("Phá Giáp Ma Kiếm", "AUTO", "48% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Xuyên giáp 10% trong 2 turn."),
    s("Ma Tâm Chấn", "AUTO", "45% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+15% DMG; Choáng phản ứng hiện tại."),
    s("Huyết Độc Ma Khí", "AUTO", "47% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Trúng độc 2 turn x 3% Max HP."),
    s("Huyết Liệt Ma Ấn", "AUTO", "51% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Chảy máu 2 turn x 4% Max HP."),
    s("Huyết Ma Nhị Thập Tứ Trảm", "ULTIMATE", "Poker Dice SSF hoặc FSF khi Party TẤN CÔNG", "Đúng 24 trảm, mỗi trảm 115% current Weapon DMG; bỏ qua Evasion.")
  )

  // R06 tactical combat effects; no invented canon proc rates.
  private val lucTram = listOf(
    s("Thiên Kiếm Linh Tâm", "PASSIVE", "Lục Trầm ACTIVE; Party chọn QUAN SÁT",
      "Đọc quỹ đạo và tăng thêm 1 opening.", "Không đọc ý nghĩ hoặc tự nhận dạng Entity."),
    s("Nhất Tuyến Phá Vọng", "AUTO", "Party TẤN CÔNG khi mục tiêu ở CLOSE hoặc có opening",
      "Chém tập trung và bỏ qua 20% giáp.", "20% là hệ số gameplay, không phải canon."),
    s("Bạch Hồng Quán Nhật", "AUTO", "Party TẤN CÔNG khi mục tiêu chưa ở CLOSE và chưa có opening",
      "Đột tiến một range band rồi đánh thường.", "Không vượt cấu trúc Level."),
    s("Tịch Quang Phản Kiếm", "COUNTER", "Entity hụt phản công, Tịch Quang đã xác nhận trang bị",
      "Phản kiếm với Accuracy / Evasion độc lập.", "Không tự giả định bản mệnh kiếm còn nguyên."),
    s("Vạn Kiếm Quy Tâm", "UTILITY", "Party chọn NÉ TRÁNH hoặc PHÒNG THỦ",
      "Kiếm khí giữ lối rút: cover HARD khi né; thêm opening khi thủ.", "Không miễn mọi đòn."),
    s("Thiên Kiếm Định Giới", "ULTIMATE", "Poker Dice SSF hoặc FSF, có Tịch Quang, opening từ 2 và chưa dùng trong wave",
      "Dùng lượt của Lục Trầm dựng Kiếm Vực: cover HARD, opening 3.", "Không đổi geometry Backrooms.")
  )

  private val lucia = listOf(
    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia ở trong Party", "+5 điểm phần trăm vào generic loot roll hiện có."),
    s("M4A1 Joint Attack", "COMMAND", "Party TẤN CÔNG, Lucia ACTIVE", "150% current M4A1 DMG, có Accuracy/Evasion gate."),
    s("Toxic Burst", "AUTO", "52% độc lập sau phát M4A1 trúng; không ở lượt Ultimate", "+25% current DMG; Trúng độc 2 turn, 3% Max HP/turn."),
    s("Armor-Piercing Burst", "AUTO", "55% độc lập sau phát M4A1 trúng; không ở lượt Ultimate", "+20% current DMG; Xuyên giáp 10% trong 2 turn."),
    s("Concussive Burst", "AUTO", "46% độc lập sau phát M4A1 trúng; không ở lượt Ultimate", "+15% current DMG; Choáng chặn phản ứng hiện tại."),
    s("Rending Burst", "AUTO", "49% độc lập sau phát M4A1 trúng; không ở lượt Ultimate", "+20% current DMG; Chảy máu 2 turn, 3% Max HP/turn."),
    s("Corrosive Burst", "AUTO", "51% độc lập sau phát M4A1 trúng; không ở lượt Ultimate", "+20% current DMG; Trúng độc 2 turn, 4% Max HP/turn."),
    s("Too Young To Die", "ULTIMATE", "Poker Dice SSF hoặc FSF khi Lucia ACTIVE và Party TẤN CÔNG",
      "Đúng 60 phát M4A1; mỗi phát 100% current DMG +15% bonus; FSF x200%. Bỏ qua Evasion và Critical phụ.")
  )

  private val catherine = listOf(
    s("Đồng Tử Hổ Phách", "PASSIVE", "Catherine ACTIVE và Party TẤN CÔNG",
      "+10 điểm phần trăm Accuracy cho phát bắn thường; vẫn kiểm tra Evasion của Entity.", "Không phải tự động trúng hoặc nhìn xuyên tường."),
    s("Khúc Đạn Hồi Âm", "AUTO", "30% sau phát thường trúng; không trong lượt Ultimate",
      "Phát bồi gây 125% current Weapon DMG, trừ Armor."),
    s("Hoa Hồng Xuyên Giáp", "AUTO", "25% sau phát thường trúng; không trong lượt Ultimate",
      "Viên đạn trừ tà gây 155% current Weapon DMG, bỏ qua 30% Armor."),
    s("Valse Đỏ", "UTILITY", "Catherine ACTIVE; Party chọn NÉ TRÁNH",
      "Dịch chuyển theo nhịp chân làm Entity giảm 15 điểm phần trăm Accuracy trong phản ứng hiện tại."),
    s("Phản Xạ Màn Nhung", "COUNTER", "20% sau khi Entity hụt phản công và Catherine ACTIVE",
      "Phản đạn gây 110% current Weapon DMG, trừ Armor; không chiếm lượt tấn công mới."),
    s("Dạ Khúc Mười Ba Phát", "ULTIMATE", "Poker Dice SSF hoặc FSF; Catherine ACTIVE và Party TẤN CÔNG",
      "13 quỹ đạo đạn được chuẩn bị, tổng 325% current Weapon DMG, trừ Armor một lần; bỏ qua Evasion, không cộng Critical phụ.", "Một lần kích hoạt trong lượt Ultimate; không tạo đạn vô hạn.")
  )

  private val janet = listOf(
    s("Ragin' Cajun", "PASSIVE", "Janet ACTIVE; Party chọn TẤN CÔNG",
      "+10 điểm phần trăm Accuracy cho đòn gậy thường, vẫn kiểm tra Evasion; cận chiến không bắn từ xa."),
    s("Bayou Swing", "AUTO", "30% sau đòn gậy thường trúng; không kích hoạt từ Ultimate",
      "Quét gậy thêm lần nữa, 125% current Weapon DMG, trừ Armor."),
    s("Barbed Encore", "AUTO", "25% sau đòn gậy thường trúng; không kích hoạt từ Ultimate",
      "Đánh dây gai 140% current Weapon DMG, bỏ qua 20% Armor cho đòn này."),
    s("Redline Sidestep", "UTILITY", "Janet ACTIVE; Party chọn NÉ TRÁNH",
      "+12 Escape Progress, không tự miễn mọi đòn đánh."),
    s("Ride or Die", "DEFENSE", "Janet ACTIVE; Party chọn PHÒNG THỦ",
      "Entity -15 điểm phần trăm Accuracy trong phản ứng hiện tại."),
    s("Last Inning // Bayou Thunder", "ULTIMATE", "Poker Dice SSF hoặc FSF; Janet ACTIVE và Party TẤN CÔNG",
      "Cú đánh cận chiến 330% Weapon DMG, trừ Armor một lần, bỏ qua Evasion; không kèm proc/crit.",
      "Không đánh xuyên vách, không tự thắng boss hay tạo năng lượng vô hạn.")
  )

  private val nguyenQuocNam = listOf(
    s("Quan Sát Lâm Sàng", "PASSIVE", "Quốc Nam ACTIVE; Party TẤN CÔNG",
      "+8 điểm phần trăm Accuracy cho cú bắn thường, vẫn có phép kiểm tra Evasion."),
    s("Phát Bắn Kiềm Chế", "AUTO", "25% sau phát thường trúng, không dùng trong Ultimate",
      "Một phát bồi gây 120% Weapon DMG, trừ Armor; không gây proc dây chuyền."),
    s("Sơ Cứu Tại Chỗ", "UTILITY", "Quốc Nam ACTIVE; Party PHÒNG THỦ; tối đa một lần mỗi encounter",
      "Hồi tối đa 12% Max HP cho một đồng đội còn sống bị thương nặng nhất theo tỷ lệ; không hồi sinh."),
    s("Giữ Khoảng Cách", "DEFENSE", "Quốc Nam ACTIVE; Party NÉ TRÁNH",
      "Entity -12 điểm phần trăm Accuracy trong phản ứng hiện tại."),
    s("Đánh Giá Nguy Cơ", "UTILITY", "Quốc Nam ACTIVE; Party QUAN SÁT",
      "Thêm 1 Opening, tuân thủ mức tối đa hiện hành; không tiết lộ Exit."),
    s("Lời Hứa Trở Về", "ULTIMATE", "Poker Dice SSF hoặc FSF; Quốc Nam ACTIVE và Party TẤN CÔNG",
      "Một phát bắn chuẩn xác 285% Weapon DMG, trừ Armor một lần và bỏ qua Evasion.",
      "Không cộng Critical hoặc Phát Bắn Kiềm Chế; không liên lạc với gia đình.")
  )

  fun forCharacter(characterId: String): List<CharacterSkillDefinition> = when (characterId) {
    CAO_MINH_ID -> caoMinh
    IRIS_ID -> iris
    SYVIAL_ID -> syvial
    AN_NHIEN_ID -> anNhien
    LUCIA_ID -> lucia
    LUC_TRAM_ID -> lucTram
    CATHERINE_ID -> catherine
    JANET_ID -> janet
    NGUYEN_QUOC_NAM_ID -> nguyenQuocNam
    else -> emptyList()
  }
}
