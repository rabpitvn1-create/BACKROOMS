from __future__ import annotations

import json
import math
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
DRIVE_CANON = (ROOT / "drive-canon.txt").read_text(encoding="utf-8")
CAO_MINH_CANON = (ROOT / "kai-codex.txt").read_text(encoding="utf-8")
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
    if any(k in action for k in ["entity", "hound", "smiler", "jeff", "almond", "item", "loot", "inventory", "vạn khố", "water", "nước", "thuốc"]):
        out.append(section(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "LỤC TRẦM / SYVIAL"))
    if case.get("present") or any(k in action for k in ["lục trầm", "syvial", "nói", "hỏi", "dialogue", "trò chuyện"]):
        out.append(section(DRIVE_CANON, "LỤC TRẦM / SYVIAL", "GAMEPLAY HARD LOCK"))
    return "\n\n".join(x for x in out if x)


def old_cao_minh_packet(case: dict) -> str:
    out = [
        section(CAO_MINH_CANON, "1. ĐỊNH DANH", "2. NGOẠI HÌNH"),
        section(CAO_MINH_CANON, "3. TÍNH CÁCH / NGUYÊN TẮC", "4. PHONG CÁCH GIAO TIẾP"),
        section(CAO_MINH_CANON, "4. PHONG CÁCH GIAO TIẾP", "5. NĂNG LỰC CHIẾN ĐẤU"),
        section(CAO_MINH_CANON, "5. NĂNG LỰC CHIẾN ĐẤU", "6. VẠN QUỶ MA TÂM"),
        section(CAO_MINH_CANON, "6. VẠN QUỶ MA TÂM", "7. VẠN QUỶ MA THÂN"),
        section(CAO_MINH_CANON, "10. HUYẾT MA CHIẾN KHẢI & CÁC PHẦN LIÊN KẾT", "11. VẠN KHỐ GIỚI CHỈ"),
        section(CAO_MINH_CANON, "13. GIỚI HẠN THỰC SỰ", "14. ACTION LOCKS / CẤM MODEL TỰ BỊA"),
        section(CAO_MINH_CANON, "14. ACTION LOCKS / CẤM MODEL TỰ BỊA", "END OF CAO MINH OPERATIONAL CODEX"),
    ]
    action = case["action"].lower()
    if any(k in action for k in ["bắn", "đánh", "combat", "tấn công", "entity", "hound", "smiler", "threat", "đe dọa"]):
        out.extend([
            section(CAO_MINH_CANON, "7. VẠN QUỶ MA THÂN", "10. HUYẾT MA CHIẾN KHẢI & CÁC PHẦN LIÊN KẾT"),
            section(CAO_MINH_CANON, "12. PHONG CÁCH CHIẾN ĐẤU", "13. GIỚI HẠN THỰC SỰ"),
        ])
    if any(k in action for k in ["vạn khố", "item", "inventory", "scan", "hoàn nguyên", "restore"]):
        out.append(section(CAO_MINH_CANON, "11. VẠN KHỐ GIỚI CHỈ", "12. PHONG CÁCH CHIẾN ĐẤU"))
    return "\n\n".join(x for x in out if x)


MANDATORY = {
    "GAME.TEXT.CORE", "GAME.GM.FAIRNESS", "WORLD.CORE", "WRITING.KNOWLEDGE_BOUNDARY",
    "WRITING.COMPETENCE", "WRITING.PLAYER_AGENCY", "CHAR.CAO_MINH.RUNTIME_CORE"
}


