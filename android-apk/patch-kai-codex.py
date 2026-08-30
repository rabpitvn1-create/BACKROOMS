from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CODEX = ROOT / "kai-codex.txt"
GUN_SKILLS = ROOT / "kai-gun-skills.txt"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
SOURCE_MARKER = "KAI-AKECHI-TWILIGHT-CODEX-20260829-R06"
RUNTIME_MARKER = "KAI-SRU-R08-RUNTIME-20260830"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def current_character_override() -> str:
    return """CURRENT CHARACTER CANON R08 — HARD OVERRIDE
Runtime marker: KAI-SRU-R08-RUNTIME-20260830

KAI / STORY & KNOWLEDGE LOCK
- Kai Akechi / Twilight hiện là Đội trưởng SRU — Special Response Unit / Lực lượng Phản ứng Đặc biệt — thuộc lực lượng Cảnh Sát chống hiện tượng dị thường. Mọi continuity tổ chức cũ của Kai dưới Vatican / Black Blood đã hết hiệu lực.
- Hồ sơ SRU công khai coi Kai là con người. Sự thật Kai là bán nhân / bán quỷ, con trai của Sparda và Eve, là TUYỆT MẬT / KNOWLEDGE LOCK dành cho người viết. Không nhân vật nào được mặc định biết hoặc tự suy ra bí mật này chỉ từ năng lực, Devil Trigger, đạn quỷ lực, hồi phục hay cơ chế tự sửa chữa.
- Kai đến từ năm 2299; đây là niên đại xuất thân, không phải năm sinh. Tuổi thật vẫn không rõ.

KAI / KHÓA THỊ GIÁC R08
- Ngoại hình tương ứng người đàn ông khoảng 30 tuổi: cao, cân đối, thân hình săn chắc, vai rộng vừa phải và thiên về khả năng vận động hơn khối cơ bắp quá đồ sộ.
- Ảnh tham chiếu hiện hành để lộ đầu và khuôn mặt. Kai có tóc đen dày, hơi dài, rối tự nhiên; mắt xanh lạnh. Không dùng helmet kín đầu, Demon Jaw Mask che mặt, sừng cơ khí, pauldron đầu rồng, áo choàng hay dải vải rách của thiết kế cũ.
- SRU-MK20 là powered armor / exoskeleton đen–gunmetal, có nhận diện POLICE / SRU / SPECIAL RESPONSE UNIT. Cổ giáp cao bảo vệ cổ và hàm dưới nhưng không che mặt; các mảng giáp và cơ cấu trợ lực tập trung ở thân trên, vai, cánh tay, đầu gối, cẳng chân và bàn chân; vùng hông, đùi và thân dưới giữ vải chiến thuật đen để bảo toàn độ cơ động. Các đường sáng xanh nhỏ chỉ là điểm báo trạng thái hệ thống.
- Silhouette phải đọc như sĩ quan phản ứng đặc biệt được tăng cường cơ học, không phải hiệp sĩ fantasy.

IRIS / STORY & VISUAL
- Iris / ARGUS thuộc SRU, là thành viên đội Kai với vai trò Scout / Target Eliminator. Kai giữ quyền chỉ huy tổng thể; Syvial là Đội phó. ARGUS là callsign SRU, không còn là callsign Black Blood.
- Iris là con gái Belial và một người mẹ loài người. Mẹ cô đã qua đời; danh tính, nghề nghiệp, xuất thân và nguyên nhân qua đời của người mẹ vẫn UNKNOWN. Kai là người đầu tiên của SRU phát hiện Iris trong một sự cố siêu nhiên tại khu dân cư; sau đó SRU xác minh Belial Core, Thousandfold Cognition và chuẩn hóa Project 07.
- Ngoại hình hiện hành: nữ trẻ khoảng 18 tuổi, tóc bob đen ngắn quanh xương hàm/gáy, mắt nâu hổ phách; giáp cơ khí toàn thân đen–gunmetal với điểm sáng đỏ cam. Không có helmet che mặt, Command Slate, tablet hay drone trong cấu hình chính thức. Ivory & Ebony là cặp súng đặc trưng.
- Iris có tình cảm với Kai nhưng Kai chưa đáp lại; xưng em và gọi Kai là anh. Với Syvial, quan hệ là bạn bè/đồng đội có cạnh tranh tình cảm, không phải thù địch.

SYVIAL / STORY & VISUAL
- Syvial là con gái Lucifer và một người mẹ loài người chưa khóa danh tính. Cô đến từ năm 2299; đây là niên đại xuất thân, không phải năm sinh. Tuổi thật chưa rõ.
- Syvial thuộc SRU và giữ chức Đội phó trong đội Kai Akechi / Twilight. Cô ở cấp UR+ và cùng tầng sức mạnh tổng thể với Kai nhưng không có cùng bộ kỹ năng hay phong cách chiến đấu.
- Ngoại hình hiện hành: nữ trẻ trưởng thành, cao, chân dài, cân đối; tóc bạc trắng pha tím rất nhạt, cực dài, phần lớn buộc đuôi ngựa cao; mắt đỏ hồng / magenta. Headgear đen–gunmetal quanh thái dương và hai module nhọn là thiết bị cơ khí/cảm biến của Lucifer Armor, không phải sừng sinh học; gương mặt để lộ trong cấu hình tham chiếu.
- Syvial yandere rất nặng với Kai nhưng vẫn tỉnh táo, thông minh và có năng lực xã hội; cô muốn Kai tự nguyện chọn mình, không mặc định xóa ý chí, giam giữ hay tấn công mọi phụ nữ tiếp cận Kai.

Nếu bất kỳ dữ kiện legacy nào bên dưới mâu thuẫn với phần CURRENT CHARACTER CANON R08 này về tổ chức, story, knowledge boundary hoặc ngoại hình, phần R08 ở trên thắng tuyệt đối."""


