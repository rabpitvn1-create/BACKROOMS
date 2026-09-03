"""Install after all native character overlays so their renderer anchors remain intact."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
source = MAIN.read_text(encoding="utf-8")
pattern = r"renderSnapshot=function\(\)\{(__baseRenderSnapshot\(\);appendEntityOverlay\(\);(?:appendLuciaEntityOverlay\(\);)?)\};"
source, count = re.subn(
    pattern,
    lambda match: "renderSnapshot=function(){var fx=window.CombatOverlayFeedback;if(fx)fx.beforeSnapshot();"
    + match.group(1) + "if(fx)fx.afterSnapshot();};",
    source,
)
if count != 1:
    raise RuntimeError(f"Combat feedback native renderer: expected one anchor, found {count}")
MAIN.write_text(source, encoding="utf-8")
facade = (ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt").read_text(encoding="utf-8")
if 'output.put("combatFeedback", combatFeedback)' not in facade:
    raise RuntimeError("Authoritative combat feedback projection is missing")
print("Combat feedback preserves the completed native Entity and character renderer.")
