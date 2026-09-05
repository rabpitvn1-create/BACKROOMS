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


# JOHN_DOE_TOXIC_RUSH_V1
# Active Entity skill. Exactly one 26% proc roll occurs only on John Doe's own
# Entity-response turn. No damage, Stun, Cover break, Escape loss, or persistent stack.
# EVADE fully counters the rush. The skill extends John Doe's existing poison/stun
# predator identity without multiplying either status or creating a lock loop.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val JOHN_DOE_TOXIC_RUSH_PROC_PERCENT = 26
'''
if 'JOHN_DOE_TOXIC_RUSH_PROC_PERCENT = 26' not in combat:
    combat = replace_once(combat, constant_anchor, constant_anchor + constant_block, "John Doe Toxic Rush constant")

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // JOHN_DOE_TOXIC_RUSH_V1: one proc check on John Doe's Entity response turn only.
      if (c.entityKey == "john_doe" &&
          roll(c.copy(eventCounter = c.eventCounter + 4129), 100) < JOHN_DOE_TOXIC_RUSH_PROC_PERCENT) {
        if (intent == Intent.EVADE) {
          log += "Toxic Rush: proc ${JOHN_DOE_TOXIC_RUSH_PROC_PERCENT}% nhưng Party EVADE khỏi đường áp sát nhiễm độc; kỹ năng bị vô hiệu."
        } else {
          val momentumBefore = c.momentum
          val momentumAfter = max(-3, momentumBefore - 1)
          c = c.copy(momentum = momentumAfter)
          log += "Toxic Rush: John Doe ép tuyến bằng nhịp áp sát nhiễm độc; proc ${JOHN_DOE_TOXIC_RUSH_PROC_PERCENT}%, Momentum $momentumBefore -> $momentumAfter."
        }
      }
'''
if 'JOHN_DOE_TOXIC_RUSH_V1' not in combat:
    combat = replace_once(combat, response_anchor, response_block, "John Doe Toxic Rush Entity-turn proc")

for marker in (
    'JOHN_DOE_TOXIC_RUSH_V1',
    'JOHN_DOE_TOXIC_RUSH_PROC_PERCENT = 26',
    'c.entityKey == "john_doe"',
    'intent == Intent.EVADE',
    'momentumBefore - 1',
):
    if marker not in combat:
        raise RuntimeError("John Doe Toxic Rush runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'johnDoeToxicRushIsEntityTurnOnlyTempoPressureWithEvadeCounterplay' not in test:
    tests = r'''
  @Test fun johnDoeToxicRushIsEntityTurnOnlyTempoPressureWithEvadeCounterplay() {
    assertEquals(26, CombatRuntime.JOHN_DOE_TOXIC_RUSH_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ thế thủ vững và chờ phản ứng")
      if (result.reply.contains("Toxic Rush:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("26% John Doe proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Momentum"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))

    var evadeResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "EVADE", "né sang bên và giữ khoảng cách")
      if (result.reply.contains("Toxic Rush:")) {
        evadeResult = result
        break
      }
    }

    assertNotNull("John Doe proc must also be reachable on an EVADE Entity-response turn", evadeResult)
    assertTrue(evadeResult!!.reply, evadeResult!!.reply.contains("kỹ năng bị vô hiệu"))
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: John Doe Toxic Rush (ACTIVE, 26% on John Doe Entity turn, EVADE counterplay).")