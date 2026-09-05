from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
RUNTIME = CORE / "CombatRuntime.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatStartGateGeneratedTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Stage an Entity encounter first. No HP/Survival/skill RNG is committed until Start Combat.
# ---------------------------------------------------------------------------
runtime = RUNTIME.read_text(encoding="utf-8")
start = runtime.find("object CombatRuntime {")
end = runtime.find("\n\nobject CombatJson", start)
if start < 0 or end < 0:
    raise RuntimeError("CombatRuntime object anchors not found")

runtime_object = r'''object CombatRuntime {
  private const val LAST_ENCOUNTER_KEY = "combat.lastEncounterId"
  private const val PENDING_ID_KEY = "combat.pendingEncounterId"
  private const val PENDING_ENTITIES_KEY = "combat.pendingEntityIds"
  private const val PENDING_LEVEL_KEY = "combat.pendingLevel"

  fun hasPending(state: GameState): Boolean = !state.metadata[PENDING_ID_KEY].isNullOrBlank()

  fun resolveEncounter(
    state: GameState,
    candidate: JSONObject,
    encounterId: String,
    random: CombatRandom = DefaultCombatRandom()
  ): CombatRuntimeResult {
    val entityIds = encounterIds(candidate)
    if (entityIds.isEmpty()) return CombatRuntimeResult(state, null)
    if (state.metadata[LAST_ENCOUNTER_KEY] == encounterId) return CombatRuntimeResult(state, null)
    if (hasPending(state)) return CombatRuntimeResult(state, null)

    val staged = state.copy(metadata = state.metadata + mapOf(
      PENDING_ID_KEY to encounterId,
      PENDING_ENTITIES_KEY to entityIds.joinToString(","),
      PENDING_LEVEL_KEY to levelNumber(candidate, state).toString()
    ))
    return CombatRuntimeResult(staged, null)
  }

  fun pendingJson(state: GameState): JSONObject? {
    val encounterId = state.metadata[PENDING_ID_KEY]?.takeIf { it.isNotBlank() } ?: return null
    val entityIds = pendingEntityIds(state)
    if (entityIds.isEmpty()) return null
    return JSONObject().apply {
      put("id", encounterId)
      put("level", state.metadata[PENDING_LEVEL_KEY]?.toIntOrNull() ?: 0)
      put("entityQueue", JSONArray(entityIds))
      put("entityNames", JSONArray(entityIds.map(CombatProfiles::entityName)))
    }
  }

  fun startPendingEncounter(
    state: GameState,
    random: CombatRandom = DefaultCombatRandom()
  ): CombatRuntimeResult {
    val encounterId = state.metadata[PENDING_ID_KEY]?.takeIf { it.isNotBlank() }
      ?: return CombatRuntimeResult(state, null)
    val entityIds = pendingEntityIds(state)
    val level = state.metadata[PENDING_LEVEL_KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    if (entityIds.isEmpty()) return CombatRuntimeResult(clearPending(state), null)

    val party = state.party.memberIds.mapNotNull { id ->
      val character = state.characters[id] ?: return@mapNotNull null
      if (character.presence != CharacterPresence.ACTIVE) return@mapNotNull null
      CombatantState(
        id = character.id,
        name = character.name,
        isEntity = false,
        stats = CombatProgression.read(character),
        baseDamage = CombatProfiles.partyBaseDamage(character.id)
      )
    }
    if (party.none { it.id.equals(KAI_ID, true) }) return CombatRuntimeResult(clearPending(state), null)

    val resolution = AutoTurnCombatEngine(random).resolve(
      encounterId = encounterId,
      partyInput = party,
      entityIds = entityIds,
      level = level
    )

    val updatedCharacters = state.characters.toMutableMap()
    resolution.party.forEach { fighter ->
      val character = updatedCharacters[fighter.id] ?: return@forEach
      updatedCharacters[fighter.id] = CombatProgression.write(character, fighter.stats)
    }
    val committed = clearPending(state.copy(characters = updatedCharacters)).copy(
      metadata = clearPending(state.copy(characters = updatedCharacters)).metadata + (LAST_ENCOUNTER_KEY to encounterId)
    )
    return CombatRuntimeResult(committed, resolution)
  }

  private fun clearPending(state: GameState): GameState = state.copy(
    metadata = state.metadata - PENDING_ID_KEY - PENDING_ENTITIES_KEY - PENDING_LEVEL_KEY
  )

  private fun pendingEntityIds(state: GameState): List<String> = state.metadata[PENDING_ENTITIES_KEY]
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

  fun encounterIds(candidate: JSONObject): List<String> {
    val encounter = candidate.optJSONObject("flags")
      ?.optJSONObject("lastRolls")
      ?.optJSONObject("entityEncounter")
      ?: return emptyList()
    val ids = encounter.optJSONArray("successIds") ?: return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until ids.length()) {
      val value = ids.optString(index, "").trim()
      if (value.isNotEmpty() && value !in result) result += value
    }
    return result
  }

  fun levelNumber(candidate: JSONObject, state: GameState): Int {
    val direct = candidate.optJSONObject("level")?.optInt("number", Int.MIN_VALUE) ?: Int.MIN_VALUE
    if (direct != Int.MIN_VALUE) return direct.coerceAtLeast(0)
    val stored = state.world["levelJson"]?.let {
      runCatching { JSONObject(it).optInt("number", 0) }.getOrDefault(0)
    } ?: 0
    return stored.coerceAtLeast(0)
  }
}'''

