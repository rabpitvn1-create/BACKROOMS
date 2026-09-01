from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE = ROOT / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
KNOWLEDGE_DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# Route the existing WRITING.DIALOGUE authority whenever speech is explicit OR a
# companion is present and the GM may naturally generate dialogue. The existing
# direct structured lookup for explicit speech remains intact; this closes the
# false-negative path where dialogue can arise from scene context/presence alone.
knowledge = KNOWLEDGE.read_text(encoding="utf-8")
dialogue_anchor = '''      if (hasAny(sceneText, "bị thương", "injury", "vết thương", "sơ cứu", "medical")) affordances += "field_medical"
      if (hasAny(sceneText, "thức ăn", "nấu", "food", "cooking")) affordances += "field_food"

      affordances.forEach { affordance ->
'''
dialogue_replacement = '''      if (hasAny(sceneText, "bị thương", "injury", "vết thương", "sơ cứu", "medical")) affordances += "field_medical"
      if (hasAny(sceneText, "thức ăn", "nấu", "food", "cooking")) affordances += "field_food"

      val party = state.optJSONArray("party")
      if ((party != null && party.length() > 0) ||
          hasAny(sceneText, "nói", "hỏi", "trả lời", "trò chuyện", "nói chuyện", "gọi", "bảo", "xin lỗi", "cảm ơn", "dialogue", "talk", "tell", "ask", "answer", "reply")) {
        affordances += "dialogue"
      }

      affordances.forEach { affordance ->
'''
knowledge = replace_once(knowledge, dialogue_anchor, dialogue_replacement, "dialogue scene affordance routing")

# Keep prose guidance short and presentation-only. It cannot add facts or mutate
# gameplay; it only tells the existing writer how to express already-grounded output.
main = MAIN.read_text(encoding="utf-8")
prose_anchor = '      "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc database/context/state/roll/prompt trong văn xuôi.\\n\\n" +\n'
prose_replacement = prose_anchor + (
    '      "PROSE RULE: Văn xuôi gameplay phải là tiếng Việt tự nhiên, cụ thể và bám vào trải nghiệm hiện tại; ưu tiên quan sát, hành động và hậu quả hơn câu xác nhận trừu tượng hoặc kiểu hệ thống. Tránh chuỗi câu cụt điện ảnh, lặp ý, giải thích lại điều vừa thể hiện và exposition không cần thiết. Không dùng văn phong để thêm hoặc đổi dữ kiện gameplay/canon đã xác định.\\n\\n" +\n'
)
main = replace_once(main, prose_anchor, prose_replacement, "short gameplay prose rule")

# Fail closed if the packaged knowledge asset no longer maps dialogue to the
# canonical WRITING.DIALOGUE record. This is a contract check, not a new runtime path.
db = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
dialogue_record = next((record for record in db.get("records", []) if record.get("id") == "WRITING.DIALOGUE"), None)
if dialogue_record is None or "dialogue" not in dialogue_record.get("affordances", []):
    raise RuntimeError("WRITING.DIALOGUE must remain mapped to the dialogue affordance")

for marker in (
    'affordances += "dialogue"',
    'party != null && party.length() > 0',
    '"xin lỗi", "cảm ơn"',
):
    if marker not in knowledge:
        raise RuntimeError("dialogue routing marker missing: " + marker)

prose_marker = "PROSE RULE: Văn xuôi gameplay phải là tiếng Việt tự nhiên, cụ thể và bám vào trải nghiệm hiện tại"
if prose_marker not in main:
    raise RuntimeError("short gameplay prose rule missing")

KNOWLEDGE.write_text(knowledge, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Dialogue/prose refinement applied: WRITING.DIALOGUE routes on speech or companion presence; short grounded prose rule enabled.")
