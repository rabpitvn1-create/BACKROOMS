from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
PARTY = CORE / "PartyTurnCombat.kt"
FACADE = CORE / "GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
PARTY_TEST = TESTS / "PartyTurnCombatInterleavedTest.kt"
PROTOCOL_TEST = ROOT / "test-combat-action-protocol.cjs"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Final CombatRuntime already contains the repository's authoritative player and
# Entity effects. This patch serializes actor ownership instead of duplicating
# skill math in PartyTurnCombat.
combat = COMBAT.read_text(encoding="utf-8")

resolution_old = '''  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false
  )
'''
resolution_new = '''  data class Resolution(
    val state: GameState,
    val handled: Boolean,
    val reply: String = "",
    val entityDestroyed: Boolean = false,
    val escaped: Boolean = false,
    val committed: Boolean = true,
    val rejectionReason: String? = null,
    val replayed: Boolean = false
  )
'''
combat = replace_once(combat, resolution_old, resolution_new, "Combat resolution commit metadata")

actor_helper_anchor = '  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {\n'
actor_helpers = r'''  // PARTY_INTERLEAVED_COMBAT_V1: one serialized player actor owns each CombatRuntime event.
  // Entity resolution happens inside that same event, so eventCounter advances exactly once
  // per completed actor-action/Entity-response pair.
  private const val PARTY_TURN_ACTOR_CONTEXT_KEY = "partyCombat.actorContext"

  private fun partyTurnActorId(state: GameState): String? =
    state.metadata[PARTY_TURN_ACTOR_CONTEXT_KEY]?.trim()?.takeIf { it.isNotEmpty() }

  private fun partyTurnActorMatches(state: GameState, characterId: String): Boolean {
    val actorId = partyTurnActorId(state) ?: return true
    return actorId == characterId
  }

  fun partyTurnActorActionLocked(state: GameState, characterId: String): Boolean {
    if (characterId !in state.party.memberIds) return true
    val character = state.characters[characterId] ?: return true
    if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return true
    if (violetWardenActionLocked(state, characterId) || kaiDevilWithinActionLocked(state, characterId)) return true

    val eventCounter = active(state)?.eventCounter ?: 0
    val statusLocked = character.statusIds.asSequence().mapNotNull(state.statuses::get).any { effect ->
      val token = (effect.type + " " + effect.id).uppercase().replace('-', '_').replace(' ', '_')
      val incapacitating = listOf("STUN", "UNCONSCIOUS", "KNOCKED_OUT", "PARALYZ").any(token::contains)
      incapacitating && (effect.metadata["expiresEvent"]?.toIntOrNull()?.let { eventCounter < it } ?: true)
    }
    if (statusLocked) return true

    val metadata = character.metadata.mapKeys { it.key.trim().lowercase() }
    return metadata["stunned"].equals("true", true) ||
      metadata["unconscious"].equals("true", true) ||
      metadata["knockedout"].equals("true", true) ||
      metadata["paralyzed"].equals("true", true)
  }

'''
if "PARTY_INTERLEAVED_COMBAT_V1" not in combat:
    combat = replace_once(combat, actor_helper_anchor, actor_helpers + actor_helper_anchor, "Party actor scope helpers")

# Direct Entity attacks use the just-completed actor. The full roster helper is
# intentionally preserved for existing AoE and explicit priority-target skills.
budget_marker = "  // ENTITY_PARTY_ACTION_BUDGET_V1: direct Entity targets only.\n"
budget_start = combat.find(budget_marker)
helper_start = combat.find("  private fun entityCombatActionTargets(", budget_start)
helper_end = combat.find("\n\n", helper_start)
if budget_start < 0 or helper_start < 0 or helper_end < 0:
    raise RuntimeError("Interleaved combat: Entity target authority helper missing")
direct_helper = r'''

  private fun entityDirectActionTargets(state: GameState): List<String> {
    val targets = entityCombatActionTargets(state)
    val actorId = partyTurnActorId(state) ?: return targets
    return targets.filter { it == actorId }
  }
'''
if "private fun entityDirectActionTargets(" not in combat:
    combat = combat[:helper_end] + direct_helper + combat[helper_end:]

combat = replace_once(
    combat,
    '      val entityTargets = entityCombatActionTargets(resolvedState)\n      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."\n',
    '      val entityTargets = entityDirectActionTargets(resolvedState)\n      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per completed Party actor turn."\n',
    "Ordinary Entity direct target scope",
)
if "        val scp173ActionTargets = entityCombatActionTargets(resolvedState)\n" in combat:
    combat = combat.replace(
        "        val scp173ActionTargets = entityCombatActionTargets(resolvedState)\n",
        "        val scp173ActionTargets = entityDirectActionTargets(resolvedState)\n",
        1,
    )

scp_target_old = '''  private fun scp173TargetId(state: GameState): String? {
    val live = entityCombatActionTargets(state)
    return if (KAI_ID in live) KAI_ID else live.firstOrNull()
  }
'''
scp_target_new = '''  private fun scp173TargetId(state: GameState): String? {
    val direct = entityDirectActionTargets(state)
    if (direct.isNotEmpty()) return direct.first()
    val live = entityCombatActionTargets(state)
    return if (KAI_ID in live) KAI_ID else live.firstOrNull()
  }
'''
if scp_target_old in combat:
    combat = replace_once(combat, scp_target_old, scp_target_new, "SCP-173 direct target scope")

