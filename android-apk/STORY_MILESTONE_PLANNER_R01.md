> **Trạng thái hiện hành:** Cả Milestone đầu và prefetch Milestone kế tiếp đều dùng duy nhất SOL (`vgpt/gpt-6.1-sol`) qua transport có sẵn trong `MainActivity`, `max_tokens=12288`, `temperature=0.5`, `reasoning_effort=low`. Không fallback sang Gemini hay GM writer. Core tiếp tục kiểm tra completion v2, tối thiểu 50 turn/20 encounter mỗi CP và planning lease; JSON sai bị từ chối, người chơi có thể thử lại. CI không thay thế nghiệm thu với SOL thật trên Android. Phần Bước 5 bên dưới là tài liệu lịch sử của triển khai Gemini trước khi chuyển sang SOL. Xem `STORY_MILESTONE_R01.md` và `STORY_MILESTONE_RUNTIME_R01.md`.

# Bước 5/10: Gemini Milestone Planner R01

## Kiến trúc đã tích hợp

- **Một bộ biên kịch Gemini**. 5 `BuildConfig.GEMINI_API_KEY_1..5` là **5 khóa fallback** của transport `geminiTextPolicy` hiện hữu trong `MainActivity`, không phải 5 Gemini agent hay biểu quyết đồng thuận.
- `StoryMilestonePlannerPrompt` yêu cầu JSON chuẩn `StoryMilestonePlan` của Bước 4: event/outcome bắt buộc, lịch Turn tương đối, một FINAL_ENTITY, HP×9 và EXP×9.
- Input là thẻ Canon offline truy xuất qua `KnowledgeContextEngine.build`, state Core đã chiếu (location, current node, party, character, continuity rút gọn), và danh sách Entity thực được `CombatRuntime` hỗ trợ.
- Khi lượt chơi thông thường đầu tiên của Checkpoint 1 bắt đầu và chưa có plan, `GameCoreFacade.milestonePlanRequest()` trả về một planning lease có `checkpointId`, `startTurn`, `runToken`. `MainActivity` gọi Gemini **một lần theo cơ chế fallback key**; sau đó `GameCoreFacade.installMilestonePlan()` kiểm tra schema và lưu toàn bộ plan vào Game State.
- Các lượt tiếp theo chỉ thấy trạng thái `needed=false`: **không gửi Gemini tạo lại**.
- Nếu model trả JSON sai, Entity không được hỗ trợ, hoặc request fail, không ghi plan sai vào Core và không cho lượt đầu tiên vượt qua phần Milestone bắt buộc. Hệ thống phát lỗi và người chơi có thể thử lại.
- Nếu người chơi reset/new game trong khi request đang chạy hoặc turn tiến triển, phản hồi trễ không được ghi lên phiên chơi mới nhờ `runToken + checkpointId + startTurn`. Mã token không chứa khóa API.
- Cơ chế lưu/tải plan dùng `GameStateCodec` vốn đã lưu `world`. Không tạo database riêng, không gọi Google Drive hoặc Internet ở mỗi Turn.

## Hạn chế của Bước 5

- Việc Gemini **tự kích hoạt khi Combat boss bắt đầu** để chuẩn bị Checkpoint kế tiếp thuộc Bước 6 (hàm `StoryMilestonePlan.prepareNextDuringCombat` từ Bước 4 đã có hợp đồng).
- Việc dispatcher bắt GM lồng các event/outcome vào câu chuyện theo Turn, áp HP/EXP ×9 thật và chuyển CP cũng thuộc Bước 6–7; ở Bước 5, có dữ liệu Milestone trong Core nhưng GM vẫn chưa tiêu thụ trực tiếp.
- CP20 → Level 1 cần routing Level và số Checkpoint chưa chốt. Không tự chọn thay tác giả.
- PR #590 chuẩn hóa thêm 41 thẻ Canon Drive offline còn nằm ở nhánh độc lập; khi PR đó được tích hợp, bộ truy xuất hiện có sẽ được planner dùng tự nhiên. Chưa nhận rằng toàn bộ legacy provenance đã giải quyết.
- Không thay GM Sol/Haku/Gehihi, không đổi RNG/Hidden Exit Streak ở bước này.
- Không tạo thêm scheduler, worker nền, thư viện HTTP hay lớp AI riêng. Transport fallback Gemini đã có tiếp tục tái sử dụng.

## Xác minh

Kiểm thử chuyên biệt gồm: request chỉ khi plan chưa có; lưu rồi không tạo lại; save/load plan; chặn response sau reset hoặc Turn thay đổi; từ chối kế hoạch invalid; prompt chỉ dùng danh sách Entity có trong Combat Core và gọn. CI Build Backroom APK phải xác minh Kotlin tests, unit regressions, APK contract trước khi chốt.
