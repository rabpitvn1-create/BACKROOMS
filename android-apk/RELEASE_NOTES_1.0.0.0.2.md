# BACKROOMS 1.0.0.0.2

Bản phát hành này đóng gói các chỉnh sửa combat presentation mới nhất đã merge từ PR #297.

- Phiên bản hiển thị: **1.0.0.0.2**.
- Android versionCode: **110**, tăng từ 109 để giữ đường nâng cấp hợp lệ.
- Snapshot combat giảm kích thước nameplate; tên ngắn giữ một dòng, tên Entity/Character dài tự tách thành tên ở trên và `[HP/MAX]` ở dưới để HP không bị cắt.
- Combat ATTACK thường chuyển sang các dòng sự kiện ngắn dạng bullet, gồm hit/miss, phản công Entity, hồi HP, Devil Trigger và Action Point hiện tại.
- Loại bỏ khỏi ATTACK summary các đoạn prose dài kiểu `HÀNH ĐỘNG CỦA ĐỘI`, phần trăm evasion, mô tả giành áp lực, AP tăng từ X lên Y và dòng chuyển lượt dài.
- Giữ nguyên damage, RNG, AP calculation, Entity AI, encounter/loot authority, save format và routing **HAKU → LUNA → FAIL**; Gemini vẫn khóa khỏi runtime.

Quy trình phát hành chạy runtime patch audit, final runtime contracts, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại APK GitHub Release bằng SHA-256.
