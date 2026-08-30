from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
html = INDEX.read_text(encoding="utf-8")

MARKER = "COMBAT_ACTION_BAR_V2"

if MARKER not in html:
    # Pressure Combat's Kotlin/runtime state stays authoritative, but its old visible WebView HUD
    # must be removed from the final packaged HTML rather than merely hidden after it renders.
    legacy_style = re.compile(r'\s*<style id="pressureCombatStyle">.*?</style>\s*', re.DOTALL)
    legacy_script = re.compile(r'\s*<script>\s*/\* PRESSURE_COMBAT_HUD_V1 \*/.*?</script>\s*', re.DOTALL)
    html, style_count = legacy_style.subn("\n", html, count=1)
    html, script_count = legacy_script.subn("\n", html, count=1)
    if style_count != 1 or script_count != 1:
        raise RuntimeError(
            f"Combat action bar expected one legacy Pressure Combat HUD style/script, found style={style_count} script={script_count}"
        )

    payload = r'''
<style id="combatActionBarStyle">
/* COMBAT_ACTION_BAR_V2 */
.primary-action-row.combat-actions{grid-template-columns:1fr 1fr 1fr}
.primary-action-row.combat-actions .primary-action{color:#f7f9fa;border-color:#59636c;background:#1b2025;font-weight:800;letter-spacing:.025em}
.primary-action-row.combat-actions .primary-action:active:not(:disabled){background:#2a3137;border-color:#87919a}
.primary-action-row.combat-actions .action-icon{color:#fff;stroke:#fff;fill:none;stroke-width:2}
.primary-action-row:not(.combat-actions) .solid-action-icon{fill:currentColor;stroke:none}
@media(max-width:390px){.primary-action-row.combat-actions .primary-action{font-size:11px;gap:4px;padding:9px 4px}}
</style>
<script>
(function(){
  const normalButtons={
    search:{label:'Tìm kiếm',aria:'Tìm kiếm',icon:'<svg class="action-icon solid-action-icon icon-search" viewBox="0 0 24 24" aria-hidden="true"><path d="M10.75 3.25a7.5 7.5 0 1 0 4.58 13.44l3.99 3.99a.96.96 0 0 0 1.36-1.36l-3.99-3.99a7.5 7.5 0 0 0-5.94-12.08Zm0 2.1a5.4 5.4 0 1 1 0 10.8 5.4 5.4 0 0 1 0-10.8Z"></path></svg>'},
    execute:{label:'Thực hiện',aria:'Thực hiện',icon:'<svg class="action-icon solid-action-icon icon-execute" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.75a9.25 9.25 0 1 0 0 18.5 9.25 9.25 0 0 0 0-18.5Zm-2.1 5.18c0-.72.8-1.15 1.4-.75l6.05 4.07a.9.9 0 0 1 0 1.5l-6.05 4.07a.9.9 0 0 1-1.4-.75V7.93Z"></path></svg>'},
    explore:{label:'Khám phá',aria:'Khám phá',icon:'<svg class="action-icon solid-action-icon icon-explore" viewBox="0 0 24 24" aria-hidden="true"><path d="M20.29 3.71a1.15 1.15 0 0 0-1.2-.27L4.37 9.06a1.2 1.2 0 0 0 .08 2.27l5.43 1.81 1.81 5.43a1.2 1.2 0 0 0 2.27.08l5.62-14.72a1.15 1.15 0 0 0-.29-1.22Zm-7.35 11.02-1.13-3.39-3.39-1.13 8.03-3.07-3.51 7.59Z"></path></svg>'}
  };
  const combatButtons={
    attack:{label:'TẤN CÔNG',aria:'Tấn công Entity',action:'Tấn công',icon:'<svg class="action-icon icon-attack" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.8 4.2 5-2 2 2-2 5-8.1 8.1-5-5z"></path><path d="m9.2 14.8-3.7 3.7"></path><path d="m4 17 3 3"></path><path d="m3 21 2.5-2.5"></path></svg>'},
    evade:{label:'NÉ TRÁNH',aria:'Né tránh Entity',action:'Né tránh',icon:'<svg class="action-icon icon-evade" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7h5"></path><path d="M3 12h9"></path><path d="M3 17h5"></path><path d="M11 7h3.5a5 5 0 0 1 5 5v5"></path><path d="m16.5 14.5 3 3 3-3"></path></svg>'},
    flee:{label:'BỎ CHẠY',aria:'Bỏ chạy khỏi Entity',action:'Bỏ chạy',icon:'<svg class="action-icon icon-flee" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 3h9v18H4z"></path><path d="M10 12h11"></path><path d="m17 8 4 4-4 4"></path><circle cx="8" cy="12" r=".7"></circle></svg>'}
  };

  function combatActive(){return !!(window.state&&state.combat&&state.combat.active===true);}
  function byCombatId(id){return document.getElementById(id);}
  function setButton(el,spec){if(!el)return;el.setAttribute('aria-label',spec.aria);el.innerHTML=spec.icon+'<span>'+spec.label+'</span>';}
  function renderCombatActionBar(){
    var row=byCombatId('primaryActionRow'),left=byCombatId('searchActionButton'),middle=byCombatId('submit'),right=byCombatId('exploreActionButton');
    if(!row||!left||!middle||!right)return;
    var active=combatActive();
    row.classList.toggle('combat-actions',active);
    if(active){
      setButton(left,combatButtons.attack);setButton(middle,combatButtons.evade);setButton(right,combatButtons.flee);
      left.dataset.combatAction='attack';middle.dataset.combatAction='evade';right.dataset.combatAction='flee';
      middle.type='button';
      var locked=typeof busy!=='undefined'&&busy;
      left.disabled=locked;middle.disabled=locked;right.disabled=locked;
    }else{
      setButton(left,normalButtons.search);setButton(middle,normalButtons.execute);setButton(right,normalButtons.explore);
      delete left.dataset.combatAction;delete middle.dataset.combatAction;delete right.dataset.combatAction;
      middle.type='submit';
      if(typeof syncPrimaryActions==='function')syncPrimaryActions();
    }
  }
  function pending(label){
    if(typeof appendMacroPending==='function'){appendMacroPending(label);return;}
    var log=document.getElementById('log');if(!log)return;
    var p=document.createElement('article');p.className='message player pending';p.setAttribute('data-pending','1');p.innerHTML='<div class="role">BẠN</div><div class="text"></div>';p.querySelector('.text').textContent=label;log.appendChild(p);
  }
  function submitCombat(action){
    if(!combatActive())return false;
    if(typeof busy!=='undefined'&&busy)return true;
    if(!window.Android||typeof window.Android.submitAction!=='function'){var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android action bridge.';return true;}
    if(typeof busy!=='undefined')busy=true;
    pending(action);
    var status=document.getElementById('status');if(status)status.textContent='Đang xử lý hành động chiến đấu…';
    renderCombatActionBar();
    window.Android.submitAction(JSON.stringify(state),'EXECUTE',action);
    return true;
  }
  function interceptCombatClick(ev){
    var target=ev.target&&ev.target.closest?ev.target.closest('#searchActionButton,#submit,#exploreActionButton'):null;
    if(!target||!combatActive())return;
    ev.preventDefault();ev.stopImmediatePropagation();
    var key=target.dataset.combatAction;
    if(key&&combatButtons[key])submitCombat(combatButtons[key].action);
  }
  document.addEventListener('click',interceptCombatClick,true);

  // Do not let the original free-form form submit path leak through during combat.
  var form=byCombatId('form');
  if(form)form.addEventListener('submit',function(ev){if(combatActive()){ev.preventDefault();ev.stopImmediatePropagation();}},true);

  // The normal action UI listens to textarea input and may disable Execute when it is empty.
  // Re-apply combat button state after that existing listener so NÉ TRÁNH never depends on textarea text.
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
        raise RuntimeError("Combat action bar: closing body tag missing")
    html = html.replace("</body>", payload + "\n</body>", 1)

for marker in (
    "COMBAT_ACTION_BAR_V2",
    "function combatActive()",
    "TẤN CÔNG",
    "NÉ TRÁNH",
    "BỎ CHẠY",
    "icon-search",
    "icon-execute",
    "icon-explore",
    "solid-action-icon",
    ".primary-action-row:not(.combat-actions) .solid-action-icon{fill:currentColor;stroke:none}",
    "icon-attack",
    "icon-evade",
    "icon-flee",
    "dataset.combatAction",
    "window.Android.submitAction(JSON.stringify(state),'EXECUTE',action)",
    "document.addEventListener('click',interceptCombatClick,true)",
    "actionInput.addEventListener('input'",
    "form.addEventListener('submit'",
):
    if marker not in html:
        raise RuntimeError("Combat action bar contract missing: " + marker)

for obsolete in ('footprint-icon', 'ai-action-icon', '>AI</text>'):
    if obsolete in html:
        raise RuntimeError("Obsolete action icon survived combat finalization: " + obsolete)

# The legacy HUD is gone from the final package, not merely hidden. CombatRuntime remains in Kotlin.
for forbidden in ('PRESSURE_COMBAT_HUD_V1', 'id="pressureCombatStyle"', 'id="combatHud"'):
    if forbidden in html:
        raise RuntimeError("Legacy Pressure Combat HUD survived finalization: " + forbidden)

# Keep scene/Snapshot/Entity visuals untouched. Button icons remain inline SVG, so this visual cleanup
# adds no new bitmap asset, network dependency, or Android resource lookup to the patch chain.
main = MAIN.read_text(encoding="utf-8")
if "file:///android_asset/entity/" not in html and "file:///android_asset/entity/" not in main:
    raise RuntimeError("Combat action bar unexpectedly lost local Entity visual authority")

INDEX.write_text(html, encoding="utf-8")
print("Combat action bar V2 installed with coherent Search / Execute / Explore / Attack / Evade / Flee outline icons while CombatRuntime and scene visuals remain intact.")

# Inventory authority must be the final gameplay layer. It executes after every combat, follower,
# healing, equipment and UI transform so no older patch can restore Gemini inventory authority or
# reintroduce item narration before Game State Core commit.
runpy.run_path(str(ROOT / "patch-inventory-authority-finalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-inventory-authority-compile-fix.py"), run_name="__main__")
