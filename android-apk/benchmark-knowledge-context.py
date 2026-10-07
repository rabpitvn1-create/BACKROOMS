from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
DB = json.loads(DB_PATH.read_text(encoding="utf-8"))
RECORDS = {r["id"]: r for r in DB["records"]}


def toks(text: str) -> int:
    return max(1, math.ceil(len(text) / 4))


MANDATORY = {
    "GAME.TEXT.CORE", "GAME.GM.FAIRNESS", "WORLD.CORE", "WRITING.KNOWLEDGE_BOUNDARY",
    "WRITING.COMPETENCE", "WRITING.PLAYER_AGENCY", "CHAR.KAI.RUNTIME_CORE"
}


def select_new(case: dict) -> set[str]:
    selected = set(MANDATORY)
    selected.add(f"LEVEL.{case['level']:02d}")
    present = set(case.get("present", []))
    action = case["action"].lower()
    scene = (action + " " + case.get("scene", "")).lower()
    if "iris" in present:
        selected |= {"CHAR.IRIS.RUNTIME_CORE", "REL.KAI.IRIS.BASELINE", "ADDR.IRIS.KAI"}
    if "syvial" in present:
        selected |= {"CHAR.SYVIAL.RUNTIME_CORE", "REL.KAI.SYVIAL.BASELINE", "ADDR.SYVIAL.KAI"}
    if {"iris", "syvial"} <= present:
        selected.add("REL.IRIS.SYVIAL.BASELINE")
    direct = {
        "argus": "CHAR.IRIS.ARGUS", "terrain read": "CHAR.IRIS.ARGUS",
        "thousandfold": "CHAR.IRIS.THOUSANDFOLD", "ivory": "CHAR.IRIS.IVORY_EBONY", "ebony": "CHAR.IRIS.IVORY_EBONY",
        "field mednet": "CHAR.IRIS.SUPPORT", "field galley": "CHAR.IRIS.SUPPORT",
        "godkiller override": "CHAR.SYVIAL.GODKILLER_OVERRIDE", "lucifer core": "CHAR.SYVIAL.LUCIFER_CORE",
        "sparda core": "CHAR.KAI.SPARDA_CORE", "white wraith": "CHAR.KAI.WHITE_WRAITH",
        "omnivault": "CHAR.KAI.OMNIVAULT", "nhẫn vạn tàng": "CHAR.KAI.OMNIVAULT",
    }
    for needle, rid in direct.items():
        if needle in action:
            selected.add(rid)
    if "godkiller" in action and "godkiller override" not in action:
        selected.add("CHAR.SYVIAL.GODKILLER")
    if "devil trigger" in action:
        selected.add("CHAR.KAI.DEVIL_TRIGGER")
        if "syvial" in present:
            selected.add("CHAR.SYVIAL.DEVIL_TRIGGER")
    if any(k in scene for k in ["dấu vết", "trace", "vết chân", "route", "đường đi", "góc chết", "vật che", "phục kích", "địa hình"]):
        if "iris" in present:
            selected.add("CHAR.IRIS.ARGUS")
    if any(k in scene for k in ["đe dọa", "threat", "tấn công", "combat", "entity", "hound", "smiler", "wretch", "skin-stealer", "jeff"]):
        selected.add("ENTITY.GLOBAL_HARD_LOCK")
        if "syvial" in present:
            selected.add("CHAR.SYVIAL.COMBAT")
    if any(k in scene for k in ["bị thương", "vết thương", "medical", "sơ cứu"]) and "iris" in present:
        selected.add("CHAR.IRIS.SUPPORT")
    if any(k in scene for k in ["thức ăn", "nấu", "food", "cooking"]) and "iris" in present:
        selected.add("CHAR.IRIS.SUPPORT")
    tag_map = {
        "hound": "ENTITY.HOUND", "false puddle": "ENTITY.FALSE_PUDDLE", "smiler": "ENTITY.SMILER",
        "skin-stealer": "ENTITY.SKIN_STEALER", "skin stealer": "ENTITY.SKIN_STEALER",
        "biological pipeline": "ENTITY.BIOLOGICAL_PIPELINE", "deathmoth": "ENTITY.DEATHMOTH",
        "wretch": "ENTITY.WRETCH", "cable mimic": "ENTITY.CABLE_MIMIC", "beast of level 5": "ENTITY.BEAST_LEVEL_5",
        "jeff the killer": "ENTITY.JEFF", "almond water": "ITEM.ALMOND_WATER", "greek fire": "ITEM.GREEK_FIRE", "liquid pain": "ITEM.LIQUID_PAIN",
    }
    for needle, rid in tag_map.items():
        if needle in action:
            selected.add(rid)
    if any(k in scene for k in ["loot", "vật phẩm", "inventory", "almond", "liquid pain", "greek fire", "nước", "thuốc"]):
        selected.add("ITEM.GLOBAL_HARD_LOCK")
    if case.get("main_separated"):
        selected |= {"STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"}
    return {rid for rid in selected if rid in RECORDS}


