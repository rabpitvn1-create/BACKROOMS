from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-skill-description-vietnamese.py"
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
