from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
text = FACADE.read_text(encoding="utf-8")

old = '''    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)
    val turnId = nextTurnId(legacy, state)
'''
new = '''    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val turnId = nextTurnId(legacy, state)
    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)
'''

if new not in text:
    if text.count(old) != 1:
        raise RuntimeError(f"combat start facade compatibility anchor expected once, found {text.count(old)}")
    text = text.replace(old, new, 1)

FACADE.write_text(text, encoding="utf-8")
print("Combat start facade anchor normalized without changing An Nhien cheat behavior.")
