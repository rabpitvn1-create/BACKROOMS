from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
MARKER = "RESPONSIVE_DISPLAY_V2"

html = INDEX.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")

for required in (
    "TWO_PAGE_SWIPE_UI_V1",
    "TWO_PAGE_SWIPE_RUNTIME_V1",
    "RESPONSIVE_DISPLAY_V1",
    "RPG_COMBAT_TEXT_STYLE_V1",
    "window.visualViewport",
    ".primary-action-row",
    ".rpg-hp-loss",
    ".rpg-hp-heal",
):
    if required not in html:
        raise RuntimeError("Responsive Display V2 must run after two-page UI and RPG typography: " + required)

# Android WebView text zoom is independent from the page viewport. Pin it explicitly so Android
# font/display configuration cannot apply an extra text multiplier on top of responsive CSS.
if "settings.setTextZoom(100);" not in main:
    anchor = "    settings.setSupportZoom(false);\n"
    if main.count(anchor) != 1:
        raise RuntimeError("Responsive Display V2: WebView zoom-policy anchor missing")
    main = main.replace(anchor, "    settings.setTextZoom(100);\n" + anchor, 1)

if MARKER not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("Responsive Display V2 expected exactly one closing body tag")

    payload = r'''
<style id="responsiveDisplayV2">
/* RESPONSIVE_DISPLAY_V2 */
html{--backroom-ui-scale:1}
body.two-page-body{font-size:calc(15px * var(--backroom-ui-scale,1))}
/* FVF Fernando 08 has unusually tall glyph metrics; normalize inline RPG spans to surrounding text. */
.rpg-item-name,.rpg-hp-loss,.rpg-hp-heal{font-size:.74em;line-height:1;vertical-align:.02em;letter-spacing:.015em}
/* Preserve the wider center Execute button that the original three-action layout intended. */
.primary-action-row{grid-template-columns:minmax(0,1fr) minmax(0,1.12fr) minmax(0,1fr)}
@media(max-width:390px){
  .primary-action-row .primary-action,
  .primary-action-row.combat-actions .primary-action{font-size:calc(12px * var(--backroom-ui-scale,1));padding:9px 4px;gap:4px}
}
</style>
<script>
// RESPONSIVE_DISPLAY_RUNTIME_V2
(function(){
  if(window.__responsiveDisplayV2)return;
  window.__responsiveDisplayV2=true;
  const DISPLAY_SCALE_REFERENCE=412;
  const DISPLAY_SCALE_MIN=.82;
  function syncDisplayScale(){
    const viewport=window.visualViewport;
    const width=Math.max(1,Math.round(viewport?viewport.width:window.innerWidth));
    const uiScale=Math.max(DISPLAY_SCALE_MIN,Math.min(1,width/DISPLAY_SCALE_REFERENCE));
    document.documentElement.style.setProperty('--backroom-ui-scale',uiScale.toFixed(4));
  }
  syncDisplayScale();
  window.addEventListener('resize',syncDisplayScale,{passive:true});
  window.addEventListener('orientationchange',syncDisplayScale,{passive:true});
  if(window.visualViewport)window.visualViewport.addEventListener('resize',syncDisplayScale,{passive:true});
})();
</script>
'''
    html = html.replace("</body>", payload + "\n</body>", 1)

for marker in (
    "RESPONSIVE_DISPLAY_V2",
    "RESPONSIVE_DISPLAY_RUNTIME_V2",
    "--backroom-ui-scale",
    "body.two-page-body{font-size:calc(15px * var(--backroom-ui-scale,1))}",
    ".rpg-item-name,.rpg-hp-loss,.rpg-hp-heal{font-size:.74em;line-height:1;vertical-align:.02em;letter-spacing:.015em}",
    ".primary-action-row{grid-template-columns:minmax(0,1fr) minmax(0,1.12fr) minmax(0,1fr)}",
    "const DISPLAY_SCALE_REFERENCE=412",
    "const DISPLAY_SCALE_MIN=.82",
    "viewport?viewport.width:window.innerWidth",
    "document.documentElement.style.setProperty('--backroom-ui-scale',uiScale.toFixed(4))",
):
    if marker not in html:
        raise RuntimeError("Responsive Display V2 contract missing: " + marker)

if html.count("RESPONSIVE_DISPLAY_V2") != 1 or html.count("RESPONSIVE_DISPLAY_RUNTIME_V2") != 1:
    raise RuntimeError("Responsive Display V2 must be installed exactly once")
if "settings.setTextZoom(100);" not in main:
    raise RuntimeError("Responsive Display V2: WebView text zoom is not fixed at 100")

INDEX.write_text(html, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Responsive Display V2 finalized: normalized FVF inline metrics, fixed WebView text zoom, width-normalized body/actions, and restored weighted action-button columns.")
