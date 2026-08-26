from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

combat = COMBAT.read_text(encoding="utf-8")

# Preserve every established 17% Entity Evasion call site and its exact compatibility
# markers. Violet Warden's defensive identity is additive Block/Counter rather than a
# global evasion rewrite; this keeps Lucia/Party/Kai regressions and older mechanics intact.
transformed = "entityEvasionPercent(c.entityKey)"
if transformed in combat:
    combat = combat.replace(transformed, "ENTITY_EVASION_PERCENT")

if transformed in combat:
    raise RuntimeError("Violet Warden compatibility failed to restore shared Entity Evasion markers")
if 'val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT' not in combat:
    raise RuntimeError("Violet Warden compatibility lost Lucia Entity Evasion contract")

COMBAT.write_text(combat, encoding="utf-8")
print("Violet Warden compatibility restored established 17% Entity Evasion call sites; Block/Counter remains additive and boss-local.")

# Display authority is finalized after the Entity patch has installed Violet's overlay mapping.
# The later stun finalizer only touches CombatRuntime, so it cannot overwrite this asset choice.
runpy.run_path(str(ROOT / "patch-violet-display-finalize.py"), run_name="__main__")
