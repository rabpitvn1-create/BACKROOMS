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
if data.get("nextStep") != "0.8" or completed != EXPECTED_SEQUENCE[:12]:
    raise SystemExit(f"expected nextStep 0.8 after 0.7, got {data.get('nextStep')} / {completed}")
steps = data.get("steps") or {}
if "0.8" in steps:
    raise SystemExit("0.8 already exists")

steps["0.8"] = {
    "phase": "FLOOD_NAVIGATION",
    "storyUpdate": "Khi Core đã commit chuyển vùng vào 0.8 / Inundation, nước bắt đầu chiếm phần lớn mặt sàn và xóa đi những dấu hiệu vốn dùng để đọc khoảng cách: mép thảm, chân tường, bậc thấp và vật mốc gần nền có thể chìm một phần hoặc biến mất khỏi tầm nhìn. Kai không mặc định nước nông chỉ vì bề mặt phẳng, không coi một đoạn khô cao hơn là an toàn tuyệt đối và không dùng sức mạnh như lý do bước thẳng vào vùng chưa đo. Hắn đọc độ sâu theo từng đoạn, hướng và tốc độ dòng nếu có, độ dốc, độ bám, vật cản chìm, phản xạ ánh sáng và mọi nguy cơ điện có căn cứ từ thiết bị hoặc dây dẫn đang thật sự hiện diện; một mặt nước yên không chứng minh bên dưới trống hoặc sạch. Nếu một dấu đánh dấu cũ xuất hiện ở cao độ khác sau khi rời tầm nhìn, cả hai phải đối chiếu thời điểm, mặt bằng và topology trước khi kết luận nước đã dâng hay không gian đã đổi. Nếu Lucia đang ở party sau identity/join gate, cô giữ kỷ luật người thường được huấn luyện tốt: không lội vào trước chỉ để chứng minh gan lì, tự đánh giá giới hạn thăng bằng và thể lực, báo những thay đổi cô thật sự quan sát được và giữ vị trí cho phép quay lui. Cả hai ưu tiên điểm cao, tuyến có thể thử từng đoạn và nơi còn giữ được mốc cục bộ. Chỉ khi Core commit transition sang 0.99, dòng nước và hình học mới được phép hội tụ về một vùng sâu, tối và ít nhiễu hơn như dấu sang Deeper Regions.",
    "survivalFocus": "Nước biến hao mòn cũ thành rủi ro cộng dồn thay vì reset chúng: lạnh còn dư từ 0.66, quần áo hoặc giày ẩm nếu state còn giữ, mệt, khát, thương tích và nhiễm bẩn làm footing, phản xạ, giữ thăng bằng và khả năng quay lui tệ hơn theo tình trạng thật. Nước không được coi là tài nguyên uống chỉ vì trông trong; phải giữ khóa BACKROOMS-ITEMS rằng nguồn mở cần kiểm và nước thấm/ngập không tự trở thành Almond Water. Độ sâu không chắc chắn, hố hoặc vật cản chìm, bề mặt trơn, dòng chảy và nguy cơ điện chỉ có trọng lượng khi có bằng chứng môi trường tương ứng; không tự sinh shock, dòng xiết hay thương tích để tăng kịch tính. Kai giữ nguyên SRU-MK20, năng lực và phản xạ, nhưng không tự được cấp tính năng chống nước, cách điện, sonar, buoyancy hoặc sealing ngoài codex. Lucia vẫn là con người được huấn luyện tốt: ưu tiên ba điểm tựa, thử nền trước khi dồn trọng lượng, tránh để cả hai cùng mất footing và không vượt vùng mà đường quay lui chưa rõ. Suspense đến từ việc mặt nước che mất thông tin cần cho quyết định, không từ việc nhân vật quên kiểm tra hiển nhiên.",
    "npcPolicy": "Không sinh survivor/NPC để báo chính xác độ sâu, đưa đường khô tuyệt đối, xác nhận nước an toàn hoặc dẫn thẳng sang 0.99. Nếu Core/RNG đã commit encounter, người đó phải có nhu cầu sinh tồn cụ thể như tìm điểm cao, tránh một đoạn nền sụt, bảo vệ đồ khô, thoát vùng nước đang tăng hoặc quay lại tuyến từng đi được; tri thức của họ chỉ có giá trị theo thời điểm và đoạn đã trải qua. Một lời kể rằng nước 'luôn nông', 'không có điện', 'uống được', 'dẫn ra ngoài' hoặc có dấu Async/Entity/Iris/Syvial không tự trở thành sự thật đã kiểm chứng. Lucia vẫn là fixed/story-owned companion nếu đã gia nhập hợp lệ; bước này không bypass identity/join gate và không ép reunion khác.",
    "itemPolicy": "Không tự sinh dây, áo phao, phao nổi, ủng chống nước, máy đo chuyên dụng, bơm, bộ lọc, bình chứa, pin, đạn, thức ăn hoặc Almond Water để giải vùng ngập đúng lúc. Chỉ dùng vật phẩm và trang bị đang thật sự có trong state theo đúng chức năng đã khóa; mọi item mới chỉ được kể là tìm thấy, nhận hoặc nhặt khi Core/world-loot/inventory đã commit đúng item trong runtime catalog. Nước ngập, vật thể chìm một phần, điểm cao và dấu ẩm là dữ kiện môi trường chứ không phải loot. Không âm thầm làm vật phẩm biến mất, khô lại, chống nước hoặc miễn nhiễm nhiễm bẩn nếu state/codex không xác nhận.",
    "relationshipStage": "PRACTICAL_CARE_HELD_WITHOUT_ESCALATION. Chỉ áp dụng nếu Lucia đang thực sự đồng hành. Inundation không được dùng làm cái cớ ép nắm tay, bế, cõng hoặc tiếp xúc thân mật chỉ vì nước; mọi hỗ trợ cơ thể phải xuất phát từ nhu cầu thực tế, vị trí và đồng thuận hợp lệ trong khoảnh khắc. Lucia có thể tiếp tục ưu tiên kiểm đường, báo footing hoặc giữ vị trí giúp Kai quay lui như một phần của mẫu quan tâm đã tích lũy, còn Kai không giả vờ không nhận thấy sự ưu tiên lặp lại nhưng cũng không biến một lần đưa tay hay cảnh báo trượt chân thành bằng chứng tình cảm chắc chắn. Kai hỗ trợ Lucia theo giới hạn con người của cô mà không quyết định hộ cô hoặc biến cô thành gánh nặng. Bước này chủ ý giữ nhịp quan hệ, không bắt buộc tăng intimacy; không thú nhận, không ghen, không thử lòng, không therapist hóa và xưng hô vẫn OPEN.",
    "endingState": "0.8 được khóa như bước flood navigation: mặt nước che mất thông tin nền, nên độ sâu, độ bám, vật cản chìm, hướng dòng, điểm cao, đường quay lui và nguy cơ điện/nhiễm bẩn chỉ được kết luận từ bằng chứng đang có. Nước không tự là Almond Water, một bề mặt yên không tự là an toàn và thay đổi mực nước không tự chứng minh Backrooms có ý chí. Kai giữ nguyên năng lực/trang bị mà không được bịa tính năng chống nước; Lucia giữ quyền tự chủ cùng giới hạn con người; mọi hao mòn chỉ tồn tại theo state đã surface. Vị trí Iris/Syvial vẫn UNKNOWN; không có bằng chứng Async, Entity, survivor hoặc cơ chế mới nếu Core/state chưa surface. Khi và chỉ khi Core commit transition sang 0.99, dòng nước và hình học hội tụ về vùng sâu, tối và ít nhiễu hơn; continuity kế tiếp duy nhất là 0.99 / Deeper Regions, không nhảy Level 1 và không sửa route, quest, RNG, loot hoặc companion gates để ép tiến trình."
}
data["completedSteps"] = completed + ["0.8"]
data["nextStep"] = "0.99"
data["steps"] = steps
LEDGER.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Applied exactly one hourly story step: 0.8 / Inundation -> next 0.99")
