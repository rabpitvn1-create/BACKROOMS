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


# HOSTILE_FACELING_FALSE_APPROACH_V1
#
# Active Entity skill. One proc roll is evaluated only inside Hostile Faceling's
# own Entity-response turn. It cannot trigger from taking damage, HP thresholds,
# Party actions before the Entity response, or any other out-of-turn event.
#
# Canon fit: Hostile Facelings are humanlike predators that use humanlike behavior
# to lower suspicion before hunting at close enough distance. False Approach turns
# that deceptive body language into a brief attack-opening rather than raw damage.
#
# Balance: 25% per Hostile Faceling Entity turn, no bonus damage, no Stun, no forced
# escape loss, and no persistence across turns. On proc, direct Entity actions gain
# only +8 percentage points to hit chance for that turn. READ fully counters the
# feint by explicitly studying the Entity's telegraph instead of trusting its gait.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  internal const val DULLER_STILLFRAME_LUNGE_PROC_PERCENT = 23\n  private const val DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS = 10\n'
constant_block = constant_anchor + '''  internal const val HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT = 25
  private const val HOSTILE_FACELING_FALSE_APPROACH_ACCURACY_BONUS = 8
'''
if 'HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT = 25' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_block,
        "Hostile Faceling False Approach constants",
    )

response_anchor = '    // Enemy response. READ/guard/evasion reduce expected incoming damage; attacking blindly is riskier.\n'
response_block = response_anchor + '''    // HOSTILE_FACELING_FALSE_APPROACH_V1: exactly one proc check on Hostile Faceling's Entity response turn.
    val hostileFacelingFalseApproachProc = c.entityKey == "hostile_faceling" &&
      roll(c.copy(eventCounter = c.eventCounter + 2137), 100) < HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT
    val hostileFacelingFalseApproachActive = hostileFacelingFalseApproachProc && intent != Intent.READ
    if (hostileFacelingFalseApproachProc) {
      if (intent == Intent.READ) {
        log += "False Approach: proc ${HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT}% nhưng Party chủ động đọc telegraph và nhận ra dáng người giả; kỹ năng bị vô hiệu."
      } else {
        log += "False Approach: Hostile Faceling bắt chước dáng người vô hại rồi đổi nhịp áp sát; proc ${HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT}%, +$HOSTILE_FACELING_FALSE_APPROACH_ACCURACY_BONUS điểm % Accuracy trong Entity turn này."
      }
    }
'''
if 'HOSTILE_FACELING_FALSE_APPROACH_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Hostile Faceling False Approach Entity-turn proc",
    )

enemy_chance_old = '    val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7).coerceIn(8, 88)\n'
enemy_chance_new = '''    val enemyChance = (
      profile.aggression * 8 - defense + max(0, -c.momentum) * 7 +
        (if (hostileFacelingFalseApproachActive) HOSTILE_FACELING_FALSE_APPROACH_ACCURACY_BONUS else 0)
      ).coerceIn(8, 88)
'''
if '(if (hostileFacelingFalseApproachActive) HOSTILE_FACELING_FALSE_APPROACH_ACCURACY_BONUS else 0)' not in combat:
    combat = replace_once(
        combat,
        enemy_chance_old,
        enemy_chance_new,
        "Hostile Faceling False Approach Accuracy bonus",
    )

for marker in (
    'HOSTILE_FACELING_FALSE_APPROACH_V1',
    'HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT = 25',
    'HOSTILE_FACELING_FALSE_APPROACH_ACCURACY_BONUS = 8',
    'c.entityKey == "hostile_faceling"',
    'intent != Intent.READ',
    'if (hostileFacelingFalseApproachActive) HOSTILE_FACELING_FALSE_APPROACH_ACCURACY_BONUS else 0',
):
    if marker not in combat:
        raise RuntimeError("Hostile Faceling False Approach runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'hostileFacelingFalseApproachIsEntityTurnOnlyAccuracyPressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun hostileFacelingFalseApproachIsEntityTurnOnlyAccuracyPressureWithReadCounterplay() {
    assertEquals(25, CombatRuntime.HOSTILE_FACELING_FALSE_APPROACH_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "hostile_faceling")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "OTHER", "tiến lên quan sát hình người phía trước")
      if (result.reply.contains("False Approach:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("25% Hostile Faceling proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("+8 điểm % Accuracy"))

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "hostile_faceling")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "READ", "quan sát kỹ dáng đi và đọc nhịp tiếp cận của thực thể")
      if (result.reply.contains("False Approach:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Hostile Faceling proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(readResult!!.reply, readResult!!.reply.contains("+8 điểm % Accuracy"))
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Hostile Faceling False Approach (ACTIVE, 25% on Hostile Faceling Entity turn, READ counterplay).")
