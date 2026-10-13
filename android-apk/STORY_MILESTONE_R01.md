# Bước 4/10 · Gemini Milestone Contract v2

Gemini biên kịch action, event, outcome và Turn tương đối. GM kể tự nhiên theo lựa chọn của người chơi nhưng không thay kết quả bắt buộc. Action là hành động trong thế giới truyện; không phải cụm từ người chơi phải nhập.

## Schema

- Root chỉ có `schemaVersion: 2`, `checkpointId`, `events`, `completion`.
- Mỗi event có `id`, `turn`, `kind`, `action`, `event`, `outcome`. Các chuỗi bắt buộc không rỗng; ID duy nhất; Turn từ 1 và không giảm.
- Chính xác một `FINAL_ENTITY`, ở cuối lịch, thêm `entityKey`, `hpMultiplier: 9`, `expMultiplier: 9`. Entity phải được CombatRuntime hỗ trợ. Outcome của event này chỉ mở Combat, không tự khẳng định chiến thắng.
- CP chưa cuối: `completion` chỉ có `type: NEXT_CHECKPOINT`, `outcome`.
- CP cuối: `completion` có `type: EXIT_LEVEL`, `targetNodeId`, `nextCheckpointTotal`, `outcome`. Gemini chọn exact node thuộc Level khác trong registry hỗ trợ và tổng CP Level sau là số nguyên dương. Level 0 vẫn khóa 20 CP.

Không ép target là MAIN, không yêu cầu đi đủ tuyến phụ hoặc HiddenExitStreak. Registry kiểm tra địa chỉ tồn tại, không thay Gemini quyết định kết thúc Level. Level 7–10 chưa được runtime hỗ trợ nên không hợp lệ làm target.

## Lưu trữ và prepared plan

`story.milestone.active` giữ plan với global startTurn; `story.milestone.prepared` giữ plan tương lai. Địa chỉ kế tiếp lấy từ completion của active plan, bao gồm CP đầu Level mới. Prepared chưa phải sự kiện đã xảy ra và không tự kích hoạt Level/CP.

Parser, serialize và Save/Load dùng cùng schema v2. Không migration hoặc fallback đọc schema v1. Cùng nội dung là idempotent; ghi đè plan active/prepared bị từ chối.

## Ranh giới và xác minh

Bước này chỉ định nghĩa/kiểm tra hợp đồng và lưu dữ liệu; chưa thực thi chuyển Level, Gemini API, Boss ×9 hoặc Exit gates. Những phần đó thuộc PR #593–594.

Quyết định người dùng: chỉ Boss bị đánh bại mới hoàn tất CP. Chạy thoát giữ CP chưa xong; runtime phải có đường tái chạm trán, không đổi ESCAPED thành DEFEATED.

Tests kiểm tra action bắt buộc, completion theo vị trí CP, target không tồn tại/cùng Level, tổng CP lỗi, target Sublevel hợp lệ, prepared CP đầu Level khác và Save/Load. JSON hợp lệ không tự chứng minh Canon hoặc semantics của lời kể.
