from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
LEDGER = ROOT / "app/src/main/assets/campaign_story/hourly-story-evolution.json"
EXPECTED_SEQUENCE = [
    "PROLOGUE", "0", "epsilon", "0.01", "0.1", "0.11", "0.22", "0.23",
    "0.41", "0.5", "0.66", "0.7", "0.8", "0.99", "LS-2", "Dullness", "Red Rooms", "1",
]

data = json.loads(LEDGER.read_text(encoding="utf-8"))
if data.get("sequence") != EXPECTED_SEQUENCE:
    raise SystemExit("hourly story sequence drifted")
completed = data.get("completedSteps") or []
if completed != EXPECTED_SEQUENCE[: len(completed)]:
    raise SystemExit("completedSteps is not a sequence prefix")
if data.get("nextStep") != "0.7" or completed != EXPECTED_SEQUENCE[:11]:
    raise SystemExit(f"expected nextStep 0.7 after 0.66, got {data.get('nextStep')} / {completed}")
steps = data.get("steps") or {}
if "0.7" in steps:
    raise SystemExit("0.7 already exists")

steps["0.7"] = {
    "phase": "CONSTRAINED_MOVEMENT",
    "storyUpdate": "Khi Core đã commit chuyển vùng vào 0.7 / Claustrophobia, áp lực đổi từ nhiệt độ sang hình học bó hẹp: hành lang thu lại, góc quay ngắn dần, trần hoặc vách tiến gần tới mức việc xoay người, đổi đội hình và sử dụng vũ khí dài phải được cân nhắc theo khoảng trống thật. Kai không bị viết thành hoảng loạn chỉ vì tên khu vực; hắn đọc độ vang, luồng khí, khoảng hở, tải trọng bề mặt và khả năng quay đầu để phân biệt một đoạn chật nhưng còn thoát được với một nhánh có nguy cơ kẹt. SRU-MK20, phản xạ, sức mạnh và năng lực của hắn vẫn tồn tại đầy đủ, nhưng kích thước giáp, góc thao tác và kết cấu chưa biết khiến phá vách hoặc lao thẳng không tự động là phương án tốt. Nếu Lucia đang ở party sau identity/join gate, cô giữ khoảng cách đủ để không bị ép sát vào Kai hoặc mất đường lùi, báo đúng những điểm cô quan sát được và tự quyết định giới hạn cơ thể người thường của mình; cô không bị biến thành người cần được kéo đi chỉ để tăng căng thẳng. Cả hai ưu tiên các tuyến có luồng khí, độ vang và khoảng xoay trở tốt hơn, kiểm tra điểm nghẽn trước khi đưa toàn thân qua. Chỉ khi Core commit transition sang 0.8, nước bắt đầu chiếm phần lớn mặt sàn và thay đổi cách di chuyển mới được khóa như dấu sang Inundation.",
    "survivalFocus": "Không gian hẹp biến mọi hao mòn cũ thành chi phí cơ học cụ thể: mệt, khát, lạnh còn dư, quần áo hoặc giày ẩm, đau cơ, thương tích và nhiễm bẩn làm việc cúi, bò, xoay vai hoặc lùi khỏi điểm nghẽn khó hơn theo state thật. Lucia chịu giới hạn con người rõ ràng nhưng không tự được gán cơn hoảng sợ, chấn thương hay mất bình tĩnh nếu Core chưa surface; Kai có thể chịu lực và phản ứng tốt hơn nhưng không được dùng sức mạnh như lý do bỏ qua nguy cơ sập, mắc giáp hoặc chặn đường đồng đội. Đội hình phải giữ một hướng rút có thể thực hiện, tránh cả hai cùng chui qua một choke point chưa kiểm tra, quản lý nhịp thở và thời gian trong đoạn bó hẹp, và rút ra khi luồng khí, độ vang hoặc khoảng xoay cho thấy nhánh đang xấu đi. Căng thẳng đến từ giới hạn không gian và hậu quả thật của một lựa chọn sai, không từ việc làm nhân vật quên kỹ năng hay trang bị.",
    "npcPolicy": "Không sinh survivor/NPC để làm hoa tiêu qua khe hẹp, chứng minh một đường tắt an toàn, trấn an claustrophobia hoặc dẫn thẳng sang 0.8. Nếu Core/RNG đã commit encounter, người đó phải có nhu cầu sinh tồn cụ thể như thoát khỏi điểm kẹt, tìm tuyến rộng hơn, nghỉ vì kiệt sức hoặc tránh quay lại một choke point; tri thức của họ chỉ giới hạn ở đoạn đã trải qua và có thể không còn đúng khi topology đổi. Lời kể về Entity, Async, Iris, Syvial, lối ra hoặc nguyên nhân khiến hành lang hẹp không tự trở thành sự thật đã kiểm chứng. Lucia vẫn là fixed/story-owned companion nếu đã gia nhập hợp lệ; bước này không bypass identity/join gate và không ép reunion khác.",
    "itemPolicy": "Không tự sinh crowbar, dây, đèn nhỏ, bình oxy, mặt nạ, công cụ cắt, vật chống kẹt, nước, thức ăn, Almond Water, pin hoặc đạn để giải điểm nghẽn đúng lúc. Chỉ dùng vật phẩm và trang bị đang thật sự có trong state, theo đúng chức năng đã khóa; mọi item mới chỉ được kể là tìm thấy, nhận hoặc nhặt khi Core/world-loot/inventory đã commit đúng item trong runtime catalog. Một khe hở, luồng khí hoặc kết cấu lộ ra là dữ kiện môi trường chứ không phải loot. Nếu một trang bị dài hoặc cồng kềnh gây hạn chế thao tác trong hành lang hẹp, xử lý bằng tư thế và lựa chọn tuyến hợp lý chứ không âm thầm làm nó biến mất khỏi inventory.",
    "relationshipStage": "PROTECTIVE_SPACING_WITH_AWARENESS_UNFORCED. Chỉ áp dụng nếu Lucia đang thực sự đồng hành. Không gian chật không được dùng làm cái cớ ép tiếp xúc cơ thể hoặc đẩy romance bằng proximity bắt buộc. Lucia có thể tiếp tục ưu tiên vị trí giúp cả hai giữ đường lùi, báo điểm nghẽn trước cho Kai hoặc điều chỉnh nhịp theo tình trạng của hắn như một phần của mẫu quan tâm đã tích lũy; Kai đã có đủ continuity để xem khả năng cô dành cho mình tình cảm riêng là một giả thuyết hợp lý, nhưng vẫn không đọc ý nghĩ, không thử ép xác nhận và không biến việc đứng gần trong hành lang hẹp thành bằng chứng. Kai cũng bảo vệ không gian thao tác và đường rút của Lucia mà không quyết định hộ cô. Không thú nhận, không ghen, không thử lòng, không therapist hóa; xưng hô vẫn OPEN.",
    "endingState": "0.7 được khóa như bước constrained movement: độ chật, góc xoay, luồng khí, độ vang và khả năng quay lui quyết định tuyến; tên Claustrophobia không tự tạo trạng thái tâm lý cho nhân vật. Kai giữ nguyên năng lực và trang bị, Lucia giữ quyền tự chủ cùng giới hạn con người, và mọi hao mòn chỉ tồn tại theo state đã surface. Vị trí Iris/Syvial vẫn UNKNOWN; không có bằng chứng Async, Entity, survivor hay cơ chế mới nếu Core/state chưa surface. Khi và chỉ khi Core commit transition sang 0.8, nước bắt đầu chiếm phần lớn mặt sàn; continuity kế tiếp duy nhất là 0.8 / Inundation, không nhảy Level 1 và không sửa route, quest, RNG, loot hoặc companion gates để ép tiến trình."
}
data["completedSteps"] = completed + ["0.7"]
data["nextStep"] = "0.8"
data["steps"] = steps
LEDGER.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Applied exactly one hourly story step: 0.7 / Claustrophobia -> next 0.8")
