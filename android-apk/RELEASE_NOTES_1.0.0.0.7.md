# BACKROOMS 1.0.0.0.7

Bản phát hành này đưa Persistent Foundation vào runtime Android để chuẩn bị ngữ cảnh canon cục bộ, bền vững và nhất quán giữa các bước xử lý một lượt.

- Phiên bản hiển thị: **1.0.0.0.7**.
- Android versionCode: **115**, tăng từ 114 để giữ đường nâng cấp hợp lệ.
- Biên dịch canon, Level, Story, Party, gameplay catalog và writing rules đóng gói thành sáu section xác định.
- Lưu các object/manifest bất biến theo SHA-256 trong vùng `filesDir/foundation`, tách biệt hoàn toàn với Game State Core save.
- Chỉ kích hoạt Foundation manifest hoàn chỉnh bằng active pointer nguyên tử; dữ liệu hỏng bị cô lập thay vì xóa hoặc sửa save.
- Dùng hai local worker cùng job ledger bền vững, lease recovery và cơ chế thực thi idempotent; không gọi API để dựng Foundation lúc khởi động.
- Pin cùng một manifest cho writer, canon audit, character audit và repair trong suốt một lượt, tránh trộn context khi background build hoàn tất giữa chừng.
- Mỗi vai trò chỉ nhận turn slice có ngân sách riêng thay vì toàn bộ knowledge database.
- Writer, repair và audit response được kiểm tra schema ngay trong từng provider attempt để Haku có thể fallback sang Luna khi response sai cấu trúc hoặc ngữ nghĩa.
- Áp dụng deadline đơn điệu 75 giây dùng chung cho provider timeouts và audit futures; công việc audit quá hạn được hủy.
- Giữ nguyên Game State Core authority, save schema, gameplay math, canon và tuyến provider đang hoạt động **HAKU → LUNA → controlled failure**.
- Gemini vẫn bị khóa khỏi runtime routing; cấu hình hiện có được giữ lại để tái kích hoạt trong một thay đổi riêng có model policy được phê duyệt.

Quy trình phát hành chạy runtime patch audit, Foundation/provider/runtime verifiers, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại GitHub Release bằng SHA-256.
