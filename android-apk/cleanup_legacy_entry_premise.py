from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
INDEX = ROOT / "app/src/main/assets/index.html"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
PATCH_MAIN = ROOT / "patch-main-story-level0-1.py"
PATCH_KAI = ROOT / "patch-kai-codex.py"
PATCH_KAI_R08 = ROOT / "patch-kai-r08-knowledge-final.py"
SELF = Path(__file__)
WORKFLOW = REPO / ".github/workflows/cleanup-legacy-entry-premise.yml"

PROLOGUE = """Năm 2299.

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

OBJECTIVE = (
    "In 2299, Kai's active campaign objectives are to carry out the SRU investigation of Async and the Backrooms risk, "
    "survive and learn enough local rules to keep progressing, and find Iris and Syvial after the team is dispersed. "
    "Async traces, teammate locations, escape routes and Backrooms origin claims require real discovered evidence; the mission brief alone proves none of them."
)
SEPARATION = (
    "In 2299, Kai, Iris and Syvial are SRU members on an intentional mission to investigate Async. All three voluntarily cross the same spatial gate into Backrooms, "
    "where the transition disperses them to different Levels. Kai starts alone at Level 0. Direct team links, SRU communications and Frontrooms links are offline, "
    "and Kai does not know Iris's or Syvial's location. Their eventual reunions are story-owned continuity events, never random companion spawns or narrator inventions."
)


def rewrite_index() -> None:
    text = INDEX.read_text(encoding="utf-8")
    start = text.find("const prologue=`")
    initial = text.find("const initial={", start)
    if start < 0 or initial < 0:
        raise RuntimeError("legacy_cleanup_index_prologue_anchor_missing")
    text = text[:start] + "const prologue=`" + PROLOGUE + "`;\n\n" + text[initial:]
    clean_location = 'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng nhiệm vụ",'
    text, count = re.subn(
        r'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau [^"]+",',
        clean_location,
        text,
        count=1,
    )
    if count != 1 and clean_location not in text:
        raise RuntimeError("legacy_cleanup_location_anchor_missing")
    for forbidden in ("Bữa tối", "Nhà hàng", "sàn nhà hàng", "Black Blood", "no-clip"):
        if forbidden in text[text.find("const prologue=`"):text.find("const initial={")]:
            raise RuntimeError("legacy_cleanup_prologue_survived:" + forbidden)
    INDEX.write_text(text, encoding="utf-8")


def rewrite_knowledge() -> None:
    data = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
    records = {
        str(record.get("id")): record
        for record in data.get("records", [])
        if isinstance(record, dict) and str(record.get("id") or "").strip()
    }
    for required in ("STORY.MAIN.OBJECTIVE", "STORY.MAIN.SEPARATION"):
        if required not in records:
            raise RuntimeError("legacy_cleanup_knowledge_record_missing:" + required)
    records["STORY.MAIN.OBJECTIVE"]["text"] = OBJECTIVE
    records["STORY.MAIN.SEPARATION"]["text"] = SEPARATION
    combined = OBJECTIVE + "\n" + SEPARATION
    for forbidden in ("shared no-clip", "Black Blood/Command", "2267", "Hứa Thuý Lan"):
        if forbidden in combined:
            raise RuntimeError("legacy_cleanup_knowledge_survived:" + forbidden)
    KNOWLEDGE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def rewrite_character_patches() -> None:
    old = "After the shared no-clip event, Kai, Iris and Syvial land apart."
    new = "In 2299, Kai, Iris and Syvial voluntarily cross the same spatial gate on an SRU mission to investigate Async, then land apart."
    for path in (PATCH_KAI, PATCH_KAI_R08):
        text = path.read_text(encoding="utf-8")
        if old in text:
            text = text.replace(old, new)
        if "shared no-clip" in text:
            raise RuntimeError("legacy_cleanup_character_patch_survived:" + path.name)
        path.write_text(text, encoding="utf-8")


def rewrite_main_story_patch() -> None:
    text = PATCH_MAIN.read_text(encoding="utf-8")
    start = text.find("# New Game opening:")
    end = text.find('main = MAIN.read_text(encoding="utf-8")', start)
    if start < 0 or end < 0:
        raise RuntimeError("legacy_cleanup_main_story_patch_anchor_missing")
    replacement = '''# New Game opening is source-clean: the checked-in seed already carries the SRU / Async mission premise.\nindex = INDEX.read_text(encoding="utf-8")\nprologue_start = index.find("const prologue=`")\ninitial_start = index.find("const initial={", prologue_start)\nif prologue_start < 0 or initial_start < 0:\n    raise RuntimeError("main_story_source_clean_prologue_anchor_missing")\nportal_scene = r\'''Năm 2299.\n\nCổng không gian trước mặt đội SRU đã ổn định đủ lâu để bắt đầu nhiệm vụ. Lệnh điều tra chỉ rõ mục tiêu: tiến vào, xác minh hoạt động của Async và đánh giá nguy cơ của Backrooms đối với Frontrooms.\n\nKai kiểm tra lần cuối trang bị. Iris và Syvial đã sẵn sàng ở hai bên. Không ai bị kéo vào ngoài ý muốn. Cả ba chủ động bước qua cùng một cổng không gian theo lệnh nhiệm vụ.\n\nKai vẫn nhìn thấy Iris và Syvial khi vượt qua ranh giới. Rồi khoảng cách giữa ba người mất ý nghĩa. Backrooms phân tán họ tới những Level khác nhau; Kai không biết hai người còn lại đã bị đưa tới đâu.\n\nCảm giác chuyển tiếp kéo dài chưa tới một nhịp tim. Trọng lực trở lại đột ngột.\n\nKai bắt đầu một mình tại Level 0. Nhiệm vụ điều tra Async vẫn còn hiệu lực, nhưng mission brief không tự biến bất kỳ dấu vết nào trong Backrooms thành bằng chứng.\'''\nindex = index[:prologue_start] + "const prologue=`" + portal_scene + "`;\\n\\n" + index[initial_start:]\nclean_location = 'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau khi đi qua cổng nhiệm vụ",'\nindex, location_count = re.subn(\n    r'location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau [^\"]+",',\n    clean_location,\n    index,\n    count=1,\n)\nif location_count != 1 and clean_location not in index:\n    raise RuntimeError("main_story_source_clean_location_anchor_missing")\n\n# Later UI patches add fields inside flags, so mutate only the communication prefix.\ninitial_start = index.find("const initial={")\ninitial_end = index.find("log:[", initial_start)\nif initial_start < 0 or initial_end < 0:\n    raise RuntimeError("main_story_initial_state_anchor_missing")\ninitial_slice = index[initial_start:initial_end]\nmatch = re.search(r'flags:\\{communication:\\{[^}]*\\}', initial_slice)\nif not match:\n    raise RuntimeError("main_story_initial_communication_missing")\ncomm = match.group(0)\nif 'sruForce:"OFFLINE"' not in comm:\n    comm = comm[:-1] + ',sruForce:"OFFLINE"}'\nif 'frontrooms:"OFFLINE"' not in comm:\n    comm = comm[:-1] + ',frontrooms:"OFFLINE"}'\ninsertion = (\n    ',entryEvent:{year:2299,mode:"SPATIAL_GATE",intent:"MISSION",voluntary:true,sameGate:true,allSeparatedOnArrival:true,arrivalLevelsDifferent:true}'\n    ',iris:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}'\n    ',syvial:{exists:true,present:false,continuity:"SEPARATED",locationKnownToKai:false}'\n)\nreplacement = comm + insertion\nabsolute_start = initial_start + match.start()\nabsolute_end = initial_start + match.end()\nindex = index[:absolute_start] + replacement + index[absolute_end:]\nINDEX.write_text(index, encoding="utf-8")\n\n'''
    text = text[:start] + replacement + text[end:]
    for forbidden in ("Không ai no-clip", "shared no-clip"):
        if forbidden in text:
            raise RuntimeError("legacy_cleanup_main_story_patch_survived:" + forbidden)
    PATCH_MAIN.write_text(text, encoding="utf-8")


rewrite_index()
rewrite_knowledge()
rewrite_character_patches()
rewrite_main_story_patch()

# One-shot helper: remove the scaffolding so the PR contains only the real source cleanup.
if WORKFLOW.exists():
    WORKFLOW.unlink()
if SELF.exists():
    SELF.unlink()

print("Legacy dinner / accidental-entry premise removed from active source producers.")
