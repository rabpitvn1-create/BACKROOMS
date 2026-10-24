#!/usr/bin/env python3
"""Targeted integrity check for Level 1–13 Cloudinary and CR2 supplemental pack."""
from collections import Counter
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent
source = (root / "canon_sources/Asset/02_Level/LEVEL_0_CLOUDINARY_MAPPING.json").read_bytes()
assert hashlib.sha256(source).hexdigest() == "45d0f2d006c3734573517863b7c438e3fce75a0c198af8776f24b889775f254f"
original = [
    x for x in json.loads(source)
    if x["category"] == "LEVEL_BACKGROUND" and not x["public_id"].startswith("Level_0_")
]
reg = json.loads((root / "app/src/main/assets/knowledge/level_1plus_visual_registry.json").read_text())
pack = json.loads((root / "app/src/main/assets/knowledge/level_1plus_canon_cards.json").read_text())
seed = json.loads((root / "app/src/main/assets/knowledge/knowledge_db.json").read_text())
level0 = json.loads((root / "app/src/main/assets/knowledge/level_0_canon_cards.json").read_text())
manifest = json.loads((root / "canon_source_manifest.json").read_text())

assert len(original) == 45 and len(reg["nodes"]) == reg["node_count"] == 31
assert len(pack["records"]) == pack["recordCount"] == 38
assert len(seed["records"]) == 66 and len(level0["records"]) == 109
all_ids = [r["id"] for p in (seed, level0, pack) for r in p["records"]]
assert len(all_ids) == len(set(all_ids)) == 213
assert reg["visual_count"] == 45 and reg["route_links_defined"] is False
keys = {n["node_key"] for n in reg["nodes"]}
assert len(keys) == 31
assert all(n["parent_node_key"] is None or n["parent_node_key"] in keys for n in reg["nodes"])
assert len([n for n in reg["nodes"] if n["node_kind"] == "LEVEL"]) == 13
assert len([n for n in reg["nodes"] if n["node_kind"] == "SUBLEVEL"]) == 7
assert len([n for n in reg["nodes"] if n["node_kind"] == "AREA"]) == 11

selected = [im for n in reg["nodes"] for im in n["images"]]
assert Counter(im["public_id"] for im in selected) == Counter(im["public_id"] for im in original)
sources = {x["public_id"]: x for x in original}
for img in selected:
    assert all(img[k] == sources[img["public_id"]][k]
               for k in ("source_name", "drive_id", "public_id", "url"))
records = {r["id"]: r for r in pack["records"]}
for node in reg["nodes"]:
    r = records[node["canon_id"]]
    assert r["node_key"] == node["node_key"]
    assert r["parent_node_key"] == node["parent_node_key"]
    assert r["visual_public_ids"] == [im["public_id"] for im in node["images"]]
    assert r["truth_status"] == "INFERRED" and r["review_status"] == "PENDING_AUTHOR_REVIEW"
    assert r["references"] == []
    main_number = int(node["node_key"].split("/")[0].split(".")[0])
    assert r["activation"]["level_scope"] == f"LEVEL.{main_number:02d}"
for n in range(7, 14):
    assert f"LEVEL.{n:02d}" in records
for n in range(11, 14):
    assert records[f"LEVEL.{n:02d}"]["canon_completeness"] == "EXISTENCE_CONFIRMED_LORE_UNKNOWN"
assert "tundra tối vĩnh viễn" in records["LEVEL.NODE.6"]["text"]
assert manifest["record_coverage"]["cr2_effective_total_records"] == 213
assert len(manifest["database"]["supplemental_packs"]) == 2
print("PASS: Level 1–13 complete visual coverage, 31 nodes, 45 images, 213 unique CR2 records; no routes")
