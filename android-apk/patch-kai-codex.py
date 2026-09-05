from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CODEX = ROOT / "kai-codex-r10.txt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
codex = CODEX.read_text(encoding="utf-8").strip()

if "KAI-AKECHI-TWILIGHT-CODEX-20260902-R10" not in codex:
    raise RuntimeError("Kai Codex: wrong or missing R10 source marker")
if len(codex) < 5000:
    raise RuntimeError(f"Kai Codex unexpectedly short: {len(codex)} chars")

java_codex = json.dumps(codex, ensure_ascii=False)
constant_anchor = "  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n"
constant_block = constant_anchor + f"  private static final String KAI_CANON = {java_codex};\n"
main = replace_once(main, constant_anchor, constant_block, "Kai canon Java constant")

state_anchor = (
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
state_with_canon = (
    '            "KAI CANON R10 dưới đây là HARD LOCK. Nếu state hoặc model output cũ xung đột với danh tính, tổ chức, năng lực, trang bị, tính cách hay giới hạn cố định của Kai thì ưu tiên KAI_CANON; state chỉ mô tả tình trạng tạm thời có nguyên nhân hợp canon. Giữ đúng KNOWLEDGE LOCK: bí mật Sparda/Eve không tự trở thành kiến thức của nhân vật. Không tự nerf Kai, không tự thêm giới hạn ẩn và không tự quyết hành động có chủ ý thay Kai.\\n\\n" +\n'
    '            KAI_CANON + "\\n\\n" +\n'
    '            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n'
)
main = replace_once(main, state_anchor, state_with_canon, "Kai canon prompt injection")

MAIN.write_text(main, encoding="utf-8")
print(f"Injected Kai R10 operational codex into APK Game Master prompt ({len(codex)} chars).")
