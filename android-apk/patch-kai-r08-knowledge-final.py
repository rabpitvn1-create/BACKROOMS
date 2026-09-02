from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
ENGINE_PATH = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"


def require_record(records: dict[str, dict], record_id: str) -> dict:
    record = records.get(record_id)
    if record is None:
        raise RuntimeError(f"Kai R08 knowledge finalizer: missing record {record_id}")
    return record


data = json.loads(DB_PATH.read_text(encoding="utf-8"))
records = {record.get("id"): record for record in data.get("records", [])}

separation = require_record(records, "STORY.MAIN.SEPARATION")
separation["text"] = (
    "After the shared no-clip event, Kai, Iris and Syvial land apart. Direct links between the three, SRU/Command, "
    "Frontrooms, beacon and outside telemetry are initially offline. Kai does not know Iris's or Syvial's location/Level. "
    "Iris and Syvial exist in the campaign from the Prologue and are separated, not first-spawned by survivor RNG. "
    "Re-establishing contact or reunion requires continuity/geography/state support; rarity rolls never teleport them."
)

kai = require_record(records, "CHAR.KAI.RUNTIME_CORE")
kai["text"] = (
    "Kai Akechi / Twilight is the UR+ captain of SRU (Special Response Unit), part of the police force responding to abnormal phenomena. "
    "SRU's public dossier classifies him as human. His half-human/half-demon nature and parentage as the son of Sparda and Eve are "
    "writer-only KNOWLEDGE LOCK facts and must not be granted to NPCs without a valid in-story source. Origin era 2299 is not a birth year; "
    "true age is unknown. Outside danger he can be relaxed, lazy, teasing and irreverent; in real danger he becomes disciplined and decisive, "
    "prioritizing SRU teammates and civilians. He retains full control of himself and his power."
)
kai["source"]["anchor"] = "KAI-QUICK-01; KAI-ID-01; KAI-SECRET-01; KAI-PER-01; KAI-ACTION-LOCK-01"
kai["tags"] = ["kai", "twilight", "sru", "present core", "voice", "knowledge lock"]

sparda = require_record(records, "CHAR.KAI.SPARDA_CORE")
sparda["text"] = (
    "Sparda Core is a writer-only KNOWLEDGE LOCK source of infinite demon power with no intrinsic operating limit in current canon. "
    "Do not invent depletion, a mana bar, cooldown, corruption, berserk loss of control or backlash merely to balance Kai. "
    "The Core powers regeneration and self-repair of Kai's currently equipped items, including SRU-MK20, SRU-SG and Omnivault Ring. "
    "Infinite power does not equal omniscience or automatic success at objectives constrained by information, environment or people to protect."
)

trigger = require_record(records, "CHAR.KAI.DEVIL_TRIGGER")
trigger["text"] = (
    "Kai's Devil Trigger strongly enhances his established physical, mental, speed, reflex and combat capabilities. Its name and demonic nature "
    "are writer-only KNOWLEDGE LOCK unless revealed in-story. Current canon gives it no intrinsic maximum duration, cooldown, backlash, corruption, "
    "resource depletion or loss of control. Do not add those limits."
)

override = require_record(records, "CHAR.KAI.GUILTY_CROWN_OVERRIDE")
override["text"] = (
    "Guilty Crown Override is canonically exactly 24 consecutive SRU-SG firings with demon-power shells while external time is completely stopped "
    "and Kai is in Devil Trigger. Each firing remains a shotgun firing; do not collapse it into a generic burst, alter the count or replace the full "
    "time stop with a lesser slow-motion effect without a direct retcon."
)
override["references"] = ["CHAR.KAI.DEVIL_TRIGGER", "CHAR.KAI.SRU_SG"]

weapon = require_record(records, "CHAR.KAI.WHITE_WRAITH")
weapon["id"] = "CHAR.KAI.SRU_SG"
weapon["text"] = (
    "SRU-SG Shotgun is Kai's current signature firearm. It can fire ordinary physical shotgun shells, which are finite carried ammunition, or demon-power "
    "shells formed directly from Kai's power. Both are physical-impact attacks, but demon-power shells are tens of times more destructive than ordinary "
    "physical shells in current canon. Demon-power shells do not consume the physical-shell supply and do not run out because Sparda Core is infinite. "
    "SRU-SG remains a shotgun, not a handgun, revolver or machine gun, and self-repairs while equipped by Kai. White Wraith Magnum is retired equipment, "
    "not Kai's current weapon."
)
weapon["source"]["anchor"] = "KAI-EQP-SRU-SG-01; KAI-COMBAT-01"
weapon["tags"] = ["kai", "sru-sg", "shotgun", "demon shell", "physical shell", "white wraith legacy"]
weapon["references"] = ["CHAR.KAI.SPARDA_CORE"]

