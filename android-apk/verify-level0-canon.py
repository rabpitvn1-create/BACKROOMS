#!/usr/bin/env python3
"""Targeted integrity verifier for supplemental Level 0 Canon Runtime 2.0 pack."""
import hashlib, json
from pathlib import Path
root=Path(__file__).resolve().parent
source=(root/"canon_sources/Asset/02_Level/LEVEL_0_CLOUDINARY_MAPPING.json").read_bytes()
assert hashlib.sha256(source).hexdigest()=="45d0f2d006c3734573517863b7c438e3fce75a0c198af8776f24b889775f254f"
original=json.loads(source)
src=[x for x in original if x["category"]=="LEVEL_BACKGROUND" and x["public_id"].startswith("Level_0_")]
cards=json.loads((root/"app/src/main/assets/knowledge/level_0_canon_cards.json").read_text())
visuals=json.loads((root/"app/src/main/assets/knowledge/level_0_visual_registry.json").read_text())
assert len(src)==135 and len({x["public_id"] for x in src})==135
assert len(cards["records"])==cards["recordCount"]==109
assert len(visuals["nodes"])==visuals["node_count"]==109 and visuals["visual_count"]==135
assert visuals["routes_defined"] is False
existing=json.loads((root/"app/src/main/assets/knowledge/knowledge_db.json").read_text())["records"]
allids=[x["id"] for x in existing+cards["records"]]
assert len(allids)==len(set(allids))
byid={x["id"]:x for x in cards["records"]}
parents={x["node_key"] for x in visuals["nodes"]}
ref={x["public_id"]:x for x in src}
seen=[]
for node in visuals["nodes"]:
    assert node["parent_node_key"] is None or node["parent_node_key"] in parents
    card=byid[node["canon_id"]]
    assert card["node_key"]==node["node_key"] and card["parent_node_key"]==node["parent_node_key"]
    assert card["truth_status"]=="INFERRED" and card["review_status"]=="PENDING_AUTHOR_REVIEW"
    assert card["references"]==[] and card["activation"]["level_scope"]=="LEVEL.00"
    assert card["visual_public_ids"]==[im["public_id"] for im in node["visuals"]]
    for im in node["visuals"]:
        seen.append(im["public_id"])
        assert all(im[k]==ref[im["public_id"]][k] for k in ("source_name","drive_id","public_id","url"))
assert len(seen)==len(set(seen))==135 and set(seen)==set(ref)
print("PASS: 109 Level 0 cards, 135 asset refs, exact lineage, no route edges")
