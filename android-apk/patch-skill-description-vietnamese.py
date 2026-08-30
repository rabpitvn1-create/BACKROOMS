from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "CompanionSkillCatalogTest.kt"

if not CATALOG.exists():
    raise RuntimeError("Issue #125: CompanionSkillCatalog.kt must exist before localization")

# Issue #125: keep canonical skill names and compact gameplay notation such as
# DMG/HP, but make every trigger/effect/note sentence Vietnamese. This file is
# intentionally the final catalog authority so later character additions can
# follow one consistent writing contract instead of accumulating mixed prose.
CATALOG.write_text(r'''package com.rabpit.backroom.core

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
    s("ARGUS Terrain Read", "PASSIVE", "Bắt đầu chiến đấu / tự làm mới", "Phân tích trong 3 lượt: Iris khai thác góc bắn và điểm hở của mục tiêu.", "Không nhìn xuyên tường, không tự biết bản thể thật."),
    s("Thousandfold Cognition", "PASSIVE", "Khi Iris bị nhắm", "Tăng tốc xử lý thông tin tối đa 1:1.000 để đọc quỹ đạo và phản ứng.", "Không làm cơ thể hoặc súng nhanh hơn 1.000 lần."),
    s("Twosome Time", "AUTO", "30% mỗi lượt hợp lệ", "2 phát chéo góc, 155% DMG vũ khí; 170% nếu mục tiêu đang được phân tích."),
    s("Rain Storm", "AUTO", "20% mỗi lượt hợp lệ", "6 phát khi đổi góc trên không, tổng 145% DMG vũ khí."),
    s("Honeycomb Fire", "AUTO", "20% mỗi lượt hợp lệ", "8 phát tập trung, 185% DMG vũ khí; Phá Giáp 20% trong 2 lượt."),
    s("Charged Shot", "AUTO", "25% mỗi lượt hợp lệ", "175% DMG vũ khí, bỏ qua 35% Giáp."),
    s("Dead Angle", "COUNTER", "15% sau khi Entity phản công hụt", "Ivory & Ebony phản kích tức thời, 120% DMG vũ khí; không chiếm lượt chính."),
    s("ARGUS // Thousandfold Execution", "ULTIMATE", "Tự động mỗi 4 lượt chiến đấu", "12 phát luân phiên, 300% DMG vũ khí; trạng thái Lộ hoàn toàn kéo dài 2 lượt, giảm 25% Né tránh và 20% Giáp.", "Không tự phát hiện mục tiêu hoặc bản thể khi chưa có dữ liệu.")
  )

  private val syvial = listOf(
    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi nhân vật còn khả năng chiến đấu", "Không bị giới hạn bởi cơ chế cạn Mana, Năng lượng hoặc Quá nhiệt nội tại; hồi 2% Max HP mỗi lượt, tăng lên 4% khi Devil Trigger.", "Không hồi từ 0 HP."),
    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định", "Đọc chuyển động và chuẩn bị phản đòn; hỗ trợ Counterphase."),
    s("Rift Sever", "AUTO", "30% mỗi lượt hợp lệ", "Spatial Shift làm lệch trục phòng thủ rồi chém, 175% DMG vũ khí, bỏ qua 20% Giáp."),
    s("Crimson Guillotine", "AUTO", "20% mỗi lượt hợp lệ", "190% DMG vũ khí; Chảy máu trong 3 lượt, mỗi lượt 4% Max HP."),
    s("Lucifer Breaker", "AUTO", "20% mỗi lượt hợp lệ", "Chuỗi cận chiến kết hợp GodKiller gây 155% DMG vũ khí; làm gián đoạn phản ứng hiện tại của Entity bằng Choáng."),
    s("Counterphase", "COUNTER", "30% sau khi Entity phản công hụt", "Spatial Shift vào góc chết và phản chém 125% DMG vũ khí; không chiếm lượt chính."),
    s("GodKiller Recall", "PASSIVE", "Khi bị tước vũ khí hợp lệ", "Gọi GodKiller trở lại ở đầu lượt kế tiếp nếu không có luật của boss khóa khả năng triệu hồi."),
    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% DMG gây ra, +20% Né tránh, -20% DMG nhận vào theo vai trò cá nhân; hồi phục từ Lucifer Core tăng lên 4% Max HP mỗi lượt.", "Không có hồi chiêu nội tại, không giới hạn thời gian theo canon."),
    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger", "Chuỗi Spatial Shift kết hợp GodKiller gây 210% DMG vũ khí; Mất phương hướng làm giảm 25% Độ chính xác trong 2 lượt."),
    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Mỗi 3 lượt chiến đấu khi Devil Trigger", "Dừng thời gian ngoại giới, đúng 24 nhát chém x 10 HP = 240 HP; bỏ qua Né tránh.", "Không phải đòn kết liễu tuyệt đối.")
  )

  private val anNhien = listOf(
    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên theo Party", "Giảm 25% xác suất gặp nguy hiểm trong hành động vật lý hợp lệ."),
    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi SEARCH", "+10 điểm phần trăm vào tỷ lệ rơi vật phẩm chung hiện có.", "Không tạo lần kiểm tra rơi vật phẩm thứ hai."),
    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống xấu", "Ưu tiên vị trí an toàn; không biến An Nhiên thành nhân vật chiến đấu."),
    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% mỗi lượt chiến đấu khi đang ở trong Party", "Ném vật vô hại để đánh lạc hướng, Entity giảm 25 điểm % Độ chính xác trong phản ứng hiện tại.", "Không gây sát thương, không dùng vũ khí."),
    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi SEARCH một Exit", "Nếu kích hoạt, +20 điểm phần trăm cho lần kiểm tra Exit của hành động đó."),
    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi kiểm tra nước hoặc chất lỏng khả nghi", "Nếu kích hoạt, chặn lần kiểm tra nguy hiểm của hành động đó.", "Chỉ là kiểm tra nguy cơ, không tự biết toàn bộ bản chất vật thể."),
    s("Thôi Để Tôi Làm", "UTILITY", "Khi xử lý thao tác sinh tồn", "Đại diện lợi thế thực dụng trong lời kể của Game Master; không áp dụng cho hack, phép thuật hoặc công nghệ ngoài khả năng."),
    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Mỗi 5 lượt chiến đấu khi đang ở trong Party", "Tận dụng địa hình: +30 Tiến độ thoát và Entity giảm 20 điểm % Độ chính xác trong phản ứng hiện tại.", "Không gây sát thương.")
  )

  private val kai = listOf(
    s("The Last Requiem", "AUTO", "38% mỗi lượt hợp lệ", "SRU-SG: 4 viên đạn quỷ lực theo nhịp giật kiểm soát, đặt chùm đạn vào điểm neo vận động ở vai; 170% DMG vũ khí; Chảy máu 3 lượt x 5% Max HP."),
    s("Silent Lullaby", "AUTO", "27% mỗi lượt hợp lệ", "SRU-SG: 4 viên đạn quỷ lực vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ tản; 130% DMG vũ khí; Choáng 1 lượt."),
    s("Salvation", "AUTO", "26% mỗi lượt hợp lệ", "Bứt tốc qua góc chết, ghì SRU-SG bằng hai tay ở cự ly gần và khai hỏa 2 viên đạn quỷ lực; 147% DMG vũ khí."),
    s("Quick Step", "AUTO", "35% mỗi lượt hợp lệ", "Đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU-SG sẵn bắn; +50 điểm % Né tránh trong 3 lượt đối với phản công thường."),
    s("Guilty Crown Override", "ULTIMATE", "Mỗi 3 lượt chiến đấu", "Đúng 24 phát x 10 HP, Độ chính xác 200%, bỏ qua Né tránh.")
  )

  private val lucia = listOf(
    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia ở trong Party", "+5 điểm phần trăm vào tỷ lệ rơi vật phẩm chung hiện có."),
    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cả Kai và Lucia cùng tấn công", "Lucia có lượt xử lý bắn M4A1 riêng và vẫn phải qua kiểm tra Né tránh của Entity."),
    s("M4A1 Full Auto Burst", "AUTO", "20% mỗi 2 lượt chiến đấu hợp lệ khi Party chọn TẤN CÔNG", "Xả đúng 30 viên; mỗi viên gây 30 + Base DMG trước Giáp; toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity."),
    s("Too Young To Die", "AUTO", "15% mỗi lượt chiến đấu; khi HP < 50%, +5 điểm % mỗi 3 điểm % HP mất thêm dưới ngưỡng 50%", "Xả hết băng 60 viên; mỗi viên gây Base DMG +5% trước Giáp và các hiệu ứng tăng cường ngoài kỹ năng; toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity.", "Ví dụ: 49% HP = 15%, 47% = 20%, 44% = 25%; tỷ lệ tối đa 100%.")
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
''', encoding="utf-8")

