from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"

catalog = CATALOG.read_text(encoding="utf-8")

replacements = (
    (
        '    s("The Last Requiem", "AUTO", "38% ở mỗi lượt hợp lệ", "Kai ghìm nhịp giật của SRU-SG và bắn 4 viên đạn quỷ lực vào vùng vai để phá nhịp vận động của mục tiêu. Kỹ năng gây 170% DMG vũ khí và Chảy máu trong 3 lượt, mỗi lượt mất 5% Max HP."),',
        '    s("The Last Requiem", "AUTO", "38% ở mỗi lượt hợp lệ", "SRU-SG: 12 viên đạn quỷ lực Sparda 5.56×45 mm theo loạt bắn kiểm soát vào điểm neo vận động ở vai; 170% DMG vũ khí; Chảy máu 3 lượt x 5% Max HP."),',
        "The Last Requiem",
    ),
    (
        '    s("Silent Lullaby", "AUTO", "27% ở mỗi lượt hợp lệ", "Kai giữ chặt SRU-SG và dồn 4 viên đạn quỷ lực vào cùng một vùng trọng yếu trên ngực. Kỹ năng gây 130% DMG vũ khí và Choáng mục tiêu trong 1 lượt."),',
        '    s("Silent Lullaby", "AUTO", "27% ở mỗi lượt hợp lệ", "SRU-SG: 12 viên đạn quỷ lực Sparda 5.56×45 mm vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ dẫn; 130% DMG vũ khí; Choáng 1 lượt."),',
        "Silent Lullaby",
    ),
    (
        '    s("Salvation", "AUTO", "26% ở mỗi lượt hợp lệ", "Kai bứt qua góc chết, áp sát rồi ghì SRU-SG bằng hai tay để bắn 2 viên đạn quỷ lực ở cự ly gần, gây 147% DMG vũ khí."),',
        '    s("Salvation", "AUTO", "26% ở mỗi lượt hợp lệ", "Kai bứt qua góc chết, giữ SRU-SG ở tư thế kiểm soát và khai hỏa 6 viên đạn quỷ lực Sparda 5.56×45 mm ở cự ly gần; 147% DMG vũ khí."),',
        "Salvation",
    ),
    (
        '    s("Quick Step", "AUTO", "35% ở mỗi lượt hợp lệ", "Kai liên tục đổi góc bằng những pha bứt tốc ngắn nhưng vẫn giữ SRU-SG ở tư thế sẵn bắn. Trong 3 lượt, Kai nhận thêm 50 điểm phần trăm Né tránh trước các phản công thông thường."),',
        '    s("Quick Step", "AUTO", "35% ở mỗi lượt hợp lệ", "Kai liên tục đổi góc bằng những pha bứt tốc ngắn nhưng vẫn giữ SRU-SG ở tư thế sẵn bắn. Trong 3 lượt, Kai nhận thêm 50 điểm phần trăm Né tránh trước các phản công thông thường."),',
        "Quick Step",
    ),
    (
        '    s("Guilty Crown Override", "ULTIMATE", "Tự động sau mỗi 3 lượt chiến đấu", "Kai tung đúng 24 phát liên tiếp. Mỗi phát gây 10 HP, tổng cộng 240 HP; Độ chính xác của kỹ năng là 200% và đòn bắn bỏ qua Né tránh."),',
        '    s("Guilty Crown Override", "ULTIMATE", "Tự động sau mỗi 3 lượt chiến đấu", "Gameplay R10: đúng 72 viên x 10 HP cơ sở; Codex base 24 viên, hệ số số đạn ×3; Độ chính xác 200%, bỏ qua Né tránh."),',
        "Guilty Crown Override",
    ),
)

for old, new, label in replacements:
    if new in catalog:
        continue
    count = catalog.count(old)
    if count != 1:
        raise RuntimeError(f"Kai R10 skill precompat {label}: expected exactly one final-natural anchor, found {count}")
    catalog = catalog.replace(old, new, 1)

CATALOG.write_text(catalog, encoding="utf-8")

for marker in ("12 viên", "6 viên", "72 viên", "Codex base 24 viên"):
    if marker not in catalog:
        raise RuntimeError("Kai R10 skill precompat missing: " + marker)

print("Kai R10 skill catalog precompat applied: final-natural Kai skill lines now expose 12/12/6/72 gameplay round counts.")
