# Backroom 1.4.4.6 Debug

Bản 1.4.4.6 đóng gói trạng thái `main` mới nhất sau v1.4.4.5, gồm routing Haku/Luna mới, khóa Gemini khỏi runtime và interleaved Party combat đã được sửa trong PR #268.

## AI provider routing

- Haku là provider chính cho luồng AI generation/editor tương ứng.
- Khi Haku gặp lỗi provider/runtime có thể fallback, request chuyển sang Luna.
- Luna là fallback duy nhất đang hoạt động sau Haku.
- Gemini API keys/config vẫn được giữ nguyên nhưng bị khóa khỏi runtime routing; không in, log, hard-code hoặc sao chép secret.
- CI có provider-routing contract và Gemini Provider Lock để ngăn routing cũ quay lại ngoài ý muốn.

## Party combat

- CombatRuntime tiếp tục là resolver authoritative cho Party combat tuần tự.
- Sau mỗi action hợp lệ của một Party member, Entity có một response tương ứng thay vì dồn response về cuối cả Party cycle.
- Actor sequencing và regression tests dùng stable character ID thay vì display name có thể thay đổi.
- AUTO/COUNTER/PASSIVE/STATE không thể bị kích hoạt thủ công để tiêu AP hoặc làm lệch lượt.
- Internal `PARTY_TURN_*` protocol không rò vào battle log hiển thị cho người chơi.
- Duplicate callback/retry không được tiêu AP, gây damage hoặc kích thêm Entity response.

## Phiên bản và phát hành

- Android `versionCode 105`, `versionName 1.4.4.6`.
- APK debug: `Backroom-1.4.4.6-debug.apk`.
- Full Preflight gồm Level validation, provider configuration gate, patch-chain/orphan audit, runtime/provider contracts, Node protocol tests, Kotlin/JUnit tests, APK build và packaged APK verification.
- Push-main workflow tạo tag/release `v1.4.4.6`, tải lại APK đã publish, đối chiếu SHA-256 và chạy lại packaged verification.
