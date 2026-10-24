from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CODEX = ROOT / "kai-codex.txt"
GUN_SKILLS = ROOT / "kai-gun-skills.txt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
codex = CODEX.read_text(encoding="utf-8").strip()
gun_skills = GUN_SKILLS.read_text(encoding="utf-8").strip()

if "CAO MINH — VẠN GIỚI MA TÔN — OPERATIONAL CODEX" not in codex:
    raise RuntimeError("Cao Minh Codex: wrong or missing R05 source marker")
if len(codex) < 5000:
    raise RuntimeError(f"Kai Codex unexpectedly short: {len(codex)} chars")
for marker in (
    "HUYẾT MA TỨ LIÊN",
    "MA TÂM TRẤN HỒN",
    "HUYẾT ẢNH MA ĐỘN",
    "THIÊN MA BỘ",
    "+50 điểm phần trăm Evasion",
):
    if marker not in gun_skills:
        raise RuntimeError("Cao Minh sword-skill addendum missing: " + marker)

combined_codex = codex + "\n\n" + gun_skills
java_codex = json.dumps(combined_codex, ensure_ascii=False)
constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = constant_anchor + f"  private static final String CAO_MINH_CANON = {java_codex};\n"
main = replace_once(main, constant_anchor, constant_block, "Cao Minh canon Java constant")

state_anchor = (
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
state_with_canon = (
    '            "CAO MINH CANON dưới đây là HARD LOCK. Nếu state hoặc model output cũ xung đột với danh tính, năng lực, trang bị, tính cách hay giới hạn cố định của Cao Minh thì ưu tiên CAO_MINH_CANON; state chỉ mô tả tình trạng tạm thời có nguyên nhân hợp canon. Không tự nerf Cao Minh, không tự thêm giới hạn ẩn và không tự quyết hành động có chủ ý thay Cao Minh.\\n\\n" +\n'
    '            CAO_MINH_CANON + "\\n\\n" +\n'
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
main = replace_once(main, state_anchor, state_with_canon, "Cao Minh canon prompt injection")

MAIN.write_text(main, encoding="utf-8")
print(f"Injected Cao Minh operational codex plus automatic sword-skill addendum into APK Game Master prompt ({len(combined_codex)} chars).")
