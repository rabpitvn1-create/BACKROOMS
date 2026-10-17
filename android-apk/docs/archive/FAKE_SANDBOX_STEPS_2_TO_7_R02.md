> **RETIRED: TÀI LIỆU LỊCH SỬ, KHÔNG ÁP DỤNG CHO RUNTIME HIỆN HÀNH.** Story Milestone / Story Checkpoint cũ đã bị loại bỏ. Không dùng nội dung này làm hợp đồng gameplay hoặc thiết kế END-POINT mới.

# Fake Sandbox · Đối chiếu tích hợp Bước 2–7 · 2026-10-13

PR #595 (`820038c8`) đã tích hợp và sửa các phần Canon, Checkpoint, Milestone, planner, runtime và continuity từ những nhánh Bước 2–7. PR #598 được đối chiếu với main này; các implementation cũ được thay thế bởi bản đã kiểm thử, không đưa lại schema `levelEnd`, giới hạn 48 OPEN hoặc receipt chỉ dựa trên ID. PR #589 bổ sung đồng bộ damage/log với contact của animation.

## Hợp đồng runtime hiện hành

- Level 0 có 20 Story Checkpoint. CP khác Level và node; tổng CP Level sau do Gemini chọn và Core kiểm tra.
- Milestone v2 gồm `schemaVersion`, `checkpointId`, `events`, `completion`. Events có action/event/outcome và Turn tương đối. Completion là `NEXT_CHECKPOINT` hoặc `EXIT_LEVEL`; CP cuối có targetNodeId, nextCheckpointTotal và outcome được Gemini biên kịch.
- Mỗi CP ít nhất 50 turn và 20 encounter riêng biệt, gồm 19 ENTITY thường và một Boss cuối. Encounter phải được nhận đúng Turn và kiểm định; planned facts, nhắc tên, reload không cộng chỉ tiêu.
- Boss HP/EXP ×9 áp dụng theo instance, không tăng ATK/DEF. Chỉ Core-confirmed defeat hoàn tất CP; Boss chỉ thắng/thua, không cho chạy thoát hoặc đấu lại.
- Dispatcher dùng exact Turn; event bỏ lỡ yêu cầu phục hồi checkpoint, không replay trễ. Random Entity/Exit và HiddenExitStreak không quyết định Milestone khi có active plan.
- Một planner dùng 5 keys fallback hiện hữu. Lease gắn run/CP/turn; prefetch dùng target Canon và chịu được phản hồi muộn. Completion không yêu cầu MAIN-only hoặc đi đủ tuyến phụ.
- Receipt cần separate verifier, exact narrative quotes và request hash. Core kiểm tra ID/context; kiểm định ngữ nghĩa vẫn là đánh giá của mô hình, chưa phải chứng minh hình thức.
- Story memory EVENT/ENTITY/CLUE có provenance; planned chưa commit. OPEN chưa giải quyết được giữ trong save, history dài được archive và truy xuất có ngân sách. Ledger không đổi HP/Status/Combat/Equipment/Inventory.
- Stats và CP/Level history dùng committed evidence, actual turn/encounter counts và verified defeats. Save lỗi không khởi tạo âm thầm rồi ghi đè; valid manual restore đổi response lease và giữ encounter identity. Không migration save cũ.

## Tài liệu kiểm tra

- `FAKE_SANDBOX_STEP1_CANON_AUDIT_R02.md`: audit theo mốc nguồn, không dùng runtime làm lore authority.
- `STORY_CHECKPOINT_R01.md`: cấu trúc CP và lịch sử phạm vi Bước 3.
- `STORY_MILESTONE_R01.md`: contract v2 và quota hiện hành.
- `STORY_MILESTONE_PLANNER_R01.md`: planner và lease.
- `STORY_MILESTONE_RUNTIME_R01.md`: dispatcher, Boss, evidence, completion và quota.
- `STORY_CONTINUITY_R02.md`: verified memory và archive.
- `STORY_SAVE_HISTORY_R01.md`: counters/history/save recovery.

## Giới hạn nghiệm thu

CI kiểm thử synthetic 20 CP, 50 turn/20 encounter mỗi CP, Save/Load, early/late prefetch, chặn Boss escape, stale evidence, history corruption và Canon budgets. Fixtures mô phỏng verifier và forced Combat settlement; không phải 20 trận Boss thật. Gemini thật, adversarial semantics, Android gameplay và measured device performance vẫn chưa nghiệm thu. 42 provenance legacy, hồ sơ Iris/An Nhiên/Lucia thiếu và node conflicts 0.1/0.7 vẫn deferred. Level 7–10 reference-only, chưa hỗ trợ runtime transition. Không biến ví dụ hoặc fixture thành Lore Canon.
