# BACKROOMS 1.0.0.0.5

Bản phát hành này đưa hotfix Lucia encounter và HAKU reliability đã merge ở PR #308 vào bản phát hành mới.

- Phiên bản hiển thị: **1.0.0.0.5**.
- Android versionCode: **113**, tăng từ 112 để giữ đường nâng cấp hợp lệ.
- Entity encounter ngẫu nhiên mới chỉ được phép khởi tạo từ **EXPLORE**; `SEARCH` và freeform `EXECUTE`, bao gồm hội thoại với Lucia, không còn tự roll roaming Entity.
- Lucia tiếp tục là encounter cố định do story authority quản lý ở Level 0, không bị biến thành random encounter.
- Giữ fix stun một lượt hiện có và regression guard để tránh quay lại trạng thái khóa hành động kéo dài.
- HAKU vẫn là provider chính theo thứ tự **HAKU → LUNA → controlled failure**.
- HAKU dùng JSON-only contract chặt hơn, temperature thấp hơn, completion budget lớn hơn và read timeout riêng để giảm malformed/truncated output.
- Nếu HAKU trả JSON lỗi hoặc response không hợp lệ, runtime chuyển sang LUNA theo cùng validation contract; Gemini vẫn bị khóa khỏi runtime routing.
- Không thay đổi save format, canon authority hay combat math ngoài các fix đã merge.

Quy trình phát hành chạy full runtime patch chain, encounter/provider contract checks, Kotlin tests, build debug APK, packaged APK verification và xác minh GitHub Release bằng SHA-256.
