# Fake Sandbox — Checkpoint / Milestone v3 (hợp đồng hiện hành)

## Mục tiêu và thẩm quyền

Một **Checkpoint là một chương truyện**. Bộ biên kịch được chọn (Gemini hoặc SOL)
tạo **một mạch truyện `storyArc` khoảng 500 ký tự trước**, sau đó tạo
**lịch các sự kiện Milestone bắt buộc** để mạch truyện đó diễn ra. Milestone
không phải dàn ý 50 đoạn, không ép người chơi nhập đúng câu lệnh.

- **SOL/Gemini:** quyết định mạch truyện, sự kiện đến hạn, nhân vật/Entity, kết thúc chương.
- **Milestone runtime:** lưu lịch, phát sự kiện đến hạn, ghi nhận sự kiện đã thực thi; không sáng tác sự kiện.
- **Gehihi (GM):** hợp lý hóa hành động người chơi với mọi sự kiện đến hạn trong cùng Turn,
  kể tự nhiên, có hội thoại đối với cuộc gặp NPC/Follower, bảo toàn lịch sử.
- **Game Core:** tính Combat, Equipment, Item; không chọn hoặc viết lại sự kiện cốt truyện.

## Explorer Turn

- `EXPLORE` (Khám Phá) **và** `EXECUTE` (Thực Hiện) đều tiến một Explorer Turn
  khi hành động được chấp nhận. Combat có tiến trình riêng, không cộng Explorer Turn.
- Checkpoint có **tối thiểu 50 Explorer Turn**, không phải tối đa 50.
  SOL/Gemini có thể chọn Turn cuối lớn hơn khi câu chuyện cần.
- Sự kiện Milestone có `turn` tương đối từ 1. Turn không có sự kiện bắt buộc
  vẫn được Gehihi kể. Nhiều sự kiện phi-Combat có thể cùng một Turn.
- Hành động người chơi không được làm biến mất hoặc hoãn sự kiện bắt buộc đến lượt.
  Tương tự, GM không được tự quyết định hành động hoặc kết quả Combat của người chơi.

## Encounter là Combat bắt buộc

- Mỗi Checkpoint có ít nhất **20 ENTITY thường và một FINAL_ENTITY (Boss) riêng**.
  Đây là quota **cố định tối thiểu**, không phải phần trăm độ dài.
- Mỗi `ENTITY` được lập lịch là **một lần gặp trực tiếp và bắt buộc mở Combat**.
  Không có `combatRequired=false`, sự kiện chỉ quan sát hoặc nhìn rồi bỏ đi
  được tính là Entity Encounter.
- Encounter của Milestone tạo `PENDING_ENCOUNTER_CONFIRMATION`;
  `Battle Start` chỉ kích hoạt trận đã được xác lập, không phải chọn đánh hay không.
- Không tiếp tục Explorer Turn khi trận bắt buộc còn pending/active.
  Combat Core xác định kết quả và phần thưởng; GM dừng trước đòn đánh.
- Mỗi encounter có ID ổn định, không tạo trùng qua retry hoặc Save/Load.

## NPC/Follower, sự kiện và diễn biến câu chuyện

- Gặp NPC/Follower phải có cuộc gặp và lời thoại thật trong lời kể.
  Gặp Follower không đồng nghĩa gia nhập Party.
- Những lời hỏi, manh mối, tiết lộ, diễn biến nghi ngờ/tin tưởng... có thể là
  các STORY event bắt buộc. SOL/Gemini lên lịch; Gehihi viết lời kể và thoại.
- `storyArc` và lịch sử các sự kiện đã thực thi phải vào ngữ cảnh GM và biên kịch
  của Checkpoint tiếp theo. Lưu cả những sự kiện quan trọng thay vì chỉ 12 event
  gần nhất, vì có thể có 20 trận Combat che mất các mốc nhân vật.

## Boss và prefetch

Boss xuất hiện **sau diễn biến Explorer của Turn cuối**, không tiêu thụ một
Explorer Turn mới, cũng không thuộc quota 20 Entity thường.

Khi Boss Combat được bắt đầu, tạo trước `storyArc` và Milestone của Checkpoint
tiếp theo trong khi người chơi chiến đấu. Milestone chuẩn bị chỉ được kích hoạt
sau khi Boss thật sự bị đánh bại. Nếu Boss thắng trước khi tải xong, giữ kết quả
đã lưu và cho phép hoàn thành/thử lại yêu cầu tải; không tạo Boss lần nữa.

## Định dạng và lưu trữ

Schema plan v3 có chính xác `schemaVersion`, `checkpointId`, `storyArc`,
`events`, `completion`. Mỗi event có `id`, `turn`, `kind`,
`action`, `event`, `outcome`, và trường định danh nhân vật/Entity
phù hợp loại. `ENTITY` không có cờ bật/tắt Combat; `FINAL_ENTITY`
luôn có hệ số Boss cố định do Combat Core xử lý.

Lời kể Gehihi trả về `reply` cùng `completedEventIds` xác nhận các ID
Milestone thực sự được thực hiện trong lượt. Ứng dụng đối chiếu các ID đến hạn
trước khi ghi nhận lượt. Đó là xác nhận cấu trúc từ GM, **không phải kiểm định
độc lập ngữ nghĩa của lời kể**.

SaveState hiện hành giữ kế hoạch, `storyArc`, các event đã áp dụng, encounter
ID, Boss result, prepared plan và lịch sử Checkpoint; tải lại không được làm
trùng sự kiện. Không duy trì migration hoặc adapter chỉ cho save cũ.

## Tiêu chí nghiệm thu

- Parser bác bỏ kế hoạch dưới 50 Turn, dưới 20 ENTITY thường, thêm cờ
  `combatRequired`, thiếu `storyArc`, Boss thiếu hoặc không ở cuối.
- Kế hoạch dài hơn 50 Turn với 20 ENTITY vẫn hợp lệ.
- Thực Hiện tại Turn có NPC/Follower không làm mất sự kiện; cả EXPLORE/EXECUTE tăng lượt.
- Đủ 20 encounter thường, mỗi encounter có pending Combat và Battle Start,
  Combat kết thúc mới khám phá tiếp; Boss riêng.
- Save/Load không phát lại cuộc gặp, không mất `storyArc` và lịch sử nhân vật;
  prefetch không làm gián đoạn Boss Combat hoặc kích hoạt kế hoạch sai phiên.

**Lưu ý:** Các tài liệu R01/R02 phản ánh những giai đoạn triển khai lịch sử.
Khi quy tắc quota hoặc encounter mâu thuẫn với v3, lấy tài liệu này và
hợp đồng parser/runtime v3 làm chuẩn.