runtime = runtime[:start] + runtime_object + runtime[end:]
RUNTIME.write_text(runtime, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Core APIs: pending combat blocks normal turns; Start Combat commits exactly once;
#    New Game returns a projection from current GameState.initial() instead of legacy HTML data.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
old_commit = '''    val combatRuntime = CombatRuntime.resolveEncounter(committed.state, candidate, turnId)
    repository.save(combatRuntime.state)
    val synchronized = syncLegacy(candidate, combatRuntime.state, incrementTurn = false)
    synchronized.remove("combat")
    combatRuntime.resolution?.let { synchronized.put("combat", CombatJson.encode(it)) }
'''
new_commit = '''    val combatRuntime = CombatRuntime.resolveEncounter(committed.state, candidate, turnId)
    repository.save(combatRuntime.state)
    val synchronized = syncLegacy(candidate, combatRuntime.state, incrementTurn = false)
    synchronized.remove("combat")
    synchronized.remove("pendingCombat")
    CombatRuntime.pendingJson(combatRuntime.state)?.let { synchronized.put("pendingCombat", it) }
'''
facade = replace_once(facade, old_commit, new_commit, "stage combat instead of auto-resolve")

rule_anchor = '''    val state = loadOrMigrate(legacy)
    val turnId = nextTurnId(legacy, state)
'''
rule_new = '''    val state = loadOrMigrate(legacy)
    CombatRuntime.pendingJson(state)?.let { pendingCombat ->
      val locked = syncLegacy(legacy, state, incrementTurn = false)
      locked.remove("combat")
      locked.put("pendingCombat", pendingCombat)
      return response(true, locked, "combat_pending", "combat_pending", "Entity encounter đang chờ BẮT ĐẦU COMBAT.")
    }
    val turnId = nextTurnId(legacy, state)
'''
facade = replace_once(facade, rule_anchor, rule_new, "block rule actions during pending combat")

candidate_anchor = '''    val core = loadOrMigrate(before)
    val turnId = nextTurnId(before, core)
'''
candidate_new = '''    val core = loadOrMigrate(before)
    CombatRuntime.pendingJson(core)?.let { pendingCombat ->
      val locked = syncLegacy(before, core, incrementTurn = false)
      locked.remove("combat")
      locked.put("pendingCombat", pendingCombat)
      return response(true, locked, "combat_pending", "combat_pending", "Entity encounter đang chờ BẮT ĐẦU COMBAT.")
    }
    val turnId = nextTurnId(before, core)
'''
facade = replace_once(facade, candidate_anchor, candidate_new, "block validated actions during pending combat")

api_anchor = '''  fun currentCoreState(): String = GameStateCodec.encode(repository.load())
'''
api_block = '''  fun startPendingCombat(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val runtime = CombatRuntime.startPendingEncounter(state)
    val resolution = runtime.resolution
      ?: return response(false, legacy, "no_pending_combat", "combat_start_rejected")
    repository.save(runtime.state)
    val synchronized = syncLegacy(legacy, runtime.state, incrementTurn = false)
    synchronized.remove("pendingCombat")
    synchronized.put("combat", CombatJson.encode(resolution))
    return response(true, synchronized, null, "combat_started")
  }

  fun freshGameState(legacyTemplateJson: String): String {
    repository.clear()
    val fresh = GameState.initial()
    repository.save(fresh)
    val synchronized = syncLegacy(JSONObject(legacyTemplateJson), fresh, incrementTurn = false)
    synchronized.put("turn", 1)
    synchronized.remove("combat")
    synchronized.remove("pendingCombat")
    return synchronized.toString()
  }

  fun currentCoreState(): String = GameStateCodec.encode(repository.load())
'''
facade = replace_once(facade, api_anchor, api_block, "combat start and fresh-game APIs")
FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Android bridge for explicit Start Combat and canonical New Game bootstrap.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
bridge_anchor = '''    @JavascriptInterface public void clearCoreState() {
      if (gameCore != null) gameCore.clear();
    }

'''
bridge_block = '''    @JavascriptInterface public void clearCoreState() {
      if (gameCore != null) gameCore.clear();
    }

    @JavascriptInterface public String freshGameState(String legacyTemplateJson) {
      try {
        return gameCore != null ? gameCore.freshGameState(legacyTemplateJson) : legacyTemplateJson;
      } catch (Exception e) {
        return legacyTemplateJson;
      }
    }

    @JavascriptInterface public void startCombat(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject result = new JSONObject(gameCore.startPendingCombat(stateJson));
          if (!result.optBoolean("handled", false)) {
            emit("backroomError", "Không có combat đang chờ để bắt đầu.");
            return;
          }
          emit("backroomTurn", result.getJSONObject("state").toString());
        } catch (Exception e) {
          emit("backroomError", "Không thể bắt đầu combat: " + (e.getMessage() == null ? "unknown error" : e.getMessage()));
        }
      });
    }

'''
main = replace_once(main, bridge_anchor, bridge_block, "Android combat/new-game bridge")
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) UI: explicit encounter gate, slower readable combat playback, and canonical New Game.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")

