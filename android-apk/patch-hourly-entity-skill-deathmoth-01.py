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


# DEATHMOTH_PHOTOTACTIC_SWARM_V1
#
# Active Entity skill. The proc is evaluated exactly once inside Deathmoth's own
# Entity-response turn. It cannot trigger from taking damage, HP thresholds,
# Party actions before the response phase, or any other out-of-turn event.
#
# Canon fit: Deathmoths attack in clusters and prioritize light and movement.
# Balance: 26% per Deathmoth Entity turn, no direct damage and no Stun. On proc,
# the swarm forces a moving/exposed Party out of one cover tier and erases one
# opening. GUARD is explicit counterplay and negates the entire skill.
combat = COMBAT.read_text(encoding="utf-8")

constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
constant_block = '''  internal const val DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT = 26
'''
if 'DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT = 26' not in combat:
    combat = replace_once(
        combat,
        constant_anchor,
        constant_anchor + constant_block,
        "Deathmoth Phototactic Swarm constants",
    )

response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
response_block = response_anchor + '''
      // DEATHMOTH_PHOTOTACTIC_SWARM_V1: one proc check on Deathmoth's Entity response turn only.
      if (c.entityKey == "deathmoth" &&
          roll(c.copy(eventCounter = c.eventCounter + 1931), 100) < DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT) {
        if (intent == Intent.GUARD) {
          log += "Phototactic Swarm: proc ${DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT}% nhưng Party thu ánh sáng và giữ đội hình sau vật che; hiệu ứng bị vô hiệu."
        } else {
          val coverBefore = c.cover
          val coverAfter = when (coverBefore) {
            Cover.HARD -> Cover.PARTIAL
            Cover.PARTIAL -> Cover.EXPOSED
            Cover.EXPOSED -> Cover.EXPOSED
          }
          val openingBefore = c.opening
          c = c.copy(
            cover = coverAfter,
            opening = max(0, openingBefore - 1)
          )
          log += "Phototactic Swarm: Deathmoth kéo cả cụm lao theo nguồn sáng/chuyển động; proc ${DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT}%, Cover ${coverBefore.name} -> ${coverAfter.name}, Opening ${openingBefore} -> ${c.opening}."
        }
      }
'''
combat = replace_once(combat, response_anchor, response_block, "Deathmoth Phototactic Swarm Entity-turn proc")

for marker in (
    'DEATHMOTH_PHOTOTACTIC_SWARM_V1',
    'DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT = 26',
    'c.entityKey == "deathmoth"',
    'intent == Intent.GUARD',
    'Cover.HARD -> Cover.PARTIAL',
    'opening = max(0, openingBefore - 1)',
):
    if marker not in combat:
        raise RuntimeError("Deathmoth Phototactic Swarm runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'deathmothPhototacticSwarmIsEntityTurnOnlyPressureWithGuardCounterplay' not in test:
    tests = r'''
  @Test fun deathmothPhototacticSwarmIsEntityTurnOnlyPressureWithGuardCounterplay() {
    assertEquals(26, CombatRuntime.DEATHMOTH_PHOTOTACTIC_SWARM_PROC_PERCENT)
    var pressureResult: CombatRuntime.Resolution? = null

    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "deathmoth")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to CombatRuntime.Cover.HARD.name,
        "combat.opening" to "2"
      ))
      val result = CombatRuntime.resolve(state, "OTHER", "giữ nguyên đội hình")
      if (result.reply.contains("Phototactic Swarm:")) {
        pressureResult = result
        break
      }
    }

    assertNotNull("26% Deathmoth proc must be reachable across deterministic Entity turns", pressureResult)
    val pressured = CombatRuntime.active(pressureResult!!.state)!!
    assertEquals(CombatRuntime.Cover.PARTIAL, pressured.cover)
    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Opening 2 -> 1"))

    var guardResult: CombatRuntime.Resolution? = null
    for (counter in 0..300) {
      var state = CombatRuntime.start(GameState.initial(), "deathmoth")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.cover" to CombatRuntime.Cover.EXPOSED.name,
        "combat.opening" to "2"
      ))
      val result = CombatRuntime.resolve(state, "GUARD", "thu ánh sáng và cố thủ sau vật che")
      if (result.reply.contains("Phototactic Swarm:")) {
        guardResult = result
        break
      }
    }

    assertNotNull("Deathmoth proc must also be reachable on a GUARD Entity-response turn", guardResult)
    assertTrue(guardResult!!.reply, guardResult!!.reply.contains("hiệu ứng bị vô hiệu"))
    val guarded = CombatRuntime.active(guardResult!!.state)!!
    assertEquals(CombatRuntime.Cover.HARD, guarded.cover)
  }
'''
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace not found")
    test = test[:close] + tests + test[close:]

TEST.write_text(test, encoding="utf-8")
print("Hourly Entity skill applied: Deathmoth Phototactic Swarm (ACTIVE, 26% on Deathmoth Entity turn, GUARD counterplay).")
