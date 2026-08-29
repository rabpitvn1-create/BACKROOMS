from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
html = INDEX.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")

# This patch intentionally runs after the main WebView UI stack. It does not duplicate game state
# or move gameplay controls into a second runtime. The existing .game and .side views remain the
# same DOM nodes and therefore keep one shared authoritative state.
required_before = (
    '<main class="shell">',
    '<section class="game">',
    '<aside class="side">',
    'id="searchActionButton"',
    'id="submit"',
    'id="exploreActionButton"',
    'id="saveButton"',
    'id="loadButton"',
    'id="partyTime"',
    'COMBAT_ACTION_BAR_V2',
    "function combatActive(){return !!(typeof state!=='undefined'&&state&&state.combat&&state.combat.active===true);}",
)
for marker in required_before:
    if marker not in html:
        raise RuntimeError("Two-page UI must run after the final runtime/UI patch chain: " + marker)

# Keep the WebView at one CSS scale. Android display-size / resolution changes still resize the
# device viewport and are handled responsively below, while browser pinch/double-tap zoom can no
# longer leave the fixed two-page shell rendered at a partial or stale scale.
VIEWPORT = '<meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover,interactive-widget=resizes-content">'
if VIEWPORT not in html:
    html, count = re.subn(r'<meta name="viewport" content="[^"]*">', VIEWPORT, html, count=1)
    if count != 1:
        raise RuntimeError("Responsive viewport meta anchor missing")

native_zoom_policy = '''    settings.setSupportZoom(false);\n    settings.setBuiltInZoomControls(false);\n    settings.setDisplayZoomControls(false);\n    settings.setUseWideViewPort(true);\n    settings.setLoadWithOverviewMode(false);\n'''
if "settings.setSupportZoom(false);" not in main:
    anchor = "    settings.setAllowFileAccess(true);\n"
    if main.count(anchor) != 1:
        raise RuntimeError("WebView settings anchor missing for responsive display policy")
    main = main.replace(anchor, anchor + native_zoom_policy, 1)

