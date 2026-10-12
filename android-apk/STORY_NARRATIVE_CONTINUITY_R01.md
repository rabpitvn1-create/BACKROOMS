# Bước 7/10 · Narrative Continuity / Story Events / Entity / Clues

## Quyết định kiến trúc

**Lời kể của GM và gameplay là hai hệ thống độc lập.** Một dữ kiện truyện có thể còn hiệu lực qua nhiều Level, nhưng việc ghi nhớ nó không trực tiếp chỉnh HP, Status, Combat, Equipment, kỹ năng hoặc Inventory. Chỉ các sự kiện có hợp đồng Game Core riêng mới được tác động đến gameplay, ví dụ FINAL_ENTITY của Bước 6.

**Không có tình tiết truyện được xác lập từ ví dụ minh họa, tài liệu kỹ thuật hoặc dữ liệu kiểm thử.** Chỉ kịch bản thực sự được Gemini tạo trong game, các dữ kiện được GM xác thực và những trạng thái game có nguồn mới được lưu vào continuity.

## Trách nhiệm

- `StoryContinuityReducer` hiện có ghi những thay đổi Level, Party, communication và registry đã xác thực. `storyContinuity.events` là bộ đệm tối đa 12 sự kiện gần nhất, **không phải** nguồn để kết luận một chuyện đã hết hiệu lực.
- `StoryFactLedger` lưu những dữ kiện có ý nghĩa lâu dài trong `GameState.world["story.factLedger"]`: tất cả facts chưa giải quyết (OPEN), và lịch sử đã commit (giới hạn 96 bản ghi gần nhất).
- Các loại fact: `EVENT` (sự kiện truyện), `ENTITY` (Entity đã có bằng chứng được xác nhận), `CLUE` (manh mối đã phát hiện).
- Gemini có thể thêm `storyFacts` vào mỗi event Milestone. **Chưa tới Turn và chưa commit thì không được ghi fact như thể đã xảy ra.**
- GM trả `storyFacts` mỗi lượt gồm các thao tác `ADD` hoặc `RESOLVE`, hoặc mảng trống `[]` khi không có dữ kiện bền vững mới.
- `StoryFactLedger.context` gửi tất cả facts OPEN cùng lịch sử gần đây cho Gemini và GM, kể cả khi đã qua nhiều Level. Không đưa chúng sang API Combat/Inventory.

## Hợp đồng dữ liệu

Định dạng, không phải kịch bản mặc định:

```json
{
  "storyFacts": [
    {"op": "ADD", "kind": "CLUE", "subject": "fact.key", "fact": "Nội dung thực tế từ lượt chơi"}
  ]
}
```

- `ADD`: `kind` phải thuộc EVENT, ENTITY, CLUE; `subject` là mã truy xuất bền vững; `fact` phải mô tả dữ kiện thực sự được xác nhận trong lượt chơi.
- `RESOLVE`: tham chiếu chính xác `referenceId` của fact đang OPEN; chỉ đánh dấu kết thúc khi có tình tiết giải quyết hợp lệ.
- Fact đã OPEN không thể bị đổi nội dung âm thầm. Cùng `subject` và `kind` nhưng nội dung mâu thuẫn phải được giải quyết rõ ràng.
- Entity fact phải có key tồn tại trong Combat Core và có bằng chứng encounter hợp lệ.
- Core sinh ID, kiểm tra kiểu/trường/độ dài; ghi facts và receipt sự kiện trong cùng lần commit hợp lệ, Save/Load bảo toàn.
- Quá 48 facts OPEN sẽ báo lỗi rõ, không âm thầm xóa để nhường chỗ.

## Những gì không làm

Không chỉnh `CombatRuntime`, `EntityExpRewards`, `LuciaCanon`, `CharacterEquipmentSystem` hoặc `StateReducer` cho các dữ kiện lời kể. Không đưa ví dụ trao đổi với tác giả vào Gemini prompt, Canon Registry, kịch bản Level, hoặc bản ghi Save khởi tạo.

## Giới hạn

- Chưa có bảo đảm rằng GM tự phát hiện và ghi lại 100% tình tiết đáng nhớ. Bộ kiểm tra hiện xác nhận hợp đồng dữ liệu, không chứng minh mọi câu văn đúng ngữ nghĩa.
- Clue do GM ghi là dữ kiện truyện, chưa tự chứng minh nó là Canon chính thức.
- Đến ngưỡng facts OPEN sẽ cần chính sách hợp nhất có chứng cứ; không được cắt bớt các facts quan trọng một cách âm thầm.

## Kiểm thử

Chỉ dùng định danh và nội dung **TEST_ONLY** để kiểm tra: Save/Load, dữ kiện OPEN tồn tại qua nhiều Turn/Level, ADD/RESOLVE, receipt đúng Turn, từ chối Entity chưa xác nhận, chống mâu thuẫn, và xác nhận việc ghi ký ức không chỉnh HP/Status/Equipment/Inventory. Các fixtures không được tải như nội dung truyện khi chạy game.