# Kai base ATTACK: keep shared roll/hitChance variables in scope for follower
# code, but suppress Kai's personal hit/miss branch when another actor owns it.
kai_hit_anchor = '''        if (violetWardenKaiActionLocked) {
          log += "Violet Warden STUN: Kai mất lượt hành động cá nhân; các thành viên ACTIVE khác vẫn tiếp tục lệnh TẤN CÔNG."
'''
kai_hit_new = '''        if (!partyTurnActorMatches(resolvedState, KAI_ID)) {
          // Another serialized Party member owns this ATTACK event.
        } else if (partyTurnActorActionLocked(resolvedState, KAI_ID)) {
          log += "Kai đang bị choáng hoặc mất khả năng hành động nên không thực hiện được đòn đánh."
        } else if (violetWardenKaiActionLocked) {
          log += "Violet Warden STUN: Kai mất lượt hành động cá nhân."
'''
combat = replace_once(combat, kai_hit_anchor, kai_hit_new, "Kai serialized base attack")

lucia_active_old = '''        val luciaActive = LUCIA_ID in resolvedState.party.memberIds &&
          lucia?.presence == CharacterPresence.ACTIVE && (lucia.vitalState.currentHp > 0)
'''
lucia_active_new = '''        val luciaActive = partyTurnActorMatches(resolvedState, LUCIA_ID) &&
          !partyTurnActorActionLocked(resolvedState, LUCIA_ID) &&
          LUCIA_ID in resolvedState.party.memberIds &&
          lucia?.presence == CharacterPresence.ACTIVE && (lucia.vitalState.currentHp > 0)
'''
combat = replace_once(combat, lucia_active_old, lucia_active_new, "Lucia serialized base attack")

for old, new, label in (
    (
        "        val irisPartyAttack = activePartyCharacter(resolvedState, IRIS_ID)\n",
        "        val irisPartyAttack = activePartyCharacter(resolvedState, IRIS_ID)?.takeIf { partyTurnActorMatches(resolvedState, IRIS_ID) }\n",
        "Iris serialized base attack",
    ),
    (
        "        val syvialPartyAttack = activePartyCharacter(resolvedState, SYVIAL_ID)\n",
        "        val syvialPartyAttack = activePartyCharacter(resolvedState, SYVIAL_ID)?.takeIf { partyTurnActorMatches(resolvedState, SYVIAL_ID) }\n",
        "Syvial serialized base attack",
    ),
):
    combat = replace_once(combat, old, new, label)

# Automatic offensive skills stay in their existing resolver/state model, but
# fire only during their owner's serialized ATTACK event.
kai_skill_start = combat.find("    // PARTY_ATTACK_GCO_GATE_V1\n")
iris_skill_start = combat.find("    if (irisActive && c.entityHp > 0) {\n", kai_skill_start)
if kai_skill_start < 0 or iris_skill_start < 0:
    raise RuntimeError("Interleaved combat: Kai automatic skill section missing")
kai = combat[kai_skill_start:iris_skill_start]
kai = kai.replace(
    "    if (intent == Intent.ATTACK && !violetWardenKaiActionLocked) {\n",
    "    if (partyTurnActorMatches(resolvedState, KAI_ID) && !partyTurnActorActionLocked(resolvedState, KAI_ID) && intent == Intent.ATTACK && !violetWardenKaiActionLocked) {\n",
    1,
)
kai = kai.replace(
    "val isGuiltyCrownTurn = intent == Intent.ATTACK &&",
    "val isGuiltyCrownTurn = partyTurnActorMatches(resolvedState, KAI_ID) && !partyTurnActorActionLocked(resolvedState, KAI_ID) && intent == Intent.ATTACK &&",
    1,
)
kai = kai.replace(
    "if (intent == Intent.ATTACK && !isGuiltyCrownTurn",
    "if (partyTurnActorMatches(resolvedState, KAI_ID) && !partyTurnActorActionLocked(resolvedState, KAI_ID) && intent == Intent.ATTACK && !isGuiltyCrownTurn",
)
kai = kai.replace(
    "if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn",
    "if (partyTurnActorMatches(resolvedState, KAI_ID) && !partyTurnActorActionLocked(resolvedState, KAI_ID) && (intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn",
)
combat = combat[:kai_skill_start] + kai + combat[iris_skill_start:]

