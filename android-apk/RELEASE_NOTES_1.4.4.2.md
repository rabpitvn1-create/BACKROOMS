# Backroom 1.4.4.2 Debug

Bản 1.4.4.2 đóng gói trạng thái `main` mới nhất sau v1.4.4.1, nổi bật với combat turn-based Party đã được merge trong PR #239.

## Turn-based Party combat

- Party hỗ trợ tối đa 7 thành viên; Kai luôn mở đầu mỗi encounter.
- Cả Party dùng chung thanh AP `0/7`.
- `ATK` và `DEFEND` cộng `+1 AP`; skill thường tốn `1 AP`; UTM/ULTIMATE tốn `2 AP`.
- Combat chuyển actor tuần tự và projection chỉ expose actor đang có lượt để UI đổi overlay theo nhân vật hiện tại.
- Popup Skill trong combat chỉ hiển thị tên skill và AP cost; description tiếp tục nằm trong Character Status.
- Combat log giữ kết quả authoritative từ engine: damage, HP còn lại, AP delta và hiệu ứng áp dụng.
- Entity/boss phase, defeat cleanup và loot authority vẫn đi qua runtime hiện có thay vì để GM tự quyết định gameplay state.

## Phiên bản và phát hành

- Android `versionCode 101`, `versionName 1.4.4.2`.
- APK debug: `Backroom-1.4.4.2-debug.apk`.
- Workflow chỉ tạo `v1.4.4.2` khi tag/release chưa tồn tại, sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged verification.
