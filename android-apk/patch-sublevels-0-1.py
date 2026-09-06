from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "sublevels_0_1.json"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"

catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
knowledge = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = knowledge.get("records")
if not isinstance(records, list):
    raise RuntimeError("knowledge_db.json records missing")

sublevels = catalog.get("sublevels")
if not isinstance(sublevels, list):
    raise RuntimeError("sublevels_0_1.json sublevels missing")

expected = ["0.1", "0.2", "0.3", "0.5", "0.7", "1.1", "1.2", "1.3", "1.5"]
actual = [str(entry.get("id", "")) for entry in sublevels]
if actual != expected:
    raise RuntimeError(f"sublevel catalog order must be {expected}, found {actual}")

# Replace only our generated records. Other knowledge records and their provenance stay intact.
generated_ids = {"SUBLEVEL.INDEX.00", "SUBLEVEL.INDEX.01"}
records[:] = [record for record in records if record.get("id") not in generated_ids]

for parent in (0, 1):
    entries = [entry for entry in sublevels if int(entry.get("parentLevel", -1)) == parent]
    if not entries:
        raise RuntimeError(f"no sublevels for parent Level {parent}")

    lines = [
        f"Project sub-levels available inside Level {parent}. These are locations within the parent Level, not decimal replacements for state.level.number.",
        "STATE CONTRACT: keep state.level.number at the integer parent. Enter/leave a same-parent sub-level with set_location using the exact Level X.Y label; reserve set_level for an actual parent-Level transition.",
        "AUTHORITY CONTRACT: Project WORLD/ENTITY/ITEM hard locks win over external wiki lore. Do not import external factions, friendly/neutral Entity behavior, guaranteed safety or abundant resources unless separately locked by Project canon.",
    ]
    for entry in entries:
        lines.append(
            f"Level {entry['id']} – {entry['name']}: {entry['environment']} Gameplay: {entry['gameplay']} "
            f"Source status: {entry['sourceStatus']}."
        )

    record_id = f"SUBLEVEL.INDEX.{parent:02d}"
    records.append(
        {
            "id": record_id,
            "domain": "LEVEL",
            "kind": "project-adaptation",
            "text": "\n".join(lines),
            "source": {"document": "sublevels_0_1.json", "anchor": f"parentLevel={parent}"},
            "authority": "WORLD_CANON_ADAPTATION",
            "mutability": "CURRENT",
            "priority": 24,
            "tags": [
                "sublevel",
                "sub-level",
                f"level {parent}",
                *[f"level {entry['id']}" for entry in entries],
                *[entry["name"].lower() for entry in entries],
            ],
            "references": [],
            "affordances": ["exploration", "transition"],
        }
    )

by_id = {record.get("id"): record for record in records if isinstance(record, dict)}
for parent in (0, 1):
    level_id = f"LEVEL.{parent:02d}"
    target = by_id.get(level_id)
    if target is None:
        raise RuntimeError(f"missing parent knowledge record {level_id}")
    refs = target.get("references")
    if not isinstance(refs, list):
        refs = []
    sublevel_ref = f"SUBLEVEL.INDEX.{parent:02d}"
    if sublevel_ref not in refs:
        refs.append(sublevel_ref)
    target["references"] = refs

knowledge["records"] = records
KNOWLEDGE.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Level 0-1 sub-level catalog injected into runtime knowledge with parent-level state compatibility.")
