# Narrative continuity · verified facts v2

StoryFactLedger chỉ giữ EVENT/ENTITY/CLUE của câu chuyện; không có API đổi HP, Status, Combat, Equipment hoặc Inventory. Facts không tự thành Canon mới hoặc kiến thức chung của mọi nhân vật.

ADD/RESOLVE cần phản hồi kiểm định riêng, quote nguyên văn và request token gắn narrative/proposal/CP/Turn. RESOLVE cấp nguyên fact gốc cho validator, chỉ đóng đúng ID đang OPEN. Milestone storyFacts chỉ commit sau receipt và evidence; prepared/future plans không được ghi vào memory.

Mỗi fact có ID riêng, Checkpoint/Turn/node nguồn, source, quote và request token. Fact COMMITTED vẫn có resolutionState=OPEN; RESOLVE giữ nguyên nguồn gốc và thêm provenance giải quyết. `subject + kind` định danh một fact OPEN: cùng nội dung là idempotent, khác nội dung phải resolve rõ ràng.

Bỏ cổng 48 OPEN. Facts chưa giải quyết luôn còn trong save; lịch sử vượt 96 recent được chuyển sang archive có chứng cứ, không xóa. Context truy xuất theo query/actor, facts cũ/mới và cửa sổ luân phiên, bị giới hạn ký tự và báo số facts bị lược. Facts bị lược khỏi context vẫn chưa resolve và có thể lấy lại bằng subject/ID.

Schema ledger v2, không migration save v1. Tests TEST_ONLY kiểm tra source/evidence, reject ADD/RESOLVE thiếu bằng chứng, ENTITY có Core evidence, không mutation gameplay, 100 OPEN giữ qua Save/Load và lịch sử archived không mất chứng cứ. Chất lượng kiểm định ngữ nghĩa và recall trên Gemini/Android thật vẫn cần nghiệm thu thực tế.
