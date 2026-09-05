# Backroom 1.0.0.0.14 Debug

Bản phát hành này đóng gói trạng thái `main` mới nhất sau `1.0.0.0.13`, bao gồm các thay đổi đã merge từ PR #351, PR #352 và PR #353.

- Phiên bản hiển thị: **1.0.0.0.14**.
- Android versionCode: **122**.
- Bao gồm PR #351: thêm passive Entity skill **Deathly Stillness** cho Hotel Corpse Lure, proc 26% đúng trong Entity-response turn, gây tối đa -1 Momentum và có READ counterplay; regression test đã được sửa để dùng action `SEARCH` đúng với classifier runtime.
- Bao gồm PR #352: sửa chuỗi state gate khi Lucia tự giới thiệu rồi được mời gia nhập Party; identity được lưu bằng structured operation, invitation đi qua consent/validated candidate pipeline, và các encounter/presence/follower flag vẫn thuộc quyền engine.
- Bao gồm PR #353: thêm AUTO skill **Anchorline Burst** cho Lucia, proc 40% trong lượt chiến đấu hợp lệ của Lucia và gây 103% sát thương vũ khí M4A1 với log combat gọn.
- Giữ nguyên Persistent Foundation và routing ưu tiên Gemini → HAKU → LUNA → controlled failure.
- Release workflow tiếp tục chạy runtime patch audit, runtime contracts, Kotlin tests, APK build, packaged APK verification và Gemini live smoke trước khi phát hành.
