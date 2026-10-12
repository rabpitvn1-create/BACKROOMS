> **ĐÃ ĐƯỢC THAY THẾ BỞI R02.** Đây là tài liệu lịch sử của lần triển khai cũ, không phải hợp đồng runtime hiện hành. Đọc [FAKE_SANDBOX_STEPS_2_TO_7_R02.md](FAKE_SANDBOX_STEPS_2_TO_7_R02.md) trước khi thiết kế hoặc chỉnh code. Milestone schema hiện là **v2**, bao gồm Turn/action/outcome và `levelEnd` do Gemini quyết định ở Checkpoint cuối. Lời kể không tự thay đổi HP, Combat hay Inventory; CP cuối có thể chuyển Level sau Combat hợp lệ.

# Bước 6/10 · Milestone Runtime R01

## Đã nối hệ thống

- `StoryMilestoneRuntime.due()` lấy các sự kiện bắt buộc đúng Checkpoint/Turn, kể cả sự kiện chưa xác nhận từ lượt trước. Game Core lưu danh sách event ID đã xác nhận trong `world["story.milestone.applied"]`.
- GM nhận sự kiện và kết quả bắt buộc thông qua `writerPrompt`, trả `milestoneEventIds` theo thứ tự; thiếu/sai sẽ vào luồng sửa một lần, nếu vẫn sai thì không commit lượt. **Receipt không thay thế việc đồng bộ ảnh hưởng gameplay thực tế.**
- Các lượt có sự kiện bắt buộc không bị xử lý tắt bởi bộ `processRule`. Encounter Entity ngẫu nhiên bị vô hiệu hóa khi Checkpoint có Milestone để không phá lịch trình, nhưng cơ chế RNG khác và HiddenExitStreak vẫn cần đánh giá ở Bước 8.
- Sau Core commit của FINAL_ENTITY, chính `StoryMilestoneRuntime.acceptTurn()` khởi tạo một **Combat instance thật** qua `CombatRuntime.startMilestoneBoss()`, HP lấy từ HP đã scale rồi nhân 9; khi được Core xác nhận tiêu diệt, `EntityExpRewards` nhân thưởng EXP 9 và chống thưởng trùng qua instance ID.
- Combat kết thúc bằng tiêu diệt hoặc bỏ chạy được Core ghi nhận; EXP chỉ được thưởng khi tiêu diệt. Nếu Gemini đã tạo kế hoạch tiếp theo, Core chuyển sang CP kế tiếp; nếu chưa, lưu trạng thái chờ an toàn.
- Gemini bắt đầu tạo kế hoạch tiếp theo ngay sau khi Core xác nhận khởi tạo Combat Boss. Sử dụng `auditIo` hiện hữu, một kế hoạch đang bay mỗi runToken/checkpoint/encounterId; vẫn dùng 5 key fallback tuần tự; không để request này chặn UI Combat.
- Phản hồi bất đồng bộ được xác thực bằng runToken/checkpoint/encounterId, không thể ghi vào game sau reset hay vào một Combat khác. Nếu Combat đã kết thúc rất nhanh, kết quả vẫn có thể được nhận và chuyển Checkpoint sau đó.
- `window.backroomMilestoneReady` chỉ làm mới state hiển thị từ Core; không ghi trực tiếp state gameplay từ WebView.
- Save/Load dùng `GameStateCodec` hiện tại; vị trí Checkpoint, applied event IDs, boss ID, prepared plan, kết quả Boss được lưu trong Core.

## Ranh giới hiện tại

- **Chưa merge** PR #590–#593 và PR Bước 6. Phải tích hợp tuần tự sau khi cổng xác minh và quy trình PR cho phép.
- Đây là bộ điều phối lịch và Combat. Những tác động có cấu trúc như "Lucia chắc chắn bị thương" hiện vẫn cần được ánh xạ từ outcome sang Core command có tính chất bắt buộc. GM có event và receipt, nhưng receipt không tự làm giảm HP hay thêm Injury. Phần này phải được giải quyết ở Bước 7, không được mô tả là đã đồng bộ mọi hậu quả.
- Bước 6 chỉ tạo đường prefetch sang `nextAddress()` cùng Level. **CP-L00-20 → Level 1** chưa thể lập CP kế tiếp vì chưa có tổng số Checkpoint Level 1 hay quy tắc routing/commit được chốt; tuyệt đối không tự giả định.
- Nếu Gemini chưa có kịch bản khi Boss đã kết thúc, các action gameplay mới bị dừng ở trạng thái chờ; lần tương tác sau có thể thử tạo lại, không phỏng đoán nội dung.
- Những hoạt động meta/Item UI đi qua đường riêng có thể cần kiểm soát đồng bộ tương tự ở bước điều chỉnh GM/roll; không coi việc thêm event receipt là semantic verification tự động.

## Kiểm tra

Regression test tập trung: event receipts chính xác và không replay qua Save/Load, HP×9 dựa trên HP hiệu lực, EXP×9 đúng instance, Entity thường không được nhân, trễ trả kết quả Gemini sau Combat vẫn chuyển Checkpoint, kế hoạch mới có Turn tương đối mới, checkpoint/plan cũ không còn active. CI kiểm tra Kotlin/JUnit và Android debug APK.