def upgrade_kai_identity_visual(codex: str) -> str:
    start = codex.index("1. ĐỊNH DANH\n")
    end = codex.index("3. TÍNH CÁCH / NGUYÊN TẮC\n")
    replacement = """1. ĐỊNH DANH / STORY R08
- Tên: Kai Akechi (カイ・アケチ). Mật danh: Twilight. Giới tính: nam.
- Kai là Đội trưởng SRU — Special Response Unit / Lực lượng Phản ứng Đặc biệt — thuộc lực lượng Cảnh Sát chống hiện tượng dị thường. Mọi continuity tổ chức cũ dưới Vatican / Black Blood đã bị loại khỏi canon hiện hành.
- Hồ sơ SRU công khai phân loại Kai là con người. Sự thật hắn là bán nhân / bán quỷ, con trai Sparda và Eve, là TUYỆT MẬT / KNOWLEDGE LOCK; NPC không được tự biết hoặc suy ra nếu không có nguồn tri thức hợp lệ.
- Tuổi thật: không rõ. Tuổi biểu kiến: khoảng 30. Niên đại xuất thân: năm 2299, không phải năm sinh.
- Tôn giáo: Công Giáo như lựa chọn cá nhân, không phải tư cách thành viên của một tổ chức tôn giáo.
- Phân cấp chiến lực: UR+.
- Vai trò: chỉ huy hiện trường, xạ thủ chủ lực, chuyên gia xử lý mục tiêu dị thường cấp cao.

2. NGOẠI HÌNH / KHÓA THỊ GIÁC R08
- Ngoại hình tương ứng một người đàn ông khoảng 30 tuổi: cao, cân đối, thân hình săn chắc, vai rộng vừa phải, thiên về khả năng vận động hơn khối cơ quá đồ sộ.
- Ảnh tham chiếu chính thức hiện hành để lộ đầu và khuôn mặt. Kai có tóc đen dày, hơi dài, rối tự nhiên; mắt xanh lạnh. Không dùng mũ kín đầu, mặt nạ hàm hoặc sừng cơ khí của hình tham chiếu cũ.
- SRU-MK20 có nền đen–gunmetal, cấu trúc powered armor / exoskeleton nhiều lớp ở thân trên, vai, cánh tay, đầu gối, cẳng chân và bàn chân. Các vùng hông, đùi và thân dưới chừa vải chiến thuật đen để giữ độ cơ động; các đường sáng xanh nhỏ chỉ là tín hiệu trạng thái.
- Ngực và vai mang nhận diện POLICE / SRU / SPECIAL RESPONSE UNIT. Cổ giáp dựng cao bảo vệ cổ và hàm dưới nhưng không che khuôn mặt.
- Silhouette phải đọc như sĩ quan phản ứng đặc biệt tăng cường cơ học: torso bọc giáp, vai có module cứng, cẳng tay cơ khí lớn, đùi dùng dây đai/túi chiến thuật, đầu gối–ống chân–bàn chân có khung trợ lực.
- HARD VISUAL LOCK R08: không áo choàng, không dải vải rách, không pauldron đầu rồng và không chi tiết fantasy của bộ giáp cũ.

"""
    return codex[:start] + replacement + codex[end:]


