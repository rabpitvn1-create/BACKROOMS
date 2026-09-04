# BACKROOMS 1.0.0.0.3

Bản phát hành này đóng gói trạng thái `main` sau khi hoàn tất và merge PR #303, đồng thời giữ nguyên model WorldDirector production hiện hành vì candidate V2 chưa vượt đủ promotion gates.

- Phiên bản hiển thị: **1.0.0.0.3**.
- Android versionCode: **111**, tăng từ 110 để giữ đường nâng cấp hợp lệ.
- Giữ lại tooling và báo cáo distillation WorldDirector V2 để tiếp tục nghiên cứu/benchmark về sau.
- Candidate V2 đã được đánh giá tốt hơn V1 trên teacher test nhưng **không được promote vào runtime** do chưa đạt toàn bộ ngưỡng accepted accuracy, per-label recall và teacher agreement.
- Production `backrooms_director.tflite` không bị thay thế bởi candidate V2.
- Các workflow Haku trả phí dùng một lần trong thí nghiệm đã được loại bỏ; release này không thêm cơ chế tự động gọi Haku cho training.
- Routing Game Master production giữ nguyên **HAKU → LUNA → FAIL**; Gemini vẫn bị khóa khỏi runtime routing.
- Giữ nguyên combat authority, encounter/loot authority, save format và các runtime contracts đã được kiểm chứng trước đó.

Quy trình phát hành chạy Level/content validation, runtime patch audit, provider routing verification, inventory verification, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại GitHub Release bằng SHA-256.
