from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
STORY = ROOT / "app/src/main/assets/campaign_story/level0-to-level1.json"

story = json.loads(STORY.read_text(encoding="utf-8"))
entry = story.get("entryEvent") or {}
mission = story.get("officialMission") or {}
if entry.get("year") != 2299:
    raise RuntimeError("prologue_authority_year_mismatch")
if entry.get("mode") != "SPATIAL_GATE" or entry.get("sameGate") is not True:
    raise RuntimeError("prologue_authority_same_gate_missing")
if entry.get("entryIntent") != "MISSION" or entry.get("voluntaryMissionEntry") is not True:
    raise RuntimeError("prologue_authority_mission_entry_missing")
if entry.get("allSeparatedOnArrival") is not True or entry.get("arrivalLevelsDifferent") is not True:
    raise RuntimeError("prologue_authority_separation_missing")
if entry.get("participants") != ["kai", "iris", "syvial"]:
    raise RuntimeError("prologue_authority_participants_mismatch")
if mission.get("year") != 2299 or mission.get("unit") != "SRU" or mission.get("subject") != "Async":
    raise RuntimeError("prologue_authority_sru_async_mission_mismatch")

prologue = """Năm 2299.

Khu chuẩn bị nhiệm vụ của SRU chỉ còn tiếng hệ thống kiểm tra thiết bị và tiếng trường năng lượng vọng từ cổng không gian phía trước. Mệnh lệnh đã được chốt trước khi đội tới đây: điều tra hoạt động của Async, xác minh cổng mà chúng đã tiếp cận và đánh giá nguy cơ của Backrooms đối với Frontrooms.

Kai kiểm tra SRU-MK20 và SRU-SG lần cuối. Iris hoàn tất phần kiểm tra trinh sát ở bên trái hắn. Syvial đứng phía còn lại, GodKiller đã sẵn sàng nhưng chưa rời vị trí mang. Không ai trong ba người bước vào vì tai nạn. Đây là một nhiệm vụ chủ động của SRU.

Cổng ổn định.

Kai nhìn Iris và Syvial một lần, nhận tín hiệu sẵn sàng từ cả hai rồi tiến lên. Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ.

Trong khoảnh khắc đầu tiên, Kai vẫn nhìn thấy hai người bên cạnh. Sau đó khoảng cách mất ý nghĩa.

Không có tiếng nổ hay lực va chạm. Hình ảnh Iris lệch khỏi vị trí mà cô vừa đứng. Syvial cũng biến khỏi cùng một hệ quy chiếu. Không gian giữa ba người bị kéo thành những hướng không còn khớp với nhau, rồi mọi điểm tham chiếu tắt cùng lúc.

Cảm giác ấy kéo dài chưa tới một nhịp tim.

Trọng lực trở lại đột ngột.

Kai chạm xuống một tấm thảm ẩm. Hắn hạ trọng tâm theo phản xạ, giữ thăng bằng rồi quan sát ngay thay vì di chuyển tiếp.

Tường vàng nhạt. Hoa văn lặp. Trần ghép ô vuông. Đèn huỳnh quang trắng nhợt và tiếng ù kéo dài không có nguồn kết thúc rõ ràng. Không cửa sổ. Không dấu hiệu của khu chuẩn bị nhiệm vụ SRU.

Iris không ở đây.

Syvial cũng không.

Kai thử kênh liên lạc trực tiếp của đội, rồi kênh SRU và đường truyền về Frontrooms. Tất cả đều ngoại tuyến. Không có dữ kiện nào cho biết Iris hoặc Syvial đã bị đưa tới đâu, chỉ có một điều hắn có thể xác nhận từ khoảnh khắc cuối trước khi mất liên lạc: cả ba đã bị phân tán tới những Level khác nhau.

Kai không gọi tên hai người thêm lần nữa. Hắn ghi nhận tình trạng liên lạc, đánh dấu vị trí ban đầu và bắt đầu đọc môi trường.

Nhiệm vụ điều tra Async vẫn còn hiệu lực. Việc tìm lại Iris và Syvial cũng vậy. Nhưng Kai không có quyền biến nghi ngờ thành bằng chứng: mọi dấu vết về Async, mọi tín hiệu về đồng đội và mọi lối đi tiếp đều phải được kiểm chứng từ những gì thực sự tồn tại trong Backrooms.

Kai bắt đầu một mình tại Level 0."""

