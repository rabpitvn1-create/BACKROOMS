from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

MARKER = "POKER_DICE_119_POLISH_R01"
if MARKER in html:
    raise RuntimeError("1.1.99 Poker Dice polish already applied")
for required in ("POKER_DICE_CORE_BACKPORT_R01", "DIRECT_COMBAT_GATE_V1", 'id="pokerDiceModal"'):
    if required not in html:
        raise RuntimeError("Poker Dice 1.1.99 polish prerequisite missing: " + required)

style = r'''
<style id="pokerDice119PolishStyle">
/* POKER_DICE_119_POLISH_R01 */
.poker-dice-sheet{isolation:isolate;background-color:#39341e!important;background-image:linear-gradient(145deg,rgba(15,17,13,.55),rgba(12,15,13,.74) 55%,rgba(15,17,13,.60)),url('file:///android_asset/dice/level0-wallpaper.svg')!important;background-size:auto,64px 96px!important;border-color:#8b8052!important;box-shadow:inset 0 0 0 1px #c5b87818,inset 0 -24px 50px #0004,0 -18px 52px #000b!important}
.poker-dice-sheet:before{content:"";position:absolute;inset:0;z-index:0;pointer-events:none;opacity:.14;background:repeating-linear-gradient(0deg,transparent 0 2px,#e8dca21a 3px,transparent 4px)}
.poker-dice-sheet>*{position:relative;z-index:1}
.poker-dice-kicker{font-size:9px;font-weight:800;letter-spacing:.16em;color:#b9ad77;text-shadow:0 1px 2px #000}
.poker-dice-head h2{font-size:14px!important;font-weight:700!important;letter-spacing:.09em!important;color:#efe8cf!important;text-shadow:0 1px 0 #000,0 0 10px rgba(194,178,103,.16)}
.poker-dice-meta{margin-top:7px!important;color:#c5c3a8!important;font-size:11px!important;letter-spacing:.02em;text-shadow:0 1px 2px #000}
.poker-dice-row{display:grid!important;grid-template-columns:repeat(5,minmax(0,1fr))!important;gap:3px!important;padding:8px 0 0!important;margin-top:4px!important}
.poker-die{--die-delay:0ms;position:relative!important;min-width:44px!important;min-height:76px!important;aspect-ratio:auto!important;padding:4px 0 24px!important;border:1px solid transparent!important;border-radius:7px!important;background:transparent!important;display:grid!important;place-items:center!important;overflow:visible!important;box-shadow:none!important;transition:background .18s,border-color .18s!important}
.poker-die-shadow{position:absolute;left:16%;right:12%;bottom:24px;height:9px;border-radius:50%;background:#0008;filter:blur(4px);pointer-events:none}
.poker-die-object{position:relative;width:100%;max-width:84px;aspect-ratio:1;display:grid;place-items:center;transform-origin:50% 65%;z-index:1}
.poker-die-skin{display:block!important;width:100%!important;height:100%!important;object-fit:contain!important;pointer-events:none;filter:drop-shadow(0 3px 2px #0005)!important}
.poker-die-unknown{width:78%;aspect-ratio:1;display:grid;place-items:center;border:1px solid #645b49;border-radius:7px;background:linear-gradient(145deg,#25261f,#151816);color:#ad9e79;font:700 24px/1 system-ui,sans-serif}
.poker-die-hold-seal{position:absolute;bottom:5px;left:50%;transform:translateX(-50%);display:block;visibility:hidden;min-width:0;padding:3px 0;border:0;background:transparent;color:#9be1bc;font:700 9px/1 system-ui,sans-serif;letter-spacing:.04em;pointer-events:none;white-space:nowrap}
.poker-die.held{--held-glow:#9be1bc;border-color:transparent!important;background:transparent!important;box-shadow:none!important}
.poker-die.held:after{content:none!important}
.poker-die.held .poker-die-hold-seal{visibility:visible;color:var(--held-glow);text-shadow:0 0 7px #9be1bc88}
.poker-die.held .poker-die-hold-seal:before{content:"";display:inline-block;width:4px;height:4px;margin-right:4px;border-radius:50%;background:var(--held-glow);box-shadow:0 0 5px #9be1bcaa;vertical-align:middle}
.poker-die.held .poker-die-skin{filter:drop-shadow(0 0 2px var(--held-glow)) drop-shadow(0 0 6px #9be1bc99) drop-shadow(0 0 11px #9be1bc55) drop-shadow(0 3px 2px #0005)!important}
.poker-die:focus-visible{outline:2px solid #e8ca87;outline-offset:2px}
.poker-die:active:not(:disabled) .poker-die-object{transform:translateY(2px)}
.poker-die:disabled{opacity:.84}
.poker-die.rolling:before{display:none!important}
.poker-die.rolling .poker-die-skin{visibility:hidden}
.poker-die.rolling .poker-die-shadow{animation:poker-die-shadow-roll .68s ease-in-out infinite;animation-delay:var(--die-delay)}
.poker-die.rolling .poker-die-object:before{content:"";position:absolute;inset:0;background-image:url('file:///android_asset/dice/roll-3d.png');background-size:2400% 100%;background-repeat:no-repeat;animation:poker-die-3d .68s steps(23,end) infinite;animation-delay:var(--die-delay)}
.poker-die.rolling .poker-die-object{animation:poker-die-toss .68s ease-in-out infinite;animation-delay:var(--die-delay)}
.poker-die.settling .poker-die-object{animation:poker-die-settle .17s ease-out both}
@keyframes poker-die-3d{from{background-position:0% 0}to{background-position:100% 0}}
@keyframes poker-die-toss{0%,100%{transform:translateY(0)}35%{transform:translateY(-7px)}70%{transform:translateY(-3px)}}
@keyframes poker-die-shadow-roll{0%,100%{transform:scaleX(1);opacity:.8}22%,74%{transform:scaleX(.65);opacity:.35}48%{transform:scaleX(.8);opacity:.55}}
@keyframes poker-die-settle{0%{transform:translateY(-4px) scale(1.02)}60%{transform:translateY(1px) scale(.98)}100%{transform:translateY(0) scale(1)}}
.poker-dice-hand{min-height:22px!important;margin-top:8px!important;text-align:center;font-size:16px!important;font-weight:700!important;letter-spacing:.055em!important;color:#d8c477!important;text-shadow:0 1px 0 #000,0 0 11px rgba(216,196,119,.2)!important}
.poker-dice-actions{display:grid!important;grid-template-columns:1fr 1fr!important;gap:8px!important;margin-top:10px!important}
.poker-dice-roll,.poker-dice-finish{width:100%;min-height:48px;padding:12px 8px!important;color:#f2f4ef;font-weight:700!important;letter-spacing:.12em!important;border-radius:7px!important;transition:filter .1s,box-shadow .1s,transform .1s}
.poker-dice-roll{background:linear-gradient(180deg,#1b3029,#13221e)!important;border:1px solid #4f9479!important;box-shadow:inset 0 0 0 1px rgba(103,190,153,.09),0 4px 12px rgba(0,0,0,.16)!important;color:#dff7ec!important}
.poker-dice-finish{background:linear-gradient(180deg,#30231d,#231815)!important;border:1px solid #8b6047!important;box-shadow:inset 0 0 0 1px rgba(180,122,84,.08),0 4px 12px rgba(0,0,0,.16)!important;color:#f2ddd0!important}
.poker-dice-roll:active:not(:disabled){transform:translateY(-2px);filter:brightness(1.3);box-shadow:inset 0 0 0 1px #a0ffd655,0 0 10px #69d8a888,0 0 22px #69d8a844!important}
.poker-dice-finish:active:not(:disabled){transform:translateY(-2px);filter:brightness(1.3);box-shadow:inset 0 0 0 1px #ffc39855,0 0 10px #cb926988,0 0 22px #cb926944!important}
.poker-dice-roll:disabled,.poker-dice-finish:disabled{opacity:.45}
@media(max-width:390px){.poker-die{min-width:0!important}.poker-dice-sheet{padding-left:10px!important;padding-right:10px!important}}
@media(prefers-reduced-motion:reduce){.poker-die,.poker-die-object,.poker-dice-roll,.poker-dice-finish{transition:none!important}.poker-die.rolling .poker-die-object,.poker-die.rolling .poker-die-shadow,.poker-die.settling .poker-die-object{animation:none!important}.poker-die.rolling .poker-die-object:before{display:none!important;animation:none!important}.poker-die.rolling .poker-die-skin{visibility:visible!important}}
</style>
'''
if "</head>" not in html:
    raise RuntimeError("Poker Dice 1.1.99 polish: closing head missing")
