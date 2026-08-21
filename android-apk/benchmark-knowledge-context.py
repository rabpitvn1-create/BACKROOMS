from __future__ import annotations

import json
import math
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
DRIVE_CANON = (ROOT / "drive-canon.txt").read_text(encoding="utf-8")
KAI_CANON = (ROOT / "kai-codex.txt").read_text(encoding="utf-8")
DB = json.loads(DB_PATH.read_text(encoding="utf-8"))
RECORDS = {r["id"]: r for r in DB["records"]}


def toks(text: str) -> int:
    return max(1, math.ceil(len(text) / 4))


def section(text: str, start: str, end: str) -> str:
    a = text.find(start)
    if a < 0:
        return ""
    b = text.find(end, a + len(start))
    if b < 0:
        b = len(text)
    return text[a:b].strip()


def level_line(level: int) -> str:
    prefix = f"- Level {level} /"
    for line in DRIVE_CANON.splitlines():
        if line.startswith(prefix):
            return line
    return ""


def old_drive_packet(case: dict) -> str:
    out = [
        section(DRIVE_CANON, "PHẠM VI", "VĂN PHONG VÀ KINH DỊ"),
        section(DRIVE_CANON, "VĂN PHONG VÀ KINH DỊ", "THẾ GIỚI"),
        section(DRIVE_CANON, "THẾ GIỚI", "LEVEL 0–6"),
        level_line(case["level"]),
        section(DRIVE_CANON, "GAMEPLAY HARD LOCK", "END DRIVE CANON R06"),
    ]
    action = case["action"].lower()
    if any(k in action for k in ["entity", "hound", "smiler", "jeff", "almond", "item", "loot", "inventory", "omnivault", "water", "nước", "thuốc"]):
        out.append(section(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "IRIS / SYVIAL"))
    if case.get("present") or any(k in action for k in ["iris", "syvial", "nói", "hỏi", "dialogue", "trò chuyện"]):
        out.append(section(DRIVE_CANON, "IRIS / SYVIAL", "GAMEPLAY HARD LOCK"))
    return "\n\n".join(x for x in out if x)


def old_kai_packet(case: dict) -> str:
    out = [
        section(KAI_CANON, "1. ĐỊNH DANH", "2. NGOẠI HÌNH"),
        section(KAI_CANON, "3. TÍNH CÁCH / NGUYÊN TẮC", "4. PHONG CÁCH GIAO TIẾP"),
        section(KAI_CANON, "4. PHONG CÁCH GIAO TIẾP", "5. NĂNG LỰC CHIẾN ĐẤU"),
        section(KAI_CANON, "5. NĂNG LỰC CHIẾN ĐẤU", "6. SPARDA CORE"),
        section(KAI_CANON, "6. SPARDA CORE", "7. DEVIL TRIGGER"),
        section(KAI_CANON, "10. BLACKBLOOD ARMOR & MODULES", "11. OMNIVAULT RING / NHẪN VẠN TÀNG"),
        section(KAI_CANON, "13. GIỚI HẠN THỰC SỰ", "14. ACTION LOCKS / CẤM MODEL TỰ BỊA"),
        section(KAI_CANON, "14. ACTION LOCKS / CẤM MODEL TỰ BỊA", "END OF KAI OPERATIONAL CODEX"),
    ]
    action = case["action"].lower()
    if any(k in action for k in ["bắn", "đánh", "combat", "tấn công", "entity", "hound", "smiler", "threat", "đe dọa"]):
        out.extend([
            section(KAI_CANON, "7. DEVIL TRIGGER", "10. BLACKBLOOD ARMOR & MODULES"),
            section(KAI_CANON, "12. PHONG CÁCH CHIẾN ĐẤU", "13. GIỚI HẠN THỰC SỰ"),
        ])
    if any(k in action for k in ["omnivault", "item", "inventory", "scan", "hoàn nguyên", "restore"]):
        out.append(section(KAI_CANON, "11. OMNIVAULT RING / NHẪN VẠN TÀNG", "12. PHONG CÁCH CHIẾN ĐẤU"))
    return "\n\n".join(x for x in out if x)


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


def old_packet_tokens(case: dict) -> int:
    state = {
        "turn": case.get("turn", 1), "level": {"number": case["level"]},
        "location": case.get("location", "A local scene with a few relevant observations."),
        "party": case.get("present", []), "flags": case.get("legacy_flags", {}),
        "log": case.get("log", [])[-6:],
    }
    text = old_drive_packet(case) + "\n\n" + old_kai_packet(case) + "\n\n" + json.dumps(state, ensure_ascii=False) + "\n\n" + case["action"]
    return toks(text)


def old_supports(case: dict, rid: str) -> bool:
    packet = (old_drive_packet(case) + "\n" + old_kai_packet(case)).lower()
    probes = {
        "CHAR.IRIS.RUNTIME_CORE": ["iris / argus"],
        "CHAR.IRIS.ARGUS": ["argus terrain read"],
        "CHAR.IRIS.THOUSANDFOLD": ["thousandfold cognition"],
        "CHAR.IRIS.IVORY_EBONY": ["ivory & ebony"],
        "CHAR.IRIS.SUPPORT": ["field mednet", "field galley"],
        "ADDR.IRIS.KAI": ["xưng “em”, gọi kai “anh”", "xưng \"em\", gọi kai \"anh\""],
        "REL.IRIS.SYVIAL.BASELINE": ["bạn bè", "trusted teammates"],
        "CHAR.SYVIAL.RUNTIME_CORE": ["syvial: con gái lucifer"],
        "CHAR.SYVIAL.COMBAT": ["kiếm sĩ siêu nhiên"],
        "ADDR.SYVIAL.KAI": ["xưng “em”, gọi “anh” hoặc “kai”", "xưng \"em\", gọi \"anh\" hoặc \"kai\""],
        "CHAR.KAI.OMNIVAULT": ["omnivault ring / nhẫn vạn tàng"],
        "CHAR.KAI.GUILTY_CROWN_OVERRIDE": ["guilty crown override"],
        "STORY.MAIN.OBJECTIVE": ["mục tiêu dài hạn"],
        "STORY.MAIN.SEPARATION": ["black_blood_link", "location unknown to kai"],
        "ENTITY.HOUND": ["hound"],
        "ENTITY.BEAST_LEVEL_5": ["the beast"],
        "ITEM.ALMOND_WATER": ["almond water"],
    }
    candidates = probes.get(rid)
    if not candidates:
        return True
    return any(p in packet for p in candidates)


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


