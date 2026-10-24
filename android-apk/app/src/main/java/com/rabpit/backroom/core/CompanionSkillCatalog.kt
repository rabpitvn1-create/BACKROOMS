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

  private val lucTram = listOf(
    s("Thiên Kiếm Linh Tâm", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Căn cơ Kiếm Đạo của Lục Trầm: thống nhất thần niệm, linh lực và cảm quan không gian để đọc tuyến tấn công, phá nhịp và phản kiếm.", "Không phải trái tim vật lý thứ hai."),
    s("Thiên Kiếm Thất Thức", "AUTO", "Khi Lục Trầm tấn công", "Hệ thống kiếm thức nền tảng của Thiên Kiếm Môn, luyện tới không còn cần thứ tự cố định; Nhất Tuyến Phá Vọng tập trung một tuyến phá thế."),
    s("Tịch Quang", "PASSIVE", "Bản mệnh linh kiếm", "Bản mệnh linh kiếm của Lục Trầm: trường kiếm thẳng, lưỡi bạc sáng ít phản xạ, tay cầm viền vàng đen.", "Kiếm gắn với căn cơ Thiên Kiếm Linh Tâm."),
    s("Thiên Cơ Bạch Kim Kiếm Khải", "PASSIVE", "Pháp bảo hộ thân", "Chiến khải cao cấp do Kiếm Các và Khí Các của Thiên Kiếm Môn cùng rèn sau khi Lục Trầm thành chân truyền đệ tử.")
  )

  private val syvial = listOf(
    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Miễn cơ chế cạn Mana/Energy/Overheat nội tại; hồi 2% Max HP mỗi turn, 4% khi Devil Trigger.", "Không hồi từ 0 HP."),
    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định", "Đọc chuyển động và chuẩn bị phản đòn; hỗ trợ Counterphase."),
    s("Rift Sever", "AUTO", "30% mỗi turn hợp lệ", "Spatial Shift lệch trục phòng thủ rồi chém, 175% Weapon DMG, bỏ qua 20% Armor."),
    s("Crimson Guillotine", "AUTO", "20% mỗi turn hợp lệ", "190% Weapon DMG; Bleeding 3 turn, mỗi turn 4% Max HP."),
    s("Lucifer Breaker", "AUTO", "20% mỗi turn hợp lệ", "Chuỗi cận chiến + GodKiller 155% Weapon DMG; Stun phản ứng hiện tại của Entity."),
    s("Counterphase", "COUNTER", "30% sau khi Entity hụt phản công", "Spatial Shift vào góc chết và phản chém 125% Weapon DMG; không chiếm lượt chính."),
    s("GodKiller Recall", "PASSIVE", "Khi bị Disarm hợp lệ", "Gọi GodKiller trở lại ở đầu lượt kế tiếp nếu không có luật boss khóa triệu hồi."),
    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% outgoing DMG, +20% Evasion, -20% incoming DMG theo vai trò cá nhân; hồi phục Lucifer Core tăng lên 4% Max HP/turn.", "Không cooldown nội tại, không giới hạn thời gian canon."),
    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger", "Chuỗi Spatial Shift + GodKiller, 210% Weapon DMG; Disoriented -25% Accuracy trong 2 turn."),
    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Mỗi 3 combat turn khi Devil Trigger", "Dừng thời gian ngoại giới, đúng 24 nhát chém x 10 HP = 240 HP; bỏ qua Evasion.", "Không phải instant-kill tuyệt đối.")
  )

  private val anNhien = listOf(
    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên theo Party", "Giảm 25% xác suất hazard trên action vật lý hợp lệ."),
    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi SEARCH", "+10 điểm phần trăm vào generic loot roll hiện có.", "Không tạo loot roll thứ hai."),
    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống xấu", "Ưu tiên vị trí an toàn; không biến An Nhiên thành combatant."),
    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% mỗi combat turn khi ACTIVE trong Party", "Ném vật vô hại để đánh lạc hướng, Entity -25 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage, không dùng vũ khí."),
    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi SEARCH một Exit", "Nếu proc, +20 điểm phần trăm cho Exit probe của action đó."),
    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi kiểm tra nước/chất lỏng khả nghi", "Nếu proc, chặn hazard roll của action kiểm tra đó.", "Chỉ là kiểm tra nguy cơ, không tự biết toàn bộ bản chất vật thể."),
    s("Thôi Để Tôi Làm", "UTILITY", "Khi xử lý thao tác sinh tồn", "Đại diện lợi thế thực dụng trong narration/Game Master; không áp cho hack, phép thuật hoặc công nghệ ngoài khả năng."),
    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Mỗi 5 combat turn khi ACTIVE trong Party", "Tận dụng địa hình: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage.")
  )

  private val caoMinh = listOf(
    s("Đại Đạo Ma Tôn", "PASSIVE", "Luôn hoạt động", "+10% Base STR/DEF/SKL/VIT; không đổi Core cost. STR tăng sát thương vật lý/đánh thường; DEF tăng giảm sát thương và Critical Resistance; SKL tăng Critical, Evasion Resistance và skill damage; VIT tăng Max HP và Evasion. Sau mỗi lượt Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%.", "Passive tách khỏi Base Stat và reset combat stack khi combat kết thúc."),
      s("Đại Ma Đầu", "PASSIVE", "Luôn hoạt động", "+50% stats hiện tại (STR/DEF/SKL/VIT) của Cao Minh, tính lại theo stats hiện tại mỗi lần tính Effective Stats; Max HP derive từ VIT; hoạt động song song với Đại Đạo Ma Tôn.", "Không chiếm ô trang bị, không stack qua save-load hoặc equip."),
    s("Huyết Ma Tứ Liên", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm, 170% Weapon DMG; Chảy máu 3 turn x 5% Max HP."),
    s("Ma Tâm Trấn Hồn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm cùng điểm, 130% Weapon DMG; Choáng phản ứng hiện tại."),
    s("Huyết Ảnh Ma Độn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Dịch chuyển theo Huyết Ma Kiếm, đúng 2 trảm, 147% Weapon DMG."),
    s("Thiên Ma Bộ", "AUTO", "30% khi TẤN CÔNG hoặc NÉ TRÁNH", "+50 điểm phần trăm Evasion trong 3 turn; không chặn AoE bắt buộc."),
    s("Huyết Sát Kiếm Ấn", "AUTO", "50% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+25% DMG; Chảy máu 2 turn x 3% Max HP."),
    s("Phá Giáp Ma Kiếm", "AUTO", "48% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Xuyên giáp 10% trong 2 turn."),
    s("Ma Tâm Chấn", "AUTO", "45% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+15% DMG; Choáng phản ứng hiện tại."),
    s("Huyết Độc Ma Khí", "AUTO", "47% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Trúng độc 2 turn x 3% Max HP."),
    s("Huyết Liệt Ma Ấn", "AUTO", "51% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Chảy máu 2 turn x 4% Max HP."),
    s("Huyết Ma Nhị Thập Tứ Trảm", "ULTIMATE", "Mỗi 3 combat turn khi TẤN CÔNG", "Đúng 24 trảm, mỗi trảm 115% current Weapon DMG; bỏ qua Evasion.")
  )

  private val lucia = listOf(
    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia ở trong Party", "+5 điểm phần trăm vào generic loot roll hiện có."),
    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cả Cao Minh và Lucia cùng tấn công", "Lucia có resolution bắn M4A1 riêng và vẫn chịu Entity Evasion gate.")
  )

  fun forCharacter(characterId: String): List<CharacterSkillDefinition> = when (characterId) {
    CAO_MINH_ID -> caoMinh
    LUC_TRAM_ID -> lucTram
    SYVIAL_ID -> syvial
    AN_NHIEN_ID -> anNhien
    "lucia" -> lucia
    else -> emptyList()
  }
}