CSS = r'''
/* TWO_PAGE_SWIPE_UI_V1 */
/* RESPONSIVE_DISPLAY_V1 */
html,body{width:100%;max-width:100%;min-width:0;min-height:100%;overflow-x:hidden;-webkit-text-size-adjust:100%;text-size-adjust:100%}
body.two-page-body{overflow:hidden}
.shell.two-page-shell{--backroom-vh:100dvh;position:relative;width:100%;max-width:100%;height:var(--backroom-vh);min-height:var(--backroom-vh);overflow:hidden;padding:10px 10px calc(34px + env(safe-area-inset-bottom))}
.shell.two-page-shell>.game,.shell.two-page-shell>.side{position:absolute;top:10px;bottom:calc(34px + env(safe-area-inset-bottom));width:calc(100% - 20px);max-width:calc(100% - 20px);min-width:0;margin:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior-y:contain;-webkit-overflow-scrolling:touch;transition:left .22s ease;will-change:left}
.shell.two-page-shell[data-page="game"]>.game{left:10px}
.shell.two-page-shell[data-page="game"]>.side{left:calc(100% + 10px)}
.shell.two-page-shell[data-page="status"]>.game{left:calc(-100% - 10px)}
.shell.two-page-shell[data-page="status"]>.side{left:10px}
.shell.two-page-shell>.side{display:grid;align-content:start;gap:10px}
.shell.two-page-shell *, .character-inventory-view *, .equipment-detail-modal *, .character-skills-modal *{min-width:0}
img,video,canvas{max-width:100%}
textarea,input,select,button{max-width:100%}
textarea{width:100%}
.topbar>div:first-child,.row>span,.equipment-card-main,.character-profile>div{min-width:0;overflow-wrap:anywhere}
.row{grid-template-columns:minmax(72px,90px) minmax(0,1fr)}
.primary-action-row{grid-template-columns:repeat(3,minmax(0,1fr))}
.primary-action{min-width:0;overflow:hidden}
.primary-action span{min-width:0;overflow:hidden;text-overflow:ellipsis}
.character-profile{grid-template-columns:minmax(84px,110px) minmax(0,1fr)}
.character-inventory-view,.character-skills-modal,.equipment-detail-modal{width:100%;max-width:100%;height:var(--backroom-vh,100dvh);max-height:var(--backroom-vh,100dvh)}
.equipment-detail-sheet,.character-skills-sheet{width:min(720px,100%);max-width:100%}
.swipe-page-indicator{position:absolute;z-index:40;left:50%;bottom:calc(9px + env(safe-area-inset-bottom));transform:translateX(-50%);display:flex;align-items:center;gap:9px;padding:3px 8px;border-radius:999px;background:#080a0ccc;backdrop-filter:blur(4px)}
.swipe-page-dot{width:8px;height:8px;min-width:8px;min-height:8px;padding:0;border:1px solid #626d76;border-radius:50%;background:#20262b;box-shadow:none}
.swipe-page-dot[aria-current="true"]{background:#eef1f3;border-color:#eef1f3}
@media(max-width:360px){
  .shell.two-page-shell{padding:6px 6px calc(30px + env(safe-area-inset-bottom))}
  .shell.two-page-shell>.game,.shell.two-page-shell>.side{top:6px;bottom:calc(30px + env(safe-area-inset-bottom));width:calc(100% - 12px);max-width:calc(100% - 12px)}
  .shell.two-page-shell[data-page="game"]>.game{left:6px}.shell.two-page-shell[data-page="game"]>.side{left:calc(100% + 6px)}
  .shell.two-page-shell[data-page="status"]>.game{left:calc(-100% - 6px)}.shell.two-page-shell[data-page="status"]>.side{left:6px}
  .topbar{padding:10px;gap:6px}.topbar h1{font-size:clamp(17px,6vw,21px)}.turn{font-size:10px}.turn strong{font-size:18px}
  .snapshot{height:clamp(120px,25dvh,160px);margin:6px}.log{height:clamp(170px,40dvh,300px);padding:7px}.composer{padding:7px}
  .row{grid-template-columns:minmax(62px,28%) minmax(0,1fr)}
  .character-profile{grid-template-columns:84px minmax(0,1fr);gap:10px}.character-profile img{width:84px;height:84px}
  .character-hp-value{font-size:16px}.character-hp-heart{font-size:22px}.inventory-capacity{font-size:15px}
  .primary-action-row{gap:4px}.primary-action{font-size:10px;padding:8px 3px;gap:3px}.primary-action .action-icon{width:16px;height:16px;flex-basis:16px}.primary-action .icon-execute{width:17px;flex-basis:17px}
  .party-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
  .character-inventory-view,.character-skills-modal{padding:8px}.character-section{padding:10px}.equipment-detail-sheet,.character-skills-sheet{padding:10px}
}
@media(max-width:280px){
  .topbar{display:grid;grid-template-columns:minmax(0,1fr) auto}.eyebrow{font-size:8px}.topbar h1{font-size:16px}
  .character-profile{grid-template-columns:1fr}.character-profile img{width:78px;height:78px}
  .primary-action span{font-size:9px}.row{grid-template-columns:1fr}.row b{padding-bottom:2px}
}
@media(orientation:landscape) and (max-height:520px){
  .shell.two-page-shell{padding-top:6px}.shell.two-page-shell>.game,.shell.two-page-shell>.side{top:6px}
  .snapshot{height:clamp(96px,26dvh,140px)}.log{height:clamp(130px,34dvh,210px)}textarea{min-height:56px}
  .character-inventory-view,.character-skills-modal{padding-top:8px}
}
@media(prefers-reduced-motion:reduce){.shell.two-page-shell>.game,.shell.two-page-shell>.side{transition:none}}
'''

