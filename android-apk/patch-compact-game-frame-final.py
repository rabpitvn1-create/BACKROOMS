from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MARKER = "COMPACT_GAME_FRAME_V1"

html = INDEX.read_text(encoding="utf-8")

for required in (
    "TWO_PAGE_SWIPE_UI_V1",
    "RESPONSIVE_DISPLAY_V2",
    'id="primaryActionRow"',
    'id="searchActionButton"',
    'id="exploreActionButton"',
):
    if required not in html:
        raise RuntimeError("Compact game frame must run after responsive/action UI: " + required)

if MARKER not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Compact game frame expected exactly one closing body tag")
    css = r'''
<style id="compactGameFrameV1">
/* COMPACT_GAME_FRAME_V1 */
/* Keep the complete game surface inside one viewport-sized frame. Only the log scrolls. */
.shell.two-page-shell{padding:4px 4px calc(4px + env(safe-area-inset-bottom))}
.shell.two-page-shell>.game,.shell.two-page-shell>.side{
  top:calc(4px + env(safe-area-inset-top));
  bottom:calc(4px + env(safe-area-inset-bottom));
  width:calc(100% - 8px);max-width:calc(100% - 8px)
}
.shell.two-page-shell[data-page="game"]>.game{left:4px}
.shell.two-page-shell[data-page="game"]>.side{left:calc(100% + 4px)}
.shell.two-page-shell[data-page="status"]>.game{left:calc(-100% - 4px)}
.shell.two-page-shell[data-page="status"]>.side{left:4px}
.shell.two-page-shell>.game{display:flex;flex-direction:column;overflow:hidden;min-height:0}
.game>.topbar{flex:0 0 auto;padding:8px 10px;gap:6px}
.game>.topbar h1{margin-top:2px;font-size:clamp(17px,5vw,22px);line-height:1.08}
.game>.snapshot{flex:0 0 clamp(138px,28dvh,224px);height:auto;margin:4px 4px 0}
.game>.log{flex:1 1 auto;height:auto;min-height:112px;overflow-y:auto;padding:5px 4px;gap:6px}
.game>.log .message{padding:8px 10px}
.game>.composer{flex:0 0 auto;padding:5px 4px 4px;gap:5px}
.game>.composer textarea{min-height:58px;max-height:18dvh;padding:8px;resize:none}
.game>.status{flex:0 0 auto;padding:5px 8px;min-height:0}
.primary-action-row{gap:4px;grid-template-columns:minmax(0,1fr) minmax(0,1.08fr) minmax(0,1fr)}
.primary-action-row .primary-action,
.primary-action-row.combat-actions .primary-action{
  min-height:42px;padding:7px 2px;gap:3px;font-size:clamp(9px,2.8vw,12px);letter-spacing:0
}
.primary-action-row .primary-action .action-icon{width:17px;height:17px;flex-basis:17px}
.primary-action-row .primary-action .icon-execute{width:18px;flex-basis:18px}
.primary-action-row .primary-action span{
  display:block;min-width:0;overflow:visible;text-overflow:clip;white-space:nowrap
}
.swipe-page-indicator{bottom:calc(5px + env(safe-area-inset-bottom));padding:2px 7px;gap:7px;background:transparent}
.swipe-page-dot{width:7px;height:7px;min-width:7px;min-height:7px}
@media(max-width:360px){
  .shell.two-page-shell{padding:3px 3px calc(3px + env(safe-area-inset-bottom))}
  .shell.two-page-shell>.game,.shell.two-page-shell>.side{top:calc(3px + env(safe-area-inset-top));bottom:calc(3px + env(safe-area-inset-bottom));width:calc(100% - 6px);max-width:calc(100% - 6px)}
  .shell.two-page-shell[data-page="game"]>.game{left:3px}.shell.two-page-shell[data-page="game"]>.side{left:calc(100% + 3px)}
  .shell.two-page-shell[data-page="status"]>.game{left:calc(-100% - 3px)}.shell.two-page-shell[data-page="status"]>.side{left:3px}
  .game>.topbar{padding:6px 8px}.game>.snapshot{flex-basis:clamp(126px,26dvh,190px);margin:3px 3px 0}
  .game>.log{padding:4px 3px;gap:4px}.game>.log .message{padding:7px 8px}
  .game>.composer{padding:4px 3px 3px}.game>.composer textarea{min-height:52px}
  .primary-action-row{gap:3px}.primary-action-row .primary-action,.primary-action-row.combat-actions .primary-action{padding:6px 1px;gap:2px}
}
@media(orientation:landscape) and (max-height:520px){
  .game>.snapshot{flex-basis:clamp(88px,25dvh,128px)}
  .game>.log{min-height:84px}.game>.composer textarea{min-height:44px;max-height:15dvh}
}
</style>
'''
    html = html.replace("</body>", css + "\n</body>", 1)

for marker in (
    MARKER,
    ".shell.two-page-shell>.game{display:flex;flex-direction:column;overflow:hidden;min-height:0}",
    ".game>.log{flex:1 1 auto;height:auto;min-height:112px;overflow-y:auto",
    "overflow:visible;text-overflow:clip;white-space:nowrap",
    "font-size:clamp(9px,2.8vw,12px)",
):
    if marker not in html:
        raise RuntimeError("Compact game frame contract missing: " + marker)

if html.count(MARKER) != 1:
    raise RuntimeError("Compact game frame must be installed exactly once")

INDEX.write_text(html, encoding="utf-8")
print("Compact game frame finalized: one viewport frame, compact spacing, scrolling log and full action labels.")
