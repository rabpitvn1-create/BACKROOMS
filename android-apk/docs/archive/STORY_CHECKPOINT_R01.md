> **RETIRED: TÀI LIỆU LỊCH SỬ, KHÔNG ÁP DỤNG CHO RUNTIME HIỆN HÀNH.** Story Milestone / Story Checkpoint cũ đã bị loại bỏ. Không dùng nội dung này làm hợp đồng gameplay hoặc thiết kế END-POINT mới.

> **Tham chiếu lịch sử:** [Fake Sandbox / Milestone v3](FAKE_SANDBOX_CHECKPOINT_MILESTONE_V3.md). Phần còn lại lưu vết thiết kế/triển khai lịch sử, không dùng làm luật quota hoặc encounter hiện tại.

> **Trạng thái tích hợp 2026-10-13:** Cấu trúc Checkpoint đã vào main qua PR #595 (`820038c8`). Phạm vi Bước 3 bên dưới là mốc lịch sử; runtime hiện hành có planner và completion v2 ở Bước 4–9. Level 0 có 20 CP; Level sau nhận tổng CP do Gemini biên kịch và Core kiểm tra. Mỗi CP yêu cầu ít nhất 50 turn, 20 encounter được xác nhận và đánh bại Boss. Xem `STORY_MILESTONE_R01.md`, `STORY_MILESTONE_RUNTIME_R01.md` và `STORY_SAVE_HISTORY_R01.md`.

# Story Checkpoint R01 (Step 3/10)

**Trạng thái:** khung định danh và persistence, **chưa kích hoạt Milestone hoặc Boss**.
Đây là cơ sở kỹ thuật cho mô hình Fake Sandbox đã thống nhất. Không thay game sang Sandbox ngẫu nhiên.

## Các khái niệm và ranh giới

- **Level**: vùng môi trường do `LevelRouteRegistry` và World Canon định nghĩa. Một Level có thể chứa nhiều Sublevel/node và nhánh địa điểm.
- **Story Checkpoint (CP)**: một phần kịch bản có ranh giới do Gemini biên kịch. Không phải Level node, không phải action-session checkpoint, không phải nút Save thủ công.
- **Milestone Plan**: lịch các sự kiện/kết quả bắt buộc của Gemini, gắn với CP và các Turn. GM kể và lồng ghép vào hành động thực tế, không phải tác giả kết quả Milestone.
- **Player Turn**: hành động tương tác. Nhiều Turn được phép trong cùng một CP; chỉ số Turn không tự chuyển CP.

## Định danh

- Level 0 có **chính xác 20 CP**, `CP-L00-01` … `CP-L00-20`.
- `index` bắt đầu từ 1; `total` khóa 20 đối với Level 0.
- Level 1 trở đi dùng cùng mẫu `CP-Lxx-yy`, nhưng **chưa chốt số lượng CP**; chỉ cấu hình số lượng sau khi có quyết định cho Level tương ứng.
- Không gắn cứng các CP số 1…20 với route node 0, 0.1, 0.2… vì như vậy vô tình tạo thêm rào di chuyển và tăng coupling với thiết lập Level. Node chính xác vẫn nằm riêng trong `levelJson.id`.
- Level 0/1/2… có thể có Sublevel; di chuyển node hợp lệ **không tự kết thúc CP**.

## Trạng thái lưu hiện có

`GameState.world["story.checkpoint"]` chứa JSON ngắn:

```json
{"level":0,"index":1,"total":20}
```

Tồn tại cạnh các trường `levelJson`, `flagsJson` và `location`, không thay thế hay ghi đè trường nào. `GameStateCodec` đã serialize `world`, nên giá trị này được lưu/tải như phần còn lại của Game Core. CP đầu tiên được seed trong Game Core lúc tạo game mới, **không** khi chỉ đọc/chiếu một save đã tồn tại.

`StoryCheckpointCore` trong Bước 3 mới:
- cung cấp ID CP theo định dạng chuẩn, nhận diện CP cuối và địa chỉ CP kế tiếp;
- khởi tạo CP đầu khi **bắt đầu game mới** ở Level 0; không âm thầm đưa save đang chơi vào CP đầu;
- từ chối chỉ số không hợp lệ thay vì âm thầm reset;
- **không** tự sửa CP sau mỗi `CoreClientProjection.advance()`.

## Hợp đồng chuyển tiếp cho Bước 4–6 (chưa triển khai)

```text
CP hiện tại: Gemini milestone plan (các Turn và kết quả đã định)
    ↓
Turn cuối của Milestone: Entity bắt buộc xuất hiện
    ↓
Combat đã được Game Core khởi tạo
    ├── Combat vận hành: Entity cuối Milestone HP ×9, EXP ×9
    └── Gemini tạo ngay Milestone Plan của CP kế tiếp (bất đồng bộ)
    ↓
Kết quả Combat và State được Core xác nhận, SaveStats của CP hiện tại được chốt
    ↓
Plan kế tiếp đã READY + không có combat đang hoạt động + mốc kết thúc hợp lệ
    ↓
CP kế tiếp ACTIVE (hoặc chuyển Level theo routing Core khi đã xong CP cuối)
```

- **Một trận Entity kết thúc Milestone/Checkpoint**, không tự đặt thêm trận cho mọi Turn.
- Không lấy việc đánh dấu `nextAddress` làm lệnh chuyển CP. Mọi thay đổi trạng thái phải xuất phát từ hành động nội bộ Game Core đã xác thực, không cho GM hoặc WebView tự ghi chỉ số CP.
- Đang Combat thì Gemini chuẩn bị sẵn CP kế tiếp; Gemini không cần chạy lại ở mỗi Turn.
- Nếu Gemini chưa tạo xong Plan, không được phát sinh checkpoint rỗng hoặc coi Plan đã sẵn sàng.
- Kết quả bắt buộc của Milestone thuộc quyền quyết định biên kịch Gemini. Game Core vẫn thực thi HP/EXP và trạng thái gameplay nhất quán; không được lấy lời kể chưa commit làm sự kiện đã xảy ra.
- Chỉ Level 0 đã có số lượng CP chính thức. Không tự mở Level 7–10 để dựng checkpoint gameplay; World Canon của chúng hiện mang tính tham khảo.
- Việc xóa Hidden Exit Streak, thay random encounter và kích hoạt Boss ×9 thuộc bước tích hợp sau, **không** nằm trong thay đổi của Bước 3.

## Xác minh Bước 3

Regression test kiểm tra:
1. CP-L00-01 được seed khi tạo trạng thái fresh Level 0; total=20.
2. Normal Turn, save/load, chuyển Sublevel không tự tăng CP.
3. CP cuối là CP-L00-20 và không có phép tăng ngầm sang CP-L00-21.
4. Không tự áp đặt số lượng CP cho Level 1.
5. Chỉ số sai bị từ chối; Game Core không tự sửa/sáng tác lịch Milestone.

**Mốc tiếp theo (Bước 4/10):** Định nghĩa dữ liệu Milestone, lịch Turn, biến cố/kết quả bắt buộc và trận Entity kết thúc Milestone. Sau đó mới kết nối bộ biên kịch Gemini và runtime kích hoạt thực tế.