JS = r'''
<script>
// TWO_PAGE_SWIPE_RUNTIME_V1
(function(){
  const shell=document.querySelector('main.shell');
  if(!shell||shell.dataset.twoPageReady==='1')return;
  const children=Array.from(shell.children);
  const game=children.find(el=>el.classList&&el.classList.contains('game'));
  const status=children.find(el=>el.classList&&el.classList.contains('side'));
  if(!game||!status)return;

  document.body.classList.add('two-page-body');
  shell.classList.add('two-page-shell');
  shell.dataset.twoPageReady='1';
  game.classList.add('swipe-game-page');
  status.classList.add('swipe-status-page');

  // visualViewport follows Android resolution/display-size changes while the page zoom itself is
  // locked at 1x. Feeding its current height into CSS avoids stale 100vh sizing after rotation,
  // display-size changes, system bars or the on-screen keyboard resize the WebView.
  function syncViewport(){
    const viewport=window.visualViewport;
    const height=Math.max(1,Math.round(viewport?viewport.height:window.innerHeight));
    shell.style.setProperty('--backroom-vh',height+'px');
  }
  syncViewport();
  window.addEventListener('resize',syncViewport,{passive:true});
  window.addEventListener('orientationchange',syncViewport,{passive:true});
  if(window.visualViewport)window.visualViewport.addEventListener('resize',syncViewport,{passive:true});

  // Management is checked more often than destructive save actions, so Party sits directly
  // below Status while Save / Load remains available lower on the same page.
  const statusCards=Array.from(status.children).filter(el=>el.classList&&el.classList.contains('card'));
  const cardByTitle=title=>statusCards.find(card=>{
    const heading=card.querySelector('h2');
    return heading&&heading.textContent.trim()===title;
  });
  const partyCard=cardByTitle('Party');
  const saveCard=cardByTitle('Save / Load');
  if(partyCard&&saveCard)status.insertBefore(partyCard,saveCard);

  const indicator=document.createElement('nav');
  indicator.className='swipe-page-indicator';
  indicator.setAttribute('aria-label','Chuyển trang');
  indicator.innerHTML='<button type="button" class="swipe-page-dot" data-page="game" aria-label="Game"></button><button type="button" class="swipe-page-dot" data-page="status" aria-label="Status"></button>';
  shell.appendChild(indicator);
  const dots=Array.from(indicator.querySelectorAll('.swipe-page-dot'));

  function setPage(page){
    const next=page==='status'?'status':'game';
    shell.dataset.page=next;
    game.setAttribute('aria-hidden',next==='game'?'false':'true');
    status.setAttribute('aria-hidden',next==='status'?'false':'true');
    dots.forEach(dot=>dot.setAttribute('aria-current',dot.dataset.page===next?'true':'false'));
  }
  dots.forEach(dot=>dot.addEventListener('click',()=>setPage(dot.dataset.page)));

  const blockedSelector='textarea,input,select,button,a,[contenteditable="true"],.party-member,#characterInventoryView,#equipmentDetailModal,#characterSkillsModal';
  const SWIPE_DISTANCE=56;
  let startX=0,startY=0,tracking=false;
  function blocked(target){return !!(target&&target.closest&&target.closest(blockedSelector));}
  shell.addEventListener('touchstart',event=>{
    if(event.touches.length!==1||blocked(event.target)){tracking=false;return;}
    const touch=event.touches[0];startX=touch.clientX;startY=touch.clientY;tracking=true;
  },{passive:true});
  shell.addEventListener('touchend',event=>{
    if(!tracking||!event.changedTouches.length){tracking=false;return;}
    tracking=false;
    const touch=event.changedTouches[0];
    const dx=touch.clientX-startX,dy=touch.clientY-startY;
    if(Math.abs(dx)<SWIPE_DISTANCE||Math.abs(dx)<=Math.abs(dy)*1.2)return;
    setPage(dx<0?'status':'game');
  },{passive:true});
  shell.addEventListener('touchcancel',()=>{tracking=false;},{passive:true});

  // Exposed only as a UI control hook; gameplay state continues to live in the existing `state`.
  window.backroomPagePager={setPage,getPage:()=>shell.dataset.page};
  setPage('game');
})();
</script>
'''

if "TWO_PAGE_SWIPE_UI_V1" not in html:
    if "</style>" not in html:
        raise RuntimeError("Two-page UI style anchor missing")
    html = html.replace("</style>", CSS + "\n</style>", 1)

if "TWO_PAGE_SWIPE_RUNTIME_V1" not in html:
    if "</body>" not in html:
        raise RuntimeError("Two-page UI body anchor missing")
    html = html.replace("</body>", JS + "\n</body>", 1)

for marker in (
    "TWO_PAGE_SWIPE_UI_V1",
    "RESPONSIVE_DISPLAY_V1",
    "TWO_PAGE_SWIPE_RUNTIME_V1",
    "user-scalable=no",
    "interactive-widget=resizes-content",
    "shell.dataset.twoPageReady='1'",
    "window.visualViewport",
    "--backroom-vh",
    "@media(max-width:360px)",
    "status.insertBefore(partyCard,saveCard)",
    "const SWIPE_DISTANCE=56",
    "Math.abs(dx)<=Math.abs(dy)*1.2",
    "textarea,input,select,button,a",
    "window.backroomPagePager={setPage,getPage:()=>shell.dataset.page}",
    ".shell.two-page-shell[data-page=\"game\"]>.side",
    ".shell.two-page-shell[data-page=\"status\"]>.game",
):
    if marker not in html:
        raise RuntimeError("Two-page responsive contract missing: " + marker)

for marker in (
    "settings.setSupportZoom(false);",
    "settings.setBuiltInZoomControls(false);",
    "settings.setDisplayZoomControls(false);",
    "settings.setUseWideViewPort(true);",
    "settings.setLoadWithOverviewMode(false);",
):
    if marker not in main:
        raise RuntimeError("WebView responsive contract missing: " + marker)

if html.count("TWO_PAGE_SWIPE_UI_V1") != 1 or html.count("TWO_PAGE_SWIPE_RUNTIME_V1") != 1 or html.count("RESPONSIVE_DISPLAY_V1") != 1:
    raise RuntimeError("Two-page responsive UI must be installed exactly once")

INDEX.write_text(html, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Responsive two-page UI applied: fixed 1x WebView scale, dynamic viewport height, narrow-screen reflow and rotation-safe sizing.")
