# Backroom 1.0.0.0.15 Debug

Bản phát hành này đóng gói trạng thái `main` mới nhất sau `1.0.0.0.14`.

- Phiên bản hiển thị: **1.0.0.0.15**.
- Android versionCode: **123**.
- Bao gồm PR #355: combat overlay của Syvial dùng asset riêng `Syvial.png`, trong khi metadata avatar ngoài combat vẫn giữ nguyên.
- Giữ nguyên Persistent Foundation và routing ưu tiên Gemini → HAKU → LUNA → controlled failure.
- Giữ Gemini High Priority smoke đã được harden: credential/model access được kiểm tra riêng, live generation vẫn xác minh pool failover, lỗi auth/model/payload vĩnh viễn vẫn làm CI đỏ, còn provider-wide 408/429/5xx được phân loại là transient availability thay vì code regression.
- Release workflow tiếp tục chạy runtime patch audit, runtime contracts, Kotlin tests, APK build, packaged APK verification và Gemini high-priority verification trước khi phát hành.
