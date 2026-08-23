from pathlib import Path
import runpy

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

# Final Entity combat balance authority runs after the healing-item chain so no later runtime patch can
# rewrite Entity HP, evasion, regeneration, or legacy combat migration semantics.
runpy.run_path(str(ROOT / "patch-entity-combat-durability.py"), run_name="__main__")

# Kai's automatic Ultimate must run after Entity durability because it depends on the final evasion,
# regeneration, and upgraded Entity HP contracts. This keeps Guilty Crown Override as the last combat
# mechanics authority without rewriting any earlier patch in the chain.
runpy.run_path(str(ROOT / "patch-kai-guilty-crown-override.py"), run_name="__main__")