replacements = (
    (
        "      val irisUltimate = intent == Intent.ATTACK && c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0\n",
        "      val irisUltimate = partyTurnActorMatches(resolvedState, IRIS_ID) && intent == Intent.ATTACK && c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0\n",
        "Iris ultimate owner gate",
    ),
    (
        "      } else if (intent == Intent.ATTACK) {\n        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30",
        "      } else if (partyTurnActorMatches(resolvedState, IRIS_ID) && intent == Intent.ATTACK) {\n        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30",
        "Iris auto owner gate",
    ),
    (
        "      val syvialUltimate = intent == Intent.ATTACK && syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0\n",
        "      val syvialUltimate = partyTurnActorMatches(resolvedState, SYVIAL_ID) && intent == Intent.ATTACK && syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0\n",
        "Syvial ultimate owner gate",
    ),
    (
        "      } else if (intent == Intent.ATTACK) {\n        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30",
        "      } else if (partyTurnActorMatches(resolvedState, SYVIAL_ID) && intent == Intent.ATTACK) {\n        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30",
        "Syvial auto owner gate",
    ),
    (
        "    val luciaFullAutoActive = activePartyCharacter(resolvedState, LUCIA_ID) != null\n",
        "    val luciaFullAutoActive = partyTurnActorMatches(resolvedState, LUCIA_ID) && activePartyCharacter(resolvedState, LUCIA_ID) != null\n",
        "Lucia full-auto owner gate",
    ),
)
for old, new, label in replacements:
    combat = replace_once(combat, old, new, label)

# Too Young To Die is installed later in the legacy chain than Full Auto.
too_young_old = "    val luciaTooYoungCharacter = activePartyCharacter(resolvedState, LUCIA_ID)\n"
if too_young_old in combat:
    combat = replace_once(
        combat,
        too_young_old,
        "    val luciaTooYoungCharacter = activePartyCharacter(resolvedState, LUCIA_ID)?.takeIf { partyTurnActorMatches(resolvedState, LUCIA_ID) }\n",
        "Lucia Too Young owner gate",
    )

for marker in (
    "PARTY_INTERLEAVED_COMBAT_V1",
    "private fun entityDirectActionTargets(",
    "partyTurnActorMatches(resolvedState, KAI_ID)",
    "partyTurnActorMatches(resolvedState, IRIS_ID)",
    "partyTurnActorMatches(resolvedState, SYVIAL_ID)",
    "partyTurnActorMatches(resolvedState, LUCIA_ID)",
):
    if marker not in combat:
        raise RuntimeError("Interleaved combat runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")


