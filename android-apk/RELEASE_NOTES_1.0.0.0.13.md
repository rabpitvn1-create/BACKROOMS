# BACKROOMS 1.0.0.0.13

Bản phát hành này đóng gói trạng thái `main` mới nhất sau `1.0.0.0.12`, bao gồm bản sửa false-positive canon softlock từ PR #349.

- Phiên bản hiển thị: **1.0.0.0.13**.
- Android versionCode: **121**.
- Bao gồm PR #349: sửa trường hợp `flag_patch` đã được thỏa mãn nhưng bị nhận nhầm là operation thất bại chỉ vì root không thay đổi.
- So sánh `flag_patch` nay bám shallow-object merge semantics của reducer, giữ nguyên kiểu JSON, chuẩn hóa root whitespace và không phụ thuộc thứ tự key.
- Các thay đổi thực sự không được áp dụng vẫn tiếp tục bị chặn; reducer permissions, canon gates và các operation type khác không bị nới lỏng.
- Repair feedback/diagnostics bổ sung bounded root và operation index mà không ghi giá trị flag nhạy cảm.
- Giữ nguyên tuyến AI: **Persistent Foundation → Gemini K1 → K2 → K3 → K4 → K5 → K6 (nếu được provision) → HAKU → LUNA → controlled failure**.
- Không thay đổi save schema hoặc provider routing contract.

CI của release tiếp tục chạy runtime patch audit, provider/Foundation verifiers, Gemini high-priority smoke, Kotlin/Java tests, APK build, packaged APK verification và xác minh GitHub Release bằng SHA-256.
