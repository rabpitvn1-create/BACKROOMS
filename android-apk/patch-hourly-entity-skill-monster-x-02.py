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


# MONSTER_X_BLOOD_SCENT_PIVOT_V1
#
# Passive Entity skill. Exactly one 22% proc roll is evaluated only during Monster X's
# own Entity-response turn. It cannot trigger from taking damage, Party actions, HP loss,
# status ticks, or any other out-of-turn event.
#
# Canon/mechanical fit: Monster X already hunts through relentless close pressure,
# persistent Bleeding and delayed Stun. Blood-Scent Pivot models it reading the Party's
# movement through scent and abruptly re-cutting the pursuit line rather than adding a
# new supernatural power.
#
# Balance: no direct damage, no Stun, no Cover break, no Escape loss and no persistent
# stack. On proc it lowers Momentum by at most 1. READ fully counters the tracking feint.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT = 22
'''
if 'MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT = 22' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Monster X Blood-Scent Pivot constant",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // MONSTER_X_BLOOD_SCENT_PIVOT_V1: one proc check on Monster X's Entity response turn only.
      if (c.entityKey == "monster_x" &&
          roll(c.copy(eventCounter = c.eventCounter + 3629), 100) < MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "Blood-Scent Pivot: proc ${MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT}% nhưng Party đọc được hướng đổi tuyến; kỹ năng bị vô hiệu."
        } else {
          val momentumBefore = c.momentum
          val momentumAfter = max(-3, momentumBefore - 1)
          c = c.copy(momentum = momentumAfter)
          log += "Blood-Scent Pivot: Monster X bẻ hướng săn theo dấu mùi; proc ${MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT}%, Momentum $momentumBefore -> $momentumAfter."
        }
      }
'''
if 'MONSTER_X_BLOOD_SCENT_PIVOT_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Monster X Blood-Scent Pivot Entity-turn proc",
    )

for marker in (
    'MONSTER_X_BLOOD_SCENT_PIVOT_V1',
    'MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT = 22',
    'c.entityKey == "monster_x"',
    'intent == Intent.READ',
    'momentumBefore - 1',
):
    if marker not in combat:
        raise RuntimeError("Monster X Blood-Scent Pivot runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'monsterXBloodScentPivotIsEntityTurnOnlyTempoPressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun monsterXBloodScentPivotIsEntityTurnOnlyTempoPressureWithReadCounterplay() {
    assertEquals(22, CombatRuntime.MONSTER_X_BLOOD_SCENT_PIVOT_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "monster_x")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "MOVE", "đổi góc chạy sang nhánh hành lang bên phải")
      if (result.reply.contains("Blood-Scent Pivot:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("22% Monster X proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Momentum"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Blood-Scent Pivot: Monster X") && pressureResult!!.reply.contains("STUN"))

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "monster_x")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "READ", "đọc nhịp săn và quan sát hướng Monster X đổi tuyến")
      if (result.reply.contains("Blood-Scent Pivot:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Monster X proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(readResult!!.reply, readResult!!.reply.contains("Momentum 3 -> 2"))
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Monster X Blood-Scent Pivot (PASSIVE, 22% on Monster X Entity turn, READ counterplay).")