PARTY.write_text('package com.rabpit.backroom.core\n\nimport org.json.JSONArray\nimport org.json.JSONObject\n\nobject PartyTurnCombat {\n  const val MAX_AP = 7\n  private const val PREFIX = "partyCombat."\n  private const val AP = "${PREFIX}ap"\n  private const val ACTOR_INDEX = "${PREFIX}actorIndex"\n  private const val ROUND = "${PREFIX}round"\n  private const val ACTION_SERIAL = "${PREFIX}actionSerial"\n  private const val ACTOR_CONTEXT = "${PREFIX}actorContext"\n\n  private const val REPLAY_PREFIX = "combatReplay."\n  private const val REPLAY_REQUEST = "${REPLAY_PREFIX}requestId"\n  private const val REPLAY_REPLY = "${REPLAY_PREFIX}reply"\n  private const val REPLAY_ACTION = "${REPLAY_PREFIX}displayAction"\n\n  data class Actor(val id: String, val name: String, val avatarRef: String?)\n\n  fun init(state: GameState): GameState {\n    if (CombatRuntime.active(state) == null) return clear(state)\n    val metadata = state.metadata\n      .filterKeys { !it.startsWith(PREFIX) && !it.startsWith(REPLAY_PREFIX) }\n      .toMutableMap()\n    metadata[AP] = "0"\n    metadata[ACTOR_INDEX] = "0"\n    metadata[ROUND] = "1"\n    metadata[ACTION_SERIAL] = "0"\n    return state.copy(party = state.party.copy(maxMembers = 7), metadata = metadata)\n  }\n\n  fun requestKey(clientRequestId: String): String = clientRequestId.trim()\n\n  fun replayReply(state: GameState, requestKey: String): String? {\n    if (requestKey.isBlank() || state.metadata[REPLAY_REQUEST] != requestKey) return null\n    return state.metadata[REPLAY_REPLY]\n  }\n\n  fun replayDisplayAction(state: GameState): String =\n    state.metadata[REPLAY_ACTION].orEmpty().ifBlank { "Hành động chiến đấu đã được xử lý." }\n\n  fun resolve(\n    state: GameState,\n    actionKind: String,\n    action: String,\n    requestKey: String = ""\n  ): CombatRuntime.Resolution {\n    if (requestKey.isNotBlank() && state.metadata[REPLAY_REQUEST] == requestKey) {\n      return CombatRuntime.Resolution(\n        state = state,\n        handled = true,\n        reply = state.metadata[REPLAY_REPLY].orEmpty().ifBlank { "Hành động này đã được xử lý." },\n        committed = false,\n        replayed = true\n      )\n    }\n\n    CombatRuntime.active(state)\n      ?: return CombatRuntime.Resolution(state, handled = false, committed = false)\n\n    val actor = currentActor(state)\n      ?: return CombatRuntime.Resolution(\n        clear(CombatRuntime.clear(state)),\n        handled = true,\n        reply = "Party không còn thành viên có khả năng tiếp tục chiến đấu.",\n        committed = true\n      )\n\n    val display = displayAction(state, action)\n    val locked = CombatRuntime.partyTurnActorActionLocked(state, actor.id)\n\n    return when {\n      action == "PARTY_TURN_ATK" -> {\n        val scoped = withActorContext(state, actor.id)\n        val engine = CombatRuntime.resolve(\n          scoped,\n          "EXECUTE",\n          if (locked) "không thể hành động" else "tấn công"\n        )\n        finishValidAction(\n          state, withoutActorContext(engine), actor,\n          apDelta = if (locked) 0 else 1,\n          requestKey = requestKey,\n          displayAction = display,\n          locked = locked\n        )\n      }\n\n      action == "PARTY_TURN_DEFEND" -> {\n        val scoped = withActorContext(state, actor.id)\n        val engine = CombatRuntime.resolve(\n          scoped,\n          "EXECUTE",\n          if (locked) "không thể hành động" else "phòng thủ"\n        )\n        finishValidAction(\n          state, withoutActorContext(engine), actor,\n          apDelta = if (locked) 0 else 1,\n          requestKey = requestKey,\n          displayAction = display,\n          locked = locked\n        )\n      }\n\n      action == "PARTY_TURN_RUN" -> {\n        val scoped = withActorContext(state, actor.id)\n        val engine = CombatRuntime.resolve(\n          scoped,\n          if (locked) "EXECUTE" else actionKind,\n          if (locked) "không thể hành động" else "bỏ chạy"\n        )\n        finishValidAction(\n          state, withoutActorContext(engine), actor,\n          apDelta = 0,\n          requestKey = requestKey,\n          displayAction = display,\n          locked = locked\n        )\n      }\n\n      action.startsWith("PARTY_TURN_SKILL::") -> {\n        val skillName = action.removePrefix("PARTY_TURN_SKILL::").trim()\n        val skill = selectableSkills(actor.id).firstOrNull { it.name == skillName }\n        if (skill == null) {\n          CombatRuntime.Resolution(\n            state = state,\n            handled = true,\n            reply = "Không thể kích hoạt “$skillName” bằng lệnh tay. Kỹ năng này không có định nghĩa ACTIVE/MANUAL trong catalog hiện hành, nên AP và lượt không thay đổi.",\n            committed = false,\n            rejectionReason = "skill_not_manually_activatable"\n          )\n        } else {\n          CombatRuntime.Resolution(\n            state = state,\n            handled = true,\n            reply = "“${skill.name}” chưa có resolver ACTIVE/MANUAL authoritative và chi phí AP riêng trong dữ liệu hiện hành. Không trừ AP và không chuyển lượt.",\n            committed = false,\n            rejectionReason = "manual_skill_resolver_missing"\n          )\n        }\n      }\n\n      else -> CombatRuntime.Resolution(\n        state = state,\n        handled = true,\n        reply = "Lệnh chiến đấu không hợp lệ cho lượt của ${actor.name}. AP và lượt không thay đổi.",\n        committed = false,\n        rejectionReason = "invalid_party_combat_command"\n      )\n    }\n  }\n\n  fun json(state: GameState): JSONObject? {\n    if (CombatRuntime.active(state) == null) return null\n    val actorAt = currentActorWithIndex(state)\n    val actor = actorAt?.second\n    val list = actors(state)\n    return JSONObject().apply {\n      put("ap", ap(state))\n      put("maxAp", MAX_AP)\n      put("round", round(state))\n      put("actorIndex", actorAt?.first ?: actorIndex(state))\n      put("actorCount", list.size)\n      put("actorId", actor?.id ?: JSONObject.NULL)\n      put("actorName", actor?.name ?: JSONObject.NULL)\n      put("actorAvatar", actor?.avatarRef ?: JSONObject.NULL)\n      put("skills", JSONArray().apply {\n        if (actor != null) {\n          selectableSkills(actor.id).forEach { skill ->\n            put(JSONObject().apply {\n              put("name", skill.name)\n              put("kind", skill.kind)\n              put("cost", JSONObject.NULL)\n            })\n          }\n        }\n      })\n    }\n  }\n\n  fun feedback(before: GameState, result: CombatRuntime.Resolution): JSONObject {\n    val after = result.state\n    val entityBefore = CombatRuntime.active(before)\n    val entityAfter = CombatRuntime.active(after)\n    val hits = JSONArray()\n    val entityDamage = if (entityBefore != null) {\n      val hpAfter = entityAfter?.entityHp ?: if (result.entityDestroyed) 0 else entityBefore.entityHp\n      (entityBefore.entityHp - hpAfter).coerceAtLeast(0)\n    } else 0\n    if (entityDamage > 0 && entityBefore != null) {\n      hits.put(JSONObject().apply {\n        put("targetType", "entity")\n        put("targetId", entityBefore.entityKey)\n        put("targetName", entityBefore.entityName)\n        put("damage", entityDamage)\n      })\n    }\n    before.characters.forEach { (id, character) ->\n      val afterHp = after.characters[id]?.vitalState?.currentHp ?: character.vitalState.currentHp\n      val damage = (character.vitalState.currentHp - afterHp).coerceAtLeast(0)\n      if (damage > 0) hits.put(JSONObject().apply {\n        put("targetType", "party")\n        put("targetId", id)\n        put("targetName", character.name)\n        put("damage", damage)\n      })\n    }\n    return JSONObject().apply {\n      put("eventId", "combat:${entityBefore?.encounterId ?: "none"}:${actionSerial(after)}")\n      put("accepted", result.committed)\n      put("replayed", result.replayed)\n      put("rejectionReason", result.rejectionReason ?: JSONObject.NULL)\n      put("hits", hits)\n      put("entityDestroyed", result.entityDestroyed)\n      put("escaped", result.escaped)\n      put("apBefore", ap(before))\n      put("apAfter", ap(after))\n      put("apDelta", ap(after) - ap(before))\n      val next = currentActor(after)\n      put("nextActorId", next?.id ?: JSONObject.NULL)\n      put("nextActorName", next?.name ?: JSONObject.NULL)\n    }\n  }\n\n  fun displayAction(state: GameState, action: String): String {\n    val actorName = currentActor(state)?.name ?: "Party"\n    val entityName = CombatRuntime.active(state)?.entityName ?: "Entity"\n    return when {\n      action == "PARTY_TURN_ATK" -> "$actorName tấn công $entityName."\n      action == "PARTY_TURN_DEFEND" -> "$actorName vào thế phòng thủ trước $entityName."\n      action == "PARTY_TURN_RUN" -> "$actorName tìm đường rút khỏi giao tranh với $entityName."\n      action.startsWith("PARTY_TURN_SKILL::") -> {\n        val skillName = action.removePrefix("PARTY_TURN_SKILL::").trim()\n        "$actorName yêu cầu dùng $skillName lên $entityName."\n      }\n      else -> "$actorName gửi một lệnh chiến đấu không hợp lệ."\n    }\n  }\n\n  fun playerFacingReply(reply: String): String {\n    var text = reply\n    text = Regex("""ENTITY ACTION BUDGET:\\s*[^.]*\\.\\s*""").replace(text, "")\n    text = Regex("""ENTITY ACTION \\d+/\\d+\\s*->\\s*[^:]+:\\s*SCP-173 primary UNOBSERVED action resolved\\.\\s*""").replace(text, "")\n    text = Regex("""ENTITY ACTION \\d+/\\d+\\s*->\\s*[^:]+:\\s*(?:HIT|MISS)\\.\\s*""").replace(text, "")\n    text = Regex("""PARTY ACTION (?:TẤN CÔNG|NÉ TRÁNH|BỎ CHẠY):\\s*[^.]*\\.\\s*""").replace(text, "")\n    text = text\n      .replace("% DMG", "% sát thương")\n      .replace(" DMG", " sát thương")\n      .replace("Base DMG", "sát thương cơ sở")\n      .replace("Max HP", "HP tối đa")\n      .replace(" Evasion", " Né tránh")\n      .replace(" Accuracy", " Chính xác")\n      .replace(" Armor", " Giáp")\n      .replace(" turn", " lượt")\n    return text.replace(Regex("""[ \\t]{2,}"""), " ").trim()\n  }\n\n  fun actionSerial(state: GameState): Int =\n    state.metadata[ACTION_SERIAL]?.toIntOrNull()?.coerceAtLeast(0) ?: 0\n\n  private fun finishValidAction(\n    before: GameState,\n    engine: CombatRuntime.Resolution,\n    actor: Actor,\n    apDelta: Int,\n    requestKey: String,\n    displayAction: String,\n    locked: Boolean\n  ): CombatRuntime.Resolution {\n    var next = engine.state\n    var reply = playerFacingReply(engine.reply)\n\n    if (locked) {\n      reply = listOf(\n        "${actor.name} đang bị choáng hoặc mất khả năng hành động và mất lượt.",\n        reply\n      ).filter { it.isNotBlank() }.joinToString(" ")\n    }\n\n    val terminal = engine.entityDestroyed || engine.escaped || CombatRuntime.active(next) == null\n    if (!terminal) {\n      val oldAp = ap(before)\n      val newAp = (oldAp + apDelta).coerceIn(0, MAX_AP)\n      next = withAp(next, newAp)\n      next = withActionSerial(next, actionSerial(before) + 1)\n      next = advanceActor(next, actor.id)\n\n      val nextActor = currentActor(next)\n      val apLine = if (apDelta > 0) "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP." else ""\n      val turnLine = nextActor?.let { "Lượt ${it.name} · AP $newAp/$MAX_AP." }.orEmpty()\n      reply = listOf(reply, apLine, turnLine).filter { it.isNotBlank() }.joinToString(" ")\n    } else {\n      next = clear(next)\n    }\n\n    next = rememberReplay(next, requestKey, reply, displayAction)\n    return engine.copy(\n      state = next,\n      reply = reply,\n      committed = true,\n      rejectionReason = null,\n      replayed = false\n    )\n  }\n\n  private fun withoutActorContext(result: CombatRuntime.Resolution): CombatRuntime.Resolution {\n    val metadata = result.state.metadata.toMutableMap()\n    metadata.remove(ACTOR_CONTEXT)\n    return result.copy(state = result.state.copy(metadata = metadata))\n  }\n\n  private fun withActorContext(state: GameState, actorId: String): GameState =\n    state.copy(metadata = state.metadata + (ACTOR_CONTEXT to actorId))\n\n  private fun rememberReplay(state: GameState, requestKey: String, reply: String, displayAction: String): GameState {\n    if (requestKey.isBlank()) return state\n    return state.copy(metadata = state.metadata + mapOf(\n      REPLAY_REQUEST to requestKey,\n      REPLAY_REPLY to reply,\n      REPLAY_ACTION to displayAction\n    ))\n  }\n\n  private fun orderedIds(state: GameState): List<String> =\n    (listOf(KAI_ID) + state.party.memberIds.filter { it != KAI_ID }).distinct().take(7)\n\n  private fun actorForId(state: GameState, id: String): Actor? {\n    val character = state.characters[id] ?: return null\n    if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return null\n    if (character.metadata["nonCombat"].equals("true", true)) return null\n    if (character.statProfile.combatRole.uppercase().contains("NON-COMBAT")) return null\n    return Actor(id, character.name, character.avatarRef)\n  }\n\n  private fun actors(state: GameState): List<Actor> =\n    orderedIds(state).mapNotNull { actorForId(state, it) }\n\n  private fun currentActorWithIndex(state: GameState): Pair<Int, Actor>? {\n    val ids = orderedIds(state)\n    if (ids.isEmpty()) return null\n    val start = actorIndex(state).coerceIn(0, ids.lastIndex)\n    for (offset in 0 until ids.size) {\n      val index = (start + offset) % ids.size\n      val actor = actorForId(state, ids[index])\n      if (actor != null) return index to actor\n    }\n    return null\n  }\n\n  private fun currentActor(state: GameState): Actor? = currentActorWithIndex(state)?.second\n\n  private fun advanceActor(state: GameState, actorId: String): GameState {\n    val ids = orderedIds(state)\n    if (ids.isEmpty()) return clear(CombatRuntime.clear(state))\n    val start = ids.indexOf(actorId).takeIf { it >= 0 } ?: actorIndex(state).coerceIn(0, ids.lastIndex)\n    for (offset in 1..ids.size) {\n      val index = (start + offset) % ids.size\n      if (actorForId(state, ids[index]) == null) continue\n      val wrapped = start + offset >= ids.size\n      val metadata = state.metadata.toMutableMap()\n      metadata[ACTOR_INDEX] = index.toString()\n      if (wrapped) metadata[ROUND] = (round(state) + 1).toString()\n      return state.copy(metadata = metadata)\n    }\n    return clear(CombatRuntime.clear(state))\n  }\n\n  private fun selectableSkills(characterId: String): List<CharacterSkillDefinition> =\n    CompanionSkillCatalog.forCharacter(characterId).filter {\n      it.kind.trim().uppercase() in setOf("ACTIVE", "MANUAL")\n    }\n\n  private fun withAp(state: GameState, value: Int): GameState =\n    state.copy(metadata = state.metadata + (AP to value.coerceIn(0, MAX_AP).toString()))\n\n  private fun withActionSerial(state: GameState, value: Int): GameState =\n    state.copy(metadata = state.metadata + (ACTION_SERIAL to value.coerceAtLeast(0).toString()))\n\n  private fun ap(state: GameState): Int =\n    state.metadata[AP]?.toIntOrNull()?.coerceIn(0, MAX_AP) ?: 0\n\n  private fun actorIndex(state: GameState): Int =\n    state.metadata[ACTOR_INDEX]?.toIntOrNull()?.coerceAtLeast(0) ?: 0\n\n  private fun round(state: GameState): Int =\n    state.metadata[ROUND]?.toIntOrNull()?.coerceAtLeast(1) ?: 1\n\n  private fun clear(state: GameState): GameState =\n    state.copy(metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) })\n}\n', encoding="utf-8")


