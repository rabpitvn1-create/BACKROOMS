#!/usr/bin/env python3
"""Validate and report BACKROOMS Level content without executing runtime generation.

The catalog is authoritative. Level IDs are opaque strings and are never parsed numerically.
The report intentionally excludes hidden escape blueprints, evidence graphs and required actions.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict, deque
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any

CATALOG_SCHEMA = 1
DEFINITION_SCHEMA = 1
PROFILE_SCHEMAS = {1, 2}
KINDS = {"MAIN", "SUBLEVEL", "SPECIAL"}
EVIDENCE_SOURCES = {"ENVIRONMENT", "SEARCH", "SURVIVOR", "ANOMALY"}
CANON_PATCH_FIELDS = {
    "environmentTagsAdd", "environmentTagsRemove",
    "requiredZoneTagsAdd", "requiredZoneTagsRemove",
    "allowedPhenomenaAdd", "allowedPhenomenaRemove",
    "forbiddenClaimsAdd", "transitionTagsAdd", "transitionTagsRemove",
    "metadataSet", "metadataRemove",
}
CONSTRAINT_PATCH_FIELDS = {
    "minZones", "maxZones", "minEvidencePerRequiredFact",
    "minEvidenceSourceTypesPerRequiredFact", "maxRequiredActions",
    "allowSurvivors", "allowEntities", "proceduralTopology",
    "proceduralLandmarks", "proceduralEvidencePlacement",
    "proceduralEscapeBlueprint",
}
DEFAULT_CONSTRAINTS = {
    "minZones": 1,
    "maxZones": 64,
    "minEvidencePerRequiredFact": 2,
    "minEvidenceSourceTypesPerRequiredFact": 2,
    "maxRequiredActions": 12,
    "allowSurvivors": True,
    "allowEntities": True,
    "proceduralTopology": False,
    "proceduralLandmarks": False,
    "proceduralEvidencePlacement": False,
    "proceduralEscapeBlueprint": False,
}
HIDDEN_REPORT_TERMS = {
    "escapeBlueprint", "solutionId", "requiredFacts", "requiredActions",
    "evidence", "actions", "completedActions", "levelInstance",
}


@dataclass(frozen=True, order=True)
class Issue:
    file: str
    levelId: str
    code: str
    detail: str = ""
    target: str = ""

    def to_json(self) -> dict[str, str]:
        return {k: v for k, v in asdict(self).items() if v != ""}


class Collector:
    def __init__(self) -> None:
        self.issues: list[Issue] = []

    def add(self, file: str, level: str, code: str, detail: str = "", target: str = "") -> None:
        self.issues.append(Issue(file, level, code, detail, target))

    def sorted(self) -> list[Issue]:
        return sorted(set(self.issues))


def _truthy(value: Any) -> bool:
    return value is True or (isinstance(value, str) and value.strip().lower() in {"1", "true", "yes", "on"})


def _safe_id(value: Any) -> str:
    return value if isinstance(value, str) else ""


def _rel(path: Path, assets_root: Path) -> str:
    try:
        return path.relative_to(assets_root).as_posix()
    except ValueError:
        return path.as_posix()


def _json_files(root: Path, directory: str) -> list[Path]:
    base = root / directory
    if not base.is_dir():
        return []
    return sorted(p for p in base.rglob("*.json") if p.is_file() and not p.name.startswith("_"))


def _load_json(path: Path, assets_root: Path, c: Collector) -> Any | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        c.add(_rel(path, assets_root), "", "json_invalid", f"{type(exc).__name__}: {exc}")
        return None


def _validate_id(level_id: str, file: str, c: Collector, prefix: str = "level") -> None:
    if not level_id.strip():
        c.add(file, level_id, f"{prefix}_id_missing")
        return
    if len(level_id) > 128:
        c.add(file, level_id, f"{prefix}_id_too_long")
    if any(ch in "/\\" or ord(ch) < 32 or ord(ch) == 127 for ch in level_id):
        c.add(file, level_id, f"{prefix}_id_invalid_character")


def _metadata(obj: dict[str, Any]) -> dict[str, Any]:
    value = obj.get("metadata")
    return value if isinstance(value, dict) else {}


def _is_placeholder(entry: dict[str, Any]) -> bool:
    status = str(_metadata(entry).get("contentStatus", "")).strip().lower()
    return status in {"placeholder", "content-placeholder", "intentionally-unimplemented"}


def _is_terminal(entry: dict[str, Any]) -> bool:
    meta = _metadata(entry)
    return _truthy(meta.get("terminal")) or _truthy(meta.get("endContent"))


def _transitions(entry: dict[str, Any], file: str, c: Collector) -> list[str]:
    raw = entry.get("outgoingTransitions", [])
    if raw is None:
        return []
    if not isinstance(raw, list):
        c.add(file, _safe_id(entry.get("id")), "transition_list_invalid")
        return []
    result: list[str] = []
    for item in raw:
        if isinstance(item, str):
            target = item
        elif isinstance(item, dict):
            target = _safe_id(item.get("targetId"))
        else:
            c.add(file, _safe_id(entry.get("id")), "transition_entry_invalid")
            continue
        result.append(target)
    return result


def load_catalog(assets_root: Path, c: Collector) -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    entries: dict[str, dict[str, Any]] = {}
    sources: dict[str, str] = {}
    for path in _json_files(assets_root, "level_catalog"):
        file = _rel(path, assets_root)
        raw = _load_json(path, assets_root, c)
        if raw is None:
            continue
        inherited_campaign: str | None = None
        inherited_schema = CATALOG_SCHEMA
        if isinstance(raw, list):
            items = raw
        elif isinstance(raw, dict):
            if "entries" in raw:
                items = raw.get("entries")
                if not isinstance(items, list):
                    c.add(file, "", "catalog_entries_invalid")
                    continue
                inherited_campaign = str(raw.get("campaignId") or "").strip() or None
                schema = raw.get("schemaVersion", CATALOG_SCHEMA)
                if not isinstance(schema, int) or isinstance(schema, bool):
                    c.add(file, "", "catalog_schema_invalid")
                    schema = -1
                inherited_schema = schema
            else:
                items = [raw]
        else:
            c.add(file, "", "catalog_document_invalid")
            continue

        for index, item in enumerate(items):
            if not isinstance(item, dict):
                c.add(file, "", "catalog_entry_not_object", f"index={index}")
                continue
            entry = dict(item)
            entry.setdefault("schemaVersion", inherited_schema)
            if inherited_campaign and not str(entry.get("campaignId") or "").strip():
                entry["campaignId"] = inherited_campaign
            level_id = _safe_id(entry.get("id"))
            if level_id in entries:
                c.add(file, level_id, "duplicate_level_id", f"first={sources[level_id]}")
                continue
            entries[level_id] = entry
            sources[level_id] = file
    return entries, sources


def validate_catalog(entries: dict[str, dict[str, Any]], sources: dict[str, str], c: Collector) -> None:
    campaign_orders: dict[str, dict[int, str]] = defaultdict(dict)
    adjacency: dict[str, list[str]] = {level_id: [] for level_id in entries}

    for level_id, entry in entries.items():
        file = sources.get(level_id, "level_catalog")
        _validate_id(level_id, file, c)
        name = entry.get("name")
        if not isinstance(name, str) or not name.strip():
            c.add(file, level_id, "level_name_missing")
        kind = str(entry.get("kind") or "").strip().upper()
        if kind not in KINDS:
            c.add(file, level_id, "unknown_level_kind", kind)
        schema = entry.get("schemaVersion", CATALOG_SCHEMA)
        if schema != CATALOG_SCHEMA:
            c.add(file, level_id, "unsupported_catalog_schema", str(schema))

        parent = entry.get("parentId")
        if parent is not None and not isinstance(parent, str):
            c.add(file, level_id, "parent_id_invalid_type")
            parent = None
        parent = parent.strip() if isinstance(parent, str) else ""
        if parent:
            if parent == level_id:
                c.add(file, level_id, "parent_self_reference")
            elif parent not in entries:
                c.add(file, level_id, "parent_missing", target=parent)
            else:
                child_campaign = str(entry.get("campaignId") or "").strip()
                parent_campaign = str(entries[parent].get("campaignId") or "").strip()
                if child_campaign != parent_campaign:
                    c.add(file, level_id, "parent_campaign_mismatch", target=parent)

        campaign = str(entry.get("campaignId") or "").strip()
        order = entry.get("campaignOrder")
        if order is not None:
            if isinstance(order, bool) or not isinstance(order, int) or order < 0:
                c.add(file, level_id, "campaign_order_invalid", repr(order))
            elif not campaign:
                c.add(file, level_id, "campaign_id_missing_for_order")
            elif order in campaign_orders[campaign]:
                c.add(file, level_id, "duplicate_campaign_order", f"order={order}", campaign_orders[campaign][order])
            else:
                campaign_orders[campaign][order] = level_id

        targets = _transitions(entry, file, c)
        seen: set[str] = set()
        for target in targets:
            if not target.strip():
                c.add(file, level_id, "transition_target_missing")
                continue
            if target in seen:
                c.add(file, level_id, "duplicate_transition", target=target)
                continue
            seen.add(target)
            adjacency[level_id].append(target)
            if target == level_id:
                c.add(file, level_id, "transition_self_loop", target=target)
                continue
            if target not in entries:
                c.add(file, level_id, "transition_target_missing", target=target)
                continue
            target_entry = entries[target]
            src_campaign = str(entry.get("campaignId") or "").strip()
            dst_campaign = str(target_entry.get("campaignId") or "").strip()
            if not src_campaign or src_campaign != dst_campaign:
                c.add(file, level_id, "transition_campaign_mismatch", target=target)
            src_order = entry.get("campaignOrder")
            dst_order = target_entry.get("campaignOrder")
            if not isinstance(src_order, int) or isinstance(src_order, bool) or not isinstance(dst_order, int) or isinstance(dst_order, bool):
                c.add(file, level_id, "transition_order_missing", target=target)
            elif dst_order <= src_order:
                c.add(file, level_id, "transition_not_forward", f"{src_order}->{dst_order}", target)

        if not targets and not _is_terminal(entry) and not _is_placeholder(entry):
            c.add(file, level_id, "terminal_not_declared")

    complete: set[str] = set()
    for start in sorted(entries):
        if start in complete:
            continue
        positions: dict[str, int] = {}
        chain: list[str] = []
        current = start
        while current in entries and current not in complete:
            if current in positions:
                cycle = chain[positions[current]:] + [current]
                c.add(sources.get(current, "level_catalog"), current, "parent_cycle", "->".join(cycle))
                break
            positions[current] = len(chain)
            chain.append(current)
            parent = entries[current].get("parentId")
            current = parent.strip() if isinstance(parent, str) else ""
            if not current:
                break
        complete.update(chain)

    indegree = {level_id: 0 for level_id in entries}
    valid_adj: dict[str, list[str]] = {level_id: [] for level_id in entries}
    for source, targets in adjacency.items():
        for target in targets:
            if target in entries and target != source:
                valid_adj[source].append(target)
                indegree[target] += 1
    queue = deque(sorted(k for k, degree in indegree.items() if degree == 0))
    visited = 0
    while queue:
        node = queue.popleft()
        visited += 1
        for target in sorted(valid_adj[node]):
            indegree[target] -= 1
            if indegree[target] == 0:
                queue.append(target)
    if visited != len(entries):
        cycle_nodes = sorted(k for k, degree in indegree.items() if degree > 0)
        for node in cycle_nodes:
            c.add(sources.get(node, "level_catalog"), node, "transition_cycle", ",".join(cycle_nodes))


def _canon_profile(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict):
        return {"environmentTags": [], "requiredZoneTags": [], "allowedPhenomena": [], "forbiddenClaims": [], "transitionTags": [], "metadata": {}}
    return {
        "environmentTags": list(raw.get("environmentTags", [])) if isinstance(raw.get("environmentTags", []), list) else [],
        "requiredZoneTags": list(raw.get("requiredZoneTags", [])) if isinstance(raw.get("requiredZoneTags", []), list) else [],
        "allowedPhenomena": list(raw.get("allowedPhenomena", [])) if isinstance(raw.get("allowedPhenomena", []), list) else [],
        "forbiddenClaims": list(raw.get("forbiddenClaims", [])) if isinstance(raw.get("forbiddenClaims", []), list) else [],
        "transitionTags": list(raw.get("transitionTags", [])) if isinstance(raw.get("transitionTags", []), list) else [],
        "metadata": dict(raw.get("metadata", {})) if isinstance(raw.get("metadata", {}), dict) else {},
    }


def _constraints(raw: Any) -> dict[str, Any]:
    result = dict(DEFAULT_CONSTRAINTS)
    if isinstance(raw, dict):
        for key in DEFAULT_CONSTRAINTS:
            if key in raw:
                result[key] = raw[key]
    return result


def validate_constraints(constraints: dict[str, Any], file: str, level: str, c: Collector, prefix: str = "profile") -> None:
    int_fields = ["minZones", "maxZones", "minEvidencePerRequiredFact", "minEvidenceSourceTypesPerRequiredFact", "maxRequiredActions"]
    for key in int_fields:
        value = constraints.get(key)
        if isinstance(value, bool) or not isinstance(value, int):
            c.add(file, level, f"{prefix}_generation_constraint_type", f"{key}={value!r}")
            return
    if constraints["minZones"] < 1:
        c.add(file, level, f"{prefix}_generation_min_zones_invalid")
    if constraints["maxZones"] < max(2, constraints["minZones"]):
        c.add(file, level, f"{prefix}_generation_zone_range_invalid")
    if constraints["minEvidencePerRequiredFact"] < 1:
        c.add(file, level, f"{prefix}_generation_evidence_count_invalid")
    if constraints["minEvidenceSourceTypesPerRequiredFact"] < 1:
        c.add(file, level, f"{prefix}_generation_evidence_sources_invalid")
    if constraints["maxRequiredActions"] < 1:
        c.add(file, level, f"{prefix}_generation_max_actions_invalid")
    for key in DEFAULT_CONSTRAINTS:
        if isinstance(DEFAULT_CONSTRAINTS[key], bool) and not isinstance(constraints.get(key), bool):
            c.add(file, level, f"{prefix}_generation_constraint_type", f"{key}={constraints.get(key)!r}")
    available_sources = 4 if constraints.get("allowSurvivors") is True else 3
    if constraints["minEvidenceSourceTypesPerRequiredFact"] > available_sources:
        c.add(file, level, f"{prefix}_generation_source_diversity_unreachable")


def load_definitions(assets_root: Path, catalog: dict[str, dict[str, Any]], c: Collector) -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    definitions: dict[str, dict[str, Any]] = {}
    sources: dict[str, str] = {}
    for path in _json_files(assets_root, "levels"):
        file = _rel(path, assets_root)
        raw = _load_json(path, assets_root, c)
        if not isinstance(raw, dict):
            if raw is not None:
                c.add(file, "", "level_definition_not_object")
            continue
        level_id = _safe_id(raw.get("id"))
        if level_id in definitions:
            c.add(file, level_id, "duplicate_level_definition", f"first={sources[level_id]}")
            continue
        definitions[level_id] = raw
        sources[level_id] = file
        _validate_id(level_id, file, c)
        if level_id not in catalog:
            c.add(file, level_id, "definition_level_not_in_catalog")
        elif (raw.get("parentId") or None) != (catalog[level_id].get("parentId") or None):
            c.add(file, level_id, "definition_parent_mismatch", target=str(catalog[level_id].get("parentId") or ""))
        if raw.get("schemaVersion", DEFINITION_SCHEMA) != DEFINITION_SCHEMA:
            c.add(file, level_id, "unsupported_definition_schema", str(raw.get("schemaVersion")))
        validate_definition(raw, file, c)
    return definitions, sources


def _reachable(zones: dict[str, dict[str, Any]], start: str) -> set[str]:
    if start not in zones:
        return set()
    seen = {start}
    q = deque([start])
    while q:
        node = q.popleft()
        connections = zones[node].get("connections", [])
        if not isinstance(connections, list):
            continue
        for target in connections:
            if isinstance(target, str) and target in zones and target not in seen:
                seen.add(target)
                q.append(target)
    return seen


def _condition_parts(condition: str) -> tuple[str, str]:
    return condition.split(":", 1) if ":" in condition else (condition, "")


def validate_definition(definition: dict[str, Any], file: str, c: Collector) -> None:
    level = _safe_id(definition.get("id"))
    name = definition.get("name")
    if not isinstance(name, str) or not name.strip():
        c.add(file, level, "level_name_missing")
    constraints = _constraints(definition.get("generationConstraints"))
    validate_constraints(constraints, file, level, c, "definition")

    raw_zones = definition.get("zones")
    if not isinstance(raw_zones, list):
        c.add(file, level, "zones_invalid")
        raw_zones = []
    zones: dict[str, dict[str, Any]] = {}
    for index, zone in enumerate(raw_zones):
        if not isinstance(zone, dict):
            c.add(file, level, "zone_not_object", f"index={index}")
            continue
        zid = _safe_id(zone.get("id"))
        if not zid:
            c.add(file, level, "zone_id_missing", f"index={index}")
            continue
        if zid in zones:
            c.add(file, level, "duplicate_zone", target=zid)
            continue
        zones[zid] = zone
        if not isinstance(zone.get("name"), str) or not str(zone.get("name")).strip():
            c.add(file, level, "zone_name_missing", target=zid)
    for zid, zone in zones.items():
        conns = zone.get("connections", [])
        if not isinstance(conns, list):
            c.add(file, level, "zone_connections_invalid", target=zid)
            continue
        for target in conns:
            if not isinstance(target, str) or target not in zones:
                c.add(file, level, "unknown_connection", target=f"{zid}->{target}")

    initial = _safe_id(definition.get("initialZoneId"))
    if initial not in zones:
        c.add(file, level, "initial_zone_missing", target=initial)
    reachable = _reachable(zones, initial)

    canon = _canon_profile(definition.get("canonProfile"))
    present_tags: set[str] = set()
    escape_zones: set[str] = set()
    for zid, zone in zones.items():
        tags = zone.get("tags", [])
        if isinstance(tags, list):
            string_tags = {x for x in tags if isinstance(x, str)}
            present_tags |= string_tags
            if "escape" in string_tags:
                escape_zones.add(zid)
    for tag in canon["requiredZoneTags"]:
        if isinstance(tag, str) and tag not in present_tags:
            c.add(file, level, "missing_required_zone_tag", target=tag)
    if not escape_zones:
        c.add(file, level, "escape_zone_missing")
    elif not (escape_zones & reachable):
        c.add(file, level, "escape_zone_unreachable")

    route = definition.get("exploreRoute", [])
    if not isinstance(route, list):
        c.add(file, level, "explore_route_invalid")
        route = []
    previous = initial
    for index, target in enumerate(route):
        if not isinstance(target, str) or target not in zones:
            c.add(file, level, "unknown_explore_zone", f"index={index}", str(target))
            previous = str(target)
            continue
        if previous in zones:
            conns = zones[previous].get("connections", [])
            if not isinstance(conns, list) or target not in conns:
                c.add(file, level, "explore_route_disconnected", f"index={index}:{previous}->{target}")
        previous = target

    blueprint = definition.get("escapeBlueprint")
    if not isinstance(blueprint, dict):
        c.add(file, level, "escape_blueprint_missing")
        blueprint = {}
    if blueprint.get("locked", True) is not True:
        c.add(file, level, "escape_blueprint_must_be_locked")
    required_facts = blueprint.get("requiredFacts", [])
    required_actions = blueprint.get("requiredActions", [])
    if not isinstance(required_facts, list) or not [f for f in required_facts if isinstance(f, str) and f]:
        c.add(file, level, "required_facts_missing")
        required_facts = []
    if not isinstance(required_actions, list) or not [a for a in required_actions if isinstance(a, str) and a]:
        c.add(file, level, "required_actions_missing")
        required_actions = []
    if len(required_actions) > constraints.get("maxRequiredActions", 0):
        c.add(file, level, "required_actions_exceed_limit")

    raw_evidence = definition.get("evidence", [])
    if not isinstance(raw_evidence, list):
        c.add(file, level, "evidence_invalid")
        raw_evidence = []
    evidence_by_id: dict[str, dict[str, Any]] = {}
    for item in raw_evidence:
        if not isinstance(item, dict):
            c.add(file, level, "evidence_not_object")
            continue
        eid = _safe_id(item.get("id"))
        if not eid:
            c.add(file, level, "evidence_id_missing")
            continue
        if eid in evidence_by_id:
            c.add(file, level, "duplicate_evidence", target=eid)
            continue
        evidence_by_id[eid] = item
        supports = item.get("supports", [])
        sources = item.get("sources", [])
        if not isinstance(supports, list) or not any(isinstance(x, str) and x for x in supports):
            c.add(file, level, "evidence_support_missing", target=eid)
        if not isinstance(sources, list) or not sources:
            c.add(file, level, "evidence_source_missing", target=eid)
        else:
            for source in sources:
                if source not in EVIDENCE_SOURCES:
                    c.add(file, level, "unknown_evidence_source", f"evidence={eid}", str(source))
                if source == "SURVIVOR" and constraints.get("allowSurvivors") is False:
                    c.add(file, level, "survivor_evidence_forbidden", target=eid)
        zone = item.get("zoneId")
        if zone is not None:
            if not isinstance(zone, str) or zone not in zones:
                c.add(file, level, "unknown_evidence_zone", target=f"{eid}:{zone}")
            elif zone not in reachable:
                c.add(file, level, "unreachable_evidence", target=eid)

    min_evidence = constraints.get("minEvidencePerRequiredFact", 1)
    min_sources = constraints.get("minEvidenceSourceTypesPerRequiredFact", 1)
    for fact in required_facts:
        if not isinstance(fact, str):
            continue
        supporting = []
        source_types: set[str] = set()
        for item in evidence_by_id.values():
            supports = item.get("supports", [])
            zone = item.get("zoneId")
            if isinstance(supports, list) and fact in supports and (zone is None or zone in reachable):
                supporting.append(item)
                sources = item.get("sources", [])
                if isinstance(sources, list):
                    source_types.update(x for x in sources if isinstance(x, str))
        if len(supporting) < min_evidence:
            c.add(file, level, "impossible_fact_quorum", f"fact={fact}:evidence={len(supporting)}/{min_evidence}")
        if len(source_types) < min_sources:
            c.add(file, level, "impossible_source_quorum", f"fact={fact}:sources={len(source_types)}/{min_sources}")

    raw_actions = definition.get("actions", [])
    if not isinstance(raw_actions, list):
        c.add(file, level, "actions_invalid")
        raw_actions = []
    actions: dict[str, dict[str, Any]] = {}
    for action in raw_actions:
        if not isinstance(action, dict):
            c.add(file, level, "action_not_object")
            continue
        aid = _safe_id(action.get("id"))
        if not aid:
            c.add(file, level, "action_id_missing")
            continue
        if aid in actions:
            c.add(file, level, "duplicate_action", target=aid)
            continue
        actions[aid] = action
        groups = action.get("matchGroups", [])
        if not isinstance(groups, list) or not groups or any(not isinstance(group, list) or not group for group in groups):
            c.add(file, level, "action_matcher_missing", target=aid)
        for effect in action.get("effects", []) if isinstance(action.get("effects", []), list) else []:
            if not isinstance(effect, dict):
                c.add(file, level, "effect_invalid", target=aid)
                continue
            kind = effect.get("type")
            if kind not in {"SET_ENVIRONMENT", "MOVE_TO_ZONE", "COMPLETE_LEVEL"}:
                c.add(file, level, "unknown_level_effect", f"action={aid}", str(kind))
            elif kind == "MOVE_TO_ZONE" and effect.get("zoneId") not in zones:
                c.add(file, level, "effect_zone_unknown", f"action={aid}", str(effect.get("zoneId")))
            elif kind == "SET_ENVIRONMENT" and (not isinstance(effect.get("key"), str) or not effect.get("key") or "value" not in effect):
                c.add(file, level, "effect_environment_invalid", target=aid)

    known_facts = {x for x in required_facts if isinstance(x, str)}
    for item in evidence_by_id.values():
        supports = item.get("supports", [])
        if isinstance(supports, list):
            known_facts.update(x for x in supports if isinstance(x, str))

    def validate_condition(owner: str, condition: Any) -> None:
        if not isinstance(condition, str):
            c.add(file, level, "invalid_condition", f"{owner}:{condition!r}")
            return
        kind, body = _condition_parts(condition)
        if kind == "visit":
            parts = body.split(":")
            try:
                count = int(parts[1]) if len(parts) == 2 else 0
            except ValueError:
                count = 0
            if len(parts) != 2 or parts[0] not in zones or count < 1:
                c.add(file, level, "invalid_condition", f"{owner}:{condition}")
        elif kind == "env":
            if "=" not in body or not body.split("=", 1)[0]:
                c.add(file, level, "invalid_condition", f"{owner}:{condition}")
        elif kind == "zone":
            if body not in zones:
                c.add(file, level, "invalid_condition", f"{owner}:{condition}")
        elif kind == "action":
            if body not in actions:
                c.add(file, level, "invalid_condition", f"{owner}:{condition}")
        elif kind == "fact":
            if body not in known_facts:
                c.add(file, level, "invalid_condition", f"{owner}:{condition}")
        else:
            c.add(file, level, "unsupported_condition", f"{owner}:{condition}")

    for eid, item in evidence_by_id.items():
        conditions = item.get("discoverConditions", [])
        if isinstance(conditions, list):
            for condition in conditions:
                validate_condition(f"evidence:{eid}", condition)
    for aid, action in actions.items():
        conditions = action.get("conditions", [])
        if isinstance(conditions, list):
            for condition in conditions:
                validate_condition(f"action:{aid}", condition)

    env = dict(definition.get("environment", {})) if isinstance(definition.get("environment"), dict) else {}
    completed: set[str] = set()
    complete_indexes: list[int] = []
    for index, action_id in enumerate(required_actions):
        if not isinstance(action_id, str) or action_id not in actions:
            c.add(file, level, "missing_required_action_rule", target=str(action_id))
            continue
        action = actions[action_id]
        conditions = action.get("conditions", []) if isinstance(action.get("conditions", []), list) else []
        for condition in conditions:
            if not isinstance(condition, str):
                continue
            kind, body = _condition_parts(condition)
            if kind == "action" and body not in completed:
                c.add(file, level, "impossible_action_order", f"action={action_id}:requires={body}")
            elif kind == "env" and "=" in body:
                key, value = body.split("=", 1)
                if env.get(key) != value:
                    c.add(file, level, "environment_dependency_softlock", f"action={action_id}:{key}={value}")
            elif kind == "zone" and body not in reachable:
                c.add(file, level, "inaccessible_required_zone", f"action={action_id}", body)
        effects = action.get("effects", []) if isinstance(action.get("effects", []), list) else []
        for effect in effects:
            if not isinstance(effect, dict):
                continue
            if effect.get("type") == "SET_ENVIRONMENT" and isinstance(effect.get("key"), str):
                env[effect["key"]] = effect.get("value")
            elif effect.get("type") == "COMPLETE_LEVEL":
                complete_indexes.append(index)
        completed.add(action_id)
    if required_actions:
        if not complete_indexes:
            c.add(file, level, "missing_complete_level_path")
        elif complete_indexes[-1] != len(required_actions) - 1:
            c.add(file, level, "complete_level_before_required_actions_done", f"index={complete_indexes[-1]}")


def _validate_canon(raw: Any, file: str, level: str, c: Collector, required: bool) -> dict[str, Any]:
    if not isinstance(raw, dict):
        if required:
            c.add(file, level, "canon_profile_missing")
        return _canon_profile(None)
    for key in ["environmentTags", "requiredZoneTags", "allowedPhenomena", "forbiddenClaims", "transitionTags"]:
        value = raw.get(key, [])
        if not isinstance(value, list) or any(not isinstance(x, str) or not x for x in value):
            c.add(file, level, "canon_profile_field_invalid", key)
    if "metadata" in raw and not isinstance(raw.get("metadata"), dict):
        c.add(file, level, "canon_profile_field_invalid", "metadata")
    return _canon_profile(raw)


def _validate_patch(raw: Any, file: str, level: str, c: Collector) -> dict[str, Any]:
    if raw is None:
        return {}
    if not isinstance(raw, dict):
        c.add(file, level, "canon_patch_invalid")
        return {}
    for key in sorted(set(raw) - CANON_PATCH_FIELDS):
        c.add(file, level, "unknown_canon_patch_field", key)
    pairs = [
        ("environmentTagsAdd", "environmentTagsRemove", "environmentTags"),
        ("requiredZoneTagsAdd", "requiredZoneTagsRemove", "requiredZoneTags"),
        ("allowedPhenomenaAdd", "allowedPhenomenaRemove", "allowedPhenomena"),
        ("transitionTagsAdd", "transitionTagsRemove", "transitionTags"),
    ]
    for add_key, remove_key, label in pairs:
        add = raw.get(add_key, [])
        remove = raw.get(remove_key, [])
        if not isinstance(add, list) or not isinstance(remove, list):
            c.add(file, level, "canon_patch_field_invalid", label)
            continue
        overlap = sorted(set(x for x in add if isinstance(x, str)) & set(x for x in remove if isinstance(x, str)))
        for item in overlap:
            c.add(file, level, "profile_canon_patch_conflict", f"{label}:{item}")
    metadata_set = raw.get("metadataSet", {})
    metadata_remove = raw.get("metadataRemove", [])
    if isinstance(metadata_set, dict) and isinstance(metadata_remove, list):
        for key in sorted(set(metadata_set) & set(x for x in metadata_remove if isinstance(x, str))):
            c.add(file, level, "profile_canon_patch_conflict", f"metadata:{key}")
    return raw


def _validate_constraint_patch(raw: Any, file: str, level: str, c: Collector) -> dict[str, Any]:
    if raw is None:
        return {}
    if not isinstance(raw, dict):
        c.add(file, level, "generation_constraints_patch_invalid")
        return {}
    for key in sorted(set(raw) - CONSTRAINT_PATCH_FIELDS):
        c.add(file, level, "unknown_generation_constraints_patch_field", key)
    return {k: v for k, v in raw.items() if k in CONSTRAINT_PATCH_FIELDS}


def load_profiles(assets_root: Path, catalog: dict[str, dict[str, Any]], definitions: dict[str, dict[str, Any]], c: Collector) -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    profiles: dict[str, dict[str, Any]] = {}
    sources: dict[str, str] = {}
    for path in _json_files(assets_root, "level_profiles"):
        file = _rel(path, assets_root)
        raw = _load_json(path, assets_root, c)
        if not isinstance(raw, dict):
            if raw is not None:
                c.add(file, "", "profile_not_object")
            continue
        level = _safe_id(raw.get("id"))
        _validate_id(level, file, c, "profile_level")
        if level in profiles:
            c.add(file, level, "duplicate_profile", f"first={sources[level]}")
            continue
        profiles[level] = raw
        sources[level] = file
        if level not in catalog:
            c.add(file, level, "profile_level_not_in_catalog")
        if level in definitions:
            c.add(file, level, "explicit_profile_collision", f"definition={level}")
        schema = raw.get("schemaVersion", 1)
        if schema not in PROFILE_SCHEMAS:
            c.add(file, level, "unsupported_profile_schema", str(schema))
        parent = raw.get("inheritsFrom")
        if parent is not None and not isinstance(parent, str):
            c.add(file, level, "profile_inheritance_source_invalid_type")
            parent = None
        parent = parent.strip() if isinstance(parent, str) else ""
        if parent:
            if schema != 2:
                c.add(file, level, "profile_inheritance_requires_schema_2")
            if parent == level:
                c.add(file, level, "profile_inheritance_self_reference")
            if level in catalog and (catalog[level].get("parentId") or None) != parent:
                c.add(file, level, "profile_inheritance_must_match_catalog_parent", target=parent)
            _validate_patch(raw.get("canonPatch"), file, level, c)
            _validate_constraint_patch(raw.get("generationConstraintsPatch"), file, level, c)
        else:
            if raw.get("canonPatch") not in (None, {}) or raw.get("generationConstraintsPatch") not in (None, {}):
                c.add(file, level, "profile_patch_requires_inheritance")
            _validate_canon(raw.get("canonProfile"), file, level, c, required=True)
            if not isinstance(raw.get("generationConstraints"), dict):
                c.add(file, level, "profile_generation_constraints_missing")
            constraints = _constraints(raw.get("generationConstraints"))
            validate_constraints(constraints, file, level, c)
    return profiles, sources


def _apply_canon_patch(base: dict[str, Any], patch: dict[str, Any]) -> dict[str, Any]:
    result = {
        "environmentTags": list(base["environmentTags"]),
        "requiredZoneTags": list(base["requiredZoneTags"]),
        "allowedPhenomena": list(base["allowedPhenomena"]),
        "forbiddenClaims": list(base["forbiddenClaims"]),
        "transitionTags": list(base["transitionTags"]),
        "metadata": dict(base["metadata"]),
    }
    for field, add_key, remove_key in [
        ("environmentTags", "environmentTagsAdd", "environmentTagsRemove"),
        ("requiredZoneTags", "requiredZoneTagsAdd", "requiredZoneTagsRemove"),
        ("allowedPhenomena", "allowedPhenomenaAdd", "allowedPhenomenaRemove"),
        ("transitionTags", "transitionTagsAdd", "transitionTagsRemove"),
    ]:
        values = set(x for x in result[field] if isinstance(x, str))
        remove = patch.get(remove_key, []) if isinstance(patch.get(remove_key, []), list) else []
        add = patch.get(add_key, []) if isinstance(patch.get(add_key, []), list) else []
        values.difference_update(x for x in remove if isinstance(x, str))
        values.update(x for x in add if isinstance(x, str))
        result[field] = sorted(values)
    result["forbiddenClaims"] = sorted(set(result["forbiddenClaims"]) | set(x for x in patch.get("forbiddenClaimsAdd", []) if isinstance(x, str)))
    metadata_remove = patch.get("metadataRemove", []) if isinstance(patch.get("metadataRemove", []), list) else []
    for key in metadata_remove:
        if isinstance(key, str):
            result["metadata"].pop(key, None)
    metadata_set = patch.get("metadataSet", {}) if isinstance(patch.get("metadataSet", {}), dict) else {}
    result["metadata"].update({str(k): str(v) for k, v in metadata_set.items()})
    return result


def resolve_profiles(profiles: dict[str, dict[str, Any]], profile_sources: dict[str, str], definitions: dict[str, dict[str, Any]], c: Collector) -> dict[str, dict[str, Any]]:
    resolved: dict[str, dict[str, Any]] = {}

    def explicit_base(level: str) -> dict[str, Any] | None:
        raw = definitions.get(level)
        if raw is None:
            return None
        return {"id": level, "canonProfile": _canon_profile(raw.get("canonProfile")), "generationConstraints": _constraints(raw.get("generationConstraints")), "source": "explicit"}

    for start in sorted(profiles):
        if start in resolved:
            continue
        chain: list[str] = []
        positions: dict[str, int] = {}
        current = start
        base: dict[str, Any] | None = None
        while True:
            if current in resolved:
                base = resolved[current]
                break
            if current in positions:
                cycle = chain[positions[current]:] + [current]
                c.add(profile_sources.get(start, "level_profiles"), start, "inheritance_cycle", "->".join(cycle))
                base = None
                break
            positions[current] = len(chain)
            raw = profiles.get(current)
            if raw is None:
                base = explicit_base(current)
                if base is None:
                    c.add(profile_sources.get(start, "level_profiles"), start, "inheritance_source_missing", target=current)
                break
            chain.append(current)
            parent = raw.get("inheritsFrom")
            if not isinstance(parent, str) or not parent.strip():
                base = None
                break
            current = parent.strip()

        effective = base
        failed = False
        for level in reversed(chain):
            raw = profiles[level]
            parent = raw.get("inheritsFrom")
            if isinstance(parent, str) and parent.strip():
                if effective is None:
                    if not any(i.code == "inheritance_source_missing" and i.levelId == start for i in c.issues):
                        c.add(profile_sources[level], level, "inheritance_source_missing", target=parent.strip())
                    failed = True
                    break
                canon = _apply_canon_patch(effective["canonProfile"], raw.get("canonPatch") if isinstance(raw.get("canonPatch"), dict) else {})
                constraints = dict(effective["generationConstraints"])
                patch = raw.get("generationConstraintsPatch") if isinstance(raw.get("generationConstraintsPatch"), dict) else {}
                for key in CONSTRAINT_PATCH_FIELDS:
                    if key in patch:
                        constraints[key] = patch[key]
                validate_constraints(constraints, profile_sources[level], level, c)
                effective = {"id": level, "canonProfile": canon, "generationConstraints": constraints, "source": "inherited-profile", "inheritsFrom": parent.strip()}
            else:
                canon = _canon_profile(raw.get("canonProfile"))
                constraints = _constraints(raw.get("generationConstraints"))
                validate_constraints(constraints, profile_sources[level], level, c)
                effective = {"id": level, "canonProfile": canon, "generationConstraints": constraints, "source": "procedural-profile", "inheritsFrom": None}
            resolved[level] = effective
        if failed:
            for level in chain:
                resolved.pop(level, None)
    return resolved


def compile_profile_fallback(entry: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    constraints = profile["generationConstraints"]
    canon = profile["canonProfile"]
    zone_count = max(2, int(constraints["minZones"]))
    zone_ids = ["profile_entry" if i == 0 else "profile_transition" if i == zone_count - 1 else f"profile_zone_{i}" for i in range(zone_count)]
    tags = [set() for _ in zone_ids]
    tags[0].add("entry")
    tags[-1].add("escape")
    for index, tag in enumerate(sorted(x for x in canon["requiredZoneTags"] if isinstance(x, str))):
        target = 0 if tag == "entry" else zone_count - 1 if tag == "escape" else index % zone_count
        tags[target].add(tag)
    zones = []
    for index, zid in enumerate(zone_ids):
        conns = []
        if index > 0:
            conns.append(zone_ids[index - 1])
        if index + 1 < zone_count:
            conns.append(zone_ids[index + 1])
        zones.append({"id": zid, "name": f"{entry.get('name', entry.get('id', 'Level'))} {index}", "connections": conns, "tags": sorted(tags[index])})
    sources = sorted(EVIDENCE_SOURCES - ({"SURVIVOR"} if constraints.get("allowSurvivors") is False else set()))
    evidence_count = max(int(constraints["minEvidencePerRequiredFact"]), int(constraints["minEvidenceSourceTypesPerRequiredFact"]))
    evidence = []
    for index in range(evidence_count):
        source = sources[index % len(sources)]
        zid = zone_ids[1 + index % (zone_count - 1)]
        evidence.append({"id": f"profile_evidence_{index + 1}", "supports": ["PROFILE_EXIT_PATTERN_CONFIRMED"], "sources": [source], "zoneId": zid, "discoverConditions": [] if source == "SEARCH" else [f"visit:{zid}:1"]})
    return {
        "schemaVersion": 1,
        "id": entry.get("id"),
        "parentId": entry.get("parentId"),
        "name": entry.get("name"),
        "initialZoneId": zone_ids[0],
        "zones": zones,
        "landmarks": {},
        "environment": {},
        "escapeBlueprint": {"solutionId": f"profile-fallback:{entry.get('id')}", "requiredFacts": ["PROFILE_EXIT_PATTERN_CONFIRMED"], "requiredActions": ["follow_profile_transition"], "locked": True},
        "evidence": evidence,
        "npcKnowledge": {},
        "exploreRoute": zone_ids[1:],
        "actions": [{"id": "follow_profile_transition", "matchGroups": [["continue"], ["transition"]], "conditions": [f"zone:{zone_ids[-1]}", "fact:PROFILE_EXIT_PATTERN_CONFIRMED"], "effects": [{"type": "COMPLETE_LEVEL"}]}],
        "replies": {},
        "canonProfile": canon,
        "generationConstraints": constraints,
        "metadata": {"definitionSource": "procedural-profile"},
    }


def discover_visuals(assets_root: Path, c: Collector) -> set[str]:
    present: set[str] = set()
    base = assets_root / "level_snapshots"
    if not base.is_dir():
        return present
    for path in sorted(base.rglob("*.json")):
        raw = _load_json(path, assets_root, c)
        if not isinstance(raw, dict) or not isinstance(raw.get("areas"), dict):
            continue
        for level, area in raw["areas"].items():
            if not isinstance(level, str) or not isinstance(area, dict):
                continue
            images = area.get("images", [])
            if not isinstance(images, list):
                continue
            for image in images:
                if not isinstance(image, dict):
                    continue
                local = image.get("local_file")
                if isinstance(local, str) and local and (path.parent / local).is_file():
                    present.add(level)
                    break
    return present


def audit_source(source_root: Path) -> list[Issue]:
    c = Collector()
    critical = {
        "AndroidLevelRegistry": source_root / "app/src/main/java/com/rabpit/backroom/core/AndroidLevelRegistry.kt",
        "ProceduralLevelProfileCompiler": source_root / "app/src/main/java/com/rabpit/backroom/core/ProceduralLevelProfile.kt",
        "GameCoreFacade": source_root / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt",
        "GenericLevelRuntime": source_root / "app/src/main/java/com/rabpit/backroom/core/GenericLevelRuntime.kt",
        "MainActivity": source_root / "app/src/main/java/com/rabpit/backroom/MainActivity.java",
        "packagedVerifier": source_root / "ci_verify_packaged_apk.sh",
    }
    patterns = [
        (re.compile(r"when\s*\(\s*levelId\b"), "hardcoded_when_level_id"),
        (re.compile(r"switch\s*\(\s*level\b", re.I), "hardcoded_switch_level"),
        (re.compile(r"range\s*\(\s*7\s*\)"), "hardcoded_range_7"),
        (re.compile(r"\b0\s*\.\.\s*6\b"), "hardcoded_range_0_6"),
        (re.compile(r"(?:Integer\.)?parseInt\s*\(\s*level(?:Id)?\b"), "numeric_level_parse"),
        (re.compile(r"\blevelId\s*\.\s*to(?:Int|Double)(?:OrNull)?\s*\("), "numeric_level_parse"),
    ]
    for label, path in critical.items():
        if not path.is_file():
            c.add(path.as_posix(), "", "source_contract_file_missing", label)
            continue
        text = path.read_text(encoding="utf-8")
        for regex, code in patterns:
            if regex.search(text):
                c.add(path.relative_to(source_root).as_posix(), "", code, label)

    packaged = critical["packagedVerifier"]
    if packaged.is_file():
        text = packaged.read_text(encoding="utf-8")
        forbidden = [
            r"for\s+level\s+in\s+2\s+3\s+4\s+5\s+6",
            r"level_profiles/2\.json", r"level_profiles/3\.json", r"level_profiles/4\.json",
            r"level_profiles/5\.json", r"level_profiles/6\.json",
            r"assets/levels/0\.json", r"assets/levels/1\.json",
        ]
        for pattern in forbidden:
            if re.search(pattern, text):
                c.add(packaged.relative_to(source_root).as_posix(), "", "packaged_level_list_hardcoded", pattern)
        if "validate_level_content.py" not in text:
            c.add(packaged.relative_to(source_root).as_posix(), "", "packaged_validator_not_used")

    android_registry = critical["AndroidLevelRegistry"]
    if android_registry.is_file():
        text = android_registry.read_text(encoding="utf-8")
        for marker in ["collectDefinitionDocuments", "collectProfileDocuments", "AndroidLevelCatalog.load"]:
            if marker not in text:
                c.add(android_registry.relative_to(source_root).as_posix(), "", "registry_discovery_contract_missing", marker)
    return c.sorted()


def validate_content(assets_root: Path, strict: bool = False, source_root: Path | None = None, audit: bool = False) -> dict[str, Any]:
    assets_root = assets_root.resolve()
    c = Collector()
    catalog, catalog_sources = load_catalog(assets_root, c)
    if not catalog:
        c.add("level_catalog", "", "catalog_empty")
    validate_catalog(catalog, catalog_sources, c)
    definitions, definition_sources = load_definitions(assets_root, catalog, c)
    profiles, profile_sources = load_profiles(assets_root, catalog, definitions, c)
    resolved_profiles = resolve_profiles(profiles, profile_sources, definitions, c)

    for level, profile in sorted(resolved_profiles.items()):
        if level not in catalog:
            continue
        validate_definition(compile_profile_fallback(catalog[level], profile), profile_sources.get(level, "level_profiles"), c)

    visuals = discover_visuals(assets_root, c)
    incoming: dict[str, list[str]] = defaultdict(list)
    for source, entry in catalog.items():
        for target in _transitions(entry, catalog_sources.get(source, "level_catalog"), c):
            if target in catalog:
                incoming[target].append(source)

    levels: list[dict[str, Any]] = []
    explicit_count = procedural_count = inherited_count = placeholder_count = 0
    for level in sorted(catalog):
        entry = catalog[level]
        file = catalog_sources.get(level, "level_catalog")
        placeholder = _is_placeholder(entry)
        has_explicit = level in definitions
        has_profile = level in profiles
        if placeholder and (has_explicit or has_profile):
            c.add(file, level, "placeholder_conflicts_with_implementation")
        if has_explicit:
            source = "explicit"
            inheritance_source = None
            explicit_count += 1
            canon_present = isinstance(definitions[level].get("canonProfile"), dict)
            constraints = _constraints(definitions[level].get("generationConstraints"))
        elif level in resolved_profiles:
            source = resolved_profiles[level]["source"]
            inheritance_source = resolved_profiles[level].get("inheritsFrom")
            if source == "inherited-profile":
                inherited_count += 1
            else:
                procedural_count += 1
            canon_present = True
            constraints = resolved_profiles[level]["generationConstraints"]
        elif placeholder:
            source = "placeholder"
            inheritance_source = None
            placeholder_count += 1
            canon_present = False
            constraints = None
            if strict and not _truthy(_metadata(entry).get("allowPlaceholderInStrict")):
                c.add(file, level, "placeholder_forbidden_in_strict")
        else:
            source = "missing"
            inheritance_source = None
            canon_present = False
            constraints = None
            c.add(file, level, "level_content_missing")

        visual_present = level in visuals
        if _truthy(_metadata(entry).get("visualRequired")) and not visual_present:
            c.add(file, level, "required_visual_missing")

        level_errors = [issue for issue in c.issues if issue.levelId == level]
        levels.append({
            "levelId": level,
            "name": str(entry.get("name") or ""),
            "kind": str(entry.get("kind") or ""),
            "parentId": entry.get("parentId") if isinstance(entry.get("parentId"), str) else None,
            "campaignId": str(entry.get("campaignId") or "") or None,
            "campaignOrder": entry.get("campaignOrder") if isinstance(entry.get("campaignOrder"), int) and not isinstance(entry.get("campaignOrder"), bool) else None,
            "definitionSource": source,
            "inheritanceSource": inheritance_source,
            "outgoingTransitions": sorted(set(_transitions(entry, file, c))),
            "incomingTransitions": sorted(set(incoming.get(level, []))),
            "canonProfilePresence": canon_present,
            "generationConstraints": constraints,
            "validationStatus": "error" if level_errors else "valid",
            "visualAssetPresence": visual_present,
        })

    audit_issues: list[Issue] = []
    if audit:
        root = source_root.resolve() if source_root else assets_root.parents[3]
        audit_issues = audit_source(root)
        c.issues.extend(audit_issues)

    errors = [issue.to_json() for issue in c.sorted()]
    edge_count = sum(len(set(_transitions(entry, catalog_sources.get(level, "level_catalog"), c))) for level, entry in catalog.items())
    terminal_count = sum(1 for entry in catalog.values() if not entry.get("outgoingTransitions"))
    summary = {
        "totalCatalogLevels": len(catalog),
        "explicitDefinitions": explicit_count,
        "proceduralProfiles": procedural_count,
        "inheritedProfiles": inherited_count,
        "placeholders": placeholder_count,
        "transitionEdges": edge_count,
        "terminalLevels": terminal_count,
        "validationErrors": len(errors),
    }
    report = {"summary": summary, "levels": levels, "errors": errors}
    if audit:
        report["sourceAudit"] = {"errors": len(audit_issues)}

    encoded = json.dumps(report, ensure_ascii=False, sort_keys=True)
    for hidden in HIDDEN_REPORT_TERMS:
        if f'"{hidden}"' in encoded:
            raise AssertionError(f"hidden field leaked into content report: {hidden}")
    return report


def print_text(report: dict[str, Any], include_levels: bool) -> None:
    summary = report["summary"]
    print(f"Total catalog Levels: {summary['totalCatalogLevels']}")
    print(f"Explicit definitions: {summary['explicitDefinitions']}")
    print(f"Procedural profiles: {summary['proceduralProfiles']}")
    print(f"Inherited profiles: {summary['inheritedProfiles']}")
    print(f"Placeholders: {summary['placeholders']}")
    print(f"Transition edges: {summary['transitionEdges']}")
    print(f"Terminal Levels: {summary['terminalLevels']}")
    print(f"Validation errors: {summary['validationErrors']}")
    if include_levels:
        for level in report["levels"]:
            parent = level["parentId"] or "-"
            inherited = level["inheritanceSource"] or "-"
            print(f"LEVEL {level['levelId']} | {level['kind']} | {level['definitionSource']} | parent={parent} | inherited={inherited} | status={level['validationStatus']} | visual={'yes' if level['visualAssetPresence'] else 'no'}")
    for issue in report["errors"]:
        print(f"ERROR {issue.get('file', '')}", file=sys.stderr)
        if issue.get("levelId"):
            print(f"level={issue['levelId']}", file=sys.stderr)
        print(f"code={issue['code']}", file=sys.stderr)
        if issue.get("target"):
            print(f"target={issue['target']}", file=sys.stderr)
        if issue.get("detail"):
            print(f"detail={issue['detail']}", file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate data-driven Level content")
    parser.add_argument("--assets-root", type=Path, default=Path(__file__).resolve().parent / "app/src/main/assets", help="Assets root containing level_catalog/, level_profiles/ and levels/")
    parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--report", action="store_true", help="Print deterministic per-Level inventory")
    parser.add_argument("--json", action="store_true", help="Print deterministic JSON report")
    parser.add_argument("--strict", action="store_true", help="Reject placeholders unless explicitly allowed")
    parser.add_argument("--audit-source", action="store_true", help="Run no-source-change/hard-code regression audit")
    args = parser.parse_args(argv)

    report = validate_content(args.assets_root, strict=args.strict, source_root=args.source_root, audit=args.audit_source)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        print_text(report, include_levels=args.report)
    return 1 if report["summary"]["validationErrors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
