"""Prepare the original 1.1.63.0.3 runtime before Gradle, without GitHub workflows.

Run once in a clean checkout: existing patches intentionally reject reapplication.
The list matches the original 1.1.63.0.3 release pipeline, in its original order.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
PATCHES = (
    "patch-provider-status.py",
    "patch-provider-key-pool.py",
    "patch-level-snapshot-backgrounds.py",
    "patch-snapshot-fallback.py",
    "patch-kai-hd-continuous.py",
    "patch-kai-png-preferred.py",
    "patch-kai-codex.py",
    "patch-r06-source-marker.py",
    "patch-drive-canon-gameplay.py",
    "patch-inventory-persistence.py",
    "patch-ai-orchestrator.py",
    "patch-state-op-hardening.py",
    "patch-gemini-health-pool.py",
    "patch-conditional-audit.py",
    "patch-audit-validated-risk.py",
    "patch-gameplay-parity-final.py",
    "patch-final-authority-hardening.py",
    "patch-rejected-op-repair-final.py",
    "patch-kai-resource-policy-final.py",
    "patch-provider-deadline-final.py",
    "patch-gemini-model-matrix-final.py",
    "patch-java-compile-hardening.py",
    "patch-save-controls-final.py",
    "patch-hard-mode-label.py",
    "patch-snapshot-unconfigured.py",
    "patch-game-state-core-bridge.py",
    "patch-character-inventory-ui.py",
    "patch-character-detail-avatar-fallback.py",
    "patch-progression-snapshot-equipment.py",
    "patch-ui-1.1.99-shell.py",
    "patch-poker-dice-core-backport.py",
    "patch-poker-dice-ui-119-polish.py",
    "patch-cao-minh-identity-overlay.py",
    "patch-item-detail-actions.py",
    "patch-item-whole-unit.py",
    "patch-cao-minh-skills-equipment.py",
    "patch-cao-minh-prologue.py",
    "patch-character-stats-1193a-restore.py",
    "patch-combat-feedback-1193a.py",

)


def main():
    html = (ROOT / "app/src/main/assets/index.html").read_text(encoding="utf-8")
    if "UI_119_BACKPORT_R01" in html:
        raise SystemExit("Runtime already patched. Use a clean checkout; do not apply twice.")
    for name in PATCHES:
        print(f"Applying {name}", flush=True)
        subprocess.run([sys.executable, str(ROOT / name)], cwd=ROOT.parent, check=True)


if __name__ == "__main__":
    main()