def sync_runtime_knowledge() -> None:
    data = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
    records = {record.get("id"): record for record in data.get("records", [])}
    required_ids = (
        "STORY.MAIN.SEPARATION",
        "CHAR.KAI.RUNTIME_CORE",
        "CHAR.KAI.ARMOR",
        "CHAR.IRIS.RUNTIME_CORE",
        "CHAR.SYVIAL.RUNTIME_CORE",
    )
    missing = [record_id for record_id in required_ids if record_id not in records]
    if missing:
        raise RuntimeError("SRU character knowledge sync: missing records: " + ", ".join(missing))

    records["STORY.MAIN.SEPARATION"]["text"] = (
        "After the shared no-clip event, Kai, Iris and Syvial land apart. Direct links between the three, SRU/Command, "
        "Frontrooms, beacon and outside telemetry are initially offline. Kai does not know Iris's or Syvial's location/Level. "
        "Iris and Syvial exist in the campaign from the Prologue and are separated, not first-spawned by survivor RNG. "
        "Re-establishing contact or reunion requires continuity/geography/state support; rarity rolls never teleport them."
    )

    kai = records["CHAR.KAI.RUNTIME_CORE"]
    kai["text"] = (
        "Kai Akechi / Twilight is the UR+ captain of SRU (Special Response Unit), part of the police force responding to abnormal phenomena. "
        "SRU's public dossier treats him as human. His half-human/half-demon nature and parentage as the son of Sparda and Eve are writer-only KNOWLEDGE LOCK facts and must not be granted to NPCs without a valid in-story source. "
        "Origin era 2299 is not a birth year; true age is unknown. Outside danger he can be relaxed, lazy, teasing and irreverent; in real danger he becomes disciplined and decisive, prioritizing SRU teammates and civilians. "
        "R08 visual lock: exposed head and face, thick slightly long messy black hair, cold blue eyes, black-gunmetal SRU-MK20 powered armor with POLICE/SRU markings, high protective collar, tactical fabric at hips/thighs and small blue system-status accents. "
        "No enclosed helmet, jaw mask, mechanical horns, dragon pauldron, cape or ragged rear cloth from retired designs."
    )
    kai["source"]["anchor"] = "KAI-QUICK-01; KAI-ID-01; KAI-SECRET-01; KAI-VIS-01; KAI-PER-01"
    kai["tags"] = list(dict.fromkeys([tag for tag in kai.get("tags", []) if tag != "black blood"] + ["sru", "r08 visual", "knowledge lock"]))

    armor = records["CHAR.KAI.ARMOR"]
    armor["text"] = (
        "R08 visual/current armor lock: SRU-MK20 is Kai's black-gunmetal powered armor/exoskeleton with POLICE / SRU / SPECIAL RESPONSE UNIT identification. "
        "It leaves Kai's head and face exposed, uses a high collar that protects the neck/lower jaw without covering the face, concentrates segmented armor and assist mechanisms on the torso, shoulders, arms, knees, shins and feet, and keeps black tactical fabric around the hips, thighs and lower torso for mobility. "
        "Small blue lines are system-status accents. The current silhouette has no enclosed helmet, Demon Jaw face covering, mechanical horns, cape, ragged cloth or dragon-head pauldron."
    )
    armor["source"]["anchor"] = "KAI-VIS-01; KAI-EQP-SRU-MK20-01"
    armor["tags"] = ["kai", "sru-mk20", "powered armor", "r08 visual", "police", "sru"]

    iris = records["CHAR.IRIS.RUNTIME_CORE"]
    iris["text"] = (
        "Iris: official name has no locked surname; ARGUS is her SRU callsign. She is a half-human/half-demon daughter of Belial and a human mother who is deceased; the mother's identity, occupation, origin and cause of death remain UNKNOWN. "
        "Kai was the first SRU member to discover Iris during a supernatural residential incident; SRU later verified Belial Core, Thousandfold Cognition and standardized Project 07. "
        "Within Kai's SRU team she is Scout / Target Eliminator; Kai holds overall command and Syvial is deputy leader. She is calm, decisive, sharp, brave and caring, and a real ranged combatant/Gunslinger. "
        "Current visual lock: young woman around 18, short black bob, warm amber-brown eyes, black-gunmetal full-body mechanical armor with orange-red status lights, Ivory & Ebony, no face-covering helmet, Command Slate, tablet or drones. "
        "She has romantic feelings for Kai but he has not reciprocated; Iris and Syvial are friends/teammates with romantic rivalry, not enemies."
    )
    iris["source"]["anchor"] = "IRIS-ID-01; IRIS-ORIGIN-01; IRIS-ORIGIN-KAI-01; IRIS-VIS-01; IRIS-REL-01"
    iris["tags"] = list(dict.fromkeys([tag for tag in iris.get("tags", []) if tag != "black blood"] + ["sru", "project 07", "r06 visual"]))

    syvial = records["CHAR.SYVIAL.RUNTIME_CORE"]
    syvial["text"] = (
        "Syvial is the half-human/half-demon daughter of Lucifer and a human mother whose identity is not locked. Origin era 2299 is not a birth year; true age is unknown. "
        "She belongs to SRU and is deputy leader of Kai Akechi / Twilight's team. She is UR+ and in the same overall power tier as Kai, without sharing the same skill set or fighting style. "
        "Outside combat she is natural, social, teasing and food-loving; in danger she becomes focused and precise. She is intensely yandere toward Kai but lucid, intelligent and socially capable, and values Kai freely choosing her. "
        "Current visual lock: tall young adult woman with long legs, very long silver-white hair with a faint violet tint tied mostly in a high ponytail, bright magenta eyes, and black-gunmetal mechanical headgear around the temples; the two pointed modules are Lucifer Armor sensors, not biological horns, and her face is exposed in the reference configuration."
    )
    syvial["source"]["anchor"] = "SYVIAL-QUICK-01; SYVIAL-VIS-01; SYVIAL-YANDERE-01"
    syvial["tags"] = list(dict.fromkeys(syvial.get("tags", []) + ["sru", "deputy leader", "visual lock"]))

    KNOWLEDGE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    verified = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
    by_id = {record.get("id"): record for record in verified.get("records", [])}
    checks = {
        "STORY.MAIN.SEPARATION": ("SRU/Command",),
        "CHAR.KAI.RUNTIME_CORE": ("SRU (Special Response Unit)", "writer-only KNOWLEDGE LOCK", "No enclosed helmet"),
        "CHAR.KAI.ARMOR": ("SRU-MK20", "head and face exposed", "no enclosed helmet"),
        "CHAR.IRIS.RUNTIME_CORE": ("SRU callsign", "Syvial is deputy leader", "short black bob", "orange-red"),
        "CHAR.SYVIAL.RUNTIME_CORE": ("deputy leader", "same overall power tier as Kai", "magenta eyes"),
    }
    for record_id, markers in checks.items():
        text = by_id[record_id].get("text", "")
        for marker in markers:
            if marker not in text:
                raise RuntimeError(f"SRU character knowledge sync: {record_id} missing marker {marker!r}")
    for retired in ("Black Blood captain under Vatican", "ARGUS is her Black Blood callsign"):
        if retired in json.dumps(verified, ensure_ascii=False):
            raise RuntimeError("SRU character knowledge sync: retired runtime character story remains: " + retired)


