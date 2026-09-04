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


# WRETCH_SCENTLINE_RUSH_V1
#
# Active Entity skill. Exactly one 30% proc roll is evaluated only during
# Wretch's own Entity-response turn, after the Party actor has resolved and only
# if Wretch actually receives that response turn. It cannot trigger from taking
# damage, HP thresholds, player actions, status ticks, or any other out-of-turn event.
#
# Canon fit: Wretch hunts through sound and scent, moves irregularly, tolerates pain,
# and threatens prey at close range. Scentline Rush models a short predatory surge
# that reclaims a little escape distance without inventing ranged or supernatural power.
#
# Balance: 30% per Wretch Entity turn. No direct damage, no Stun, no Cover break,
# no persistent stack. On proc it removes at most 6 Escape progress. EVADE fully
# counters it by breaking the immediate rush line.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val WRETCH_SCENTLINE_RUSH_PROC_PERCENT = 30
'''
if 'WRETCH_SCENTLINE_RUSH_PROC_PERCENT = 30' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Wretch Scentline Rush constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // WRETCH_SCENTLINE_RUSH_V1: one proc check on Wretch's Entity response turn only.
      if (c.entityKey == "wretch" &&
          roll(c.copy(eventCounter = c.eventCounter + 3527), 100) < WRETCH_SCENTLINE_RUSH_PROC_PERCENT) {
        if (intent == Intent.EVADE) {
          log += "Scentline Rush: proc ${WRETCH_SCENTLINE_RUSH_PROC_PERCENT}% nhưng Party né đổi góc khỏi đường lao của Wretch; kỹ năng bị vô hiệu."
        } else {
          val escapeBefore = c.escapeProgress
          val escapeAfter = max(0, escapeBefore - 6)
          c = c.copy(escapeProgress = escapeAfter)
          log += "Scentline Rush: Wretch bám tiếng động và mùi để lao cắt đường thoát; proc ${WRETCH_SCENTLINE_RUSH_PROC_PERCENT}%, Escape ${escapeBefore}% -> ${escapeAfter}%."
        }
      }
'''
if 'WRETCH_SCENTLINE_RUSH_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Wretch Scentline Rush Entity-turn proc",
    )

for marker in (
    'WRETCH_SCENTLINE_RUSH_V1',
    'WRETCH_SCENTLINE_RUSH_PROC_PERCENT = 30',
    'c.entityKey == "wretch"',
    'intent == Intent.EVADE',
    'max(0, escapeBefore - 6)',
):
    if marker not in combat:
        raise RuntimeError("Wretch Scentline Rush runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'wretchScentlineRushIsEntityTurnOnlyEscapePressureWithEvadeCounterplay' not in test:
    tests = r'''
  @Test fun wretchScentlineRushIsEntityTurnOnlyEscapePressureWithEvadeCounterplay() {
    assertEquals(30, CombatRuntime.WRETCH_SCENTLINE_RUSH_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "wretch")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ thế phòng thủ chắc chắn")
      if (result.reply.contains("Scentline Rush:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("30% Wretch proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Escape 40% -> 34%"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(34, CombatRuntime.active(pressureResult!!.state)!!.escapeProgress)

    var evadeResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "wretch")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "EVADE", "né gấp sang góc khác để phá đường lao")
      if (result.reply.contains("Scentline Rush:")) {
        evadeResult = result
        break
      }
    }

    assertNotNull("Wretch proc must also be reachable on an EVADE Entity-response turn", evadeResult)
    assertTrue(evadeResult!!.reply, evadeResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(CombatRuntime.active(evadeResult!!.state)!!.escapeProgress >= 40)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Wretch Scentline Rush (ACTIVE, 30% on Wretch Entity turn, EVADE counterplay).")
