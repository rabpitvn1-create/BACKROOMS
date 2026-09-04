from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

main = MAIN.read_text(encoding="utf-8")
old = '          runtimeDiagnostics.beginTurn(foundationTurnId.isEmpty() ? "turn-" + before.optInt("turn", 0) : foundationTurnId);\n'
new = '          runtimeDiagnostics.beginTurn(foundationTurnId.isEmpty() ? "turn-" + new JSONObject(stateJson).optInt("turn", 0) : foundationTurnId);\n'

if new not in main:
    count = main.count(old)
    if count != 1:
        raise RuntimeError(f"Issue #330 turn correlation compile fix: expected exactly one stale anchor, found {count}")
    main = main.replace(old, new, 1)

if old in main:
    raise RuntimeError("Issue #330 turn correlation compile fix: stale pre-declaration before reference remains")
if new not in main:
    raise RuntimeError("Issue #330 turn correlation compile fix: stateJson fallback marker missing")

MAIN.write_text(main, encoding="utf-8")
print("Issue #330 turn correlation fixed: fallback derives turn from submitTurn stateJson without referencing before before declaration.")
