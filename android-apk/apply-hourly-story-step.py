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
if data.get("nextStep") != "LS-2" or completed != EXPECTED_SEQUENCE[:14]:
    raise SystemExit(f"expected nextStep LS-2 after 0.99, got {data.get('nextStep')} / {completed}")
steps = data.get("steps") or {}
if "LS-2" in steps:
    raise SystemExit("LS-2 already exists")

steps["LS-2"] = {
    "phase": "INTERFACE_ZONE",
    "storyUpdate": "Khi Core đã commit chuyển vùng từ 0.99 vào LS-2, cốt truyện không được biến cái tên kỹ thuật của khu vực thành kiến thức nội thế giới hay giải thích LS-2 là một cỗ máy, cổng điều khiển hoặc tầng trung chuyển có chủ đích. Điều đáng sợ ở đây là quan hệ giữa các ngưỡng bắt đầu kém đáng tin hơn chính bề mặt của chúng: một cửa, khe mở, đoạn tường hoặc khoảng chuyển có thể trông quen nhưng vị trí tương đối, hướng âm, luồng khí, độ sáng, dấu ẩm hoặc thứ tự vật mốc sau khi đi qua chỉ có giá trị khi được đối chiếu lại. Kai kiểm từng threshold như hiện trường: ghi trạng thái trước khi vượt, giữ điểm quay lui gần, so thời gian, hướng, vật liệu và cue cảm biến sau khi vượt, rồi hạ mức chắc chắn nếu hai lần kiểm không cho cùng quan hệ. Nếu một dấu cũ xuất hiện ở phía không phù hợp, một âm vang trở lại từ hướng sai hoặc một lối vừa đi qua không còn nối đúng như trước, đó là bằng chứng topology cục bộ đã lệch chứ không phải bằng chứng Backrooms có ý chí, Async đang điều khiển khu vực hay Entity đang cố dẫn họ. Nếu Lucia đang ở party sau identity/join gate, cô giữ vai trò một người lính được huấn luyện tốt: không tự tách qua ngưỡng chưa kiểm, báo chính xác điều cô thấy thay vì nói chắc nguyên nhân, đối chiếu ký ức của mình với dấu vật lý và chấp nhận sửa nhận định khi bằng chứng không khớp. Cả hai không cố 'giải mã' LS-2 thành một câu trả lời lớn; mục tiêu chỉ là xác định chuỗi threshold nào còn giữ đủ continuity để tiếp tục. Chỉ khi Core commit transition sang Dullness, màu sắc và chi tiết cảm nhận mới được phép phẳng dần theo motif bước kế tiếp.",
    "survivalFocus": "LS-2 làm chi phí của sai định hướng trở nên rõ hơn: quay nhầm qua một threshold có thể tiêu thêm thời gian, nước, sức tập trung và khả năng giữ đường rút ngay cả khi không có combat. Mệt, khát, đói, lạnh hoặc ẩm còn dư, thương tích, nhiễm bẩn và thiếu ngủ chỉ tồn tại theo state đã surface nhưng phải tiếp tục ảnh hưởng tốc độ kiểm chứng, độ chính xác thao tác và mức chịu đựng việc phải quay lại kiểm một tuyến. Kai giữ nguyên toàn bộ năng lực, cảm biến và trang bị đã khóa; hắn dùng chúng để giảm sai số chứ không được bịa chức năng xác định tuyệt đối vị trí, nguồn gốc hay topology. Lucia vẫn có giới hạn người thường, vì vậy đội hình ưu tiên vượt từng ngưỡng một, xác nhận người còn lại đã qua an toàn trước khi bỏ điểm quay lui, tránh để cả hai cùng mất reference và dừng lại ở điểm chắc gần nhất nếu chuỗi cue bắt đầu mâu thuẫn. Suspense đến từ việc một ngưỡng nhìn hoàn toàn bình thường vẫn có thể làm quan hệ không gian trước-sau mất độ tin cậy, không từ việc nhân vật quên kiểm tra điều hiển nhiên hay hoảng loạn trái tính cách.",
    "npcPolicy": "Không sinh survivor/NPC để giải thích LS-2, gọi tên cơ chế của khu vực, đưa bản đồ threshold đáng tin tuyệt đối hoặc dẫn thẳng sang Dullness. Nếu Core/RNG đã commit encounter, người đó phải có nhu cầu cụ thể như tìm lại điểm quay lui, xác nhận một lối từng nối được, bảo toàn nước/đồ khô hoặc thoát khỏi vòng đi lặp; lời kể của họ chỉ là dữ kiện cục bộ theo thời điểm. Một survivor nói rằng một cánh cửa 'luôn dẫn đúng', rằng LS-2 là cổng, rằng Backrooms đang thử họ, hoặc rằng đã thấy Async/Iris/Syvial/Entity không tự trở thành sự thật đã xác minh. Lucia vẫn là fixed/story-owned companion nếu đã gia nhập hợp lệ; bước này không bypass identity/join gate và không ép reunion khác.",
    "itemPolicy": "Không tự sinh chalk, dây đánh dấu, beacon, bản đồ, la bàn mới, đèn, pin, camera, cảm biến chuyên dụng, nước, thức ăn, Almond Water, đạn hoặc vật phẩm cứu nguy để giải threshold đúng lúc. Chỉ dùng vật phẩm và trang bị đang thật sự có trong state theo đúng chức năng đã khóa; mọi item mới chỉ được kể là tìm thấy, nhận hoặc nhặt khi Core/world-loot/inventory đã commit đúng item trong runtime catalog. Dấu trên tường, độ ẩm, tiếng vọng, hướng gió, thứ tự vật mốc và sai biệt vị trí là dữ kiện môi trường, không phải loot. Không âm thầm hồi tài nguyên, reset hao mòn hoặc biến metadata kỹ năng thành nguồn vật tư vô hạn.",
    "relationshipStage": "TRUST_UNDER_THRESHOLD_UNCERTAINTY_HELD. Chỉ áp dụng nếu Lucia đang thực sự đồng hành. Sau 0.99, Kai đã có đủ continuity để xem khả năng Lucia dành cho mình tình cảm riêng là một giả thuyết đáng kể; LS-2 chủ ý không đẩy nó thành kết luận hay lời thú nhận. Trong một khu vực nơi chính quan hệ trước-sau có thể sai lệch, sự tin cậy nên hiện ra qua hành vi thực tế: Lucia có thể chờ Kai xác nhận cùng một mismatch trước khi tự gán nguyên nhân, Kai có thể chấp nhận sửa route khi quan sát của cô tốt hơn, và cả hai giữ nhau trong chuỗi kiểm chứng mà không biến việc ở gần thành cớ romance. Kai không giả mù trước mẫu quan tâm đã tích lũy nhưng cũng không thử lòng, ép xác nhận hay bảo bọc quá mức; Lucia không mất kỷ luật chỉ vì tình cảm. Không ghen, không therapist hóa, không dùng nguy hiểm để ép tiếp xúc thân mật và xưng hô vẫn OPEN.",
    "endingState": "LS-2 được khóa như bước threshold/interface uncertainty: bề mặt có thể trông quen nhưng quan hệ không gian qua ngưỡng chỉ được tin sau kiểm chứng; một sai biệt không chứng minh Backrooms có ý chí, không chứng minh Async điều khiển khu vực và không tự là dấu Entity. Kai giữ nguyên năng lực/trang bị mà không được bịa khả năng định vị tuyệt đối; Lucia giữ quyền tự chủ cùng giới hạn con người; mọi hao mòn và tài nguyên tiếp tục theo state thật. Vị trí Iris/Syvial vẫn UNKNOWN; không có bằng chứng Async, Entity, survivor, loot hoặc cơ chế mới nếu Core/state chưa surface. Khi và chỉ khi Core commit transition sang Dullness, continuity kế tiếp duy nhất là Dullness / Perceptual Flattening; không nhảy Red Rooms hay Level 1 và không sửa route, quest, RNG, loot hoặc companion gates để ép tiến trình."
}

data["completedSteps"] = completed + ["LS-2"]
data["nextStep"] = "Dullness"
data["steps"] = steps
LEDGER.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Applied exactly one hourly story step: LS-2 -> next Dullness")
