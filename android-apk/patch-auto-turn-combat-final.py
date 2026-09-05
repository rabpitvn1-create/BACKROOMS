from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
PROJECTION = CORE / "CharacterDetailProjection.kt"
SERIALIZER = CORE / "CharacterDetailJson.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Read-only character projection exposes the new combat stats without a save-schema bump.
# ---------------------------------------------------------------------------
projection = PROJECTION.read_text(encoding="utf-8")
projection = replace_once(
    projection,
    "data class CharacterDetailProjection(\n",
    '''data class CharacterCombatProjection(
  val currentHp: Int,
  val maxHp: Int,
  val hpStat: Int,
  val defend: Int,
  val defensePoints: Int,
  val agi: Int,
  val evasionPercent: Double,
  val crit: Int,
  val critPercent: Double,
  val survival: Int,
  val survivalTarget: Int,
  val growthPerCompletion: Int
)

data class CharacterDetailProjection(
''',
    "character combat projection type",
)
projection = replace_once(
    projection,
    "  val statusEffects: List<StatusEffect>\n)",
    "  val statusEffects: List<StatusEffect>,\n  val combat: CharacterCombatProjection\n)",
    "character combat projection field",
)
projection = replace_once(
    projection,
    '''    val effects = character.statusIds.mapNotNull(state.statuses::get)
      .sortedWith(compareBy<StatusEffect> { it.type }.thenBy { it.id })

    return CharacterDetailProjection(''',
    '''    val effects = character.statusIds.mapNotNull(state.statuses::get)
      .sortedWith(compareBy<StatusEffect> { it.type }.thenBy { it.id })
    val combatStats = CombatProgression.read(character)

    return CharacterDetailProjection(''',
    "character combat stats derivation",
)
projection = replace_once(
    projection,
    "      healthState = character.healthState,",
    '      healthState = "${combatStats.currentHp}/${combatStats.maxHp}",',
    "character combat health projection",
)
projection = replace_once(
    projection,
    "      statusEffects = effects\n",
    '''      statusEffects = effects,
      combat = CharacterCombatProjection(
        currentHp = combatStats.currentHp,
        maxHp = combatStats.maxHp,
        hpStat = combatStats.hpStat,
        defend = combatStats.defend,
        defensePoints = combatStats.defensePoints,
        agi = combatStats.agi,
        evasionPercent = combatStats.evasionChance * 100.0,
        crit = combatStats.crit,
        critPercent = combatStats.criticalChance * 100.0,
        survival = combatStats.survival,
        survivalTarget = combatStats.survivalTarget,
        growthPerCompletion = CombatProgression.growthPerCompletion(character.id)
      )
''',
    "character combat projection values",
)
PROJECTION.write_text(projection, encoding="utf-8")

serializer = SERIALIZER.read_text(encoding="utf-8")
serializer = replace_once(
    serializer,
    '    character.healthState?.let { put("healthState", it) }\n',
    '''    character.healthState?.let { put("healthState", it) }
    put("combat", JSONObject().apply {
      put("currentHp", character.combat.currentHp)
      put("maxHp", character.combat.maxHp)
      put("hpStat", character.combat.hpStat)
      put("defend", character.combat.defend)
      put("defensePoints", character.combat.defensePoints)
      put("agi", character.combat.agi)
      put("evasionPercent", character.combat.evasionPercent)
      put("crit", character.combat.crit)
      put("critPercent", character.combat.critPercent)
      put("survival", character.combat.survival)
      put("survivalTarget", character.combat.survivalTarget)
      put("growthPerCompletion", character.combat.growthPerCompletion)
    })
''',
    "character combat JSON",
)
SERIALIZER.write_text(serializer, encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) A validated Entity encounter is resolved locally before authoritative state is saved.
#    Gemini still handles narrative/canon, but it no longer owns hit/crit/evasion/damage outcomes.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
facade = replace_once(
    facade,
    '''    repository.save(committed.state)
    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
''',
    '''    val combatRuntime = CombatRuntime.resolveEncounter(committed.state, candidate, turnId)
    repository.save(combatRuntime.state)
    val synchronized = syncLegacy(candidate, combatRuntime.state, incrementTurn = false)
    synchronized.remove("combat")
    combatRuntime.resolution?.let { synchronized.put("combat", CombatJson.encode(it)) }
''',
    "validated candidate combat commit",
)
FACADE.write_text(facade, encoding="utf-8")

