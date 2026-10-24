#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Benchmark Canon Runtime 2.0 (CR2) — mô phỏng Python trên knowledge_db.json v2 THẬT.

Mục đích (theo ARCHITECTURE.md §12): đo Retrieval Effectiveness + Canon Safety của
thiết kế CR2 ở mức mô phỏng, TRƯỚC khi có runtime Kotlin. Script này KHÔNG thực thi
Kotlin, KHÔNG gọi API, KHÔNG chứng minh chất lượng văn xuôi của model.

Semantics mô phỏng (đúng thiết kế CR2):
  - Scene-first: đầu vào là SceneSnapshot chuẩn hoá (actor hiện diện, Level,
    entity đã CONFIRMED, mention chưa xác nhận, intent, vật trong focus).
  - Cổng 0 — scope/POV: universe_id khớp; knowledge_visibility:
    WORLD/META luôn qua; ACTOR_AWARE chỉ qua khi entity liên quan actor hiện diện/POV.
  - Cổng 1 — ID chính xác: global hard-lock anchors, runtime-card của actor hiện diện,
    record của Level hiện tại, entity đã CONFIRMED_PRESENT, cặp quan hệ/xưng hô giữa
    các actor cùng hiện diện, record của vật trong focus.
  - Cổng 2 — Alias có kiểm soát: mention chỉ khớp qua alias index (segment của record id,
    chuẩn hoá bỏ dấu tiếng Việt). Mention KHÔNG BAO GIỜ nâng entity thành confirmed.
    Alias trỏ tới >1 entity mà scope (actor hiện diện) không thu hẹp được về 1
    -> AMBIGUOUS, không chọn record nào qua cổng này.
  - Cổng 3 — Typed edge, maxHops = 1: đi theo `references` của record required,
    chỉ kéo record priority <= 55 (hợp đồng engine hiện hành, Gate 0 §5.1).
    Cạnh LEVEL -> ENTITY được suy luận là MAY_CONTAIN: KHÔNG suy ra IS_PRESENT,
    target bị loại khỏi packet trừ khi được xác nhận độc lập.
    (DB v2 chưa có typed edges — đây là suy luận suy giảm đã ghi rõ, Gate 2 sẽ thay
    bằng cạnh có type thật.)
  - Cổng 4 — affordance: intent của scene ánh xạ sang affordance; record của actor
    hiện diện mang affordance đó là required (năng lực "có thể dùng" phải nạp trước,
    §6.3 — phân biệt CAN_DO với WILL_DO: nạp record không ép actor hành động).
    Intent lạ -> NO_CANON_MATCH cho phần optional, không ép record gần giống.
  - ContextCompiler: ngân sách trọn record theo profile, estimator bảo thủ
    tokens = ceil(chars / 3.5) (§7.2). Required xếp trước, optional theo
    (priority, id) xác định. Record không vừa ngân sách thì BỎ NGUYÊN record,
    không cắt cụt text; required bị bỏ -> báo tường minh trong missing_required
    và packet mang trạng thái NEEDS_SAFE_LOCAL_RESOLUTION.
  - truth_status UNKNOWN: record vẫn được truy xuất khi liên quan, nhưng packet
    gắn cờ UNKNOWN và record KHÔNG nằm trong tập "affirmed canon".

Ngân sách profile (token ước lượng tại chars/3.5): COMPACT 2600 / STANDARD 3200 /
RICH 3900 — tương đương họ ngân sách 2200/2800/3400 của engine v1 tại estimator
chars/4 cũ, quy đổi sang estimator bảo thủ hơn (làm tròn lên ở COMPACT).

