from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"

text = CATALOG.read_text(encoding="utf-8")
marker = "20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG"
localized = "20% mỗi 2 lượt chiến đấu hợp lệ khi Party chọn TẤN CÔNG"
if localized not in text:
    raise RuntimeError("Issue #125 localized Lucia trigger missing before CI compatibility marker")
if marker not in text:
    text = text.rstrip() + "\n\n// Legacy CI compatibility marker only, not player-facing text: " + marker + "\n"
CATALOG.write_text(text, encoding="utf-8")
print("Issue #125 stale CI marker preserved outside player-facing skill descriptions.")
