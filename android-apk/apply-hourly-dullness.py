from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
PATH = ROOT / "app/src/main/assets/campaign_story/hourly-story-evolution.json"

data = json.loads(PATH.read_text(encoding="utf-8"))
sequence = data.get("sequence") or []
completed = data.get("completedSteps") or []
expected_prefix = ["PROLOGUE", "0", "epsilon", "0.01", "0.1", "0.11", "0.22", "0.23", "0.41", "0.5", "0.66", "0.7", "0.8", "0.99", "LS-2"]
if completed != expected_prefix:
    raise SystemExit(f"unexpected completedSteps: {completed}")
if data.get("nextStep") != "Dullness":
    raise SystemExit(f"unexpected nextStep: {data.get('nextStep')}")
if sequence[:len(expected_prefix) + 1] != expected_prefix + ["Dullness"]:
    raise SystemExit("Dullness is not the next canonical route step")
if "Dullness" in (data.get("steps") or {}):
    raise SystemExit("Dullness step already exists")

step = {
    "phase": "PERCEPTUAL_FLATTENING",
    "storyUpdate": "Khi Core đã commit chuyển vùng từ LS-2 vào Dullness, màu sắc, độ tương phản và khác biệt cảm giác giữa các phòng bị ép phẳng dần: những mảng vàng vốn lệch sắc trở nên khó phân biệt, mép vật liệu bớt nổi, tiếng huỳnh quang ít biến thiên và các vật mốc cùng loại dễ bị nhớ như một khối giống nhau. Cái tên Dullness không được biến thành chẩn đoán tâm lý, thanh sanity hay bằng chứng rằng Backrooms đang hút cảm xúc. Kai xử lý đây như suy giảm chất lượng quan sát: hắn ưu tiên chuỗi hành động có thể lặp lại, thời điểm, khoảng cách đo được, dấu vật lý còn kiểm chứng được, cảm biến hợp lệ và đối chiếu chéo giữa nhiều cue thay vì dựa vào cảm giác 'phòng này quen'. Ký ức về Iris hoặc Syvial có thể bị một bố cục nhạt màu gợi lại, nhưng ký ức chỉ là ký ức và không trở thành tín hiệu vị trí. Nếu Lucia đang thực sự ở party sau identity/join gate, cô giữ đúng giới hạn con người: báo phần mình chắc chắn nhìn thấy, phân biệt 'không nhận ra khác biệt' với 'hai nơi giống hệt nhau', không tự bù khoảng trống bằng phỏng đoán và phối hợp với Kai để kiểm tra lại thứ tự mốc. Chỉ khi Core commit transition sang Red Rooms, sắc đỏ và hình học kín mới được phép thay thế dần vùng cảm nhận phẳng này; Dullness không tự nhảy Level 1.",
    "survivalFocus": "Dullness làm sai định hướng rẻ về cảm giác nhưng đắt về tài nguyên: khi hai đoạn hành lang trông gần như nhau, một lần quay nhầm có thể tiêu thêm thời gian, nước, sức tập trung, nhiệt cơ thể và đường quay lui. Đói, khát, mệt, thiếu ngủ, thương tích, ẩm/lạnh còn dư và nhiễm bẩn chỉ tiếp tục nếu state đã surface, nhưng phải có trọng lượng thật trong tốc độ kiểm tra và độ chính xác thao tác. Kai không bị tước năng lực, trí nhớ hay cảm biến; hắn dùng toàn bộ công cụ đã khóa để giảm sai số, nhưng không được bịa chức năng tạo bản đồ tuyệt đối hoặc phục hồi tương phản môi trường. Lucia vẫn là con người được huấn luyện tốt, nên nếu hao mòn tồn tại cô có thể cần nhịp kiểm chậm hơn, nghỉ hợp lý hoặc xác nhận mốc gần hơn mà không bị viết thành bất lực. Suspense đến từ việc thông tin thị giác và cảm giác trở nên ít phân biệt, không từ việc hai nhân vật bỏ qua kiểm tra hiển nhiên hoặc hoảng loạn trái tính cách.",
    "npcPolicy": "Không sinh survivor/NPC để gọi tên Dullness, giải thích cơ chế, cung cấp bản đồ đáng tin tuyệt đối, xác nhận một phòng 'đúng' hay dẫn thẳng sang Red Rooms. Nếu Core/RNG đã commit encounter, người đó phải có nhu cầu sinh tồn cụ thể như tìm lại mốc, tránh đi vòng, giữ nước/thức ăn, xử lý kiệt sức hoặc quay về một đoạn họ từng xác nhận; lời kể của họ chỉ là thông tin cục bộ theo thời điểm. Việc một survivor nói đã thấy Iris, Syvial, Async, Entity hay lối ra không tự trở thành sự thật. Lucia vẫn là fixed/story-owned companion nếu đã gia nhập hợp lệ; bước này không bypass identity/join gate và không ép reunion khác.",
    "itemPolicy": "Không tự sinh sơn đánh dấu, chalk mới, dây, beacon, bản đồ, camera, cảm biến chuyên dụng, đèn, pin, nước, thức ăn, Almond Water, đạn hoặc vật phẩm tăng độ tương phản để giải Dullness. Chỉ dùng item và trang bị đang thật sự có trong state theo đúng chức năng đã khóa; mọi vật phẩm mới chỉ được kể là tìm thấy, nhận hoặc nhặt khi Core/world-loot/inventory đã commit đúng item trong runtime catalog. Dấu vật lý, vết ẩm, cạnh vật liệu, âm thanh, nhiệt độ, thời điểm và thứ tự thao tác là dữ kiện môi trường chứ không phải loot. Không reset hao mòn, hồi tài nguyên hoặc biến skill metadata thành nguồn vật tư.",
    "relationshipStage": "QUIET_RELIANCE_WITH_AWARENESS_HELD. Chỉ áp dụng nếu Lucia đang thực sự đồng hành. Kai đã có đủ continuity để xem khả năng Lucia dành cho mình tình cảm riêng là một giả thuyết đáng kể, nhưng Dullness không được biến sự mơ hồ cảm nhận thành cớ để hắn 'đọc' cảm xúc cô chính xác hơn. Quan hệ có thể hiện qua cách hai người tin vào quan sát đã được kiểm chứng của nhau khi chính môi trường làm mọi thứ khó phân biệt: Lucia có thể chủ động báo một sai biệt nhỏ cô chắc chắn, Kai có thể đổi tuyến khi bằng chứng của cô tốt hơn, và cả hai có thể giữ nhịp phối hợp quen thuộc mà không cần biến nó thành cảnh lãng mạn. Lucia không mất kỷ luật vì tình cảm; Kai không giả mù trước mẫu quan tâm đã tích lũy nhưng không thử lòng, ép xác nhận hay suy diễn một cử chỉ thành thú nhận. Không ghen, không therapist hóa, không ép tiếp xúc thân mật và xưng hô vẫn OPEN.",
    "endingState": "Dullness được khóa như bước perceptual flattening: màu sắc, độ tương phản và độ khác biệt cảm giác giảm độ tin cậy, vì vậy tuyến chỉ được giữ bằng dấu vật lý, trình tự hành động, thời điểm, phép đo và kiểm chứng chéo. Không biến hiện tượng thành sanity drain, mất cảm xúc, mất trí nhớ, ý chí của Backrooms hay bằng chứng Async/Entity. Kai giữ nguyên năng lực và trang bị; Lucia giữ quyền tự chủ cùng giới hạn con người; mọi hao mòn, tài nguyên và thương tích tiếp tục theo state thật. Vị trí Iris/Syvial vẫn UNKNOWN; không có survivor, loot hay discovery mới nếu Core/state chưa surface. Khi và chỉ khi Core commit transition sang Red Rooms, màu đỏ và hình học đóng kín mới dần thay thế vùng phẳng cảm nhận; continuity kế tiếp duy nhất là Red Rooms, không nhảy Level 1 và không sửa route, quest, RNG, loot hoặc companion gates để ép tiến trình."
}

data.setdefault("steps", {})["Dullness"] = step
data["completedSteps"] = completed + ["Dullness"]
data["nextStep"] = "Red Rooms"
PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Applied exactly one hourly continuity step: Dullness -> Red Rooms")
