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


# HOUND_DEAD_END_HERDING_V1
#
# Passive Entity skill. Exactly one 23% proc roll is evaluated only during the
# Hound's own Entity-response turn, after the Party actor has resolved and only
# if the Hound actually receives that response turn. It cannot trigger from
# taking damage, HP thresholds, player actions, status ticks, or other out-of-turn events.
#
# Canon fit: project canon describes Hounds as hostile rush/maul predators that
# can pressure prey toward dead ends and use deceptive footstep cues in Level 2.
# Dead-End Herding represents that positional pressure without inventing magic,
# ranged attacks, hard crowd control, or guaranteed damage.
#
# Balance: 23% per Hound Entity turn. No direct damage, no Stun, no Cover loss,
# and no persistent stack. On proc it removes only 6 Escape progress. READ fully
# counters it by identifying the pressure route before the Party commits to it.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val HOUND_DEAD_END_HERDING_PROC_PERCENT = 23
  private const val HOUND_DEAD_END_HERDING_ESCAPE_LOSS = 6
'''
if 'HOUND_DEAD_END_HERDING_PROC_PERCENT = 23' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Hound Dead-End Herding constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // HOUND_DEAD_END_HERDING_V1: one proc check on the Hound's Entity response turn only.
      if (c.entityKey == "hound" &&
          roll(c.copy(eventCounter = c.eventCounter + 3209), 100) < HOUND_DEAD_END_HERDING_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "Dead-End Herding: proc ${HOUND_DEAD_END_HERDING_PROC_PERCENT}% nhưng Party đọc được hướng dồn ép của Hound; kỹ năng bị vô hiệu."
        } else {
          val escapeBefore = c.escapeProgress
          c = c.copy(
            escapeProgress = max(0, escapeBefore - HOUND_DEAD_END_HERDING_ESCAPE_LOSS)
          )
          log += "Dead-End Herding: Hound dồn Party lệch về hướng cụt bằng nhịp áp sát và tiếng động đánh lạc hướng; proc ${HOUND_DEAD_END_HERDING_PROC_PERCENT}%, Escape ${escapeBefore} -> ${c.escapeProgress}."
        }
      }
'''
if 'HOUND_DEAD_END_HERDING_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Hound Dead-End Herding Entity-turn proc",
    )

for marker in (
    'HOUND_DEAD_END_HERDING_V1',
    'HOUND_DEAD_END_HERDING_PROC_PERCENT = 23',
    'HOUND_DEAD_END_HERDING_ESCAPE_LOSS = 6',
    'c.entityKey == "hound"',
    'intent == Intent.READ',
    'escapeProgress = max(0, escapeBefore - HOUND_DEAD_END_HERDING_ESCAPE_LOSS)',
):
    if marker not in combat:
        raise RuntimeError("Hound Dead-End Herding runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'houndDeadEndHerdingIsEntityTurnOnlyEscapePressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun houndDeadEndHerdingIsEntityTurnOnlyEscapePressureWithReadCounterplay() {
    assertEquals(23, CombatRuntime.HOUND_DEAD_END_HERDING_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "hound")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ đội hình phòng thủ trước Hound")
      if (result.reply.contains("Dead-End Herding:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("23% Hound proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Escape 40 -> 34"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(34, CombatRuntime.active(pressureResult!!.state)!!.escapeProgress)

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "hound")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "READ", "đọc nhịp áp sát và xác định hướng Hound đang dồn đội hình")
      if (result.reply.contains("Dead-End Herding:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Hound proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(readResult!!.reply, readResult!!.reply.contains("Escape 40 -> 34"))
    assertTrue(CombatRuntime.active(readResult!!.state)!!.escapeProgress >= 40)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Hound Dead-End Herding (PASSIVE, 23% on Hound Entity turn, READ counterplay).")
