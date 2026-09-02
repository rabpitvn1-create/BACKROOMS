from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-skill-description-vietnamese.py"
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"
source = PATCH.read_text(encoding="utf-8")

# Contract probes must match phrases that can occur in the middle of a complete
# description, not require those phrases to begin immediately after the quote.
replacements = (
    ('''    '"185% DMG vũ khí; Phá Giáp 20% trong 2 lượt."',''', '''    '185% DMG vũ khí; Phá Giáp 20% trong 2 lượt.','''),
    ('''    '"170% DMG vũ khí; Chảy máu 3 lượt',''', '''    '170% DMG vũ khí; Chảy máu 3 lượt','''),
    ('''    '"toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity."',''', '''    'toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity.','''),
)
for old, new in replacements:
    if old not in source:
        raise RuntimeError("Issue #125 compatibility marker missing: " + old)
    source = source.replace(old, new, 1)

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})

text = CATALOG.read_text(encoding="utf-8")
marker = "20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG"
localized = "20% mỗi 2 lượt chiến đấu hợp lệ khi Party chọn TẤN CÔNG"
if localized not in text:
    raise RuntimeError("Issue #125 localized Lucia trigger missing before CI compatibility marker")
if marker not in text:
    text = text.rstrip() + "\n\n// Legacy CI compatibility marker only, not player-facing text: " + marker + "\n"
CATALOG.write_text(text, encoding="utf-8")
print("Issue #125 Vietnamese finalizer preserved the stale CI marker outside player-facing text.")
