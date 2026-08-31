from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CATALOG = ROOT / "app/src/main/assets/level_catalog/backrooms-0-6.json"
CAMPAIGN_ID = "BACKROOMS_FANDOM_LEVELS_0_6_R01"


def java_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def java_string_array(values) -> str:
    return ", ".join(java_string(value) for value in values)


root = json.loads(CATALOG.read_text(encoding="utf-8"))
if not isinstance(root, dict) or root.get("campaignId") != CAMPAIGN_ID:
    raise RuntimeError("campaign_route_catalog_mismatch")
entries = root.get("entries")
if not isinstance(entries, list) or not entries:
    raise RuntimeError("campaign_route_entries_missing")

route = []
seen_ids = set()
seen_orders = set()
for raw in entries:
    if not isinstance(raw, dict):
        raise RuntimeError("campaign_route_entry_not_object")
    level_id = str(raw.get("id") or "").strip()
    if not level_id or level_id in seen_ids:
        raise RuntimeError(f"campaign_route_invalid_id:{level_id}")
    seen_ids.add(level_id)
    try:
        order = int(raw["campaignOrder"])
    except (KeyError, TypeError, ValueError) as error:
        raise RuntimeError(f"campaign_route_order_missing:{level_id}") from error
    if order < 0 or order in seen_orders:
        raise RuntimeError(f"campaign_route_invalid_order:{level_id}:{order}")
    seen_orders.add(order)
    parent = raw.get("parentMainLevel")
    if isinstance(parent, bool):
        raise RuntimeError(f"campaign_route_parent_invalid:{level_id}")
    try:
        parent_level = int(parent)
    except (TypeError, ValueError) as error:
        raise RuntimeError(f"campaign_route_parent_missing:{level_id}") from error
    route.append((order, level_id, raw, parent_level))

route.sort(key=lambda item: item[0])
ids = [item[1] for item in route]
by_id = {item[1]: item for item in route}
main_names = {
    item[3]: str(item[2].get("name") or item[1])
    for item in route
    if str(item[2].get("kind") or "").strip().upper() == "MAIN"
}

next_ids = []
for index, (order, level_id, raw, parent_level) in enumerate(route):
    transitions = raw.get("outgoingTransitions", [])
    if not isinstance(transitions, list):
        raise RuntimeError(f"campaign_route_transitions_not_array:{level_id}")
    targets = []
    for transition in transitions:
        if isinstance(transition, str):
            target = transition.strip()
        elif isinstance(transition, dict):
            target = str(transition.get("targetId") or "").strip()
        else:
            raise RuntimeError(f"campaign_route_transition_invalid:{level_id}")
        if not target or target not in by_id:
            raise RuntimeError(f"campaign_route_target_missing:{level_id}:{target}")
        targets.append(target)

    terminal = str(raw.get("metadata", {}).get("terminal", "")).strip().lower() == "true"
    if index == len(route) - 1:
        if targets or not terminal:
            raise RuntimeError(f"campaign_route_terminal_contract:{level_id}")
        next_ids.append("")
        continue

    expected = ids[index + 1]
    if len(targets) != 1:
        raise RuntimeError(f"campaign_route_must_be_linear:{level_id}:{len(targets)}")
    if targets[0] != expected:
        raise RuntimeError(f"campaign_route_non_adjacent_transition:{level_id}:{targets[0]}:{expected}")
    if by_id[targets[0]][0] <= order:
        raise RuntimeError(f"campaign_route_not_forward:{level_id}:{targets[0]}")
    next_ids.append(expected)

names = [str(item[2].get("name") or item[1]) for item in route]
types = [str(item[2].get("kind") or "").strip().upper() for item in route]
levels = [item[3] for item in route]
parent_names = [main_names.get(level, f"Level {level}") for level in levels]

text = MAIN.read_text(encoding="utf-8")
replacements = {
    "LINEAR_AREA_IDS": f"  private static final String[] LINEAR_AREA_IDS = {{ {java_string_array(ids)} }};",
    "LINEAR_AREA_NAMES": f"  private static final String[] LINEAR_AREA_NAMES = {{ {java_string_array(names)} }};",
    "LINEAR_AREA_TYPES": f"  private static final String[] LINEAR_AREA_TYPES = {{ {java_string_array(types)} }};",
    "LINEAR_AREA_LEVELS": f"  private static final int[] LINEAR_AREA_LEVELS = {{ {', '.join(map(str, levels))} }};",
    "LINEAR_AREA_PARENT_NAMES": f"  private static final String[] LINEAR_AREA_PARENT_NAMES = {{ {java_string_array(parent_names)} }};",
    "LINEAR_AREA_NEXT_IDS": f"  private static final String[] LINEAR_AREA_NEXT_IDS = {{ {java_string_array(next_ids)} }};",
}

for name, replacement in replacements.items():
    pattern = rf"^\s*private static final (?:String|int)\[\] {name} = \{{.*\}};$"
    text, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(f"campaign_route_runtime_array_missing:{name}:{count}")

# Runtime route identity is macro campaign state only. Local procedural topology stays in
# LevelInstance.zones / exploreRoute and must never be injected into these arrays.
if "LINEAR_AREA_IDS" not in text or "LINEAR_AREA_NEXT_IDS" not in text:
    raise RuntimeError("campaign_route_runtime_contract_missing")

MAIN.write_text(text, encoding="utf-8")
print(f"Campaign route authority finalized in campaignOrder with {len(route)} entries; linear handoff targets are explicit and local topology remains separate.")