html = html.replace("</head>", style + "\n</head>", 1)

old_vars = '''  var DIRECT_COMBAT_ACTION="Cả Party cùng tấn công",submitAfterFinalize=false;
  window.__combatDiceBusy=false;window.__directCombatPreparing=false;window.__directCombatResolving=false;
'''
new_vars = '''  var DIRECT_COMBAT_ACTION="Cả Party cùng tấn công",submitAfterFinalize=false;
  var diceRollAnimating=false,diceRollStartedAt=0,diceRollToken=0,diceSettleTimer=0,diceSettleUntil=0;
  var diceSettleMask=[false,false,false,false,false],finalizeTimer=0,finalizeKey="";
  var DICE_ROLL_ANIMATION_MS=680,DICE_SETTLE_ANIMATION_MS=170,FINALIZE_PREVIEW_MS=2000;
  window.__combatDiceBusy=false;window.__combatDiceRolling=false;window.__directCombatPreparing=false;window.__directCombatResolving=false;
'''
if old_vars not in html:
    raise RuntimeError("Poker Dice 1.1.99 polish: runtime variable anchor missing")
html = html.replace(old_vars, new_vars, 1)

render_start = html.find("  function renderDice(){")
render_end = html.find("\n  function ensureDirectCombatDice(){", render_start)
if render_start < 0 or render_end < 0:
    raise RuntimeError("Poker Dice 1.1.99 polish: renderDice boundaries missing")
