"""Install the final Cao Minh prologue after identity and kit migration patches."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "app/src/main/assets/index.html"

PROLOGUE = (ROOT / "cao-minh-prologue.txt").read_text(encoding="utf-8").strip()

html = HTML.read_text(encoding="utf-8")

start_token = "const prologue=`"
start = html.index(start_token)
end_match = re.search(r"`;\s*const initial=", html[start:])
if not end_match:
    raise RuntimeError("Missing prologue closing anchor")
end = start + end_match.start()
html = html[:start] + "const prologue=`" + PROLOGUE + "`;" + html[end + 2:]

old_turn = '{role:"gm",text:"LƯỢT 1\\n\\nKhông có liên lạc với Iris, Syvial hay Black Blood. Bạn điều khiển Cao Minh từ đây."}'
new_turn = '{role:"gm",text:"LƯỢT 1\\n\\nCao Minh tỉnh lại một mình tại Level 0. Không có dấu vết của Lục Trầm. Bạn điều khiển Cao Minh từ đây."}'
if old_turn not in html:
    raise RuntimeError("Missing Cao Minh Turn 1 prologue handoff anchor")
html = html.replace(old_turn, new_turn, 1)

signature_anchor = 'const oldPrologueSignature="Không beacon. Không telemetry.";'
if signature_anchor not in html:
    raise RuntimeError("Missing prologue migration signature anchor")
html = html.replace(
    signature_anchor,
    signature_anchor + '\nconst legacyCaoPrologueSignature="Bữa tối bắt đầu như bao lần khác.";',
    1,
)

old_condition = 'if(currentOpening.length<1000||currentOpening.includes(oldPrologueSignature)){'
new_condition = 'if(currentOpening.length<1000||currentOpening.includes(oldPrologueSignature)||currentOpening.includes(legacyCaoPrologueSignature)){'
if old_condition not in html:
    raise RuntimeError("Missing prologue migration condition anchor")
html = html.replace(old_condition, new_condition, 1)

old_turn_migration = 'if(state.log[1]&&String(state.log[1].text||"").startsWith("TURN 1")) state.log[1]=initial.log[1];'
new_turn_migration = 'if(state.log[1]&&(String(state.log[1].text||"").startsWith("TURN 1")||String(state.log[1].text||"").startsWith("LƯỢT 1"))) state.log[1]=initial.log[1];'
if old_turn_migration not in html:
    raise RuntimeError("Missing Turn 1 migration anchor")
html = html.replace(old_turn_migration, new_turn_migration, 1)

HTML.write_text(html, encoding="utf-8")

final = HTML.read_text(encoding="utf-8")
if not final.split("const prologue=`", 1)[1].startswith("Lôi Thiên Vực chưa từng có một ngày yên tĩnh như thế."):
    raise RuntimeError("Cao Minh prologue was not installed")
if new_turn not in final:
    raise RuntimeError("Cao Minh Turn 1 handoff was not installed")
if "legacyCaoPrologueSignature" not in final:
    raise RuntimeError("Turn 1 prologue migration was not installed")

print("Cao Minh final-world prologue installed and Turn 1 migration updated.")