index = INDEX.read_text(encoding="utf-8")
start = index.find("const prologue=`")
initial = index.find("const initial={", start)
if start < 0 or initial < 0:
    raise RuntimeError("prologue_authority_index_anchor_missing")
close = index.rfind("`;", start, initial)
if close < 0:
    raise RuntimeError("prologue_authority_closing_anchor_missing")
index = index[:start] + "const prologue=`" + prologue + "`;\n\n" + index[initial:]

prologue_block = index[index.find("const prologue=`"):index.find("const initial={")]
for required in (
    "Năm 2299.",
    "nhiệm vụ chủ động của SRU",
    "điều tra hoạt động của Async",
    "Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ.",
    "cả ba đã bị phân tán tới những Level khác nhau",
    "Kai bắt đầu một mình tại Level 0",
):
    if required not in prologue_block:
        raise RuntimeError("prologue_authority_required_marker_missing:" + required)
for obsolete in (
    "Bữa tối bắt đầu như bao lần khác.",
    "Nhà hàng nằm trên một tầng cao",
    "Chiếc ly rơi xuống",
    "sàn nhà hàng",
    "Nhà hàng đã biến mất",
    "Black Blood",
    "no-clip",
    "Hứa Thuý Lan",
    "2267",
):
    if obsolete in prologue_block:
        raise RuntimeError("obsolete_prologue_survived:" + obsolete)
INDEX.write_text(index, encoding="utf-8")

knowledge = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = {
    str(record.get("id")): record
    for record in knowledge.get("records", [])
    if isinstance(record, dict) and str(record.get("id") or "").strip()
}
for required_id in ("STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"):
    if required_id not in records:
        raise RuntimeError("prologue_authority_knowledge_record_missing:" + required_id)

records["STORY.MAIN.OBJECTIVE"]["text"] = (
    "In 2299, Kai's active campaign objectives are to carry out the SRU investigation of Async and the Backrooms risk, "
    "survive and learn enough local rules to keep progressing, and find Iris and Syvial after the team is dispersed. "
    "Async traces, teammate locations, escape routes and Backrooms origin claims require real discovered evidence; the mission brief alone proves none of them."
)
records["STORY.MAIN.SEPARATION"]["text"] = (
    "In 2299, Kai, Iris and Syvial are SRU members on an intentional mission to investigate Async. All three voluntarily cross the same spatial gate into Backrooms, "
    "where the transition disperses them to different Levels. Kai starts alone at Level 0. Direct team links, SRU communications and Frontrooms links are offline, "
    "and Kai does not know Iris's or Syvial's location. Their eventual reunions are story-owned continuity events, never random companion spawns or narrator inventions."
)

combined_story_knowledge = "\n".join(
    str(records[record_id].get("text") or "")
    for record_id in ("STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION")
)
for required in ("2299", "SRU", "Async", "same spatial gate", "different Levels", "Level 0"):
    if required not in combined_story_knowledge:
        raise RuntimeError("prologue_authority_knowledge_marker_missing:" + required)
for obsolete in ("shared no-clip event", "Black Blood/Command", "Hứa Thuý Lan", "2267"):
    if obsolete in combined_story_knowledge:
        raise RuntimeError("obsolete_story_knowledge_survived:" + obsolete)

KNOWLEDGE.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Story prologue authority finalized: 2299 / SRU / Async / same-gate separation.")
