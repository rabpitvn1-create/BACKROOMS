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
        assert len(documents) == 10, "Expected 10 explicitly approved source documents"
        assert "TRAC_LAM_CODEX.md" in manifest["excludedDocuments"]
        assert PREFIX + "TRAC_LAM_CODEX.md" not in names
        excerpts = [record for record in db["records"]
                    if record["id"].startswith("NOVEL_ASSET.")]
        assert len(excerpts) == 259, f"Expected 259 source chunks, got {len(excerpts)}"
        seen = set()
        for document in documents:
            name = document["name"]
            entry = "assets/" + document["path"]
            assert entry in names, f"Missing packaged source document: {name}"
            full_text = archive.read(entry).decode("utf-8")
            owned = [chunk for chunk in excerpts
                     if chunk["source"]["document"] == "Novel/Asset/" + name]
            assert owned, f"No indexed excerpts for {name}"
            assert all(chunk["id"] not in seen for chunk in owned)
            seen.update(chunk["id"] for chunk in owned)
            reconstructed = "".join(chunk["text"] for chunk in owned)
            assert reconstructed == full_text, f"Packaged index not byte-faithful to {name}"
            assert all(chunk["priority"] == 55 for chunk in owned)
            if document["policy"] == "narrative_lore_writer_only":
                assert all(chunk["authority"] == "WRITER_SECRET" for chunk in owned)
            if name == "CAO_MINH_CODEX.md":
                assert "Ngay trước biến cố, Cao Minh ở trên Ma Sơn" not in full_text
            if name == "LUC_TRAM_CODEX.md":
                assert "Lục Trầm không rơi cùng Cao Minh." not in full_text
        assert len(seen) == len(excerpts), "Index contains unattributed source excerpts"
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
