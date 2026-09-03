# Backroom 1.4.4.5 Debug

Bản 1.4.4.5 đóng gói trạng thái `main` mới nhất sau v1.4.4.4, gồm provider fallback đã sửa, Lucia first-contact gate và cập nhật Entity codex mới nhất.

## AI provider fallback

- Gemini 3.6 Flash thử tuần tự K1 -> K2 -> K3 -> K4 -> K5.
- Mỗi Gemini key có một request hữu hạn; read timeout của một key chuyển ngay sang key kế tiếp thay vì dừng toàn bộ lượt.
- Sau khi các Gemini lane không khả dụng, runtime fallback sang Luna rồi Haku.
- Luna dùng `LUNA_MODEL` cấu hình trực tiếp, không còn pre-chat `/models` discovery gây thêm độ trễ.
- Haku chỉ là provider fallback cuối, không còn lớp biên tập hậu kỳ hay API call lần hai sau một response thành công.
- Runtime status hiển thị lane/provider đang được thử để quan sát fallback thực tế.

## Story / companion gate

- Lucia không thể được đưa vào party trước first-contact hợp lệ; party join được khóa sau mốc gặp mặt đầu tiên.
- Giữ nguyên authority của Core, RNG, quest, combat, inventory và story progression.

## Entity codex

- Đóng gói cập nhật Biological Pipeline và Beast of Level 5 mới nhất từ `main`.
- Entity vẫn chỉ là hazard/combat pressure và không biết hoặc điều khiển hidden escape solution.

## Phiên bản và phát hành

- Android `versionCode 104`, `versionName 1.4.4.5`.
- APK debug: `Backroom-1.4.4.5-debug.apk`.
- Full Preflight gồm Level validation, provider configuration gate, patch-chain/orphan audit, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification.
- Push-main workflow tạo tag/release `v1.4.4.5`, tải lại APK đã publish, đối chiếu SHA-256 và chạy lại packaged verification.
