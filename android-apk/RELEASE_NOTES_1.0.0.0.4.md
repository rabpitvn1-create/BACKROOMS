# BACKROOMS 1.0.0.0.4

Bản phát hành này sửa lỗi provider HAKU trả nội dung không phải JSON hợp lệ nhưng runtime vẫn coi request là thành công, khiến lượt dừng ở lỗi `AI trả JSON không hợp lệ` thay vì chuyển sang LUNA.

- Phiên bản hiển thị: **1.0.0.0.4**.
- Android versionCode: **112**, tăng từ 111 để giữ đường nâng cấp hợp lệ.
- Router giờ xác thực output contract ngay bên trong từng provider attempt.
- Nếu HAKU trả malformed JSON hoặc response rỗng/không parse được, lỗi đó được coi là provider/runtime failure và route chuyển sang **LUNA**.
- LUNA cũng phải vượt cùng JSON validation trước khi response được trả về cho gameplay pipeline.
- Thêm regression test cho trường hợp HAKU trả prose thường thay vì JSON, xác nhận LUNA được gọi đúng một lần và Gemini không tham gia routing.
- Giữ nguyên thứ tự **HAKU → LUNA → controlled failure**; Gemini tiếp tục bị khóa khỏi runtime.
- Không thay đổi gameplay state authority, combat, encounter/loot, save format hay canon.

Quy trình phát hành chạy provider-routing regression, runtime patch audit, final runtime contracts, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại GitHub Release bằng SHA-256.
