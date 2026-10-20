# PR #654: 6 bước triển khai progression, riddles và vật phẩm nhiệm vụ

Yêu cầu đã chốt:
- **Không nhảy Level 0 → 0.1 trước khi khảo sát đầy đủ 8 khu chính + 29 khu phụ.**
- Sau đó là tuyến một chiều theo `LevelRouteRegistry`, không bỏ qua sub-level, AREA hoặc special node.
- Riddle thử thách suy luận cấp cao bằng bằng chứng kiểm chứng được, không phải ngẫu nhiên/brute force hoặc mật mã tự đoán.
- Ô **Vật phẩm nhiệm vụ** cạnh **Sổ tay** dành riêng cho GM, không dùng Inventory/Equipment Core. Vật phẩm một lần; GM quyết định sinh và tiêu thụ.
- Trạng thái GM item phải khôi phục đúng lúc Save/Load/New Game, không làm nghịch lý sau tải; không cần duy trì save schema cũ.

## 1. Gate đầu tiên: Level 0 → Level 0.1 [đã có code, CHƯA chạy Android tests]

- Core mở lựa chọn `INTERACT exit:0.1` khi đã thăm đầy đủ 37 environment identities và xác nhận hết evidence laws hiện hành.
- Lựa chọn người chơi là bắt buộc; GM không ra lệnh chuyển Level bằng lời kể.
- Stage giữ đồ thị nguồn; receipt Core tạo world graph cho `0.1` theo cùng giao dịch, không spawn bất ngờ trên bước rời Level.
- Chưa mở lối ra của `0.7` sang AREA `dullness` vì chưa có world definition cho các AREA, phải giải quyết trước khi gọi tuyến Level 0 là chơi xuyên suốt được.
- Test: `WorldExitProgressionTest.kt`; cần chạy test có mục tiêu trên Android/CI.

## 2. Thiết kế lại riddles Level 0 [đã viết code, CHƯA chạy Android/Kotlin tests]

- Nâng từ 2 luật một bước thành 4 chuỗi điều tra: `wet-ceiling`, `loop-reflection`, `signal-distortion`, `fracture-boundary`.
- Mỗi chuỗi có 4 chứng cứ ở những khu phụ khác nhau, 1 phép đối chiếu hai ghi chép cùng khu và 2 bước chọn giả thuyết có thứ tự; tổng cộng 16 chứng cứ, 4 phép đối chiếu, 8 quyết định.
- Hai chuỗi cần các dấu vật lý tự tạo. Chuỗi tín hiệu cần giải xong vòng lặp; chuỗi Rạn Nứt cần các kết quả từ nước và tín hiệu, nhưng KHÔNG kết luận Rạn Nứt là cửa sang Level khác.
- `WorldGraphRuntime` chỉ cho chọn giả thuyết sau khi đủ chứng cứ, đối chiếu, dấu neo và kết quả tiên quyết. Chọn sai không ghi kết luận, không khóa game; mỗi lựa chọn tiêu thụ lượt Core bình thường. Sổ tay ghi các suy luận đã được xác nhận.
- `inferenceDecisions` được lưu theo world progress và kiểm tra tại `validateSavedProgress`; không tin lời GM, không tạo vật phẩm, NPC, route hay sự kiện Canon mới.
- `Level0WorldDefinition.version` lên 5; không thêm lớp chuyển đổi save cũ.
- Đã thêm `Level0RiddleTestSupport.kt`, `Level0InferenceGuardTest.kt`, cập nhật `Level0PuzzleSolverTest.kt`, `WorldExitProgressionTest.kt` và Android `Level0RiddleGameplayTest.kt`.
- Đây là thử thách suy luận nhiều tầng, **không thể khẳng định độ khó thực tế tương ứng IQ nào** khi chưa có người chơi thử.

## 3. Riddles + lối ra cho toàn tuyến Level 0 [chưa triển khai]

- Áp dụng puzzle chuyên biệt cho 7 node có world definition, không dùng một câu đố giống nhau đổi tên.
- Phải có WorldDefinitions/đường khám phá được kiểm chứng cho các AREA `dullness`, `epsilon`, `ls_2`, hoặc thiết kế cơ chế AREA rõ ràng; giữ phân loại AREA.
- Rời một node bằng action Core xác thực sau tiến độ hợp lệ; không teleport và không tự chuyển sau khi giải riddle.
- Test từng chặng, không bỏ qua node bắt buộc.

## 4. UI Vật phẩm nhiệm vụ [chưa triển khai]

- Đặt ô cạnh nút Sổ tay trong `index.html` / `explorer-controls.js`; giữ nguyên header, GM panel, font và CRT snapshot.
- Không hiển thị vật phẩm GM trong kho chính hoặc giới hạn 9 loại vật phẩm.
- Danh sách có tên, mô tả, khả năng xem chi tiết và trạng thái có/đã dùng.

## 5. GM Quest Item Manager + persistence [chưa triển khai]

- Hợp đồng GM riêng cho `create` / `consume`, có ID ổn định, phạm vi node, giới hạn số lượng và thao tác idempotent.
- Vật phẩm thuộc miền GM; bộ quản lý chỉ xác nhận sơ đồ lệnh và lưu giao dịch, không giải puzzle thay GM.
- Không nới quyền `ProviderResponseContract.parseSceneWriter` của Core hiện tại thành quyền sinh vật phẩm Core.
- Save/Load phải snapshot Core gameplay và miền GM item cùng checkpoint, không để hai nguồn lưu lệch nhau. Không suy ra vật phẩm từ lời kể tự do.

## 6. Integration và regression [chưa triển khai]

- Kiểm thử node gates, đáp án, AI retry/network failure, resume, save/load/new game, dữ liệu vật phẩm giả mạo, dùng trùng, nhảy Level, snapshot cố định.
- Chạy các test trọng điểm theo giai đoạn; không tự động chạy toàn bộ suite/build liên tục.
- Chỉ đánh dấu hoàn tất khi có log xác minh thực tế.

## Quyền quyết định

- **Core**: vị trí, đồ thị, route, điều kiện bắt buộc và trận chiến.
- **GM**: sáng tạo câu đố và vật phẩm nhiệm vụ thuộc lớp GM khi được cho phép.
- **Quest Item Manager**: lưu/xác thực thao tác dữ liệu; không quyết định đúng sai của puzzle Core.
- **UI**: hiển thị, không phải nguồn xác thực cho chuyển Level hay vật phẩm.