# Tell the writer to stop exactly at encounter setup. The local core owns the battle resolution.
main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    '      "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +\n',
    '      "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +\n'
    '      "Nếu entityEncounter.successIds có ít nhất một Entity, chỉ mô tả việc chạm trán dẫn vào combat; KHÔNG tự quyết định attack, hit, damage, critical, né tránh, HP, status hay kết quả trận. Combat Core Android tự giải quyết toàn bộ trận. " +\n',
    "GM combat authority lock",
)
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) Character Status UI: HP + SURVIVAL bars stay inside the selected character detail only.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
status_old = '<div class="character-section"><h3>Status</h3><div class="survival-hud" id="characterSurvivalHud"></div><div class="character-status-list" id="characterStatusList"></div></div>'
status_new = '<div class="character-section"><h3>Status</h3><div class="combat-hud" id="characterCombatHud"></div><div class="survival-hud" id="characterSurvivalHud"></div><div class="character-status-list" id="characterStatusList"></div></div>'
html = replace_once(html, status_old, status_new, "character combat HUD container")
html = replace_once(
    html,
    "  const survivalHud=document.getElementById('characterSurvivalHud');",
    "  const survivalHud=document.getElementById('characterSurvivalHud');\n  const combatHud=document.getElementById('characterCombatHud');",
    "character combat HUD reference",
)
html = replace_once(
    html,
    "    if(member&&member.healthState)rows.push(['Thể trạng',member.healthState,'status-normal']);",
    "    if(member&&member.healthState&&!member.combat)rows.push(['Thể trạng',member.healthState,'status-normal']);",
    "remove duplicate combat health row",
)
status_tail = "    if(Array.isArray(member&&member.statuses)&&member.statuses.length)rows.push(['Hiệu ứng',member.statuses.map(x=>x.type||x.id).join(', '),'status-warning']);\n    return rows;"
status_tail_new = """    if(member&&member.combat){
      const c=member.combat;
      rows.push(['HP',String(c.hpStat),'status-normal']);
      rows.push(['DEFEND',String(c.defend)+' ['+String(c.defensePoints)+' DF]','status-normal']);
      rows.push(['AGI',String(c.agi)+' ['+Number(c.evasionPercent||0).toFixed(1)+'% EVA]','status-normal']);
      rows.push(['CRIT',String(c.crit)+' ['+Number(c.critPercent||0).toFixed(1)+'%]','status-normal']);
    }
    if(Array.isArray(member&&member.statuses)&&member.statuses.length)rows.push(['Hiệu ứng',member.statuses.map(x=>x.type||x.id).join(', '),'status-warning']);
    return rows;"""
html = replace_once(html, status_tail, status_tail_new, "combat stat rows")

combat_hud_functions = r'''  function combatClamp(raw,min,max){const n=Number(raw);return Number.isFinite(n)?Math.max(min,Math.min(max,n)):min}
  function renderCombatHud(member){
    if(!combatHud)return;
    const c=member&&member.combat;
    if(!c){combatHud.hidden=true;combatHud.innerHTML='';return}
    combatHud.hidden=false;
    const hpMax=Math.max(1,Math.round(Number(c.maxHp)||50));
    const hpNow=Math.round(combatClamp(c.currentHp,0,hpMax));
    const hpPct=Math.round(hpNow*100/hpMax);
    const target=Math.max(1,Math.round(Number(c.survivalTarget)||10));
    const survival=Math.round(combatClamp(c.survival,0,target));
    const survivalPct=Math.round(survival*100/target);
    combatHud.innerHTML='<div class="combat-meter combat-hp"><div class="combat-meter-head"><strong>HP</strong><span>'+hpNow+' / '+hpMax+'</span></div><div class="combat-meter-track"><i style="width:'+hpPct+'%"></i></div></div>'+
      '<div class="combat-meter combat-survival"><div class="combat-meter-head"><strong>SURVIVAL</strong><span>'+survival+' / '+target+'</span></div><div class="combat-meter-track"><i style="width:'+survivalPct+'%"></i></div><small>Đầy thanh: +'+String(c.growthPerCompletion||2)+' mỗi stat</small></div>';
  }
'''
html = replace_once(
    html,
    "  function renderSurvivalHud(member){",
    combat_hud_functions + "  function renderSurvivalHud(member){",
    "combat HUD renderer",
)
html = replace_once(
    html,
    "    renderSurvivalHud(member);\n    const inv=Array.isArray(member.inventory)?member.inventory:(member.id==='kai'?kaiItems():[]);",
    "    renderCombatHud(member);\n    renderSurvivalHud(member);\n    const inv=Array.isArray(member.inventory)?member.inventory:(member.id==='kai'?kaiItems():[]);",
    "combat HUD render call",
)

