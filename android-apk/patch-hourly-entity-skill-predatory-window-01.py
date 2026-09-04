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


# PREDATORY_WINDOW_FALSE_VISTA_LURE_V1
#
# Active Entity skill. Exactly one 25% proc roll is evaluated only during
# Predatory Window's own Entity-response turn, after the Party actor has resolved
# and only if the Entity actually receives that response turn. It cannot trigger
# from taking damage, HP thresholds, player actions, status ticks, or any other
# out-of-turn event.
#
# Canon fit: Predatory Window is stationary and hunts by presenting a misleading
# view/whispering lure, drawing prey close enough for a short-range grab. False
# Vista Lure models that bait stealing a little escape progress without inventing
# locomotion, ranged damage, teleportation, or reality-warping power.
#
# Balance: 25% per Predatory Window Entity turn. No direct damage, no Stun, no
# Cover break, no persistent stack. On proc it removes at most 5 Escape progress.
# READ fully counters it by recognizing the false scene before committing to it.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT = 25
  private const val PREDATORY_WINDOW_FALSE_VISTA_LURE_ESCAPE_LOSS = 5
'''
if 'PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT = 25' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Predatory Window False Vista Lure constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // PREDATORY_WINDOW_FALSE_VISTA_LURE_V1: one proc check on Predatory Window's Entity response turn only.
      if (c.entityKey == "predatory_window" &&
          roll(c.copy(eventCounter = c.eventCounter + 3619), 100) < PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "False Vista Lure: proc ${PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT}% nhưng Party nhận ra khung cảnh giả sau kính; kỹ năng bị vô hiệu."
        } else {
          val escapeBefore = c.escapeProgress
          val escapeAfter = max(0, escapeBefore - PREDATORY_WINDOW_FALSE_VISTA_LURE_ESCAPE_LOSS)
          c = c.copy(escapeProgress = escapeAfter)
          log += "False Vista Lure: Predatory Window dùng cảnh giả và tín hiệu dụ kéo Party lệch khỏi tuyến thoát; proc ${PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT}%, Escape ${escapeBefore}% -> ${escapeAfter}%."
        }
      }
'''
if 'PREDATORY_WINDOW_FALSE_VISTA_LURE_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Predatory Window False Vista Lure Entity-turn proc",
    )

for marker in (
    'PREDATORY_WINDOW_FALSE_VISTA_LURE_V1',
    'PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT = 25',
    'c.entityKey == "predatory_window"',
    'intent == Intent.READ',
    'max(0, escapeBefore - PREDATORY_WINDOW_FALSE_VISTA_LURE_ESCAPE_LOSS)',
):
    if marker not in combat:
        raise RuntimeError("Predatory Window False Vista Lure runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'predatoryWindowFalseVistaLureIsEntityTurnOnlyEscapePressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun predatoryWindowFalseVistaLureIsEntityTurnOnlyEscapePressureWithReadCounterplay() {
    assertEquals(25, CombatRuntime.PREDATORY_WINDOW_FALSE_VISTA_LURE_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "predatory_window")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ vị trí phòng thủ, không phân tích khung cảnh")
      if (result.reply.contains("False Vista Lure:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("25% Predatory Window proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Escape 40% -> 35%"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(35, CombatRuntime.active(pressureResult!!.state)!!.escapeProgress)

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "predatory_window")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "READ", "quan sát kỹ cảnh phản chiếu và dấu hiệu bất thường sau kính")
      if (result.reply.contains("False Vista Lure:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Predatory Window proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(CombatRuntime.active(readResult!!.state)!!.escapeProgress >= 40)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Predatory Window False Vista Lure (ACTIVE, 25% on Predatory Window Entity turn, READ counterplay).")
