from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CODEX = ROOT / "kai-codex.txt"
GUN_SKILLS = ROOT / "kai-gun-skills.txt"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
R06_MARKER = "KAI-AKECHI-TWILIGHT-CODEX-20260829-R06"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def sync_runtime_knowledge() -> None:
    data = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
    records = {record.get("id"): record for record in data.get("records", [])}
    required_ids = (
        "CHAR.KAI.RUNTIME_CORE",
        "CHAR.KAI.WHITE_WRAITH",
        "CHAR.KAI.ARMOR",
    )
    missing = [record_id for record_id in required_ids if record_id not in records]
    if missing:
        raise RuntimeError("Kai R06 knowledge sync: missing records: " + ", ".join(missing))

    runtime = records["CHAR.KAI.RUNTIME_CORE"]
    runtime["text"] = (
        "Kai Akechi / Twilight: Black Blood captain under Vatican, UR+, half-human/half-demon, son of Sparda and Eve. "
        "Origin era 2299 is not a birth year; true age is unknown. Outside danger he can be relaxed, lazy, teasing and irreverent; "
        "in real danger he becomes disciplined and decisive, prioritizing teammates/civilians. He retains full control of himself and demon power. "
        "R06 combat visual lock: Kai wears full-body black-gunmetal/silver powered armor with a fully enclosed dark-visored helmet; "
        "the current design has no cape, no ragged rear cloth and no default blue glow. Do not infer his face, hair or eyes from the helmeted combat reference. "
        "Do not make him a reckless idiot, a moralizing hero-mouthpiece, or a puppet whose intentional actions GM chooses for the player."
    )
    runtime["source"]["anchor"] = "KAI-QUICK-01; KAI-VIS-01; KAI-PER-01; KAI-ACTION-LOCK-01"
    runtime["tags"] = list(dict.fromkeys(runtime.get("tags", []) + ["r06 visual", "powered armor", "helmet"]))

    weapon = records["CHAR.KAI.WHITE_WRAITH"]
    weapon["text"] = (
        "White Wraith Magnum is Kai's signature HANDCANNON revolver: black-gunmetal, with a clearly mechanical cylinder and an extremely long, thick barrel, "
        "large enough to read visually as a handheld cannon while still being used as a handgun. This R06 description locks silhouette only. "
        "Mechanically it uses one ammunition type formed directly from Kai's demon power, supports single-shot and near-600-round-per-minute automatic fire, "
        "and self-repairs through Sparda Core. Do not invent conventional ammo scarcity or extra special ammunition types merely to create difficulty."
    )
    weapon["tags"] = list(dict.fromkeys(weapon.get("tags", []) + ["handcannon", "revolver"]))

    armor = records["CHAR.KAI.ARMOR"]
    armor["text"] = (
        "R06 Blackblood Armor visual lock: full-body black-gunmetal and silver powered armor worn over Kai's human anatomy, with layered faceted/segmented sharp plating, "
        "bronze mechanical joint details, thick segmented limb armor and an intentionally asymmetric silhouette. One shoulder carries a large mechanical dragon-head pauldron "
        "with metal teeth and backward horns/spikes; the other uses layered sharp plates. The dragon pauldron is a visual form of the armor and grants no separate invented ability. "
        "The current armor has no cape, no ragged cloth strips and no default blue light accents. Demon Jaw Mask's current visual form is a fully enclosed helmet/faceplate protecting "
        "the entire head, face, jaw and neck, with a dark visor hiding the eyes; it is not merely a lower-face mask. Functionally Blackblood Armor and linked modules self-repair using "
        "Sparda Core and act as an extension of Kai's body rather than a heavy slowing shell. Demon Jaw provides gas filtration, enhanced vision, motion tracking, biological/demonic analysis, "
        "encrypted communications and targeting support. Talon Gauntlets provide claws, striking/grip/climb support and short-range electromagnetic effects on metal; Phantom Greaves provide bursts, "
        "jumps, mid-air direction changes, wall running, landing mitigation and stronger kicks."
    )
    armor["tags"] = list(dict.fromkeys(armor.get("tags", []) + ["full helmet", "dragon pauldron", "r06 visual"]))

    KNOWLEDGE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    verified = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
    by_id = {record.get("id"): record for record in verified.get("records", [])}
    checks = {
        "CHAR.KAI.RUNTIME_CORE": ("no cape", "no default blue glow", "fully enclosed dark-visored helmet"),
        "CHAR.KAI.WHITE_WRAITH": ("HANDCANNON revolver", "mechanical cylinder", "silhouette only"),
        "CHAR.KAI.ARMOR": ("mechanical dragon-head pauldron", "no ragged cloth strips", "fully enclosed helmet/faceplate"),
    }
    for record_id, markers in checks.items():
        text = by_id[record_id].get("text", "")
        for marker in markers:
            if marker not in text:
                raise RuntimeError(f"Kai R06 knowledge sync: {record_id} missing marker {marker!r}")


