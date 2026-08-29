from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
runpy.run_path(str(ROOT / "patch-best-kai-overlay-finalize-base.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-rpg-combat-text-style-final.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-responsive-display-v2-final.py"), run_name="__main__")
