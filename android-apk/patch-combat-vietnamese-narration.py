from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
REGRESSION = TESTS / "CombatVietnameseNarrationTest.kt"

combat = COMBAT.read_text(encoding="utf-8")

# Issue #123: user-facing combat narration is Vietnamese except canonical skill names
# and accepted combat notation such as HP / Max HP. Centralize the wording here so
# every current and future combat log line receives the same treatment without
# changing gameplay/state identifiers or canonical skill names.
helper_anchor = '  private const val PREFIX = "combat."\n'
helper = r'''  // COMBAT_VIETNAMESE_NARRATION_V1
  internal fun localizeCombatNarration(text: String): String = text
    .replace("PARTY ACTION TẤN CÔNG:", "HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG:")
    .replace("PARTY ACTION NÉ TRÁNH:", "HÀNH ĐỘNG CỦA ĐỘI - NÉ TRÁNH:")
    .replace("PARTY ACTION BỎ CHẠY:", "HÀNH ĐỘNG CỦA ĐỘI - BỎ CHẠY:")
    .replace("cùng khai triển đòn đánh trong một combat turn.", "cùng khai triển đòn đánh trong một lượt tấn công.")
    .replace("cùng thực hiện trong một combat turn.", "cùng thực hiện trong một lượt chiến đấu.")
    .replace("cùng rút khỏi encounter trong một combat turn.", "cùng rút khỏi giao tranh trong một lượt chiến đấu.")
    .replace("vulnerable Blink/Blind/Stun", "dễ bị ảnh hưởng bởi Blink/Blind/Stun")
    .replace("Stun không proc", "hiệu ứng Choáng không kích hoạt")
    .replace("Stun 1 turn", "Choáng 1 lượt")
    .replace("Stun 1 lượt", "Choáng 1 lượt")
    .replace("bị Stun", "bị Choáng")
    .replace("Bleeding", "Chảy máu")
    .replace("CD ", "hồi chiêu còn ")
    .replace("tỷ lệ proc", "tỷ lệ kích hoạt")
    .replace("proc hiện tại", "kích hoạt hiện tại")
    .replace("% proc", "% tỷ lệ kích hoạt")
    .replace("không proc", "không kích hoạt")
    .replace("Weapon DMG", "sát thương vũ khí")
    .replace("Base DMG", "sát thương cơ bản")
    .replace("DMG", "sát thương")
    .replace(" damage", " sát thương")
    .replace(" Evasion", " Né tránh")
    .replace("Accuracy ", "Độ chính xác ")
    .replace("(CRITICAL)", "(CHÍ MẠNG)")
    .replace("Entity turn", "lượt của Entity")
    .replace("combat turn", "lượt chiến đấu")
    .replace(" turn", " lượt")
    .replace("encounter", "giao tranh")
    .replace("Party", "đội")
    .replace("Armor", "Giáp")
    .replace("buff", "hiệu ứng tăng cường")
    .replace("Forced Blink", "Blink cưỡng bức")
    .replace("blinkCounter", "bộ đếm chớp mắt")
    .replace("State=", "Trạng thái=")
    .replace("first UNOBSERVED strike", "đòn đầu khi không bị quan sát")
    .replace("đang OBSERVED", "đang được quan sát")
    .replace("ở UNOBSERVED", "không bị quan sát")
    .replace("Execution hợp lệ", "Kết liễu hợp lệ")
    .replace("narration", "lời tường thuật")
    .replace("shell", "viên đạn")
    .replace("; ", " • ")

'''

if "COMBAT_VIETNAMESE_NARRATION_V1" not in combat:
    if helper_anchor not in combat:
        raise RuntimeError("Issue #123: CombatRuntime prefix anchor missing")
    combat = combat.replace(helper_anchor, helper_anchor + helper, 1)

    join = 'log.joinToString(" ")'
    join_count = combat.count(join)
    if join_count < 2:
        raise RuntimeError(f"Issue #123: expected multiple combat reply joins, found {join_count}")
    combat = combat.replace(join, 'localizeCombatNarration(log.joinToString(" "))')

COMBAT.write_text(combat, encoding="utf-8")

