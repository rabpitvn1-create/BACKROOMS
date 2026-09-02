from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "sru-codex.txt"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
ENGINE = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
RECORD_ID = "WORLD.SRU.CORE"


def runtime_canon(text: str) -> str:
    start_marker = "BEGIN_RUNTIME_CANON\n"
    end_marker = "\nEND_RUNTIME_CANON"
    start = text.find(start_marker)
    end = text.find(end_marker)
    if start < 0 or end < 0 or end <= start:
        raise RuntimeError("SRU codex: runtime canon markers missing or malformed")
    return text[start + len(start_marker):end].strip()


source_text = SOURCE.read_text(encoding="utf-8")
if "SRU-FORCE-2299-R01" not in source_text:
    raise RuntimeError("SRU codex: wrong or missing canon marker")
canon = runtime_canon(source_text)
for marker in (
    "Cảnh Sát chống hiện tượng dị thường",
    "Kai Akechi / Twilight giữ chức Đội trưởng SRU",
    "SRU-MK20",
    "SRU-SG Shotgun",
    "lực lượng bước vào trước",
):
    if marker not in canon:
        raise RuntimeError("SRU codex: missing required marker: " + marker)

# Runtime knowledge record. Keep the full approved organization canon available only
# when the scene/action is actually about SRU, rather than spending context every turn.
data = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = data.get("records", [])
record = {
    "id": RECORD_ID,
    "domain": "WORLD",
    "kind": "organization",
    "text": canon,
    "source": {
        "document": "android-apk/sru-codex.txt",
        "anchor": "SRU-FORCE-2299-R01 / BEGIN_RUNTIME_CANON",
    },
    "authority": "USER_LOCKED_GAME_CANON",
    "mutability": "IMMUTABLE",
    "priority": 40,
    "tags": [
        "sru",
        "special response unit",
        "lực lượng phản ứng đặc biệt",
        "cảnh sát chống hiện tượng dị thường",
        "2299",
        "kai",
        "police",
    ],
    "references": [],
    "affordances": [],
}

existing = next((i for i, item in enumerate(records) if item.get("id") == RECORD_ID), None)
if existing is None:
    world_core = next((i for i, item in enumerate(records) if item.get("id") == "WORLD.CORE"), None)
    insert_at = len(records) if world_core is None else world_core + 1
    records.insert(insert_at, record)
else:
    records[existing] = record

KNOWLEDGE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Add an explicit organization lookup. Do not trigger on bare "sru" so ordinary
# SRU-SG / SRU-MK20 equipment actions do not drag ~500 words of organization lore
# into every combat packet.
engine = ENGINE.read_text(encoding="utf-8")
lookup = (
    '      if (hasAny(actionText, "lực lượng sru", "đơn vị sru", "đội sru", '
    '"special response unit", "lực lượng phản ứng đặc biệt", '
    '"cảnh sát chống hiện tượng dị thường", "sru năm 2299", "sru là gì")) '
    'direct += "WORLD.SRU.CORE"\n'
)
anchor = '      direct.forEach { add(it, "direct structured lookup") }\n'
if lookup not in engine:
    count = engine.count(anchor)
    if count != 1:
        raise RuntimeError(f"SRU codex: expected one direct lookup anchor, found {count}")
    engine = engine.replace(anchor, lookup + anchor, 1)
    ENGINE.write_text(engine, encoding="utf-8")

# Regression guards.
verified = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
by_id = {item.get("id"): item for item in verified.get("records", [])}
if RECORD_ID not in by_id:
    raise RuntimeError("SRU codex: runtime knowledge record missing after write")
text = by_id[RECORD_ID].get("text", "")
for marker in ("Đội trưởng SRU", "quyền phán đoán tại hiện trường", "SRU-SG Shotgun"):
    if marker not in text:
        raise RuntimeError("SRU codex: runtime record missing marker: " + marker)

engine_verified = ENGINE.read_text(encoding="utf-8")
if lookup not in engine_verified:
    raise RuntimeError("SRU codex: structured lookup was not installed")
if 'hasAny(actionText, "sru")' in engine_verified:
    raise RuntimeError("SRU codex: bare SRU lookup would cause unnecessary context bloat")

print(f"Installed {RECORD_ID} from SRU-FORCE-2299-R01 and explicit organization retrieval.")
