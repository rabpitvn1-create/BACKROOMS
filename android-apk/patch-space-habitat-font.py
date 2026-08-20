from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
SHARED_FONT_CSS = ROOT.parent / "app/space-habitat.css"

index = INDEX.read_text(encoding="utf-8")
font_css = SHARED_FONT_CSS.read_text(encoding="utf-8").strip()

if 'font-family: "MBF Space Habitat"' not in font_css:
    raise RuntimeError("Space Habitat shared CSS is missing the expected font family")
if ".log," not in font_css or ".composer textarea" not in font_css:
    raise RuntimeError("Space Habitat CSS must preserve the chat/log font override")

marker = '<style data-backroom-space-habitat="1">'
if marker not in index:
    anchor = "<style>\n"
    if anchor not in index:
        raise RuntimeError("APK index style anchor not found")
    injected = f'{marker}\n{font_css}\n</style>\n'
    index = index.replace(anchor, injected + anchor, 1)

INDEX.write_text(index, encoding="utf-8")
print("Space Habitat applied to APK UI outside the chat log/input area.")
