#!/usr/bin/env python3
"""Deterministically index every permitted Novel/Asset text excerpt into the runtime knowledge DB.

Usage: python3 android-apk/build-novel-asset-canon-index.py --write|--check
No external dependencies, network access or inferred canonical facts.
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets/knowledge"
MANIFEST = ASSETS / "novel_asset/manifest.json"
DB = ASSETS / "knowledge_db.json"
CHUNK_CHARS = 880
PREFIX = "NOVEL_ASSET."

AUTHORITIES = {
    "character_baseline": ("CHARACTER_CANON", "IMMUTABLE"),
    "world": ("WORLD_CANON", "BASELINE"),
    "visual_reference_not_entity_behavior": ("VISUAL_REFERENCE", "BASELINE"),
    "world_organization": ("WORLD_CANON", "BASELINE"),
    "world_mechanic": ("WORLD_CANON", "BASELINE"),
    "narrative_lore_writer_only": ("WRITER_SECRET", "BASELINE"),
    "gm_writing": ("WRITING_CANON", "BASELINE"),
}


def verify_world_scene_coverage():
    """All registered journey locations must have a nonempty scene IN the real world asset.

    Derive the expected 50 Level IDs and 17 named areas from the existing Kotlin
    catalog: never maintain a second level list or runtime canon registry.
    """
    kotlin = (ROOT / "app/src/main/java/com/rabpit/backroom/core/progression/WorldContentCatalog.kt").read_text(
        encoding="utf-8"
    )
    levels = set(re.findall(r'WorldContentEntry\\(WorldNodeId\\("(level-[0-9]+(?:\\.[0-9]+)?)"\\)', kotlin))
    named = set(
        f"area:{parent}:{key}"
        for parent, key in re.findall(r'WorldNamedSection\\(([0-9]+), "([a-z0-9-]+)"', kotlin)
    )
    expected = levels | named
    if len(levels) != 50 or len(named) != 17 or len(expected) != 67:
        raise AssertionError(f"Unexpected Core world catalog coverage: {len(levels)} + {len(named)}")
    world = (ASSETS / "novel_asset/BACKROOMS_WORLD.md").read_text(encoding="utf-8")
    matches = list(re.finditer(r'<!-- scene-key:([a-z0-9:.-]+) -->', world))
    keys = [match.group(1) for match in matches]
    repeated = [key for key, count in Counter(keys).items() if count > 1]
    if repeated or len(keys) != 67 or set(keys) != expected:
        raise AssertionError(
            f"World scenes mismatch: duplicates={repeated}, "
            f"missing={sorted(expected - set(keys))}, unexpected={sorted(set(keys) - expected)}"
        )
    for match in matches:
        # Require at least a real paragraph before the next heading or scene.
        section = world[match.end():]
        boundary = re.search(r'(?m)^#{2,3} ', section)
        scene = section[:boundary.start()] if boundary else section
        if len(scene.strip()) < 80:
            raise AssertionError(f"Scene too sparse or empty: {match.group(1)}")
    if "tundra tối vĩnh viễn" not in world or "Deep Emptiness" not in world or "Claustrophobia" not in world:
        raise AssertionError("Project world hard locks lost from canonical environment")
    print(f"World scene coverage verified: {len(keys)} locations (50 ranked + 17 named)")


def imported_records(manifest):
    records = []
    for document in manifest["documents"]:
        name = document["name"]
        policy = document["policy"]
        if name in manifest["excludedDocuments"]:
            raise AssertionError(f"Excluded source in manifest: {name}")
        if policy not in AUTHORITIES:
            raise AssertionError(f"Unknown source authority policy: {policy}")
        path = ASSETS.parent / document["path"]
        content = path.read_bytes().decode("utf-8")
        if not content:
            raise AssertionError(f"Empty canonical asset: {name}")
        if name == "CAO_MINH_CODEX.md" and "Ngay trước biến cố, Cao Minh ở trên Ma Sơn" in content:
            raise AssertionError("Retired drinking-on-Ma-Son arrival leaked into active codex")
        if name == "LUC_TRAM_CODEX.md" and "Lục Trầm không rơi cùng Cao Minh." in content:
            raise AssertionError("Retired independent Level 0 arrival leaked into active codex")
        topics = document.get("topics", [])
        gate = document.get("gate", "contextual")
        if not topics or any(not isinstance(topic, str) or not topic.strip() or topic != topic.strip().lower() for topic in topics):
            raise AssertionError(f"Missing/invalid curated topic aliases: {name}")
        if len(topics) != len(set(topics)):
            raise AssertionError(f"Repeated topic alias: {name}")
        if gate not in ("contextual", "after_first_contact"):
            raise AssertionError(f"Unknown knowledge disclosure gate: {name}")
        if name == "TRAC_LAM_CODEX.md" and gate != "after_first_contact":
            raise AssertionError("Trac Lam must not be disclosed before first contact")
        stem = Path(name).stem.upper()
        fragments = [content[i:i + CHUNK_CHARS] for i in range(0, len(content), CHUNK_CHARS)]
        authority, mutability = AUTHORITIES[policy]
        for index, excerpt in enumerate(fragments, 1):
            records.append({
                "id": f"{PREFIX}{stem}.C{index:04d}",
                "domain": "NOVEL_ASSET",
                "kind": policy,
                "text": excerpt,
                "source": {
                    "document": f"Novel/Asset/{name}",
                    "anchor": f"full-text excerpt {index}/{len(fragments)}",
                },
                "authority": authority,
                "mutability": mutability,
                "priority": 55,
                "tags": [f"{stem.lower()}:{index:04d}"] + [f"novel_topic:{topic}" for topic in topics] + (["novel_gate:after_first_contact"] if gate == "after_first_contact" else []),
                "references": [],
                "affordances": [],
            })
    return records


def main():
    parser = argparse.ArgumentParser()
    opts = parser.add_mutually_exclusive_group(required=True)
    opts.add_argument("--write", action="store_true")
    opts.add_argument("--check", action="store_true")
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 2 or manifest.get("excludedDocuments"):
        raise AssertionError("Novel/Asset manifest must explicitly cover all eleven documents")
    verify_world_scene_coverage()
    db = json.loads(DB.read_text(encoding="utf-8"))
    original = [item for item in db["records"] if not item["id"].startswith(PREFIX)]
    expected = imported_records(manifest)
    ids = [record["id"] for record in original + expected]
    if len(ids) != len(set(ids)):
        raise AssertionError("Duplicate source or runtime knowledge ID")
    indexed = [item for item in db["records"] if item["id"].startswith(PREFIX)]
    if args.check:
        if indexed != expected:
            raise AssertionError(
                f"Novel/Asset index stale or incomplete: {len(indexed)} present, {len(expected)} expected"
            )
        if len(indexed) == 0 or len(manifest["documents"]) != 11:
            raise AssertionError("Novel/Asset manifest incomplete")
    else:
        db["records"] = original + expected
        DB.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Novel/Asset {'verified' if args.check else 'indexed'}: "
          f"{len(manifest['documents'])} documents, {len(expected)} excerpts, "
          f"{len(original)} existing registry records retained")


if __name__ == "__main__":
    main()
