from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

if 'id="gameplayPage"' in html:
    print("Two-page UI already applied.")
    raise SystemExit(0)

style_anchor = "</style>"
style = r'''
.app-page{display:none}.app-page.active{display:block}.shell{padding-bottom:calc(68px + env(safe-area-inset-bottom))}
#gameplayPage .topbar{padding:9px 12px;gap:8px}
#gameplayPage .topbar h1{margin-top:2px;font-size:19px;line-height:1.15}
#gameplayPage .topbar .eyebrow{font-size:9px;line-height:1.1}
#gameplayPage .topbar .turn{font-size:10px;line-height:1.15}
#gameplayPage .topbar .turn strong{font-size:17px}
.page-nav{position:fixed;z-index:40;left:8px;right:8px;bottom:calc(6px + env(safe-area-inset-bottom));display:grid;grid-template-columns:1fr 1fr;gap:6px;padding:5px;border:1px solid #2b3137;background:#0b0e11f2;box-shadow:0 12px 36px #000a}
.page-nav button{min-height:36px;padding:8px 10px;font-size:12px;background:#13181d;color:#8f9aa4;border-color:#303840;letter-spacing:.08em}
.page-nav button.active{background:#252d34;color:#fff;border-color:#59646e}
#infoPage>.status{margin:0 0 10px;border:1px solid #2b3137;background:#0e1114}
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
    '#gameplayPage .topbar{padding:9px 12px;gap:8px}',
    '.page-nav button{min-height:36px;padding:8px 10px;font-size:12px',
]:
    if marker not in html:
        raise RuntimeError(f"Two-page UI contract missing: {marker}")

INDEX.write_text(html, encoding="utf-8")
print("Two-page UI applied: compact GAME header/nav keep THỰC HIỆN visible; status and remaining panels are on THÔNG TIN.")
