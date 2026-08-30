# Backroom 1.3.2 Debug

Bản 1.3.2 gom trạng thái gameplay mới nhất trên `main` sau 1.3.1 và hợp nhất quy trình Preflight + Build/Release thành một pipeline duy nhất.

## Thay đổi chính

- Khôi phục và hoàn thiện nội tại ẩn `Devil Blessing` của Kai cho Party: đồng đội nhận +5% Tấn Công, Phòng Thủ, Né Tránh và Máu; Kai không nhận buff của chính mình.
- Sửa phạm vi áp dụng Né Tránh của `Devil Blessing` và giữ Kai miễn trừ đúng theo cơ chế `Sparda's Son` của `Kai - The Devil Within`.
- Làm chuỗi patch `Devil Blessing` ổn định/idempotent sau các bước Việt hóa và tương thích với Syvial.
- Viết lại mô tả kỹ năng nhân vật theo tiếng Việt tự nhiên hơn, giữ nguyên cơ chế gameplay và tên kỹ năng cần thiết.
- Hợp nhất `BACKROOMS Preflight` và `Build Backroom` thành một workflow CI/Release duy nhất: kiểm tra cấu hình, smoke test Luna, chạy toàn bộ runtime patch chain, contract checks, Kotlin tests, build APK, kiểm tra package/chữ ký/zipalign/assets, rồi mới cho phép phát hành.
- Release tự kiểm tra lại APK đã tải từ GitHub Release bằng SHA-256 và bộ packaged-contract checks.

## Phiên bản Android

- `versionCode 87`
- `versionName 1.3.2`

Đây là APK debug dành cho kiểm thử. Android có thể yêu cầu cho phép cài ứng dụng từ nguồn không xác định.
