from pathlib import Path

ROOT = Path(__file__).resolve().parent
ENGINES = ROOT / "app/src/main/java/com/rabpit/backroom/core/Engines.kt"

text = ENGINES.read_text(encoding="utf-8")
old = 'finishItemUse(state, changed(state, "item_used"), command, physiologyEffects)'
new = 'finishItemUse(state, changed(state, "item_used"), command, physiologyEffects, healingAmount)'
if new not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Healing fallback-use call: expected exactly 1 anchor, found {count}")
    text = text.replace(old, new, 1)

if 'finishItemUse(state, inventoryResult, command, physiologyEffects)' in text or old in text:
    raise RuntimeError("A pre-healing finishItemUse call survived")

ENGINES.write_text(text, encoding="utf-8")
print("Healing item final use call updated with healHp argument.")
