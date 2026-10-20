# Level 0 satellites: 7 môi trường mở rộng (21/10/2026)

## Trạng thái nội dung

**Mở rộng do tác giả chỉ đạo**, dựa trên đặc điểm các node đã được lưu trong World Bible/LevelRouteRegistry và các quick/detail cards. Đây là **28 khu chính + 112 khu phụ mới của dự án**, không khẳng định chúng đã xuất hiện trong tài liệu wiki gốc hoặc Google Drive trước ngày này.

Tất cả tên, mô tả riêng và hierarchy nằm trong duy nhất
`app/src/main/java/com/rabpit/backroom/core/Level0SatelliteWorldDefinition.kt`.
Nội dung **không tạo** Entity/NPC, vật phẩm, thuốc, phần thưởng, hidden exit, sự kiện bắt buộc hoặc lore giải thích nguồn gốc chưa được xác thực.

| Core node | Không gian Canon giữ nguyên | Khu chính | Khu phụ |
| --- | --- | ---: | ---: |
| `0.1` Zenith Station | Metal/glass damaged station, old electronics, exposed wiring | 4 | 16 |
| `0.2` Remodeled Mess | White renovated walls, dry red carpet, collapsing architecture | 4 | 16 |
| `0.5` Aquaclaustrophobic Infirmary | Polluted flooded passages, decayed infirmary, electricity | 4 | 16 |
| `0.7` The Reminiscence District | Mixed architectural eras; **không xác nhận time travel** | 4 | 16 |
| `manila_room` Manila Room | Quiet beige room, wooden floor, central table, abandoned notes | 4 | 16 |
| `the_torment` The Torment | Grayscale concentric graveyard, dead trees, fog, fixed moon, broken statue | 4 | 16 |
| `red_rooms` Red Rooms | Red wallpaper, red dim light, sticky carpet, mold, poor signals | 4 | 16 |
| **Tổng** | | **28** | **112** |

`dullness`, `epsilon`, `ls_2` vẫn thuộc phân loại AREA trong LevelRouteRegistry và **không** bị đổi thành SPECIAL trong phạm vi này. Level 0 gốc vẫn giữ nguyên 8 khu + 29 khu phụ.

## Định danh và Core

`sat_<code>_z1..z4` là khu chính; `sat_<code>_z1..z4_s1..s4` là khu phụ.
Các code: 01, 02, 05, 07, MN, TM, RR. Không dùng số khu làm ID Level chuyển cảnh.

`WorldDefinitions` đăng ký đúng bảy exact node; từng node cung cấp 20 môi trường và một đồ thị 20 phòng có thể sinh lại từ seed.
Bốn khu chính nằm trên vòng nối hai chiều; mỗi khu chính và bốn khu phụ tạo một chu trình riêng, nên hành lang đơn không khóa toàn bộ đường quay lại. Các `fieldStudies` lấy mô tả Core-authored tương ứng. Không sửa `LevelRouteRegistry`, không mở lối Level 0 → 0.1 trước khi gameplay thực sự cho phép.

Đây là **catalog và world definition cho các node khi được khởi tạo**; thay đổi này không bổ sung nút chuyển level hoặc gỡ khóa progression của PR #654.

## Tái sử dụng ảnh Drive có sẵn

Nguồn Drive gốc: [Novel / Backrooms Level](https://drive.google.com/drive/folders/1QsapoDnhAH69j0EIgdVQb7jV6sKc_IZe).

Mỗi node có ba WebP đã đóng gói trong `app/src/main/assets/level_snapshots/` và được liệt kê trong `snapshot-mapping.js`:
- `0.1`: `level_00p1__snapshot_001..003.webp`
- `0.2`: `level_00p2__snapshot_001..003.webp`
- `0.5`: `level_00p5__snapshot_001..003.webp`
- `0.7`: `level_00p7__snapshot_001..003.webp`
- `manila_room`: `area_00__manila_room__snapshot_001..003.webp`
- `the_torment`: `area_00__the_torment__snapshot_001..003.webp`
- `red_rooms`: `area_00__red_rooms__snapshot_001..003.webp`

Không sao chép WebP hoặc nén lại. Hình được chọn **cố định theo exact node và environment ID**, không dựa vào lượt, vị trí tên tự do hoặc bộ đếm. Dùng 21 ảnh cho 140 cảnh đồng nghĩa một số cảnh có chung nền; đây là giới hạn asset có thật, không phải 140 ảnh độc nhất hay 140 tranh tái tạo. Đổi ảnh riêng sau này chỉ cần thêm WebP và sửa ánh xạ đúng ID, tránh biến snapshot thành slideshow.

## Giới hạn xác minh

Đã rà soát tĩnh số nhóm/nội dung và định danh. Có regression về 140 ánh xạ WebP và world-graph node riêng tại:
- `tests/level0-satellite-snapshots.test.cjs`
- `app/src/test/java/com/rabpit/backroom/core/Level0SatelliteWorldDefinitionTest.kt`

Không tự nhận Android runtime, build APK hay CI đã thành công nếu chưa có kết quả. Không thay đổi logic save cũ, và không tạo migration/adapter save cũ.
