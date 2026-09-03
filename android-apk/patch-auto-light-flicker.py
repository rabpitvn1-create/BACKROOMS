"""Install the background-only automatic light flicker layer after snapshot/combat overlays exist."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ENGINE = ROOT / "app/src/main/assets/auto-light-flicker.js"

if not ENGINE.is_file() or ENGINE.stat().st_size <= 0:
    raise RuntimeError("Auto light flicker engine asset is missing")

html = HTML.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
if "className='snapshot-bg'" not in main:
    raise RuntimeError("Auto light flicker requires the final layered snapshot background renderer")

style = r'''<style id="autoLightFlickerStyle">
.snapshot .snapshot-auto-light-layer{position:absolute;inset:0;width:100%;height:100%;z-index:1;pointer-events:none;mix-blend-mode:screen;opacity:.1;filter:blur(1.5px);transform:translateZ(0);animation:snapshotAutoLightFlicker var(--auto-light-period,5.2s) ease-in-out var(--auto-light-delay,0ms) infinite;will-change:opacity}
@keyframes snapshotAutoLightFlicker{0%,100%{opacity:.08}16%{opacity:.2}34%{opacity:.11}52%{opacity:.29}69%{opacity:.15}84%{opacity:.24}}
.auto-light-paused .snapshot-auto-light-layer{animation-play-state:paused}
@media(prefers-reduced-motion:reduce){.snapshot .snapshot-auto-light-layer{animation:none!important;opacity:.13!important}}
</style>'''
script = '<script src="auto-light-flicker.js"></script>'

if 'id="autoLightFlickerStyle"' not in html:
    if html.count("</head>") != 1:
        raise RuntimeError(f"Auto light flicker head anchor expected once, found {html.count('</head>')}")
    html = html.replace("</head>", style + "\n</head>", 1)

if script not in html:
    if html.count("</body>") != 1:
        raise RuntimeError(f"Auto light flicker body anchor expected once, found {html.count('</body>')}")
    html = html.replace("</body>", script + "\n</body>", 1)

for marker in (
    'id="autoLightFlickerStyle"',
    'snapshotAutoLightFlicker',
    'snapshot-auto-light-layer',
    'src="auto-light-flicker.js"',
):
    if marker not in html:
        raise RuntimeError("Auto light flicker runtime marker missing: " + marker)

HTML.write_text(html, encoding="utf-8")
print("Automatic snapshot light flicker installed: background pixels are analyzed once per image; glow renders below character and Entity overlays.")