main = MAIN.read_text(encoding="utf-8")
codex = CODEX.read_text(encoding="utf-8").strip()
gun_skills = GUN_SKILLS.read_text(encoding="utf-8").strip()

if R06_MARKER not in codex:
    raise RuntimeError("Kai Codex: wrong or missing R06 source marker")
if "KAI-AKECHI-TWILIGHT-CODEX-20260817-R05" in codex:
    raise RuntimeError("Kai Codex: stale R05 source marker remains")
if len(codex) < 5000:
    raise RuntimeError(f"Kai Codex unexpectedly short: {len(codex)} chars")
for marker in (
    "HARD VISUAL LOCK R06",
    "không có áo choàng",
    "không dùng các dải sáng xanh",
    "helmet/faceplate kín đầu",
    "HANDCANNON revolver",
    "Đầu rồng ở vai chỉ là hình thức của Blackblood Armor",
):
    if marker not in codex:
        raise RuntimeError("Kai R06 visual canon missing: " + marker)
for stale in (
    "Các điểm sáng xanh trên giáp là dấu hiệu vận hành hệ thống",
    "Bảo vệ phần dưới mặt, đầu/cổ",
):
    if stale in codex:
        raise RuntimeError("Kai R06 codex still contains stale R05 visual rule: " + stale)
for marker in (
    "THE LAST REQUIEM",
    "SILENT LULLABY",
    "SALVATION",
    "QUICK STEP",
    "+50 điểm phần trăm Evasion",
):
    if marker not in gun_skills:
        raise RuntimeError("Kai gun-skill addendum missing: " + marker)

sync_runtime_knowledge()

combined_codex = codex + "\n\n" + gun_skills
java_codex = json.dumps(combined_codex, ensure_ascii=False)
constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = constant_anchor + f"  private static final String KAI_CANON = {java_codex};\n"
main = replace_once(main, constant_anchor, constant_block, "Kai canon Java constant")

state_anchor = (
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
state_with_canon = (
    '            "KAI CANON dưới đây là HARD LOCK. Nếu state hoặc model output cũ xung đột với danh tính, năng lực, trang bị, tính cách hay giới hạn cố định của Kai thì ưu tiên KAI_CANON; state chỉ mô tả tình trạng tạm thời có nguyên nhân hợp canon. Không tự nerf Kai, không tự thêm giới hạn ẩn và không tự quyết hành động có chủ ý thay Kai.\\n\\n" +\n'
    '            KAI_CANON + "\\n\\n" +\n'
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
main = replace_once(main, state_anchor, state_with_canon, "Kai canon prompt injection")

MAIN.write_text(main, encoding="utf-8")
print(f"Injected Kai R06 operational codex, synced R06 runtime knowledge, and retained gun-skill addendum ({len(combined_codex)} chars).")
