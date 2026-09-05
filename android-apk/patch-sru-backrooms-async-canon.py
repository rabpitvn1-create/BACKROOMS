from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "sru-backrooms-async-canon-r01.txt"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KCE = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"

SOURCE_ID = "SRU-BACKROOMS-ASYNC-FOUNDATION-R01"
SOURCE_DOC = "android-apk/sru-backrooms-async-canon-r01.txt"

source_text = SOURCE.read_text(encoding="utf-8")
for marker in [
    SOURCE_ID,
    "1. BẢN CHẤT SRU",
    "2. KAI, SYVIAL VÀ IRIS",
    "3. NHIỆM VỤ BACKROOMS NĂM 2299",
    "4. ASYNC",
    "5. KHÓA DIỄN GIẢI",
]:
    if marker not in source_text:
        raise RuntimeError(f"Foundation canon source marker missing: {marker}")

root = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = root.get("records")
if not isinstance(records, list):
    raise RuntimeError("knowledge_db.json records missing")


def upsert(record: dict) -> None:
    matches = [i for i, item in enumerate(records) if isinstance(item, dict) and item.get("id") == record["id"]]
    if len(matches) > 1:
        raise RuntimeError(f"Duplicate knowledge record before foundation canon patch: {record['id']}")
    if matches:
        records[matches[0]] = record
    else:
        records.append(record)


upsert({
    "id": "WORLD.SRU.BASELINE",
    "domain": "WORLD",
    "kind": "organization",
    "text": (
        "SRU / Special Response Unit is the special-response arm of the Cảnh Sát chống hiện tượng dị thường. "
        "It deploys when ordinary procedures cannot safely handle an anomalous incident. Its baseline is not 'destroy every anomaly': "
        "identify the situation, protect survivors, prevent escalation, then contain or eliminate a threat when necessary. Kai Akechi / Twilight "
        "is SRU team captain and primary marksman; Syvial is his UR+ deputy/frontline high-tier combatant; Iris / ARGUS is Scout / Target Eliminator. "
        "Their roles are complementary, not a hierarchy that erases independent judgment. Lucia / Hứa Thuý Mai is not an initial SRU member."
    ),
    "source": {"document": SOURCE_DOC, "anchor": "1-2"},
    "authority": "PROJECT_CANON",
    "mutability": "IMMUTABLE",
    "priority": 34,
    "tags": ["sru", "special response unit", "cảnh sát chống hiện tượng dị thường", "kai", "syvial", "iris", "organization"],
    "references": [],
    "affordances": [],
})

upsert({
    "id": "WORLD.ASYNC.BASELINE",
    "domain": "WORLD",
    "kind": "historical-organization",
    "text": (
        "Async Research Institute is a historical private research organization adapted into Project canon from the Kane Pixels continuity. "
        "Project KV31 used the Low-Proximity Magnetic Distortion System to create the Threshold and gain controlled access to the space Async called The Complex, "
        "then survey it and establish observation infrastructure. KV31 is not pre-locked as a villainous extermination plot. The Complex showed anomalous geometry, "
        "time irregularities and people appearing without evidence of using Async's Threshold. HARD LOCK: Async accessed and studied The Complex, but current canon does "
        "not prove that Async created Backrooms or caused all cross-era disappearances. Whether Async caused, widened or merely discovered a pre-existing access mechanism remains OPEN."
    ),
    "source": {"document": SOURCE_DOC, "anchor": "4-5"},
    "authority": "PROJECT_CANON",
    "mutability": "IMMUTABLE",
    "priority": 36,
    "tags": ["async", "async research institute", "project kv31", "kv31", "lpmds", "threshold", "the complex"],
    "references": [],
    "affordances": [],
})