# Facade: client request idempotency, no time/regen for rejects or replays, and
# player-facing action text in the persisted legacy battle log.
facade = FACADE.read_text(encoding="utf-8")
method_start = facade.find("  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {\n")
method_end = facade.find("\n  private fun loadOrMigrate(legacy: JSONObject): GameState {\n", method_start)
if method_start < 0 or method_end < 0:
    raise RuntimeError("Interleaved combat: processCombat boundary missing")
method = facade[method_start:method_end]

active_old = '''    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")

    var resolution = PartyTurnCombat.resolve(current, actionKind, action)
'''
active_new = '''    val current = loadOrMigrate(legacy)
    val combatRequestKey = PartyTurnCombat.requestKey(legacy.optString("combatRequestId"))
    if (CombatRuntime.active(current) == null) {
      val replay = PartyTurnCombat.replayReply(current, combatRequestKey)
      if (replay != null) {
        val output = syncLegacy(legacy, current, incrementTurn = false)
        appendLog(output, PartyTurnCombat.replayDisplayAction(current), replay)
        return response(true, output, null, "combat_replayed", replay)
      }
      return response(false, legacy, null, "combat_inactive")
    }

    var resolution = PartyTurnCombat.resolve(current, actionKind, action, combatRequestKey)
'''
method = replace_once(method, active_old, active_new, "Combat request idempotency entry")

