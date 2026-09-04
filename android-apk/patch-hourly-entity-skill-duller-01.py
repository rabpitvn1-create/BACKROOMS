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


# DULLER_STILLFRAME_LUNGE_V1
#
# Active Entity skill. One proc roll is evaluated only inside Duller's own
# Entity-response turn. It cannot trigger from taking damage, HP thresholds,
# Party actions before the Entity response, or any other out-of-turn event.
#
# Canon fit: Dullers are established as humanoid distortions that can watch for a
# long time and remain still enough to be mistaken for a person before closing in.
# Stillframe Lunge turns that deceptive stillness into a short burst of pressure.
#
# Balance: 23% per Duller Entity turn, no bonus damage, no Stun, no forced escape
# loss. On proc, direct Entity actions gain only +10 percentage points Accuracy for
# that turn. GUARD explicitly counters the entire skill by holding formation and
# denying the false-distance opening.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val DULLER_STILLFRAME_LUNGE_PROC_PERCENT = 23
  private const val DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS = 10
'''
if 'DULLER_STILLFRAME_LUNGE_PROC_PERCENT = 23' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Duller Stillframe Lunge constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // DULLER_STILLFRAME_LUNGE_V1: exactly one proc check on Duller's Entity response turn.
      val dullerStillframeLungeProc = c.entityKey == "duller" &&
        roll(c.copy(eventCounter = c.eventCounter + 1973), 100) < DULLER_STILLFRAME_LUNGE_PROC_PERCENT
      val dullerStillframeLungeActive = dullerStillframeLungeProc && intent != Intent.GUARD
      if (dullerStillframeLungeProc) {
        if (intent == Intent.GUARD) {
          log += "Stillframe Lunge: proc ${DULLER_STILLFRAME_LUNGE_PROC_PERCENT}% nhưng Party giữ đội hình và khoảng cách sau vật che; kỹ năng bị vô hiệu."
        } else {
          log += "Stillframe Lunge: Duller bất động đánh lừa khoảng cách rồi lao tới; proc ${DULLER_STILLFRAME_LUNGE_PROC_PERCENT}%, +$DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS điểm % Accuracy trong Entity turn này."
        }
      }
'''
if 'DULLER_STILLFRAME_LUNGE_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Duller Stillframe Lunge Entity-turn proc",
    )

accuracy_old = '''        val killerAccuracyBonus =
          (if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0) +
          (if (jeffSilentStalker && entityTargets.size == 1) JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS else 0)
'''
accuracy_new = '''        val killerAccuracyBonus =
          (if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0) +
          (if (jeffSilentStalker && entityTargets.size == 1) JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS else 0) +
          (if (dullerStillframeLungeActive) DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS else 0)
'''
if '(if (dullerStillframeLungeActive) DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS else 0)' not in combat:
    combat = replace_once(
        combat,
        accuracy_old,
        accuracy_new,
        "Duller Stillframe Lunge Accuracy bonus",
    )

for marker in (
    'DULLER_STILLFRAME_LUNGE_V1',
    'DULLER_STILLFRAME_LUNGE_PROC_PERCENT = 23',
    'DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS = 10',
    'c.entityKey == "duller"',
    'intent != Intent.GUARD',
    'if (dullerStillframeLungeActive) DULLER_STILLFRAME_LUNGE_ACCURACY_BONUS else 0',
):
    if marker not in combat:
        raise RuntimeError("Duller Stillframe Lunge runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'dullerStillframeLungeIsEntityTurnOnlyAccuracyPressureWithGuardCounterplay' not in test:
    tests = r'''
  @Test fun dullerStillframeLungeIsEntityTurnOnlyAccuracyPressureWithGuardCounterplay() {
    assertEquals(23, CombatRuntime.DULLER_STILLFRAME_LUNGE_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "duller")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "OTHER", "giữ đội hình và quan sát phía trước")
      if (result.reply.contains("Stillframe Lunge:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("23% Duller proc must be reachable across deterministic Entity turns", pressureResult)
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("+10 điểm % Accuracy"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))

    var guardResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "duller")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ đội hình và khoảng cách sau vật che")
      if (result.reply.contains("Stillframe Lunge:")) {
        guardResult = result
        break
      }
    }

    assertNotNull("Duller proc must also be reachable on a GUARD Entity-response turn", guardResult)
    assertTrue(guardResult!!.reply, guardResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(guardResult!!.reply, guardResult!!.reply.contains("+10 điểm % Accuracy"))
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Duller Stillframe Lunge (ACTIVE, 23% on Duller Entity turn, GUARD counterplay).")
