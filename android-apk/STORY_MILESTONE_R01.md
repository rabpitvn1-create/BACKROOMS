> **ĐÃ ĐƯỢC THAY THẾ BỞI R02.** Đây là tài liệu lịch sử của lần triển khai cũ, không phải hợp đồng runtime hiện hành. Đọc [FAKE_SANDBOX_STEPS_2_TO_7_R02.md](FAKE_SANDBOX_STEPS_2_TO_7_R02.md) trước khi thiết kế hoặc chỉnh code. Milestone schema hiện là **v2**, bao gồm Turn/action/outcome và `levelEnd` do Gemini quyết định ở Checkpoint cuối. Lời kể không tự thay đổi HP, Combat hay Inventory; CP cuối có thể chuyển Level sau Combat hợp lệ.

# Bước 4/10 · Gemini Milestone Contract R01

**Mục đích:** dữ liệu kịch bản có kiểm soát cho Fake Sandbox. Gemini ấn định các sự kiện, Turn và kết quả **bắt buộc**; GM chỉ đưa chúng vào lời kể theo tương tác hiện thời. Không ép GM kể theo mẫu hội thoại, không yêu cầu người chơi quyết định lại kết quả đã khóa.

## Gói Milestone của một Story Checkpoint

Ví dụ ngắn cho `CP-L00-01`:

```json
{
  "schemaVersion": 1,
  "checkpointId": "CP-L00-01",
  "events": [
    {
      "id": "opening",
      "turn": 1,
      "kind": "STORY",
      "event": "Một âm thanh cào xé vang lên từ hành lang",
      "outcome": "Cao Minh nghe thấy âm thanh bất thường"
    },
    {
      "id": "hound",
      "turn": 3,
      "kind": "FINAL_ENTITY",
      "event": "Hound xuất hiện và áp sát",
      "outcome": "Cuộc giao tranh với Hound bắt đầu",
      "entityKey": "hound",
      "hpMultiplier": 9,
      "expMultiplier": 9
    }
  ]
}
```

- `turn` là số Turn **bên trong Checkpoint**, bắt đầu từ 1 khi Checkpoint được kích hoạt, không phải số Turn toàn game. Không giới hạn 20 Turn hoặc quota hành động mỗi Checkpoint.
- Mọi `event` đều bắt buộc; `outcome` là kết quả đã được Gemini định trước. Không có trường `optional`, không có RNG kích hoạt lại sự kiện và không có quyền GM chỉnh kết quả.
- Một Turn có thể chứa nhiều sự kiện theo thứ tự trong mảng `events`.
- **Chính xác một** `FINAL_ENTITY`, là sự kiện cuối cùng trong lịch. Boss dùng mã Entity đang hỗ trợ bởi CombatRuntime, HP ×9, EXP ×9. Hệ số này chỉ là contract ở Bước 4, **chưa được áp vào Combat**.
- Gemini được tự quyết định bao nhiêu Turn và sự kiện trong mỗi Checkpoint, trong giới hạn dung lượng nhằm chống payload bất thường; không có công thức kịch bản cố định.
- Người chơi vẫn tương tác tự do trong giếng của Checkpoint; Gemini đã khóa cái gì xảy ra, GM lồng ghép như thế nào. Game Core là nơi cuối cùng cập nhật HP/EXP, combat và trạng thái nhất quán.

## Xác minh dữ liệu trước khi lưu

`StoryMilestonePlan.parse` kiểm tra: schema v1; chính xác checkpoint ID; key/kiểu trường; danh sách không rỗng; Turn hợp lệ và không giảm; event ID duy nhất; event/outcome không rỗng; đúng một FINAL_ENTITY ở cuối; Entity tồn tại trong CombatRuntime; hệ số HP và EXP chính xác ×9. Không cắt gọt dữ liệu sai để hợp thức hóa kế hoạch lỗi.

Lịch được lưu trong `GameState.world`:
- `story.milestone.active`: plan đang hoạt động và Turn toàn game khi Checkpoint bắt đầu.
- `story.milestone.prepared`: plan của Checkpoint kế tiếp; được chuẩn bị khi trận Entity cuối Milestone đang diễn ra và đã tới Turn kết thúc, chưa kích hoạt.

Tái gửi cùng một bản kế hoạch là idempotent; thay nội dung đang hoạt động hoặc nội dung prepared đã lưu thì bị từ chối. `GameStateCodec` đã serialize `world`; do đó dữ liệu lưu/tải theo Core, không giữ bên WebView/GM.

## Ranh giới triển khai

**Bước 4 (PR này):** định nghĩa contract, codec/validation, lưu/đọc current và prepared plan có điều kiện, test mục tiêu; chỉ dùng thông tin về Entity hợp lệ từ CombatRuntime, không tạo thêm danh mục Entity thứ hai.

**Bước 5:** 5 Gemini API key hoạt động dưới dạng fallback để sinh đúng contract này. Không tạo nhiều agent Gemini và không gọi lại theo từng Turn.

**Bước 6:** dispatcher thực thi sự kiện bắt buộc đúng Turn một lần, làm căn cứ khởi tạo Combat cuối Milestone; kết nối async Gemini ngay khi Combat bắt đầu, thực hiện HP ×9 / EXP ×9 trong Combat Core; chuyển CP khi trạng thái cuối được xác nhận.

**Bước 9:** SaveStats và Level History dựa trên sự kiện thực đã commit. Kịch bản chưa kích hoạt không được ghi như lịch sử đã xảy ra.

### Chưa giải quyết trong Bước 4

- Chưa tự động chuyển Checkpoint, chưa có bộ đếm sự kiện đã thực thi, chưa gửi kế hoạch cho GM, chưa có network/background Gemini, chưa thay encounter random hoặc Hidden Exit Streak.
- `CP-L00-20` sang Level kế tiếp cần routing cấp Level và số CP Level sau đã được cấu hình; không tự ấn định con số Level 1.
- Một loại Entity tình cờ giống boss và xuất hiện sau Turn kết thúc chưa đủ để xác nhận Combat thuộc Milestone. Bước 6 phải gắn dấu/encounter ID từ sự kiện cuối đã xác nhận trước khi chuyển CP.
- Tính hợp lý của `event/outcome` với Canon và các kết quả chiến đấu thực tế phải được xác minh khi Gemini tạo và dispatcher áp dụng; cú pháp JSON hợp lệ không tự chứng minh nội dung hợp Canon.
