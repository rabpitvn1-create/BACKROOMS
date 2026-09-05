#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets/items"
CATALOG = ASSETS / "item_catalog.json"
LOOT = ASSETS / "loot_tables.json"

ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]*$")
CATEGORIES = {"CONSUMABLE", "TOOL", "AMMO", "MATERIAL", "EQUIPMENT", "KEY_ITEM", "OTHER"}
STACK_MODES = {"STACK", "INSTANCE"}
CONTENT_MODELS = {"FULL_LOW_EMPTY"}
KNOWN_EFFECTS = {"WATER", "FOOD"}


def fail(message: str) -> None:
    raise SystemExit(f"Item content validation failed: {message}")


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"{path.relative_to(ROOT)}: {exc}")


catalog = read_json(CATALOG)
if catalog.get("schemaVersion") != 1:
    fail("item_catalog.json schemaVersion must be 1")
items = catalog.get("items")
if not isinstance(items, list) or not items:
    fail("item_catalog.json items must be a non-empty array")

ids: set[str] = set()
alias_owner: dict[str, str] = {}
for index, item in enumerate(items):
    where = f"item_catalog.json items[{index}]"
    if not isinstance(item, dict):
        fail(f"{where} must be an object")
    item_id = str(item.get("id", "")).strip()
    if not ID_RE.fullmatch(item_id):
        fail(f"{where}.id is invalid: {item_id!r}")
    if item_id in ids:
        fail(f"duplicate item id: {item_id}")
    ids.add(item_id)
    name = str(item.get("name", "")).strip()
    if not name:
        fail(f"{where}.name is required")
    category = str(item.get("category", "OTHER")).upper()
    if category not in CATEGORIES:
        fail(f"{where}.category is invalid: {category}")
    stack_mode = str(item.get("stackMode", "STACK")).upper()
    if stack_mode not in STACK_MODES:
        fail(f"{where}.stackMode is invalid: {stack_mode}")
    max_stack = item.get("maxStack", 1 if stack_mode == "INSTANCE" else 99)
    if not isinstance(max_stack, int) or max_stack <= 0:
        fail(f"{where}.maxStack must be a positive integer")
    if stack_mode == "INSTANCE" and max_stack != 1:
        fail(f"{where}: INSTANCE items must use maxStack=1")
    content_model = item.get("contentModel")
    if content_model is not None and content_model not in CONTENT_MODELS:
        fail(f"{where}.contentModel is unsupported: {content_model}")
    if content_model == "FULL_LOW_EMPTY":
        states = item.get("stateNames")
        if not isinstance(states, dict) or any(not str(states.get(key, "")).strip() for key in ("FULL", "LOW", "EMPTY")):
            fail(f"{where}.stateNames must define FULL, LOW and EMPTY")
    effects = item.get("effects", [])
    if not isinstance(effects, list):
        fail(f"{where}.effects must be an array")
    unknown_effects = {str(effect).upper() for effect in effects} - KNOWN_EFFECTS
    if unknown_effects:
        fail(f"{where}.effects contains unknown handlers: {sorted(unknown_effects)}")
    aliases = [name, item_id, *item.get("aliases", [])]
    for raw_alias in aliases:
        alias = str(raw_alias).strip().lower()
        if not alias:
            continue
        previous = alias_owner.setdefault(alias, item_id)
        if previous != item_id:
            fail(f"alias {alias!r} belongs to both {previous} and {item_id}")

loot = read_json(LOOT)
if loot.get("schemaVersion") != 1:
    fail("loot_tables.json schemaVersion must be 1")
tables = loot.get("tables")
if not isinstance(tables, dict):
    fail("loot_tables.json tables must be an object")
if not tables.get("explore:default"):
    fail("loot_tables.json must define explore:default")
if not tables.get("entity:default"):
    fail("loot_tables.json must define entity:default")
for table_name, entries in tables.items():
    if not isinstance(entries, list) or not entries:
        fail(f"loot table {table_name} must be a non-empty array")
    for index, entry in enumerate(entries):
        where = f"loot_tables.json {table_name}[{index}]"
        if not isinstance(entry, dict):
            fail(f"{where} must be an object")
        weight = entry.get("weight")
        if not isinstance(weight, int) or weight <= 0:
            fail(f"{where}.weight must be a positive integer")
        item_id = entry.get("itemId")
        if item_id is not None and item_id not in ids:
            fail(f"{where} references unknown itemId {item_id}")
        minimum = entry.get("minQuantity", 1)
        maximum = entry.get("maxQuantity", minimum)
        if not isinstance(minimum, int) or not isinstance(maximum, int) or minimum <= 0 or maximum < minimum:
            fail(f"{where} has an invalid quantity range")
        if item_id is not None:
            definition = next(item for item in items if item["id"] == item_id)
            stack_limit = definition.get("maxStack", 1 if definition.get("stackMode") == "INSTANCE" else 99)
            if maximum > stack_limit:
                fail(f"{where}.maxQuantity exceeds {item_id} maxStack={stack_limit}")

print(f"Item content valid: {len(ids)} definitions, {len(tables)} loot tables.")
