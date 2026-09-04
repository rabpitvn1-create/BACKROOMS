from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
AUDIT_RUNNER = ROOT / "ci_patch_audit_runner.py"
ORPHAN_AUDIT = ROOT / "ci_patch_orphan_audit.py"
COMPACT_VERIFY = ROOT / "ci_verify_compact_combat_summary.py"
ENCOUNTER_VERIFY = ROOT / "ci_verify_encounter_action_authority.py"

SCRIPTS = [
    "patch-provider-status.py",
    "patch-luna-text.py",
    "patch-level-snapshot-backgrounds.py",
    "patch-snapshot-fallback.py",
    "patch-kai-hd-continuous.py",
    "patch-kai-codex.py",
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
    "patch-story-companion-runtime-invariants.py",
    "patch-story-quest-state-level0-1.py",
    "patch-story-prologue-authority-final.py",
    "patch-hourly-main-story-evolution.py",
    "patch-dialogue-prose-runtime.py",
    "patch-sru-force-codex.py",
    "patch-action-runtime-terminal-cleanup.py",
    "patch-level-zero-observation-boundary.py",
    "patch-kai-r08-knowledge-final.py",
    "patch-lucia-r03-runtime-canon.py",
    "patch-lucia-first-contact-party-gate.py",
    "patch-kai-r10-skill-catalog-precompat.py",
    "patch-kai-r10-runtime-canon.py",
    "patch-kai-r10-test-compile-fix.py",
    "patch-background-music.py",
    "patch-turnbased-party-ap-final.py",
    "patch-interleaved-party-combat-final-v2.py",
    "patch-interleaved-party-combat-final-v3.py",
    "patch-interleaved-party-combat-final-v4.py",
    "patch-interleaved-party-combat-final-v5.py",
    "patch-ap-skill-authority-precompat.py",
    "patch-ap-skill-authority-final.py",
    "patch-ap-skill-test-compat.py",
    "patch-lucia-entity-overlay-final.py",
    "patch-combat-overlay-feedback.py",
    "patch-entity-encounter-narrative-authority-final.py",
    "patch-readable-gm-evidence.py",
    "patch-provider-haku-luna-lock-gemini-final.py",
    "patch-haku-json-reliability-final.py",
    "patch-auto-light-flicker.py",
    "patch-combat-presentation-authority-final.py",
    "patch-combat-summary-final.py",
    "patch-stun-expiry-character-shadow-final.py",
    "patch-inventory-icons.py",
    "patch-issue311-debug-log-export.py",
    "patch-canon-audit-softlock-final.py",
    "patch-hourly-character-auto-skill-syvial-01.py",
    "patch-persistent-foundation-final.py",
]

for required in (AUDIT_RUNNER, ORPHAN_AUDIT, COMPACT_VERIFY, ENCOUNTER_VERIFY):
    if not required.is_file():
        raise SystemExit(f"Missing runtime patch audit tool: {required.name}")

subprocess.run([sys.executable, str(ORPHAN_AUDIT)], cwd=ROOT.parent, check=True)

for script in SCRIPTS:
    path = ROOT / script
    if not path.is_file():
        raise SystemExit(f"Missing runtime patch script: {script}")
    print(f"==> {script}", flush=True)
    subprocess.run(
        [sys.executable, str(AUDIT_RUNNER), str(path)],
        cwd=ROOT.parent,
        check=True,
    )

subprocess.run([sys.executable, str(COMPACT_VERIFY)], cwd=ROOT.parent, check=True)
subprocess.run([sys.executable, str(ENCOUNTER_VERIFY)], cwd=ROOT.parent, check=True)
