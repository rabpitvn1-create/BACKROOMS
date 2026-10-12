# Bước 6/10 · Milestone Runtime v2

## Core và timeline

Due chỉ nhận đúng Turn tương đối và event chưa commit. Nếu state đã vượt một event chưa nhận, Core báo `milestone_missed_turn_restore_checkpoint`; không replay trễ hoặc tự đổi anchor. Tải manual checkpoint trước event là đường phục hồi rõ ràng. Rule/Inventory không được tăng Turn qua event đang đến hạn.

Receipt/event và Boss start được ghi trong cùng transaction Core. Các thao tác repository trên facade được khóa cùng callback prefetch. Load manual checkpoint đổi runToken để loại phản hồi đang bay từ timeline trước; current/prepared phải parse thành công trước khi thay state.

## Combat và completion

FINAL_ENTITY tạo một Boss instance HP×9; EXP×9 được cấp bởi Combat Core đúng instance khi tiêu diệt. Chỉ DEFEATED có bằng chứng award mới hoàn tất CP. ESCAPED không hoàn tất: lưu snapshot Boss, HP và encounter ID; nút Đối đầu lại Boss phục hồi cùng instance, không nhân HP/EXP lần nữa và không khôi phục HP người chơi từ lời kể.

Địa chỉ tiếp theo lấy từ completion Gemini. CP thường dùng NEXT_CHECKPOINT; CP cuối dùng EXIT_LEVEL, exact target node, tổng CP và outcome tác giả. Không yêu cầu HiddenExitStreak, RNG Exit hoặc đi qua tuyến phụ. GM/client không được đổi main Level; việc đi lại hợp lệ trong cùng Level vẫn dùng topology. Core trình bày completion outcome một lần, không tự diễn giải thành HP/Status/Inventory mutation.

Prefetch dùng Canon/scene của target Level; prepared chưa phải lịch sử đã xảy ra. Core neo kế hoạch tại Turn kích hoạt thật, không tại Turn gửi request. Có thể nhận prepared khi Combat đang chạy hoặc sau kết quả đã xác minh, chịu được phản hồi muộn và duplicate callback.

## Kiểm chứng và giới hạn

Tests có mục tiêu gồm lịch exact Turn/missed event, Boss ×9, escape/resume giữ instance/HP, chống thưởng trùng, 20 CP Level 0 → Sublevel hợp lệ của Level 1, prefetch trước/sau defeat, Save/Load và reject client đổi Level/Inventory vượt Turn.

Receipt ID vẫn chỉ là kiểm tra cấu trúc; semantic evidence cho lời GM phải được sửa riêng trước khi nghiệm thu #594. Không biến thương tích trong lời kể thành gameplay, không duy trì schema save cũ. Gemini thật và Android thật chưa được xác minh bởi unit tests.