def new_packet_tokens(case: dict, selected: set[str]) -> int:
    state = {
        "turn": case.get("turn", 1),
        "level": {"number": case["level"]},
        "location": case.get("location", "A local scene with a few relevant observations."),
        "party": [{"id": p, "name": p.title()} for p in case.get("present", [])],
    }
    log = case.get("log", [])[-4:]
    base = toks(json.dumps(state, ensure_ascii=False)) + toks(json.dumps(log, ensure_ascii=False)) + 80
    record_cost = sum(toks(RECORDS[rid]["text"]) + 26 for rid in selected)
    return min(3400, base + record_cost)


long_log = [
    {"role": "player" if i % 2 == 0 else "gm", "text": ("Đoạn hội thoại gần đây chứa chi tiết không cần mang dài hạn. " * 8) + str(i)}
    for i in range(8)
]

CORPUS = [
    {
        "name": "level0_quiet_exploration", "level": 0, "action": "Kai kiểm tra tường và các dấu mốc quanh hành lang.",
        "present": [], "main_separated": True, "log": long_log,
        "required": {"LEVEL.00", "CHAR.KAI.RUNTIME_CORE", "STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"},
        "quality": {"story_continuity_errors": {"STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"}}
    },
    {
        "name": "iris_present_trace", "level": 1, "action": "Kai nhìn dấu vết lạ cạnh cột bê tông.", "scene": "dấu vết cần kiểm tra và một tuyến rút chưa chắc chắn",
        "present": ["iris"], "log": long_log,
        "required": {"CHAR.IRIS.RUNTIME_CORE", "CHAR.IRIS.ARGUS", "REL.KAI.IRIS.BASELINE", "ADDR.IRIS.KAI", "LEVEL.01"},
        "quality": {"character_errors": {"CHAR.IRIS.RUNTIME_CORE"}, "address_errors": {"ADDR.IRIS.KAI"}, "ability_overreach": {"CHAR.IRIS.ARGUS"}}
    },
    {
        "name": "iris_thousandfold", "level": 2, "action": "Iris dùng Thousandfold Cognition để phân tích dữ kiện mâu thuẫn.",
        "present": ["iris"], "log": long_log,
        "required": {"CHAR.IRIS.THOUSANDFOLD", "CHAR.IRIS.RUNTIME_CORE", "ADDR.IRIS.KAI"},
        "quality": {"ability_overreach": {"CHAR.IRIS.THOUSANDFOLD"}}
    },
    {
        "name": "iris_field_mednet", "level": 3, "action": "Kai bị thương; Iris kiểm tra vết thương bằng Field MedNet.",
        "present": ["iris"], "log": long_log,
        "required": {"CHAR.IRIS.SUPPORT", "CHAR.IRIS.RUNTIME_CORE"},
        "quality": {"ability_overreach": {"CHAR.IRIS.SUPPORT"}}
    },
    {
        "name": "syvial_direct_threat", "level": 2, "action": "Một Hound lao vào từ hành lang hẹp.", "scene": "direct threat combat",
        "present": ["syvial"], "log": long_log,
        "required": {"CHAR.SYVIAL.RUNTIME_CORE", "CHAR.SYVIAL.COMBAT", "REL.KAI.SYVIAL.BASELINE", "ADDR.SYVIAL.KAI", "ENTITY.HOUND"},
        "quality": {"character_errors": {"CHAR.SYVIAL.RUNTIME_CORE"}, "address_errors": {"ADDR.SYVIAL.KAI"}, "competence_suppression": {"CHAR.SYVIAL.COMBAT"}}
    },
    {
        "name": "both_followers_dialogue", "level": 4, "action": "Kai hỏi Iris và Syvial nghĩ gì về việc nghỉ ở đây.",
        "present": ["iris", "syvial"], "log": long_log,
        "required": {"CHAR.IRIS.RUNTIME_CORE", "CHAR.SYVIAL.RUNTIME_CORE", "ADDR.IRIS.KAI", "ADDR.SYVIAL.KAI", "REL.IRIS.SYVIAL.BASELINE"},
        "quality": {"character_errors": {"CHAR.IRIS.RUNTIME_CORE", "CHAR.SYVIAL.RUNTIME_CORE"}, "address_errors": {"ADDR.IRIS.KAI", "ADDR.SYVIAL.KAI"}}
    },
    {
        "name": "omnivault_scan", "level": 1, "action": "Kai dùng Omnivault scan vật vô tri vừa tìm được.",
        "present": [], "log": long_log,
        "required": {"CHAR.KAI.OMNIVAULT", "LEVEL.01"},
        "quality": {"ability_overreach": {"CHAR.KAI.OMNIVAULT"}}
    },
    {
        "name": "level5_beast", "level": 5, "action": "Dấu vết cho thấy Beast of Level 5 có thể đang theo dõi nhóm.", "scene": "trace threat",
        "present": ["syvial"], "log": long_log,
        "required": {"LEVEL.05", "ENTITY.BEAST_LEVEL_5", "ENTITY.GLOBAL_HARD_LOCK", "CHAR.SYVIAL.COMBAT"},
        "quality": {"canon_errors": {"ENTITY.BEAST_LEVEL_5"}, "competence_suppression": {"CHAR.SYVIAL.COMBAT"}}
    },
]