upsert({
    "id": "STORY.BACKROOMS.MISSION",
    "domain": "STORY",
    "kind": "foundation-premise",
    "text": (
        "In 2299 SRU investigates disappearances spanning multiple eras. Repeating survivor evidence makes Backrooms a working hypothesis, not a solved cause; historical Async/KV31 records become the strongest technical lead. "
        "The mission sequence is: test whether Backrooms links the disappearances; seek evidence of missing people; determine how far Async reached and whether its activity is causal or only contact; if supported, learn exit rules or a reliable route to Frontrooms. "
        "Initial entry participants are Kai, Syvial and Iris only. They enter through an SRU-tested time-space gate; a transition anomaly separates them, Kai begins at Level 0, and team/outside links are initially unavailable. "
        "Reunion must follow geography/continuity/state and may not be produced by random spawning. SRU is investigating Async, not entering Backrooms with a pre-decided verdict against it."
    ),
    "source": {"document": SOURCE_DOC, "anchor": "3-5"},
    "authority": "PROJECT_CANON",
    "mutability": "IMMUTABLE",
    "priority": 32,
    "tags": ["backrooms mission", "sru mission", "2299", "async", "cross-era disappearances", "kai", "syvial", "iris"],
    "references": ["WORLD.SRU.BASELINE", "WORLD.ASYNC.BASELINE"],
    "affordances": [],
})

# Retire the legacy Black Blood/shared-no-clip wording still present in the old story seed.
separation = next((r for r in records if isinstance(r, dict) and r.get("id") == "STORY.MAIN.SEPARATION"), None)
if separation is None:
    raise RuntimeError("STORY.MAIN.SEPARATION record missing")
separation["text"] = (
    "During the 2299 SRU Backrooms mission, Kai, Iris and Syvial enter through the tested SRU time-space gate; a transition anomaly separates them. "
    "Kai begins at Level 0 and does not know Iris's or Syvial's location. Direct team links and outside telemetry are initially unavailable. "
    "Iris and Syvial exist from the Prologue and are separated, not spawned by survivor RNG. Re-establishing contact or reunion requires geography, continuity and state support."
)
separation["source"] = {"document": SOURCE_DOC, "anchor": "3"}
separation["authority"] = "PROJECT_CANON"
separation["mutability"] = "BASELINE"
separation["tags"] = ["main campaign", "separated", "communication", "iris", "syvial", "sru", "time-space gate"]

ids = [r.get("id") for r in records if isinstance(r, dict)]
for required_id in ["WORLD.SRU.BASELINE", "WORLD.ASYNC.BASELINE", "STORY.BACKROOMS.MISSION", "STORY.MAIN.SEPARATION"]:
    if ids.count(required_id) != 1:
        raise RuntimeError(f"Foundation canon record must exist exactly once: {required_id}")

KNOWLEDGE.write_text(json.dumps(root, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

kce = KCE.read_text(encoding="utf-8")
direct_anchor = '      val direct = linkedSetOf<String>()\n'
direct_block = '''      if (hasAny(actionText, "sru", "special response unit", "cảnh sát chống hiện tượng dị thường")) direct += "WORLD.SRU.BASELINE"\n      if (hasAny(actionText, "async", "project kv31", "kv31", "threshold", "the complex", "low-proximity magnetic distortion")) {\n        direct += "WORLD.ASYNC.BASELINE"\n        direct += "STORY.BACKROOMS.MISSION"\n      }\n'''
if direct_block not in kce:
    if kce.count(direct_anchor) != 1:
        raise RuntimeError(f"Knowledge direct lookup anchor count changed: {kce.count(direct_anchor)}")
    kce = kce.replace(direct_anchor, direct_anchor + direct_block, 1)

mission_anchor = '        add("STORY.MAIN.OBJECTIVE", "active main-campaign objective")\n'
mission_line = '        add("STORY.BACKROOMS.MISSION", "active SRU Backrooms mission")\n'
if mission_line not in kce:
    if kce.count(mission_anchor) != 1:
        raise RuntimeError(f"Main campaign routing anchor count changed: {kce.count(mission_anchor)}")
    kce = kce.replace(mission_anchor, mission_line + mission_anchor, 1)

for marker in [
    'direct += "WORLD.SRU.BASELINE"',
    'direct += "WORLD.ASYNC.BASELINE"',
    'direct += "STORY.BACKROOMS.MISSION"',
    'add("STORY.BACKROOMS.MISSION", "active SRU Backrooms mission")',
]:
    if marker not in kce:
        raise RuntimeError(f"Foundation canon routing marker missing: {marker}")

KCE.write_text(kce, encoding="utf-8")
print("SRU / Backrooms / Async foundation canon R01 finalized and routed into runtime knowledge.")
