# Backroom 1.3.8 Debug

Bản 1.3.8 đóng gói trạng thái `main` sau v1.3.7, tập trung vào độ ổn định Action Runtime, chất lượng văn GM/canon, SRU knowledge và chất lượng Snapshot Level 0.

## Action Runtime và ổn định lượt chơi

- Sửa lỗi một local rejection có thể để lại ActionRuntime session cũ, khiến hành động kế tiếp bị từ chối với `action_session_already_active`.
- Các kết quả deterministic/local đã kết thúc giờ giải phóng session trước khi trả quyền điều khiển cho UI.
- `abortAction()` dọn cả ActionRuntime metadata và PendingTurn tương ứng.
- Save đã bị kẹt từ APK cũ có thể tự hồi phục khi phát hiện session terminal/orphaned, nhưng provider retry hợp lệ vẫn giữ PendingTurn và RNG đã khóa để không reroll cùng một lượt.
- Thêm regression test cho chuỗi local rejection → release session → hành động kế tiếp bắt đầu bình thường.

## GM narration, hội thoại và Canon

- Registered-Level narration bị khóa về second-person limited qua Kai, dùng “bạn” cho người chơi và nhận campaign story context hiện hành.
- Loại bỏ fallback kể Kai ở ngôi ba khỏi đường Registered-Level presentation.
- Sửa interaction giữa narrative-boundary patch và discovery projection để runtime patch chain vẫn fail-closed nhưng không tự phá anchor của nhau.
- `WRITING.DIALOGUE` được route khi có speech intent hoặc companion hiện diện và GM có thể phát sinh hội thoại tự nhiên.
- Thêm prose rule ngắn cho GM: ưu tiên tiếng Việt tự nhiên, quan sát/hành động/hậu quả cụ thể; tránh chuỗi câu cụt điện ảnh, lặp ý, giải thích lại và exposition thừa; không cho prose sửa gameplay fact đã khóa.
- Không thêm model call mới cho lớp prose này.

## SRU Canon

- Thêm `WORLD.SRU.CORE` từ SRU codex 2299 làm nguồn knowledge runtime riêng cho tổ chức SRU.
- Retrieval SRU được giới hạn theo ngữ cảnh tổ chức để không kéo toàn bộ codex chỉ vì action nhắc tới `SRU-SG` hoặc `SRU-MK20`.

## Snapshot

- Level 2.2 — The Red Flood dùng Snapshot được người dùng cung cấp từ Google Drive cho slot chuyên biệt hiện hành.
- Level 0 dùng bốn ảnh WebP gốc đúng byte từ nguồn project, không resize hoặc recompress ở bản cuối.
- Xóa bộ Level 0 trung gian/override dư thừa sau khi authority ảnh gốc đã được khóa bằng kích thước và SHA-256.
- Giữ nguyên cơ chế rotation Snapshot hiện có và không thay đổi gameplay/Level routing.

## Phạm vi hợp nhất từ v1.3.7

- PR #186: Fix GM narration POV and restore canon context.
- PR #187: Use Drive snapshot for The Red Flood.
- PR #188: Route dialogue authority and tighten GM prose.
- PR #189: Add SRU force game codex.
- PR #190–#192: thay thế, sửa và cuối cùng khóa bốn Snapshot Level 0 ở chất lượng gốc.
- PR #193: Fix stale ActionRuntime lock after local rejection.

## Phiên bản Android

- `versionCode 93`
- `versionName 1.3.8`

Đây là APK debug dành cho kiểm thử. Workflow phát hành phải qua Level validation, Luna smoke test, runtime patch chain, runtime contracts, Kotlin tests, APK build, packaged APK verification, artifact upload, sau đó mới tạo Release và tải APK về đối chiếu SHA-256.
