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


# JEFF_THE_KILLER_CORNERING_FEINT_V1
#
# Active Entity skill. Exactly one 24% proc roll is evaluated only during Jeff's
# own Entity-response turn. It cannot trigger from taking damage, HP thresholds,
# Party actions outside resolution, bleed/status ticks, or any other out-of-turn event.
#
# Canon/mechanical fit: Jeff is already a human-scale stalking knife predator with
# Go to Sleep, Silent Stalker and No Safe Route. Cornering Feint models a knife-line
# fake during his response window that forces the Party to surrender a little tempo.
#
# Balance: no direct damage, no Stun, no Cover break, no Escape loss, no persistent
# stack. On proc it lowers Momentum by at most 1. GUARD fully counters the feint.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val JEFF_CORNERING_FEINT_PROC_PERCENT = 24
'''
if 'JEFF_CORNERING_FEINT_PROC_PERCENT = 24' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Jeff Cornering Feint constant",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // JEFF_THE_KILLER_CORNERING_FEINT_V1: one proc check on Jeff's Entity response turn only.
      if (c.entityKey == "jeff_the_killer" &&
          roll(c.copy(eventCounter = c.eventCounter + 3517), 100) < JEFF_CORNERING_FEINT_PROC_PERCENT) {
        if (intent == Intent.GUARD) {
          log += "Cornering Feint: proc ${JEFF_CORNERING_FEINT_PROC_PERCENT}% nhưng Party giữ guard kín; kỹ năng bị vô hiệu."
        } else {
          val momentumBefore = c.momentum
          val momentumAfter = max(-3, momentumBefore - 1)
          c = c.copy(momentum = momentumAfter)
          log += "Cornering Feint: Jeff ép góc bằng đường dao giả; proc ${JEFF_CORNERING_FEINT_PROC_PERCENT}%, Momentum $momentumBefore -> $momentumAfter."
        }
      }
'''
if 'JEFF_THE_KILLER_CORNERING_FEINT_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Jeff Cornering Feint Entity-turn proc",
    )

for marker in (
    'JEFF_THE_KILLER_CORNERING_FEINT_V1',
    'JEFF_CORNERING_FEINT_PROC_PERCENT = 24',
    'c.entityKey == "jeff_the_killer"',
    'intent == Intent.GUARD',
    'momentumBefore - 1',
):
    if marker not in combat:
        raise RuntimeError("Jeff Cornering Feint runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'jeffCorneringFeintIsEntityTurnOnlyTempoPressureWithGuardCounterplay' not in test:
    tests = r'''
  @Test fun jeffCorneringFeintIsEntityTurnOnlyTempoPressureWithGuardCounterplay() {
    assertEquals(24, CombatRuntime.JEFF_CORNERING_FEINT_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "jeff_the_killer")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "MOVE", "lùi sang hành lang bên cạnh để đổi góc")
      if (result.reply.contains("Cornering Feint:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("24% Jeff proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Momentum"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))

    var guardResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "jeff_the_killer")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "khóa tư thế phòng thủ và giữ guard kín")
      if (result.reply.contains("Cornering Feint:")) {
        guardResult = result
        break
      }
    }

    assertNotNull("Jeff proc must also be reachable on a GUARD Entity-response turn", guardResult)
    assertTrue(guardResult!!.reply, guardResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(guardResult!!.reply, guardResult!!.reply.contains("Momentum 3 -> 2"))
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Jeff the Killer Cornering Feint (ACTIVE, 24% on Jeff Entity turn, GUARD counterplay).")
