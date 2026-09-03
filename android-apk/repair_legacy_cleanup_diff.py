from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
SELF = Path(__file__)
WORKFLOW = REPO / ".github/workflows/repair-legacy-cleanup-diff.yml"

raw = subprocess.check_output(
    ["git", "show", "origin/main:android-apk/app/src/main/assets/knowledge/knowledge_db.json"],
    cwd=REPO,
    text=True,
)

old_objective = "Initial long-term objectives for Kai: survive and learn enough of the environment to move; determine Iris and Syvial's condition; find/re-establish a route to reunite if possible; understand Backrooms exit rules; and seek communication or a route back to Frontrooms only if gameplay/canon truly supports it. None is guaranteed to complete quickly."
new_objective = "In 2299, Kai's active campaign objectives are to carry out the SRU investigation of Async and the Backrooms risk, survive and learn enough local rules to keep progressing, and find Iris and Syvial after the team is dispersed. Async traces, teammate locations, escape routes and Backrooms origin claims require real discovered evidence; the mission brief alone proves none of them."

old_separation = "After the shared no-clip event, Kai, Iris and Syvial land apart. Direct links between the three, Black Blood/Command, Frontrooms, beacon and outside telemetry are initially offline. Kai does not know Iris's or Syvial's location/Level. Iris and Syvial exist in the campaign from the Prologue and are separated, not first-spawned by survivor RNG. Re-establishing contact or reunion requires continuity/geography/state support; rarity rolls never teleport them."
new_separation = "In 2299, Kai, Iris and Syvial are SRU members on an intentional mission to investigate Async. All three voluntarily cross the same spatial gate into Backrooms, where the transition disperses them to different Levels. Kai starts alone at Level 0. Direct team links, SRU communications and Frontrooms links are offline, and Kai does not know Iris's or Syvial's location. Their eventual reunions are story-owned continuity events, never random companion spawns or narrator inventions."

for old, new, label in (
    (old_objective, new_objective, "objective"),
    (old_separation, new_separation, "separation"),
):
    count = raw.count(old)
    if count != 1:
        raise RuntimeError(f"repair_{label}_anchor_count:{count}")
    raw = raw.replace(old, new, 1)

for forbidden in ("shared no-clip event", "Black Blood/Command"):
    if forbidden in raw:
        raise RuntimeError("repair_forbidden_survived:" + forbidden)

KNOWLEDGE.write_text(raw, encoding="utf-8")

if WORKFLOW.exists():
    WORKFLOW.unlink()
if SELF.exists():
    SELF.unlink()

print("knowledge_db formatting restored; only two story records changed")
