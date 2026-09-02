from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"

catalog = CATALOG.read_text(encoding="utf-8")

# This patch runs after every legacy/natural-language skill rewrite. Target the
# canonical skill-name row, not one historical wording of that row.
targets = {
    "The Last Requiem": '    s("The Last Requiem", "AUTO", "38% ở mỗi lượt hợp lệ", "SRU-SG: 12 viên đạn quỷ lực Sparda 5.56×45 mm theo loạt bắn kiểm soát vào điểm neo vận động ở vai; 170% sát thương vũ khí; Chảy máu 3 lượt x 5% Máu tối đa."),',
    "Silent Lullaby": '    s("Silent Lullaby", "AUTO", "27% ở mỗi lượt hợp lệ", "SRU-SG: 12 viên đạn quỷ lực Sparda 5.56×45 mm vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ dẫn; 130% sát thương vũ khí; Choáng 1 lượt."),',
    "Salvation": '    s("Salvation", "AUTO", "26% ở mỗi lượt hợp lệ", "Kai bứt qua góc chết, giữ SRU-SG ở tư thế kiểm soát và khai hỏa 6 viên đạn quỷ lực Sparda 5.56×45 mm ở cự ly gần; 147% sát thương vũ khí."),',
    "Quick Step": '    s("Quick Step", "AUTO", "35% ở mỗi lượt hợp lệ", "Kai liên tục đổi góc bằng những pha bứt tốc ngắn nhưng vẫn giữ SRU-SG ở tư thế sẵn bắn. Trong 3 lượt, Kai nhận thêm 50 điểm phần trăm Né tránh trước các phản công thông thường."),',
    "Guilty Crown Override": '    s("Guilty Crown Override", "ULTIMATE", "Tự động sau mỗi 3 lượt chiến đấu", "Trong game R10: đúng 72 viên, mỗi viên gây 10 Máu cơ sở; Codex base 24 viên, hệ số số đạn ×3; Độ chính xác 200%, bỏ qua Né tránh."),',
}

lines = catalog.splitlines()
for skill_name, replacement in targets.items():
    prefix = f'    s("{skill_name}",'
    indexes = [i for i, line in enumerate(lines) if line.startswith(prefix)]
    if len(indexes) != 1:
        raise RuntimeError(
            f"Kai R10 skill precompat {skill_name}: expected exactly one final skill row, found {len(indexes)}"
        )
    lines[indexes[0]] = replacement

catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
CATALOG.write_text(catalog, encoding="utf-8")

for marker in ("12 viên", "6 viên", "72 viên", "Codex base 24 viên"):
    if marker not in catalog:
        raise RuntimeError("Kai R10 skill precompat missing: " + marker)

for forbidden in ("DMG", "Max HP", "Base DMG"):
    for skill_name in targets:
        row = next(line for line in catalog.splitlines() if line.startswith(f'    s("{skill_name}",'))
        if forbidden in row:
            raise RuntimeError(f"Kai R10 skill precompat reintroduced mixed-language token {forbidden}: {skill_name}")

print("Kai R10 skill catalog precompat applied: final Kai rows now expose 12/12/6/72 gameplay round counts.")
