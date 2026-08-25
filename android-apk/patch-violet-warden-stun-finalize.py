from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")

# Keep Violet's STUN save-persistent for exactly the next CombatRuntime event and
# scoped to the locked Duel Target. This supplements the existing StatusEffect so
# the status is not merely visual: the affected character cannot perform a direct
# combat action while other ACTIVE Party members remain free to act.
constants_anchor = '  private const val VIOLET_WARDEN_STATUS_PREFIX = "violet_warden:"\n'
constants_extra = '''  private const val VIOLET_WARDEN_STUN_TARGET_KEY = "combat.violetWardenStunTargetId"
  private const val VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY = "combat.violetWardenStunUntilEvent"
'''
if 'VIOLET_WARDEN_STUN_TARGET_KEY' not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants_extra, "Violet Warden stun metadata constants")

helper_anchor = '''  private fun violetWardenDuelTarget(state: GameState): String? {
'''
lock_helper = '''  private fun violetWardenActionLocked(state: GameState, characterId: String): Boolean {
    if (state.metadata["combat.entityKey"] != VIOLET_WARDEN_KEY) return false
    if (state.metadata[VIOLET_WARDEN_STUN_TARGET_KEY] != characterId) return false
    val untilEvent = state.metadata[VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY]?.toIntOrNull() ?: return false
    val nextEvent = (state.metadata["combat.eventCounter"]?.toIntOrNull() ?: 0) + 1
    return nextEvent <= untilEvent
  }

'''
if 'private fun violetWardenActionLocked(' not in combat:
    combat = replace_once(combat, helper_anchor, lock_helper + helper_anchor, "Violet Warden stun action-lock helper")

old_apply = '''  private fun violetWardenApplyStun(state: GameState, characterId: String, eventCounter: Int): GameState {
    if (characterId !in state.characters) return state
    val id = VIOLET_WARDEN_STATUS_PREFIX + "stun:" + characterId
    val effect = StatusEffect(
      id = id,
      type = "STUN",
      source = VIOLET_WARDEN_KEY,
      startTurnId = state.turn.currentTurnId,
      durationTurns = 1,
      persistent = false,
      metadata = mapOf("combatEvent" to eventCounter.toString())
    )
    val operation = if (id in state.statuses) StatusCommand.Operation.UPDATE else StatusCommand.Operation.APPLY
    val result = StatusEngine.execute(state, StatusCommand(
      commandId = "VIOLET_WARDEN:STUN:$characterId:$eventCounter",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = operation,
      effect = effect,
      statusId = id
    ))
    return if (result.applied) result.state else state
  }
'''
new_apply = '''  private fun violetWardenApplyStun(state: GameState, characterId: String, eventCounter: Int): GameState {
    if (characterId !in state.characters) return state
    var scheduled = violetWardenMetadata(state, VIOLET_WARDEN_STUN_TARGET_KEY, characterId)
    scheduled = violetWardenMetadata(scheduled, VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY, (eventCounter + 1).toString())
    val id = VIOLET_WARDEN_STATUS_PREFIX + "stun:" + characterId
    val effect = StatusEffect(
      id = id,
      type = "STUN",
      source = VIOLET_WARDEN_KEY,
      startTurnId = state.turn.currentTurnId,
      durationTurns = 1,
      persistent = false,
      metadata = mapOf("combatEvent" to eventCounter.toString())
    )
    val operation = if (id in scheduled.statuses) StatusCommand.Operation.UPDATE else StatusCommand.Operation.APPLY
    val result = StatusEngine.execute(scheduled, StatusCommand(
      commandId = "VIOLET_WARDEN:STUN:$characterId:$eventCounter",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = operation,
      effect = effect,
      statusId = id
    ))
    return if (result.applied) result.state else scheduled
  }
'''
combat = replace_once(combat, old_apply, new_apply, "Violet Warden scheduled one-event stun")

# Companion actions already flow through activePartyCharacter. Make that action
# eligibility helper reject only the single Violet-stunned character for the one
# scheduled event. Party membership/presence and persistent stats are unchanged.
active_anchor = '''  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in state.party.memberIds) return null
'''
active_new = '''  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in state.party.memberIds) return null
    if (violetWardenActionLocked(state, characterId)) return null
'''
combat = replace_once(combat, active_anchor, active_new, "Violet Warden companion stun action gate")

# Kai owns the primary ATTACK resolution directly. Keep shared roll/hitChance
# variables in their original scope because follower attacks reuse hitChance later
# in the same Party event. Suppress only Kai's hit branch when he is stunned.
log_anchor = '    val log = mutableListOf<String>()\n'
kai_lock_line = '    val violetWardenKaiActionLocked = current.entityKey == VIOLET_WARDEN_KEY && violetWardenActionLocked(state, KAI_ID)\n'
if kai_lock_line not in combat:
    combat = replace_once(combat, log_anchor, log_anchor + kai_lock_line, "Violet Warden Kai action-lock local")