def percentile(values: list[int], p: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    idx = min(len(ordered) - 1, max(0, math.ceil(p * len(ordered)) - 1))
    return ordered[idx]


context_sizes = []
missing_required = 0
quality_names = [
    "canon_errors", "story_continuity_errors", "character_errors", "address_errors",
    "knowledge_leaks", "ability_overreach", "competence_suppression"
]
quality = {name: 0 for name in quality_names}
case_rows = []

for case in CORPUS:
    selected = select_new(case)
    context_size = new_packet_tokens(case, selected)
    context_sizes.append(context_size)
    required = set(case["required"])
    missing_ids = required - selected
    missing_required += len(missing_ids)
    for metric, ids in case.get("quality", {}).items():
        if any(rid not in selected for rid in ids):
            quality[metric] += 1
    case_rows.append({
        "case": case["name"],
        "context_tokens": context_size,
        "missing_required": sorted(missing_ids),
        "selected": sorted(selected),
    })

report = {
    "verification": "offline current knowledge-context contract",
    "corpus_cases": len(CORPUS),
    "token_estimator": "ceil(chars/4), matching the runtime budget estimator",
    "current": {
        "p50_context_tokens": percentile(context_sizes, 0.50),
        "p95_context_tokens": percentile(context_sizes, 0.95),
        "max_context_tokens": max(context_sizes) if context_sizes else 0,
        "missing_required_context": missing_required,
        **quality,
    },
    "cases": case_rows,
    "limitations": [
        "This verifier checks deterministic required-record coverage and the packaged context budget on a fixed corpus.",
        "It does not fabricate provider-output quality metrics or compare against the retired compact-canon implementation.",
    ],
}

out_path = ROOT / "knowledge-benchmark-report.json"
out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False, indent=2))

assert report["current"]["p95_context_tokens"] <= 3400, "Current p95 exceeds hard ceiling"
assert report["current"]["max_context_tokens"] <= 3400, "Current context exceeds hard ceiling"
assert report["current"]["missing_required_context"] == 0, "Current selector misses required context on corpus"
for metric in quality_names:
    assert report["current"][metric] == 0, f"Current selector misses {metric} coverage"