# ---------------------------------------------------------------------------
# 4) Combat timeline presentation: one active Entity, party overlay follows each automatic turn.
# ---------------------------------------------------------------------------
if "<!-- AUTO_TURN_COMBAT_UI_BEGIN -->" not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("auto-turn combat UI: expected exactly one </body>")
    block = r'''<!-- AUTO_TURN_COMBAT_UI_BEGIN -->
<style>
.combat-hud{display:grid;gap:9px;margin:0 0 12px}.combat-meter{border:1px solid #3b454d;background:#090d10;padding:8px 10px;clip-path:polygon(0 14%,4% 0,94% 0,100% 30%,100% 84%,96% 100%,5% 100%,0 86%)}.combat-meter-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:6px;font-size:11px;letter-spacing:.08em}.combat-meter-head strong{font-size:12px}.combat-meter-track{height:16px;padding:2px;border:1px solid #4a555d;background:#050709;overflow:hidden}.combat-meter-track i{display:block;height:100%;transition:width .22s ease;background:repeating-linear-gradient(90deg,#a85646 0,#a85646 9px,#67362f 10px,#67362f 12px)}.combat-survival .combat-meter-track i{background:repeating-linear-gradient(90deg,#698b96 0,#698b96 9px,#405861 10px,#405861 12px)}.combat-meter small{display:block;margin-top:5px;color:#79858d;font-size:9px;text-align:right}
.snapshot.combat-turn-managed .snapshot-character,.snapshot.combat-turn-managed .snapshot-party-entity-overlay{opacity:0;transform:translateX(10px) rotate(3deg) scale(.97);transition:opacity .2s ease,transform .22s ease}.snapshot.combat-turn-managed .snapshot-character.combat-focus,.snapshot.combat-turn-managed .snapshot-party-entity-overlay.combat-focus{opacity:1;transform:translateX(0) rotate(0) scale(1)}.snapshot.combat-turn-managed .snapshot-entity-overlay{opacity:0;transform:translateX(-7px) rotate(-2deg) scale(.98);transition:opacity .2s ease,transform .22s ease}.snapshot.combat-turn-managed .snapshot-entity-overlay.combat-active-entity{opacity:1;transform:translateX(0) rotate(0) scale(1)}.snapshot.combat-finished .snapshot-party-entity-layer,.snapshot.combat-finished .snapshot-entities{opacity:0!important;pointer-events:none}.snapshot.combat-finished .snapshot-character{opacity:1!important;transform:none!important}
</style>
<script>
(function(){
  let playbackToken=0,currentActor='',currentEnemy='';
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  function box(){return document.getElementById('snapshot')}
  function setCombatControls(locked){
    const submit=document.getElementById('submit'),explore=document.getElementById('explore'),action=document.getElementById('action');
    if(submit)submit.disabled=!!locked;
    if(explore)explore.disabled=!!locked;
    if(action)action.readOnly=!!locked;
  }
  function applyVisualFocus(){
    const root=box();if(!root)return;
    root.classList.add('combat-turn-managed');root.classList.remove('combat-finished');
    const kai=root.querySelector('.snapshot-character');
    if(kai){
      kai.classList.toggle('combat-focus',currentActor==='kai');
      if(currentActor==='kai')kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay_combat.png');
    }
    root.querySelectorAll('.snapshot-party-entity-overlay').forEach(img=>img.classList.toggle('combat-focus',img.dataset.partyEntityId===currentActor));
    root.querySelectorAll('.snapshot-entity-overlay').forEach(img=>img.classList.toggle('combat-active-entity',img.dataset.entityId===currentEnemy));
  }
  function focusTurn(actorId,enemyId){currentActor=String(actorId||'').toLowerCase();if(enemyId)currentEnemy=String(enemyId);applyVisualFocus()}
  function focusEntity(enemyId){if(enemyId)currentEnemy=String(enemyId);applyVisualFocus()}
  function finishVisuals(){
    const root=box();if(!root)return;
    currentActor='';currentEnemy='';root.classList.add('combat-turn-managed','combat-finished');
    root.querySelectorAll('.combat-focus').forEach(node=>node.classList.remove('combat-focus'));
    root.querySelectorAll('.combat-active-entity').forEach(node=>node.classList.remove('combat-active-entity'));
    const kai=root.querySelector('.snapshot-character');if(kai)kai.setAttribute('src','file:///android_asset/kai_snapshot_overlay.png');
  }
  function resetVisuals(){
    const root=box();if(!root)return;
    currentActor='';currentEnemy='';root.classList.remove('combat-turn-managed','combat-finished');
  }
  function alreadyLogged(combatId,index){return Array.isArray(state&&state.log)&&state.log.some(row=>row&&row.combatId===combatId&&Number(row.combatEventIndex)===index)}
  function appendCombatLine(combat,event,index){
    if(!event||!event.text||event.kind==='FOCUS'||alreadyLogged(combat.id,index))return;
    if(!Array.isArray(state.log))state.log=[];
    const row={role:'gm',text:String(event.text),combatId:String(combat.id),combatEventIndex:index};state.log.push(row);
    const log=document.getElementById('log');if(!log)return;
    const article=document.createElement('article');article.className='message gm combat-message';
    const role=document.createElement('div');role.className='role';role.textContent='COMBAT';
    const text=document.createElement('div');text.className='text';text.textContent=row.text;
    article.appendChild(role);article.appendChild(text);log.appendChild(article);log.scrollTop=log.scrollHeight;
  }
  async function playCombat(combat){
    if(!combat||!combat.id||!Array.isArray(combat.timeline))return;
    const token=++playbackToken;setCombatControls(true);await sleep(60);
    for(let i=0;i<combat.timeline.length;i++){
      if(token!==playbackToken)return;
      const event=combat.timeline[i]||{};
      if(event.kind==='FOCUS'){
        focusTurn(event.actorId,event.enemyId);await sleep(220);continue;
      }
      if(event.kind==='ENTITY_ENTER')focusEntity(event.enemyId);
      appendCombatLine(combat,event,i);
      await sleep(event.kind==='ENTITY_ENTER'||event.kind==='ENTITY_DOWN'?300:240);
    }
    if(token!==playbackToken)return;
    try{if(typeof save==='function')save()}catch(ignore){}
    finishVisuals();setCombatControls(false);
  }
  function attachSnapshotObserver(){const root=box();if(root)new MutationObserver(()=>{if(root.classList.contains('combat-turn-managed')&&!root.classList.contains('combat-finished'))applyVisualFocus()}).observe(root,{childList:true,subtree:true})}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attachSnapshotObserver,{once:true});else attachSnapshotObserver();
  const priorTurn=window.backroomTurn;
  if(typeof priorTurn==='function')window.backroomTurn=function(){
    const result=priorTurn.apply(this,arguments);
    setTimeout(()=>{
      const combat=state&&state.combat;
      if(combat&&combat.id&&Array.isArray(combat.timeline)&&combat.timeline.length)playCombat(combat);else{playbackToken++;resetVisuals();setCombatControls(false)}
    },0);
    return result;
  };
})();
</script>
<!-- AUTO_TURN_COMBAT_UI_END -->'''
    html = html.replace("</body>", block + "\n</body>", 1)