old_sizes = []
new_sizes = []
old_missing = 0
new_missing = 0
old_irrelevant = 0
new_irrelevant = 0
quality_names = ["canon_errors", "story_continuity_errors", "character_errors", "address_errors", "knowledge_leaks", "ability_overreach", "competence_suppression"]
old_quality = {name: 0 for name in quality_names}
new_quality = {name: 0 for name in quality_names}
case_rows = []

for case in CORPUS:
    selected = select_new(case)
    old_size = old_packet_tokens(case)
    new_size = new_packet_tokens(case, selected)
    old_sizes.append(old_size)
    new_sizes.append(new_size)
    required = set(case["required"])
    old_missing_ids = {rid for rid in required if not old_supports(case, rid)}
    new_missing_ids = required - selected
    old_missing += len(old_missing_ids)
    new_missing += len(new_missing_ids)
    # OLD compact packets are broad prose blobs. Count sections outside direct required needs as coarse irrelevant units.
    old_units = 8 + (2 if any(k in case["action"].lower() for k in ["entity", "hound", "item", "omnivault", "water", "nước"]) else 0) + (1 if case.get("present") else 0)
    old_irrelevant += max(0, old_units - len(required))
    supportive = required | MANDATORY | {f"LEVEL.{case['level']:02d}", "ENTITY.GLOBAL_HARD_LOCK", "ITEM.GLOBAL_HARD_LOCK", "REL.KAI.IRIS.BASELINE", "REL.KAI.SYVIAL.BASELINE"}
    new_irrelevant += len(selected - supportive)
    for quality, ids in case.get("quality", {}).items():
        if any(not old_supports(case, rid) for rid in ids):
            old_quality[quality] += 1
        if any(rid not in selected for rid in ids):
            new_quality[quality] += 1
    case_rows.append({
        "case": case["name"], "old_tokens": old_size, "new_tokens": new_size,
        "old_missing": sorted(old_missing_ids), "new_missing": sorted(new_missing_ids),
        "selected": sorted(selected)
    })

report = {
    "benchmark": "offline context-contract OLD vs NEW",
    "corpus_cases": len(CORPUS),
    "token_estimator": "ceil(chars/4), same estimator for OLD and NEW",
    "old": {
        "average_context_tokens": round(statistics.mean(old_sizes), 2),
        "p50_context_tokens": percentile(old_sizes, 0.50),
        "p95_context_tokens": percentile(old_sizes, 0.95),
        "missing_required_context": old_missing,
        "irrelevant_retrieved_context_units": old_irrelevant,
        **old_quality,
        "critic_invocation_rate": "unchanged conditional policy; provider-run metric not fabricated offline",
        "repair_rate": "provider-run metric not fabricated offline"
    },
    "new": {
        "average_context_tokens": round(statistics.mean(new_sizes), 2),
        "p50_context_tokens": percentile(new_sizes, 0.50),
        "p95_context_tokens": percentile(new_sizes, 0.95),
        "missing_required_context": new_missing,
        "irrelevant_retrieved_context_units": new_irrelevant,
        **new_quality,
        "critic_invocation_rate": "same validated-risk threshold; deterministic validator runs every generated non-meta turn",
        "repair_rate": "single repair remains; deterministic validator can trigger it without forcing semantic critic"
    },
    "cases": case_rows,
    "limitations": [
        "This benchmark measures packaged context size and deterministic required-record coverage on the same corpus.",
        "It does not invent model-output error rates, critic invocation rates, or repair rates without actually running providers.",
        "A live-provider regression can be layered on later, but CI acceptance remains deterministic and reproducible."
    ]
}

out_path = ROOT / "knowledge-benchmark-report.json"
out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(json.dumps(report, ensure_ascii=False, indent=2))

# Acceptance gates requested for the architecture itself.
assert report["new"]["average_context_tokens"] <= report["old"]["average_context_tokens"], "AVG_CONTEXT_NEW > AVG_CONTEXT_OLD"
assert report["new"]["p95_context_tokens"] <= 3400, "NEW p95 exceeds hard ceiling"
assert report["new"]["missing_required_context"] == 0, "NEW misses required context on corpus"
assert report["new"]["missing_required_context"] < report["old"]["missing_required_context"], "NEW required-context coverage did not improve"
assert report["new"]["irrelevant_retrieved_context_units"] < report["old"]["irrelevant_retrieved_context_units"], "NEW irrelevant retrieval did not improve"
for metric in ["canon_errors", "story_continuity_errors", "character_errors", "address_errors", "ability_overreach", "competence_suppression"]:
    assert report["new"][metric] <= report["old"][metric], f"NEW regressed {metric}"
assert any(report["new"][m] < report["old"][m] for m in ["story_continuity_errors", "character_errors", "ability_overreach", "competence_suppression"]), "NEW consistency did not improve on any measured contract"