# Update only stale catalog assertions created by older patch layers. Runtime
# reply assertions remain governed by the combat-narration localizer.
test = TEST.read_text(encoding="utf-8")
test = test.replace(
    'assertFalse(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.effect.contains("Weapon DMG") })',
    'assertFalse(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.effect.contains("DMG vũ khí") })',
)
test = test.replace(
    'assertFalse(fragment.contains("Weapon DMG"))',
    'assertFalse(fragment.contains("sát thương vũ khí"))',
)
test = test.replace('skill.trigger.contains("2 combat turn")', 'skill.trigger.contains("2 lượt chiến đấu")')
test = test.replace('skill.effect.contains("Entity Evasion")', 'skill.effect.contains("Né tránh của Entity")')

regression = r'''
  @org.junit.Test fun issue125SkillDescriptionsUseVietnameseGameplayWording() {
    val all = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUCIA_ID)
      .flatMap(CompanionSkillCatalog::forCharacter)
    val forbidden = listOf(
      " combat ", " turn", "Weapon DMG", " Armor", "Evasion", "Accuracy",
      "Bleeding", "Stun", " proc", " gate", "resolution", "hazard",
      "generic loot roll", "outgoing", "incoming", "Fully Exposed",
      "Armor Break", "Disarm", "Disoriented", " damage"
    )
    all.forEach { skill ->
      val prose = listOfNotNull(skill.trigger, skill.effect, skill.note).joinToString(" ")
      forbidden.forEach { token ->
        org.junit.Assert.assertFalse("${skill.name} still contains mixed-English token: $token | $prose", prose.contains(token, ignoreCase = false))
      }
    }
    org.junit.Assert.assertTrue(all.any { it.effect.contains("DMG") })
    org.junit.Assert.assertTrue(all.any { it.effect.contains("HP") })
  }
'''
if "issue125SkillDescriptionsUseVietnameseGameplayWording" not in test:
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Issue #125: CompanionSkillCatalogTest closing brace missing")
    test = test[:close] + "\n" + regression.rstrip() + test[close:]
TEST.write_text(test, encoding="utf-8")

catalog = CATALOG.read_text(encoding="utf-8")
for marker in (
    '"Phân tích trong 3 lượt:',
    '"185% DMG vũ khí; Phá Giáp 20% trong 2 lượt."',
    '"+25% DMG gây ra, +20% Né tránh, -20% DMG nhận vào',
    '"Nếu kích hoạt, +20 điểm phần trăm cho lần kiểm tra Exit',
    '"170% DMG vũ khí; Chảy máu 3 lượt',
    '"toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity."',
):
    if marker not in catalog:
        raise RuntimeError("Issue #125 skill-description contract missing: " + marker)

print("Issue #125 applied: skill names and DMG/HP notation are preserved while trigger/effect/note prose is Vietnamese.")