render = r'''  function diceAsset(value){return "file:///android_asset/dice/die-"+String(value)+".png"}
  function allHeld(values){return Array.isArray(values)&&values.length===5&&values.every(function(x){return x===true})}
  function handLabel(value){
    var labels={"NO HAND":"No Hand","ONE PAIR":"One Pair","TWO PAIR":"Two Pair","THREE OF A KIND":"Three of a Kind","STRAIGHT":"Straight","FULL HOUSE":"Full House","FOUR OF A KIND":"Four of a Kind","SSF":"SSF","FSF":"FSF"};
    var key=String(value||"NO HAND");return labels[key]||key;
  }
  function renderDice(){
    var d=dice();
    if(!d){row.textContent="";hand.textContent="";meta.textContent="Đang chuẩn bị lượt combat…";return}
    var actor=state&&state.combat&&state.combat.currentActor;
    document.getElementById("pokerDiceTitle").textContent=String(actor||"POKER DICE");
    var rerolls=Math.max(0,Number(d.rerollsUsed)||0),maxRerolls=Math.max(0,Number(d.maxRerolls)||3);
    meta.textContent="Lượt Quay "+String(rerolls)+"/"+String(maxRerolls)+" - Chạm Vào Xúc Xắc Để Giữ";
    row.textContent="";
    var values=Array.isArray(d.values)?d.values:[0,0,0,0,0],held=Array.isArray(d.held)?d.held:[false,false,false,false,false];
    for(var i=0;i<5;i++){(function(index){
      var button=document.createElement("button");button.type="button";
      var rolling=diceRollAnimating&&held[index]!==true;
      var settling=!rolling&&held[index]!==true&&diceSettleUntil>Date.now()&&diceSettleMask[index]===true;
      button.className="poker-die"+(held[index]===true?" held":"")+(rolling?" rolling":"")+(settling?" settling":"");
      button.style.setProperty("--die-delay",String(index*-55)+"ms");
      button.disabled=d.finalized===true||window.__combatDiceBusy;
      button.setAttribute("aria-pressed",held[index]===true?"true":"false");
      var shadow=document.createElement("span");shadow.className="poker-die-shadow";shadow.setAttribute("aria-hidden","true");button.appendChild(shadow);
      var seal=document.createElement("span");seal.className="poker-die-hold-seal";seal.textContent="GIỮ";seal.setAttribute("aria-hidden","true");button.appendChild(seal);
      var object=document.createElement("span");object.className="poker-die-object";button.appendChild(object);
      var value=Number(values[index])||0,visualValue=value>=1&&value<=6?value:(rolling?(index%6)+1:0);
      button.setAttribute("aria-label","Xúc xắc "+String(index+1)+(value>=1&&value<=6?" · "+String(value):"")+(held[index]===true?" · Đang giữ":""));
      if(visualValue>=1&&visualValue<=6){
        var img=document.createElement("img");img.className="poker-die-skin";img.src=diceAsset(visualValue);img.alt="";img.setAttribute("aria-hidden","true");object.appendChild(img);
      }else{
        var blank=document.createElement("span");blank.className="poker-die-unknown";blank.textContent="?";object.appendChild(blank);
      }
      button.addEventListener("click",function(){
        if(window.__combatDiceBusy||d.finalized===true||!window.Android||typeof Android.combatDiceHold!=="function")return;
        setBusy(true);renderDice();Android.combatDiceHold(JSON.stringify(state),index,held[index]!==true);
      });
      row.appendChild(button);
    })(i)}
    hand.textContent=handLabel(d.hand);
    roll.textContent="ROLL";finish.textContent="FINISH";
    roll.disabled=window.__combatDiceBusy||d.finalized===true||rerolls>=maxRerolls||allHeld(held);
    finish.disabled=window.__combatDiceBusy||d.finalized===true;
  }
'''
html = html[:render_start] + render + html[render_end:]

