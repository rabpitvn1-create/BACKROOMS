> **RETIRED: TÀI LIỆU LỊCH SỬ, KHÔNG ÁP DỤNG CHO RUNTIME HIỆN HÀNH.** Story Milestone / Story Checkpoint cũ đã bị loại bỏ. Không dùng nội dung này làm hợp đồng gameplay hoặc thiết kế END-POINT mới.

> **Trạng thái tích hợp 2026-10-13:** Runtime và các sửa phụ thuộc đã vào main qua PR #595 (`820038c8`). Khi đối chiếu nhánh Bước 6, giữ nguyên evidence verifier, Boss thắng/thua, authored completion, safe restore và quota CP 50 turn/20 encounter của bản mới. Tài liệu bên dưới mô tả runtime hiện hành; save/history được bổ sung trong `STORY_SAVE_HISTORY_R01.md`. CI là nghiệm thu tự động; Gemini/Android gameplay thật vẫn pending.

# Bước 6/10 · Milestone Runtime v2

## Core và timeline

Due chỉ nhận đúng Turn tương đối và event chưa commit. Nếu state đã vượt một event chưa nhận, Core báo `milestone_missed_turn_restore_checkpoint`; không replay trễ hoặc tự đổi anchor. Tải manual checkpoint trước event là đường phục hồi rõ ràng. Rule/Inventory không được tăng Turn qua event đang đến hạn.

Receipt/event và Boss start được ghi trong cùng transaction Core. Các thao tác repository trên facade được khóa cùng callback prefetch. Load manual checkpoint đổi runToken để loại phản hồi đang bay từ timeline trước; current/prepared phải parse thành công trước khi thay state.

## Combat và completion

FINAL_ENTITY tạo một Boss instance HP×9; EXP×9 được cấp bởi Combat Core đúng instance khi tiêu diệt. Chỉ DEFEATED có bằng chứng award mới hoàn tất CP. Boss chỉ có thắng hoặc thua. Core chặn lệnh chạy thoát và không tự kết thúc trận khi né/di chuyển tích đủ escape progress; không lưu suspended Boss hoặc cung cấp nút đấu lại.

Địa chỉ tiếp theo lấy từ completion Gemini. CP thường dùng NEXT_CHECKPOINT; CP cuối dùng EXIT_LEVEL, exact target node, tổng CP và outcome tác giả. Không yêu cầu HiddenExitStreak, RNG Exit hoặc đi qua tuyến phụ. GM/client không được đổi main Level; việc đi lại hợp lệ trong cùng Level vẫn dùng topology. Core trình bày completion outcome một lần, không tự diễn giải thành HP/Status/Inventory mutation.

Prefetch dùng Canon/scene của target Level; prepared chưa phải lịch sử đã xảy ra. Core neo kế hoạch tại Turn kích hoạt thật, không tại Turn gửi request. Có thể nhận prepared khi Combat đang chạy hoặc sau kết quả đã xác minh, chịu được phản hồi muộn và duplicate callback.

## Kiểm chứng và giới hạn

Tests có mục tiêu gồm lịch exact Turn/missed event, Boss ×9, chặn Boss escape bằng lệnh và escape progress, chống thưởng trùng, 20 CP Level 0 → Sublevel hợp lệ của Level 1, prefetch trước/sau defeat, Save/Load và reject client đổi Level/Inventory vượt Turn.

Receipt ID vẫn được kiểm tra cấu trúc, nhưng không còn đủ để commit. Bridge yêu cầu một phản hồi kiểm định riêng qua transport Gemini hiện hữu: action/event/outcome phải có chứng cứ trích nguyên văn từ lời kể được nhận. Core kiểm tra verified, toàn bộ ID, quote và request hash gắn runToken/CP/Turn/plan/lời kể; lưu provenance cùng receipt trong transaction. Meta không commit sự kiện hoặc tăng Turn. Saved receipts thiếu evidence bị từ chối.

Đây là kiểm định ngữ nghĩa bằng mô hình cùng các ràng buộc Core xác định được, không phải chứng minh hình thức mọi cách diễn đạt. Unit tests dùng validator fixtures TEST_ONLY; chưa đánh giá false-positive/false-negative bằng Gemini thật hoặc Android thật. Không biến thương tích trong lời kể thành gameplay và không duy trì schema save cũ.



## CP minimum pacing and encounter quota — 2026-10-13

Every CP, at every Level, must schedule at least 50 relative Core turns and
at least 20 distinct Entity encounters. The final Boss counts once, leaving
at least 19 ordinary ENTITY events. Encounters must occupy distinct turns.
Repeated species are allowed; repeated receipts, loading and resuming the same
Boss never increase the count. Ordinary ENTITY means actual direct contact,
not a name mention, rumor, footprint, future plan or combat turn. It may be a
noncombat scene; it does not award HP/EXP/loot or authorize a narrated kill.

The parser rejects shorter or under-quota plans, unsupported entity keys and
multiple encounters on one turn. The planner and independent narrative verifier
receive these rules. Only accepted, verified event receipts count at runtime;
Core gives each encounter a stable identity preserved across manual load leases.
CP promotion additionally checks the actual relative Core turn and committed
encounter count, all events applied and verified Boss defeat. History records
turnCount, entityEncounterCount and encounter IDs with ledger sources; corrupt
counts/IDs are rejected during save validation. No old-save migration is added.

Tests use synthetic independent-validator responses and forced Combat settlement;
they verify quotas and state transitions, not live Gemini semantic accuracy or
Android gameplay. The 20-CP fixture now processes at least 1,000 relative turns
and 400 distinct encounters, with one Boss per CP.

Milestone đầu được yêu cầu ngay khi giao diện nhận Core state của new game, không chờ hành động đầu tiên. GM hiển thị popup khi thiếu active plan hoặc đã thắng Boss nhưng next plan chưa sẵn sàng; chặn hành động, hiển thị lỗi và cho thử lại khi planner thất bại. Callback UI gắn runToken/checkpointId để bỏ phản hồi cũ.
