# Thứ tự hành trình Level 0–13

**Trạng thái:** Đã xác lập thứ tự trong code; **chưa kích hoạt các route gameplay mới**.

## Quy tắc

1. Mỗi nhóm đi từ **Level chính** → **toàn bộ sublevel đánh số theo thứ tự chỉ định** → **các khu phụ có tên** → **Level kế tiếp**.
2. **Red Rooms là điểm cuối của nhóm Level 0**, ngay trước Level 1, theo yêu cầu của tác giả.
3. Không bỏ qua các mã thập phân hiện hữu: ví dụ Level 0.01, 0.11, 0.22, 0.23, 0.41, 0.66, 0.99 và Level 6.31 đều có vị trí cụ thể.
4. Khu phụ có tên sử dụng `area:<parent>:<key>` làm *itinerary key*, **không phải** `WorldNodeId` có rank chiến đấu hoặc exit tự động.
5. `OPEN` vẫn có chỗ trong thứ tự để bổ sung sau. Chưa có canon không đồng nghĩa game tự cấp cơ chế, tỷ lệ Entity, vật phẩm hay lối thoát.
6. Thứ tự là quyết định thiết kế của Project, **không phải tuyên bố rằng Wiki xác nhận các lối thông nhau**. Không dùng tên hoặc số tầng để suy độ khó.

## Toàn bộ tuyến theo nhóm

| Bắt đầu | Sublevel đánh số | Khu phụ sau các sublevel |
| --- | --- | --- |
| **Level 0** | 0.01 → 0.1 → 0.11 → 0.2 → 0.22 → 0.23 → 0.3 → 0.41 → 0.5 → 0.66 → 0.7 → 0.8 → 0.99 | Level ε → Dullness → LS-2 → Manila Room → The Torment → **Red Rooms** |
| **Level 1** | 1.1 → 1.2 → 1.3 → 1.5 | Base Alpha → Traders Vault |
| **Level 2** | 2.1 | Office Space EL3A |
| **Level 3** | 3.5 | Không có khu phụ có tên |
| **Level 4** | Không có sublevel đánh số | The Office Market |
| **Level 5** | 5.1 → 5.2 → 5.3 | Không có khu phụ có tên |
| **Level 6** | 6.1 → 6.2 → 6.3 → 6.31 | Không có khu phụ có tên |
| **Level 7** | 7.6 → 7.7 → 7.8 | The Hadal Zone |
| **Level 8** | 8.1 | The Sanctum Subterraneous |
| **Level 9** | 9.2 → 9.3 → 9.5 | Không có khu phụ có tên |
| **Level 10** | 10.1 → 10.2 | Không có khu phụ có tên |
| **Level 11** | 11.3 | Asset 11.1 → Scene-01.2 → AFTER HOURS → The Headquarters → Radio Backrooms' Studio |
| **Level 12** | Không có sublevel đánh số | Không có khu phụ có tên |
| **Level 13** | Không có sublevel đánh số | Không có khu phụ có tên |

**Chuỗi mẫu Level 0:** Level 0 → 0.01 → 0.1 → 0.11 → 0.2 → 0.22 → 0.23 → 0.3 → 0.41 → 0.5 → 0.66 → 0.7 → 0.8 → 0.99 → Level ε → Dullness → LS-2 → Manila Room → The Torment → **Red Rooms** → Level 1.

**Điểm kết:** Level 13, chưa xác lập điểm tiếp theo.

## Cấu trúc code và giới hạn

- Nguồn thứ tự có thể truy vấn: `core/progression/WorldJourneyOrder.kt`. Các `GROUPS` khai báo rõ từng mục, `STOPS` cung cấp danh sách phẳng; `nextAfter`, `previousBefore`, `groupStops` là phép tra cứu thuần, không cam kết việc chuyển tầng đã diễn ra.
- Danh tính, tình trạng canon và tên gọi: `WorldContentCatalog.kt`.
- Node và rank hiện hành: `WorldProgressionCore.kt`. **Không đổi 50 node/rank đã khóa, không tạo 17 rank mới, không thay 42 legacy edges.**
- Gameplay vẫn dùng `LinearWorldRouteResolver` cũ cho tới khi có thay đổi riêng ở Core exit resolver. Thứ tự này không vô hiệu hóa save cũ và không tự thay đổi Entity scaling.
- Khi triển khai chuyển tầng thật phải đánh giá toàn bộ đường đi, skip, backtrack, save migration và chuyển `namedSections` thành WorldNodes **chỉ khi có thiết kế rank/cơ chế được phê duyệt**.

## Kiểm chứng

`WorldJourneyOrderTest.kt` khóa đủ **14 Level + 36 sublevel đánh số + 17 khu phụ = 67 điểm**; kiểm tra thứ tự đầy đủ, Red Rooms trước Level 1, kế tiếp/liền trước, thiếu/trùng điểm, tính nguyên trạng của rank/exit. Khi chỉnh sửa thứ tự phải sửa test có chủ đích, không xóa kiểm tra để qua CI.
