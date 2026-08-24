from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

MARKER = "COMBAT_ACTION_BAR_V1"

if MARKER not in html:
    payload = r'''
<style id="combatActionBarStyle">
/* COMBAT_ACTION_BAR_V1 */
#combatHud{display:none!important}
.primary-action-row.combat-actions{grid-template-columns:1fr 1fr 1fr}
.primary-action-row.combat-actions .primary-action{color:#f7f9fa;border-color:#59636c;background:#1b2025;font-weight:800;letter-spacing:.025em}
.primary-action-row.combat-actions .primary-action:active:not(:disabled){background:#2a3137;border-color:#87919a}
.primary-action-row.combat-actions .action-icon{color:#fff;stroke:#fff;fill:none}
.primary-action-row.combat-actions .combat-fill-icon{fill:#fff;stroke:none}
@media(max-width:390px){.primary-action-row.combat-actions .primary-action{font-size:11px;gap:4px;padding:9px 4px}}
</style>
<script>
(function(){
  const normalButtons={
    search:{label:'Tìm kiếm',aria:'Tìm kiếm',icon:'<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="5.5"></circle><path d="M14.7 14.7 20 20"></path></svg>'},
    execute:{label:'Thực hiện',aria:'Thực hiện',icon:'<svg class="action-icon ai-action-icon" viewBox="0 0 28 24" aria-hidden="true"><path d="M3.5 5.5A2.5 2.5 0 0 1 6 3h11a2.5 2.5 0 0 1 2.5 2.5v7A2.5 2.5 0 0 1 17 15H6a2.5 2.5 0 0 1-2.5-2.5z"></path><text x="7" y="11.8">AI</text><path class="spark" d="M22 2v5m-2.5-2.5h5M23.5 9v3m-1.5-1.5h3"></path></svg>'},
    explore:{label:'Khám phá',aria:'Khám phá',icon:'<svg class="action-icon footprint-icon" viewBox="0 0 24 24" aria-hidden="true"><ellipse cx="8" cy="8" rx="3" ry="4.2" transform="rotate(-20 8 8)"></ellipse><ellipse cx="15.8" cy="15.5" rx="3" ry="4.2" transform="rotate(18 15.8 15.5)"></ellipse><circle cx="5.2" cy="3.4" r="1"></circle><circle cx="18.5" cy="10.3" r="1"></circle></svg>'}
  };
  const combatButtons={
    attack:{label:'TẤN CÔNG',aria:'Tấn công Entity',action:'Tấn công',icon:'<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 4.5 19.5 9.5 10 19H5v-5z"></path><path d="m13 6 5 5"></path><path d="M4 20h7"></path></svg>'},
    evade:{label:'NÉ TRÁNH',aria:'Né tránh Entity',action:'Né tránh',icon:'<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 5 3 9l4 4"></path><path d="M3 9h9a5 5 0 0 1 5 5v5"></path><path d="m14 16 3 3 3-3"></path></svg>'},
    flee:{label:'BỎ CHẠY',aria:'Bỏ chạy khỏi Entity',action:'Bỏ chạy',icon:'<svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="13" cy="5" r="2"></circle><path d="m11 8-3 4 3 2 2 5"></path><path d="m11 8 4 3 3-1"></path><path d="m8 12-4 6"></path></svg>'}
  };

  function combatActive(){return !!(window.state&&state.combat&&state.combat.active===true);}
  function button(id){return document.getElementById(id);}
  function setButton(el,spec){if(!el)return;el.setAttribute('aria-label',spec.aria);el.innerHTML=spec.icon+'<span>'+spec.label+'</span>';}
  function removeLegacyHud(){var hud=document.getElementById('combatHud');if(hud)hud.remove();}
  function renderCombatActionBar(){
    removeLegacyHud();
    var row=button('primaryActionRow'),left=button('searchActionButton'),middle=button('submit'),right=button('exploreActionButton');
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
  function intercept(ev){
    var target=ev.target&&ev.target.closest?ev.target.closest('#searchActionButton,#submit,#exploreActionButton'):null;
    if(!target||!combatActive())return;
    ev.preventDefault();ev.stopImmediatePropagation();
    var key=target.dataset.combatAction;
    if(key&&combatButtons[key])submitCombat(combatButtons[key].action);
  }
  document.addEventListener('click',intercept,true);

  var observer=new MutationObserver(function(){removeLegacyHud();});
  observer.observe(document.body,{childList:true,subtree:true});

  var previousRender=window.render;
  if(typeof previousRender==='function')window.render=function(){var value=previousRender.apply(this,arguments);renderCombatActionBar();return value;};
  var previousTurn=window.backroomTurn;
  if(typeof previousTurn==='function')window.backroomTurn=function(json){var value=previousTurn.call(this,json);renderCombatActionBar();return value;};

  window.renderCombatActionBar=renderCombatActionBar;
  removeLegacyHud();renderCombatActionBar();
})();
</script>
'''
    if "</body>" not in html:
        raise RuntimeError("Combat action bar: closing body tag missing")
    html = html.replace("</body>", payload + "\n</body>", 1)

# The old Pressure Combat renderer may still exist in the generated HTML because it owns
# the underlying combat mechanics. It must never remain visible or survive in the DOM.
for marker in (
    "COMBAT_ACTION_BAR_V1",
    "function combatActive()",
    "TẤN CÔNG",
    "NÉ TRÁNH",
    "BỎ CHẠY",
    "dataset.combatAction",
    "window.Android.submitAction(JSON.stringify(state),'EXECUTE',action)",
    "new MutationObserver(function(){removeLegacyHud();})",
    "#combatHud{display:none!important}",
):
    if marker not in html:
        raise RuntimeError("Combat action bar contract missing: " + marker)

# Keep the existing scene/Snapshot/Entity overlay implementation untouched. No bitmap assets are
# introduced by this patch; every combat button icon is inline vector SVG using currentColor/white.
if "file:///android_asset/entity/" not in html and "file:///android_asset/entity/" not in (ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8"):
    raise RuntimeError("Combat action bar unexpectedly lost local Entity visual authority")

INDEX.write_text(html, encoding="utf-8")
print("Combat action bar installed: legacy Pressure Combat HUD removed; Entity encounters use Attack / Evade / Flee vector buttons while combat mechanics and scene visuals remain intact.")
