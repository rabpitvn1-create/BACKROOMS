from pathlib import Path


ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"

facade = FACADE.read_text(encoding="utf-8")

# Issue #134: CombatRuntime owns its own combat-turn counter (eventCounter). The
# legacy `turn` field is the world/total turn shown by the UI and must not advance
# for each combat command. Pressure Combat originally projected every combat
# resolution with incrementTurn=true, which made ATTACK/EVADE/ESCAPE consume
# world turns even though CombatRuntime itself never changes GameState.turn.
start_marker = "  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {\n"
end_marker = "\n  private fun loadOrMigrate(legacy: JSONObject): GameState {\n"
start = facade.find(start_marker)
end = facade.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("Issue #134 processCombat boundary missing")

combat_block = facade[start:end]
old = "    val output = syncLegacy(legacy, next, incrementTurn = true)\n"
new = "    val output = syncLegacy(legacy, next, incrementTurn = false)\n"
if new not in combat_block:
    if combat_block.count(old) != 1:
        raise RuntimeError(
            f"Issue #134 expected one combat legacy-turn increment, found {combat_block.count(old)}"
        )
    combat_block = combat_block.replace(old, new, 1)
    facade = facade[:start] + combat_block + facade[end:]

# Keep normal world actions unchanged. This bug fix isolates combat only instead
# of globally disabling the total-turn counter like a particularly enthusiastic
# sledgehammer.
if "syncLegacy(legacy, committed.state, incrementTurn = true)" not in facade[:start]:
    raise RuntimeError("Issue #134 non-combat total-turn increment contract disappeared")
if "reason = \"combat_action\"" not in combat_block:
    raise RuntimeError("Issue #134 combat subjective-time accounting disappeared")
if old in combat_block:
    raise RuntimeError("Issue #134 combat still increments legacy total turn")
if new not in combat_block:
    raise RuntimeError("Issue #134 combat total-turn freeze was not applied")

FACADE.write_text(facade, encoding="utf-8")
print(
    "Issue #134 applied: combat commands keep the legacy/global turn fixed while CombatRuntime keeps its own combat-turn counter."
)
