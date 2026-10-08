#!/usr/bin/env python3
"""Deterministically index every permitted Novel/Asset text excerpt into the runtime knowledge DB.

Usage: python3 android-apk/build-novel-asset-canon-index.py --write|--check
No external dependencies, network access or inferred canonical facts.
"""
import argparse
import json
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


def imported_records(manifest):
    records = []
    for document in manifest["documents"]:
        name = document["name"]
        policy = document["policy"]
        if name in manifest["excludedDocuments"] or name == "TRAC_LAM_CODEX.md":
            raise AssertionError(f"Excluded source in manifest: {name}")
        if policy not in AUTHORITIES:
            raise AssertionError(f"Unknown source authority policy: {policy}")
        path = ASSETS / document["path"]
        content = path.read_text(encoding="utf-8")
        if not content:
            raise AssertionError(f"Empty canonical asset: {name}")
        if name == "CAO_MINH_CODEX.md" and "Ngay trước biến cố, Cao Minh ở trên Ma Sơn" in content:
            raise AssertionError("Retired drinking-on-Ma-Son arrival leaked into active codex")
        if name == "LUC_TRAM_CODEX.md" and "Lục Trầm không rơi cùng Cao Minh." in content:
            raise AssertionError("Retired independent Level 0 arrival leaked into active codex")
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
                "tags": [f"{stem.lower()}:{index:04d}"],
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
        if len(indexed) == 0 or len(manifest["documents"]) != 10:
            raise AssertionError("Novel/Asset manifest incomplete")
    else:
        db["records"] = original + expected
        DB.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Novel/Asset {'verified' if args.check else 'indexed'}: "
          f"{len(manifest['documents'])} documents, {len(expected)} excerpts, "
          f"{len(original)} existing registry records retained")


if __name__ == "__main__":
    main()
