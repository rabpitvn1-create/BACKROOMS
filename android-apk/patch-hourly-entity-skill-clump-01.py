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


# CLUMP_TANGLE_DRAG_V1
#
# Active Entity skill. The proc is evaluated only inside Clump's Entity-response
# branch, after the current Party actor has resolved and only if Clump actually
# receives its response turn. It therefore cannot proc from taking damage, HP
# thresholds, or any out-of-turn event.
#
# Balance: 24% per Clump Entity turn. It adds no direct damage and no Stun. A
# successful proc degrades cover by one step, trims 10 escape progress, and costs
# one momentum. EVADE is explicit counterplay and negates the whole skill.
combat = COMBAT.read_text(encoding="utf-8")

# This late patch runs after the AP/interleaved-combat finalizers. Anchor to the
# final Party-turn context constant, which is deliberately retained by those
# finalizers, rather than to early legacy combat HP constants that are rewritten.
constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val CLUMP_TANGLE_DRAG_PROC_PERCENT = 24
  private const val CLUMP_TANGLE_DRAG_ESCAPE_LOSS = 10
'''
if 'CLUMP_TANGLE_DRAG_PROC_PERCENT = 24' not in combat:
    combat = replace_once(combat, constant_anchor, constant_anchor + constant_block, "Clump Tangle Drag constants")

# Interleaved combat V2 preserves the full Entity roster for status/AoE work and
# introduces entityDirectTargets only for the ordinary direct-response action.
# Its final action-budget line is the stable boundary of that Clump response turn.
response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // CLUMP_TANGLE_DRAG_V1: exactly one proc check on Clump's own Entity response turn.
      if (c.entityKey == "clump" &&
          roll(c.copy(eventCounter = c.eventCounter + 1907), 100) < CLUMP_TANGLE_DRAG_PROC_PERCENT) {
        if (intent == Intent.EVADE) {
          log += "Tangle Drag: proc ${CLUMP_TANGLE_DRAG_PROC_PERCENT}% nhưng Party né khỏi vòng chi của Clump; không mất Cover hay tiến độ thoát."
        } else {
          val coverBefore = c.cover
          val coverAfter = when (coverBefore) {
            Cover.HARD -> Cover.PARTIAL
            Cover.PARTIAL -> Cover.EXPOSED
            Cover.EXPOSED -> Cover.EXPOSED
          }
          val escapeBefore = c.escapeProgress
          c = c.copy(
            cover = coverAfter,
            escapeProgress = max(0, escapeBefore - CLUMP_TANGLE_DRAG_ESCAPE_LOSS),
            momentum = max(-3, c.momentum - 1)
          )
          log += "Tangle Drag: Clump quét nhiều chi kéo đội hình khỏi vị trí; proc ${CLUMP_TANGLE_DRAG_PROC_PERCENT}%, Cover ${coverBefore.name} -> ${coverAfter.name}, tiến độ thoát ${escapeBefore}% -> ${c.escapeProgress}%."
        }
      }
'''
combat = replace_once(combat, response_anchor, response_block, "Clump Tangle Drag Entity-turn proc")

for marker in (
    'CLUMP_TANGLE_DRAG_V1',
    'CLUMP_TANGLE_DRAG_PROC_PERCENT = 24',
    'c.entityKey == "clump"',
    'intent == Intent.EVADE',
    'Cover.HARD -> Cover.PARTIAL',
    'escapeBefore - CLUMP_TANGLE_DRAG_ESCAPE_LOSS',
):
    if marker not in combat:
        raise RuntimeError("Clump Tangle Drag runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'clumpTangleDragIsEntityTurnOnlyPressureWithEvadeCounterplay' not in test:
    tests = r'''
  @Test fun clumpTangleDragIsEntityTurnOnlyPressureWithEvadeCounterplay() {
    assertEquals(24, CombatRuntime.CLUMP_TANGLE_DRAG_PROC_PERCENT)
    var procCounter: Int? = null
    var procResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "clump")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to CombatRuntime.Cover.HARD.name,
        "combat.escapeProgress" to "50"
      ))
      val result = CombatRuntime.resolve(state, "OTHER", "giữ vị trí")
      if (result.reply.contains("Tangle Drag:")) {
        procCounter = counter
        procResult = result
        break
      }
    }

    assertNotNull("24% Clump proc must be reachable across deterministic Entity turns", procCounter)
    assertNotNull(procResult)
    val pressured = CombatRuntime.active(procResult!!.state)!!
    assertEquals(CombatRuntime.Cover.PARTIAL, pressured.cover)
    assertEquals(40, pressured.escapeProgress)

    var evade = CombatRuntime.start(GameState.initial(), "clump")
    evade = evade.copy(metadata = evade.metadata + mapOf(
      "combat.eventCounter" to procCounter!!.toString(),
      "combat.cover" to CombatRuntime.Cover.HARD.name,
      "combat.escapeProgress" to "50"
    ))
    val evaded = CombatRuntime.resolve(evade, "EXECUTE", "Cả Party cùng né tránh")
    assertTrue(evaded.reply, evaded.reply.contains("Tangle Drag:"))
    assertTrue(evaded.reply, evaded.reply.contains("không mất Cover hay tiến độ thoát"))
    val safe = CombatRuntime.active(evaded.state)!!
    assertEquals(CombatRuntime.Cover.HARD, safe.cover)
    // EVADE itself grants +10 escape from this starting state; Tangle Drag must not subtract it.
    assertEquals(60, safe.escapeProgress)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Clump Tangle Drag (ACTIVE, 24% on Clump Entity turn, EVADE counterplay).")
