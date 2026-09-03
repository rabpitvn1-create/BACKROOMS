# BACKROOMS 1.0.0.0.1

Bản phát hành này đưa các thay đổi combat presentation và Snapshot mới nhất từ `main` vào APK phát hành kế tiếp.

- Phiên bản hiển thị: **1.0.0.0.1**.
- Android versionCode: **109**, tăng từ 108 để giữ đường nâng cấp hợp lệ.
- Combat skill narration được rút gọn theo authority hiện tại, tránh semantics auto-proc cũ lọt vào skill chủ động.
- Snapshot combat hiển thị nameplate dạng `KAI [HP/MAX]` và Entity `[HP/MAX]` bằng font DFVN Broad với bóng chữ.
- Hit feedback được tăng lực nhưng vẫn chỉ là presentation, không thay đổi combat state hay damage math.
- Auto Light Flicker dùng confidence-filtered pixel emitter mask thay cho glow ellipse rộng.
- Giữ nguyên applicationId, save format, combat/AP formulas, encounter/loot authority và routing **HAKU → LUNA → FAIL**; Gemini vẫn khóa khỏi runtime.

Quy trình phát hành chạy runtime patch audit, final runtime contracts, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại APK GitHub Release bằng SHA-256.
