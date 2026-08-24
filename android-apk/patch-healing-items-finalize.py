from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
ENGINES = ROOT / "app/src/main/java/com/rabpit/backroom/core/Engines.kt"
ITEM_CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/ItemCatalog.kt"

MODERN_OFFICIAL_ITEMS = ITEM_CATALOG.exists() and 'OfficialItem(BANDAGE, "Bandage"' in ITEM_CATALOG.read_text(encoding="utf-8")
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
mode = "official catalog" if MODERN_OFFICIAL_ITEMS else "legacy"
print(f"Healing item final use call updated with healHp argument ({mode} mode).")

# Final Entity combat balance authority runs after the healing-item chain so no later runtime patch can
# rewrite Entity HP, evasion, regeneration, or legacy combat migration semantics.
runpy.run_path(str(ROOT / "patch-entity-combat-durability.py"), run_name="__main__")

# Kai's automatic Ultimate must run after Entity durability because it depends on the final evasion,
# regeneration, and upgraded Entity HP contracts. This keeps Guilty Crown Override as the last general
# combat mechanic before unique boss overrides are applied.
runpy.run_path(str(ROOT / "patch-kai-guilty-crown-override.py"), run_name="__main__")

# Diệp Minh remains the unique-boss authority for exact HP, percentage attacks, regeneration and spawn.
# Apply that first so Kai's passive gun skills can wrap the final enemy-response path without replacing
# or weakening any of the boss contracts.
runpy.run_path(str(ROOT / "patch-diep-minh-boss.py"), run_name="__main__")

# Kai's automatic gun-skill pass is deliberately last in CombatRuntime. It adds persistent Bleeding,
# Stun and Quick Step state around the already-final generic/boss response while preserving Guilty Crown.
runpy.run_path(str(ROOT / "patch-kai-gun-skills.py"), run_name="__main__")
