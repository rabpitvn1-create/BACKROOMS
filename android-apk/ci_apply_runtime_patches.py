from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent

SCRIPTS = [
    "patch-provider-status.py",
    "patch-luna-text.py",
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
    "patch-kai-devil-within-entity.py",
    "patch-kai-devil-blessing.py",
    "patch-item-interaction-coherence.py",
    "patch-item-identity-authority-v2.py",
    "patch-item-identity-regression-diagnostics.py",
    "patch-item-identity-finalize.py",
    "patch-item-reference-fallback-final.py",
    "patch-passive-skill-visibility.py",
    "patch-skill-description-full-vietnamese.py",
    "patch-combat-total-turn-isolation.py",
    "patch-registered-level-runtime-bridge.py",
    "patch-gemini-level-generation.py",
    "patch-forward-progression-contract.py",
    "patch-private-core-save-final.py",
    "patch-escape-chance-hud.py",
    "patch-chicken-rice-box-test-compat.py",
    "patch-entity-procedural-loot-rebalance.py",
    "patch-evidence-highlight-final.py",
    "patch-registered-level-narrative-boundary.py",
    "patch-player-facing-authority-boundary.py",
    "patch-jane-doe-finalizer.py",
    "patch-jane-doe-test-compat.py",
    "patch-kai-devil-within-snapshot-finalize.py",
    "patch-campaign-route-authority-final.py",
    "patch-discovery-knowledge-projection.py",
    "patch-world-director-proposal-boundary.py",
    "patch-pending-turn-idempotent-rng.py",
    "patch-main-story-level0-1.py",
    "patch-story-owned-companion-continuity.py",
]

for script in SCRIPTS:
    path = ROOT / script
    if not path.is_file():
        raise SystemExit(f"Missing runtime patch script: {script}")
    print(f"==> {script}", flush=True)
    subprocess.run([sys.executable, str(path)], cwd=ROOT.parent, check=True)