Chạy: python3 benchmark-canon-runtime-2.py   (exit 0 khi mọi kịch bản PASS)
"""

import json
import math
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "app" / "src" / "main" / "assets" / "knowledge" / "knowledge_db.json"

PROFILES = {"COMPACT": 2600, "STANDARD": 3200, "RICH": 3900}
EDGE_MAX_PRIORITY = 55          # hợp đồng engine hiện hành: references chỉ kéo pri <= 55
MAX_GRAPH_HOPS = 1              # §6.2 hàng rào chống bùng nổ


def tok(text: str) -> int:
    """Estimator bảo thủ chars/3.5 (§7.2)."""
    return max(1, math.ceil(len(text) / 3.5))


def record_cost(rec: dict) -> int:
    # text nguyên vẹn + dòng header gọn (id + truth + why) — không provenance dài (§7.2).
    return tok(rec["text"]) + tok(rec["id"]) + 6


def strip_accents(s: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn").lower()


# ---------------------------------------------------------------- DB + indexes
class CanonStore:
    def __init__(self, db: dict):
        assert db.get("schemaVersion") == 2, "benchmark CR2 yêu cầu knowledge_db schemaVersion 2"
        self.records = {r["id"]: r for r in db["records"]}
        assert len(self.records) == len(db["records"]), "trùng id trong DB"
        self.by_entity = {}
        for r in db["records"]:
            self.by_entity.setdefault(r["entity_id"], []).append(r["id"])
        # Alias index (Cổng 2): token = segment của record id, bỏ dấu, >= 3 ký tự.
        self.alias_index = {}
        skip = {"char", "entity", "item", "level", "rel", "addr", "game",
                "writing", "world", "story"}
        for r in db["records"]:
            tokens = set()
            for seg in r["id"].replace(".", "_").split("_"):
                t = strip_accents(seg)
                if len(t) >= 3 and t not in skip:
                    tokens.add(t)
            for t in tokens:
                self.alias_index.setdefault(t, set()).add(r["id"])


# ---------------------------------------------------------------- Scene + Resolver
INTENT_AFFORDANCE = {
    "inspect_tracks": "trace_analysis",
    "dialogue": "dialogue",
    "combat": "direct_threat",
    "use_item": "item_manipulation",
    "check_resource": "resource_check",
}

GLOBAL_ANCHORS = ["WORLD.CORE"]  # hard-canon nền, luôn required cùng hard-lock pri<=12


def entity_actors(entity_id: str):
    """Actor liên quan của một entity: CHAR.X -> {CHAR.X}; REL.A.B -> {CHAR.A, CHAR.B}."""
    parts = entity_id.split(".")
    if parts[0] == "CHAR" and len(parts) >= 2:
        return {"CHAR." + parts[1]}
    if parts[0] == "REL" and len(parts) >= 3:
        return {"CHAR." + parts[1], "CHAR." + parts[2]}
    return set()


class Resolution:
    def __init__(self):
        self.required = {}       # id -> why
        self.optional = []       # ids đã xếp hạng
        self.missing_required = []  # (id, reason) — báo tường minh, không im lặng
        self.notes = []          # trace: lý do loại/chọn
        self.ambiguous = []      # mention mơ hồ đã bị chặn


class Resolver:
    def __init__(self, store: CanonStore):
        self.store = store

    # Cổng 0 — scope/POV
    def gate0(self, rec: dict, scene: dict) -> bool:
        if rec.get("universe_id") != scene["universe_id"]:
            return False
        vis = rec.get("knowledge_visibility")
        if vis in ("WORLD", "META"):
            return True
        if vis == "ACTOR_AWARE":
            present = set(scene["actors_present"]) | {scene["pov_actor_id"]}
            return bool(entity_actors(rec["entity_id"]) & present)
        return False

    def require(self, res: Resolution, rid: str, why: str, scene: dict):
        rec = self.store.records.get(rid)
        if rec is None:
            res.missing_required.append((rid, "NO_CANON_MATCH"))
            return
        if not self.gate0(rec, scene):
            res.notes.append(f"{rid}: bị Cổng 0 chặn (scope/POV)")
            return
        res.required.setdefault(rid, why)

    def resolve(self, scene: dict) -> Resolution:
        st = self.store
        res = Resolution()
        present = list(scene["actors_present"])
        present_set = set(present) | {scene["pov_actor_id"]}
        affordances = {INTENT_AFFORDANCE[i] for i in scene.get("action_intents", [])
                       if i in INTENT_AFFORDANCE}
        unknown_intents = [i for i in scene.get("action_intents", [])
                           if i not in INTENT_AFFORDANCE]

        # Cổng 1a — global hard-locks (kind hard-lock, priority <= 12) + anchor nền
        for r in st.records.values():
            if r["kind"] == "hard-lock" and r["priority"] <= 12:
                self.require(res, r["id"], "global hard-lock", scene)
        for rid in GLOBAL_ANCHORS:
            self.require(res, rid, "world anchor", scene)

        # Cổng 1b — runtime-card của mọi actor hiện diện + POV
        for actor in sorted(present_set):
            for rid in st.by_entity.get(actor, []):
                if st.records[rid]["kind"] == "runtime-card":
                    self.require(res, rid, "actor present: core identity", scene)

        # Cổng 1c — Level hiện tại
        level_id = scene["level_id"]
        level_recs = [rid for rid in st.by_entity.get(level_id, [])
                      if st.records[rid]["domain"] == "LEVEL"]
        if not level_recs:
            res.missing_required.append((level_id, "NO_CANON_MATCH"))
        for rid in level_recs:
            self.require(res, rid, "current level", scene)

        # Cổng 1d — entity đã CONFIRMED_PRESENT (chỉ từ state, không từ mention)
        confirmed = {e["id"] for e in scene.get("entity_encounters", [])
                     if e.get("status") == "CONFIRMED_PRESENT"}
        for ent in sorted(confirmed):
            for rid in st.by_entity.get(ent, []):
                if st.records[rid]["domain"] == "ENTITY":
                    self.require(res, rid, "entity CONFIRMED_PRESENT", scene)

        # Cổng 1e — vật trong focus
        for obj in scene.get("objects_in_focus", []):
            for rid in st.by_entity.get(obj, []):
                self.require(res, rid, "object in focus", scene)
        if scene.get("objects_in_focus") or "item_manipulation" in affordances:
            self.require(res, "ITEM.GLOBAL_HARD_LOCK", "item hard-lock", scene)

        # Cổng 4 — affordance của actor hiện diện (nạp năng lực có thể dùng, §6.3).
        # CHAR.X khớp khi X hiện diện. REL.A.B là khoá quan hệ/xưng hô giữa HAI phía:
        # chỉ required khi CẢ HAI phía cùng hiện diện trong scene — một phía vắng mặt
        # thì quan hệ đó không phải ràng buộc của lượt này.
        for r in st.records.values():
            actors = entity_actors(r["entity_id"])
            if r["entity_id"].startswith("REL."):
                involved_ok = bool(actors) and actors <= present_set
            else:
                involved_ok = r["entity_id"] in present_set or bool(actors & present_set)
            if involved_ok and affordances & set(r.get("affordances", [])):
                self.require(res, r["id"],
                             f"affordance {sorted(affordances & set(r.get('affordances', [])))}",
                             scene)

        # Cổng 3 — typed edge, maxHops=1, từ tập required hiện tại
        frontier = list(res.required)
        for rid in frontier:
            src = st.records[rid]
            for ref in src.get("references", []):
                tgt = st.records.get(ref)
                if tgt is None:
                    res.notes.append(f"{rid} -> {ref}: BROKEN_REFERENCE")
                    continue
                if src["domain"] == "LEVEL" and tgt["domain"] == "ENTITY":
                    res.notes.append(f"{rid} -> {ref}: MAY_CONTAIN, không suy ra IS_PRESENT")
                    continue
                if tgt["priority"] > EDGE_MAX_PRIORITY:
                    res.notes.append(f"{rid} -> {ref}: quá EDGE_MAX_PRIORITY")
                    continue
                if ref not in res.required:
                    self.require(res, ref, f"edge from {rid} (hop 1)", scene)

        # Cổng 2 — alias cho mention chưa xác nhận: KHÔNG nâng thành confirmed
        for mention in scene.get("mentions", []):
            key = strip_accents(mention).strip()
            cand_ids = set()
            for token in key.split():
                cand_ids |= st.alias_index.get(token, set())
            cand_entities = {st.records[i]["entity_id"] for i in cand_ids}
            scoped = cand_entities & present_set
            if not cand_ids:
                res.notes.append(f"mention '{mention}': NO_CANON_MATCH")
            elif len(cand_entities) > 1 and len(scoped) != 1:
                res.ambiguous.append(mention)
                res.notes.append(
                    f"mention '{mention}': AMBIGUOUS {sorted(cand_entities)} — cần scope, không chọn")
            elif len(scoped) == 1:
                ent = next(iter(scoped))
                for rid in sorted(cand_ids):
                    if st.records[rid]["entity_id"] == ent and rid not in res.required:
                        res.optional.append(rid)
                        res.notes.append(
                            f"mention '{mention}': scoped theo actor hiện diện {ent} -> optional {rid}")
            else:
                res.notes.append(
                    f"mention '{mention}': MENTIONED_NOT_CONFIRMED — không đưa vào packet")

        if unknown_intents:
            res.notes.append(f"intent lạ {unknown_intents}: NO_CANON_MATCH cho optional")

        # Optional pool: record qua Cổng 0 của actor hiện diện + story thread đang chạy,
        # chưa required; xếp hạng xác định (priority, id).
        opt = set(res.optional)
        for r in st.records.values():
            if r["id"] in res.required or not self.gate0(r, scene):
                continue
            if r["entity_id"] in present_set or r["entity_id"] in scene.get("story_threads_active", []):
                opt.add(r["id"])
        res.optional = sorted(opt, key=lambda i: (st.records[i]["priority"], i))
        return res


# ---------------------------------------------------------------- Compiler
class Packet:
    def __init__(self, ids, est_tokens, missing, status, text, affirmed):
        self.ids = ids
        self.est_tokens = est_tokens
        self.missing_required = missing
        self.status = status
        self.text = text
        self.affirmed = affirmed


def compile_packet(store: CanonStore, res: Resolution, budget: int) -> Packet:
    ordered = sorted(res.required, key=lambda i: (store.records[i]["priority"], i))
    ordered += [i for i in res.optional if i not in res.required]
    chosen, used, missing = [], 0, list(res.missing_required)
    chosen_set = set()
    for rid in ordered:
        c = record_cost(store.records[rid])
        if used + c <= budget:
            chosen.append(rid)
            chosen_set.add(rid)
            used += c
        elif rid in res.required:
            missing.append((rid, "BUDGET_EXCEEDED_WHOLE_RECORD"))
    lines = ["[CANON_PACKET CR2-SIM]"]
    for rid in chosen:
        r = store.records[rid]
        why = res.required.get(rid, "optional")
        lines.append(f"[RECORD {rid} | truth={r['truth_status']} | why={why}]")
        lines.append(r["text"])
    affirmed = [i for i in chosen if store.records[i]["truth_status"] == "CONFIRMED"]
    status = "NEEDS_SAFE_LOCAL_RESOLUTION" if missing else "OK"
    return Packet(chosen, used, missing, status, "\n".join(lines), affirmed)


# ---------------------------------------------------------------- Scenes (fixture trên DB thật)
def scene(**kw):
    base = {"universe_id": "BACKROOMS_MAIN", "level_id": "LEVEL.01",
            "pov_actor_id": "CHAR.CAO_MINH", "actors_present": ["CHAR.CAO_MINH"],
            "entity_encounters": [], "mentions": [], "action_intents": [],
            "objects_in_focus": [], "story_threads_active": []}
    base.update(kw)
    return base


RESULTS = []


def check(name, ok, detail):
    RESULTS.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} — {detail}")


def main():
    store = CanonStore(json.loads(DB_PATH.read_text(encoding="utf-8")))
    resolver = Resolver(store)
    print(f"DB v2: {len(store.records)} records | estimator chars/3.5 | "
          f"profiles {PROFILES}\n")

    # (a1) Required recall — scene điều tra + đối thoại, 2 actor, Level 1
    sc = scene(level_id="LEVEL.01",
               actors_present=["CHAR.CAO_MINH", "CHAR.LUC_TRAM"],
               action_intents=["inspect_tracks", "dialogue"])
    res = resolver.resolve(sc)
    pkt = compile_packet(store, res, PROFILES["STANDARD"])
    expected = {"GAME.TEXT.CORE", "GAME.GM.FAIRNESS", "WRITING.PLAYER_AGENCY",
                "WRITING.KNOWLEDGE_BOUNDARY", "WRITING.COMPETENCE", "WORLD.CORE",
                "CHAR.CAO_MINH.RUNTIME_CORE", "CHAR.LUC_TRAM.RUNTIME_CORE", "LEVEL.01",
                "ADDR.LUC_TRAM.CAO_MINH", "REL.CAO_MINH.LUC_TRAM.BASELINE",
                "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM"}
    got = expected & set(pkt.ids)
    check("a1 required recall (điều tra + đối thoại, STANDARD)",
          got == expected and not pkt.missing_required,
          f"recall {len(got)}/{len(expected)}; packet {len(pkt.ids)} records, "
          f"~{pkt.est_tokens} tok / ngân sách {PROFILES['STANDARD']}; "
          f"missing={pkt.missing_required}")

    # (a2) Required recall — combat với entity đã CONFIRMED, Level 5
    sc = scene(level_id="LEVEL.05",
               entity_encounters=[{"id": "ENTITY.BEAST_LEVEL_5",
                                   "status": "CONFIRMED_PRESENT"}],
               action_intents=["combat"])
    res = resolver.resolve(sc)
    pkt = compile_packet(store, res, PROFILES["RICH"])
    expected = {"GAME.TEXT.CORE", "WORLD.CORE", "CHAR.CAO_MINH.RUNTIME_CORE",
                "LEVEL.05", "ENTITY.BEAST_LEVEL_5", "ENTITY.GLOBAL_HARD_LOCK",
                "CHAR.CAO_MINH.DAI_MA_DAU", "CHAR.CAO_MINH.HUYET_MA_KIEM"}
    got = expected & set(pkt.ids)
    check("a2 required recall (combat, entity confirmed, RICH)",
          got == expected and not pkt.missing_required,
          f"recall {len(got)}/{len(expected)}; packet {len(pkt.ids)} records, "
          f"~{pkt.est_tokens} tok / ngân sách {PROFILES['RICH']}")

    # (b) Adversarial: entity chỉ được NHẮC trong action + MAY_CONTAIN của Level
    sc = scene(level_id="LEVEL.01", mentions=["smiler"],
               action_intents=["inspect_tracks"])
    res = resolver.resolve(sc)
    pkt = compile_packet(store, res, PROFILES["STANDARD"])
    leaked = {"ENTITY.SMILER", "ENTITY.HOUND", "ENTITY.FALSE_PUDDLE"} & set(pkt.ids)
    check("b adversarial: mention không thành confirmed, MAY_CONTAIN không thành present",
          not leaked,
          f"leaked={sorted(leaked)}; notes={[n for n in res.notes if 'SMILER' in n or 'MAY_CONTAIN' in n]}")

    # (c) Alias mơ hồ cần scope
    sc = scene(level_id="LEVEL.04", pov_actor_id="CHAR.SYVIAL",
               actors_present=["CHAR.SYVIAL"], mentions=["kiếm"])
    res1 = resolver.resolve(sc)
    pkt1 = compile_packet(store, res1, PROFILES["STANDARD"])
    amb = {"CHAR.CAO_MINH.HUYET_MA_KIEM", "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM",
           "CHAR.LUC_TRAM.THIEN_KIEM_THAT_THUC"} & set(pkt1.ids)
    sc2 = scene(level_id="LEVEL.04", pov_actor_id="CHAR.LUC_TRAM",
                actors_present=["CHAR.LUC_TRAM"], mentions=["kiếm"])
    res2 = resolver.resolve(sc2)
    pkt2 = compile_packet(store, res2, PROFILES["RICH"])
    scoped_ok = ("CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM" in pkt2.ids
                 and "kiếm" not in res2.ambiguous
                 and any("scoped theo actor hiện diện CHAR.LUC_TRAM" in n
                         for n in res2.notes))
    check("c alias mơ hồ: không scope -> chặn; có scope actor hiện diện -> chọn đúng entity",
          not amb and "kiếm" in res1.ambiguous and scoped_ok,
          f"không-scope chọn={sorted(amb)}, ambiguous={res1.ambiguous}; "
          f"có-scope: THIEN_KIEM_LINH_TAM trong packet={('CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM' in pkt2.ids)}")

    # (d) UNKNOWN không được khẳng định thành canon
    sc = scene(level_id="LEVEL.00", actors_present=["CHAR.LUC_TRAM", "CHAR.SYVIAL"],
               pov_actor_id="CHAR.LUC_TRAM", action_intents=["dialogue"])
    res = resolver.resolve(sc)
    pkt = compile_packet(store, res, PROFILES["STANDARD"])
    unk = "REL.LUC_TRAM.SYVIAL.BASELINE"
    check("d UNKNOWN: truy xuất được nhưng gắn cờ, không vào affirmed canon",
          unk in pkt.ids and unk not in pkt.affirmed
          and f"truth=UNKNOWN" in pkt.text,
          f"trong packet={unk in pkt.ids}; affirmed={unk in pkt.affirmed}; "
          f"affirmed total {len(pkt.affirmed)}/{len(pkt.ids)} records")

    # (e) Ngân sách: không vượt profile, không cắt cụt record, COMPACT < RICH
    sc = scene(level_id="LEVEL.04",
               actors_present=["CHAR.CAO_MINH", "CHAR.LUC_TRAM"],
               action_intents=["dialogue"])
    res = resolver.resolve(sc)
    pc = compile_packet(store, res, PROFILES["COMPACT"])
    pr = compile_packet(store, res, PROFILES["RICH"])
    whole = all(store.records[i]["text"] in pc.text for i in pc.ids) and \
        all(store.records[i]["text"] in pr.text for i in pr.ids)
    check("e ngân sách trọn record (COMPACT vs RICH cùng scene)",
          pc.est_tokens <= PROFILES["COMPACT"] and pr.est_tokens <= PROFILES["RICH"]
          and whole and len(pc.ids) < len(pr.ids) and not pc.missing_required,
          f"COMPACT {len(pc.ids)} records ~{pc.est_tokens}/{PROFILES['COMPACT']} tok; "
          f"RICH {len(pr.ids)} records ~{pr.est_tokens}/{PROFILES['RICH']} tok; whole-record={whole}")

    # (f1) Missing-required do ngân sách: báo tường minh, không cắt cụt hard record
    pkt = compile_packet(store, resolver.resolve(
        scene(action_intents=["dialogue"],
              actors_present=["CHAR.CAO_MINH", "CHAR.LUC_TRAM"])), 100)
    miss_ids = {m[0] for m in pkt.missing_required}
    check("f1 missing-required do ngân sách -> báo tường minh + NEEDS_SAFE_LOCAL_RESOLUTION",
          "GAME.TEXT.CORE" in miss_ids and pkt.status == "NEEDS_SAFE_LOCAL_RESOLUTION"
          and pkt.est_tokens <= 100,
          f"missing={[m[0] for m in pkt.missing_required][:4]}...; status={pkt.status}; "
          f"packet ~{pkt.est_tokens}/100 tok")

    # (f2) Missing-required do scene đòi record không tồn tại (Level 99)
    res = resolver.resolve(scene(level_id="LEVEL.99"))
    pkt = compile_packet(store, res, PROFILES["STANDARD"])
    check("f2 missing-required do NO_CANON_MATCH (LEVEL.99) -> báo tường minh",
          ("LEVEL.99", "NO_CANON_MATCH") in pkt.missing_required
          and pkt.status == "NEEDS_SAFE_LOCAL_RESOLUTION",
          f"missing={pkt.missing_required}")

    failed = [n for n, ok, _ in RESULTS if not ok]
    print(f"\n== CR2 BENCHMARK: {len(RESULTS) - len(failed)}/{len(RESULTS)} PASS ==")
    if failed:
        print("FAIL:", failed)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
