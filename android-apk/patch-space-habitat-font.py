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

# The original patch inserted the custom font BEFORE the base <style> block.
# index.html later declares `body{...font:15px system-ui,sans-serif}`, which
# overwrote the new family. Insert this style AFTER the base CSS instead so the
# intended outer-UI font wins the cascade.
marker = '<style data-backroom-space-habitat="1">'
forced_outer_ui = r'''
body,
.shell,
.topbar,
.topbar *,
.snapshot,
.snapshot *,
.composer button,
.status,
.side,
.side * {
  font-family: var(--backroom-ui-font) !important;
}

.log,
.log *,
.composer textarea {
  font-family: var(--backroom-chat-font) !important;
}
'''.strip()

injected = f'{marker}\n{font_css}\n{forced_outer_ui}\n</style>'

if marker in index:
    start = index.index(marker)
    end = index.index("</style>", start) + len("</style>")
    index = index[:start] + injected + index[end:]
else:
    base_close = index.find("</style>")
    if base_close < 0:
        raise RuntimeError("APK index base style closing tag not found")
    insert_at = base_close + len("</style>")
    index = index[:insert_at] + "\n" + injected + index[insert_at:]

# Structural proof: custom style must appear AFTER the original base style.
base_style = index.find("<style>")
custom_style = index.find(marker)
if base_style < 0 or custom_style < 0 or custom_style <= base_style:
    raise RuntimeError("Space Habitat override must be injected after the base APK CSS")
if "font-family: var(--backroom-ui-font) !important" not in index:
    raise RuntimeError("Space Habitat outer UI override missing")
if "font-family: var(--backroom-chat-font) !important" not in index:
    raise RuntimeError("Space Habitat chat preservation override missing")

INDEX.write_text(index, encoding="utf-8")
print("Space Habitat applied after base APK CSS; outer UI now wins cascade while chat keeps readable font.")
