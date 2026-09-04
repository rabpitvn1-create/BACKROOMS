# BACKROOMS 1.0.0.0.11

Bản phát hành này sửa triệt để lỗi điều tra runtime được ghi nhận trong Issue #330 và log in-game của `1.0.0.0.10`, đồng thời đóng gói trạng thái `main` mới nhất sau các thay đổi Entity gần đây.

- Phiên bản hiển thị: **1.0.0.0.11**.
- Android versionCode: **119**.
- Giữ nguyên tuyến AI: **Persistent Foundation → Gemini K1 → K2 → K3 → K4 → K5 → K6 (nếu được provision) → HAKU → LUNA → controlled failure**.
- TXT debug log có `ERROR_SUMMARY`, `ROOT_CAUSE` và `ERROR_TIMELINE`, kèm correlation id, component, phase, provider, Gemini credential slot, error type và fallback path.
- Gemini K1..K6 ghi rõ từng request/success/failure đã sanitize; lỗi schema ghi đúng role `WRITER` / `REPAIR` / `AUDIT` và validation reason.
- Foundation không còn nuốt nguyên nhân build/slice failure; log phân biệt `FOUNDATION_COMPILE`, `FOUNDATION_MANIFEST`, `FOUNDATION_INSTALL`, `FOUNDATION_ACTIVATE`, `FOUNDATION_SLICE` và legacy fallback.
- Sửa WebView diagnostic bridge: native event không có callback JavaScript sẽ không còn bị gọi mù và sinh `Script error.`. Lỗi JS thật giờ ghi message, source, line, column và stack khi trình duyệt cung cấp.
- Canon audit tiếp tục repair các `state_narrative_mismatch`, nhưng chỉ **Android deterministic reducer** mới có quyền hard-block cả lượt vì mismatch state/narrative. Nhận định semantic từ AI auditor vẫn được dùng để repair nhưng không còn tự gây softlock sau repair khi reducer không chứng minh state operation bị từ chối.
- Audit diagnostics giữ `source`, `rule`, `claim` và `reason`, thay vì chỉ xuất tên rule.
- Giữ ring buffer bounded và tăng cường redaction cho Gemini/HAKU/LUNA keys, Bearer token, `sk-*` và chuỗi API key dạng Google; không dump raw prompt/canon/private state vào structured diagnostics.
- Bổ sung regression tests cho root-cause extraction, fallback timeline, secret redaction, bounded ledger và typed schema validation.

CI của release chạy runtime patch audit, Issue #330 verifier, provider/Foundation verifiers, Node regressions, Kotlin/Java unit tests, APK build, packaged APK verification và xác minh GitHub Release bằng SHA-256.
