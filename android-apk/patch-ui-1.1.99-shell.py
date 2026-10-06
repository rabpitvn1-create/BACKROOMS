from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

MARKER = "UI_119_BACKPORT_R01"
if MARKER in html:
    raise RuntimeError("1.1.99 UI shell already applied")

# The three current actions stay authoritative. Only their presentation is moved into
# the 1.1.99-style bottom Action frame; free-form input lives in a modal opened by Execute.
row_pattern = re.compile(
    r'<div class="primary-action-row" id="primaryActionRow">.*?</div>',
    re.DOTALL,
)
row_match = row_pattern.search(html)
if not row_match:
    raise RuntimeError("primaryActionRow not found")
action_row = row_match.group(0)
html = html[:row_match.start()] + html[row_match.end():]
action_row = re.sub(
    r'(<button\s+)([^>]*\bid="submit"[^>]*)',
    lambda m: m.group(1) + re.sub(r'\btype="[^"]*"', 'type="button"', m.group(2)) if 'type="' in m.group(2)
    else m.group(1) + 'type="button" ' + m.group(2),
    action_row,
    count=1,
)

form_pattern = re.compile(r'<form class="composer" id="form">(.*?)</form>', re.DOTALL)
form_match = form_pattern.search(html)
if not form_match:
    raise RuntimeError("composer form not found after action row extraction")
form_inner = form_match.group(1).strip()
if 'id="action"' not in form_inner:
    raise RuntimeError("action textarea missing")

action_shell = f'''<div class="player-action-bar" id="playerActionBar" aria-label="Action">
{action_row}
</div>
<div class="player-action-modal" id="playerActionModal" hidden aria-hidden="true">
  <div class="player-action-backdrop" id="playerActionBackdrop"></div>
  <section class="player-action-sheet" role="dialog" aria-modal="true" aria-labelledby="playerActionTitle">
    <div class="player-action-head"><h2 id="playerActionTitle">PLAYER ACTION</h2><button class="player-action-close" id="playerActionClose" type="button" aria-label="Đóng">×</button></div>
    <form class="composer" id="form">
      <label for="action">Kai sẽ làm gì?</label>
      {form_inner}
      <div class="composer-actions"><button id="playerActionCancel" type="button">HỦY</button><button id="playerActionConfirm" type="submit">THỰC HIỆN</button></div>
    </form>
  </section>
</div>'''
html = html[:form_match.start()] + action_shell + html[form_match.end():]

header_pattern = re.compile(r'<header class="topbar">.*?</header>', re.DOTALL)
header_match = header_pattern.search(html)
if not header_match:
    raise RuntimeError("topbar not found")
header = '''<header class="topbar"><div><div class="eyebrow">BACKROOM TEXT GAME</div><h1 id="title"></h1><span class="turn-runtime">TURN <strong id="turn"></strong></span></div><button class="game-menu-trigger" id="gameMenuOpen" type="button" aria-label="Mở menu game" aria-haspopup="dialog" aria-controls="gameMenuModal" aria-expanded="false">☰</button></header>'''
html = html[:header_match.start()] + header + html[header_match.end():]

side_pattern = re.compile(r'<aside class="side">.*?</aside>', re.DOTALL)
side_match = side_pattern.search(html)
if not side_match:
    raise RuntimeError("side panel not found")
side = side_match.group(0)
menu = f'''<div class="game-menu-modal" id="gameMenuModal" hidden aria-hidden="true">
  <div class="game-menu-backdrop" id="gameMenuBackdrop"></div>
  <section class="game-menu-sheet" role="dialog" aria-modal="true" aria-labelledby="gameMenuTitle">
    <div class="game-menu-head"><h2 id="gameMenuTitle">GAME MENU</h2><button class="game-menu-close" id="gameMenuClose" type="button" aria-label="Đóng">×</button></div>
    {side}
  </section>
</div>'''
html = html[:side_match.start()] + menu + html[side_match.end():]

