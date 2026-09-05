from pathlib import Path
import json
import py_compile

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
HTML = ROOT / "app/src/main/assets/index.html"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KCE = CORE / "knowledge/KnowledgeContextEngine.kt"

for script in [ROOT / "patch-kai-codex.py", ROOT / "patch-character-canon-r07.py", ROOT / "patch-entity-overlays-local.py"]:
    py_compile.compile(str(script), doraise=True)

kai_codex = (ROOT / "kai-codex-r10.txt").read_text(encoding="utf-8")
assert "KAI-AKECHI-TWILIGHT-CODEX-20260902-R10" in kai_codex
assert "SRU Assault Rifle MK19" in kai_codex
assert "SRU-MK20" in kai_codex
assert "Omnivault Ring R10 only has" not in kai_codex  # exact English runtime wording belongs to knowledge, not source digest
assert "Quét, Sao chép" in kai_codex

state = (CORE / "GameState.kt").read_text(encoding="utf-8")
for marker in [
    'const val KAI_SRU_MK19_ID = "kai:sru-assault-rifle-mk19"',
    'const val KAI_SRU_MK20_ID = "kai:sru-mk20"',
    'const val WEAPON_NAME = "SRU Assault Rifle MK19"',
    'const val ARMOR_NAME = "SRU-MK20"',
    'LUCIA_ID to LuciaCanon.character()',
    'LUCIA_ID to LuciaCanon.inventory()',
    'LUCIA_ID to LuciaCanon.equipment()',
]:
    assert marker in state, marker
assert 'const val KAI_WHITE_WRAITH_ID' not in state
assert 'const val KAI_BLACKBLOOD_ARMOR_ID' not in state

lucia = (CORE / "LuciaCanon.kt").read_text(encoding="utf-8")
for marker in [
    'const val LEGAL_NAME = "Hứa Thuý Mai"',
    'const val AGE = 19',
    '"powerScale" to "HUMAN_TRAINED"',
    '"fixedEncounterLevel" to "0"',
    '"randomSpawn" to "false"',
    '"addressKai" to "OPEN"',
    'quantity = 90',
]:
    assert marker in lucia, marker

continuity = (CORE / "StoryCompanionContinuity.kt").read_text(encoding="utf-8")
assert 'const val LUCIA_LEVEL = 0' in continuity
assert 'randomSpawnAllowed' in continuity

# Inventory V2 is the final runtime authority and intentionally retires Omnivault
# SCAN/COPY after the earlier character-canon patch has run. Verify the final engine,
# not the transient pre-Inventory-V2 compatibility markers.
omni = (CORE / "OmnivaultEngine.kt").read_text(encoding="utf-8")
assert 'OmnivaultCommand.Operation.STORE -> store' in omni
assert 'OmnivaultCommand.Operation.WITHDRAW -> withdraw' in omni
assert 'OmnivaultCommand.Operation.RESTORE -> restore' in omni
assert 'OmnivaultCommand.Operation.SCAN' not in omni
assert 'OmnivaultCommand.Operation.COPY' not in omni
assert 'omnivault_scanned' not in omni
assert 'omnivault_copied' not in omni

main = MAIN.read_text(encoding="utf-8")
for marker in [
    'thresholdRoll("luciaEncounter", 1, 1',
    'story-owned fixed Level 0 contact',
    'lucia.put("encountered", true)',
    '.put("follower", false)',
    '.put("followerCandidate", true)',
    'LUCIA R03 HARD LOCK:',
]:
    assert marker in main, marker

html = HTML.read_text(encoding="utf-8")
assert '<span>SRU Assault Rifle MK19</span>' in html
assert '<span>SRU-MK20</span>' in html
assert '<span>W.W Magnum</span>' not in html
assert '<span>Blackblood Armor & linked modules</span>' not in html

engine = KCE.read_text(encoding="utf-8")
assert 'CHAR.KAI.SRU_AR_MK19' in engine
assert 'CHAR.LUCIA.RUNTIME_CORE' in engine
assert 'REL.KAI.LUCIA.OPEN' in engine
assert 'STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER' in engine
assert 'CHAR.KAI.WHITE_WRAITH' not in engine

data = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = {r["id"]: r for r in data["records"]}
for rid in [
    "CHAR.KAI.RUNTIME_CORE",
    "CHAR.KAI.SRU_AR_MK19",
    "CHAR.KAI.ARMOR",
    "CHAR.KAI.OMNIVAULT",
    "CHAR.IRIS.RUNTIME_CORE",
    "CHAR.SYVIAL.RUNTIME_CORE",
    "CHAR.LUCIA.RUNTIME_CORE",
    "CHAR.LUCIA.M4A1",
    "CHAR.LUCIA.GAMEPLAY",
    "CHAR.LUCIA.NAVIGATION",
    "STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER",
    "REL.KAI.LUCIA.OPEN",
]:
    assert rid in records, rid
assert "CHAR.KAI.WHITE_WRAITH" not in records
assert "SRU" in records["CHAR.KAI.RUNTIME_CORE"]["text"]
assert "KNOWLEDGE LOCK" in records["CHAR.KAI.RUNTIME_CORE"]["text"]
assert "Scan, Copy" in records["CHAR.KAI.OMNIVAULT"]["text"]
assert records["REL.KAI.LUCIA.OPEN"]["mutability"] == "OPEN"
assert "HUMAN_TRAINED" in records["CHAR.LUCIA.RUNTIME_CORE"]["text"]
assert "random spawn" in records["STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER"]["text"]

regression = (TESTS / "CharacterCanonR07Test.kt").read_text(encoding="utf-8")
for marker in ["kaiStartsWithR10SruEquipment", "luciaIsKnownButNotInitiallyInParty", "luciaEncounterIsStoryOwnedAndNeverRandom"]:
    assert marker in regression

print("Character Canon R07 preflight contract passed.")