main = MAIN.read_text(encoding="utf-8")
codex = CODEX.read_text(encoding="utf-8").strip()
gun_skills = GUN_SKILLS.read_text(encoding="utf-8").strip()

if SOURCE_MARKER not in codex:
    raise RuntimeError("Kai Codex: wrong or missing source marker")
if len(codex) < 5000:
    raise RuntimeError(f"Kai Codex unexpectedly short: {len(codex)} chars")
for marker in ("1. ĐỊNH DANH", "2. NGOẠI HÌNH", "3. TÍNH CÁCH / NGUYÊN TẮC"):
    if marker not in codex:
        raise RuntimeError("Kai source codex structure missing: " + marker)
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

r08_codex = upgrade_kai_identity_visual(codex)
identity_visual = r08_codex[r08_codex.index("1. ĐỊNH DANH"):r08_codex.index("3. TÍNH CÁCH / NGUYÊN TẮC")]
for marker in ("Đội trưởng SRU", "KNOWLEDGE LOCK", "KHÓA THỊ GIÁC R08", "tóc đen dày", "POLICE / SRU / SPECIAL RESPONSE UNIT"):
    if marker not in identity_visual:
        raise RuntimeError("Kai R08 story/visual upgrade missing: " + marker)
for retired in ("Tổ chức: Vatican", "Black Blood — Huyết Nha", "helmet/faceplate kín đầu", "pauldron lớn hình đầu rồng"):
    if retired in identity_visual:
        raise RuntimeError("Kai R08 story/visual upgrade retained retired marker: " + retired)

