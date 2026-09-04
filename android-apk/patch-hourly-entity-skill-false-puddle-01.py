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


# FALSE_PUDDLE_SUBSURFACE_LATCH_V1
#
# Active Entity skill. Exactly one proc roll is evaluated only during False Puddle's
# own Entity-response turn. It cannot trigger from Party movement, taking damage,
# HP thresholds, contact events, or any other out-of-turn condition.
#
# Canon fit: False Puddle hides a toothed mouth beneath a flat liquid-like surface,
# reacts to footstep vibration, and can fake a harmless/dead state. Subsurface Latch
# represents the concealed mouth actively contracting under nearby footing once the
# encounter is already underway, without inventing mobility or ranged reach.
#
# Balance: 22% per False Puddle Entity turn. No direct damage, no Stun, no Cover loss,
# and no persistent stacking. On proc it trims only 8 Escape progress and 1 Momentum.
# EVADE fully counters the latch by getting off the suspect surface before it closes.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT = 22
  private const val FALSE_PUDDLE_SUBSURFACE_LATCH_ESCAPE_LOSS = 8
  private const val FALSE_PUDDLE_SUBSURFACE_LATCH_MOMENTUM_LOSS = 1
'''
if 'FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT = 22' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "False Puddle Subsurface Latch constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // FALSE_PUDDLE_SUBSURFACE_LATCH_V1: one proc check on False Puddle's Entity response turn only.
      if (c.entityKey == "false_puddle" &&
          roll(c.copy(eventCounter = c.eventCounter + 2269), 100) < FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT) {
        if (intent == Intent.EVADE) {
          log += "Subsurface Latch: proc ${FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT}% nhưng Party rời khỏi bề mặt khả nghi trước khi miệng ngầm khép lại; kỹ năng bị vô hiệu."
        } else {
          val escapeBefore = c.escapeProgress
          val momentumBefore = c.momentum
          c = c.copy(
            escapeProgress = max(0, escapeBefore - FALSE_PUDDLE_SUBSURFACE_LATCH_ESCAPE_LOSS),
            momentum = max(-3, momentumBefore - FALSE_PUDDLE_SUBSURFACE_LATCH_MOMENTUM_LOSS)
          )
          log += "Subsurface Latch: False Puddle khép miệng ngầm dưới chân mục tiêu; proc ${FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT}%, Escape ${escapeBefore} -> ${c.escapeProgress}, Momentum ${momentumBefore} -> ${c.momentum}."
        }
      }
'''
if 'FALSE_PUDDLE_SUBSURFACE_LATCH_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "False Puddle Subsurface Latch Entity-turn proc",
    )

for marker in (
    'FALSE_PUDDLE_SUBSURFACE_LATCH_V1',
    'FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT = 22',
    'FALSE_PUDDLE_SUBSURFACE_LATCH_ESCAPE_LOSS = 8',
    'FALSE_PUDDLE_SUBSURFACE_LATCH_MOMENTUM_LOSS = 1',
    'c.entityKey == "false_puddle"',
    'intent == Intent.EVADE',
    'escapeProgress = max(0, escapeBefore - FALSE_PUDDLE_SUBSURFACE_LATCH_ESCAPE_LOSS)',
):
    if marker not in combat:
        raise RuntimeError("False Puddle Subsurface Latch runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'falsePuddleSubsurfaceLatchIsEntityTurnOnlyPressureWithEvadeCounterplay' not in test:
    tests = r'''
  @Test fun falsePuddleSubsurfaceLatchIsEntityTurnOnlyPressureWithEvadeCounterplay() {
    assertEquals(22, CombatRuntime.FALSE_PUDDLE_SUBSURFACE_LATCH_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "false_puddle")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40",
        "combat.momentum" to "1"
      ))
      val result = CombatRuntime.resolve(state, "OTHER", "giữ vị trí và quan sát bề mặt")
      if (result.reply.contains("Subsurface Latch:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("22% False Puddle proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Escape 40 -> 32"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(32, CombatRuntime.active(pressureResult!!.state)!!.escapeProgress)

    var evadeResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "false_puddle")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40",
        "combat.momentum" to "1"
      ))
      val result = CombatRuntime.resolve(state, "EVADE", "né khỏi vũng khả nghi và đổi góc di chuyển")
      if (result.reply.contains("Subsurface Latch:")) {
        evadeResult = result
        break
      }
    }

    assertNotNull("False Puddle proc must also be reachable on an EVADE Entity-response turn", evadeResult)
    assertTrue(evadeResult!!.reply, evadeResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(evadeResult!!.reply, evadeResult!!.reply.contains("Escape 40 -> 32"))
    assertTrue(CombatRuntime.active(evadeResult!!.state)!!.escapeProgress >= 50)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: False Puddle Subsurface Latch (ACTIVE, 22% on False Puddle Entity turn, EVADE counterplay).")
