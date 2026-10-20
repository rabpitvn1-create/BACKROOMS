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

## 3. Riddles + lối ra cho toàn tuyến Level 0 [đã triển khai code, chờ CI tại HEAD]

- Đã thêm `Level0RouteRiddles.kt` với **20 chuỗi riddle khác nhau**: mỗi node trong 10 node tiếp theo có hai chuỗi điều tra, mỗi chuỗi có bốn chứng cứ môi trường, một đối chiếu cùng khu, dấu kiểm chứng, hai quyết định suy luận và xác minh cuối. Chuỗi hai phụ thuộc kết quả chuỗi một.
- Bảy node đặc biệt/sub-level hiện có đã nâng `Level0SatelliteWorldDefinition.version` lên 2, đóng băng lawInstances và yêu cầu giải đố thật.
- Ba AREA bắt buộc `dullness`, `epsilon`, `ls_2` có `Level0AreaWorldDefinition.kt`: mỗi AREA có **4 khu chính × 4 khu phụ**, sơ đồ seed ổn định và đường đi không cần unlock passage phụ.
- **LS-2** là Canon gốc của dự án được người dùng trực tiếp cho phép thiết kế ngày 2026-10-21. Căn cứ: `LS2_PROJECT_CANON_20261021.md`; có thẻ `LEVEL.NODE.ls_2` trong `knowledge_db.json` để GM truy xuất chính xác. Không coi đây là Canon ngoại bộ có sẵn.
- Tuyến 12 node bắt buộc: `0→0.1→0.2→0.5→0.7→dullness→epsilon→ls_2→manila_room→the_torment→red_rooms→1`. Core chỉ cấp hành động rời node sau khi toàn bộ khu và cả hai chuỗi riddle (Level 0 là bốn chuỗi) được xác minh. Chọn sai không tự mở khóa; không tự động chuyển node khi giải xong.
- Có `Level1ArrivalWorldDefinition.kt` làm **bản đồ tiếp nhận tối thiểu** từ Red Rooms đến bãi đỗ xe Level 1, dựa trên Canon sẵn có. Không mặc nhận đây là bản triển khai đầy đủ Level 1; không mở sẵn Level 1→1.2.
- `MandatoryLevel0RouteTest.kt` kiểm tra tiến độ từng node, chặn nhảy cóc, round-trip và toàn bộ 11 lần chuyển node qua giao dịch Core. `Level0SatelliteWorldDefinitionTest.kt`, `WorldExitProgressionTest.kt`, `WorldGraphRuntimeTest.kt` được cập nhật phù hợp.
- Đã đọc log CI: mốc `8874900` biên dịch Kotlin thành công nhưng test `WorldGraphRuntimeTest` còn kỳ vọng cũ rằng Level 1 chưa đăng ký; đã sửa kỳ vọng ở commit `f5feaf8`. **Chưa có kết quả test trên commit sau bản sửa**.

### Giới hạn thiết kế còn lại

- Chỉ xác thực độ khó logic và công bằng của riddle bằng test máy, chưa có playtest người thật nên không thể khẳng định tương quan IQ.
- Các khu AREA và riddle mới là nội dung do dự án sáng tạo; dữ liệu môi trường cũ của bảy node được giữ nguyên.
- Các bước 4–5 mới triển khai giao diện và kho vật phẩm do GM quản lý độc lập; riddle Core hiện không phụ thuộc vật phẩm của GM.


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