ensure_start = html.find("  function ensureDirectCombatDice(){")
ensure_end = html.find("\n  window.ensureDirectCombatDice=ensureDirectCombatDice;", ensure_start)
if ensure_start < 0 or ensure_end < 0:
    raise RuntimeError("Poker Dice 1.1.99 polish: ensureDirectCombatDice boundaries missing")
ensure = r'''  function clearDiceTimers(){
    if(diceSettleTimer){clearTimeout(diceSettleTimer);diceSettleTimer=0}
    if(finalizeTimer){clearTimeout(finalizeTimer);finalizeTimer=0}
    diceSettleUntil=0;diceSettleMask=[false,false,false,false,false];
  }
  function scheduleDirectResolve(d){
    if(!d||d.finalized!==true||window.__directCombatResolving)return;
    var key=String(d.hand||"")+":"+String(d.rerollsUsed||0)+":"+String(state&&state.combat&&state.combat.encounterId||"");
    if(finalizeKey===key&&finalizeTimer)return;
    if(finalizeTimer)clearTimeout(finalizeTimer);
    finalizeKey=key;
    if(statusEl)statusEl.textContent="Đã chốt "+handLabel(d.hand)+".";
    finalizeTimer=setTimeout(function(){
      finalizeTimer=0;
      if(!combatActive()||window.__directCombatResolving)return;
      var current=dice();if(!current||current.finalized!==true)return;
      window.__directCombatResolving=true;setBusy(true);
      if(statusEl)statusEl.textContent="Đang giải quyết combat…";
      Android.submitAction(JSON.stringify(state),"EXECUTE",DIRECT_COMBAT_ACTION);
    },FINALIZE_PREVIEW_MS);
  }
  function ensureDirectCombatDice(){
    if(!combatActive()){
      window.__directCombatPreparing=false;window.__directCombatResolving=false;clearDiceTimers();hide();
      if(typeof busy!=="undefined")busy=false;
      if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar();
      return;
    }
    show();
    var d=dice();
    if(d){
      window.__directCombatPreparing=false;window.__combatDiceBusy=false;renderDice();
      if(typeof busy!=="undefined")busy=true;
      if(d.finalized===true){scheduleDirectResolve(d);return}
      if(statusEl)statusEl.textContent="Poker Dice · "+handLabel(d.hand);
      return;
    }
    if(window.__directCombatPreparing||window.__directCombatResolving)return;
    if(!window.Android||typeof Android.combatDicePrepare!=="function"){
      if(statusEl)statusEl.textContent="Không tìm thấy Poker Dice bridge.";
      return;
    }
    window.__directCombatPreparing=true;setBusy(true);renderDice();
    if(statusEl)statusEl.textContent="Đang vào combat…";
    Android.combatDicePrepare(JSON.stringify(state),DIRECT_COMBAT_ACTION);
  }'''
html = html[:ensure_start] + ensure + html[ensure_end:]

old_roll = '''  roll.addEventListener("click",function(){
    if(window.__combatDiceBusy||!window.Android||typeof Android.combatDiceRoll!=="function")return;
    window.__combatDiceRolling=true;setBusy(true);renderDice();Android.combatDiceRoll(JSON.stringify(state));
  });
'''
new_roll = '''  roll.addEventListener("click",function(){
    if(window.__combatDiceBusy||!window.Android||typeof Android.combatDiceRoll!=="function")return;
    window.__combatDiceRolling=true;diceRollAnimating=true;diceRollStartedAt=Date.now();++diceRollToken;
    setBusy(true);renderDice();Android.combatDiceRoll(JSON.stringify(state));
  });
'''
if old_roll not in html:
    raise RuntimeError("Poker Dice 1.1.99 polish: ROLL handler anchor missing")