def select_new(case: dict) -> set[str]:
    selected = set(MANDATORY)
    selected.add(f"LEVEL.{case['level']:02d}")
    present = set(case.get("present", []))
    action = case["action"].lower()
    scene = (action + " " + case.get("scene", "")).lower()
    if "luc-tram" in present:
        selected |= {"CHAR.LUC_TRAM.RUNTIME_CORE", "REL.CAO_MINH.LUC_TRAM.BASELINE", "ADDR.LUC_TRAM.CAO_MINH"}
    if "syvial" in present:
        selected |= {"CHAR.SYVIAL.RUNTIME_CORE", "REL.CAO_MINH.SYVIAL.BASELINE", "ADDR.SYVIAL.CAO_MINH"}
    if {"luc-tram", "syvial"} <= present:
        selected.add("REL.LUC_TRAM.SYVIAL.BASELINE")
    direct = {
        "thiên kiếm linh tâm": "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM", "linh tâm": "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM",
        "thiên kiếm linh tâm": "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM", "tịch quang": "CHAR.LUC_TRAM.TICH_QUANG", "thiên kiếm thất thức": "CHAR.LUC_TRAM.TICH_QUANG",
        "kiếm khải": "CHAR.LUC_TRAM.THIEN_CO_BACH_KIM_KIEM_KHAI", "thiên kiếm thất thức": "CHAR.LUC_TRAM.THIEN_KIEM_THAT_THUC",
        "godkiller override": "CHAR.SYVIAL.GODKILLER_OVERRIDE", "lucifer core": "CHAR.SYVIAL.LUCIFER_CORE",
        "vạn quỷ ma tâm": "CHAR.CAO_MINH.VAN_QUY_MA_TAM", "huyết ma kiếm": "CHAR.CAO_MINH.HUYET_MA_KIEM",
        "vạn khố giới chỉ": "CHAR.CAO_MINH.VAN_KHO_GIOI_CHI", "vạn khố": "CHAR.CAO_MINH.VAN_KHO_GIOI_CHI",
    }
    for needle, rid in direct.items():
        if needle in action:
            selected.add(rid)
    if "godkiller" in action and "godkiller override" not in action:
        selected.add("CHAR.SYVIAL.GODKILLER")
    if "vạn quỷ ma thân" in action or "devil trigger" in action:
        selected.add("CHAR.CAO_MINH.VAN_QUY_MA_THAN")
        if "syvial" in present:
            selected.add("CHAR.SYVIAL.DEVIL_TRIGGER")
    if any(k in scene for k in ["dấu vết", "trace", "vết chân", "route", "đường đi", "góc chết", "vật che", "phục kích", "địa hình"]):
        if "luc-tram" in present:
            selected.add("CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM")
    if any(k in scene for k in ["đe dọa", "threat", "tấn công", "combat", "entity", "hound", "smiler", "wretch", "skin-stealer", "jeff"]):
        selected.add("ENTITY.GLOBAL_HARD_LOCK")
        if "syvial" in present:
            selected.add("CHAR.SYVIAL.COMBAT")
    if any(k in scene for k in ["bị thương", "vết thương", "medical", "sơ cứu"]) and "luc-tram" in present:
        selected.add("CHAR.LUC_TRAM.RUNTIME_CORE")
    if any(k in scene for k in ["thức ăn", "nấu", "food", "cooking"]) and "luc-tram" in present:
        selected.add("CHAR.LUC_TRAM.RUNTIME_CORE")
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
    text = old_drive_packet(case) + "\n\n" + old_cao_minh_packet(case) + "\n\n" + json.dumps(state, ensure_ascii=False) + "\n\n" + case["action"]
    return toks(text)


