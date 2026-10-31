"""Single source of truth for Cao Minh's four visual assets and build-time registry."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ROLES = {"avatar", "combat", "exploration", "sword"}
SCRIPT_MARKER = '<script src="cloudinary-gameplay.js"></script>'
SCRIPT_PREFIX = (
    '<script src="cao-minh-asset-db.js"></script>\n'
    '<script src="offline-character-assets.js"></script>\n'
)


def load_database():
    data = json.loads((ROOT / "web/characters/cao-minh-assets.json").read_text(encoding="utf-8"))
    assets = data["assets"]
    if data.get("character_id") != "cao-minh" or len(assets) != 4 or {
        asset["role"] for asset in assets
    } != ROLES:
        raise ValueError("Cao Minh database must contain avatar, combat, exploration and sword")
    for asset in assets:
        if asset["id"] != "cao-minh:" + asset["role"]:
            raise ValueError("Cao Minh asset identity mismatch")
        if not asset["url"].startswith("https://res.cloudinary.com/csz2me5b/image/upload/"):
            raise ValueError("Unexpected asset host")
        if asset["public_id"] not in asset["url"]:
            raise ValueError("Cloudinary public ID mismatch")
        if asset["bytes"] <= 0 or asset["width"] <= 0 or asset["height"] <= 0:
            raise ValueError("Asset geometry or bytes missing")
        if not re.fullmatch(r"[0-9a-f]{64}", asset["sha256"]):
            raise ValueError("Original image SHA256 missing")
        if asset["mime_type"] not in {"image/png", "image/jpeg"}:
            raise ValueError("Unexpected image MIME type")
        if not asset["url"].endswith(".jpg" if asset["mime_type"] == "image/jpeg" else ".png"):
            raise ValueError("Incorrect asset extension")
    return data


def registry_script(data=None):
    data = load_database() if data is None else data
    return ("window.BACKROOMS_CAO_MINH_ASSET_DATABASE = " +
            json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";\n")


def inject_scripts(html):
    if SCRIPT_MARKER not in html:
        raise ValueError("Missing Cloudinary game bootstrap")
    if SCRIPT_PREFIX in html:
        return html
    return html.replace(SCRIPT_MARKER, SCRIPT_PREFIX + SCRIPT_MARKER, 1)