attack_start = combat.find('      Intent.ATTACK -> {\n')
attack_end = combat.find('      Intent.OTHER -> {\n', attack_start)
if attack_start < 0 or attack_end < 0:
    raise RuntimeError("Violet Warden could not bound final Party ATTACK block")
attack = combat[attack_start:attack_end]
if 'VIOLET_WARDEN_KAI_STUN_GATE_V1' not in attack:
    hit_marker = '        if (roll < hitChance) {\n'
    if attack.count(hit_marker) != 1:
        raise RuntimeError(f"Violet Warden Kai hit anchor changed: found {attack.count(hit_marker)}")
    hit_gate = '''        // VIOLET_WARDEN_KAI_STUN_GATE_V1
        if (violetWardenKaiActionLocked) {
          log += "Violet Warden STUN: Kai mất lượt hành động cá nhân; các thành viên ACTIVE khác vẫn tiếp tục lệnh TẤN CÔNG."
        } else if (roll < hitChance) {
'''
    attack = attack.replace(hit_marker, hit_gate, 1)
    combat = combat[:attack_start] + attack + combat[attack_end:]

# Automatic Kai offense must obey the same one-character lock. Keep the existing
# action-gate text/order intact because established regressions key off those
# expressions; append the Violet condition rather than rewriting their prefix.
gco_marker = '''    // PARTY_ATTACK_GCO_GATE_V1
    if (intent == Intent.ATTACK) {
'''
gco_new = '''    // PARTY_ATTACK_GCO_GATE_V1
    if (intent == Intent.ATTACK && !violetWardenKaiActionLocked) {
'''
combat = replace_once(combat, gco_marker, gco_new, "Violet Warden Guilty Crown stun gate")
combat = combat.replace(
    'intent == Intent.ATTACK && !isGuiltyCrownTurn',
    'intent == Intent.ATTACK && !isGuiltyCrownTurn && !violetWardenKaiActionLocked',
)
combat = combat.replace(
    '(intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn',
    '(intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn && !violetWardenKaiActionLocked',
)
combat = combat.replace(
    'val isGuiltyCrownTurn = intent == Intent.ATTACK && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0',
    'val isGuiltyCrownTurn = intent == Intent.ATTACK && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0 && !violetWardenKaiActionLocked',
)

# Expose the active lock through the existing boss JSON for deterministic UI/tests.
json_anchor = '      put("originEra", "15th century")\n'
json_extra = '''      put("stunTargetId", state.metadata[VIOLET_WARDEN_STUN_TARGET_KEY] ?: "")
      put("stunUntilEvent", state.metadata[VIOLET_WARDEN_STUN_UNTIL_EVENT_KEY]?.toIntOrNull() ?: 0)
'''
if 'put("stunTargetId"' not in combat:
    combat = replace_once(combat, json_anchor, json_anchor + json_extra, "Violet Warden stun JSON projection")

for marker in (
    'private const val VIOLET_WARDEN_STUN_TARGET_KEY',
    'private fun violetWardenActionLocked(',
    '(eventCounter + 1).toString()',
    'if (violetWardenActionLocked(state, characterId)) return null',
    'val violetWardenKaiActionLocked =',
    'VIOLET_WARDEN_KAI_STUN_GATE_V1',
    'Violet Warden STUN: Kai mất lượt hành động cá nhân',
    'intent == Intent.ATTACK && !isGuiltyCrownTurn',
    'put("stunTargetId"',
):
    if marker not in combat:
        raise RuntimeError("Violet Warden stun contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'violetWardenStunSuppressesOnlyKaiPersonalAttackForOneCombatEvent' not in test:
    tests = r'''
  @Test fun violetWardenStunSuppressesOnlyKaiPersonalAttackForOneCombatEvent() {
    var state = CombatRuntime.start(GameState.initial(), "violet_warden")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.violetWardenStunTargetId" to KAI_ID,
      "combat.violetWardenStunUntilEvent" to "1"
    ))
    val before = CombatRuntime.active(state)!!.entityHp
    val locked = CombatRuntime.resolve(state, "ATTACK", "tấn công")
    assertEquals(before, CombatRuntime.active(locked.state)!!.entityHp)
    assertTrue(locked.reply, locked.reply.contains("Violet Warden STUN: Kai mất lượt hành động cá nhân"))

    val released = CombatRuntime.resolve(locked.state, "ATTACK", "tấn công")
    assertFalse(released.reply, released.reply.contains("Violet Warden STUN: Kai mất lượt hành động cá nhân"))
  }
'''
    close = test.rfind('\n}')
    if close < 0:
        raise RuntimeError("Violet Warden stun test closing brace missing")
    test = test[:close] + "\n" + tests.rstrip() + test[close:]
TEST.write_text(test, encoding="utf-8")

print("Violet Warden single-target STUN finalized: one Duel Target loses exactly the next personal combat action; Party-wide action economy remains intact.")
