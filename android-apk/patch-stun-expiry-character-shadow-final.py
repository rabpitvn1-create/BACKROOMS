from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
PARTY = CORE / "PartyTurnCombat.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
AP_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/PartyTurnCombatApSkillAuthorityTest.kt"


def require(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"Stun/shadow finalizer missing generated file: {path.name}")
    return path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# A one-turn combat STUN created by older saves has combatEvent but no expiresEvent.
# The old fallback treated every incapacitating status without expiresEvent as permanent,
# so PartyTurnCombat could keep skipping the same actor forever. Infer the one-event
# expiry only for non-persistent durationTurns == 1 statuses that carry combatEvent.
combat = require(COMBAT)
status_old = '''      val token = (effect.type + " " + effect.id).uppercase().replace('-', '_').replace(' ', '_')
      val incapacitating = listOf("STUN", "UNCONSCIOUS", "KNOCKED_OUT", "PARALYZ").any(token::contains)
      incapacitating && (effect.metadata["expiresEvent"]?.toIntOrNull()?.let { eventCounter < it } ?: true)
'''
status_new = '''      val token = (effect.type + " " + effect.id).uppercase().replace('-', '_').replace(' ', '_')
      val incapacitating = listOf("STUN", "UNCONSCIOUS", "KNOCKED_OUT", "PARALYZ").any(token::contains)
      val explicitExpiry = effect.metadata["expiresEvent"]?.toIntOrNull()
      val legacyOneEventExpiry = effect.metadata["combatEvent"]?.toIntOrNull()?.let { appliedEvent ->
        if (!effect.persistent && effect.durationTurns == 1) appliedEvent + 1 else null
      }
      incapacitating && ((explicitExpiry ?: legacyOneEventExpiry)?.let { eventCounter < it } ?: true)
'''
combat = replace_once(combat, status_old, status_new, "One-event incapacitating status expiry")

# New Violet Warden saves now carry an explicit combat-event expiry. The inferred
# fallback above remains for already-created saves from 1.0.0.0.2 and earlier.
apply_start = combat.find('  private fun violetWardenApplyStun(state: GameState, characterId: String, eventCounter: Int): GameState {')
apply_end = combat.find('\n  }\n', apply_start)
if apply_start < 0 or apply_end < 0:
    raise RuntimeError("Violet Warden stun helper missing from final CombatRuntime")
apply_end += len('\n  }\n')
apply_block = combat[apply_start:apply_end]
metadata_old = '      metadata = mapOf("combatEvent" to eventCounter.toString())\n'
metadata_new = '      metadata = mapOf("combatEvent" to eventCounter.toString(), "expiresEvent" to (eventCounter + 1).toString())\n'
apply_block = replace_once(apply_block, metadata_old, metadata_new, "Violet Warden explicit stun expiry")
combat = combat[:apply_start] + apply_block + combat[apply_end:]

for marker in (
    'val legacyOneEventExpiry = effect.metadata["combatEvent"]?.toIntOrNull()',
    'if (!effect.persistent && effect.durationTurns == 1) appliedEvent + 1 else null',
    '"expiresEvent" to (eventCounter + 1).toString()',
):
    if marker not in combat:
        raise RuntimeError("Final stun expiry contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")


# Manual SKILL used while stunned must consume the same skipped personal action as
# ATTACK/DEFEND/RUN. It must not spend AP, but it must advance the combat event and
# actor sequencing so a one-turn stun cannot self-lock on repeated skill attempts.
party = require(PARTY)
locked_old = '''        if (locked) {
          return CombatRuntime.Resolution(
            state = state,
            handled = true,
            reply = "${actor.name} đang bị choáng hoặc mất khả năng hành động. AP và lượt không thay đổi.",
            committed = false,
            rejectionReason = "actor_action_locked"
          )
        }
'''
locked_new = '''        if (locked) {
          val scopedLocked = withActorContext(state, actor.id)
          val lockedEngine = CombatRuntime.resolve(scopedLocked, "EXECUTE", "không thể hành động")
          return finishValidAction(
            state, withoutActorContext(lockedEngine), actor,
            apDelta = 0,
            requestKey = requestKey,
            displayAction = display,
            locked = true
          )
        }
'''
party = replace_once(party, locked_old, locked_new, "Locked manual skill consumes one personal action")
if 'rejectionReason = "actor_action_locked"' in party:
    raise RuntimeError("Permanent locked-skill rejection survived final PartyTurnCombat")
