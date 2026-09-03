from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
STATE = CORE / "GameState.kt"
CODEC = CORE / "GameStateCodec.kt"
COMBAT = CORE / "CombatRuntime.kt"
FACADE = CORE / "GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
AN_TEST = TESTS / "AnNhienFollowerTest.kt"
PARTY_TEST = TESTS / "PartyTurnCombatTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Seven-member Party authority, including old-save normalization.
state = STATE.read_text(encoding="utf-8")
state = replace_once(
    state,
    "data class PartyState(val leaderId: String = KAI_ID, val memberIds: List<String> = listOf(KAI_ID), val maxMembers: Int = 4)",
    "data class PartyState(val leaderId: String = KAI_ID, val memberIds: List<String> = listOf(KAI_ID), val maxMembers: Int = 7)",
    "PartyState seven-member default",
)
STATE.write_text(state, encoding="utf-8")

codec = CODEC.read_text(encoding="utf-8")
codec = replace_once(
    codec,
    '      maxMembers = partyJson.optInt("maxMembers", 4).coerceAtLeast(1)',
    '      maxMembers = 7',
    "PartyState save migration",
)
CODEC.write_text(codec, encoding="utf-8")

if AN_TEST.is_file():
    test = AN_TEST.read_text(encoding="utf-8")
    test = test.replace("assertEquals(4, state.party.maxMembers)", "assertEquals(7, state.party.maxMembers)")
    AN_TEST.write_text(test, encoding="utf-8")


# CombatRuntime remains Entity/boss authority; expose one narrow Party damage adapter.
combat = COMBAT.read_text(encoding="utf-8")
party_damage = r'''  fun applyPartyTurnDamage(
    state: GameState,
    actorId: String,
    damage: Int,
    actionName: String
  ): Resolution {
    val current = active(state) ?: return Resolution(state, handled = false)
    val dealt = min(current.entityHp, damage.coerceAtLeast(0))
    val hp = max(0, current.entityHp - dealt)
    val updated = current.copy(
      entityHp = hp,
      entityCondition = condition(hp, current.entityMaxHp)
    )
    if (hp <= 0) {
      val persisted = encode(
        state,
        updated.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED)
      )
      val cleared = EntityLootEngine.onDefeat(clearCombatOnly(persisted), current.encounterId, lootRng)
      return Resolution(
        cleared,
        handled = true,
        reply = "$actionName gây $dealt DMG. ${current.entityName} đã bị tiêu diệt.",
        entityDestroyed = true
      )
    }
    val next = encode(state, updated)
    return Resolution(
      next,
      handled = true,
      reply = "$actionName gây $dealt DMG. ${current.entityName}: $hp/${current.entityMaxHp} HP."
    )
  }

'''
if "fun applyPartyTurnDamage(" not in combat:
    pos = combat.find("  fun toJson(state: GameState): JSONObject?")
    if pos < 0:
        raise RuntimeError("CombatRuntime toJson boundary missing")
    combat = combat[:pos] + party_damage + combat[pos:]
COMBAT.write_text(combat, encoding="utf-8")