# Fallback must also be current canon in case the Android bridge is unavailable.
legacy_inventory = '''  inventory:[
    {name:"White Wraith Magnum"},
    {name:"Blackblood Armor & linked modules"},
    {name:"Omnivault Ring / Nhẫn Vạn Tàng"}
  ],'''
current_inventory = '''  inventory:[
    {name:"SRU Assault Rifle MK19"},
    {name:"SRU-MK20"},
    {name:"Omnivault Ring / Nhẫn Vạn Tàng"}
  ],'''
if current_inventory not in html:
    html = replace_once(html, legacy_inventory, current_inventory, "current-canon HTML fallback inventory")

# New Game synchronously asks current Core for the fresh projection.
reset_old = '''    clearAuthoritativeCore();
    state=freshInitial();
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo và lưu ở Turn 1. Game State Core và Snapshot cũ đã được xóa.";'''
reset_new = '''    const template=freshInitial();
    try{
      if(window.Android&&typeof Android.freshGameState==="function")state=JSON.parse(Android.freshGameState(JSON.stringify(template)));
      else{clearAuthoritativeCore();state=template;}
    }catch(ignore){clearAuthoritativeCore();state=template;}
    state.turn=1;delete state.combat;delete state.pendingCombat;
    render();
    if(save())statusEl.textContent="NEW GAME đã tạo từ canon hiện hành và lưu ở Turn 1. Game State Core và Snapshot cũ đã được xóa.";'''
html = replace_once(html, reset_old, reset_new, "canonical New Game bootstrap")

