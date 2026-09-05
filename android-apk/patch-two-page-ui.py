from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

if 'id="gameplayPage"' in html:
    print("Two-page UI already applied.")
    raise SystemExit(0)

# Both actions share the existing Android turn pipeline and in-flight guard.
button_anchor = '<button id="submit">THỰC HIỆN</button>'
if html.count(button_anchor) != 1:
    raise RuntimeError("Action button anchor missing or ambiguous")
html = html.replace(button_anchor, '<div class="composer-actions"><button type="submit" id="submit">THỰC HIỆN</button><button type="button" id="explore">KHÁM PHÁ &amp; TÌM KIẾM</button></div>', 1)

old_submit = 'formEl.addEventListener("submit",e=>{e.preventDefault();const a=actionEl.value.trim();if(!a||busy)return;if(!window.Android){statusEl.textContent="Không tìm thấy Android bridge.";return}busy=true;submitEl.disabled=true;statusEl.textContent="Gemini đang xử lý lượt…";window.Android.submitTurn(JSON.stringify(state),a)});'
new_submit = r'''const exploreEl=byId("explore");
const explorationAction="Kai khám phá khu vực hiện tại và tìm kiếm manh mối, vật phẩm cùng dấu hiệu nguy hiểm xung quanh.";
let explorationTurn=false;
function setActionBusy(value){
  busy=value;
  submitEl.disabled=value;
  exploreEl.disabled=value;
  submitEl.textContent=value&&!explorationTurn?"ĐANG THỰC HIỆN…":"THỰC HIỆN";
  exploreEl.textContent=value&&explorationTurn?"ĐANG KHÁM PHÁ…":"KHÁM PHÁ & TÌM KIẾM";
}
function submitAction(action,explore=false){
  if(busy||!action)return;
  if(!window.Android){statusEl.textContent="Không tìm thấy Android bridge.";return}
  explorationTurn=explore;
  setActionBusy(true);
  statusEl.textContent="Gemini đang xử lý lượt…";
  try{window.Android.submitTurn(JSON.stringify(state),action)}
  catch(error){window.backroomError(error.message||String(error))}
}
formEl.addEventListener("submit",e=>{e.preventDefault();submitAction(actionEl.value.trim())});
exploreEl.addEventListener("click",()=>submitAction(explorationAction,true));'''
if html.count(old_submit) != 1:
    raise RuntimeError("Action submit handler anchor missing or ambiguous")
html = html.replace(old_submit, new_submit, 1)
# Preserve the player's draft when an exploration turn completes.
turn_anchor = 'actionEl.value="";busy=false;submitEl.disabled=false;'
error_anchor = 'busy=false;submitEl.disabled=false;statusEl.textContent="Lỗi Gemini: "'
if html.count(turn_anchor) != 1 or html.count(error_anchor) != 1:
    raise RuntimeError("Action completion/error anchors missing or ambiguous")
html = html.replace(turn_anchor, 'if(!explorationTurn)actionEl.value="";explorationTurn=false;setActionBusy(false);', 1)
html = html.replace(error_anchor, 'explorationTurn=false;setActionBusy(false);statusEl.textContent="Lỗi Gemini: "', 1)

style_anchor = "</style>"
style = r'''
.app-page{display:none}.app-page.active{display:block}.shell{padding-bottom:calc(56px + env(safe-area-inset-bottom))}
/* Fill the space above navigation; let the document scroll on short viewports. */
#gameplayPage .game{height:calc(100vh - 66px - env(safe-area-inset-bottom));height:calc(100dvh - 66px - env(safe-area-inset-bottom));min-height:540px;display:flex;flex-direction:column}
#gameplayPage .topbar,#gameplayPage .snapshot,#gameplayPage .composer{flex-shrink:0}
#gameplayPage .topbar{padding:7px 10px;gap:6px}
#gameplayPage .topbar h1{margin-top:1px;font-size:18px;line-height:1.12}
#gameplayPage .topbar .eyebrow{font-size:8px;line-height:1.05}
#gameplayPage .topbar .turn{font-size:9px;line-height:1.1}
#gameplayPage .topbar .turn strong{font-size:16px}
#gameplayPage .snapshot{margin:6px 8px}
#gameplayPage .log{height:auto;min-height:96px;flex:1 1 0;padding:6px 8px;gap:6px}
#gameplayPage .message{padding:8px 9px}
#gameplayPage .role{margin-bottom:4px}
#gameplayPage .composer{gap:5px;padding:6px 8px 7px}
#gameplayPage .composer textarea{min-height:64px;padding:8px}
#gameplayPage .composer-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:5px}
#gameplayPage .composer-actions button{min-width:0;min-height:44px;padding:8px 5px;font-size:12px;line-height:1.25}
#gameplayPage .composer-actions button:disabled{opacity:.5}
.page-nav{position:fixed;z-index:40;left:6px;right:6px;bottom:calc(4px + env(safe-area-inset-bottom));display:grid;grid-template-columns:1fr 1fr;gap:4px;padding:4px;border:1px solid #2b3137;background:#0b0e11f2;box-shadow:0 10px 28px #0009}
.page-nav button{min-height:34px;padding:6px 8px;font-size:11px;background:#13181d;color:#8f9aa4;border-color:#303840;letter-spacing:.07em}
.page-nav button.active{background:#252d34;color:#fff;border-color:#59646e}
#infoPage>.status{margin:0 0 8px;border:1px solid #2b3137;background:#0e1114}
#infoPage .side{margin-top:0}
'''
if html.count(style_anchor) != 1:
    raise RuntimeError("Two-page UI style anchor missing or ambiguous")
