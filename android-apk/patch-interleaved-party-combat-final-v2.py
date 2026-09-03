from pathlib import Path

ROOT = Path(__file__).resolve().parent
LEGACY = ROOT / "patch-interleaved-party-combat-final.py"

if not LEGACY.is_file():
    raise RuntimeError("Interleaved combat legacy finalizer missing")

source = LEGACY.read_text(encoding="utf-8")

# The legacy finalizer expected the Entity target roster and its debug line to be
# adjacent. Jeff/Jane insert status/skill work between them, so preserve the full
# roster for explicit status/AoE logic and scope only the generic direct loop.
old_direct = r'''combat = replace_once(
    combat,
    '      val entityTargets = entityCombatActionTargets(resolvedState)\n      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."\n',
    '      val entityTargets = entityDirectActionTargets(resolvedState)\n      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per completed Party actor turn."\n',
    "Ordinary Entity direct target scope",
)
'''

new_direct = r'''# Jeff/Jane and later Entity patches insert status/skill work between the full
# roster declaration and the ordinary direct-action loop. Preserve that full
# roster for status ticks, marks, AoE and other explicit mechanics; scope only
# the generic direct-action loop to the Party actor that just completed a turn.
ordinary_marker = """    } else if (c.entityKey != SCP_173_KEY &&
        !(c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0)) {
"""
ordinary_start = combat.find(ordinary_marker)
ordinary_end = combat.find('    } else if (c.entityKey == JOHN_DOE_KEY) {\n', ordinary_start)
if ordinary_start < 0 or ordinary_end < 0:
    raise RuntimeError("Interleaved combat: ordinary Entity response branch missing")
ordinary = combat[ordinary_start:ordinary_end]

budget_log = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."\n'
direct_declaration = '      val entityDirectTargets = entityDirectActionTargets(resolvedState)\n'
new_budget_log = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
if direct_declaration not in ordinary:
    ordinary = replace_once(
        ordinary,
        budget_log,
        direct_declaration + new_budget_log,
        "Ordinary Entity direct target declaration",
    )
ordinary = replace_once(
    ordinary,
    '      entityTargets.forEachIndexed { actionIndex, targetId ->\n',
    '      entityDirectTargets.forEachIndexed { actionIndex, targetId ->\n',
    "Ordinary Entity direct action loop",
)
# Action-slot narration belongs to the direct loop, so its denominator must use
# the scoped list as well. Full-roster mechanics above remain untouched.
ordinary = ordinary.replace('/${entityTargets.size}', '/${entityDirectTargets.size}')
combat = combat[:ordinary_start] + ordinary + combat[ordinary_end:]
'''

if old_direct not in source:
    raise RuntimeError("Interleaved combat V2 could not locate legacy direct-target patch block")
source = source.replace(old_direct, new_direct, 1)

# Visual-state hardening inserts resolvedEntityKey between the combat-active guard
# and resolver invocation. Replace the legacy all-at-once anchor with two narrow
# anchors so that cleanup identity remains intact.
old_idempotency = r'''active_old = '''    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")

    var resolution = PartyTurnCombat.resolve(current, actionKind, action)
'''
active_new = '''    val current = loadOrMigrate(legacy)
    val combatRequestKey = PartyTurnCombat.requestKey(legacy.optString("combatRequestId"))
    if (CombatRuntime.active(current) == null) {
      val replay = PartyTurnCombat.replayReply(current, combatRequestKey)
      if (replay != null) {
        val output = syncLegacy(legacy, current, incrementTurn = false)
        appendLog(output, PartyTurnCombat.replayDisplayAction(current), replay)
        return response(true, output, null, "combat_replayed", replay)
      }
      return response(false, legacy, null, "combat_inactive")
    }

    var resolution = PartyTurnCombat.resolve(current, actionKind, action, combatRequestKey)
'''
method = replace_once(method, active_old, active_new, "Combat request idempotency entry")
'''

new_idempotency = r'''active_guard_old = '''    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")
'''
active_guard_new = '''    val current = loadOrMigrate(legacy)
    val combatRequestKey = PartyTurnCombat.requestKey(legacy.optString("combatRequestId"))
    if (CombatRuntime.active(current) == null) {
      val replay = PartyTurnCombat.replayReply(current, combatRequestKey)
      if (replay != null) {
        val output = syncLegacy(legacy, current, incrementTurn = false)
        appendLog(output, PartyTurnCombat.replayDisplayAction(current), replay)
        return response(true, output, null, "combat_replayed", replay)
      }
      return response(false, legacy, null, "combat_inactive")
    }
'''
method = replace_once(method, active_guard_old, active_guard_new, "Combat request idempotency guard")
method = replace_once(
    method,
    "    var resolution = PartyTurnCombat.resolve(current, actionKind, action)\n",
    "    var resolution = PartyTurnCombat.resolve(current, actionKind, action, combatRequestKey)\n",
    "Combat request idempotency resolver",
)
'''

if old_idempotency not in source:
    raise RuntimeError("Interleaved combat V2 could not locate legacy facade idempotency block")
source = source.replace(old_idempotency, new_idempotency, 1)

exec(
    compile(source, str(LEGACY), "exec"),
    {"__name__": "__main__", "__file__": str(LEGACY)},
)

print("Interleaved combat V2 applied: Entity skills keep full-roster semantics, direct attacks are actor-scoped, and facade anchors preserve visual cleanup.")
