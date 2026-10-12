# Bước 7/10 · Đồng bộ GM và hậu quả Milestone trong Game Core

## Nguyên tắc thẩm quyền

- **Gemini Planner** ấn định sự kiện, kết quả bắt buộc và các `effects` có cấu trúc nếu kết quả làm thay đổi cơ chế game.
- **GM Sol/Haku/Gehihi** chỉ kể lại đúng bối cảnh, phản ứng theo hành động người chơi. GM có thể diễn đạt tự nhiên, không có quyền sửa hoặc phát tác động cơ học.
- **Game Core** là nơi áp dụng hiệu ứng, thay đổi HP, status, injuries và lưu dữ liệu. `milestoneEventIds` là receipt được đối chiếu, không phải lệnh cập nhật HP từ GM.
- Dữ liệu thương tích thực tế của Party được gửi cho GM từ `GameCoreFacade.milestonePartyHealthContext()` ở những lượt có Milestone.
- Không tạo subsystem injury mới. Dùng `StatusCommand`/ `StateReducer`, `CharacterStatEngine.setCurrentHp`, và `GameState.characters[].injuries`.

## Schema thêm vào event STORY (tùy chọn)

```json
{
  "id": "lucia_wounded",
  "turn": 3,
  "kind": "STORY",
  "event": "Hound bất ngờ cắn Lucia",
  "outcome": "Lucia bị thương ở vai",
  "effects": [
    {
      "type": "INJURY",
      "characterId": "lucia",
      "label": "vết cắn trên vai",
      "hpLoss": 12
    }
  ]
}
```

Chỉ `STORY` nhận effects, tối đa 8 injury effect mỗi sự kiện. `INJURY` được hỗ trợ đầu tiên, với `characterId`, `label` và `hpLoss` từ 0–200. Đây là mức mất HP tối đa do vết thương, không tự giết mục tiêu; hp tối thiểu còn 1 khi áp dụng vào nhân vật sống. Game Core không hỗ trợ thêm `AWARD_EXP`, `SPAWN_ITEM`, `PARTY_JOIN`, `KILL_CHARACTER` hoặc chuyển Level bằng effects ở bước này.

Không có `effects` nghĩa là chỉ có kết quả lời kể. Nếu người viết biên kịch muốn kết quả có tác động cơ học, phải khai báo rõ trong `effects`. Schema không thể tự hiểu mọi lời kể tự do, nên không thể bảo đảm tất cả loại kết quả cơ học chưa được mô hình hóa sẽ tự thay đổi Game State. Phần không hỗ trợ phải được mở rộng bằng Core command cụ thể, không đoán từ văn bản.

## Điều kiện áp dụng và atomic commit

- Mục tiêu phải tồn tại, đang ACTIVE, còn sống và có mặt trong Party ở thời điểm event được Core commit. Nếu tình huống chỉ có nhân vật ngoài Party, schema không tự dịch chuyển hoặc thêm nhân vật vào Party để hợp thức hóa hiệu ứng.
- Cùng lượt: Core áp dụng commands gameplay đã xác minh → áp Status INJURY và trừ HP bằng Core → đánh dấu Milestone event đã thực thi → tiến Turn → lưu một lần. Lỗi ở bất kỳ bước áp effect nào khiến cả lượt không được lưu.
- Status ID được xác định từ `checkpointId/eventId/effectIndex`, không phụ thuộc vào câu chữ GM. Mỗi event chỉ áp một lần, kể cả Save/Load hoặc retry.
- `CharacterState.injuries`, `statusIds`, `GameState.statuses` và `vitalState.currentHp` đều cập nhật cùng một state và serialize theo GameStateCodec.
- Bước 7 không sửa loot, NPC spawn, boss multiplier ×9, combat hay Milestone handoff từ Bước 6.

## Ranh giới và công việc tiếp theo

- GM vẫn là một mô hình sinh văn bản, receipt chỉ kiểm tra việc báo ID; chưa có bộ xác thực ngữ nghĩa đầy đủ để chứng minh mọi câu chữ GM khớp chính xác outcome. Game Core có thẩm quyền đối với effects; mô tả sai vẫn cần validator/repair có cấu trúc ở các bước sau.
- Tình tiết yêu cầu bị thương nhưng target không ở Party bị từ chối thay vì tự tưởng tượng nhân vật hiện diện. Gemini nên lập sự kiện hợp cảnh; hệ thống sau này có thể bổ sung semantic effect cho NPC ngoài Party khi Core có bằng chứng hiện diện rõ ràng.
- Exit/HiddenExitStreak/Random Encounter còn tồn tại ở các đường khác và sẽ được rà soát ở Bước 8; GM đã được tránh đọc các chỉ dẫn roaming RNG trái với Milestone tại những lượt có kế hoạch.
- Canon chưa có nguồn chính thức vẫn giữ UNKNOWN; không tự bổ sung tiểu sử/địa điểm/Entity.
- PR này nối tiếp các bước trước, chưa merge vào main và chưa phát hành.

## Regression tests

Kiểm thử có mục tiêu: injury giảm HP và tạo status thực; status/injuries vẫn tồn tại qua Save/Load; không replay khi event đã applied; mục tiêu vắng Party bị từ chối, không có partial save; loại effect không hỗ trợ và HP âm/quá giới hạn bị từ chối; injury không tự giết người hoặc phát EXP; STORY không có effects không làm thay đổi HP.
