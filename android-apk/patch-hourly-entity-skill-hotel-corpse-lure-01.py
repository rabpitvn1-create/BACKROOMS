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


# HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_V1
#
# Passive Entity skill. Exactly one 26% proc roll is evaluated only during Hotel
# Corpse Lure's own Entity-response turn, after the Party actor has resolved and
# only if the Entity actually receives that response turn. It cannot trigger from
# taking damage, HP thresholds, player actions, status ticks, or any out-of-turn
# event.
#
# Canon fit: Hotel Corpse Lure is a hostile Level 5 lure. Deathly Stillness models
# the hesitation caused by its corpse-like bait without inventing ranged damage,
# teleportation, hard control, or any new supernatural capability.
#
# Balance: 26% per Hotel Corpse Lure Entity turn. No direct damage, no Stun, no
# Cover break, no Escape loss, and no persistent stack. On proc it removes at
# most 1 Momentum. READ fully counters it by identifying the lure before the
# hesitation costs Party tempo.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT = 26
  private const val HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_MOMENTUM_LOSS = 1
'''
if 'HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT = 26' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Hotel Corpse Lure Deathly Stillness constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_V1: one proc check on Hotel Corpse Lure's Entity response turn only.
      if (c.entityKey == "hotel_corpse_lure" &&
          roll(c.copy(eventCounter = c.eventCounter + 3671), 100) < HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT) {
        if (intent == Intent.READ) {
          log += "Deathly Stillness: proc ${HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT}% nhưng Party nhận ra mồi nhử giả xác; kỹ năng bị vô hiệu."
        } else {
          val momentumBefore = c.momentum
          val momentumAfter = max(-3, momentumBefore - HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_MOMENTUM_LOSS)
          c = c.copy(momentum = momentumAfter)
          log += "Deathly Stillness: Hotel Corpse Lure giữ bất động như xác chết khiến Party chững nhịp; proc ${HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT}%, Momentum ${momentumBefore} -> ${momentumAfter}."
        }
      }
'''
if 'HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_V1' not in combat:
    combat = replace_once(
        combat,
        response_anchor,
        response_block,
        "Hotel Corpse Lure Deathly Stillness Entity-turn proc",
    )

for marker in (
    'HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_V1',
    'HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT = 26',
    'c.entityKey == "hotel_corpse_lure"',
    'intent == Intent.READ',
    'max(-3, momentumBefore - HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_MOMENTUM_LOSS)',
):
    if marker not in combat:
        raise RuntimeError("Hotel Corpse Lure Deathly Stillness runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'hotelCorpseLureDeathlyStillnessIsEntityTurnOnlyMomentumPressureWithReadCounterplay' not in test:
    tests = r'''
  @Test fun hotelCorpseLureDeathlyStillnessIsEntityTurnOnlyMomentumPressureWithReadCounterplay() {
    assertEquals(26, CombatRuntime.HOTEL_CORPSE_LURE_DEATHLY_STILLNESS_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "hotel_corpse_lure")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "đỡ đòn và giữ tư thế phòng thủ")
      if (result.reply.contains("Deathly Stillness:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("26% Hotel Corpse Lure proc must be reachable across deterministic Entity turns", pressureResult)
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Momentum"))
    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
    assertTrue(CombatRuntime.active(pressureResult!!.state)!!.momentum <= 2)

    var readResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "hotel_corpse_lure")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.momentum" to "2"
      ))
      val result = CombatRuntime.resolve(state, "READ", "phân tích dấu hiệu của xác giả và nhận diện mồi nhử trong khách sạn")
      if (result.reply.contains("Deathly Stillness:")) {
        readResult = result
        break
      }
    }

    assertNotNull("Hotel Corpse Lure proc must also be reachable on a READ Entity-response turn", readResult)
    assertTrue(readResult!!.reply, readResult!!.reply.contains("kỹ năng bị vô hiệu"))
    assertTrue(CombatRuntime.active(readResult!!.state)!!.momentum >= 2)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Hotel Corpse Lure Deathly Stillness (PASSIVE, 26% on Hotel Corpse Lure Entity turn, READ counterplay).")