# Earlier generated tests assert the old wording. Only rewrite reply assertions,
# leaving state/status identifiers and skill-catalog contracts untouched.
reply_replacements = (
    ("PARTY ACTION TẤN CÔNG:", "HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG:"),
    ("PARTY ACTION NÉ TRÁNH:", "HÀNH ĐỘNG CỦA ĐỘI - NÉ TRÁNH:"),
    ("PARTY ACTION BỎ CHẠY:", "HÀNH ĐỘNG CỦA ĐỘI - BỎ CHẠY:"),
    ("Bleeding từ The Last Requiem", "Chảy máu từ The Last Requiem"),
    ("bị Stun và mất lượt phản ứng hiện tại", "bị Choáng và mất lượt phản ứng hiện tại"),
    ("+50% Evasion trong 3 turn", "+50% Né tránh trong 3 lượt"),
    ("Accuracy 200%", "Độ chính xác 200%"),
    ("Stun 1 lượt", "Choáng 1 lượt"),
    ("Execution hợp lệ", "Kết liễu hợp lệ"),
)
for path in TESTS.glob("*.kt"):
    source = path.read_text(encoding="utf-8")
    lines = []
    for line in source.splitlines(keepends=True):
        if ".reply" in line:
            for old, new in reply_replacements:
                line = line.replace(old, new)
            # This assertion refers to rendered narration, not the authoritative
            # observationState JSON, which intentionally remains OBSERVED/UNOBSERVED.
            if 'result.reply.contains("OBSERVED")' in line:
                line = line.replace('result.reply.contains("OBSERVED")', 'result.reply.contains("được quan sát")')
        lines.append(line)
    updated = "".join(lines)
    if updated != source:
        path.write_text(updated, encoding="utf-8")

REGRESSION.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatVietnameseNarrationTest {
  @Test fun issue123ExampleIsVietnameseWhileSkillNamesStayCanonical() {
    val raw = "Bleeding từ The Last Requiem gây -97 HP (5% Max HP; 381/1930); còn 1 turn. " +
      "Concrete Rush: Kai Akechi -35 HP (25% Max HP, vulnerable Blink/Blind/Stun); CD 2; Stun 1 lượt (35% proc). " +
      "SCP-173 Snap Strike -11 HP (10% Max HP); Stun không proc."

    val localized = CombatRuntime.localizeCombatNarration(raw)

    assertEquals(
      "Chảy máu từ The Last Requiem gây -97 HP (5% Max HP • 381/1930) • còn 1 lượt. " +
        "Concrete Rush: Kai Akechi -35 HP (25% Max HP, dễ bị ảnh hưởng bởi Blink/Blind/Stun) • hồi chiêu còn 2 • Choáng 1 lượt (35% tỷ lệ kích hoạt). " +
        "SCP-173 Snap Strike -11 HP (10% Max HP) • hiệu ứng Choáng không kích hoạt.",
      localized
    )
    assertTrue(localized.contains("The Last Requiem"))
    assertTrue(localized.contains("Concrete Rush"))
    assertTrue(localized.contains("Snap Strike"))
    assertFalse(localized.contains("Bleeding"))
    assertFalse(localized.contains("CD 2"))
    assertFalse(localized.contains("Stun không proc"))
  }

  @Test fun partyActionHeaderAndCombatTurnAreVietnamese() {
    val raw = "PARTY ACTION TẤN CÔNG: Kai Akechi, Lucia \"Lục\" cùng khai triển đòn đánh trong một combat turn."
    assertEquals(
      "HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG: Kai Akechi, Lucia \"Lục\" cùng khai triển đòn đánh trong một lượt tấn công.",
      CombatRuntime.localizeCombatNarration(raw)
    )
  }
}
''', encoding="utf-8")

final_combat = COMBAT.read_text(encoding="utf-8")
for marker in (
    "COMBAT_VIETNAMESE_NARRATION_V1",
    "HÀNH ĐỘNG CỦA ĐỘI - TẤN CÔNG:",
    "dễ bị ảnh hưởng bởi Blink/Blind/Stun",
    "hiệu ứng Choáng không kích hoạt",
    "localizeCombatNarration(log.joinToString(\" \"))",
):
    if marker not in final_combat:
        raise RuntimeError("Issue #123 combat narration contract missing: " + marker)

if final_combat.count('localizeCombatNarration(log.joinToString(" "))') < 2:
    raise RuntimeError("Issue #123: not every combat reply join is localized")

print("Issue #123 applied: combat narration is Vietnamese, skill names remain canonical, and semicolon separators render as bullets.")