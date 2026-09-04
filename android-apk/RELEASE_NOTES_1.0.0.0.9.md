# BACKROOMS 1.0.0.0.9

Bản phát hành này đóng gói trạng thái `main` mới nhất sau khi tuyến Persistent Foundation + Gemini high-priority đã ổn định, đồng thời đưa các thay đổi mới nhất sau 1.0.0.0.8 vào APK phát hành.

- Phiên bản hiển thị: **1.0.0.0.9**.
- Android versionCode: **117**.
- Giữ nguyên tuyến AI: **Persistent Foundation → Gemini K1 → K2 → K3 → K4 → K5 → K6 (nếu được provision) → HAKU → LUNA → controlled failure**.
- Giữ nguyên schema validation theo từng provider attempt, shared turn deadline và telemetry `backroomFoundation` / `backroomProviderError` đã lọc secret.
- Bao gồm kỹ năng Entity-turn mới **Duller — Stillframe Lunge** từ thay đổi mới nhất trên `main`.
- Gemini live smoke CI được gia cố với connect/request timeout và retry có giới hạn cho lỗi transport, HTTP 408/429/5xx; lỗi auth/model/payload thật vẫn fail-fast.
- Snapshot UI vẫn network-free; Gemini high-priority chỉ áp dụng cho tuyến text Game Master/Foundation.
- Không hard-code hoặc ghi API key vào source, log hay release artifact.

CI của release tiếp tục chạy runtime patch audit, provider/Foundation verifiers, Node regressions, Kotlin tests, APK build, packaged APK verification và xác minh GitHub Release bằng SHA-256.