for token in [
    'id="characterCombatHud"',
    'function renderCombatHud(member)',
    "rows.push(['DEFEND'",
    "rows.push(['AGI'",
    "rows.push(['CRIT'",
    '<!-- AUTO_TURN_COMBAT_UI_BEGIN -->',
    "event.kind==='FOCUS'",
    'combat-active-entity',
    'kai_snapshot_overlay_combat.png',
    'kai_snapshot_overlay.png',
]:
    if token not in html:
        raise RuntimeError(f"auto-turn combat UI contract missing: {token}")
INDEX.write_text(html, encoding="utf-8")

# Fail closed if the authority or projection wiring disappears later in the chain.
for path, tokens in {
    PROJECTION: ["CharacterCombatProjection", "CombatProgression.read(character)", "growthPerCompletion"],
    SERIALIZER: ['put("combat"', 'put("defensePoints"', 'put("survivalTarget"'],
    FACADE: ["CombatRuntime.resolveEncounter(committed.state, candidate, turnId)", 'synchronized.put("combat", CombatJson.encode(it))'],
    MAIN: ["Combat Core Android tự giải quyết toàn bộ trận"],
}.items():
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise RuntimeError(f"auto-turn combat contract missing in {path.name}: {token}")

print("Auto-turn Combat v1 wired: deterministic Basic Attack rotation, Entity queue, Survival progression, character-only HP/Survival HUD and sequential overlay playback.")
