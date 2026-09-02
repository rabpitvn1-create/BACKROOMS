# Backroom 1.4.2 Debug

Bản 1.4.2 là hotfix cho lớp provider runtime sau v1.4.1. Trọng tâm là ngăn APK tự đánh dấu provider khỏe thành lỗi chỉ vì timeout quá ngắn, đồng thời bổ sung kiểm tra sống cho toàn bộ Gemini key pool.

## Provider runtime hotfix

- Tăng Luna fallback read timeout từ 12 giây lên 30 giây, vẫn giữ đúng một fallback attempt có giới hạn.
- Tăng Haku prose-editor read timeout từ 12 giây lên 30 giây; Haku vẫn chỉ xử lý presentation và vẫn fail-open về prose Gemini gốc nếu editor lỗi.
- Không thay đổi thứ tự provider, gameplay state, canon, inventory, combat, dialogue authority, dice, progression hoặc quyền của Haku.
- Giữ nguyên Android `INTERNET` permission và cơ chế đóng gói các secret hiện tại.

## Gemini provider health

- Thêm live smoke workflow cho `GEMINI_API_KEY_1` đến `GEMINI_API_KEY_5` bằng cùng họ Google GenerateContent API mà APK sử dụng.
- Smoke test chỉ in lane K1-K5 và trạng thái, không in giá trị secret.
- Credential failure 401/403 được coi là lỗi xác thực rõ ràng; pool chỉ được xem là serviceable khi còn ít nhất một lane tạo nội dung khỏe.
- Kiểm tra thực tế trước release cho thấy cả năm Gemini lane đều healthy, Luna smoke test pass và Haku smoke test pass.

## Phiên bản và phát hành

- Bao gồm hotfix từ PR #216.
- Android `versionCode 97`, `versionName 1.4.2`.
- APK debug dành cho kiểm thử: `Backroom-1.4.2-debug.apk`.
- Workflow chạy Level validation, LiteRT checks, Luna smoke test, Haku smoke test, runtime patch chain, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification trước khi xuất bản.
- Gemini K1-K5 có workflow smoke riêng để phân biệt lỗi credential/provider thật với lỗi timeout hoặc routing trong APK.
- Sau khi merge vào `main`, workflow chỉ tạo `v1.4.2` nếu tag/release chưa tồn tại; sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged APK verification.
