# Fake Sandbox · Bước 2–7 · Hợp đồng tích hợp R02

Trạng thái: code đề xuất, **không phải Lore Canon**. Mọi đoạn `TEST_ONLY` trong unit tests chỉ kiểm tra bộ máy, tuyệt đối không phải sự kiện trong game. Thẩm quyền Canon: Google Drive `/Novel/Asset` và Internet công khai có truy nguyên. Gameplay thực thi bởi Game Core.

## Bước 2 · Canon

Đã tích hợp 41 thẻ mới của nhánh PR #590 lên nhánh tiến trình Bước 3–7 thay vì để thành hai nhánh độc lập: 22 ngoại hình Entity (đủ 22 trong Encounter Pool), 11 baseline Level 0–10 (Level 7–10 reference-only), 4 exact node, 4 Character authored và cập nhật provenance cho 6 thẻ Syvial. `KnowledgeContextEngine` chọn đúng actor, Level/node, Entity có roll xác thực trước Combat, không tải trọn Canon theo Turn. 42 legacy records và nguồn chưa rõ vẫn chờ đối chiếu; thiếu Codex không được dùng làm cớ sáng tác thêm.

## Bước 3 · Story Checkpoint

Level 0 có **đúng 20 Checkpoint**. Checkpoint độc lập với node Level và hành động/Turn. Định danh và save nằm trong `GameState.world`. Level tiếp theo lấy tổng số CP do chính Gemini biên kịch ở kết thúc Level trước, không tự mặc định Level 1 cũng có 20 CP. Save cũ không cần tương thích.

## Bước 4 · Milestone schemaVersion 2

Mỗi event được Gemini định rõ: **id, Turn tương đối trong CP, kind, event, action, outcome**. `action` là hành động được ấn định trong câu chuyện (NPC/Entity/môi trường), **không phải mệnh lệnh ép người chơi chọn một hành động cụ thể**; gameplay tự do trong khung Fake Sandbox.

Một Milestone chỉ có một `FINAL_ENTITY`, ở cuối danh sách sự kiện, với HP×9 và EXP×9 (không nhân ATK/DEF). StoryFacts tùy chọn mang ký ức lời kể, **không** tự chỉnh Combat/Inventory.

**Checkpoint cuối Level** bắt buộc có thêm `levelEnd` do Gemini viết:
- `targetNode`: exact node của **Level tiếp theo có route runtime**; Core cung cấp node hợp lệ để Gemini không bịa route.
- `nextCheckpointTotal`: số lượng CP Level tiếp theo do Gemini chọn (1..100).
- `outcome`: nội dung **kết thúc Level** thuộc Milestone.

Các CP còn lại **không** có `levelEnd`. Turn cuối của FINAL_ENTITY quyết định khi nào bước vào Combat; sau khi Core xác nhận Combat đã kết thúc, Core sử dụng `levelEnd` để hoàn tất Level. **Không cần HiddenExitStreak, tung xúc xắc Exit hay ép đi đến node cửa do hệ thống cũ tự đặt.** Core xác nhận trạng thái Combat, nhưng GM không được tự chuyển Level bằng ops.

Định dạng thử nghiệm:
```json
{
  "schemaVersion": 2,
  "checkpointId": "CP-L00-20",
  "events": [
    {
      "id": "scene_a",
      "turn": 1,
      "kind": "STORY",
      "event": "<sự kiện do Gemini quyết định>",
      "action": "<hành động diễn ra>",
      "outcome": "<kết quả bắt buộc>"
    },
    {
      "id": "final",
      "turn": 3,
      "kind": "FINAL_ENTITY",
      "event": "<cuộc chạm trán>",
      "action": "<hành động của Entity>",
      "outcome": "<Combat bắt đầu>",
      "entityKey": "<key trong CombatRuntime>",
      "hpMultiplier": 9,
      "expMultiplier": 9
    }
  ],
  "levelEnd": {
    "targetNode": "1",
    "nextCheckpointTotal": 5,
    "outcome": "<kết quả kết thúc Level do Gemini viết>"
  }
}
```
**Chỉ là ví dụ cấu trúc JSON, không phải kịch bản được phê duyệt.** `targetNode` và `nextCheckpointTotal` được kiểm tra theo Core; `1` và `5` trong mẫu không gắn vào game mặc định.