# Slow the existing deterministic timeline enough to actually read it.
html = replace_once(html, "const token=++playbackToken;setCombatControls(true);await sleep(60);", "const token=++playbackToken;setCombatControls(true);await sleep(350);", "combat opening pacing")
html = replace_once(html, "focusTurn(event.actorId,event.enemyId);await sleep(220);continue;", "focusTurn(event.actorId,event.enemyId);await sleep(650);continue;", "turn-focus pacing")
old_delay = "      await sleep(event.kind==='ENTITY_ENTER'||event.kind==='ENTITY_DOWN'?300:240);"
new_delay = '''      const kind=String(event.kind||'');
      const delay=(kind==='ATTACK'||kind==='SKILL')?1450:(kind==='PASSIVE'||kind==='STATUS'||kind==='DEVIL_TRIGGER_ON'||kind==='DEVIL_TRIGGER_OFF')?1150:(kind==='ENTITY_ENTER'||kind==='ENTITY_DOWN'||kind==='COMBAT_END')?1300:900;
      await sleep(delay);'''
html = replace_once(html, old_delay, new_delay, "readable per-event pacing")

panel_anchor = '<div class="status" id="status"></div>'
panel_html = '<div class="pending-combat-panel" id="pendingCombatPanel" hidden></div>\n' + panel_anchor
html = replace_once(html, panel_anchor, panel_html, "pending combat panel")

pending_style = r'''<style id="pending-combat-style">
.pending-combat-panel{margin:0 10px 10px;padding:12px;border:1px solid #704f38;background:linear-gradient(180deg,#1b130e,#100d0a);box-shadow:inset 0 0 0 1px #2b211b}.pending-combat-panel[hidden]{display:none}.pending-combat-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.pending-combat-head strong{font-size:11px;letter-spacing:.14em;color:#dcb892}.pending-combat-head span{font-size:10px;color:#927d6b}.pending-combat-entities{font-size:13px;color:#e8ecef;line-height:1.45;margin-bottom:10px}.pending-combat-panel button{width:100%;background:#2a1d15;border-color:#8b664a;color:#fff1df;letter-spacing:.08em}.pending-combat-panel button:disabled{opacity:.55}
</style>'''
if 'id="pending-combat-style"' not in html:
    if html.count('</head>') != 1:
        raise RuntimeError('pending combat style head anchor missing')
    html = html.replace('</head>', pending_style + '\n</head>', 1)

pending_script = r'''<!-- PENDING_COMBAT_GATE_BEGIN -->
<script>
(function(){
  let startingCombat=false;
  function pendingPanel(){return document.getElementById('pendingCombatPanel')}
  function lockForPending(locked){
    const submit=document.getElementById('submit'),explore=document.getElementById('explore'),action=document.getElementById('action');
    if(submit)submit.disabled=!!locked;
    if(explore)explore.disabled=!!locked;
    if(action)action.readOnly=!!locked;
  }
  function renderPendingCombat(){
    const panel=pendingPanel();if(!panel)return;
    const pending=state&&state.pendingCombat;
    if(!pending||!pending.id){panel.hidden=true;panel.innerHTML='';return}
    lockForPending(true);
    const names=Array.isArray(pending.entityNames)&&pending.entityNames.length?pending.entityNames:(Array.isArray(pending.entityQueue)?pending.entityQueue:[]);
    panel.hidden=false;
    panel.innerHTML='<div class="pending-combat-head"><strong>ENTITY ENCOUNTER</strong><span>'+esc(String(pending.id))+'</span></div><div class="pending-combat-entities">'+esc(names.join(' • ')||'Entity hostile')+'</div><button type="button" id="startCombatButton">'+(startingCombat?'ĐANG KHỞI ĐỘNG…':'BẮT ĐẦU COMBAT')+'</button>';
    const button=document.getElementById('startCombatButton');if(button){button.disabled=startingCombat;button.addEventListener('click',startPendingCombat,{once:true})}
  }
  function startPendingCombat(){
    if(startingCombat)return;
    if(!state||!state.pendingCombat||!window.Android||typeof Android.startCombat!=='function'){
      const status=document.getElementById('status');if(status)status.textContent='Không tìm thấy Android Combat bridge.';return;
    }
    startingCombat=true;renderPendingCombat();
    const status=document.getElementById('status');if(status)status.textContent='Combat đang khởi động…';
    Android.startCombat(JSON.stringify(state));
  }
  window.startPendingCombat=startPendingCombat;
  const priorRender=window.render;
  if(typeof priorRender==='function')window.render=function(){const result=priorRender.apply(this,arguments);renderPendingCombat();return result};
  const priorTurn=window.backroomTurn;
  if(typeof priorTurn==='function')window.backroomTurn=function(){
    const result=priorTurn.apply(this,arguments);
    startingCombat=false;
    setTimeout(renderPendingCombat,0);
    return result;
  };
  const priorError=window.backroomError;
  window.backroomError=function(){startingCombat=false;if(typeof priorError==='function')priorError.apply(this,arguments);setTimeout(renderPendingCombat,0)};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',renderPendingCombat,{once:true});else renderPendingCombat();
})();
</script>
<!-- PENDING_COMBAT_GATE_END -->'''
if '<!-- PENDING_COMBAT_GATE_BEGIN -->' not in html:
    if html.count('</body>') != 1:
        raise RuntimeError('pending combat script body anchor missing')
    html = html.replace('</body>', pending_script + '\n</body>', 1)

