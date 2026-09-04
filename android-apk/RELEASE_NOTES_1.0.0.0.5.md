# BACKROOMS 1.0.0.0.5

Bản phát hành này đóng gói hotfix sau PR #308 cho lỗi hội thoại với Lucia có thể kích hoạt Entity encounter lặp lại, đồng thời tăng độ ổn định của HAKU primary.

- Phiên bản hiển thị: **1.0.0.0.5**.
- Android versionCode: **113**, tăng từ 112 để giữ đường nâng cấp hợp lệ.
- Encounter Entity mới chỉ được phép khởi tạo từ **EXPLORE**; `SEARCH` và freeform `EXECUTE`, bao gồm hội thoại với Lucia, không còn roll roaming Entity mới.
- Lucia tiếp tục là fixed story-owned encounter tại Level 0, không bị biến thành random Entity/follower spawn.
- Giữ nguyên fix stun one-event đã có: trạng thái mất hành động hết hạn sau đúng một personal action đã commit.
- HAKU primary được siết JSON-only contract, giảm temperature xuống `0.2`, tăng completion budget lên `3200`, và dùng read timeout riêng `30s`.
- Nếu HAKU trả malformed/empty JSON, router tiếp tục fallback sang **LUNA**; thứ tự runtime vẫn là **HAKU → LUNA → controlled failure**.
- Gemini tiếp tục bị khóa khỏi runtime text routing.
- Không thay đổi save format, combat math, loot, canon hay Party capacity.

Quy trình phát hành chạy runtime patch audit, encounter/provider routing verifier, final runtime contracts, Node regressions, Kotlin tests, build debug APK, packaged APK verification và xác minh lại GitHub Release bằng SHA-256.