css = r'''
/* UI_119_BACKPORT_R01 */
html,body{height:100%;overflow:hidden;overscroll-behavior:none}
body{margin:0;background:#080a0c;color:#eef1f3}
.shell{height:var(--app-height,100dvh);min-height:0;overflow:hidden;padding:0;background:radial-gradient(circle at top,#161b20,#07090b 75%)}
.game{height:100%;min-height:0;display:grid;grid-template-rows:auto auto minmax(0,1fr) auto;overflow:hidden;border:0!important;border-radius:0!important;box-shadow:none!important}
.topbar{display:flex;justify-content:space-between;align-items:flex-start;gap:8px;padding:10px max(14px,env(safe-area-inset-right)) 8px max(14px,env(safe-area-inset-left));border-bottom:1px solid #2b3137;position:relative;isolation:isolate;overflow:hidden;background-color:#0e1114;background-image:linear-gradient(90deg,rgba(5,7,8,.92) 0%,rgba(6,8,9,.72) 52%,rgba(5,6,7,.88) 100%),url('level_snapshots/level_0.webp');background-size:cover;background-position:center 46%;background-repeat:no-repeat}
.topbar>div,.topbar>.game-menu-trigger{position:relative;z-index:1}
.topbar .eyebrow,.topbar h1{text-shadow:0 1px 2px #000,0 0 8px #000c}
.topbar .eyebrow{color:#d6d0aa;font-size:9px}
.topbar h1{margin:2px 0 0;font-size:18px;line-height:1.15}
.turn-runtime{display:none}
.game-menu-trigger{width:40px;height:40px;flex:0 0 40px;padding:0;display:grid;place-items:center;border:1px solid #3a4249;background:rgba(13,16,18,.82);color:#eef1f3;font-size:19px;line-height:1;touch-action:manipulation;box-shadow:0 0 0 1px #0007,0 3px 12px #0006}
.game-menu-trigger:active{transform:scale(.96);background:#252c32}
.game-menu-trigger[aria-expanded="true"]{border-color:#707b84;background:#20272d}
.snapshot{border-radius:7px!important}
.log{height:auto!important;min-height:0;overflow:auto;overscroll-behavior:contain;-webkit-overflow-scrolling:touch;padding:10px;display:grid;gap:10px;align-content:start}
.message,.message.player,.message.gm{border-radius:0!important}
.player-action-bar{padding:8px calc(10px + env(safe-area-inset-right)) calc(10px + env(safe-area-inset-bottom)) calc(10px + env(safe-area-inset-left));border-top:1px solid #252b31;background-color:#171812;background-image:linear-gradient(90deg,rgba(8,9,7,.90) 0%,rgba(12,12,8,.62) 50%,rgba(8,9,7,.88) 100%),url('hud/player_action_backrooms.png');background-size:cover;background-position:center 52%;background-repeat:no-repeat;box-shadow:inset 0 0 22px rgba(162,145,62,.08)}
.player-action-bar .primary-action-row{grid-template-columns:1fr 1.12fr 1fr;gap:7px}
.player-action-bar .primary-action{min-height:52px;background:rgba(20,23,20,.88);border-color:#5d593f;color:#f2efd8;text-shadow:0 1px 2px #000}
.player-action-bar .primary-action.execute-action{background:rgba(32,39,45,.9);border-color:#766e49;color:#fff7cf}
.player-action-modal[hidden],.game-menu-modal[hidden]{display:none}
.player-action-modal,.game-menu-modal{position:fixed;inset:0;display:flex;align-items:flex-end;justify-content:center}
.game-menu-modal{z-index:110}.player-action-modal{z-index:120}
.player-action-backdrop,.game-menu-backdrop{position:absolute;inset:0;background:#000b}
.player-action-sheet,.game-menu-sheet{position:relative;width:min(100%,680px);max-height:min(calc(var(--app-height,100dvh) - 12px),760px);overflow:auto;overscroll-behavior:contain;background:#0e1114;border:1px solid #3a4249;border-bottom:0;padding:14px 14px calc(14px + env(safe-area-inset-bottom));box-shadow:0 -24px 60px #000b}
.player-action-sheet,.game-menu-sheet{border-radius:7px 7px 0 0!important}
.player-action-head,.game-menu-head{display:flex;align-items:center;justify-content:space-between;gap:10px}
.player-action-head{margin-bottom:12px}
.player-action-head h2,.game-menu-head h2{margin:0;font-size:13px;font-weight:800;letter-spacing:.1em}
.game-menu-head{position:sticky;top:-14px;z-index:2;margin:-14px -14px 10px;padding:14px;background:#0e1114;border-bottom:1px solid #252b31}
.player-action-close{width:auto;min-width:42px;padding:8px 10px}
.game-menu-close{width:44px;height:44px;min-width:44px;padding:0;font-size:22px;line-height:1}
.player-action-sheet .composer{display:grid;gap:10px;padding:0}
.player-action-sheet .composer label{font-weight:700;font-size:13px}
.player-action-sheet textarea{min-height:150px;max-height:calc(var(--app-height,100dvh) - 180px);resize:vertical;background:#090c0f;color:#fff;border:1px solid #30373e;padding:12px;line-height:1.45}
.composer-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.status{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
.game-menu-sheet .side{display:grid;gap:10px;margin-top:0}
.game-menu-sheet .card{box-shadow:none}
body.player-action-open,body.game-menu-open{overflow:hidden}
.card,.game-menu-trigger,.player-action-close,.game-menu-close,.primary-action,textarea,button,.chips span,.gm-choice,.combat-dice-panel,.combat-target,.combat-die,.combat-die-unknown{border-radius:7px!important}
'''
if "</style>" not in html:
    raise RuntimeError("style closing tag missing")
