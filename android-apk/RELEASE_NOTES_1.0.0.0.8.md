# BACKROOMS 1.0.0.0.8

Bản phát hành này hoàn tất việc đưa Persistent Foundation vào tuyến Game Master thực tế và kích hoạt lại Gemini với mức ưu tiên cao.

- Phiên bản hiển thị: **1.0.0.0.8**.
- Android versionCode: **116**.
- Giữ nguyên Persistent Foundation sáu section, manifest bất biến, active pointer nguyên tử, hai local worker và turn pinning từ 1.0.0.0.7.
- Tuyến AI cuối cùng: **Gemini K1 → K2 → K3 → K4 → K5 → K6 (nếu được provision) → HAKU → LUNA → controlled failure**.
- Runtime dùng contract sáu slot Gemini theo naming của project `Hua-s-Family`: `GEMINI_API_KEY`, `GEMINI_API_KEY_2` ... `GEMINI_API_KEY_6`; không hard-code hoặc ghi secret vào log.
- K1 tương thích với secret cũ `GEMINI_API_KEY_1`; K6 tự động tham gia pool khi repository được provision secret tương ứng.
- Gemini dùng `gemini-3.6-flash`, model stable được giữ theo cấu hình writer của `Hua-s-Family`.
- Writer, repair và audit của Foundation đều dùng Gemini trước, sau đó mới fallback HAKU/LUNA; schema validation chạy trong từng provider attempt.
- Gemini, HAKU và LUNA cùng chia sẻ deadline đơn điệu của turn để không phá ngân sách 75 giây.
- Thêm telemetry `backroomFoundation` để phân biệt Foundation slice active với legacy fallback.
- Thêm `backroomProviderError` đã rút gọn và lọc secret để debug HTTP/timeout/network/schema mà không ghi prompt hay API key.
- Log TXT tiếp tục redaction toàn bộ HAKU/LUNA/Gemini credentials, bao gồm cả slot Gemini K6.
- CI live-smoke toàn bộ Gemini credential đang được provision và bắt buộc tối thiểu K1-K5 phải hoạt động.
- Snapshot UI vẫn network-free; việc kích hoạt Gemini ở đây áp dụng cho tuyến text Game Master/Foundation.

CI chạy runtime patch audit, provider/Foundation verifiers, Node regressions, Kotlin tests, APK build, packaged APK verification và xác minh GitHub Release bằng SHA-256.