## Bước 5 · Gemini Planner

Một Gemini dùng 5 API key có fallback tuần tự sẵn trong `MainActivity`; không tạo 5 agent. Planner nhận thẻ Canon offline + state + StoryFactLedger. Core phát lease theo `runToken/checkpointId/Turn` và xác nhận trước khi lưu. Kế hoạch đã lưu không bị tái sinh mỗi Turn. Khi khởi tạo next-plan từ Combat, Core cung cấp thông tin node mới (không lấy Canon Level cũ làm bối cảnh Level tiếp theo).

## Bước 6 · Thực thi

Dispatcher dùng Turn **chính xác**, không đẩy sự kiện từ Turn 3 sang Turn 5 nếu chưa hoàn thành; đường sai phải bị từ chối. GM được yêu cầu kể event/action/outcome đã khóa và trả đúng receipts, Core từ chối thiếu ID. **Giới hạn:** receipt ID không chứng minh ngữ nghĩa câu văn GM đúng hoàn toàn; không mô tả cơ chế là đã có semantic verification trọn vẹn.

FINAL_ENTITY sau commit khởi tạo Combat instance, tăng HP×9/EXP×9 theo encounter ID. Gemini tạo kế hoạch tiếp theo bất đồng bộ ngay khi Combat bắt đầu. Nếu trả chậm sau Combat, Core chờ và chuyển CP/Level sau khi hợp đồng có hiệu lực, không ghi trùng. Core **không** chấp nhận thay main Level do GM/client tự đề xuất; trường hợp chuyển Level hợp lệ là `levelEnd` của cuối CP sau Combat.

`HiddenExitStreak` và random Exit bị vô hiệu trong Checkpoint có Milestone; cơ chế đó không bao giờ quyết định Level kết thúc. Di chuyển chủ động giữa exact node kề nhau cùng main Level vẫn không tự làm tăng CP. Cổng nào ngoài milestone chưa có đủ cơ sở thay đổi thì phải giữ hành vi hiện hành và ghi nhận để kiểm tra ở Bước 8.

## Bước 7 · GM Continuity

`StoryFactLedger` lưu EVENT/ENTITY/CLUE thực sự được commit, phát cho Gemini và GM xuyên Level. Dữ kiện còn OPEN giữ nguyên cho đến khi có RESOLVE được xác nhận. Lời kể không tự giảm HP, xóa vũ khí, tạo Poison, đổi kỹ năng, Inventory hay Combat. Không dùng những ví dụ giả định do tác giả nêu để giải thích như dữ kiện hoặc prompt kịch bản.

**Giới hạn chưa xóa:** GM có thể tự báo ADD/RESOLVE khi chưa có semantic proof đủ mạnh; schema validation và encounter ID guard không đồng nghĩa kiểm chứng ngữ nghĩa. Khả năng full semantic validation phải được kiểm thử/đánh giá trước khi tuyên bố giải quyết hoàn toàn.

## Cổng nghiệm thu

- Targeted tests cho strict schema, exact Turn/action/outcome, kiểm soát LevelEnd trên CP cuối, không cho GM tự đổi Level, cấp Boss ×9, Gemini prefetch, CP20→Level 1 sau Combat, save/load và ký ức truyện độc lập gameplay.
- Không merge, phát hành hoặc tuyên bố gameplay thực tế đúng chỉ vì CI thành công. Chưa test Gemini API với key thật thì phải ghi chưa test.
- Các hồ sơ Canon còn thiếu được bổ sung theo nguồn sau; không tạo compatibility layer phục vụ save đời cũ.