combined_codex = current_character_override() + "\n\n" + r08_codex + "\n\n" + gun_skills
java_codex = json.dumps(combined_codex, ensure_ascii=False)
constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = constant_anchor + f"  private static final String KAI_CANON = {java_codex};\n"
main = replace_once(main, constant_anchor, constant_block, "current SRU character canon Java constant")

state_anchor = (
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
state_with_canon = (
    '            "CURRENT CHARACTER CANON R08 dưới đây là HARD LOCK. Nếu DRIVE_CANON, state, model output hoặc dữ liệu legacy xung đột với tổ chức, story, knowledge boundary, ngoại hình, năng lực hay giới hạn cố định của Kai/Iris/Syvial thì ưu tiên KAI_CANON; state chỉ mô tả tình trạng tạm thời có nguyên nhân hợp canon. Không tự nerf Kai, không tự thêm giới hạn ẩn và không tự quyết hành động có chủ ý thay Kai.\\n\\n" +\n'
    '            KAI_CANON + "\\n\\n" +\n'
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
main = replace_once(main, state_anchor, state_with_canon, "current SRU character canon prompt injection")

MAIN.write_text(main, encoding="utf-8")
print(f"Injected {RUNTIME_MARKER}, synced SRU story/visual runtime knowledge, and retained non-conflicting legacy ability references ({len(combined_codex)} chars).")