feedback_anchor = "    val combatFeedback = PartyTurnCombat.feedback(current, resolution)\n"
method = replace_once(
    method,
    feedback_anchor,
    "    resolution = resolution.copy(reply = PartyTurnCombat.playerFacingReply(resolution.reply))\n" + feedback_anchor,
    "Combat player-facing reply",
)

time_start = method.find("    val time = TimeEngine.execute(next, TimeAdvanceCommand(\n")
save_pos = method.find("    repository.save(next)\n", time_start)
if time_start < 0 or save_pos < 0:
    raise RuntimeError("Interleaved combat: subjective-time block missing")
time_block = method[time_start:save_pos]
if 'reason = "combat_action"' not in time_block:
    raise RuntimeError("Interleaved combat: combat time block changed")
indented = "\n".join(("  " + line if line.strip() else line) for line in time_block.rstrip("\n").splitlines())
method = method[:time_start] + "    if (resolution.committed) {\n" + indented + "\n    }\n" + method[save_pos:]

method = replace_once(
    method,
    "    appendLog(output, action, resolution.reply)\n",
    "    appendLog(output, PartyTurnCombat.displayAction(current, action), resolution.reply)\n",
    "Persist human combat action",
)
facade = facade[:method_start] + method + facade[method_end:]
FACADE.write_text(facade, encoding="utf-8")