html = html.replace(old_roll, new_roll, 1)

state_start = html.find("  window.backroomCombatDiceState=function(json){")
state_end = html.find("\n  function selectedCharacter(){", state_start)
if state_start < 0 or state_end < 0:
    raise RuntimeError("Poker Dice 1.1.99 polish: dice callback boundaries missing")
callback = r'''  window.backroomCombatDiceState=function(json){
    try{
      var nextState=JSON.parse(json);
      var applyDiceState=function(){
        var wasRolling=diceRollAnimating;
        if(wasRolling){
          var before=dice(),beforeHeld=before&&Array.isArray(before.held)?before.held:[false,false,false,false,false];
          diceSettleMask=beforeHeld.map(function(v){return v!==true});
          diceSettleUntil=Date.now()+DICE_SETTLE_ANIMATION_MS;
          if(diceSettleTimer)clearTimeout(diceSettleTimer);
          diceSettleTimer=setTimeout(function(){diceSettleTimer=0;diceSettleUntil=0;renderDice()},DICE_SETTLE_ANIMATION_MS+24);
        }
        diceRollAnimating=false;window.__combatDiceRolling=false;window.__directCombatPreparing=false;window.__combatDiceBusy=false;
        state=nextState;show();renderDice();
        var d=dice();
        if(submitAfterFinalize&&d&&d.finalized===true){submitAfterFinalize=false;scheduleDirectResolve(d);return}
        if(d&&d.finalized===true){scheduleDirectResolve(d);return}
        if(typeof busy!=="undefined")busy=true;
        if(statusEl)statusEl.textContent="Poker Dice · "+handLabel(d&&d.hand);
        if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar();
      };
      if(diceRollAnimating){
        var token=diceRollToken,wait=Math.max(0,DICE_ROLL_ANIMATION_MS-(Date.now()-diceRollStartedAt));
        if(wait>0){setTimeout(function(){if(token===diceRollToken)applyDiceState()},wait);return}
      }
      applyDiceState();
    }catch(_){window.backroomCombatDiceError("Poker Dice state không hợp lệ.")}
  };
  window.backroomCombatDiceError=function(message){
    diceRollAnimating=false;window.__combatDiceBusy=false;window.__combatDiceRolling=false;window.__directCombatPreparing=false;window.__directCombatResolving=false;submitAfterFinalize=false;++diceRollToken;clearDiceTimers();
    if(typeof busy!=="undefined")busy=combatActive();
    if(statusEl)statusEl.textContent=String(message||"Poker Dice lỗi.");
    renderDice();if(typeof window.renderCombatActionBar==="function")window.renderCombatActionBar();
  };
'''
html = html[:state_start] + callback + html[state_end:]

old_turn_reset = '''    window.__combatDiceBusy=false;window.__combatDiceRolling=false;window.__directCombatPreparing=false;window.__directCombatResolving=false;submitAfterFinalize=false;
    renderCorePanel();
'''
new_turn_reset = '''    diceRollAnimating=false;window.__combatDiceBusy=false;window.__combatDiceRolling=false;window.__directCombatPreparing=false;window.__directCombatResolving=false;submitAfterFinalize=false;++diceRollToken;clearDiceTimers();
    renderCorePanel();
'''
if old_turn_reset not in html:
    raise RuntimeError("Poker Dice 1.1.99 polish: turn reset anchor missing")
html = html.replace(old_turn_reset, new_turn_reset, 1)

for marker in (
    MARKER,
    "poker-die-shadow",
    "poker-die-object",
    "poker-die-hold-seal",
    "@keyframes poker-die-3d",
    "@keyframes poker-die-toss",
    "@keyframes poker-die-shadow-roll",
    "@keyframes poker-die-settle",
    'setProperty("--die-delay",String(index*-55)+"ms")',
    "DICE_ROLL_ANIMATION_MS=680",
    "DICE_SETTLE_ANIMATION_MS=170",
    "FINALIZE_PREVIEW_MS=2000",
    'meta.textContent="Lượt Quay "',
    'seal.textContent="GIỮ"',
    "scheduleDirectResolve",
):
    if marker not in html:
        raise RuntimeError("Poker Dice 1.1.99 polish contract missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
print("Poker Dice UI upgraded to full 1.1.99 animation quality: staggered 3D roll, toss/shadow, settle, HOLD glow, press feedback and 2s finalized-hand preview; 7px shell preserved.")