for marker in (
    'val scopedLocked = withActorContext(state, actor.id)',
    'val lockedEngine = CombatRuntime.resolve(scopedLocked, "EXECUTE", "không thể hành động")',
    'apDelta = 0',
    'locked = true',
):
    if marker not in party:
        raise RuntimeError("Locked skill consumption contract missing: " + marker)
PARTY.write_text(party, encoding="utf-8")


# Ground the Kai character shadow closer to the actual sprite footprint seen in the
# Snapshot. Shift its center rightward and make it slightly darker without adding a
# new effect layer or changing the sprite itself.
main = require(MAIN)
shadow_old = (
    ".snapshot .snapshot-character-shadow{position:absolute;right:5%;bottom:1.5%;width:38%;height:7%;"
    "max-width:210px;background:radial-gradient(ellipse at center,rgba(0,0,0,.56) 0%,"
    "rgba(0,0,0,.34) 42%,rgba(0,0,0,0) 74%);border-radius:50%;filter:blur(3px);"
    "transform:scaleY(.72);transform-origin:center;z-index:2;pointer-events:none}"
)
shadow_new = (
    ".snapshot .snapshot-character-shadow{position:absolute;right:1%;bottom:1.5%;width:34%;height:7%;"
    "max-width:190px;background:radial-gradient(ellipse at center,rgba(0,0,0,.66) 0%,"
    "rgba(0,0,0,.42) 42%,rgba(0,0,0,0) 76%);border-radius:50%;filter:blur(3px);"
    "transform:scaleY(.72);transform-origin:center;z-index:2;pointer-events:none}"
)
main = replace_once(main, shadow_old, shadow_new, "Character foot shadow grounding")
if shadow_old in main or shadow_new not in main:
    raise RuntimeError("Character foot shadow final CSS contract invalid")
MAIN.write_text(main, encoding="utf-8")


# Regression covers both the old-save migration path and the manual-skill branch:
# a legacy one-event STUN consumes exactly one action, preserves AP, then releases.
test = require(AP_TEST)
if 'legacyOneEventStunConsumesOneSkillActionThenReleases' not in test:
    extra = r'''

  @Test fun legacyOneEventStunConsumesOneSkillActionThenReleases() {
    var state = gainAp(kaiCombat(), 2)
    val event = CombatRuntime.active(state)!!.eventCounter
    val effect = StatusEffect(
      id = "test:legacy-one-event-stun:kai",
      type = "STUN",
      source = "legacy-test",
      durationTurns = 1,
      persistent = false,
      metadata = mapOf("combatEvent" to event.toString())
    )
    val applied = StatusEngine.execute(state, StatusCommand(
      commandId = "legacy-stun-apply",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = StatusCommand.Operation.APPLY,
      effect = effect
    ))
    assertTrue(applied.applied)
    state = applied.state

    val locked = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::The Last Requiem", "legacy-stun-skill-1"
    )
    assertTrue(locked.handled)
    assertTrue(locked.committed)
    assertEquals(2, PartyTurnCombat.json(locked.state)!!.getInt("ap"))
    assertEquals(event + 1, CombatRuntime.active(locked.state)!!.eventCounter)
    assertTrue(locked.reply, locked.reply.contains("mất lượt"))

    val released = PartyTurnCombat.resolve(
      locked.state, "EXECUTE", "PARTY_TURN_SKILL::The Last Requiem", "legacy-stun-skill-2"
    )
    assertTrue(released.committed)
    assertEquals(0, PartyTurnCombat.json(released.state)!!.getInt("ap"))
    assertFalse(released.reply, released.reply.contains("đang bị choáng hoặc mất khả năng hành động"))
  }
'''
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("AP skill authority test class closing brace missing")
    test = test[:close] + extra + test[close:]
AP_TEST.write_text(test, encoding="utf-8")

print(
    "Final stun/shadow fix applied: one-event incapacitation expires after one committed personal action, "
    "locked manual skills consume that action without AP cost, and Kai's foot shadow is darker and re-centered."
)