# UI: protocol remains PARTY_TURN_*, pending/display never shows it, and the
# serialized request id lets Android retries replay instead of double-committing.
html = INDEX.read_text(encoding="utf-8")
submit_anchor = '''  function submitCombat(action){
    if(!combatActive())return false;
'''
display_helper = r'''  function combatDisplayAction(action){
    var c=window.state&&state.combat||{},p=c.partyTurn||{},actor=p.actorName||'Party',entity=c.entityName||c.entityKey||'Entity';
    if(action==='PARTY_TURN_ATK')return actor+' tấn công '+entity+'.';
    if(action==='PARTY_TURN_DEFEND')return actor+' vào thế phòng thủ trước '+entity+'.';
    if(action==='PARTY_TURN_RUN')return actor+' tìm đường rút khỏi giao tranh với '+entity+'.';
    if(String(action||'').indexOf('PARTY_TURN_SKILL::')===0)return actor+' yêu cầu dùng '+String(action).slice('PARTY_TURN_SKILL::'.length)+' lên '+entity+'.';
    return String(action||'Hành động chiến đấu');
  }
  function combatRequestPayload(){
    var payload=JSON.parse(JSON.stringify(state||{}));
    window.__combatRequestSeq=(Number(window.__combatRequestSeq)||0)+1;
    payload.combatRequestId='combat-'+Date.now().toString(36)+'-'+window.__combatRequestSeq;
    return payload;
  }
'''
if "function combatDisplayAction(action)" not in html:
    html = replace_once(html, submit_anchor, display_helper + submit_anchor, "Combat pending display helper")
html = replace_once(html, "    pending(action);\n", "    pending(combatDisplayAction(action));\n", "Combat pending label")
html = replace_once(
    html,
    "    window.Android.submitAction(JSON.stringify(state),'EXECUTE',action);\n",
    "    window.Android.submitAction(JSON.stringify(combatRequestPayload()),'EXECUTE',action);\n",
    "Combat request payload",
)
INDEX.write_text(html, encoding="utf-8")


