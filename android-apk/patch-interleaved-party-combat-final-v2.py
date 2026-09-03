from pathlib import Path

ROOT = Path(__file__).resolve().parent
LEGACY = ROOT / "patch-interleaved-party-combat-final.py"

if not LEGACY.is_file():
    raise RuntimeError("Interleaved combat legacy finalizer missing")

source = LEGACY.read_text(encoding="utf-8")

old = r'''combat = replace_once(
    combat,
    '      val entityTargets = entityCombatActionTargets(resolvedState)\n      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."\n',
    '      val entityTargets = entityDirectActionTargets(resolvedState)\n      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per completed Party actor turn."\n',
    "Ordinary Entity direct target scope",
)
'''

new = r'''# Jeff/Jane and later Entity patches insert status/skill work between the full
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

if old not in source:
    raise RuntimeError("Interleaved combat V2 could not locate legacy direct-target patch block")
source = source.replace(old, new, 1)

exec(
    compile(source, str(LEGACY), "exec"),
    {"__name__": "__main__", "__file__": str(LEGACY)},
)

print("Interleaved combat V2 applied: full Entity skill/status roster preserved; only generic direct attacks are actor-scoped.")
