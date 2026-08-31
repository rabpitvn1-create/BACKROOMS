from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

html = INDEX.read_text(encoding="utf-8")
java = MAIN.read_text(encoding="utf-8")

# Player-facing TURN and ESCAPE percentages are presentation noise. Keep the existing internal
# counters/state for idempotency, saves, legacy compatibility and diagnostics, but do not expose
# them in the HUD. Registered Levels resolve escape through locked blueprints rather than a visible
# probability meter.
if ".turn{display:none!important}" not in html:
    html, count = re.subn(r"\.turn\{[^}]*\}", ".turn{display:none!important}", html, count=1)
    if count == 0:
        head_end = html.find("</head>")
        if head_end < 0:
            raise RuntimeError("progress HUD head anchor missing")
        html = html[:head_end] + "<style>.turn{display:none!important}</style>\n" + html[head_end:]

html = html.replace('placeholder="Kai làm gì trong Turn hiện tại?"', 'placeholder="Kai làm gì?"')
html = html.replace('placeholder="Kai làm gì trong turn hiện tại?"', 'placeholder="Kai làm gì?"')

for forbidden in (
    'id="escapeChance"',
    'ESCAPE_CHANCE_HUD_R01',
    'ESCAPE_CHANCE_HUD_R02',
    'window.Android.getEscapeChancePercent',
    'function renderEscapeChance()',
):
    if forbidden in html:
        raise RuntimeError("Obsolete player-facing escape HUD survived: " + forbidden)

if "getEscapeChancePercent(String stateJson)" in java:
    raise RuntimeError("Obsolete Android escape-percent bridge survived")
if ".turn{display:none!important}" not in html:
    raise RuntimeError("TURN HUD is not hidden")
if 'id="turn"' not in html:
    raise RuntimeError("Internal WebView turn binding must remain available for legacy bookkeeping")

INDEX.write_text(html, encoding="utf-8")
print("Progress HUD finalized: TURN remains internal-only and ESCAPE percentage is not exposed to the player.")
