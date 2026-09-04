# BACKROOMS 1.0.0.0.6

Bản phát hành này đóng gói bản sửa Issue #311 / PR #312 cho phần trình bày combat và công cụ chẩn đoán lỗi in-game.

- Phiên bản hiển thị: **1.0.0.0.6**.
- Android versionCode: **114**, tăng từ 113 để giữ đường nâng cấp hợp lệ.
- Sửa bóng dưới chân nhân vật bị lệch khi vào Entity/combat: shadow dùng cùng offset sàn combat `28px` với character overlay, trong khi layout exploration giữ nguyên.
- Thay nút Snapshot đã vô hiệu hóa ở trang thứ hai bằng nút **Xuất log TXT**.
- Log TXT dùng Android Storage Access Framework để người chơi tự chọn nơi lưu, không cần quyền truy cập bộ nhớ diện rộng.
- Log chứa version/thời gian, Level/location, turn và action gần nhất, Party/active actor, combat/Entity context, provider status, recent game log và runtime/provider errors gần đây.
- Runtime diagnostics được giới hạn tối đa **80 sự kiện** để tránh tăng dữ liệu không kiểm soát.
- Log thực hiện redaction cho HAKU/LUNA/Gemini credentials, Bearer token và token dạng `sk-*`; không cố ý xuất API key/secret.
- Không thay đổi gameplay authority, combat math, provider routing order, save schema, loot hoặc canon.

Quy trình phát hành chạy runtime patch audit, final runtime contracts, provider/inventory verifiers, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại GitHub Release bằng SHA-256.