# Sequential Party controller with one shared AP pool.
(CORE / "PartyTurnCombat.kt").write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object PartyTurnCombat {
  const val MAX_AP = 7
  private const val PREFIX = "partyCombat."
  private const val AP = "${PREFIX}ap"
  private const val ACTOR_INDEX = "${PREFIX}actorIndex"
  private const val ROUND = "${PREFIX}round"

  data class Actor(val id: String, val name: String, val avatarRef: String?)

  fun init(state: GameState): GameState {
    if (CombatRuntime.active(state) == null) return clear(state)
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    metadata[AP] = "0"
    metadata[ACTOR_INDEX] = "0"
    metadata[ROUND] = "1"
    return state.copy(party = state.party.copy(maxMembers = 7), metadata = metadata)
  }

  fun resolve(state: GameState, actionKind: String, action: String): CombatRuntime.Resolution {
    if (CombatRuntime.active(state) == null) {
      return CombatRuntime.Resolution(clear(state), handled = false)
    }

    if (action == "PARTY_TURN_RUN") {
      val result = CombatRuntime.resolve(state, actionKind, "bỏ chạy")
      return if (result.entityDestroyed || result.escaped) result.copy(state = clear(result.state)) else result
    }

    val actor = currentActor(state)
      ?: return CombatRuntime.Resolution(state, handled = true, reply = "Không còn thành viên Party nào có thể hành động.")
    val beforeAp = ap(state)

    return when {
      action == "PARTY_TURN_ATK" -> {
        val damage = max(1, CharacterStatEngine.weaponDamage(state, actor.id))
        val hit = CombatRuntime.applyPartyTurnDamage(state, actor.id, damage, "${actor.name} ATK")
        if (hit.entityDestroyed) hit.copy(state = clear(hit.state))
        else finishActor(withAp(hit.state, beforeAp + 1), actor, hit.reply + " AP +1.")
      }

      action == "PARTY_TURN_DEFEND" ->
        finishActor(withAp(state, beforeAp + 1), actor, "${actor.name} DEFEND. AP +1.")

      action.startsWith("PARTY_TURN_SKILL::") -> {
        val skillName = action.removePrefix("PARTY_TURN_SKILL::").trim()
        val skill = CompanionSkillCatalog.forCharacter(actor.id).firstOrNull { it.name == skillName }
          ?: return CombatRuntime.Resolution(state, handled = true, reply = "Skill không hợp lệ cho ${actor.name}.")
        val cost = skillCost(skill.kind)
        if (beforeAp < cost) {
          return CombatRuntime.Resolution(
            state,
            handled = true,
            reply = "${actor.name} không đủ AP cho ${skill.name}: cần $cost, hiện có $beforeAp/$MAX_AP."
          )
        }

        var next = withAp(state, beforeAp - cost)
        val damage = skillDamage(next, actor.id, skill)
        if (damage > 0) {
          val hit = CombatRuntime.applyPartyTurnDamage(next, actor.id, damage, "${actor.name} dùng ${skill.name}")
          if (hit.entityDestroyed) return hit.copy(state = clear(hit.state))
          next = hit.state
          finishActor(next, actor, hit.reply + " AP -$cost.")
        } else {
          finishActor(next, actor, "${actor.name} dùng ${skill.name}. AP -$cost.")
        }
      }

      else -> CombatRuntime.Resolution(
        state,
        handled = true,
        reply = "Hành động combat không hợp lệ cho lượt của ${actor.name}."
      )
    }
  }

  fun json(state: GameState): JSONObject? {
    if (CombatRuntime.active(state) == null) return null
    val list = actors(state)
    val actor = currentActor(state)
    return JSONObject().apply {
      put("ap", ap(state))
      put("maxAp", MAX_AP)
      put("round", round(state))
      put("actorIndex", actorIndex(state))
      put("actorCount", list.size)
      put("actorId", actor?.id ?: JSONObject.NULL)
      put("actorName", actor?.name ?: JSONObject.NULL)
      put("actorAvatar", actor?.avatarRef ?: JSONObject.NULL)
      put("skills", JSONArray().apply {
        if (actor != null) {
          selectableSkills(actor.id).forEach { skill ->
            put(JSONObject().apply {
              put("name", skill.name)
              put("cost", skillCost(skill.kind))
              put("kind", skill.kind)
            })
          }
        }
      })
    }
  }

  private fun actors(state: GameState): List<Actor> {
    val ordered = (listOf(KAI_ID) + state.party.memberIds.filter { it != KAI_ID }).distinct().take(7)
    return ordered.mapNotNull { id ->
      val character = state.characters[id] ?: return@mapNotNull null
      val currentHp = character.vitalState.currentHp
      val nonCombat = character.metadata["nonCombat"]?.equals("true", ignoreCase = true) == true
      if (character.presence != CharacterPresence.ACTIVE || currentHp <= 0 || nonCombat) null
      else Actor(id, character.name, character.avatarRef)
    }
  }

  private fun currentActor(state: GameState): Actor? {
    val list = actors(state)
    if (list.isEmpty()) return null
    return list[actorIndex(state).coerceIn(0, list.lastIndex)]
  }

  private fun finishActor(state: GameState, actor: Actor, reply: String): CombatRuntime.Resolution {
    val list = actors(state)
    if (list.isEmpty()) return CombatRuntime.Resolution(state, handled = true, reply = reply)
    val found = list.indexOfFirst { it.id == actor.id }
    val index = if (found >= 0) found else actorIndex(state)

    if (index + 1 < list.size) {
      val next = withActorIndex(state, index + 1)
      val nextActor = actors(next).getOrNull(index + 1)
      return CombatRuntime.Resolution(
        next,
        handled = true,
        reply = if (nextActor == null) reply else "$reply Lượt kế: ${nextActor.name}."
      )
    }

    var next = withActorIndex(state, 0)
    next = withRound(next, round(state) + 1)
    val enemy = CombatRuntime.resolve(next, "EXECUTE", "phòng thủ")
    if (!enemy.handled) return CombatRuntime.Resolution(next, handled = true, reply = reply)

    val ended = enemy.entityDestroyed || enemy.escaped || CombatRuntime.active(enemy.state) == null
    return CombatRuntime.Resolution(
      state = if (ended) clear(enemy.state) else withActorIndex(enemy.state, 0),
      handled = true,
      reply = listOf(reply, enemy.reply).filter { it.isNotBlank() }.joinToString(" "),
      entityDestroyed = enemy.entityDestroyed,
      escaped = enemy.escaped
    )
  }

  private fun selectableSkills(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter {
      val kind = it.kind.uppercase()
      kind != "PASSIVE" && kind != "STATE"
    }

  private fun skillCost(kind: String): Int {
    val upper = kind.uppercase()
    return if ("ULTIMATE" in upper || upper == "UTM") 2 else 1
  }

  private fun skillDamage(state: GameState, actorId: String, skill: CharacterSkillDefinition): Int {
    if (!skill.effect.contains("DMG", ignoreCase = true)) return 0
    val weapon = max(1, CharacterStatEngine.weaponDamage(state, actorId))
    val match = Regex("(\\d+)\\s*%\\s*(?:Weapon\\s*)?DMG", RegexOption.IGNORE_CASE).find(skill.effect)
    val percent = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
    return max(1, weapon * percent / 100)
  }

  private fun ap(state: GameState): Int =
    state.metadata[AP]?.toIntOrNull()?.coerceIn(0, MAX_AP) ?: 0

  private fun actorIndex(state: GameState): Int =
    state.metadata[ACTOR_INDEX]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

  private fun round(state: GameState): Int =
    state.metadata[ROUND]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

  private fun withAp(state: GameState, value: Int): GameState =
    state.copy(metadata = state.metadata + (AP to value.coerceIn(0, MAX_AP).toString()))

  private fun withActorIndex(state: GameState, value: Int): GameState =
    state.copy(metadata = state.metadata + (ACTOR_INDEX to value.coerceAtLeast(0).toString()))

  private fun withRound(state: GameState, value: Int): GameState =
    state.copy(metadata = state.metadata + (ROUND to value.coerceAtLeast(1).toString()))

  private fun clear(state: GameState): GameState =
    state.copy(metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) })
}
''', encoding="utf-8")


# Route start/action/projection through the Party controller without replacing the rest of facade logic.
facade = FACADE.read_text(encoding="utf-8")
facade, count = re.subn(
    r'(\s+)val next = CombatRuntime\.start\(current, entityKey\)',
    r'\1val next = PartyTurnCombat.init(CombatRuntime.start(current, entityKey))',
    facade,
    count=1,
)
if count != 1:
    raise RuntimeError(f"GameCoreFacade combat start route: expected 1, found {count}")

facade = replace_once(
    facade,
    "    var resolution = CombatRuntime.resolve(current, actionKind, action)",
    "    var resolution = PartyTurnCombat.resolve(current, actionKind, action)",
    "GameCoreFacade Party combat resolver",
)

projection = '    CombatRuntime.toJson(state)?.let { output.put("combat", it) } ?: output.remove("combat")'
projection_new = '''    CombatRuntime.toJson(state)?.let { combat ->
      PartyTurnCombat.json(state)?.let { combat.put("partyTurn", it) }
      output.put("combat", combat)
    } ?: output.remove("combat")'''
facade = replace_once(facade, projection, projection_new, "Party combat JSON projection")
FACADE.write_text(facade, encoding="utf-8")


# Final mobile UI override. Existing historical action bar stays as compatibility substrate.
html = INDEX.read_text(encoding="utf-8")
ui = r'''
<style id="partyTurnCombatStyle">
#partyTurnCombat{display:none;margin-top:6px}
#partyTurnCombat.active{display:block}
.party-turn-strip{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:4px 0 6px;font:700 11px/1.2 system-ui,sans-serif}
.party-turn-actor{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.party-turn-ap{white-space:nowrap;border:1px solid #444b52;padding:3px 6px;border-radius:4px}
.party-turn-actions{display:grid;grid-template-columns:1fr 1fr 1fr auto;gap:6px}
.party-turn-actions button,.party-skill-list button{min-height:38px;border:1px solid #4a5259;background:#11161a;color:#edf1f4;border-radius:5px;font:800 11px system-ui,sans-serif}
.party-turn-actions button:disabled,.party-skill-list button:disabled{opacity:.42}
#partySkillPopup{display:none;position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.68);align-items:flex-end;justify-content:center}
#partySkillPopup.open{display:flex}
.party-skill-sheet{width:min(100%,520px);max-height:62vh;overflow:auto;background:#101418;border:1px solid #4a5259;padding:10px}
.party-skill-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px;font:800 12px system-ui,sans-serif}
.party-skill-list{display:grid;gap:6px}
.party-skill-list button{display:flex;justify-content:space-between;align-items:center;padding:8px 10px;text-align:left}
.party-skill-close{width:34px}
.snapshot-party-actor{position:absolute;right:4px;bottom:0;max-width:34%;max-height:54%;object-fit:contain;z-index:3;pointer-events:none;border-radius:6px}
</style>
<div id="partySkillPopup" aria-hidden="true">
  <div class="party-skill-sheet">
    <div class="party-skill-head"><span>SKILL</span><button class="party-skill-close" type="button" onclick="closePartySkillPopup()">×</button></div>
    <div id="partySkillList" class="party-skill-list"></div>
  </div>
</div>
<script>
/* PARTY_TURN_BASED_AP_V1 */
(function(){
  function combat(){return state&&state.combat&&state.combat.active===true?state.combat:null}
  function turn(){var c=combat();return c&&c.partyTurn?c.partyTurn:null}
  function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
  function send(action){if(typeof submitAction==='function')submitAction('EXECUTE',action)}
  function ensure(){
    var old=document.getElementById('combatActionBar');if(old)old.style.display='none';
    var box=document.getElementById('partyTurnCombat');if(box)return box;
    box=document.createElement('section');box.id='partyTurnCombat';
    var target=document.querySelector('.actions')||document.getElementById('log')||document.body;
    if(target.parentNode)target.parentNode.insertBefore(box,target);else document.body.appendChild(box);
    return box;
  }
  window.closePartySkillPopup=function(){
    var p=document.getElementById('partySkillPopup');if(p){p.classList.remove('open');p.setAttribute('aria-hidden','true')}
  };
  window.openPartySkillPopup=function(){
    var t=turn(),p=document.getElementById('partySkillPopup'),list=document.getElementById('partySkillList');
    if(!t||!p||!list)return;list.innerHTML='';
    var skills=Array.isArray(t.skills)?t.skills:[];
    if(!skills.length)list.innerHTML='<div>Không có Skill khả dụng cho lượt này.</div>';
    skills.forEach(function(s){
      var b=document.createElement('button'),cost=Number(s.cost||1),ap=Number(t.ap||0);
      b.type='button';b.disabled=ap<cost;
      b.innerHTML='<span>'+esc(s.name)+'</span><span>AP -'+cost+'</span>';
      b.onclick=function(){closePartySkillPopup();send('PARTY_TURN_SKILL::'+String(s.name||''))};
      list.appendChild(b);
    });
    p.classList.add('open');p.setAttribute('aria-hidden','false');
  };
  function actorOverlay(t){
    var snap=document.getElementById('snapshot');if(!snap)return;
    var old=snap.querySelector('.snapshot-party-actor');if(old)old.remove();
    if(!t||!t.actorAvatar)return;snap.style.position='relative';
    var img=document.createElement('img');img.className='snapshot-party-actor';
    var ref=String(t.actorAvatar||'').replace(/^\/+/,'');
    img.src=ref.indexOf('file:')===0?ref:'file:///android_asset/'+ref;
    img.alt=String(t.actorName||'Party actor');snap.appendChild(img);
  }
  window.renderPartyTurnCombat=function(){
    var box=ensure(),t=turn();
    if(!t){box.className='';box.innerHTML='';actorOverlay(null);closePartySkillPopup();return}
    box.className='active';
    box.innerHTML='<div class="party-turn-strip"><span class="party-turn-actor">TURN '+Number(t.round||1)+' · '+esc(t.actorName||'PARTY')+'</span><span class="party-turn-ap">AP '+Number(t.ap||0)+'/'+Number(t.maxAp||7)+'</span></div>'+
      '<div class="party-turn-actions">'+
      '<button type="button" data-party-action="atk">ATK<br><small>AP +1</small></button>'+
      '<button type="button" data-party-action="def">DEFEND<br><small>AP +1</small></button>'+
      '<button type="button" data-party-action="skill">SKILL</button>'+
      '<button type="button" data-party-action="run">RUN</button></div>';
    box.querySelector('[data-party-action="atk"]').onclick=function(){send('PARTY_TURN_ATK')};
    box.querySelector('[data-party-action="def"]').onclick=function(){send('PARTY_TURN_DEFEND')};
    box.querySelector('[data-party-action="skill"]').onclick=openPartySkillPopup;
    box.querySelector('[data-party-action="run"]').onclick=function(){send('PARTY_TURN_RUN')};
    actorOverlay(t);
  };
  var oldRender=window.render;
  if(typeof oldRender==='function')window.render=function(){oldRender.apply(this,arguments);window.renderPartyTurnCombat()};
  var oldTurn=window.backroomTurn;
  if(typeof oldTurn==='function')window.backroomTurn=function(json){oldTurn.call(this,json);window.renderPartyTurnCombat()};
  setTimeout(window.renderPartyTurnCombat,0);
})();
</script>
'''
if "PARTY_TURN_BASED_AP_V1" not in html:
    if "</body>" not in html:
        raise RuntimeError("index.html closing body missing")
    html = html.replace("</body>", ui + "\n</body>", 1)

html = html.replace(
    "const max=Math.max(1,Math.min(4,Number(state&&state.partyDetails&&state.partyDetails.maxMembers)||4));",
    "const max=Math.max(1,Math.min(7,Number(state&&state.partyDetails&&state.partyDetails.maxMembers)||7));",
)
INDEX.write_text(html, encoding="utf-8")


# Focused regressions.
PARTY_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PartyTurnCombatTest {
  @Test fun freshPartyCapacityIsSevenAndKaiOpensCombat() {
    var state = GameState.initial()
    assertEquals(7, state.party.maxMembers)
    state = PartyTurnCombat.init(CombatRuntime.start(state, "hound"))
    val json = PartyTurnCombat.json(state)!!
    assertEquals(KAI_ID, json.getString("actorId"))
    assertEquals(0, json.getInt("ap"))
    assertEquals(7, json.getInt("maxAp"))
  }

  @Test fun attackAndDefendShareOneBoundedApPool() {
    var state = PartyTurnCombat.init(CombatRuntime.start(GameState.initial(), "hound"))
    val first = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_DEFEND")
    assertTrue(first.handled)
    state = first.state
    if (CombatRuntime.active(state) != null) assertEquals(1, PartyTurnCombat.json(state)!!.getInt("ap"))
    val second = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_ATK")
    assertTrue(second.handled)
    if (CombatRuntime.active(second.state) != null) assertEquals(2, PartyTurnCombat.json(second.state)!!.getInt("ap"))
  }

  @Test fun skillProjectionIsCompactAndInsufficientApFailsClosed() {
    val state = PartyTurnCombat.init(CombatRuntime.start(GameState.initial(), "hound"))
    val skills = PartyTurnCombat.json(state)!!.getJSONArray("skills")
    if (skills.length() > 0) {
      val skill = skills.getJSONObject(0)
      assertTrue(skill.has("name"))
      assertTrue(skill.has("cost"))
      assertFalse(skill.has("effect"))
      assertFalse(skill.has("description"))
      val result = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_SKILL::" + skill.getString("name"))
      assertTrue(result.handled)
      assertTrue(result.reply.contains("không đủ AP"))
    }
  }
}
''', encoding="utf-8")


checks = {
    "GameState.kt": (STATE, ["maxMembers: Int = 7"]),
    "CombatRuntime.kt": (COMBAT, ["fun applyPartyTurnDamage("]),
    "GameCoreFacade.kt": (FACADE, ["PartyTurnCombat.init(CombatRuntime.start", "PartyTurnCombat.resolve(current", 'combat.put("partyTurn", it)']),
    "index.html": (INDEX, ["PARTY_TURN_BASED_AP_V1", "AP +1", "PARTY_TURN_SKILL::"]),
    "PartyTurnCombat.kt": (CORE / "PartyTurnCombat.kt", ["const val MAX_AP = 7", "PARTY_TURN_ATK", "PARTY_TURN_DEFEND"]),
}
for label, (path, markers) in checks.items():
    text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise RuntimeError(f"{label}: missing final marker {marker}")

print("Turn-based Party combat finalized: Kai-first sequential actors, seven Party slots, shared AP 0/7, compact Skill picker, and existing CombatRuntime Entity phase authority.")
