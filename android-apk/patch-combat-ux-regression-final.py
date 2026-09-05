from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatCore.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatUxRegressionGeneratedTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Solo Kai must not pretend to switch party actors every round.
core = CORE.read_text(encoding="utf-8")
core = replace_once(
    core,
    "    var partyCursor = 0\n    var actions = 0\n",
    "    var partyCursor = 0\n    var actions = 0\n    var lastFocusedActorId: String? = null\n    var lastFocusedEnemyId: String? = null\n",
    "combat focus state",
)
core = replace_once(
    core,
    "      timeline += CombatTimelineEvent(\"FOCUS\", actorId = actor.id, enemyId = enemy.id, text = actor.name)\n",
    "      if (lastFocusedActorId != actor.id || lastFocusedEnemyId != enemy.id) {\n        timeline += CombatTimelineEvent(\"FOCUS\", actorId = actor.id, enemyId = enemy.id, text = actor.name)\n        lastFocusedActorId = actor.id\n        lastFocusedEnemyId = enemy.id\n      }\n",
    "solo focus dedupe",
)
CORE.write_text(core, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")

# First launch with no local save must hydrate from current Core before the first render.
old_state = 'let state=(()=>{try{const raw=localStorage.getItem("backroom-apk-state");if(!raw)return JSON.parse(JSON.stringify(initial));const parsed=JSON.parse(raw);return parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed:JSON.parse(JSON.stringify(initial));}catch(e){try{localStorage.removeItem("backroom-apk-state");}catch(ignore){}return JSON.parse(JSON.stringify(initial));}})();'
new_state = 'let state=(()=>{try{const raw=localStorage.getItem("backroom-apk-state");if(!raw){const template=JSON.parse(JSON.stringify(initial));try{if(window.Android&&typeof Android.freshGameState==="function"){const fresh=JSON.parse(Android.freshGameState(JSON.stringify(template)));if(fresh&&typeof fresh==="object"&&!Array.isArray(fresh))return fresh}}catch(ignore){}return template}const parsed=JSON.parse(raw);return parsed&&typeof parsed==="object"&&!Array.isArray(parsed)?parsed:JSON.parse(JSON.stringify(initial));}catch(e){try{localStorage.removeItem("backroom-apk-state");}catch(ignore){}const template=JSON.parse(JSON.stringify(initial));try{if(window.Android&&typeof Android.freshGameState==="function"){const fresh=JSON.parse(Android.freshGameState(JSON.stringify(template)));if(fresh&&typeof fresh==="object"&&!Array.isArray(fresh))return fresh}}catch(ignore){}return template}})();'
html = replace_once(html, old_state, new_state, "authoritative first render")

# Pending combat is a modal over the current story, not an inline panel on page 2.
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

# Old saves can still contain repeated FOCUS events. Do not replay a fake switch for the same actor.
html = replace_once(
    html,
    "focusTurn(event.actorId,event.enemyId);await sleep(650);continue;",
    "const nextActor=String(event.actorId||'').toLowerCase();const sameActor=nextActor!==''&&nextActor===currentActor;focusTurn(event.actorId,event.enemyId);if(!sameActor)await sleep(650);continue;",
    "same actor playback",
)

# Smaller, softer, correctly grounded pixel ellipse shadows.
html = replace_once(
    html,
    ".snapshot .snapshot-ground-shadow{position:absolute;transform:translate(-50%,-50%);border-radius:50%;background:rgba(0,0,0,.76);border:1px solid rgba(0,0,0,.92);box-shadow:none;filter:none;pointer-events:none;image-rendering:pixelated;transition:left 70ms linear,top 70ms linear,width 70ms linear,opacity 80ms linear}",
    ".snapshot .snapshot-ground-shadow{position:absolute;transform:translate(-50%,-50%);border-radius:50%;background:rgba(0,0,0,.48);border:0;box-shadow:none;filter:none;pointer-events:none;image-rendering:pixelated;transition:left 60ms linear,top 60ms linear,width 60ms linear,opacity 70ms linear}",
    "shadow style",
)
html = replace_once(
    html,
    "      const width=clamp(rect.width*.42,22,76);\n      const height=clamp(width*.16,5,11);\n      shadow.style.left=clamp(rect.left-rootRect.left+rect.width*.5,6,rootRect.width-6)+'px';\n      shadow.style.top=clamp(rect.bottom-rootRect.top-2,5,rootRect.height-4)+'px';\n",
    "      const width=clamp(Math.min(rect.width*.27,rect.height*.18),18,50);\n      const height=clamp(width*.13,4,7);\n      shadow.style.left=clamp(rect.left-rootRect.left+rect.width*.5,6,rootRect.width-6)+'px';\n      shadow.style.top=clamp(rect.bottom-rootRect.top-1,4,rootRect.height-3)+'px';\n",
    "shadow geometry",
)

# Remove the hash/cross glyph and animate the damaged target itself. Same code path for Kai and Entity.
old_hit_tail = '''    addImpactBurst(target);animateShadowFollow(620);
    await sleep(560);
    target.classList.remove('combat-hit-push-left','combat-hit-push-right');
    syncGroundShadows();
'''
new_hit_tail = '''    const direction=pushRight?1:-1;
    animateShadowFollow(760);
    if(typeof target.animate==='function'){
      const animation=target.animate([
        {offset:0,transform:'translateX(0) rotate(0deg) scale(1)',filter:'brightness(1) contrast(1) saturate(1)'},
        {offset:.10,transform:'translateX('+(direction*3)+'px) rotate('+(direction*-2)+'deg) scaleX(.95) scaleY(1.04)',filter:'brightness(2.45) contrast(1.35) saturate(.55) drop-shadow(0 0 5px rgba(255,245,220,.95))'},
        {offset:.32,transform:'translateX('+(direction*22)+'px) rotate('+(direction*7)+'deg) scaleX(.84) scaleY(1.09)',filter:'brightness(1.55) contrast(1.22) saturate(.82) drop-shadow(0 0 3px rgba(255,220,190,.72))'},
        {offset:.56,transform:'translateX('+(direction*11)+'px) rotate('+(direction*3)+'deg) scaleX(.93) scaleY(1.04)',filter:'brightness(1.14) contrast(1.08) saturate(.96)'},
        {offset:.78,transform:'translateX('+(direction*-4)+'px) rotate('+(direction*-1.5)+'deg) scale(1.018)',filter:'brightness(1.03) contrast(1.02) saturate(1)'},
        {offset:1,transform:'translateX(0) rotate(0deg) scale(1)',filter:'brightness(1) contrast(1) saturate(1)'}
      ],{duration:680,easing:'cubic-bezier(.16,.82,.28,1)',fill:'none'});
      try{await animation.finished}catch(ignore){await sleep(680)}
    }else{
      target.classList.add(pushRight?'combat-hit-push-right':'combat-hit-push-left');
      await sleep(620);
      target.classList.remove('combat-hit-push-left','combat-hit-push-right');
    }
    syncGroundShadows();
'''
html = replace_once(html, old_hit_tail, new_hit_tail, "symmetric target hit reaction")
if ".combat-impact-burst{display:none!important}" not in html:
    html = html.replace("</style>\n<script>\n(function(){\n  const LIGHT_SAMPLE_W=", ".snapshot .combat-impact-burst{display:none!important}\n</style>\n<script>\n(function(){\n  const LIGHT_SAMPLE_W=", 1)

# Stronger lamp recognition plus clearly visible light->strong pulse. It never blinks off.
html = replace_once(html, "const LIGHT_SAMPLE_W=96,LIGHT_SAMPLE_H=54,MAX_LIGHTS=6;", "const LIGHT_SAMPLE_W=144,LIGHT_SAMPLE_H=81,MAX_LIGHTS=8;", "lamp sampling")
html = replace_once(html, "return luminance(rgb)>=205&&(max-min)<=105;", "return luminance(rgb)>=190&&(max-min)<=125;", "lamp pixel threshold")
html = html.replace("aspect>=1.65&&aspect<=14&&w>=4", "aspect>=1.35&&aspect<=16&&w>=3", 1)
html = html.replace("contrast<18&&avg<242", "contrast<10&&avg<224", 1)
html = html.replace("(aspect>=1.65?1.15:1)", "(aspect>=1.35?1.18:1)", 1)
old_pulse = ".snapshot .snapshot-light-bloom{position:absolute;pointer-events:none;mix-blend-mode:screen;background:rgba(var(--lamp-rgb,255,248,220),.22);box-shadow:0 0 5px 2px rgba(var(--lamp-rgb,255,248,220),.48),0 0 15px 7px rgba(var(--lamp-rgb,255,248,220),.22);filter:brightness(1.18);opacity:.92;animation:backroomLampPulse 2.8s ease-in-out infinite alternate}@keyframes backroomLampPulse{0%{opacity:.48;filter:brightness(.92);box-shadow:0 0 3px 1px rgba(var(--lamp-rgb,255,248,220),.24),0 0 8px 3px rgba(var(--lamp-rgb,255,248,220),.12)}100%{opacity:1;filter:brightness(1.42);box-shadow:0 0 7px 3px rgba(var(--lamp-rgb,255,248,220),.66),0 0 22px 10px rgba(var(--lamp-rgb,255,248,220),.34)}}"
new_pulse = ".snapshot .snapshot-light-bloom{position:absolute;pointer-events:none;mix-blend-mode:screen;background:rgba(var(--lamp-rgb,255,248,220),.30);box-shadow:0 0 5px 2px rgba(var(--lamp-rgb,255,248,220),.52),0 0 17px 8px rgba(var(--lamp-rgb,255,248,220),.26);filter:brightness(1.2);opacity:.86;animation:backroomLampPulse 1.8s ease-in-out infinite alternate}@keyframes backroomLampPulse{0%{opacity:.30;filter:brightness(.95);box-shadow:0 0 2px 1px rgba(var(--lamp-rgb,255,248,220),.22),0 0 7px 3px rgba(var(--lamp-rgb,255,248,220),.10)}100%{opacity:1;filter:brightness(2.15);box-shadow:0 0 9px 4px rgba(var(--lamp-rgb,255,248,220),.82),0 0 30px 14px rgba(var(--lamp-rgb,255,248,220),.48)}}"
html = replace_once(html, old_pulse, new_pulse, "lamp pulse")

for token in [
    'Android.freshGameState(JSON.stringify(template))',
    'position:fixed;inset:0;z-index:10050',
    'class="pending-combat-dialog" role="dialog"',
    'Math.min(rect.width*.27,rect.height*.18)',
    "typeof target.animate==='function'",
    '.combat-impact-burst{display:none!important}',
    'LIGHT_SAMPLE_W=144,LIGHT_SAMPLE_H=81,MAX_LIGHTS=8',
    'animation:backroomLampPulse 1.8s ease-in-out infinite alternate',
]:
    if token not in html:
        raise RuntimeError(f"combat UX regression contract missing: {token}")
if "addImpactBurst(target);animateShadowFollow(620);" in html:
    raise RuntimeError("legacy hash impact glyph is still active")
if "lastFocusedActorId" not in core:
    raise RuntimeError("solo party focus dedupe missing")

INDEX.write_text(html, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatUxRegressionGeneratedTest {
  private class ConstantRandom(private val value: Double) : CombatRandom {
    override fun nextDouble(): Double = value
  }

  @Test fun soloKaiDoesNotEmitRepeatedFocusSwitches() {
    val kai = CombatantState(
      id = KAI_ID,
      name = "Kai Akechi",
      isEntity = false,
      stats = CombatStats(),
      baseDamage = CombatProfiles.partyBaseDamage(KAI_ID)
    )
    val result = AutoTurnCombatEngine(ConstantRandom(0.99)).resolve(
      encounterId = "SOLO_FOCUS",
      partyInput = listOf(kai),
      entityIds = listOf("ENTITY.HOUND"),
      level = 0
    )
    assertEquals(1, result.timeline.count { it.kind == "FOCUS" && it.actorId == KAI_ID })
    assertTrue(result.timeline.count { it.kind == "ATTACK" && it.actorId == KAI_ID } > 1)
  }
}
''', encoding="utf-8")

print("Combat UX regressions fixed: real popup, Turn-1 Core hydration, solo focus dedupe, proper shadows, symmetric hit reaction, and visible lamp pulse.")
