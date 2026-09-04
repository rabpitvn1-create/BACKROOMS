# BACKROOMS 1.0.0.0.12

Bản phát hành này đóng gói trạng thái `main` mới nhất sau `1.0.0.0.11`, giữ nguyên kiến trúc runtime/provider hiện tại và bổ sung các thay đổi gameplay đã được kiểm chứng gần nhất.

- Phiên bản hiển thị: **1.0.0.0.12**.
- Android versionCode: **120**.
- Bao gồm Entity skill **Predatory Window** đã vào `main` qua PR #346.
- Bao gồm AUTO skill thứ năm của Syvial, **Torque Sever**, qua PR #347: proc 40% trên đúng lượt actor của Syvial, 106% GodKiller weapon damage qua Armor, không thêm status và giữ log combat gọn.
- Giữ nguyên tuyến AI: **Persistent Foundation → Gemini K1 → K2 → K3 → K4 → K5 → K6 (nếu được provision) → HAKU → LUNA → controlled failure**.
- Giữ nguyên hệ thống diagnostics từ `1.0.0.0.11`, gồm `ERROR_SUMMARY`, `ROOT_CAUSE`, `ERROR_TIMELINE`, provider/Foundation telemetry và secret redaction.
- Không thay đổi schema save, API routing contract hoặc hành vi release ngoài việc tăng version và đóng gói `main` mới nhất.

CI của release tiếp tục chạy runtime patch audit, provider/Foundation verifiers, Gemini high-priority smoke, Kotlin/Java tests, APK build, packaged APK verification và xác minh GitHub Release bằng SHA-256.
