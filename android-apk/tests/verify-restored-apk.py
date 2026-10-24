#!/usr/bin/env python3
"""Verify the APK actually packages the restored runtime and October 24 UI."""
from pathlib import Path
from zipfile import ZipFile
import sys

apk = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/build/outputs/apk/debug/app-debug.apk")
if not apk.is_file():
    raise SystemExit(f"APK not found: {apk}")

required_assets = {
    "index.html", "cloudinary-gameplay.js", "cloudinary-entity-combat.js",
    "character-level-ui.js", "combat-feedback-1193a.js",
    "combat-feedback-1193a.css", "snapshot-stage.js", "mobile-hud.js",
    "loot-item-display.js", "level-travel.js", "diagnostic-export.js", "primary-actions.js", "mobile-hud.css", "item-detail-actions.js",
}
required_classes = {
    "CombatRuntime", "RestoredCompanionCombat", "CombatStatusLifecycle",
    "EntityExpRewards", "GameCoreFacade",
}
with ZipFile(apk) as archive:
    names = set(archive.namelist())
    missing = sorted("assets/" + name for name in required_assets if "assets/" + name not in names)
    if missing:
        raise SystemExit("Restored APK assets missing: " + ", ".join(missing))
    index = archive.read("assets/index.html").decode("utf-8")
    required_scripts = [
        "cloudinary-gameplay.js", "cloudinary-entity-combat.js",
        "character-level-ui.js", "combat-feedback-1193a.js",
        "snapshot-stage.js", "mobile-hud.js", "level-travel.js", "diagnostic-export.js", "primary-actions.js", "item-detail-actions.js",
    ]
    missing_scripts = [name for name in required_scripts if 'src="' + name + '"' not in index]
    if missing_scripts:
        raise SystemExit("APK index.html does not load: " + ", ".join(missing_scripts))
    if 'id="exportDiagnosticsJson"' not in index:
        raise SystemExit("Diagnostic export button not packaged")
    for element in ["playerActionBar", "playerActionModal", "exploreActionButton", "searchActionButton"]:
        if index.count('id="' + element + '"') != 1:
            raise SystemExit("Primary action UI missing/duplicated: " + element)
    if "level-travel-background" in archive.read("assets/level-travel.js").decode("utf-8"):
        raise SystemExit("Duplicate travel Snapshot background packaged")
    if index.index('src="diagnostic-export.js"') > index.index('src="level-travel.js"'):
        raise SystemExit("Diagnostics script must start before Level Travel boot")
    if "state.flags.iris=state.flags.iris" in index:
        raise SystemExit("Obsolete Iris initialization packaged in APK")
    registry = archive.read("assets/cloudinary-gameplay.js").decode("utf-8")
    if "res.cloudinary.com/csz2me5b/" not in registry or '"luc-tram"' not in registry:
        raise SystemExit("Canonical Cloudinary registry missing from packaged APK")
    scene = archive.read("assets/snapshot-stage.js").decode("utf-8")
    if "backroomSnapshotStage" not in scene or "backroomCloudinary" not in scene:
        raise SystemExit("Layered Snapshot / Cloudinary bridge not packaged")
    dex_names = sorted(name for name in names if name.startswith("classes") and name.endswith(".dex"))
    if not dex_names:
        raise SystemExit("No compiled DEX in APK")
    dex = b"".join(archive.read(name) for name in dex_names)
    missing_classes = sorted(
        name for name in required_classes
        if ("Lcom/rabpit/backroom/core/" + name + ";").encode("ascii") not in dex
    )
    if missing_classes:
        raise SystemExit("Restored Kotlin classes missing from DEX: " + ", ".join(missing_classes))
    if b"Lcom/rabpit/backroom/AppDiagnosticLog;" not in dex:
        raise SystemExit("Native diagnostic exporter class missing from DEX")
    if b"Lcom/rabpit/backroom/TextProviderRouter;" not in dex:
        raise SystemExit("Gehihi/Gemini text provider router missing from DEX")
print(f"APK integration integrity OK: {apk.name}, {len(required_assets)} assets, "
      f"{len(required_classes)} Kotlin classes, {len(dex_names)} DEX files")

