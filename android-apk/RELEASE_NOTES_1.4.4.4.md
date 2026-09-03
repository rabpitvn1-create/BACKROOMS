# Backroom 1.4.4.4 Debug

Bản 1.4.4.4 đóng gói trạng thái `main` mới nhất sau v1.4.4.3, tập trung vào đơn giản hóa AI text routing, cập nhật Entity codex và tiếp tục hourly story progression.

## AI routing

- Loại bỏ lớp biên tập Haku khỏi player-facing prose, không còn API call hậu kỳ lần hai sau Gemini.
- Game Master text dùng một model duy nhất: Gemini 3.6 Flash.
- Fallback credential theo thứ tự K1 -> K2 -> K3 -> K4 -> K5, mỗi key thử một lần rồi chuyển key tiếp theo khi lane key lỗi.
- Không còn Luna fallback trong `generateText` và CI không còn bắt buộc smoke Luna/Haku.
- Conditional audit vẫn tránh key vừa viết response trước, rồi mới dùng lại key đó cuối cùng nếu các key còn lại đều không khả dụng.
- Gameplay state, canon validation, ops, dice, combat, loot, evidence và story authority không thay đổi bởi việc đơn giản hóa provider routing.

## Entity codex

- Bổ sung Hostile Faceling và Predatory Window vào Entity codex supplement.
- Các Entity tiếp tục chỉ là hazard/combat pressure; không được biết hoặc điều khiển hidden escape solution.

## Story

- Hourly main-story evolution tiếp tục tiến triển từ trạng thái sau v1.4.4.3 theo campaign story asset hiện tại.
- Story progression tiếp tục giữ Core/RNG/quest/companion gates và không tự tạo hidden escape truth.

## Phiên bản và phát hành

- Android `versionCode 103`, `versionName 1.4.4.4`.
- APK debug: `Backroom-1.4.4.4-debug.apk`.
- Full Preflight gồm Level validation, Gemini provider health gate, runtime patch-chain/audit, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification.
- Workflow chỉ tạo `v1.4.4.4` khi tag/release chưa tồn tại, sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged verification.
