from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
html = INDEX.read_text(encoding="utf-8")

MARKER = "DIRECT_COMBAT_GATE_V1"

if MARKER not in html:
    # Pressure Combat remains authoritative, but its legacy HUD and the intermediate
    # Attack / Evade / Flee selector are removed. Active Entity combat owns the screen
    # and the final Poker Dice patch opens the combat sheet directly.
    legacy_style = re.compile(r'\s*<style id="pressureCombatStyle">.*?</style>\s*', re.DOTALL)
    legacy_script = re.compile(r'\s*<script>\s*/\* PRESSURE_COMBAT_HUD_V1 \*/.*?</script>\s*', re.DOTALL)
    html, style_count = legacy_style.subn("\n", html, count=1)
    html, script_count = legacy_script.subn("\n", html, count=1)
    if style_count != 1 or script_count != 1:
        raise RuntimeError(
            f"Direct combat gate expected one legacy Pressure Combat HUD style/script, found style={style_count} script={script_count}"
        )

    payload = r'''
<style id="combatActionBarStyle">
/* DIRECT_COMBAT_GATE_V1 */
.primary-action-row.combat-direct{display:none!important}
</style>
<script>
(function(){
  const normalButtons={
    search:{label:'Tìm kiếm',aria:'Tìm kiếm',icon:'<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="5.5"></circle><path d="M14.7 14.7 20 20"></path></svg>'},
    execute:{label:'Thực hiện',aria:'Thực hiện',icon:'<svg class="action-icon ai-action-icon" viewBox="0 0 28 24" aria-hidden="true"><path d="M3.5 5.5A2.5 2.5 0 0 1 6 3h11a2.5 2.5 0 0 1 2.5 2.5v7A2.5 2.5 0 0 1 17 15H6a2.5 2.5 0 0 1-2.5-2.5z"></path><text x="7" y="11.8">AI</text><path class="spark" d="M22 2v5m-2.5-2.5h5M23.5 9v3m-1.5-1.5h3"></path></svg>'},
    explore:{label:'Khám phá',aria:'Khám phá',icon:'<svg class="action-icon footprint-icon" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="8" cy="8" rx="3" ry="4.2" transform="rotate(-20 8 8)"></ellipse><ellipse cx="15.8" cy="15.5" rx="3" ry="4.2" transform="rotate(18 15.8 15.5)"></ellipse><circle cx="5.2" cy="3.4" r="1"></circle><circle cx="18.5" cy="10.3" r="1"></circle></svg>'}
  };

  function combatActive(){return !!(window.state&&state.combat&&state.combat.active===true);}
  function byCombatId(id){return document.getElementById(id);}
  function setButton(el,spec){if(!el)return;el.setAttribute('aria-label',spec.aria);el.innerHTML=spec.icon+'<span>'+spec.label+'</span>';}
  function renderCombatActionBar(){
    var row=byCombatId('primaryActionRow'),left=byCombatId('searchActionButton'),middle=byCombatId('submit'),right=byCombatId('exploreActionButton');
    if(!row||!left||!middle||!right)return;
    var active=combatActive();
    row.classList.toggle('combat-direct',active);
    row.hidden=active;
    if(active){
      left.disabled=true;middle.disabled=true;right.disabled=true;
      return;
    }
    setButton(left,normalButtons.search);setButton(middle,normalButtons.execute);setButton(right,normalButtons.explore);
    middle.type='submit';
    if(typeof syncPrimaryActions==='function')syncPrimaryActions();
  }

  // Free-form and macro actions cannot bypass direct Poker Dice while an Entity combat is active.
  var form=byCombatId('form');
  if(form)form.addEventListener('submit',function(ev){if(combatActive()){ev.preventDefault();ev.stopImmediatePropagation();}},true);
  var actionInput=byCombatId('action');
  if(actionInput)actionInput.addEventListener('input',function(){if(combatActive())renderCombatActionBar();});

  var previousRender=window.render;
  if(typeof previousRender==='function')window.render=function(){var value=previousRender.apply(this,arguments);renderCombatActionBar();return value;};
  var previousTurn=window.backroomTurn;
  if(typeof previousTurn==='function')window.backroomTurn=function(json){var value=previousTurn.call(this,json);renderCombatActionBar();return value;};

  window.renderCombatActionBar=renderCombatActionBar;
  renderCombatActionBar();
})();
</script>
'''
    if "</body>" not in html:
        raise RuntimeError("Direct combat gate: closing body tag missing")
    html = html.replace("</body>", payload + "\n</body>", 1)

for marker in (
    "DIRECT_COMBAT_GATE_V1",
    "function combatActive()",
    "row.classList.toggle('combat-direct',active)",
    "row.hidden=active",
    "form.addEventListener('submit'",
    "actionInput.addEventListener('input'",
):
    if marker not in html:
        raise RuntimeError("Direct combat gate contract missing: " + marker)

for forbidden in (
    "label:'TẤN CÔNG'",
    "label:'NÉ TRÁNH'",
    "label:'BỎ CHẠY'",
    "dataset.combatAction",
    "function submitCombat(action)",
    "PRESSURE_COMBAT_HUD_V1",
    'id="pressureCombatStyle"',
    'id="combatHud"',
):
    if forbidden in html:
        raise RuntimeError("Retired combat selector survived direct-combat finalization: " + forbidden)

main = MAIN.read_text(encoding="utf-8")
if "file:///android_asset/entity/" not in html and "file:///android_asset/entity/" not in main:
    raise RuntimeError("Direct combat gate unexpectedly lost local Entity visual authority")

INDEX.write_text(html, encoding="utf-8")
print("Direct combat gate installed: Attack / Evade / Flee selector removed; active Entity combat is reserved for Poker Dice.")

# Inventory authority must remain the final gameplay layer.
runpy.run_path(str(ROOT / "patch-inventory-authority-finalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-inventory-authority-compile-fix.py"), run_name="__main__")