PARTY_TEST.write_text('package com.rabpit.backroom.core\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass PartyTurnCombatInterleavedTest {\n  private fun party3(): GameState {\n    var state = GameState.initial()\n    state = SpecialFollowersCanon.ensure(state)\n    state = LuciaCanon.ensure(state)\n    return state.copy(\n      party = state.party.copy(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID), maxMembers = 7)\n    )\n  }\n\n  @Test fun entityRespondsAfterEachActorAndDefaultDirectTargetFollowsThatActor() {\n    var state = PartyTurnCombat.init(CombatRuntime.start(party3(), "hound"))\n\n    val irisBefore = state.characters.getValue(IRIS_ID).vitalState.currentHp\n    val luciaBefore = state.characters.getValue(LUCIA_ID).vitalState.currentHp\n    val first = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_DEFEND", "req-kai")\n    assertTrue(first.handled)\n    assertTrue(first.committed)\n    state = first.state\n    assertEquals("Iris", PartyTurnCombat.json(state)!!.getString("actorName"))\n    assertEquals(irisBefore, state.characters.getValue(IRIS_ID).vitalState.currentHp)\n    assertEquals(luciaBefore, state.characters.getValue(LUCIA_ID).vitalState.currentHp)\n\n    val kaiBeforeIris = state.characters.getValue(KAI_ID).vitalState.currentHp\n    val luciaBeforeIris = state.characters.getValue(LUCIA_ID).vitalState.currentHp\n    val irisHpBefore = state.characters.getValue(IRIS_ID).vitalState.currentHp\n    state = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_DEFEND", "req-iris").state\n    assertEquals("Lucia", PartyTurnCombat.json(state)!!.getString("actorName"))\n    assertEquals(kaiBeforeIris, state.characters.getValue(KAI_ID).vitalState.currentHp)\n    assertEquals(luciaBeforeIris, state.characters.getValue(LUCIA_ID).vitalState.currentHp)\n    assertTrue(state.characters.getValue(IRIS_ID).vitalState.currentHp <= irisHpBefore)\n\n    val kaiBeforeLucia = state.characters.getValue(KAI_ID).vitalState.currentHp\n    val irisBeforeLucia = state.characters.getValue(IRIS_ID).vitalState.currentHp\n    state = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_DEFEND", "req-lucia").state\n    assertEquals("Kai", PartyTurnCombat.json(state)!!.getString("actorName"))\n    assertEquals(2, PartyTurnCombat.json(state)!!.getInt("round"))\n    assertEquals(kaiBeforeLucia, state.characters.getValue(KAI_ID).vitalState.currentHp)\n    assertEquals(irisBeforeLucia, state.characters.getValue(IRIS_ID).vitalState.currentHp)\n\n    // One event per completed actor-action/Entity-response pair. The Entity sub-phase\n    // does not create a second eventCounter tick.\n    assertEquals(3, CombatRuntime.active(state)!!.eventCounter)\n  }\n\n  @Test fun invalidAutomaticSkillCommandDoesNotSpendApAdvanceActorOrTickCombat() {\n    val state = PartyTurnCombat.init(CombatRuntime.start(party3(), "hound"))\n    val beforeCombat = CombatRuntime.active(state)!!\n    val beforeTurn = PartyTurnCombat.json(state)!!\n    val result = PartyTurnCombat.resolve(\n      state, "EXECUTE", "PARTY_TURN_SKILL::The Last Requiem", "bad-skill"\n    )\n\n    assertTrue(result.handled)\n    assertFalse(result.committed)\n    assertEquals("skill_not_manually_activatable", result.rejectionReason)\n    assertEquals(beforeCombat.eventCounter, CombatRuntime.active(result.state)!!.eventCounter)\n    assertEquals(beforeTurn.getInt("ap"), PartyTurnCombat.json(result.state)!!.getInt("ap"))\n    assertEquals(beforeTurn.getString("actorName"), PartyTurnCombat.json(result.state)!!.getString("actorName"))\n  }\n\n  @Test fun currentCatalogDoesNotExposeAutomaticCounterPassiveOrStateSkillsAsPayableManualActions() {\n    var state = PartyTurnCombat.init(CombatRuntime.start(party3(), "hound"))\n    assertEquals(0, PartyTurnCombat.json(state)!!.getJSONArray("skills").length())\n    state = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_DEFEND", "next").state\n    assertEquals("Iris", PartyTurnCombat.json(state)!!.getString("actorName"))\n    assertEquals(0, PartyTurnCombat.json(state)!!.getJSONArray("skills").length())\n  }\n\n  @Test fun duplicateRequestIdCannotDealDamageGainApOrCreateAnotherEntityTurn() {\n    val state = PartyTurnCombat.init(CombatRuntime.start(party3(), "hound"))\n    val first = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_ATK", "same-request")\n    assertTrue(first.committed)\n    val afterFirst = first.state\n    val eventAfterFirst = CombatRuntime.active(afterFirst)?.eventCounter\n    val hpAfterFirst = afterFirst.characters.mapValues { it.value.vitalState.currentHp }\n    val turnAfterFirst = PartyTurnCombat.json(afterFirst)?.toString()\n\n    val replay = PartyTurnCombat.resolve(afterFirst, "EXECUTE", "PARTY_TURN_ATK", "same-request")\n    assertTrue(replay.handled)\n    assertFalse(replay.committed)\n    assertTrue(replay.replayed)\n    assertEquals(eventAfterFirst, CombatRuntime.active(replay.state)?.eventCounter)\n    assertEquals(hpAfterFirst, replay.state.characters.mapValues { it.value.vitalState.currentHp })\n    assertEquals(turnAfterFirst, PartyTurnCombat.json(replay.state)?.toString())\n  }\n\n  @Test fun playerFacingBattleTextNeverLeaksPartyTurnOrEntityActionProtocol() {\n    val state = PartyTurnCombat.init(CombatRuntime.start(party3(), "hound"))\n    val result = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_DEFEND", "log-test")\n    val action = PartyTurnCombat.displayAction(state, "PARTY_TURN_DEFEND")\n    val reply = PartyTurnCombat.playerFacingReply(result.reply)\n\n    assertFalse(action.contains("PARTY_TURN_"))\n    assertFalse(reply.contains("PARTY_TURN_"))\n    assertFalse(reply.contains("ENTITY ACTION"))\n    assertTrue(action.contains("Kai"))\n  }\n}\n', encoding="utf-8")

protocol = PROTOCOL_TEST.read_text(encoding="utf-8")
if "combat pending copy must hide internal protocol" not in protocol:
    protocol += r'''
{
  const src = fs.readFileSync('app/src/main/assets/index.html', 'utf8');
  assert(!src.includes('pending(action);'), 'combat pending copy must hide internal protocol');
  assert(src.includes('pending(combatDisplayAction(action));'), 'combat pending copy must use player-facing action');
  assert(src.includes("payload.combatRequestId='combat-'"), 'combat requests need an idempotency id');
  assert(src.includes("JSON.stringify(combatRequestPayload())"), 'combat bridge must send the request-id payload');
}
'''
PROTOCOL_TEST.write_text(protocol, encoding="utf-8")

print("Interleaved Party combat finalizer applied: authoritative actor effects, Entity response after each valid actor turn, Entity skills restored, protocol-safe logs, and replay protection.")
