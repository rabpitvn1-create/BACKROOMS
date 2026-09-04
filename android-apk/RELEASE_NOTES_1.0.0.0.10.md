# BACKROOMS 1.0.0.0.10

Bản phát hành này bump phiên bản từ `1.0.0.0.9` lên `1.0.0.0.10` và đóng gói trạng thái `main` hiện tại vào APK phát hành mới.

- Phiên bản hiển thị: **1.0.0.0.10**.
- Android versionCode: **118**.
- Không thay đổi tuyến AI hiện hành: **Persistent Foundation → Gemini K1 → K2 → K3 → K4 → K5 → K6 (nếu được provision) → HAKU → LUNA → controlled failure**.
- Giữ nguyên schema validation theo từng provider attempt, shared turn deadline và telemetry đã lọc secret.
- Giữ nguyên Gemini live smoke với timeout và retry có giới hạn cho lỗi transport/HTTP transient.
- Không hard-code hoặc ghi API key, token hay secret vào source, log hoặc release artifact.

CI của release chạy runtime patch audit, provider/Foundation verifiers, Node regressions, Kotlin tests, APK build, packaged APK verification và xác minh GitHub Release bằng SHA-256.
