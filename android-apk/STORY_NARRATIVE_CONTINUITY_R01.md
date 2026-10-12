# Bước 7/10 · Narrative Continuity / Story Events / Entity / Clues

## Quyết định kiến trúc

**Lời kể và gameplay là hai lớp độc lập.** Một sự kiện trong truyện có thể tồn tại xuyên nhiều Level mà **không** đụng HP, Poison, Inventory, Equipment, kỹ năng hoặc Combat. Game Core chỉ áp dụng tác động Combat nếu đã có **hợp đồng gameplay riêng** (ví dụ FINAL_ENTITY ở Bước 6), không suy đoán tác động từ câu chữ.

Ví dụ:
- Level 0: Lucia mất M4A1 trong truyện, ký ức `EVENT: lucia.m4a1` còn OPEN. Level 6, GM nhớ súng chưa được tìm lại nhưng Combat Lucia vẫn có thể dùng M4A1 theo cơ chế hiện hữu.
- Level 0: Cao Minh trúng độc trong truyện, ký ức `EVENT: cao_minh.poisoned` còn OPEN. Combat vẫn không bị gán Poison hoặc mất HP.
- Một clue hoặc Entity đã được xác nhận có thể được lưu bằng `CLUE` hoặc `ENTITY` để Gemini biên kịch tiếp và GM không tự biến mất dữ kiện.

## Sử dụng các hệ thống hiện có

- `StoryContinuityReducer` hiện hữu tiếp tục truy ra những thay đổi Level, Party, communication và registry từ dữ liệu đã xác thực. Nó vẫn có bộ đệm sự kiện giới hạn 12; **không được dùng bộ đệm đó để xác định sự kiện truyện nào đã hết hiệu lực.**
- `StoryFactLedger` lưu những dữ kiện quan trọng còn hiệu lực (OPEN) trong `GameState.world["story.factLedger"]`, cùng một bản lịch sử đã khép lại / Milestone được commit (gần nhất 96). Không tự xóa dữ kiện OPEN theo Level hay theo số Turn. Giới hạn 48 OPEN facts; nếu quá ngưỡng, từ chối thêm mới rõ ràng, không âm thầm loại dữ kiện cũ.
- Gemini Planner thêm `storyFacts` tùy chọn cho một event trong Milestone. Ví dụ:
```json
{
  "id": "loss",
  "turn": 2,
  "kind": "STORY",
  "event": "Lucia làm rơi súng",
  "outcome": "M4A1 biến mất trong hành lang",
  "storyFacts": [
    {"kind": "EVENT", "subject": "lucia.m4a1", "fact": "Lucia mất M4A1 tại Level 0"}
  ]
}
```
Fact trong Milestone chỉ được ghi khi **event đã thực sự được nhận và commit**. Mọi Milestone đã xảy ra còn được lưu vào `history` với event ID, ngay cả khi không khai báo facts.

- GM trả `storyFacts:[]` mỗi lượt; chỉ ghi thêm **dữ kiện mới có ý nghĩa lâu dài**:
  - `{"op":"ADD","kind":"EVENT","subject":"lucia.m4a1","fact":"Lucia mất M4A1 trong truyện"}`
  - `{"op":"ADD","kind":"CLUE","subject":"door.mark.red","fact":"Người chơi nhìn thấy ký hiệu đỏ trên cửa"}`
  - `{"op":"ADD","kind":"ENTITY","subject":"hound","fact":"Hound được xác nhận xuất hiện tại hành lang"}`, chỉ chấp nhận khi Entity đã có bằng chứng xuất hiện trong Game State hoặc Milestone.
  - `{"op":"RESOLVE","referenceId":"FACT:CP-L00-01:2:0","fact":"Lucia tìm lại khẩu M4A1 trong truyện"}`; chỉ đóng fact đang OPEN và phải có tình tiết hợp lệ trong lời kể.
- Cùng `subject` + `kind` đã OPEN thì không thể âm thầm thay nội dung; cần RESOLVE trước khi cập nhật sự thật khác. Re-send đúng fact được xem là idempotent.
- Core sinh ID và kiểm tra các field/enum/độ dài/khoản mục; không chấp nhận extra fields hoặc Entity key lạ. Ledger được commit cùng lượt đã được Core xác thực, sau đó Save/Load bảo toàn dữ kiện.
- `StoryFactLedger.context` cung cấp **mọi fact OPEN** (bao gồm từ Level 0) cộng lịch sử gần đây cho Gemini và GM, độc lập với bộ đệm `storyContinuity.events` giới hạn 12.

## Không thay đổi gameplay

Không thay các file `CombatRuntime`, `EntityExpRewards`, `LuciaCanon`, `CharacterEquipmentSystem`, `StateReducer`. Không có effect INJURY, trừ HP tự động, thêm status, xóa trang bị, thay kỹ năng, phát item hay tự chuyển Level từ lời kể.

`StoryFactLedger` chỉ ghi world narrative memory. Nó không phải nguồn quyền hạn của Combat; Entity trong Combat vẫn do Game Core xác nhận. Clue có thể là thông tin mà GM nhận ra trong lời kể; schema không tự chứng minh clue là sự thật Canon khi chưa có bộ semantic validator.

## Những điểm chưa tự nhận đã giải quyết

- Không thể bảo đảm 100% mỗi ý trong văn xuôi của AI đều có bản ghi `storyFacts` tương ứng. Hiện prompt bắt buộc GM trả mảng, và repair lỗi cấu trúc; tính đúng ngữ nghĩa vẫn là vấn đề cần audit riêng.
- Quá 48 fact OPEN thì Core từ chối ghi thêm, không âm thầm quên. Cần tiêu chí gộp/tóm tắt có kiểm chứng nếu chiến dịch dài đạt giới hạn.
- `storyContinuity` cũ và ledger narrative có mục đích khác nhau. Không nên xóa bộ ghi nhận biến động Level/Party đã có, cũng không lấy việc hết 12 sự kiện gần nhất làm bằng chứng sự kiện đã khép lại.
- Các PR #590–#594 chưa merge; PR #595 này thay thế implementation INJURY trước đó và phải được tích hợp tuần tự, sau CI.

## Kiểm tra hồi quy

Lưu facts qua Save/Load và đổi Level 0→6; sự kiện mất M4A1/trúng độc không thay HP/Status/Equipment/Inventory; giữ fact OPEN sau hơn 12 Turn; RESOLVE rõ ràng mới đóng; Entity phải có bằng chứng; dữ liệu sai bị từ chối; Milestone fact chỉ được ghi sau receipt đã commit.