html = html.replace(style_anchor, style + "\n" + style_anchor, 1)

page_start = '<section class="game">'
if html.count(page_start) != 1:
    raise RuntimeError("Two-page UI game section anchor missing or ambiguous")
html = html.replace(
    page_start,
    '<div class="app-page active" id="gameplayPage">\n' + page_start,
    1,
)

boundary = '<div class="status" id="status"></div>\n</section>'
if html.count(boundary) != 1:
    raise RuntimeError("Two-page UI split boundary missing or ambiguous")
html = html.replace(
    boundary,
    '</section>\n</div>\n<div class="app-page" id="infoPage" hidden>\n<div class="status" id="status"></div>',
    1,
)

main_end = "</main>"
nav = '''</div>
<nav class="page-nav" aria-label="Điều hướng game">
  <button type="button" class="active" data-app-page="gameplayPage" aria-selected="true">GAME</button>
  <button type="button" data-app-page="infoPage" aria-selected="false">THÔNG TIN</button>
</nav>
</main>'''
if html.count(main_end) != 1:
    raise RuntimeError("Two-page UI main closing anchor missing or ambiguous")
html = html.replace(main_end, nav, 1)

body_end = "</body>"
script = r'''<script>
(function(){
  const pageButtons=Array.from(document.querySelectorAll('[data-app-page]'));
  const pages=Array.from(document.querySelectorAll('.app-page'));
  function showAppPage(id){
    pages.forEach(page=>{
      const active=page.id===id;
      page.classList.toggle('active',active);
      page.hidden=!active;
    });
    pageButtons.forEach(button=>{
      const active=button.dataset.appPage===id;
      button.classList.toggle('active',active);
      button.setAttribute('aria-selected',active?'true':'false');
    });
    window.scrollTo(0,0);
  }
  pageButtons.forEach(button=>button.addEventListener('click',()=>showAppPage(button.dataset.appPage)));
  window.showAppPage=showAppPage;
  showAppPage('gameplayPage');
})();
</script>
'''
if html.count(body_end) != 1:
    raise RuntimeError("Two-page UI body closing anchor missing or ambiguous")
html = html.replace(body_end, script + body_end, 1)

# Contract: page 1 contains everything through the action submit form;
# status and all following panels live on page 2.
game_page = html.index('id="gameplayPage"')
form = html.index('id="form"')
submit = html.index('id="submit"')
info_page = html.index('id="infoPage"')
status = html.index('id="status"')
if not (game_page < form < submit < info_page < status):
    raise RuntimeError("Two-page UI ordering contract failed")
for marker in [
    'data-app-page="gameplayPage"',
    'data-app-page="infoPage"',
    "window.showAppPage=showAppPage",
    '#infoPage>.status',
    '#gameplayPage .topbar{padding:7px 10px;gap:6px}',
    '#gameplayPage .log{height:auto;min-height:96px;flex:1 1 0;padding:6px 8px;gap:6px}',
    '#gameplayPage .composer{gap:5px;padding:6px 8px 7px}',
    '.page-nav button{min-height:34px;padding:6px 8px;font-size:11px',
]:
    if marker not in html:
        raise RuntimeError(f"Two-page UI contract missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("Two-page UI applied: compact spacing keeps THỰC HIỆN fully visible while preserving GAME and THÔNG TIN navigation.")
