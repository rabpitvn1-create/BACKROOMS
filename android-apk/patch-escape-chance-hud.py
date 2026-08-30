from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

html = INDEX.read_text(encoding="utf-8")
java = MAIN.read_text(encoding="utf-8")

# Keep the HUD value tied to the same baseline percentage used by the Android
# level-exit roll instead of creating a second independently maintained number.
chance_matches = re.findall(r'rollSpec\("levelExit",\s*(\d+)\s*,', java)
if len(chance_matches) != 1:
    raise RuntimeError(
        f"Escape HUD expected exactly one levelExit baseline roll, found {len(chance_matches)}"
    )
base_escape_chance = int(chance_matches[0])
if not 0 <= base_escape_chance <= 100:
    raise RuntimeError(f"Escape HUD baseline chance is invalid: {base_escape_chance}")

old_turn = '<div class="turn">TURN <strong id="turn"></strong></div>'
new_turn = (
    '<div class="turn"><div>TURN <strong id="turn"></strong></div>'
    f'<div id="escapeChance">ESCAPE: {base_escape_chance}%</div></div>'
)

if 'id="escapeChance"' not in html:
    if html.count(old_turn) != 1:
        raise RuntimeError(
            f"Escape HUD turn anchor expected exactly 1 match, found {html.count(old_turn)}"
        )
    html = html.replace(old_turn, new_turn, 1)

marker = "// ESCAPE_CHANCE_HUD_R01"
if marker not in html:
    render_anchor = "function render(){"
    if html.count(render_anchor) != 1:
        raise RuntimeError(
            f"Escape HUD render anchor expected exactly 1 match, found {html.count(render_anchor)}"
        )
    helper = f'''{marker}
const BASE_ESCAPE_CHANCE_PERCENT={base_escape_chance};
function escapeChancePercent(){{
  const exploration=state&&state.flags&&state.flags.exploration;
  const confirmedExit=exploration&&exploration.confirmedExit;
  return typeof confirmedExit==="string"&&confirmedExit.trim()?100:BASE_ESCAPE_CHANCE_PERCENT;
}}
function renderEscapeChance(){{
  const el=byId("escapeChance");
  if(el)el.textContent="ESCAPE: "+escapeChancePercent()+"%";
}}
'''
    html = html.replace(render_anchor, helper + render_anchor, 1)

render_start = "function render(){"
render_with_escape = "function render(){renderEscapeChance();"
if render_with_escape not in html:
    if html.count(render_start) != 1:
        raise RuntimeError(
            f"Escape HUD render start expected exactly 1 match, found {html.count(render_start)}"
        )
    html = html.replace(render_start, render_with_escape, 1)

# Contract checks: ESCAPE deliberately lives inside `.turn`, so it inherits the
# exact TURN label typography instead of introducing a second HUD text style.
required = (
    'id="escapeChance"',
    marker,
    f"const BASE_ESCAPE_CHANCE_PERCENT={base_escape_chance};",
    'return typeof confirmedExit==="string"&&confirmedExit.trim()?100:BASE_ESCAPE_CHANCE_PERCENT;',
    'function render(){renderEscapeChance();',
    '<div class="turn"><div>TURN <strong id="turn"></strong></div><div id="escapeChance">',
)
for contract in required:
    if contract not in html:
        raise RuntimeError(f"Escape HUD contract missing: {contract}")

if html.count('id="escapeChance"') != 1:
    raise RuntimeError("Escape HUD must contain exactly one escapeChance element")

INDEX.write_text(html, encoding="utf-8")
print(
    f"Escape HUD applied: ESCAPE inherits TURN typography, shows {base_escape_chance}% baseline and 100% for a confirmed exit."
)
