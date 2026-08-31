# Backroom 1.3.5 Debug

Bản 1.3.5 đóng gói trạng thái mới nhất của `main` sau v1.3.4, tập trung vào mở rộng pipeline Level theo dữ liệu, Entity encounter/loot procedural và tăng kiểm chứng trước khi phát hành.

## Thay đổi chính

- Chuyển đăng ký và kiểm tra Level sang mass-content pipeline dựa trên `level_catalog/**`, `levels/**` và `level_profiles/**`; Level ID vẫn là chuỗi opaque, không phụ thuộc số học ID.
- Thêm validator fail-closed cho catalog, quan hệ parent, transition graph, inheritance profile, content coverage, solvability và audit hard-code; validator chạy trước build và chạy lại trên tài nguyên đã đóng gói trong APK.
- Tăng khả năng scale bằng inheritance resolver iterative + cache, indexed child/transition lookup, regression synthetic hơn 1.000 Level và chuỗi inheritance sâu.
- Tích hợp Entity encounter/loot theo hướng procedural và cân bằng lại loot, đồng thời giữ Entity ở vai trò hazard/combat pressure thay vì authority của hidden escape solution.
- Giữ Levels 0/1 explicit, Levels 2–6 procedural và các sublevel legacy chưa có canon runtime dưới dạng placeholder khai báo rõ thay vì âm thầm thiếu implementation.
- APK Release được build trên `main`, chạy Level validation, runtime patches/contracts, Kotlin tests, packaged verification, upload artifact, sau đó tải ngược Release để đối chiếu SHA-256.

## Phạm vi hợp nhất từ v1.3.4

- PR #161: Make Entity encounters procedural and rebalance loot.
- PR #162: Scale Level onboarding with data-driven content validation.
- PR #163: Index Level transition lookup.

## Phiên bản Android

- `versionCode 90`
- `versionName 1.3.5`

Đây là APK debug dành cho kiểm thử.