html = html.replace("</style>", css + "\n</style>", 1)

js = r'''
<script>
(function(){
  "use strict";
  if(window.__ui119BackportInstalled)return;
  window.__ui119BackportInstalled=true;

  var actionModal=document.getElementById("playerActionModal");
  var actionBackdrop=document.getElementById("playerActionBackdrop");
  var actionClose=document.getElementById("playerActionClose");
  var actionCancel=document.getElementById("playerActionCancel");
  var actionConfirm=document.getElementById("playerActionConfirm");
  var execute=document.getElementById("submit");
  var form=document.getElementById("form");
  var action=document.getElementById("action");
  var menu=document.getElementById("gameMenuModal");
  var menuOpen=document.getElementById("gameMenuOpen");
  var menuClose=document.getElementById("gameMenuClose");
  var menuBackdrop=document.getElementById("gameMenuBackdrop");
  var imeInsetCss=0,modalBaselineHeight=0;

  function combatActive(){try{return !!(state&&state.combat&&state.combat.active===true)}catch(_){return false}}
  function processing(){return !!window.__combatBusy||(typeof busy!=="undefined"&&!!busy)}
  function layoutHeight(){return Math.max(1,window.innerHeight||document.documentElement.clientHeight||1)}
  function fitActionModal(){
    if(!actionModal||actionModal.hidden)return;
    var vv=window.visualViewport,layout=layoutHeight(),visual=Math.max(1,vv&&vv.height?vv.height:layout),top=Math.max(0,vv&&vv.offsetTop?vv.offsetTop:0);
    if(imeInsetCss<=0)modalBaselineHeight=Math.max(modalBaselineHeight,layout,visual);
    var baseline=Math.max(modalBaselineHeight,layout,visual),usable=imeInsetCss>0?Math.min(visual,Math.max(1,baseline-imeInsetCss)):visual;
    actionModal.style.top=top+"px";actionModal.style.height=Math.max(1,usable)+"px";
  }
  function syncActionConfirm(){
    if(actionConfirm)actionConfirm.disabled=processing()||!String(action&&action.value||"").trim();
  }
  function closeAction(keepDraft){
    if(!actionModal||actionModal.hidden)return;
    actionModal.hidden=true;actionModal.setAttribute("aria-hidden","true");document.body.classList.remove("player-action-open");
    actionModal.style.removeProperty("top");actionModal.style.removeProperty("height");
    try{action&&action.blur()}catch(_){}
    if(!keepDraft&&action)action.value="";
    syncActionConfirm();
  }
  function openAction(){
    if(!actionModal||processing()||combatActive())return;
    actionModal.hidden=false;actionModal.setAttribute("aria-hidden","false");document.body.classList.add("player-action-open");
    fitActionModal();syncActionConfirm();
    setTimeout(function(){try{action.focus({preventScroll:true});var n=action.value.length;action.setSelectionRange(n,n)}catch(_){try{action.focus()}catch(__){}}},40);
  }
  function openMenu(){
    if(!menu||!menu.hidden)return;
    menu.hidden=false;menu.setAttribute("aria-hidden","false");if(menuOpen)menuOpen.setAttribute("aria-expanded","true");document.body.classList.add("game-menu-open");
  }
  function closeMenu(){
    if(!menu||menu.hidden)return;
    menu.hidden=true;menu.setAttribute("aria-hidden","true");if(menuOpen)menuOpen.setAttribute("aria-expanded","false");document.body.classList.remove("game-menu-open");
  }

  if(typeof syncPrimaryActions==="function"){
    syncPrimaryActions=function(){
      if(execute)execute.disabled=processing();
      var s=document.getElementById("searchActionButton"),e=document.getElementById("exploreActionButton");
      if(s)s.disabled=processing();if(e)e.disabled=processing();syncActionConfirm();
    };
  }

  if(execute)execute.addEventListener("click",function(event){
    if(combatActive())return;
    event.preventDefault();event.stopPropagation();openAction();
  });
  if(action)action.addEventListener("input",syncActionConfirm);
  if(form)form.addEventListener("submit",function(){
    if(combatActive())return;
    if(String(action&&action.value||"").trim())closeAction(true);
  });
  if(actionClose)actionClose.addEventListener("click",function(){if(!processing())closeAction(true)});
  if(actionCancel)actionCancel.addEventListener("click",function(){if(!processing())closeAction(true)});
  if(actionBackdrop)actionBackdrop.addEventListener("click",function(){if(!processing())closeAction(true)});
  if(menuOpen)menuOpen.addEventListener("click",openMenu);
  if(menuClose)menuClose.addEventListener("click",closeMenu);
  if(menuBackdrop)menuBackdrop.addEventListener("click",closeMenu);
  document.addEventListener("keydown",function(event){
    if(event.key!=="Escape")return;
    if(actionModal&&!actionModal.hidden&&!processing()){event.preventDefault();closeAction(true);return}
    if(menu&&!menu.hidden){event.preventDefault();closeMenu()}
  });
  if(window.visualViewport){
    window.visualViewport.addEventListener("resize",fitActionModal);
    window.visualViewport.addEventListener("scroll",fitActionModal);
  }
  window.addEventListener("resize",fitActionModal);
  window.backroomSetImeInset=function(physicalPixels){
    var pixels=Math.max(0,Number(physicalPixels)||0),ratio=Math.max(1,Number(window.devicePixelRatio)||1);
    imeInsetCss=pixels/ratio;if(imeInsetCss<=0)modalBaselineHeight=0;fitActionModal();
  };

  var previousTurn=window.backroomTurn;
  window.backroomTurn=function(json){
    var result=typeof previousTurn==="function"?previousTurn(json):undefined;
    closeAction(false);syncActionConfirm();return result;
  };
  var previousError=window.backroomError;
  window.backroomError=function(message){
    var result=typeof previousError==="function"?previousError(message):undefined;
    syncActionConfirm();return result;
  };

  if(execute)execute.type="button";
  syncActionConfirm();
  if(typeof syncPrimaryActions==="function")syncPrimaryActions();
})();
</script>
'''
if "</body>" not in html:
    raise RuntimeError("body closing tag missing")
html = html.replace("</body>", js + "\n</body>", 1)

for marker in [
    MARKER,
    'id="playerActionBar"',
    'id="playerActionModal"',
    'id="playerActionConfirm"',
    'id="gameMenuModal"',
    'id="gameMenuOpen"',
    'id="primaryActionRow"',
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    "border-radius:7px",
    "hud/player_action_backrooms.png",
    "window.__ui119BackportInstalled",
]:
    if marker not in html:
        raise RuntimeError(f"1.1.99 UI backport marker missing: {marker}")

# The free-form textarea must not live in the always-visible bottom action frame.
bar_start = html.index('id="playerActionBar"')
modal_start = html.index('id="playerActionModal"')
action_pos = html.index('id="action"')
if not (bar_start < modal_start < action_pos):
    raise RuntimeError("Action textarea must live inside the Execute popup")
if html.count('id="primaryActionRow"') != 1:
    raise RuntimeError("primary action row duplicated")
if html.count('id="form"') != 1:
    raise RuntimeError("composer form duplicated")

INDEX.write_text(html, encoding="utf-8")
print("1.1.99 UI shell backported: three-button Action bar, Execute popup and 7px corner radius.")
