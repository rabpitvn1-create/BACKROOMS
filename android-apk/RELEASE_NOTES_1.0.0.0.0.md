# BACKROOMS 1.0.0.0.0

Bắt đầu lại tên phiên bản phát hành theo yêu cầu của chủ dự án. Bản này được build từ main hiện tại, gồm các bản sửa combat, xử lý hành động sau combat, GM dùng lời kể rõ ràng hơn và in đậm bằng chứng đã phát hiện.

- Phiên bản hiển thị: **1.0.0.0.0**.
- Android versionCode: **108**, tiếp tục tăng từ 107; không đặt lại mã cập nhật.
- Giữ nguyên applicationId và định dạng save hiện tại.
- Định tuyến AI: HAKU → LUNA; Gemini vẫn khóa khỏi runtime.
- Sau khi APK mới được xuất bản và xác minh, xóa 87 release cũ cùng 87 tag tương ứng đã được ghi nhận trong danh sách reset. Việc xóa release cũng xóa 88 APK đính kèm cũ.
- Không xóa mã nguồn, lịch sử commit hoặc workflow run.

Quy trình phát hành chạy kiểm tra nội dung, runtime, các test Node/Kotlin, build APK, kiểm tra chữ ký và nội dung gói, rồi tải lại APK đã xuất bản để đối chiếu SHA-256 trước khi dọn release cũ.
