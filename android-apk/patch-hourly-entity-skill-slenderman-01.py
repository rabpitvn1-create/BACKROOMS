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


# SLENDERMAN_SILENT_INTERCEPT_V1
#
# Active Entity skill. Exactly one 24% proc roll is evaluated only during
# Slenderman's own Entity-response turn, after the Party actor has resolved and
# only if Slenderman actually receives that response turn. It cannot trigger
# from taking damage, HP thresholds, player actions, status ticks, or any other
# out-of-turn event.
#
# Canon fit: the repository only commits to Slenderman's hostile roaming/stalking
# baseline and explicitly forbids importing unsupported supernatural abilities.
# Silent Intercept therefore models patient physical route denial and presence at
# a bad angle, without teleportation, mind control, forced Stun, or ranged magic.
#
# Balance: 24% per Slenderman Entity turn. No direct damage, no Stun, no Escape
# loss, no persistent stack. On proc it degrades Cover by exactly one step.
# READ fully counters it by identifying the approach before the interception.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT = 24
'''
if 'SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT = 24' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Slenderman Silent Intercept constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // SLENDERMAN_SILENT_INTERCEPT_V1: one proc check on Slenderman's Entity response turn only.
      if (c.entityKey == "slenderman" &&
          roll(c.copy(eventCounter = c.eventCounter + 3413), 100) < SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "Silent Intercept: proc ${SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT}% nhưng Party đọc được hướng tiếp cận của Slenderman; kỹ năng bị vô hiệu."
        } else {
          val coverBefore = c.cover
          val coverAfter = when (coverBefore) {
            Cover.HARD -> Cover.PARTIAL
            Cover.PARTIAL -> Cover.EXPOSED
            Cover.EXPOSED -> Cover.EXPOSED
          }
          c = c.copy(cover = coverAfter)
          log += "Silent Intercept: Slenderman âm thầm khóa góc rút và ép Party rời vị trí che chắn; proc ${SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT}%, Cover ${coverBefore} -> ${coverAfter}."
        }
      }
'''
if 'SLENDERMAN_SILENT_INTERCEPT_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Slenderman Silent Intercept Entity-turn proc",
    )

for marker in (
    'SLENDERMAN_SILENT_INTERCEPT_V1',
    'SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT = 24',
    'c.entityKey == "slenderman"',
    'intent == Intent.READ',
    'Cover.HARD -> Cover.PARTIAL',
    'Cover.PARTIAL -> Cover.EXPOSED',
):
    if marker not in combat:
        raise RuntimeError("Slenderman Silent Intercept runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'slendermanSilentInterceptIsEntityTurnOnlyCoverPressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun slendermanSilentInterceptIsEntityTurnOnlyCoverPressureWithReadCounterplay() {
    assertEquals(24, CombatRuntime.SLENDERMAN_SILENT_INTERCEPT_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "slenderman")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to "HARD"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "giữ vị trí che chắn và theo dõi Slenderman")
      if (result.reply.contains("Silent Intercept:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("24% Slenderman proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Cover HARD -> PARTIAL"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertEquals(CombatRuntime.Cover.PARTIAL, CombatRuntime.active(pressureResult!!.state)!!.cover)

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "slenderman")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to "HARD"
      ))
      val result = CombatRuntime.resolve(state, "READ", "đọc hướng tiếp cận và giữ Slenderman trong tầm quan sát")
      if (result.reply.contains("Silent Intercept:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Slenderman proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertEquals(CombatRuntime.Cover.HARD, CombatRuntime.active(readResult!!.state)!!.cover)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Slenderman Silent Intercept (ACTIVE, 24% on Slenderman Entity turn, READ counterplay).")
