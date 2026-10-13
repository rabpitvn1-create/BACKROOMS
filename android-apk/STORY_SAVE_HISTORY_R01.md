# SaveStats và Story/Level history

Core lưu bốn bộ đếm: committedEvents (Milestone receipts thực sự đã commit), defeatedBosses (Core defeat có award marker), completedCheckpoints và completedLevels. Projection cấp thêm confirmed/resolved/open facts từ ledger. Planned/prepared không được tính như sự kiện đã xảy ra.

Mỗi CP hoàn tất có ID gắn encounter, visit Level, start/end Turn, eventRefs tới ledger/history archive, verified Boss result và completion. Level History giữ các CP refs, số event đã commit, outcome Gemini đã định, Turn/nguồn kết thúc và IDs của facts còn tiếp diễn. Không sinh summary lore hoặc cập nhật gameplay từ prose.

DEFEATED chưa có next plan chỉ tăng bộ đếm defeat, chưa ghi CP/Level completion. Callback muộn chỉ commit handoff một lần. Boss không có trạng thái ESCAPED hoặc resume; chỉ verified defeat cho phép handoff.

Decode manual checkpoint xác thực active/prepared, receipts/evidence, counters/history/source refs và provenance ledger trước khi thay slot hiện hành. Save bị lỗi decode phải báo lỗi, không âm thầm chơi tiếp từ GameState.initial rồi ghi đè. Người chơi có thể chủ động tải manual checkpoint hợp lệ để phục hồi; load đổi runToken, giữ visit/history và loại callback từ timeline trước.

Không migration hoặc adapter save cũ. Các tests synthetic kiểm tra 20 CP → Level mới, counters/history đúng một lần, Save/Load giữa Combat/late prefetch, current slot lỗi và manual recovery, reject stats/history hỏng, 100 OPEN/archived facts và hai encounter cùng Entity key.

Code nằm ở PR gốc #595 cùng dependency đã xác minh của #590/#592/#593/#594; chưa merge hay phát hành. Gemini thật, semantics adversarial và gameplay Android thật chưa được chứng nhận bởi các tests này. Context/prompt được giới hạn theo toàn record; chưa có số đo hiệu năng trên thiết bị.
