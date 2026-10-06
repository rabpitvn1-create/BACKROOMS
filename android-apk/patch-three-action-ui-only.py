from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

button_row = '''<div class="primary-action-row" id="primaryActionRow">
<button type="button" class="primary-action" id="searchActionButton" aria-label="Tìm kiếm">
<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="5.5"></circle><path d="M14.7 14.7 20 20"></path></svg><span>Tìm kiếm</span>
</button>
<button type="submit" class="primary-action execute-action" id="submit" aria-label="Thực hiện">
<svg class="action-icon ai-action-icon" viewBox="0 0 28 24" aria-hidden="true"><path d="M3.5 5.5A2.5 2.5 0 0 1 6 3h11a2.5 2.5 0 0 1 2.5 2.5v7A2.5 2.5 0 0 1 17 15H6a2.5 2.5 0 0 1-2.5-2.5z"></path><text x="7" y="11.8">AI</text><path class="spark" d="M22 2v5m-2.5-2.5h5M23.5 9v3m-1.5-1.5h3"></path></svg><span>Thực hiện</span>
</button>
<button type="button" class="primary-action" id="exploreActionButton" aria-label="Khám phá">
<svg class="action-icon footprint-icon" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="8" cy="8" rx="3" ry="4.2" transform="rotate(-20 8 8)"></ellipse><ellipse cx="15.8" cy="15.5" rx="3" ry="4.2" transform="rotate(18 15.8 15.5)"></ellipse><circle cx="5.2" cy="3.4" r="1"></circle><circle cx="18.5" cy="10.3" r="1"></circle></svg><span>Khám phá</span>
</button>
</div>'''

if 'id="searchActionButton"' not in html:
    pattern = re.compile(r'<button\s+id="submit"[^>]*>.*?</button>', re.IGNORECASE | re.DOTALL)
    html, count = pattern.subn(button_row, html, count=1)
    if count != 1:
        raise RuntimeError(f"UI submit button anchor expected 1 match, found {count}")

css = r'''
/* STEP2_THREE_ACTIONS */
.primary-action-row{display:grid;grid-template-columns:1fr 1.12fr 1fr;gap:7px;width:100%}
.primary-action{min-width:0;min-height:46px;border-radius:9px;display:flex;align-items:center;justify-content:center;gap:7px;padding:10px 8px;white-space:nowrap}
.primary-action.execute-action{font-weight:800;border-color:#56616a;background:#20272d}
.primary-action .action-icon{width:19px;height:19px;flex:0 0 19px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.primary-action .footprint-icon ellipse,.primary-action .footprint-icon circle{fill:currentColor;stroke:none}
.primary-action .ai-action-icon{width:22px;flex-basis:22px}
.primary-action .ai-action-icon text{font:700 6.5px system-ui,sans-serif;fill:currentColor;stroke:none;letter-spacing:.2px}
.primary-action .ai-action-icon .spark{stroke-width:1.4}
@media(max-width:390px){.primary-action-row{gap:5px}.primary-action{font-size:12px;padding:9px 5px;gap:5px}.primary-action .action-icon{width:17px;height:17px;flex-basis:17px}.primary-action .ai-action-icon{width:20px;flex-basis:20px}}
'''
if "STEP2_THREE_ACTIONS" not in html:
    if "</style>" not in html:
        raise RuntimeError("UI style closing tag missing")
    html = html.replace("</style>", css + "\n</style>", 1)

# Freeform Execute is still the original form submit, but now enters the typed shared runtime.
submit_pattern = re.compile(r'(?:window\.)?Android\.submitTurn\(JSON\.stringify\(state\),a\)')
if 'Android.submitAction(JSON.stringify(state),"EXECUTE",a)' not in html:
    html, count = submit_pattern.subn('window.Android.submitAction(JSON.stringify(state),"EXECUTE",a)', html, count=1)
    if count != 1:
        raise RuntimeError(f"UI submitTurn call expected 1 match, found {count}")

js = r'''
// STEP2_TYPED_ACTIONS
const searchActionButton=byId("searchActionButton"),exploreActionButton=byId("exploreActionButton");
function syncPrimaryActions(){
  const hasText=!!(actionEl&&actionEl.value.trim());
  if(submitEl)submitEl.disabled=busy||!hasText;
  if(searchActionButton)searchActionButton.disabled=busy;
  if(exploreActionButton)exploreActionButton.disabled=busy;
}
function appendMacroPending(label){
  if(!logEl)return;
  const player=document.createElement("article");
  player.className="message player pending";player.setAttribute("data-pending","1");
  player.innerHTML="<div class='role'>BẠN</div><div class='text'></div>";
  player.querySelector(".text").textContent=label;logEl.appendChild(player);
  const gm=document.createElement("article");
  gm.className="message pending";gm.setAttribute("data-pending","1");
  gm.innerHTML="<div class='role'>GAME MASTER</div><div class='text'>Đang xử lý lượt…</div>";
  logEl.appendChild(gm);logEl.scrollTop=logEl.scrollHeight;
}
function submitMacroAction(kind,label){
  if(busy)return;
  if(!window.Android||typeof window.Android.submitAction!=="function"){
    statusEl.textContent="Không tìm thấy Android action bridge.";return;
  }
  busy=true;syncPrimaryActions();
  statusEl.textContent=kind==="SEARCH"?"Đang tìm kiếm khu vực hiện tại…":"Đang khám phá khu vực chưa khảo sát…";
  appendMacroPending(label);
  window.Android.submitAction(JSON.stringify(state),kind,label);
}
if(searchActionButton)searchActionButton.addEventListener("click",()=>submitMacroAction("SEARCH","Tìm kiếm"));
if(exploreActionButton)exploreActionButton.addEventListener("click",()=>submitMacroAction("EXPLORE","Khám phá"));
if(actionEl)actionEl.addEventListener("input",syncPrimaryActions);
syncPrimaryActions();
'''
if "STEP2_TYPED_ACTIONS" not in html:
    anchor = "window.backroomTurn="
    pos = html.find(anchor)
    if pos < 0:
        raise RuntimeError("UI backroomTurn anchor missing")
    html = html[:pos] + js + "\n" + html[pos:]

# All primary actions lock during one in-flight turn. Execute remains disabled when input is empty.
html = html.replace("busy=true;submitEl.disabled=true;", "busy=true;syncPrimaryActions();")
html = html.replace("busy=false;submitEl.disabled=false;", "busy=false;syncPrimaryActions();")

for marker in (
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    'class="primary-action execute-action"',
    'Android.submitAction(JSON.stringify(state),"EXECUTE",a)',
    'submitMacroAction("SEARCH","Tìm kiếm")',
    'submitMacroAction("EXPLORE","Khám phá")',
    'submitEl.disabled=busy||!hasText',
    'STEP2_THREE_ACTIONS',
):
    if marker not in html:
        raise RuntimeError(f"three-action UI contract missing: {marker}")

if re.search(r'<button\s+id="submit"[^>]*>\s*THỰC HIỆN\s*</button>', html, re.IGNORECASE):
    raise RuntimeError("legacy single Execute button still present")

INDEX.write_text(html, encoding="utf-8")
print("Step 2 three-button WebView UI applied.")