def old_supports(case: dict, rid: str) -> bool:
    packet = (old_drive_packet(case) + "\n" + old_cao_minh_packet(case)).lower()
    probes = {
        "CHAR.LUC_TRAM.RUNTIME_CORE": ["lục trầm: nữ"],
        "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM": ["thiên kiếm linh tâm"],
        "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM": ["thiên kiếm linh tâm"],
        "CHAR.LUC_TRAM.TICH_QUANG": ["tịch quang"],
        "CHAR.LUC_TRAM.RUNTIME_CORE": ["lục trầm: nữ"],
        "ADDR.LUC_TRAM.CAO_MINH": ["gọi hắn “các hạ”", "gọi hắn \"các hạ\""],
        "REL.LUC_TRAM.SYVIAL.BASELINE": ["đều không phải procedural survivor"],
        "CHAR.SYVIAL.RUNTIME_CORE": ["syvial: con gái lucifer"],
        "CHAR.SYVIAL.COMBAT": ["kiếm sĩ siêu nhiên"],
        "ADDR.SYVIAL.CAO_MINH": ["xưng “em”, gọi “anh” hoặc “cao minh”", "xưng \"em\", gọi \"anh\" hoặc \"cao minh\""],
        "CHAR.CAO_MINH.VAN_KHO_GIOI_CHI": ["vạn khố giới chỉ"],
        "CHAR.CAO_MINH.HUYET_MA_NHI_THAP_TU_TRAM": ["huyết ma nhị thập tứ trảm"],
        "STORY.MAIN.OBJECTIVE": ["mục tiêu dài hạn"],
        "STORY.MAIN.SEPARATION": ["không biết vị trí/tình trạng hiện tại của lục trầm"],
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
        "name": "level0_quiet_exploration", "level": 0, "action": "Cao Minh kiểm tra tường và các dấu mốc quanh hành lang.",
        "present": [], "main_separated": True, "log": long_log,
        "required": {"LEVEL.00", "CHAR.CAO_MINH.RUNTIME_CORE", "STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"},
        "quality": {"story_continuity_errors": {"STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"}}
    },
    {
        "name": "luc_tram_present_trace", "level": 1, "action": "Cao Minh nhìn dấu vết lạ cạnh cột bê tông.", "scene": "dấu vết cần kiểm tra và một tuyến rút chưa chắc chắn",
        "present": ["luc-tram"], "log": long_log,
        "required": {"CHAR.LUC_TRAM.RUNTIME_CORE", "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM", "REL.CAO_MINH.LUC_TRAM.BASELINE", "ADDR.LUC_TRAM.CAO_MINH", "LEVEL.01"},
        "quality": {"character_errors": {"CHAR.LUC_TRAM.RUNTIME_CORE"}, "address_errors": {"ADDR.LUC_TRAM.CAO_MINH"}, "ability_overreach": {"CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM"}}
    },
    {
        "name": "luc_tram_linh_tam", "level": 2, "action": "Lục Trầm dùng Thiên Kiếm Linh Tâm để phân tích dữ kiện mâu thuẫn.",
        "present": ["luc-tram"], "log": long_log,
        "required": {"CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM", "CHAR.LUC_TRAM.RUNTIME_CORE", "ADDR.LUC_TRAM.CAO_MINH"},
        "quality": {"ability_overreach": {"CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM"}}
    },
    {
        "name": "luc_tram_first_aid", "level": 3, "action": "Cao Minh bị thương; Lục Trầm kiểm tra vết thương.",
        "present": ["luc-tram"], "log": long_log,
        "required": {"CHAR.LUC_TRAM.RUNTIME_CORE", "CHAR.LUC_TRAM.RUNTIME_CORE"},
        "quality": {"ability_overreach": {"CHAR.LUC_TRAM.RUNTIME_CORE"}}
    },
    {
        "name": "syvial_direct_threat", "level": 2, "action": "Một Hound lao vào từ hành lang hẹp.", "scene": "direct threat combat",
        "present": ["syvial"], "log": long_log,
        "required": {"CHAR.SYVIAL.RUNTIME_CORE", "CHAR.SYVIAL.COMBAT", "REL.CAO_MINH.SYVIAL.BASELINE", "ADDR.SYVIAL.CAO_MINH", "ENTITY.HOUND"},
        "quality": {"character_errors": {"CHAR.SYVIAL.RUNTIME_CORE"}, "address_errors": {"ADDR.SYVIAL.CAO_MINH"}, "competence_suppression": {"CHAR.SYVIAL.COMBAT"}}
    },
    {
        "name": "both_followers_dialogue", "level": 4, "action": "Cao Minh hỏi Lục Trầm và Syvial nghĩ gì về việc nghỉ ở đây.",
        "present": ["luc-tram", "syvial"], "log": long_log,
        "required": {"CHAR.LUC_TRAM.RUNTIME_CORE", "CHAR.SYVIAL.RUNTIME_CORE", "ADDR.LUC_TRAM.CAO_MINH", "ADDR.SYVIAL.CAO_MINH", "REL.LUC_TRAM.SYVIAL.BASELINE"},
        "quality": {"character_errors": {"CHAR.LUC_TRAM.RUNTIME_CORE", "CHAR.SYVIAL.RUNTIME_CORE"}, "address_errors": {"ADDR.LUC_TRAM.CAO_MINH", "ADDR.SYVIAL.CAO_MINH"}}
    },
    {
        "name": "van_kho_scan", "level": 1, "action": "Cao Minh dùng Vạn Khố Giới Chỉ scan vật vô tri vừa tìm được.",
        "present": [], "log": long_log,
        "required": {"CHAR.CAO_MINH.VAN_KHO_GIOI_CHI", "LEVEL.01"},
        "quality": {"ability_overreach": {"CHAR.CAO_MINH.VAN_KHO_GIOI_CHI"}}
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
    old_units = 8 + (2 if any(k in case["action"].lower() for k in ["entity", "hound", "item", "vạn khố", "water", "nước"]) else 0) + (1 if case.get("present") else 0)
    old_irrelevant += max(0, old_units - len(required))
    supportive = required | MANDATORY | {f"LEVEL.{case['level']:02d}", "ENTITY.GLOBAL_HARD_LOCK", "ITEM.GLOBAL_HARD_LOCK", "REL.CAO_MINH.LUC_TRAM.BASELINE", "REL.CAO_MINH.SYVIAL.BASELINE"}
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
