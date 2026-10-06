from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# ---- Game State Core facade -------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
combat_methods = '''
  fun startCombatState(legacyStateJson: String, entityKey: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val next = CombatRuntime.start(current, entityKey)
    repository.save(next)
    return syncLegacy(legacy, next, incrementTurn = false).toString()
  }

  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")

    var resolution = CombatRuntime.resolve(current, actionKind, action)
    if (!resolution.handled) return response(false, legacy, null, "combat_inactive")
    var next = resolution.state
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 1,
      reason = "combat_action"
    ))
    if (time.applied) next = time.state
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    appendLog(output, action, resolution.reply)
    return response(true, output, null, if (resolution.entityDestroyed) "combat_entity_destroyed" else if (resolution.escaped) "combat_escaped" else "combat_resolved", resolution.reply)
  }
'''
if "fun processCombat(legacyStateJson: String, actionKind: String, action: String)" not in facade:
    anchor = "  private fun loadOrMigrate(legacy: JSONObject): GameState {\n"
    if anchor not in facade:
        raise RuntimeError("GameCoreFacade loadOrMigrate anchor missing")
    facade = facade.replace(anchor, combat_methods + "\n" + anchor, 1)

combat_projection = '''    CombatRuntime.toJson(state)?.let { output.put("combat", it) } ?: output.remove("combat")
'''
if combat_projection not in facade:
    anchor = "    return output\n  }\n\n  private fun appendLog"
    if anchor not in facade:
        raise RuntimeError("GameCoreFacade syncLegacy return anchor missing")
    facade = facade.replace(anchor, combat_projection + "    return output\n  }\n\n  private fun appendLog", 1)

for marker in (
    "fun startCombatState(legacyStateJson: String, entityKey: String)",
    "fun processCombat(legacyStateJson: String, actionKind: String, action: String)",
    "CombatRuntime.resolve(current, actionKind, action)",
    'flags.put("entityEncounterKey", "")',
    'output.put("combat", it)',
):
    if marker not in facade:
        raise RuntimeError("Pressure combat facade contract missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")

# ---- Android pipeline -------------------------------------------------------
text = MAIN.read_text(encoding="utf-8")

# An authoritative Entity trigger starts a deterministic CombatRuntime session immediately.
start_combat = '''    flags.put("entityEncounterKey", normalizedEntityKey(entityKey));
    requireGameCore().startCombatState(candidateState.toString(), normalizedEntityKey(entityKey));
'''
if "requireGameCore().startCombatState(candidateState.toString(), normalizedEntityKey(entityKey));" not in text:
    old = '    flags.put("entityEncounterKey", normalizedEntityKey(entityKey));\n'
    text = replace_once(text, old, start_combat, "Entity trigger -> CombatRuntime start")

# While a CombatRuntime session is active it owns the turn. This happens before ActionRuntime and
# before EXPLORE encounter dice, preventing combat moves from spawning a second Entity.
combat_intercept = '''          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));
          if (combatResult.optBoolean("handled", false)) {
            emit("backroomTurn", combatResult.getJSONObject("state").toString());
            return;
          }
'''
if "requireGameCore().processCombat(stateJson, actionKind, action)" not in text:
    anchors = [
        "          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));\n",
        "          JSONObject actionStart = new JSONObject(gameCore.beginAction(stateJson, actionKind, action));\n",
    ]
    for anchor in anchors:
        if anchor in text:
            text = text.replace(anchor, combat_intercept + anchor, 1)
            break
    else:
        raise RuntimeError("Pressure combat submit intercept anchor missing")

for marker in (
    "requireGameCore().startCombatState(candidateState.toString(), normalizedEntityKey(entityKey));",
    "requireGameCore().processCombat(stateJson, actionKind, action)",
    'emit("backroomTurn", combatResult.getJSONObject("state").toString());',
):
    if marker not in text:
        raise RuntimeError("Pressure combat Android bridge missing: " + marker)
MAIN.write_text(text, encoding="utf-8")

