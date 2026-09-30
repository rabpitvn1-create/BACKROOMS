from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

choice_row = '''<div class="primary-action-row" id="primaryActionRow">
<button type="button" class="primary-action" id="searchActionButton" aria-label="Tìm kiếm">
<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="5.5"></circle><path d="M14.7 14.7 20 20"></path></svg><span>Tìm kiếm</span>
</button>
<button type="button" class="primary-action combat-middle-action" id="combatMiddleButton" hidden aria-label="Hành động chiến đấu"></button>
<button type="button" class="primary-action" id="exploreActionButton" aria-label="Khám phá">
<svg class="action-icon footprint-icon" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="8" cy="8" rx="3" ry="4.2" transform="rotate(-20 8 8)"></ellipse><ellipse cx="15.8" cy="15.5" rx="3" ry="4.2" transform="rotate(18 15.8 15.5)"></ellipse><circle cx="5.2" cy="3.4" r="1"></circle><circle cx="18.5" cy="10.3" r="1"></circle></svg><span>Khám phá</span>
</button>
</div>'''

if 'id="searchActionButton"' not in html:
    anchor = '<div class="player-action-bar"><button id="playerActionOpen" type="button">PLAYER ACTION</button></div>'
    if anchor not in html:
        raise RuntimeError("V2 player action bar anchor missing")
    html = html.replace(anchor, choice_row + "\n" + anchor, 1)

css = r'''
/* STEP2_V2_ACTION_LAYOUT */
.primary-action-row{display:grid;grid-template-columns:1fr 1fr;gap:7px;width:100%;margin-top:12px}
.primary-action{min-width:0;min-height:46px;border-radius:8px;display:flex;align-items:center;justify-content:flex-start;gap:9px;padding:11px 12px;white-space:normal;text-align:left;background:#171d22;border:1px solid #39424a;color:#f0f3f5;font-weight:700}
.primary-action:active:not(:disabled){background:#20272d;border-color:#7a858e}
.primary-action .action-icon{width:19px;height:19px;flex:0 0 19px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.primary-action .footprint-icon ellipse,.primary-action .footprint-icon circle{fill:currentColor;stroke:none}
#combatMiddleButton[hidden]{display:none}
.primary-action-row.combat-actions{grid-template-columns:1fr 1fr 1fr}
.primary-action-row.combat-actions #combatMiddleButton{display:flex}
@media(max-width:390px){.primary-action-row{gap:5px}.primary-action{font-size:12px;padding:10px 9px;gap:6px}.primary-action .action-icon{width:17px;height:17px;flex-basis:17px}}
'''
if "STEP2_V2_ACTION_LAYOUT" not in html:
    if "</style>" not in html:
        raise RuntimeError("UI style closing tag missing")
    html = html.replace("</style>", css + "\n</style>", 1)

# PLAYER ACTION remains the free-form form, but it enters the same typed ActionRuntime pipeline.
submit_pattern = re.compile(r'(?:window\.)?Android\.submitTurn\(JSON\.stringify\(state\),a\)')
if 'Android.submitAction(JSON.stringify(state),"EXECUTE",a)' not in html:
    html, count = submit_pattern.subn('window.Android.submitAction(JSON.stringify(state),"EXECUTE",a)', html, count=1)
    if count != 1:
        raise RuntimeError(f"UI submitTurn call expected 1 match, found {count}")

js = r'''
// STEP2_TYPED_ACTIONS
const primaryActionRow=byId("primaryActionRow"),searchActionButton=byId("searchActionButton"),combatMiddleButton=byId("combatMiddleButton"),exploreActionButton=byId("exploreActionButton");
function mountPrimaryActions(){
  if(!primaryActionRow||!logEl)return;
  const messages=logEl.querySelectorAll(".message:not(.player)");
  const latest=messages.length?messages[messages.length-1]:null;
  if(latest&&primaryActionRow.parentNode!==latest)latest.appendChild(primaryActionRow);
}
function syncPrimaryActions(){
  const hasText=!!(actionEl&&actionEl.value.trim());
  if(submitEl)submitEl.disabled=busy||!hasText;
  if(searchActionButton)searchActionButton.disabled=busy;
  if(exploreActionButton)exploreActionButton.disabled=busy;
  if(playerActionOpen)playerActionOpen.disabled=busy||!!(state&&state.combat&&state.combat.active);
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
const step2BaseRender=window.render;
if(typeof step2BaseRender==="function")window.render=function(){const value=step2BaseRender.apply(this,arguments);mountPrimaryActions();syncPrimaryActions();return value};
const step2BaseTurn=window.backroomTurn;
if(typeof step2BaseTurn==="function")window.backroomTurn=function(json){const value=step2BaseTurn.call(this,json);mountPrimaryActions();syncPrimaryActions();return value};
mountPrimaryActions();syncPrimaryActions();
'''
if "STEP2_TYPED_ACTIONS" not in html:
    anchor = "\nrender();\n</script>"
    if anchor not in html:
        raise RuntimeError("UI final render anchor missing")
    html = html.replace(anchor, "\n" + js + "\nrender();\n</script>", 1)

# All three functional actions lock during one in-flight turn. PLAYER ACTION submit still
# requires text, while SEARCH/EXPLORE are always available outside combat.
html = html.replace("busy=true;submitEl.disabled=true;", "busy=true;syncPrimaryActions();")
html = html.replace("busy=false;submitEl.disabled=false;", "busy=false;syncPrimaryActions();")

for marker in (
    'id="searchActionButton"',
    'id="combatMiddleButton"',
    'id="exploreActionButton"',
    'id="playerActionOpen"',
    'id="playerActionModal"',
    'Android.submitAction(JSON.stringify(state),"EXECUTE",a)',
    'submitMacroAction("SEARCH","Tìm kiếm")',
    'submitMacroAction("EXPLORE","Khám phá")',
    'submitEl.disabled=busy||!hasText',
    'mountPrimaryActions()',
    'STEP2_V2_ACTION_LAYOUT',
):
    if marker not in html:
        raise RuntimeError(f"V2 action UI contract missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("V2 action layout applied: Search / Explore live in the latest GM choice area; PLAYER ACTION stays as the separate free-form control.")
