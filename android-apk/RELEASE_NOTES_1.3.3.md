# Backroom 1.3.3 Debug

Bản 1.3.3 tổng hợp toàn bộ trạng thái hiện tại trên `main` sau v1.3.2, giữ nguyên pipeline Preflight + Build/Release thống nhất và đưa các sửa lỗi gameplay mới nhất vào cùng một APK.

## Thay đổi chính

- Sửa tính nhất quán của tương tác vật phẩm: vật phẩm vật lý được nhắc trong lời kể được đối chiếu vào world-item ledger ngay trong lượt, vật phẩm đã sở hữu không bị suy diễn lặp lại thành vật phẩm ngoài thế giới, và UI chỉ highlight vật phẩm còn khả dụng ở vị trí hiện tại cùng vật phẩm đang sở hữu.
- Sửa phân tích lệnh chuyển/trao/dùng vật phẩm để tên nhân vật không bị hiểu nhầm thành tên vật phẩm; các câu như `Đưa cho Lucia` và `Dùng băng gạc cho Lucia` được phân giải đúng người dùng, người nhận và vật phẩm.
- Sửa việc dùng consumable cho đồng đội: vật phẩm vẫn được tiêu thụ từ inventory của người thực hiện nhưng hiệu ứng có thể áp dụng lên mục tiêu sống được chỉ định; trạng thái HP của Party được đưa vào knowledge context để phản hồi và gameplay đồng bộ hơn.
- Khôi phục hiển thị kỹ năng `PASSIVE` trong bảng kỹ năng nhân vật. Kai hiển thị `Devil Blessing` đúng dưới dạng PASSIVE, đồng thời giữ nguyên thứ tự kỹ năng cũ và gameplay +5% Tấn Công, Phòng Thủ, Né tránh và Max HP cho đồng đội ACTIVE đủ điều kiện.
- Thêm regression bảo đảm Kai, Iris, Syvial, An Nhiên và Lucia đều còn kỹ năng PASSIVE trong catalog và lớp Character Detail/WebView không lọc mất các hàng PASSIVE.
- Giữ toàn bộ runtime patch chain và contract checks của v1.3.2, bổ sung các contract mới cho item interaction và passive-skill visibility trước khi Kotlin tests, build APK và packaged APK verification được phép chạy qua.

## Phạm vi hợp nhất từ v1.3.2

- PR #130: Fix item discovery, transfer, and companion item use.
- PR #132: Show passive skills in character skill table.
- Các commit thử nghiệm cleanup GitHub Actions sau v1.3.2 đã tự triệt tiêu và không tạo thay đổi chức năng trong trạng thái cuối của `main`.

## Phiên bản Android

- `versionCode 88`
- `versionName 1.3.3`

Đây là APK debug dành cho kiểm thử. Release chỉ được tạo trên `main` sau khi cùng pipeline vượt qua runtime contracts, Kotlin unit tests, build APK, kiểm tra chữ ký/package/zipalign/assets và xác minh lại APK tải từ GitHub Release bằng SHA-256.
