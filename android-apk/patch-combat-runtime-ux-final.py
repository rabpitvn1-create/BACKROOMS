from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


html = INDEX.read_text(encoding="utf-8")

# Combat events stay authoritative. This patch only exposes the timeline hook used by the
# renderer and keeps non-visual combat UX fixes together. No light/shadow code lives here.
hit_hook_old = """      appendCombatLine(combat,event,i);
      const kind=String(event.kind||'');"""
hit_hook_new = """      appendCombatLine(combat,event,i);
      if(window.__backroomCombatVisuals&&typeof window.__backroomCombatVisuals.hit==='function')await window.__backroomCombatVisuals.hit(event);
      const kind=String(event.kind||'');"""
html = replace_once(html, hit_hook_old, hit_hook_new, "combat timeline visual hook")

old_state = 'let state=(()=>{try{const raw=localStorage.getItem("backroom-apk-state");if(!raw)return JSON.parse(JSON.stringify(initial));const parsed=JSON.parse(raw);return parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed:JSON.parse(JSON.stringify(initial));}catch(e){try{localStorage.removeItem("backroom-apk-state");}catch(ignore){}return JSON.parse(JSON.stringify(initial));}})();'
new_state = 'let state=(()=>{try{const raw=localStorage.getItem("backroom-apk-state");if(!raw){const template=JSON.parse(JSON.stringify(initial));try{if(window.Android&&typeof Android.freshGameState==="function"){const fresh=JSON.parse(Android.freshGameState(JSON.stringify(template)));if(fresh&&typeof fresh==="object"&&!Array.isArray(fresh))return fresh}}catch(ignore){}return template}const parsed=JSON.parse(raw);return parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed:JSON.parse(JSON.stringify(initial));}catch(e){try{localStorage.removeItem("backroom-apk-state");}catch(ignore){}const template=JSON.parse(JSON.stringify(initial));try{if(window.Android&&typeof Android.freshGameState==="function"){const fresh=JSON.parse(Android.freshGameState(JSON.stringify(template)));if(fresh&&typeof fresh==="object"&&!Array.isArray(fresh))return fresh}}catch(ignore){}return template}})();'
html = replace_once(html, old_state, new_state, "authoritative first render")

old_style = '''<style id="pending-combat-style">
.pending-combat-panel{margin:0 10px 10px;padding:12px;border:1px solid #704f38;background:linear-gradient(180deg,#1b130e,#100d0a);box-shadow:inset 0 0 0 1px #2b211b}.pending-combat-panel[hidden]{display:none}.pending-combat-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.pending-combat-head strong{font-size:11px;letter-spacing:.14em;color:#dcb892}.pending-combat-head span{font-size:10px;color:#927d6b}.pending-combat-entities{font-size:13px;color:#e8ecef;line-height:1.45;margin-bottom:10px}.pending-combat-panel button{width:100%;background:#2a1d15;border-color:#8b664a;color:#fff1df;letter-spacing:.08em}.pending-combat-panel button:disabled{opacity:.55}
</style>'''
new_style = '''<style id="pending-combat-style">
.pending-combat-panel{position:fixed;inset:0;z-index:10050;display:grid;place-items:center;padding:18px;background:rgba(3,5,7,.72);backdrop-filter:blur(2px);-webkit-backdrop-filter:blur(2px)}.pending-combat-panel[hidden]{display:none}.pending-combat-dialog{width:min(420px,calc(100vw - 36px));padding:16px;border:1px solid #846145;background:linear-gradient(180deg,#1b130e 0%,#0e0d0c 100%);box-shadow:0 12px 38px rgba(0,0,0,.68),inset 0 0 0 1px #30231a}.pending-combat-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}.pending-combat-head strong{font-size:12px;letter-spacing:.14em;color:#e4c29f}.pending-combat-head span{font-size:10px;color:#9d8673}.pending-combat-entities{font-size:14px;color:#f0f2f3;line-height:1.5;margin-bottom:13px}.pending-combat-dialog button{width:100%;min-height:48px;background:#322116;border:1px solid #9b704e;color:#fff3e3;font-weight:700;letter-spacing:.08em}.pending-combat-dialog button:disabled{opacity:.55}
</style>'''
html = replace_once(html, old_style, new_style, "combat modal style")

old_markup = '''    panel.hidden=false;
    panel.innerHTML='<div class="pending-combat-head"><strong>ENTITY ENCOUNTER</strong><span>'+esc(String(pending.id))+'</span></div><div class="pending-combat-entities">'+esc(names.join(' • ')||'Entity hostile')+'</div><button type="button" id="startCombatButton">'+(startingCombat?'ĐANG KHỞI ĐỘNG…':'BẮT ĐẦU COMBAT')+'</button>';
    const button=document.getElementById('startCombatButton');if(button){button.disabled=startingCombat;button.addEventListener('click',startPendingCombat,{once:true})}
'''
new_markup = '''    if(panel.parentElement!==document.body)document.body.appendChild(panel);
    panel.hidden=false;
    panel.innerHTML='<div class="pending-combat-dialog" role="dialog" aria-modal="true" aria-labelledby="pendingCombatTitle"><div class="pending-combat-head"><strong id="pendingCombatTitle">ENTITY ENCOUNTER</strong><span>'+esc(String(pending.id))+'</span></div><div class="pending-combat-entities">'+esc(names.join(' • ')||'Entity hostile')+'</div><button type="button" id="startCombatButton">'+(startingCombat?'ĐANG KHỞI ĐỘNG…':'BẮT ĐẦU COMBAT')+'</button></div>';
    const button=document.getElementById('startCombatButton');if(button){button.disabled=startingCombat;button.addEventListener('click',startPendingCombat,{once:true});if(!startingCombat)setTimeout(()=>button.focus(),0)}
'''
html = replace_once(html, old_markup, new_markup, "combat modal markup")

html = replace_once(
    html,
    "focusTurn(event.actorId,event.enemyId);await sleep(650);continue;",
    "const nextActor=String(event.actorId||'').toLowerCase();const sameActor=nextActor!==''&&nextActor===currentActor;focusTurn(event.actorId,event.enemyId);if(!sameActor)await sleep(650);continue;",
    "same actor playback",
)

for token in [
    "window.__backroomCombatVisuals.hit(event)",
    'Android.freshGameState(JSON.stringify(template))',
    'position:fixed;inset:0;z-index:10050',
    'class="pending-combat-dialog" role="dialog"',
    "const sameActor=nextActor!==''&&nextActor===currentActor",
]:
    if token not in html:
        raise RuntimeError(f"combat runtime UX contract missing: {token}")

INDEX.write_text(html, encoding="utf-8")
print("Combat runtime UX finalized without legacy light/shadow renderer code.")
