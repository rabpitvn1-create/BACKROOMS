from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
runpy.run_path(str(ROOT / "patch-best-kai-overlay-finalize-base.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-rpg-combat-text-style-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-responsive-display-v2-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-remove-madgod-loot-capacity-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-sru-equipment-integration-finalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-final-whitespace-normalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-lucia-regen-interval.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-entity-loot-plus-3.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-combat-vietnamese-narration.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-character-item-transfer-use.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-character-item-transfer-use-compile-fix.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-skill-description-vietnamese-finalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-skill-description-ci-compat.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-skill-description-natural-vietnamese-finalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-skill-description-natural-test-compat.py"), run_name="__main__")
