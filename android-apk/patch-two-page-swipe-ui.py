from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

# This patch intentionally runs at the very end of the WebView patch chain. It does not
# duplicate game state or move gameplay controls into a second runtime. The existing .game
# and .side views remain the same DOM nodes and therefore keep one shared authoritative state.
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

CSS = r'''
/* TWO_PAGE_SWIPE_UI_V1 */
body.two-page-body{overflow:hidden}
.shell.two-page-shell{position:relative;height:100vh;height:100dvh;min-height:100vh;min-height:100dvh;overflow:hidden;padding:10px 10px calc(34px + env(safe-area-inset-bottom))}
.shell.two-page-shell>.game,.shell.two-page-shell>.side{position:absolute;top:10px;bottom:calc(34px + env(safe-area-inset-bottom));width:calc(100% - 20px);margin:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior-y:contain;-webkit-overflow-scrolling:touch;transition:left .22s ease;will-change:left}
.shell.two-page-shell[data-page="game"]>.game{left:10px}
.shell.two-page-shell[data-page="game"]>.side{left:calc(100% + 10px)}
.shell.two-page-shell[data-page="status"]>.game{left:calc(-100% - 10px)}
.shell.two-page-shell[data-page="status"]>.side{left:10px}
.shell.two-page-shell>.side{display:grid;align-content:start;gap:10px}
.swipe-page-indicator{position:absolute;z-index:40;left:50%;bottom:calc(9px + env(safe-area-inset-bottom));transform:translateX(-50%);display:flex;align-items:center;gap:9px;padding:3px 8px;border-radius:999px;background:#080a0ccc;backdrop-filter:blur(4px)}
.swipe-page-dot{width:8px;height:8px;min-width:8px;min-height:8px;padding:0;border:1px solid #626d76;border-radius:50%;background:#20262b;box-shadow:none}
.swipe-page-dot[aria-current="true"]{background:#eef1f3;border-color:#eef1f3}
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
    "TWO_PAGE_SWIPE_RUNTIME_V1",
    "shell.dataset.twoPageReady='1'",
    "status.insertBefore(partyCard,saveCard)",
    "const SWIPE_DISTANCE=56",
    "Math.abs(dx)<=Math.abs(dy)*1.2",
    "textarea,input,select,button,a",
    "window.backroomPagePager={setPage,getPage:()=>shell.dataset.page}",
    ".shell.two-page-shell[data-page=\"game\"]>.side",
    ".shell.two-page-shell[data-page=\"status\"]>.game",
):
    if marker not in html:
        raise RuntimeError("Two-page swipe contract missing: " + marker)

if html.count("TWO_PAGE_SWIPE_UI_V1") != 1 or html.count("TWO_PAGE_SWIPE_RUNTIME_V1") != 1:
    raise RuntimeError("Two-page swipe UI must be installed exactly once")

INDEX.write_text(html, encoding="utf-8")
print("Two-page swipe UI applied: GAME and STATUS keep independent vertical scroll positions over one shared game state.")
