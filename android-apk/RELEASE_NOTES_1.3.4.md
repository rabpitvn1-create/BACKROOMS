# Backroom 1.3.4 Debug

Bản 1.3.4 phát hành trạng thái mới nhất của `main` sau v1.3.3, tập trung vào tính nhất quán của Inventory, hiển thị xác suất thoát Level và cô lập bộ đếm turn trong combat.

## Thay đổi chính

- Thống nhất danh tính vật phẩm bằng một authority dựa trên `ItemCatalog`, để world loot, GM item gain, save/load, legacy ID và command resolver cùng sử dụng canonical item ID thay vì mỗi subsystem tự tạo một ID riêng.
- Sửa nhóm lỗi Inventory trong đó UI vẫn hiển thị vật phẩm đang sở hữu nhưng USE/TRANSFER lại báo không có hoặc không đủ vật phẩm. Các tên Việt hóa và alias như `Nước Hạnh Nhân`, `Băng gạc`, `Đèn pin`, `Pin`, `Cá Mòi Ba Cô Gái` và toàn bộ 11 official items được đưa qua cùng identity authority.
- Bổ sung migration cho save cũ và `lastReferencedItemId`, giữ các câu rút gọn như `Đưa cho Lucia` bám đúng vật phẩm canonical vẫn đang được sở hữu.
- Sửa parser quantity để từ số nằm trong tên riêng vật phẩm, ví dụ chữ `Ba` trong `Cá Mòi Ba Cô Gái`, không còn bị hiểu nhầm thành số lượng 3.
- Thêm regression end-to-end cho official item flow: world pickup → reducer → save/reload → natural-language transfer, cùng kiểm tra GM gain và legacy localized IDs.
- Thêm HUD `ESCAPE: <n>%` ngay dưới TURN, dùng cùng typography với TURN và đọc xác suất thoát authoritative từ Android runtime, bao gồm progression gate, trạng thái lối thoát đã xác nhận và bonus follower hiện hành.
- Cô lập combat turn khỏi legacy/global turn để các lệnh combat không làm tăng bộ đếm turn gameplay bên ngoài combat, đồng thời có regression riêng cho hành vi này.

## Phạm vi hợp nhất từ v1.3.3

- PR #136: Show escape chance under turn HUD.
- PR #137: Unify canonical item identity across inventory flow.
- Runtime patch cho Issue #134: combat commands giữ legacy/global turn cố định trong khi `CombatRuntime` dùng bộ đếm combat riêng.

## Phiên bản Android

- `versionCode 89`
- `versionName 1.3.4`

Đây là APK debug dành cho kiểm thử. Release chỉ được tạo trên `main` sau khi pipeline vượt qua runtime patch chain, runtime contracts, Kotlin unit tests, APK build, packaged APK verification, artifact upload, rồi tải lại APK từ GitHub Release để đối chiếu SHA-256 và kiểm tra package lần cuối.