armor = require_record(records, "CHAR.KAI.ARMOR")
armor["text"] = (
    "SRU-MK20 is Kai's current black-gunmetal powered armor/exoskeleton with POLICE / SRU / SPECIAL RESPONSE UNIT identification. It leaves his head and "
    "face exposed, uses a high protective collar, concentrates segmented armor and assist mechanisms on torso, shoulders, arms, knees, shins and feet, "
    "and keeps tactical fabric around hips/thighs for mobility. Integrated arm modules provide mechanical claws, grip/strike support and short-range "
    "electromagnetic interaction with metal; integrated leg modules support bursts, jumps, mid-air redirection, wall-running, landing mitigation and stronger "
    "kicks when geometry permits. The whole equipped system self-repairs through Sparda Core. Retired Blackblood Armor, Demon Jaw Mask, Talon Gauntlets and "
    "Phantom Greaves are not separate current equipment."
)
armor["source"]["anchor"] = "KAI-EQP-SRU-MK20-01; KAI-VIS-01"
armor["tags"] = ["kai", "sru-mk20", "powered armor", "integrated arm module", "integrated leg module", "open face"]

vault = require_record(records, "CHAR.KAI.OMNIVAULT")
vault["text"] = (
    "Omnivault Ring is Kai's infinite spatial storage for inanimate objects. Current R08 canon retains only storage/withdrawal of the same stored objects "
    "and equipment restoration. Scan, Copy, item creation, duplicate creation, Marked and Upgrade are retired and must not be offered as current abilities. "
    "Restore acts only on existing equipment and returns that same item to its best previously existing state; after a successful restore, that equipment has "
    "a 24-hour per-item Omnivault restore cooldown. The ring does not store or recreate living beings. Continuous self-repair of items Kai is currently wearing "
    "or wielding comes from Sparda Core and is separate from Omnivault's restore cooldown."
)
vault["source"]["anchor"] = "KAI-EQP-OMNIVAULT-01; KAI-WEAK-01"
vault["tags"] = ["kai", "omnivault", "nhẫn vạn tàng", "storage", "restore", "hoàn nguyên", "scan retired", "copy retired"]

# Rebuild the ID index after converting the retired weapon record into the current SRU-SG record.
records = {record.get("id"): record for record in data.get("records", [])}
if "CHAR.KAI.WHITE_WRAITH" in records:
    raise RuntimeError("Kai R08 knowledge finalizer: retired White Wraith record id still present")
if "CHAR.KAI.SRU_SG" not in records:
    raise RuntimeError("Kai R08 knowledge finalizer: current SRU-SG record missing")

DB_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

engine = ENGINE_PATH.read_text(encoding="utf-8")
old_weapon_route = '      if (hasAny(actionText, "white wraith", "magnum")) direct += "CHAR.KAI.WHITE_WRAITH"'
new_weapon_route = '      if (hasAny(actionText, "sru-sg", "sru sg", "shotgun", "white wraith", "magnum")) direct += "CHAR.KAI.SRU_SG"'
if new_weapon_route not in engine:
    count = engine.count(old_weapon_route)
    if count != 1:
        raise RuntimeError(f"Kai R08 knowledge finalizer: expected one legacy weapon route, found {count}")
    engine = engine.replace(old_weapon_route, new_weapon_route, 1)
ENGINE_PATH.write_text(engine, encoding="utf-8")

verified = json.loads(DB_PATH.read_text(encoding="utf-8"))
by_id = {record.get("id"): record for record in verified.get("records", [])}
checks = {
    "STORY.MAIN.SEPARATION": ("SRU/Command",),
    "CHAR.KAI.RUNTIME_CORE": ("captain of SRU", "writer-only KNOWLEDGE LOCK"),
    "CHAR.KAI.SRU_SG": ("SRU-SG Shotgun", "physical shotgun shells", "White Wraith Magnum is retired"),
    "CHAR.KAI.ARMOR": ("SRU-MK20", "Retired Blackblood Armor"),
    "CHAR.KAI.OMNIVAULT": ("Scan, Copy", "are retired", "24-hour per-item"),
}
for record_id, markers in checks.items():
    text = by_id.get(record_id, {}).get("text", "")
    for marker in markers:
        if marker not in text:
            raise RuntimeError(f"Kai R08 knowledge finalizer: {record_id} missing marker {marker!r}")

current_payload = "\n".join(by_id[record_id]["text"] for record_id in (
    "CHAR.KAI.RUNTIME_CORE",
    "CHAR.KAI.SRU_SG",
    "CHAR.KAI.ARMOR",
    "CHAR.KAI.OMNIVAULT",
))
for forbidden in (
    "Black Blood captain under Vatican",
    "White Wraith Magnum is Kai's signature firearm",
    "scan-copy memory has exactly 3 slots",
    "Blackblood Armor and linked modules self-repair",
):
    if forbidden in current_payload:
        raise RuntimeError("Kai R08 knowledge finalizer: retired current-canon claim remains: " + forbidden)

final_engine = ENGINE_PATH.read_text(encoding="utf-8")
if 'direct += "CHAR.KAI.WHITE_WRAITH"' in final_engine:
    raise RuntimeError("Kai R08 knowledge finalizer: runtime still routes to retired White Wraith record")
if new_weapon_route not in final_engine:
    raise RuntimeError("Kai R08 knowledge finalizer: SRU-SG direct route missing")

print("Kai R08 runtime knowledge finalized: SRU-SG/SRU-MK20/Omnivault R08 replace retired equipment facts; legacy weapon terms route to current canon.")
