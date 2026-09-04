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


# SMILER_DARK_ROUTE_FEINT_V1
#
# Active Entity skill. Exactly one 27% proc roll is evaluated only during the
# Smiler's own Entity-response turn. It cannot trigger from taking damage, HP
# thresholds, Party movement, player attacks, bleed ticks, or other out-of-turn events.
#
# Canon fit: the Smiler is primarily visible as a bright face-like feature in
# darkness, uses blacked-out areas and dark corners, and may keep its distance to
# pressure prey into choosing a bad route. Dark Route Feint weaponizes that route
# pressure without inventing a projectile, teleport, or hard crowd-control effect.
#
# Balance: 27% per Smiler Entity turn. No direct damage, no Stun, no Cover loss,
# and no persistent stack. On proc it removes only 5 Escape progress. READ fully
# counters the feint by identifying the false route before committing to it.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val SMILER_DARK_ROUTE_FEINT_PROC_PERCENT = 27
  private const val SMILER_DARK_ROUTE_FEINT_ESCAPE_LOSS = 5
'''
if 'SMILER_DARK_ROUTE_FEINT_PROC_PERCENT = 27' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Smiler Dark Route Feint constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // SMILER_DARK_ROUTE_FEINT_V1: one proc check on the Smiler's Entity response turn only.
      if (c.entityKey == "smiler" &&
          roll(c.copy(eventCounter = c.eventCounter + 2887), 100) < SMILER_DARK_ROUTE_FEINT_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "Dark Route Feint: proc ${SMILER_DARK_ROUTE_FEINT_PROC_PERCENT}% nhưng Party đọc được lối giả trong bóng tối; kỹ năng bị vô hiệu."
        } else {
          val escapeBefore = c.escapeProgress
          c = c.copy(
            escapeProgress = max(0, escapeBefore - SMILER_DARK_ROUTE_FEINT_ESCAPE_LOSS)
          )
          log += "Dark Route Feint: Smiler giữ khoảng cách và ép Party lệch sang lối tối sai; proc ${SMILER_DARK_ROUTE_FEINT_PROC_PERCENT}%, Escape ${escapeBefore} -> ${c.escapeProgress}."
        }
      }
'''
if 'SMILER_DARK_ROUTE_FEINT_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Smiler Dark Route Feint Entity-turn proc",
    )

for marker in (
    'SMILER_DARK_ROUTE_FEINT_V1',
    'SMILER_DARK_ROUTE_FEINT_PROC_PERCENT = 27',
    'SMILER_DARK_ROUTE_FEINT_ESCAPE_LOSS = 5',
    'c.entityKey == "smiler"',
    'intent == Intent.READ',
    'escapeProgress = max(0, escapeBefore - SMILER_DARK_ROUTE_FEINT_ESCAPE_LOSS)',
):
    if marker not in combat:
        raise RuntimeError("Smiler Dark Route Feint runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'smilerDarkRouteFeintIsEntityTurnOnlyEscapePressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun smilerDarkRouteFeintIsEntityTurnOnlyEscapePressureWithReadCounterplay() {
    assertEquals(27, CombatRuntime.SMILER_DARK_ROUTE_FEINT_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "smiler")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ đội hình phòng thủ và không lao theo khuôn mặt sáng")
      if (result.reply.contains("Dark Route Feint:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("27% Smiler proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Escape 40 -> 35"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(35, CombatRuntime.active(pressureResult!!.state)!!.escapeProgress)

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "smiler")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.escapeProgress" to "40"
      ))
      val result = CombatRuntime.resolve(state, "READ", "đọc bóng tối và xác định lối giả trước khi di chuyển")
      if (result.reply.contains("Dark Route Feint:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Smiler proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(readResult!!.reply, readResult!!.reply.contains("Escape 40 -> 35"))
    assertTrue(CombatRuntime.active(readResult!!.state)!!.escapeProgress >= 40)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Smiler Dark Route Feint (ACTIVE, 27% on Smiler Entity turn, READ counterplay).")