# ---- Combat HUD / healthbars ------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
marker = "PRESSURE_COMBAT_HUD_V1"
if marker not in html:
    hud = r'''
<style id="pressureCombatStyle">
#combatHud{display:none;border:1px solid #444b52;background:#0b0e10;padding:10px;margin:8px 0;font-family:system-ui,sans-serif}
#combatHud.active{display:block}.combat-title{display:flex;justify-content:space-between;gap:8px;font-size:12px;font-weight:800;letter-spacing:.08em;margin-bottom:7px}.combat-row{display:grid;grid-template-columns:70px 1fr 62px;align-items:center;gap:7px;margin:5px 0;font-size:11px}.combat-bar{height:12px;background:#252b30;border:1px solid #343c43;overflow:hidden}.combat-fill{height:100%;background:linear-gradient(90deg,#757f88,#d8dee3);transition:width .18s ease}.combat-meta{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.combat-meta span{border:1px solid #343c43;padding:3px 5px;font-size:10px;color:#c8d0d6}.combat-telegraph{margin-top:7px;font-size:11px;color:#f0c979}
</style>
<script>
/* PRESSURE_COMBAT_HUD_V1 */
(function(){
  function ensureHud(){
    var hud=document.getElementById('combatHud');if(hud)return hud;
    hud=document.createElement('section');hud.id='combatHud';
    var target=document.querySelector('.actions')||document.getElementById('log')||document.body;
    if(target.parentNode)target.parentNode.insertBefore(hud,target);else document.body.appendChild(hud);
    return hud;
  }
  function pct(v,m){v=Number(v)||0;m=Math.max(1,Number(m)||1);return Math.max(0,Math.min(100,Math.round(v*100/m)));}
  function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]});}
  function renderCombatHud(){
    var hud=ensureHud(),c=(window.state&&state.combat)||null;
    if(!c||c.active!==true){hud.className='';hud.innerHTML='';return;}
    hud.className='active';
    var php=pct(c.playerHp,c.playerMaxHp),ehp=pct(c.entityHp,c.entityMaxHp);
    hud.innerHTML='<div class="combat-title"><span>PRESSURE COMBAT</span><span>'+esc(c.entityName||c.entityKey)+'</span></div>'+
      '<div class="combat-row"><b>KAI</b><div class="combat-bar"><div class="combat-fill" style="width:'+php+'%"></div></div><span>'+Number(c.playerHp)+'/'+Number(c.playerMaxHp)+'</span></div>'+
      '<div class="combat-row"><b>ENTITY</b><div class="combat-bar"><div class="combat-fill" style="width:'+ehp+'%"></div></div><span>'+Number(c.entityHp)+'/'+Number(c.entityMaxHp)+'</span></div>'+
      '<div class="combat-meta"><span>RANGE '+esc(c.range)+'</span><span>COVER '+esc(c.cover)+'</span><span>MOMENTUM '+Number(c.momentum)+'</span><span>OPENING '+Number(c.opening)+'</span><span>ESCAPE '+Number(c.escapeProgress)+'%</span><span>NOISE '+Number(c.noise)+'</span></div>'+
      '<div class="combat-telegraph">TELEGRAPH: '+esc(c.telegraph||'UNKNOWN')+'</div>';
  }
  var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender.apply(this,arguments);renderCombatHud();};}
  var oldTurn=window.backroomTurn;if(typeof oldTurn==='function'){window.backroomTurn=function(json){oldTurn.call(this,json);renderCombatHud();};}
  window.renderCombatHud=renderCombatHud;setTimeout(renderCombatHud,0);
})();
</script>
'''
    if "</body>" not in html:
        raise RuntimeError("index.html body closing tag missing")
    html = html.replace("</body>", hud + "\n</body>", 1)

for marker in ("PRESSURE_COMBAT_HUD_V1", "combat-fill", "state.combat", "TELEGRAPH:"):
    if marker not in html:
        raise RuntimeError("Pressure combat HUD marker missing: " + marker)
INDEX.write_text(html, encoding="utf-8")

print("Pressure Combat V1 installed: authoritative HP, healthbars, telegraph, position, momentum, escape and deterministic Entity cleanup.")
