# Backroom 1.0.0.0.16 Debug

Bản phát hành này đóng gói trạng thái `main` mới nhất sau `1.0.0.0.15`.

- Phiên bản hiển thị: **1.0.0.0.16**.
- Android versionCode: **124**.
- Bao gồm PR #359: thêm AUTO skill **Crossline Burst** cho Lucia và các regression fix đi kèm sau khi đồng bộ `main`.
- Bao gồm PR #360: sửa state gate của Lucia để `identityKnown` được giữ đúng ở lượt first-contact và lời mời ở lượt sau có thể commit Lucia vào Party.
- Giữ nguyên luật: first contact không tự tuyển Lucia; gặp + gia nhập trong cùng lượt vẫn bị chặn; Party ADD cần danh tính đã commit từ lượt trước và lời mời/chấp nhận rõ ràng.
- Release CI vẫn chạy runtime patch audit, runtime contracts, Kotlin tests, APK build và packaged APK verification.
- Live Gemini provider API smoke được bỏ qua cho release-bump PR và merge push tương ứng để không tiêu tốn API quota; static Gemini authority/key-pool checks vẫn chạy.