for token in [
    'id="pendingCombatPanel"',
    'BẮT ĐẦU COMBAT',
    'Android.startCombat(JSON.stringify(state))',
    'Android.freshGameState(JSON.stringify(template))',
    "(kind==='ATTACK'||kind==='SKILL')?1450",
    'SRU Assault Rifle MK19',
    'SRU-MK20',
]:
    if token not in html:
        raise RuntimeError(f"combat gate/new-game UI contract missing: {token}")
INDEX.write_text(html, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Focused regression tests, generated after the exact final patch chain.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatStartGateGeneratedTest {
  private class ConstantRandom(private val value: Double) : CombatRandom {
    override fun nextDouble(): Double = value
  }

  private fun candidate(): JSONObject = JSONObject().apply {
    put("level", JSONObject().put("number", 0))
    put("flags", JSONObject().put("lastRolls", JSONObject().put(
      "entityEncounter",
      JSONObject().put("successIds", JSONArray().put("ENTITY.HOUND"))
    )))
  }

  @Test fun encounterStagesWithoutChangingCombatStats() {
    val state = GameState.initial()
    val before = CombatProgression.read(state.characters.getValue(KAI_ID))
    val staged = CombatRuntime.resolveEncounter(state, candidate(), "TURN_2", ConstantRandom(0.99))

    assertNull(staged.resolution)
    assertTrue(CombatRuntime.hasPending(staged.state))
    assertNotNull(CombatRuntime.pendingJson(staged.state))
    assertEquals(before, CombatProgression.read(staged.state.characters.getValue(KAI_ID)))
  }

  @Test fun startCombatConsumesPendingAndOnlyThenResolvesBattle() {
    val staged = CombatRuntime.resolveEncounter(GameState.initial(), candidate(), "TURN_2", ConstantRandom(0.99))
    val started = CombatRuntime.startPendingEncounter(staged.state, ConstantRandom(0.99))

    assertNotNull(started.resolution)
    assertFalse(CombatRuntime.hasPending(started.state))
    assertNull(CombatRuntime.pendingJson(started.state))
    assertEquals(CombatOutcome.VICTORY, started.resolution!!.outcome)
    assertTrue(started.resolution!!.defeatedEntities.contains("ENTITY.HOUND"))
  }

  @Test fun currentFreshCanonUsesSruEquipmentNames() {
    assertEquals("SRU Assault Rifle MK19", KaiStartingEquipment.WEAPON_NAME)
    assertEquals("SRU-MK20", KaiStartingEquipment.ARMOR_NAME)
  }
}
''', encoding="utf-8")

for path, tokens in {
    RUNTIME: ['PENDING_ID_KEY', 'fun startPendingEncounter(', 'fun pendingJson('],
    FACADE: ['fun startPendingCombat(', 'fun freshGameState(', '"combat_pending"'],
    MAIN: ['@JavascriptInterface public void startCombat(', '@JavascriptInterface public String freshGameState('],
    INDEX: ['PENDING_COMBAT_GATE_BEGIN', 'BẮT ĐẦU COMBAT', '?1450'],
}.items():
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise RuntimeError(f"final combat-start contract missing in {path.name}: {token}")

print("Combat start gate applied: pending encounter, explicit Start Combat, readable pacing, and canonical New Game bootstrap.")
