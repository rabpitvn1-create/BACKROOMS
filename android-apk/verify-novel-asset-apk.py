#!/usr/bin/env python3
"""Verify full approved Novel/Asset Canon coverage in the actual built APK."""
import json
import sys
from pathlib import Path
from zipfile import ZipFile

PREFIX = "assets/knowledge/novel_asset/"
INDEX = "assets/knowledge/knowledge_db.json"


def verify(apk):
    with ZipFile(apk) as archive:
        names = set(archive.namelist())
        manifest = json.loads(archive.read(PREFIX + "manifest.json"))
        db = json.loads(archive.read(INDEX))
        documents = manifest["documents"]
        assert len(documents) == 11, "Expected all 11 Novel/Asset source documents"
        assert not manifest["excludedDocuments"], "Current user-approved source must not be excluded"
        assert any(doc["name"] == "TRAC_LAM_CODEX.md" for doc in documents)
        excerpts = [record for record in db["records"]
                    if record["id"].startswith("NOVEL_ASSET.")]
        assert excerpts, "No source chunks present"
        seen = set()
        expected_chunks = 0
        for document in documents:
            name = document["name"]
            entry = "assets/" + document["path"]
            assert entry in names, f"Missing packaged source document: {name}"
            full_text = archive.read(entry).decode("utf-8")
            expected_chunks += (len(full_text) + 879) // 880
            owned = [chunk for chunk in excerpts
                     if chunk["source"]["document"] == "Novel/Asset/" + name]
            assert owned, f"No indexed excerpts for {name}"
            assert all(chunk["id"] not in seen for chunk in owned)
            seen.update(chunk["id"] for chunk in owned)
            reconstructed = "".join(chunk["text"] for chunk in owned)
            assert reconstructed == full_text, f"Packaged index not byte-faithful to {name}"
            assert all(chunk["priority"] == 55 for chunk in owned)
            aliases = {"novel_topic:" + topic for topic in document["topics"]}
            assert all(aliases.issubset(set(chunk["tags"])) for chunk in owned)
            if document.get("gate") == "after_first_contact":
                assert all("novel_gate:after_first_contact" in chunk["tags"] for chunk in owned)
            if document["policy"] == "narrative_lore_writer_only":
                assert all(chunk["authority"] == "WRITER_SECRET" for chunk in owned)
            if name == "CAO_MINH_CODEX.md":
                assert "Ngay trước biến cố, Cao Minh ở trên Ma Sơn" not in full_text
            if name == "LUC_TRAM_CODEX.md":
                assert "Lục Trầm không rơi cùng Cao Minh." not in full_text
        assert len(seen) == len(excerpts) == expected_chunks, "Index chunk count or attribution mismatch"
        html = archive.read("assets/index.html").decode("utf-8")
        assert "Lôi Thiên Vực chưa từng có một ngày yên tĩnh như thế." in html, (
            "Active prologue unexpectedly replaced"
        )
        print(f"APK Canon verified: {len(documents)} documents, "
              f"{len(excerpts)} complete indexed chunks, current prologue kept")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify-novel-asset-apk.py <built.apk>")
    apk_path = Path(sys.argv[1])
    if not apk_path.is_file():
        raise SystemExit(f"APK missing: {apk_path}")
    verify(apk_path)
