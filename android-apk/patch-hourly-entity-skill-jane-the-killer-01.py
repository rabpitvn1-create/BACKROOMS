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


# JANE_THE_KILLER_VEILED_ADVANCE_V1
#
# Passive Entity skill. Exactly one 30% proc roll is evaluated only during Jane's
# own Entity-response turn. It cannot trigger from taking damage, HP thresholds,
# Party movement, player attacks, bleed ticks, or any other out-of-turn event.
#
# Canon/mechanical fit: Jane already pressures targets through pursuit, marking,
# and close combat. Veiled Advance models her using the response window to erase a
# layer of positional safety rather than inventing ranged damage or hard control.
#
# Balance: 30% per Jane Entity turn. No direct damage, no Stun, no Escape loss,
# no persistent stack. On proc it lowers Cover by only one step. READ fully
# counters the passive by exposing Jane's approach before she can close safely.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val JANE_VEILED_ADVANCE_PROC_PERCENT = 30
'''
if 'JANE_VEILED_ADVANCE_PROC_PERCENT = 30' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Jane Veiled Advance constant",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // JANE_THE_KILLER_VEILED_ADVANCE_V1: one proc check on Jane's Entity response turn only.
      if (c.entityKey == "jane_the_killer" &&
          roll(c.copy(eventCounter = c.eventCounter + 2719), 100) < JANE_VEILED_ADVANCE_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "Veiled Advance: proc ${JANE_VEILED_ADVANCE_PROC_PERCENT}% nhưng Party đọc được hướng áp sát của Jane; kỹ năng bị vô hiệu."
        } else {
          val coverBefore = c.cover
          val coverAfter = when (coverBefore) {
            Cover.HARD -> Cover.PARTIAL
            Cover.PARTIAL -> Cover.EXPOSED
            Cover.EXPOSED -> Cover.EXPOSED
          }
          c = c.copy(cover = coverAfter)
          log += "Veiled Advance: Jane áp sát qua điểm mù; proc ${JANE_VEILED_ADVANCE_PROC_PERCENT}%, Cover ${coverBefore.name} -> ${coverAfter.name}."
        }
      }
'''
if 'JANE_THE_KILLER_VEILED_ADVANCE_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Jane Veiled Advance Entity-turn proc",
    )

for marker in (
    'JANE_THE_KILLER_VEILED_ADVANCE_V1',
    'JANE_VEILED_ADVANCE_PROC_PERCENT = 30',
    'c.entityKey == "jane_the_killer"',
    'intent == Intent.READ',
    'Cover.HARD -> Cover.PARTIAL',
    'Cover.PARTIAL -> Cover.EXPOSED',
):
    if marker not in combat:
        raise RuntimeError("Jane Veiled Advance runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'janeVeiledAdvanceIsEntityTurnOnlyCoverPressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun janeVeiledAdvanceIsEntityTurnOnlyCoverPressureWithReadCounterplay() {
    assertEquals(30, CombatRuntime.JANE_VEILED_ADVANCE_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "jane_the_killer")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to "HARD"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ vật che chắn và khóa tư thế phòng thủ")
      if (result.reply.contains("Veiled Advance:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("30% Jane proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Cover HARD -> PARTIAL"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(CombatRuntime.Cover.PARTIAL, CombatRuntime.active(pressureResult!!.state)!!.cover)

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "jane_the_killer")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to "HARD"
      ))
      val result = CombatRuntime.resolve(state, "READ", "đọc hướng áp sát của Jane và giữ vật che chắn")
      if (result.reply.contains("Veiled Advance:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Jane proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertFalse(readResult!!.reply, readResult!!.reply.contains("Cover HARD -> PARTIAL"))
    assertEquals(CombatRuntime.Cover.HARD, CombatRuntime.active(readResult!!.state)!!.cover)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Jane the Killer Veiled Advance (PASSIVE, 30% on Jane Entity turn, READ counterplay).")
